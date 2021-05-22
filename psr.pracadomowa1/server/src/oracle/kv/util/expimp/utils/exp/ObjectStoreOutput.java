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

package oracle.kv.util.expimp.utils.exp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.utils.Chunk;
import oracle.kv.util.expimp.utils.ObjectStoreAPI;
import oracle.kv.util.expimp.utils.ObjectStoreAPIFactory;
import oracle.kv.util.expimp.utils.exp.CustomStream.CustomInputStream;

/**
 * An implementation of AbstractStoreOutput that manages output to object store
 * on cloud.
 */
public class ObjectStoreOutput extends AbstractStoreOutput {

    /*
     * Stream for logging data
     */
    private final ByteArrayOutputStream loggerOutput;

    /*
     * The ObjectStoreAPI handle
     */
    private final ObjectStoreAPI objectStoreAPI;

    /*
     * Size of exported LOB file segment = 1GB
     */
    private static final long fileSize = 1000 * 1000 * 1000;

    private static final String chunksKey = "chunksKey";

    public ObjectStoreOutput(String serviceName,
                             String userName,
                             String password,
                             String serviceURL,
                             String containerName,
                             Logger logger) {
        super(logger);

        objectStoreAPI = ObjectStoreAPIFactory
                            .getObjectStoreAPI(serviceName,
                                               userName,
                                               password,
                                               serviceURL,
                                               containerName);
        objectStoreAPI.createContainer();
        loggerOutput = new ByteArrayOutputStream();
    }

    public ByteArrayOutputStream getLoggerOutput() {
        return loggerOutput;
    }

    /**
     * Exports the file segment bytes to the container in Oracle Storage
     * Cloud Service.
     *
     * @param fileName file being exported
     * @param chunkSequence identifier for the file segment being exported
     * @param stream input stream reading bytes from kvstore into export store
     */
    @Override
    public boolean doExport(String fileName,
                            String chunkSequence,
                            CustomInputStream stream) {

        String filePrefix = fileName + "-" + fileName.length();
        String fileKey = filePrefix + "-" + chunkSequence;
        return storeObject(fileKey, stream, false);
    }

    /**
     * Work done post export. A hash map with file name as the key and the
     * number of segments of the file as the value is stored in the container
     * in Oracle Storage Cloud Service. This map is used during import to
     * determine the number of segments for a given file and to retrieve all
     * the file segments.
     */
    @Override
    public void doPostExportWork(Map<String, Chunk> chunks) {
        /*
         * Map holding all the exported filenames and the number of file
         * segments
         */
        Map<String, String> customMetadata = new HashMap<String, String>();
        for (Map.Entry<String, Chunk> entry : chunks.entrySet()) {

            String fileName = entry.getKey();
            Chunk chunk = entry.getValue();

            String filePrefix = fileName + "-" + fileName.length();
            String objectStoreRecordKey = filePrefix + "-" + "ManifestKey";

            try {
                /*
                 * Store the manifest object for this file. The manifest object
                 * is used to retrieve all the file segments belonging to a
                 * given file.
                 */
                objectStoreAPI.storeObjectManifest(objectStoreRecordKey,
                                                   filePrefix);
            } catch (RuntimeException re) {
                /* do nothing */
                logger.log(Level.WARNING, re.getMessage(),
                           (re.getCause() != null ? re.getCause() : re));
            }

            /*
             * CustomMetadata map will hold entries only for table data and
             * other data (none format data). SchemaDefinition and LobFile
             * segments are skipped.
             */
            if (fileName.equals("SchemaDefinition") ||
                    fileName.contains("LOBFile")) {
                continue;
            }

            /**
             * Period(.) and colon(:) are not supported characters in key of
             * custom metadata, replace them with "-".
             */
            String key = fileName.replace(".", "-").replace(":", "-");
            customMetadata.put(key, fileName + ", " +
                                    chunk.getNumChunks().toString());
        }

        InputStream stream = new ByteArrayInputStream(chunksKey
                             .getBytes(StandardCharsets.UTF_8));

        /*
         * Store the customMetadata hashMap into Oracle Storage Cloud Service
         */
        try {
            objectStoreAPI.storeObject(chunksKey, customMetadata, stream);
        } catch (RuntimeException re) {
            /* do nothing */
            logger.log(Level.WARNING, re.getMessage(),
                       (re.getCause() != null ? re.getCause() : re));
        }
    }

    /**
     * Returns the maximum size of lob file segment that will be exported
     */
    @Override
    public long getMaxLobFileSize() {
        return fileSize;
    }

    /**
     * Stores an object in Oracle Storage Cloud Service.
     *
     * @param key key for the object
     * @param in stream holding the object data
     * @param ignoreError flag indicates
     */
    public boolean storeObject(String key, InputStream in, boolean ignoreError){
        try {
            objectStoreAPI.storeObject(key, in);
            return true;
        } catch (RuntimeException re) {
            if (ignoreError) {
                /* do nothing */
                logger.log(Level.WARNING, re.getMessage(),
                           (re.getCause() != null ? re.getCause() : re));
                return false;
            }
            throw re;
        }
    }
}
