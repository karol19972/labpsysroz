/*-
 * Copyright (C) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */

package oracle.kv.util.migrator.impl.source.ondbbinary;

import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.utils.DataSerializer;
import oracle.kv.util.expimp.utils.ObjectStoreAPI;
import oracle.kv.util.expimp.utils.DataSerializer.DataCorruptedException;
import oracle.kv.util.expimp.utils.DataSerializer.KeyValueBytes;
import oracle.kv.util.migrator.impl.data.ondbbinary.KeyValueBytesEntry;
import oracle.nosql.common.migrator.data.Entry;
import oracle.nosql.common.migrator.impl.FileConfig.FileStoreType;
import oracle.nosql.common.migrator.impl.source.DataSourceBaseImpl;

/**
 * The source supplies deseralized nosqldb binary record entries from nosql
 * binary files on local file system or object store.
 */
public class OndbBinaryFileSource extends DataSourceBaseImpl {

    private final String fileSegment;
    private final OndbBinaryFileSourceConfig config;
    private final String targetTable;
    private final boolean isTableData;
    private final ObjectStoreAPI objectStoreAPI;

    private DataInputStream dis;
    private KeyValueBytes nextKV;

    public OndbBinaryFileSource(String fileSegment,
                                OndbBinaryFileSourceConfig config,
                                String targetTable,
                                boolean isTableData,
                                Logger logger) {
        this(fileSegment, config, targetTable, isTableData, null, logger);
    }

    public OndbBinaryFileSource(String fileSegment,
                                OndbBinaryFileSourceConfig config,
                                String targetTable,
                                boolean isTableData,
                                ObjectStoreAPI objectStoreAPI,
                                Logger logger) {

        super(ONDB_BINARY_TYPE, fileSegment, null, logger);
        this.fileSegment = fileSegment;
        this.config = config;
        this.targetTable = targetTable;
        this.isTableData = isTableData;
        this.objectStoreAPI = objectStoreAPI;
        dis = null;
        nextKV = null;
    }

    @Override
    public String getTargetTable() {
        return targetTable;
    }

    @Override
    protected boolean hasNextEntry() {
        if (nextKV == null) {
            DataInputStream in = getInputStream();
            if (in != null) {
                nextKV = getNextKeyValueVersion(in);
            }
        }
        return (nextKV != null);
    }

    private DataInputStream getInputStream() {
        if (dis != null) {
            return dis;
        }
        InputStream in = null;
        if (config.getFileStoreType() == FileStoreType.FILE) {
            try {
                in = new FileInputStream(new File(fileSegment));
            } catch (FileNotFoundException e) {
                logger.warning("File segment not found: " + fileSegment);
                return null;
            }
        } else {
            try {
                in = objectStoreAPI.retrieveObject(fileSegment);
            } catch (RuntimeException re) {
                logger.log(Level.WARNING, "File segment not found " +
                                           fileSegment + ": " + re.getMessage(),
                           (re.getCause() != null ? re.getCause() : re));
                return null;
            }
        }
        dis = new DataInputStream(in);
        return dis;
    }

    private KeyValueBytes getNextKeyValueVersion(DataInputStream in) {
        while (true) {
            try {
                if (isTableData) {
                    return DataSerializer.readTableData(in, getName(),
                                           config.getExportVersion());
                }
                /* Other data: NonFormat, Lob */
                KeyValueBytes kvb = DataSerializer.readKVData(in, getName());
                if (kvb != null && kvb.getType() == KeyValueBytes.Type.LOB) {
                    InputStream lobis = getLobInputStream(kvb.getLobFileName());
                    if (lobis == null) {
                        /* Skip this record if failed to get the lob stream */
                        continue;
                    }
                    kvb.setLobStream(lobis);
                }
                return kvb;
            } catch (DataCorruptedException dce) {
                /* Skip the record if checksum verified failed */
                if (dce.isChecksumMismatched()) {
                    continue;
                }
                throw dce;
            }
        }
    }

    @Override
    protected Entry nextEntry() {
        if (!hasNextEntry()) {
            return null;
        }
        KeyValueBytesEntry kvEntry = new KeyValueBytesEntry(nextKV);
        nextKV = null;
        return kvEntry;
    }

    public boolean isTableData() {
        return isTableData;
    }

    /**
     * Gets the given LOB input stream from the export package
     *
     * @param lobFileName name of the lob file in export package
     */
    private InputStream getLobInputStream(String lobFileName) {
        if (config.getFileStoreType() == FileStoreType.FILE) {
            return getLocalLobStream(lobFileName);
        }
        return getObjectStoreLobStream(lobFileName);
    }

    private InputStream getLocalLobStream(String lobFileName) {
        File lobFolder = config.getLobFolder();
        if (!lobFolder.exists() || !lobFolder.isDirectory()) {
            logger.warning("LOB folder not found in export package: " +
                           lobFolder);
            return null;
        }

        File lobFileFolder = new File(lobFolder, lobFileName);
        if (!lobFileFolder.exists() || !lobFileFolder.isDirectory()) {
            logger.warning("LOB folder not found in export package: " +
                           lobFileFolder);
            return null;
        }

        /*
         * Batch all the lob file segment input streams
         */
        List<InputStream> inputStream = new ArrayList<InputStream>();

        for (File lobFile : lobFileFolder.listFiles()) {
            if (lobFile.isFile()) {
                try {
                    inputStream.add(new FileInputStream(lobFile));
                } catch (FileNotFoundException e) {
                    logger.log(Level.SEVERE, "File segment for LOB: " + lobFile
                               + "not found.", e);
                }
            }
        }

        /*
         * Return the entire batch of lob file segment input streams as a
         * single input stream using SequenceInputStream
         */
        Enumeration<InputStream> enumeration =
            new IteratorEnumeration<InputStream>(inputStream.iterator());
        SequenceInputStream stream = new SequenceInputStream(enumeration);

        return stream;
    }

    private InputStream getObjectStoreLobStream(String lobFileName) {
        assert (objectStoreAPI != null);
        try {
            return objectStoreAPI.retrieveObjectByManifest(lobFileName);
        } catch (RuntimeException re) {
            /* Return null if fail to retrieve lob stream */
            logger.log(Level.SEVERE, "File segment for LOB: " + lobFileName +
                                     "not found.",
                       (re.getCause() != null ? re.getCause() : re));
            return null;
        }
    }

    private class IteratorEnumeration<E> implements Enumeration<E> {
        private final Iterator<E> iterator;

        public IteratorEnumeration(Iterator<E> iterator) {
            this.iterator = iterator;
        }

        @Override
        public E nextElement() {
            return iterator.next();
        }

        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }
    }
}
