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

import static
oracle.kv.util.migrator.impl.source.ondbbinary.OndbBinaryFileSourceConfig.OTHER_DATA_FILE_PREFIX;
import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.MigratorUtils.toFilePath;
import static
oracle.kv.util.migrator.impl.source.ondbbinary.OndbBinaryFileSourceConfig.CHUNKS_KEY;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.utils.Chunk;
import oracle.kv.util.expimp.utils.ObjectStoreAPI;
import oracle.kv.util.expimp.utils.ObjectStoreAPIFactory;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.DataSourceFactory;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.FactoryBase;
import oracle.nosql.common.migrator.impl.FileConfig.FileStoreType;

/**
 * The factory that creates OndbBinaryFileSource objects.
 */
public class OndbBinaryFileSourceFactory extends FactoryBase
    implements DataSourceFactory {

    public OndbBinaryFileSourceFactory() {
        super(ONDB_BINARY_TYPE, OndbBinaryFileSourceConfig.COMMAND_ARGS);
    }

    @Override
    public DataSourceConfig parseJson(InputStream in, int configVersion) {
        return new OndbBinaryFileSourceConfig(in, configVersion);
    }

    @Override
    public DataSourceConfig createConfig(MigratorCommandParser parser) {
        return new OndbBinaryFileSourceConfig(parser);
    }

    @Override
    public DataSource[] createDataSources(DataSourceConfig config,
                                          String sinkType,
                                          Logger logger) {

        OndbBinaryFileSourceConfig cfg = (OndbBinaryFileSourceConfig) config;
        List<DataSource> sources;
        if (cfg.getFileStoreType() == FileStoreType.FILE) {
            sources = addLocalFilesSource(cfg, logger);
        } else {
            sources = addObjectStoreSource(cfg, logger);
        }
        return (sources.isEmpty() ?
                null : sources.toArray(new DataSource[sources.size()]));
    }

    @Override
    public boolean isTextual() {
        return true;
    }

    private List<DataSource> addLocalFilesSource(OndbBinaryFileSourceConfig cfg,
                                                 Logger logger) {
        List<DataSource> sources = new ArrayList<DataSource>();

        File tableDir = cfg.getTableFolder();
        if (tableDir != null) {
            if (tableDir.exists() && tableDir.isDirectory()) {
                addDataSources(tableDir, cfg, sources,
                               true /* isTableData */, logger);
            } else {
                logger.info("Folder not exists or not a valid folder: " +
                            toFilePath(tableDir));
            }
        }

        File otherDir = cfg.getOtherFolder();
        if (otherDir != null) {
            if (otherDir.exists() && otherDir.isDirectory()) {
                addDataSources(otherDir, cfg, sources,
                               false/* isTableData */, logger);
            } else {
                logger.info("Folder not exists or not a valid folder: " +
                            toFilePath(otherDir));
            }
        }
        return sources;
    }

    private void addDataSources(File folder,
                                OndbBinaryFileSourceConfig cfg,
                                List<DataSource> sources,
                                boolean isTableData,
                                Logger logger) {

        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                String tableName = file.getName();
                for (File df : file.listFiles()) {
                    addDataSource(sources, cfg, tableName, isTableData,
                                  df, logger);
                }
            } else {
                addDataSource(sources, cfg, null /* tableName */, isTableData,
                              file, logger);
            }
        }
    }

    private void addDataSource(List<DataSource> sources,
                               OndbBinaryFileSourceConfig cfg,
                               String tableName,
                               boolean isTableData,
                               File file,
                               Logger logger) {

        DataSource ds = createDataSource(file, cfg, tableName,
                                         isTableData, logger);
        sources.add(ds);
    }

    private DataSource createDataSource(File file,
                                        OndbBinaryFileSourceConfig cfg,
                                        String tableName,
                                        boolean isTableData,
                                        Logger logger) {
        return new OndbBinaryFileSource(toFilePath(file), cfg, tableName,
                                        isTableData, logger);
    }

    private List<DataSource> addObjectStoreSource(OndbBinaryFileSourceConfig cfg,
                                                  Logger logger) {

        ObjectStoreAPI objectStoreAPI =
            ObjectStoreAPIFactory.getObjectStoreAPI(cfg.getServiceName(),
                                                    cfg.getUserName(),
                                                    cfg.getPassword(),
                                                    cfg.getServiceUrl(),
                                                    cfg.getContainerName());

        /*
         * Check if the specified exists in Cloud Storage
         */
        objectStoreAPI.checkContainerExists();

        /*
         * Retrieve the input streams for all the table data files from Oracle
         * Storage Cloud Service. Custom metadata map is used to retrieve the
         * names of all the table data files
         */
        Map<String, String> customMetadata;
        try {
            customMetadata = objectStoreAPI.retrieveObjectMetadata(CHUNKS_KEY);
        } catch (RuntimeException re) {
            logger.log(Level.WARNING, "Custom metadata map not available in " +
                                      "the container. Import cant proceed " +
                                      "without this map.",
                       (re.getCause() != null ? re.getCause() : re));
            /* do nothing */
            return null;
        }

        final List<DataSource> sources = new ArrayList<DataSource>();
        DataSource ds;
        for (Map.Entry<String, String> entry : customMetadata.entrySet()) {
            String[] fileNameAndNumChunks = entry.getValue().split(",");
            String fileName = fileNameAndNumChunks[0].trim();

            boolean isTableFile = !fileName.equals(OTHER_DATA_FILE_PREFIX);

            String numChunksString = fileNameAndNumChunks[1].trim();
            Integer numChunks = Integer.parseInt(numChunksString);
            String filePrefix = fileName + "-" + fileName.length();

            /*
             * Retrieve the input stream for every segment of the file
             */
            Chunk chunk = new Chunk();
            for (int i = 0; i < numChunks; i++) {
                String nextChunkSequence = chunk.next();
                String fileSegmentName = filePrefix + "-" + nextChunkSequence;
                logger.info("Importing file segment: " + fileName +
                            ". Chunk sequence: " + nextChunkSequence);
                ds = new OndbBinaryFileSource(fileSegmentName, cfg,
                        (isTableFile ? fileName : null), isTableFile,
                        objectStoreAPI, logger);
                sources.add(ds);
            }
        }
        return sources;
    }
}
