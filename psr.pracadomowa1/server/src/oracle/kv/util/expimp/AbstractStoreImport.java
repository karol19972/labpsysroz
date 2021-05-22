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
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import oracle.kv.BulkWriteOptions;
import oracle.kv.EntryStream;
import oracle.kv.FaultException;
import oracle.kv.KVSecurityException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.Key;
import oracle.kv.KeyValueVersion;
import oracle.kv.LoginCredentials;
import oracle.kv.StatementResult;
import oracle.kv.UnauthorizedException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.FieldComparator;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.security.util.KVStoreLogin;
import oracle.kv.impl.security.util.KVStoreLogin.CredentialsProvider;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.table.RecordDef;
import oracle.kv.table.TableAPI;
import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.Utilities.TableNameComparator;
import oracle.kv.util.expimp.utils.DataSerializer;
import oracle.kv.util.expimp.utils.DataSerializer.DataCorruptedException;
import oracle.kv.util.expimp.utils.DataSerializer.KeyValueBytes;
import oracle.kv.util.expimp.utils.imp.StoreImportHandler;
import oracle.kv.util.expimp.utils.imp.StoreImportHandler.TableDataTransformer;

/**
 * Abstract implementation of Import functionality which is used to move data
 * from the export stores to Oracle NoSql database. Currently the import utility
 * supports Oracle Storage Cloud Service and Local File System as a valid
 * export store.
 *
 * For performing import, this utility will consume the export package generated
 * by the export utility. Import utility will not create any target Oracle NoSql
 * store. Its users responsibility to ensure that a target Oracle NoSql store
 * is created before performing an import against the store.
 *
 * Using the import utility:
 * 1) The user can import all the schema definitions and the data (table,
 * none format data) from the export store to the Oracle NoSql store.
 * The schema definitions include the table definitions and the index
 * definitions. The import utility first imports all schema definitions and
 * then imports the user data into the Oracle NoSql store.
 * 2) The user can import individual table in which case the corresponding table
 * schemas, index definitions and the table data will get imported.
 * 3) The TTL of the record is imported.
 *
 * The store need not be quiescent when import utility is run. It is possible
 * that the user runs the Import utility on a non empty Oracle NoSql store. For
 * this reason, the import utility will first check if the table definition is
 * already present in the nosql store. Only if the table is not already present
 * in the target nosql store, the Import utility will create the table in the
 * store. Indexes are created after the tables are created. If the index field
 * is missing in the table definition, the corresponding index creation is
 * skipped. The application data (table, none format data) is then imported
 * into the Oracle NoSql store. While importing a table data, if the
 * structure of the data does not match the structure of the table schema
 * (example: table primary key incompatibility), the corresponding table data
 * is not imported and is captured in reject-record-file.
 *
 * Exit code can be used as a diagnostic to determine the state of import. The
 * exit codes have the following meaning:
 *
 * 0   (EXIT_OK) -- No errors found during import.
 * 100 (EXIT_USAGE) -- Illegal import command usage.
 * 101 (EXIT_NOPERM) -- Unauthorized access to the Oracle Cloud Storage Service
 * 102 (EXIT_EXPSTR_NOCONNECT) -- The Oracle Cloud Storage Service could not be
 *      accessed using the service connection parameters
 * 103 (EXIT_NOCONNECT) -- The source NoSql store could not be connected using
 *      the given store-name and helper hosts.
 * 104 (EXIT_UNEXPECTED) -- The import utility experienced an unexpected error.
 * 106 (EXIT_NOREAD) -- The export package has no read permissions.
 * 112 (EXIT_NOEXPPACKAGE) -- The export package needed for import not found in
 *      the path provided. For Oracle CloudStorage Service, this means the
 *      required container not found
 * 109 (EXIT_INVALID_EXPORT_STORE) -- Invalid export store type. Valid export
 *      stores are local and object_store
 * 110 (EXIT_SECURITY_ERROR) -- Error loading security file.
 */
public abstract class AbstractStoreImport implements CredentialsProvider {

    private KVStore store;
    private final TableAPI tableAPI;

    /*
     * General utility used to convert table schema definitions and table
     * index definitions into table DDLs and table index DDLs.
     */
    private final JSONToDDL jsonToDDL;

    /*
     * Mapping between the table full name and the table instance.
     */
    private final Map<String, TableImpl> tableMap;

    /*
     * Mapping between the table full name and its parent table full name
     */
    private final Map<String, String> tableParent;

    /*
     * Mapping between the table full name and its value RecordDef object
     */
    private final Map<String, RecordDef> tableWriteValueDefs;

    private final Set<String> mismatchKeySchemaTables;

    /*
     * Retry count to load ddls
     */
    private static final int RETRY_COUNT = 10;

    /*
     * Sleep time period before trying to load the ddl
     */
    private static final int RETRY_SLEEP_PERIOD = 5000;

    /*
     * The TTL will get imported relative to this date which is chosen by the
     * user if ttlImportType is 'RELATIVE'. This value is null if ttlImportType
     * is 'ABSOLUTE'. Date should of the format YYYY-MM-DD HH:MM:SS and is
     * always in UTC.
     */
    private String importTtlRelativeDate;
    private long referenceTimeMs = 0;

    /*
     * Bulk put tuning parameters
     */
    private int streamParallelism;
    private int perShardParallelism;
    private int bulkHeapPercent;

    /* Request timeout in MS */
    private final int requestTimeoutMs;

    /*
     * The store import handler
     */
    private final StoreImportHandler storeImport;

    private static enum ImportState {
        RECORD_IMPORTED,
        FILE_SCAN_COMPLETE,
        RECORD_SKIPPED
    }

    /*
     * The exported files present in the export package are segmented in chunks
     * of 1GB. This set keeps track of all the file segments that have been
     * successfully imported into the target kvstore. This is used to aid in
     * resuming import in case of a failure. On an import retry, all the file
     * segments that have already been imported will be skipped.
     */
    private final Set<String> loadedFileSegments;

    /*
     * List of entry streams used by the BulkPut API used to import all the
     * data from export package into the target kvstore.
     */
    private final List<EntryStream<KeyValueVersion>> fileStreams;

    /*
     * Hook to induce schema changes. Used for testing
     */
    public TestHook<String> CHANGE_HOOK;

    /*
     * Logger variables
     */
    private final Logger logger;
    private final LogManager logManager;

    /*
     * Boolean variable which determines if the import output from CLI should
     * be in json format
     */
    private final boolean json;

    /*
     * For test purpose
     */
    public static boolean printToConsole = true;

    /*
     * Test flag
     */
    public static boolean testFlag = false;

    /*
     * Variables used only for testing purpose
     */
    private static final String testTableName = "Table1.Table2.Table3";
    private static final String testTableChunkSequence = "abcdefghijlk";

    /*
     *  Use for authentication
     */
    private KVStoreLogin storeLogin;
    private LoginCredentials loginCreds;

    class CustomLogManager extends LogManager {
        CustomLogManager() {
            super();
        }
    }

    /**
     * Exit import with the appropriate exit code.
     *
     * @param resetLogHandler if true resets the log handler.
     * @param exitCode
     * @param ps
     * @param message message to be dispalyed in CLI output on exit
     */
    protected void exit(boolean resetLogHandler,
                        ExitCode exitCode,
                        PrintStream ps,
                        String message) {

        if (resetLogHandler) {
            flushLogs();
            logManager.reset();
        }

        if (storeImport != null) {
            storeImport.close();
        }

        exit(exitCode, ps, message, getJson());
    }

    protected static void exit(ExitCode exitCode,
                               PrintStream ps,
                               String message,
                               boolean outputAsJson) {

        Utilities.exit(exitCode, ps, message, outputAsJson,
                       "import", printToConsole);
    }


    public AbstractStoreImport(String storeName,
                               String[] helperHosts,
                               String userName,
                               String securityFile,
                               int requestTimeoutMs,
                               boolean json) {

        this.json = json;
        logger = Logger.getLogger(AbstractStoreExport.class.getName() +
                                  (int)(Math.random()*1000));
        logger.setUseParentHandlers(false);

        logManager = new CustomLogManager();
        logManager.reset();
        logManager.addLogger(logger);

        prepareAuthentication(userName, securityFile);

        KVStoreConfig kconfig = new KVStoreConfig(storeName, helperHosts);
        kconfig.setSecurityProperties(storeLogin.getSecurityProperties());

        if (requestTimeoutMs > 0) {
            this.requestTimeoutMs = requestTimeoutMs;
            if (requestTimeoutMs >
                kconfig.getSocketReadTimeout(TimeUnit.MILLISECONDS)) {
                kconfig.setSocketReadTimeout(requestTimeoutMs,
                                             TimeUnit.MILLISECONDS);
            }
        } else {
            this.requestTimeoutMs = 0;
        }

        try {
            store = KVStoreFactory.getStore(kconfig, loginCreds,
                KVStoreLogin.makeReauthenticateHandler(this));
        } catch (IllegalArgumentException iae) {
            exit(false, ExitCode.EXIT_NOCONNECT, System.err, null);
        } catch (KVSecurityException kse) {
            exit(false, ExitCode.EXIT_NOCONNECT, System.err, null);
        } catch (FaultException fe) {
            exit(false, ExitCode.EXIT_NOCONNECT, System.err, null);
        }

        tableAPI = store.getTableAPI();

        tableMap = new HashMap<String, TableImpl>();
        tableParent = new HashMap<String, String>();
        tableWriteValueDefs = new HashMap<String, RecordDef>();
        mismatchKeySchemaTables = new HashSet<String>();
        jsonToDDL = new JSONToDDL(tableAPI, this);
        loadedFileSegments = new HashSet<String>();
        importTtlRelativeDate = null;

        /*
         * Default values for bulk put tuning parameters
         */

        /*
         * Assuming a ideal state of 40Gbit downlink and 20 concurrent streams,
         * this configuration gives good performance which is theoretically
         * 40Gbit / (8 bits * 20 streams) = 25MB/sec. Depending on the actual
         * configuration in cloud object storage, this parameter can be tuned
         * via the config file.
         */
        streamParallelism = 20;

        /*
         * Bulk put performance tests had the best performance result with
         * Shardparallelism = 3, heap percentage = 40%.
         */
        perShardParallelism = 3;
        bulkHeapPercent = 40;

        fileStreams = new ArrayList<EntryStream<KeyValueVersion>>();

        storeImport = new StoreImportHandler(store, logger);
    }

    public boolean getJson() {
        return json;
    }

    /**
     * Set the relative date for TTL when the TTL import type is 'RELATIVE'
     */
    public void setImportTtlRelativeDate(String importTtlRelativeDate) {
        this.importTtlRelativeDate = importTtlRelativeDate;
    }

    /**
     * Set the bulk put streamParallelism parameter
     * @see BulkWriteOptions
     */
    public void setStreamParallelism(int streamParallelism) {
        this.streamParallelism = streamParallelism;
    }

    /**
     * Set the bulk put perShardParallelism parameter
     * @see BulkWriteOptions
     */
    public void setPerShardParallelism(int perShardParallelism) {
        this.perShardParallelism = perShardParallelism;
    }

    /**
     * Set the bulk put heap percent parameter
     * @see BulkWriteOptions
     */
    public void setBulkHeapPercent(int bulkHeapPercent) {
        this.bulkHeapPercent = bulkHeapPercent;
    }

    /**
     * Get the reference time for ttl that will be used during import
     */
    private long getReferenceTime() {

        if (referenceTimeMs != 0) {
            return referenceTimeMs;
        }

        referenceTimeMs = System.currentTimeMillis();

        if (importTtlRelativeDate != null) {
            SimpleDateFormat formatter =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            formatter.setLenient(false);

            try {
                Date referenceDate = formatter.parse(importTtlRelativeDate);
                referenceTimeMs = referenceDate.getTime();
            } catch (ParseException e) {
                /*
                 * The check for valid format of the relative date should be
                 * done when the config.xml file is parsed in
                 * ExportImportMain.java, so a format error here is unexpected.
                 */
                throw new IllegalStateException("Invalid date format: " +
                                                importTtlRelativeDate +
                                                ": " + e);
            }
        }

        return referenceTimeMs;
    }

    /**
     * Check and set SSL connection
     */
    private void prepareAuthentication(final String user,
                                       final String securityFile) {

        storeLogin = new KVStoreLogin(user, securityFile);
        try {
            storeLogin.loadSecurityProperties();
        } catch (IllegalArgumentException iae) {
            exit(false, ExitCode.EXIT_SECURITY_ERROR, System.err, null);
        }

        /* Needs authentication */
        if (storeLogin.foundTransportSettings()) {
            try {
                loginCreds = storeLogin.makeShellLoginCredentials();
            } catch (IOException e) {
                exit(false, ExitCode.EXIT_SECURITY_ERROR, System.err, null);
            }
        }
    }

    @Override
    public LoginCredentials getCredentials() {
        return loginCreds;
    }

    /**
     * Sets the handler for the logger
     */
    protected void setLoggerHandler() {
        setLoggerHandler(logger);
    }

    /**
     * Import the table schema definition into the target kvstore.
     *
     * @param schemaJsonString
     */
    protected void importSchema(String schemaJsonString) {

        /*
         * Get the schema type of the schema definition, import table
         * definition represented with 'T'.
         */
        String schemaType =
            schemaJsonString
            .substring(0, schemaJsonString.indexOf(",")).trim();

        String jsonString =
            schemaJsonString
            .substring(schemaJsonString.indexOf(",") + 1,
                       schemaJsonString.length()).trim();

        switch (schemaType) {
            case "T": importTableSchema(jsonString);
                      break;

            default : break;
        }
    }

    /**
     * Imports the table schema definition and all the tables index definitions
     * into the target kvstore
     *
     * Format of the table schemaJsonString:
     * [T, TableFullNamespaceName: TableJsonSchema]
     */
    private void importTableSchema(String tableJsonString) {

        /*
         * Get the table name from the json string
         */
        int schemaPos = tableJsonString.indexOf("{");
        String tableName =
            tableJsonString.substring(
                0, tableJsonString.lastIndexOf(":", schemaPos)).trim();


        logger.info("Importing Table and index schema definitions for " +
                    tableName);

        /*
         * The table is not present in the target kvstore. Import the table
         * schema definition and all its index definitions.
         */
        String tableSchema =
            tableJsonString.substring(schemaPos,
                                      tableJsonString.length()).trim();

        importTableSchema(tableSchema, tableName);
    }

    /**
     * Imports the table schema definition and all the tables index definitions
     * into the target kvstore
     *
     * Format of the table schemaJsonString:
     * [T, TableFullNamespaceName: TableJsonSchema]
     *
     * @param schemaJsonString The table schema definition. It also contains
     * all its index definitions.
     * @param tableFilter a filter that returns true for the tables to import.
     * Import the table schema definition only if the table is present in this
     * list.
     */
    protected void importTableSchema(String schemaJsonString,
                                     TableFilter tableFilter) {
        /*
         * Get the schema type of the schema definition, import table
         * definition represented with 'T'.
         */
        String schemaType =
            schemaJsonString.substring(0, schemaJsonString.indexOf(",")).trim();

        if (!schemaType.equals("T")) {
            return;
        }

        /*
         * Table schema definition found. Retrieve the table name.
         */
        String jsonString =
            schemaJsonString.substring(schemaJsonString.indexOf(",") + 1,
                                       schemaJsonString.length()).trim();

        int schemaPos = jsonString.indexOf("{");
        String tableName =
            jsonString.substring(
                0, jsonString.lastIndexOf(":", schemaPos)).trim();

        /*
         * tableFilter will return true for all tables the user wants to
         * import. If this table is not in this list, don't import this table
         * schema definition.
         */
        if (!tableFilter.matches(tableName)) {
            return;
        }

        logger.info("Importing Table and index schema definitions for " +
                    tableName);

        /*
         * The table is not present in the target kvstore. Import the table
         * schema definition and all its index definitions.
         */
        String tableSchema =
            jsonString.substring(schemaPos, jsonString.length()).trim();

        importTableSchema(tableSchema, tableName);
    }

    /**
     * Given the table name and the json schema definition, import all the table
     * DDls and the index DDLs.
     *
     * @param jsonSchema The actual table schema in json format. It also
     * contains all the table index definitions.
     * @param tableName
     */
    private void importTableSchema(String jsonSchema, String tableName) {

        /*
         * Get all the table and its index DDL definitions. The first entry
         * in this list is the table DDL followed by all the tables index
         * DDLs
         */
        List<String> ddlSchemas = jsonToDDL.getTableDDLs(jsonSchema, tableName);

        if (ddlSchemas == null) {
            /*
             * Cant load DDL for table due to one of the following:
             * 1) The table is already present.
             * 2) Parent table(s) needs to be loaded first.
             * 3) The parent table keys are missing in the child table json
             *    schema definition.
             */
            return;
        }

        StatementResult result = null;
        boolean loadingTable = true;

        String namespace = NameUtils.getNamespaceFromQualifiedName(tableName);
        ExecuteOptions options = (namespace == null) ? null:
            new ExecuteOptions().setNamespace(namespace, false);
        /*
         * First load table DDL and then its index DDLs
         */
        for (String ddlSchema : ddlSchemas) {

            /*
             * Retry a few times on failure
             */
            for (int i = 0; i < RETRY_COUNT; i++) {
                try {
                    result = store.executeSync(ddlSchema, options);
                    displayResult(result, ddlSchema);

                    if (loadingTable && result.isSuccessful()) {

                        tableMap.put(tableName,
                                     (TableImpl)tableAPI.getTable(tableName));
                        loadingTable = false;
                    }

                    break;
                } catch (IllegalArgumentException e) {
                    logger.log(Level.SEVERE,
                        "Invalid statement: " + ddlSchema, e);

                    break;
                } catch (UnauthorizedException ue) {
                    logger.log(Level.WARNING,
                        "User does not have sufficient privileges to " +
                        "create the schema: " + ddlSchema);

                    break;
                } catch (FaultException | KVSecurityException e) {
                    if (i == (RETRY_COUNT - 1)) {
                        logger.log(Level.SEVERE,
                            "Statement couldn't be executed: " + ddlSchema, e);
                    } else {
                        try {
                            logger.log(Level.WARNING,
                                "Loading ddl failed. Retry #" +
                                (i + 1) + ": " + ddlSchema);
                            Thread.sleep(RETRY_SLEEP_PERIOD);
                        } catch (InterruptedException e1) {
                            logger.log(Level.SEVERE,
                                "Exception loading ddl: " + ddlSchema, e1);
                        }
                    }
                }
            }
        }
    }

    /**
     * General utility to check if a table schema has already been imported
     * in the target kvstore
     *
     * @param tableName
     * @return true of table schema already imported. False otherwise.
     */
    protected boolean isTableSchemaImported(String tableName) {

        if (tableMap.containsKey(tableName)) {
            return true;
        }

        return false;
    }

    /**
     * Display the status of table and index DDL execution.
     *
     * @param result The result of table/index DDL statement execution
     * @param statement The actual table/index DDL statement
     */
    private void displayResult(StatementResult result, String statement) {

        StringBuilder sb = new StringBuilder();

        if (result.isSuccessful()) {

            sb.append("Statement was successful:\n\t" + statement + "\n");
            sb.append("Results:\n\t" + result.getInfo() + "\n");
        } else if (result.isCancelled()) {

            sb.append("Statement was cancelled:\n\t" + statement + "\n");
        } else {

            if (result.isDone()) {

                sb.append("Statement failed:\n\t" + statement + "\n");
                sb.append("Problem:\n\t" + result.getErrorMessage() + "\n");
            } else {

                sb.append("Statement in progress:\n\t" + statement + "\n");
                sb.append("Status:\n\t" + result.getInfo() + "\n");
            }
        }

        logger.info(sb.toString());
    }

    /**
     * Create Entry Streams from multiple data file segments in the export
     * package. Add each of the EntryStream into the list holding all the
     * EntryStreams. This method is called for each of the file segments present
     * in the export package.
     *
     * @param in Input stream for the file segment in the export store
     * @param fileName name of the file in the export store
     * @param chunkSequence The file stored in export store is segmented into
     *        many chunks. FileName + chunksequence uniquely identifies a file
     *        segment
     * @param isTableData true if the file contains table data. False otherwise
     */
    protected void populateEntryStreams(BufferedInputStream in,
                                        String fileName,
                                        String chunkSequence,
                                        boolean isTableData) {

        String fileSegmentName = fileName + "-" + chunkSequence;

        /*
         * Check if the file segment from the export store is already imported
         */
        if (isLoaded(fileSegmentName)) {

            logger.info("File segment " + fileName + " with chunk sequence " +
                        chunkSequence + " already loaded in the target " +
                        "KVStore.");
            return;
        }

        /*
         * Create an entry stream to read the key/value data from the file
         * segment in the export package
         */
        FileEntryStream stream =
            new FileEntryStream(in, fileName, chunkSequence, isTableData);

        /*
         * Populate the KV EntryStreams needed for bulk put
         */
        fileStreams.add(stream);
    }

    public void addTableParent(String cTableName, String pTableName) {
        tableParent.put(cTableName, pTableName);
    }

    public void addTableMap(String tableName, TableImpl tableImpl) {
        tableMap.put(tableName, tableImpl);
    }

    public void addKeyMismatchTable(String tableName) {
        mismatchKeySchemaTables.add(tableName);
    }

    /**
     * BulkPut capability is used to provide a high performance backend for the
     * Import process. This method is called after populateEntryStreams is
     * called for all the file segments in the export package.
     */
    protected void performBulkPut() {

        logger.info("Performing Bulk Put of data.");

        try {
            if (fileStreams != null && !fileStreams.isEmpty()) {
                BulkWriteOptions writeOptions =
                    new BulkWriteOptions(null, 0, null);

                /*
                 * Sets the maximum number of streams that can be read
                 * concurrently by bulk put
                 */
                writeOptions.setStreamParallelism(streamParallelism);

                /*
                 * Sets the maximum number of threads that can concurrently
                 * write it's batch of entries to a single shard in the
                 * store
                 */
                writeOptions.setPerShardParallelism(perShardParallelism);

                writeOptions.setBulkHeapPercent(bulkHeapPercent);

                if (requestTimeoutMs > 0) {
                    writeOptions.setTimeout(requestTimeoutMs,
                                            TimeUnit.MILLISECONDS);
                }

                logger.info("Importing data with configuration: " +
                            "streamParallelism=" +
                            writeOptions.getStreamParallelism() +
                            "; perShardParallelism=" +
                            writeOptions.getPerShardParallelism() +
                            "; bulkHeapPercent=" +
                            writeOptions.getBulkHeapPercent() +
                            "; requestTimeout=" +
                            writeOptions.getTimeout() + "ms");

                KVStoreImpl storeImpl = (KVStoreImpl)store;

                /*
                 * Import all the data from the export store into the target
                 * kvstore using bulk put.
                 */
                storeImpl.put(fileStreams, getReferenceTime(), writeOptions);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception in bulk put.", e);
        }
    }

    /**
     * Return a number present in the FileEntryStream. If a number is not
     * found, NumberFormatException is thrown.
     */
    public long getNumber(BufferedInputStream bin,
                          String fileName,
                          String chunkSequence) throws Exception {

        List<Integer> byteList = new ArrayList<Integer>();

        while (true) {
            int l = bin.read();

            if (l == -1) {
                return -1;
            }

            if (l == 32) {
                break;
            }
            byteList.add(l);
        }

        byte[] numberBytes = new byte[byteList.size()];
        int x = 0;

        for (int l : byteList) {
            numberBytes[x] = (byte)l;
            x++;
        }

        String numberString = new String(numberBytes,
                                         StandardCharsets.UTF_8);
        long number = 0;

        try {
            number = Long.parseLong(numberString);
        } catch (NumberFormatException nfe) {

            logger.log(Level.SEVERE, "Cant parse number. File segment " +
                       fileName + "-" + chunkSequence +
                       " seems to be corrupted.", nfe);

            /*
             * If number cant be obtained while parsing the file segments,
             * Exception is thrown which is caught by getNextTableData()/
             * getNextKVData() methods
             */
            throw new Exception();
        }

        return number;
    }

    /**
     * The stream that supplies the data (Key/Value pair) to be batched and
     * loaded into the store.
     */
    class FileEntryStream implements EntryStream<KeyValueVersion> {

        /*
         * The file segment being imported
         */
        private final String fileName;

        /*
         * True if this file segment contains table data
         */
        private final boolean isTableData;

        /*
         * Identifies particular segment of the file in the export store that is
         * being imported
         */
        private final String chunkSequence;

        private boolean isCompleted;
        private KeyValueVersion kv;

        private final DataInputStream dis;
        private TableImpl table;
        private RecordDef tableWriteDef;
        private TableDataTransformer tableDataTrans;

        public FileEntryStream(BufferedInputStream bin,
                               String fileName,
                               String chunkSequence,
                               boolean isTableData) {

            this.fileName = fileName;
            this.chunkSequence = chunkSequence;
            this.isCompleted = true;
            this.isTableData = isTableData;

            dis = new DataInputStream(bin);
            if (isTableData) {
                table = tableMap.get(fileName);
                tableWriteDef = tableWriteValueDefs.get(fileName);
                tableDataTrans = new TableDataTransformer(table, tableWriteDef, false);
            }
        }

        @Override
        public String name() {
            return fileName;
        }

        /*
         * Returns the next entry in the stream holding non-table data or null
         * if at the end of the stream
         */
        @Override
        public KeyValueVersion getNext() {

            if (isTableData) {
                ImportState retNum = getNextTableData();

                /*
                 * Record is either expired or the checksum of record does not
                 * match
                 */
                if (retNum == ImportState.RECORD_SKIPPED) {
                    do {
                        retNum = getNextTableData();
                    } while (retNum == ImportState.RECORD_SKIPPED);
                }

                return retNum == ImportState.RECORD_IMPORTED ? kv : null;
            }

            ImportState retNum = getNextKV();

            /*
             * Record is either expired or the checksum of record does not
             * match
             */
            if (retNum == ImportState.RECORD_SKIPPED) {
                do {
                    retNum = getNextKV();
                } while (retNum == ImportState.RECORD_SKIPPED);
            }

            return retNum == ImportState.RECORD_IMPORTED ? kv : null;
        }

        private ImportState getNextTableData() {

            try {
                if (testFlag && fileName.equals(testTableName) &&
                    chunkSequence.contains(testTableChunkSequence)) {

                    testFlag = false;
                    throw new Exception("Test Exception");
                }

                if (mismatchKeySchemaTables.contains(table.getFullName())) {
                    String msg = "Mismatch between the primary key of the " +
                        "table schema in the target nosql store and the " +
                        "table data being imported. Skipping the record.";

                    throw new Exception(msg);
                }

                KeyValueBytes kvb = null;
                try {
                    kvb = DataSerializer.readTableData(dis,
                            fileName + "-" + chunkSequence);
                    if (kvb == null) {
                        return ImportState.FILE_SCAN_COMPLETE;
                    }
                } catch (DataCorruptedException dce) {
                    /* Skip the record if checksum verified failed */
                    if (dce.isChecksumMismatched()) {
                        return ImportState.RECORD_SKIPPED;
                    }
                    throw dce;
                }

                /*
                 * Skip the record if it has already expired
                 */
                if (kvb.getExpirationTime() > 0 &&
                    getReferenceTime() > kvb.getExpirationTime()) {
                    return ImportState.RECORD_SKIPPED;
                }

                /*
                 * Skip the record if its value is not empty but missing
                 * associated record value RecordDef.
                 */
                if (kvb.getValueBytes().length != 0 && tableWriteDef == null) {
                    return ImportState.RECORD_SKIPPED;
                }

                /* Transform the record */
                kvb = tableDataTrans.transform(kvb);

                kv = kvb.toKeyValueVersion();
                return ImportState.RECORD_IMPORTED;
            } catch (Exception e) {
                /*
                 * Import of this file segment was not completed. Set
                 * isCompleted to false to record this fact.
                 */
                isCompleted = false;
                logger.log(Level.SEVERE, "File segment " +
                           fileName + " import failed", e);
                return ImportState.FILE_SCAN_COMPLETE;
            }
        }

        private ImportState getNextKV() {
            try {
                KeyValueBytes kvb = null;
                try {
                    kvb = DataSerializer.readKVData(dis,
                              fileName + "-" + chunkSequence);
                    if (kvb == null) {
                        return ImportState.FILE_SCAN_COMPLETE;
                    }
                } catch (DataCorruptedException dce) {
                    /* Skip the record if checksum verified failed */
                    if (dce.isChecksumMismatched()) {
                        return ImportState.RECORD_SKIPPED;
                    }
                    throw dce;
                }

                KeyValueBytes.Type type = kvb.getType();
                switch (kvb.getType()) {
                case NONFORMAT:
                    kv = kvb.toKeyValueVersion();
                    return ImportState.RECORD_IMPORTED;
                case LOB:
                    Key lobKey = Key.fromByteArray(kvb.getKeyBytes());
                    String lobFileName = new String(kvb.getValueBytes(),
                                                    StandardCharsets.UTF_8);

                    logger.info("Importing LOB: " + lobFileName);

                    /*
                     * Get the LOB data input stream from the export store
                     */
                    InputStream in = getLobInputStream(lobFileName);
                    if (in == null) {
                        logger.warning("LOB file " + lobFileName +
                                       " not found.");
                        return ImportState.RECORD_SKIPPED;
                    }

                    logger.info("Spawning a thread to import LOB: " +
                                lobFileName);
                    storeImport.importLob(in, lobKey);
                    return ImportState.RECORD_SKIPPED;
                default:
                    /* Should never reach here */
                    throw new RuntimeException("Unexpected value type: " + type);
                }
            } catch (Exception e) {
                /*
                 * Import of this file segment was not completed. Set
                 * isCompleted to false to record this fact.
                 */
                isCompleted = false;
                logger.log(Level.SEVERE, "File segment " +
                    fileName + " import failed", e);
                return ImportState.FILE_SCAN_COMPLETE;
            }
        }

        /*
         * Invoked by the loader to indicate that all the entries supplied by
         * the stream have been processed. The callback happens sometime after
         * getNext() method returns null and all entries supplied by the stream
         * have been written to the store.
         *
         * When this method is invoked by the BulkPut API, it is used as an
         * indicator that a file segment has been successfully imported into
         * the target KVStore.
         */
        @Override
        public void completed() {

            if (isCompleted) {
                logger.info("File segment: " + fileName + " with chunk " +
                            "sequence " + chunkSequence + " successfully " +
                            "imported.");

                if (loadedFileSegments != null) {
                    loadedFileSegments.add(fileName + "-" + chunkSequence);
                }
            } else {
                logger.warning("Not all records in File segment: " + fileName +
                               " with chunk " + "sequence " + chunkSequence +
                               " got imported.");
            }
        }

        @Override
        public void keyExists(KeyValueVersion entry) {}

        /**
         * Exception encountered while adding an entry to the store
         */
        @Override
        public void catchException(RuntimeException runtimeException,
                                   KeyValueVersion kvPair) {
            isCompleted = false;
        }
    }

    /**
     * Perform any task that needs to be completed post import
     */
    protected void doPostImportWork() {

        /*
         * LOB data is imported in a separate thread using KVStore.putLOB().
         * Wait for all these threads to complete their execution.
         */
        storeImport.waitForLobTasks();

        /*
         * Write the status file with the list of all the file segments that
         * has been successfully imported into the target KVStore.
         */
        writeStatusFile(loadedFileSegments);
    }

    /**
     * Load the list of file segments that has been already been imported into
     * the kvstore. During import process, the import of these file segments
     * will be skipped
     */
    protected void loadStatusFile(String fileSegmentName) {
        loadedFileSegments.add(fileSegmentName);
    }

    /**
     * Check if the given file segment has already been imported
     */
    private boolean isLoaded(String fileSegmentName) {

        if (loadedFileSegments != null &&
                loadedFileSegments.contains(fileSegmentName)) {
            return true;
        }
        return false;
    }

    public TableImpl getTableImpl(String tableName) {
        return tableMap.get(tableName);
    }

    public void putTableWriteValueDef(String tableName, RecordDef writeDef) {
        tableWriteValueDefs.put(tableName, writeDef);
    }

    public void logMessage(String message, Level level) {
        logger.log(level, message);
    }

    protected void induceImportChange(String when) {

        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, when);
    }

    /**
     * Imports the specified tables.
     *
     * @param tableNames the tables to import.
     */
    void doTableImport(String[] tableNames) {
        final Set<String> names =
            new TreeSet<String>(oracle.kv.util.expimp.Utilities.TableNameComparator.newInstance);
        names.addAll(Arrays.asList(tableNames));

        doTableImport(new TableFilterImpl() {

            @Override
            public boolean matchTable(String tableName) {
                if (names.contains(tableName)) {
                    names.remove(tableName);
                    return true;
                }
                return false;
            }

            @Override
            public String[] getUnmatched() {
                if (names.isEmpty()) {
                    return null;
                }
                return names.toArray(new String[names.size()]);
            }

            @Override
            public boolean isDone() {
                return names.isEmpty();
            }
        });
    }

    /**
     * Imports the tables in the specified namespaces.
     *
     * @param namespaces the specified namespaces for tables to import.
     */
    void doNamespaceImport(String[] namespaces) {
        final Set<String> set = new TreeSet<String>(FieldComparator.instance);
        for (String ns : namespaces) {
            set.add(NameUtils.switchToExternalUse(ns));
        }

        doTableImport(new TableFilterImpl() {

            @Override
            public boolean matchTable(String tableName) {
                String ns = NameUtils.switchToExternalUse(
                    NameUtils.getNamespaceFromQualifiedName(tableName));
                return set.contains(NameUtils.switchToExternalUse(ns));
            }
        });
    }

    /**
     * The interface to filter the tables to import by name.
     */
    protected interface TableFilter {
        /**
         * Returns true if the specified table needed to import.
         *
         * @param tableName the table name
         *
         * @return true if the specified table needed to import
         */
        boolean matches(String tableName);

        /**
         * Returns the matched table names filtered by the TableFilter.
         *
         * @return the matched table names or null if no matched.
         */
        String[] getMatched();

        /**
         * Returns the unmatched table names filtered by the TableFilter.
         *
         * @return the unmatched table names or null if no unmatched.
         */
        String[] getUnmatched();

        /**
         * Returns true if no more table to import.
         *
         * @return true if no more table to import
         */
        boolean isDone();
    }

    /**
     * The implementation of TableFilter
     */
    public static abstract class TableFilterImpl implements TableFilter {

        private final Set<String> matched;

        TableFilterImpl() {
            matched = new TreeSet<String>(TableNameComparator.newInstance);
        }

        abstract boolean matchTable(String tableName);

        @Override
        public boolean matches(String tableName) {
            if (matched.contains(tableName)) {
                return true;
            }
            if (matchTable(tableName)) {
                matched.add(tableName);
                return true;
            }
            return false;
        }

        @Override
        public String[] getMatched() {
            if (matched.isEmpty()) {
                return null;
            }
            return matched.toArray(new String[matched.size()]);
        }

        @Override
        public String[] getUnmatched() {
            return null;
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }

    /******************* Abstract Methods **************************/

    /**
     * Imports the data/metadata from the abstract store to the target KVStore
     */
    abstract void doImport();

    /**
     * Imports the given tables schema and data from the abstract store to the
     * target KVStore
     */
    abstract void doTableImport(TableFilter tableFilter);

    /**
     * Get the given LOB input stream from the abstract store
     */
    abstract InputStream getLobInputStream(String lobFileName);

    /**
     * Write the status file (list of file segments successfully imported) into
     * the abstract store
     */
    abstract void writeStatusFile(Set<String> fileSegments);

    /**
     * Sets the log handler
     */
    abstract void setLoggerHandler(Logger logger);

    /**
     * Flush all the log streams
     */
    abstract void flushLogs();
}
