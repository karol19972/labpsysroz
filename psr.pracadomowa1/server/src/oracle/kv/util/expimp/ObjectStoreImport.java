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

package oracle.kv.util.expimp;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.utils.Chunk;
import oracle.kv.util.expimp.utils.ObjectStoreAPI;
import oracle.kv.util.expimp.utils.ObjectStoreAPIFactory;

/**
 * An implementation class for AbstractStoreImport to import contents from
 * Oracle Storage Cloud Service to Oracle NoSql Store
 */
public class ObjectStoreImport extends AbstractStoreImport {

    /*
     * Key for the object holding name of all the file segments already exported
     */
    private String statusKey;

    /* kvstore name */
    private String storeName;

    private String[] helperHosts;

    /* The ObjectStoreAPI handle */
    private ObjectStoreAPI objectStoreAPI;

    /*
     * Stream for logging data
     */
    private ByteArrayOutputStream loggerOutput;

    public static final String importLogKey = "importLogKey";
    private Logger logger;

    public ObjectStoreImport(String storeName,
                             String[] helperHosts,
                             String kvUsername,
                             String kvSecurityFile,
                             int requestTimeoutMs,
                             String containerName,
                             String serviceName,
                             String userName,
                             String password,
                             String serviceURL,
                             String status,
                             boolean json) {

        super(storeName, helperHosts, kvUsername, kvSecurityFile,
              requestTimeoutMs, json);

        this.storeName = storeName;
        this.helperHosts = helperHosts;

        loggerOutput = new ByteArrayOutputStream();

        /*
         * Set handle to the logger
         */
        setLoggerHandler();

        /*
         * Initialize ObjectStoreAPI handler
         */
        try {
            objectStoreAPI = ObjectStoreAPIFactory
                                .getObjectStoreAPI(serviceName,
                                                   userName,
                                                   password,
                                                   serviceURL,
                                                   containerName);
        } catch (RuntimeException re) {
            exit(true, Utilities.getExitCode(re, ExitCode.EXIT_UNEXPECTED),
                 System.err, re.getMessage());
        }

        /*
         * Check if the specified exists in Cloud Storage
         */
        try {
            objectStoreAPI.checkContainerExists();
        } catch (RuntimeException re) {
            String msg = "Container " + containerName +
                         " not present in Oracle Storage Cloud Service.";
            logger.severe(msg);
            objectStoreAPI = null;
            exit(true, ExitCode.EXIT_NOEXPPACKAGE, System.err, msg);
        }

        statusKey = ((status != null) ? status + "key" : null);
    }

    /**
     * Loads all the file segment names. The names in the status file represents
     * all the file segments that are already present in the kvstore.
     * The import of all file segments present in the status file will be
     * skipped.
     */
    private void loadStatusFile() {

        InputStream statusStream = null;

        if (statusKey != null) {

            logger.info("Loading status file: " + statusKey);
            statusStream = retrieveObject(statusKey, true /* ignoreError */);
        }

        if (statusStream != null) {

            readStream(new ImportProcess(){

                @Override
                public void doProcessing(String line) {
                    loadStatusFile(line);
                }

            }, statusStream, null);
        }
    }

    /**
     * Imports all the schema definitions and data/metadata from the Oracle
     * Storage Cloud Service container to kvstore
     */
    @Override
    void doImport() {

        StringBuilder hhosts = new StringBuilder();

        for (int i = 0; i < helperHosts.length - 1; i++) {
            hhosts.append(helperHosts[i] + ",");
        }

        hhosts.append(helperHosts[helperHosts.length - 1]);

        logger.info("Starting import of export package " +
            "to store " + storeName + ", helperHosts=" + hhosts.toString());

        /*
         * Imports all the schema definitions to kvstore
         */
        importSchemaDefinition(null);

        /*
         * Loads the status file
         */
        loadStatusFile();

        /*
         * Retrieve the custom metadata map. It provides a mapping
         * between the file name and the number of segments of the file
         */
        String chunksKey = "chunksKey";
        Map<String, String> customMetadata =
            retrieveObjectMetadata(chunksKey, true /* ignoreError */);
        /*
         * Return if custom metadata map not found
         */
        if (customMetadata == null) {
            logger.severe("Custom metadata map not available in the " +
                          "container. Import cant proceed without this map.");

            return;
        }

        logger.info("Importing data into KVStore.");

        String otherDataFile = "OtherData";
        String otherDataPrefix = otherDataFile + "-" +
                                 otherDataFile.length();

        InputStream in = null;
        BufferedInputStream bin = null;

        /*
         * Retrieve the input streams for all the data files from Oracle
         * Storage Cloud Service. Custom metadata map is used to retrieve the
         * names of all the data files
         */
        for (Map.Entry<String, String> entry : customMetadata.entrySet()) {

            String[] fileNameAndNumChunks = entry.getValue().split(",");
            String fileName = fileNameAndNumChunks[0].trim();
            String numChunksString = fileNameAndNumChunks[1].trim();
            Integer numChunks = Integer.parseInt(numChunksString);

            String filePrefix = fileName + "-" + fileName.length();

            Chunk chunk = new Chunk();

            boolean isTableData = !filePrefix.equals(otherDataPrefix);

            /*
             * Retrieve the input stream for every segment of the file
             */
            for (int i = 0; i < numChunks; i++) {

                String nextChunkSequence = chunk.next();
                String fileSegmentName = filePrefix + "-" + nextChunkSequence;

                logger.info("Importing file segment: " + fileName +
                            " . Chunk sequence: " + nextChunkSequence);

                in = retrieveObject(fileSegmentName, true /* ignoreError */);

                if (in == null) {
                    continue;
                }

                bin = new BufferedInputStream(in);

                populateEntryStreams(bin,
                                     fileName,
                                     nextChunkSequence,
                                     isTableData);
            }
        }

        /*
         * Perform bulk put. This does the actual work of importing data
         * from the file segment entry streams to kvstore
         */
        performBulkPut();
        doPostImportWork();

        try {
            if (bin != null) {
                bin.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception closing stream.", e);
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception closing stream.", e);
        }

        String impCompleteMsg = "Completed importing export package to store " +
            storeName + ", helperHosts=" + hhosts.toString();
        logger.info(impCompleteMsg);

        exit(true, ExitCode.EXIT_OK, System.out, impCompleteMsg);
    }

    /**
     * Import the schema definitions and the data of the requested tables.
     *
     * @param tableFilter a filter that returns true for all the tables whose
     *        schemas and data needs to be imported from Oracle Storage Cloud
     *        Service to kvstore
     */
    @Override
    void doTableImport(TableFilter tableFilter) {

        StringBuilder hhosts = new StringBuilder();

        for (int i = 0; i < helperHosts.length - 1; i++) {
            hhosts.append(helperHosts[i] + ",");
        }

        hhosts.append(helperHosts[helperHosts.length - 1]);

        logger.info("Starting import of tables from export package to store " +
            storeName + ", helperHosts=" + hhosts.toString());

        /*
         * Import the requested tables schema definitions
         */
        importSchemaDefinition(tableFilter);

        /*
         * Ignore all the table names which are not found in the Oracle Storage
         * Cloud Service. Log the message.
         */
        if (tableFilter.getUnmatched() != null) {
            for (String table : tableFilter.getUnmatched()) {
                logger.warning("Schema definition for Table: " + table +
                               " not found in the export package.");
            }
        }

        /*
         * Load the status file
         */
        loadStatusFile();

        logger.info("Importing table data into KVStore.");

        /*
         * Retrieve the custom metadata map. It provides a mapping
         * between the file name and the number of segments of the file
         */
        String chunksKey = "chunksKey";
        Map<String, String> customMetadata =
            retrieveObjectMetadata(chunksKey, true /* ignoreError */);
        if (customMetadata == null) {

            logger.severe("Custom metadata map not available in the " +
                          "container. Import cant proceed without this map.");

            return;
        }

        String otherDataFile = "OtherData";

        BufferedInputStream bin = null;
        InputStream in = null;
        /*
         * Retrieve the input streams for all the table data files from Oracle
         * Storage Cloud Service. Custom metadata map is used to retrieve the
         * names of all the table data files
         */
        for (Map.Entry<String, String> entry : customMetadata.entrySet()) {

            String[] fileNameAndNumChunks = entry.getValue().split(",");
            String fileName = fileNameAndNumChunks[0].trim();

            if (fileName.equals(otherDataFile) ||
                    !tableFilter.matches(fileName)) {

                continue;
            }

            String numChunksString = fileNameAndNumChunks[1].trim();
            Integer numChunks = Integer.parseInt(numChunksString);

            String filePrefix = fileName + "-" + fileName.length();

            Chunk chunk = new Chunk();

            /*
             * Retrieve the input stream for every segment of the file
             */
            for (int i = 0; i < numChunks; i++) {

                String nextChunkSequence = chunk.next();
                String fileSegmentName = filePrefix + "-" + nextChunkSequence;

                logger.info("Importing table file segment: " + fileName +
                            " . Chunk sequence: " + nextChunkSequence);

                in = retrieveObject(fileSegmentName, true /* ignoreError */);

                if (in == null) {
                    continue;
                }

                bin = new BufferedInputStream(in);

                populateEntryStreams(bin,
                                     fileName,
                                     nextChunkSequence,
                                     true);
            }
        }

        /*
         * Perform bulk put. This does the actual work of importing data
         * from the file segment entry streams to kvstore
         */
        performBulkPut();
        doPostImportWork();

        try {
            if (bin != null) {
                bin.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception closing stream.", e);
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception closing stream.", e);
        }

        String impCompleteMsg = "Completed import of tables from export " +
            "package to store " + storeName + ", helperHosts=" +
            hhosts.toString();
        logger.info(impCompleteMsg);

        exit(true, ExitCode.EXIT_OK, System.out, impCompleteMsg);
    }

    /**
     * Gets the given LOB input stream from Oracle Storage Cloud Service
     *
     * @param lobFileName name of the lob file in Oracle Storage Cloud Service
     */
    @Override
    InputStream getLobInputStream(String lobFileName) {
        String prefixKey = lobFileName + "-" + lobFileName.length();
        String objectKey = prefixKey + "-ManifestKey";
        return retrieveObject(objectKey, true /* ignoreError */);
    }

    /**
     * Flushes all the logging data
     */
    @Override
    void flushLogs() {
        if (objectStoreAPI == null) {
            return;
        }

        InputStream stream =
            new ByteArrayInputStream(loggerOutput.toByteArray());

        storeObject(importLogKey, stream, true /* ignoreError */);
    }

    /**
     * Sets the log handler
     */
    @Override
    void setLoggerHandler(Logger logger) {

        this.logger = logger;
        Utilities.setStreamHandler(logger, loggerOutput);
    }

    /**
     * Imports schema definitions from Oracle Storage Cloud Service to kvstore
     *
     * @param tableFilter a filter that returns true for all the tables whose
     *        schema definitions needs to be imported.
     *        If null, imports all the table schema definitions
     *        available in Oracle Storage Cloud Service
     */
    void importSchemaDefinition(TableFilter tableFilter) {

        String schemaFile = "SchemaDefinition";
        String prefixKey = schemaFile + "-" + schemaFile.length();

        /*
         * Key holding schema definition in Oracle Storage Cloud Service
         */
        String schemaDefinitionKey = prefixKey + "-ManifestKey";

        InputStream stream = retrieveObject(schemaDefinitionKey,
                                            true /* ignoreError */);

        if (stream != null) {

            readStream(new ImportProcess(){

                @Override
                public void doProcessing(String line) {

                    /*
                     * Import the table schema
                     */
                    if (tableFilter == null) {
                        importSchema(line);
                    }

                    /*
                     * Import the table schema if the table is in
                     * tableNames list
                     */
                    else {
                        importTableSchema(line, tableFilter);
                    }
                }
            }, stream, tableFilter);
        }
    }

    /**
     * Read and process the StorageInputStream line by line
     *
     * @param ip interface that defines how to process the line from the file
     * @param stream input stream for data from oracle storage cloud service
     * @param list if null process all the lines. Else process only if the line
     *        represents a table and is present in the list
     */
    private void readStream(ImportProcess ip,
                            InputStream stream,
                            TableFilter tableFilter) {

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(stream));
            String line = reader.readLine();

            while (line != null) {

                ip.doProcessing(line);
                line = reader.readLine();

                /*
                 * Break from the loop after the list has been exhausted
                 */
                if (tableFilter != null && tableFilter.isDone()) {
                    break;
                }
            }

        } catch (IOException ioe) {

            logger.log(Level.SEVERE, "IOException: ", ioe);
        } finally {

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {

                logger.log(Level.SEVERE, "IOException: ", e);
            }

            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {

                logger.log(Level.SEVERE, "IOException: ", e);
            }
        }
    }

    /**
     * Interface that defines how to process the line from StorageInputStream
     */
    interface ImportProcess {
        public void doProcessing(String line);
    }

    /**
     * Write all the file segments that have been successfully imported to
     * Oracle Storage Cloud Service reference by the status key
     */
    @Override
    void writeStatusFile(Set<String> fileSegments) {

        if (statusKey != null && fileSegments != null &&
                !fileSegments.isEmpty()) {

            logger.info("Writing the status file to oracle object store");

            StringBuffer sb = new StringBuffer();

            for (String segment : fileSegments) {
                sb.append(segment).append("\n");
            }

            InputStream stream =
                new ByteArrayInputStream(sb.toString()
                    .getBytes(StandardCharsets.UTF_8));

            storeObject(statusKey, stream, true /* ignoreError */);
        }
    }

    /**
     * Store an object to Oracle Storage Cloud Service
     *
     * @param key referencing key for the object
     * @param ignoreError the flag indicates if ignore error when failed to
     *        store object
     * @param stream holds the object data
     */
    private boolean storeObject(String key,
                                InputStream in,
                                boolean ignoreError) {
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

    /**
     * Retrieve the object from Oracle Storage Cloud Service referenced by the
     * object key
     *
     * @param key referencing key for the object
     * @param ignoreError the flag indicates if ignore error when failed to
     *        retrieve object
     * @return input stream for the object data
     */
    private InputStream retrieveObject(String key, boolean ignoreError) {
        try {
            return objectStoreAPI.retrieveObject(key);
        } catch (RuntimeException re) {
            if (ignoreError) {
                logger.log(Level.WARNING, re.getMessage(),
                           (re.getCause() != null ? re.getCause() : re));
                /* do nothing */
                return null;
            }
            throw re;
        }
    }

    /**
     * Retrieve the object's metadata from Oracle Storage Cloud Service
     * referenced by the object key
     *
     * @param key referencing key for the object
     * @param ignoreError the flag if ignore error when failed to retrieve the
     *        object's metadata
     * @return the object's metadata
     */
    private Map<String, String> retrieveObjectMetadata(String key,
                                                       boolean ignoreError) {
        try {
            return objectStoreAPI.retrieveObjectMetadata(key);
        } catch (RuntimeException re) {
            if (ignoreError) {
                logger.log(Level.WARNING, re.getMessage(),
                           (re.getCause() != null ? re.getCause() : re));
                /* do nothing */
                return null;
            }
            throw re;
        }
    }
}
