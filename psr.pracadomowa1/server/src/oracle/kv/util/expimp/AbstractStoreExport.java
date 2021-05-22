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

import static oracle.kv.util.expimp.ExportImportMain.timeConsistencyFlagValue;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.Direction;
import oracle.kv.FaultException;
import oracle.kv.KVSecurityException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.LoginCredentials;
import oracle.kv.RequestTimeoutException;
import oracle.kv.StoreIteratorException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.security.util.KVStoreLogin;
import oracle.kv.impl.security.util.KVStoreLogin.CredentialsProvider;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.topo.Topology;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;
import oracle.kv.table.TableIteratorOptions;
import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.Utilities.TableNameComparator;
import oracle.kv.util.expimp.utils.exp.AbstractStoreOutput;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordBytes;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordIterator;

/**
 * Abstract implementation of Export functionality which is used to move data
 * from Oracle NoSql database to export stores. Currently the export utility
 * supports Oracle Storage Cloud Service and Local File System as a valid
 * export store.
 *
 * Using the export utility, the user can export all the data/metadata to the
 * export store:
 * 1. Application created data (excluding security data) will be exported.
 * 2. Schema definitions are exported.
 *    a. Schema definitions are table definitions and the index definitions.
 *    b. The schema definitions are exported in json format.
 *    c. The exported table definitions will also include the table owner
 *       which is null if the table has no owner.
 * 3. TTL of every table record is exported.
 * 4. Derived data such as Index data and statistics will not be exported,
 *    but the schema definition includes index definitions, which can be used
 *    at import time.
 * 5. Instead of exporting the entire store, the user can choose to export
 *    individual tables, in which case the corresponding table schemas,
 *    index definitions and the table data will get exported.
 *
 * The export utility does not manage:
 * 1. Security data (such as user definitions) are excluded from the export.
 *    The user must load and manage identities independently of export.
 * 2. Store deployment information; the export utility focuses only on
 *    application data.
 * 3. Incremental export (exporting records since a given time) is not
 *    supported, since the underlying store cannot support this.
 *
 * Export Entire Store:  ParallelScanIterator is used to saturate the disks of
 * all the RNs in the KVStore. A fan out of the store iterator is performed
 * based on whether a data is None format or if it belongs to a particular
 * Table. The data from each data format and tables are streamed directly into
 * the external export store. When we encounter a LOB key, get the corresponding
 * LOB data from KVStore using KVStore.getLOB(lobKeyString).
 * A separate thread is  spawned to stream the lob data into the Abstract Store.
 *
 * Export Individual Table(s): User can export a subset of tables from Oracle
 * NoSql Store to the external export store. KVStore.tableIterator with
 * ParallelScan capability is used to fetch all data from a particular table.
 * The data bytes are then streamed (spawned in a separate thread) into the
 * destination store. Along with table data, corresponding table definitions
 * and its index definitions are also exported.
 *
 * Exit code can be used as a diagnostic to determine the state of export. The
 * exit codes have the following meaning:
 *
 * 0   (EXIT_OK) -- No errors found during export.
 * 100 (EXIT_USAGE) -- Illegal export command usage.
 * 101 (EXIT_NOPERM) -- Unauthorized access to the Oracle Cloud Storage Service
 * 102 (EXIT_EXPSTR_NOCONNECT) -- The Oracle Cloud Storage Service could not be
 *      accessed using the service connection parameters
 * 103 (EXIT_NOCONNECT) -- The source NoSql store could not be connected using
 *      the given store-name and helper hosts.
 * 104 (EXIT_UNEXPECTED) -- The export utility experienced an unexpected error.
 * 105 (EXIT_NOWRITE) -- The export package has no write permissions.
 * 107 (EXIT_CONTAINER_EXISTS) -- The container already exists in the Object
 *      Store. Delete the container or use another container name.
 * 108 (EXIT_NO_EXPORT_FOLDER) -- Export folder with the given name does not
 *      exist.
 * 109 (EXIT_INVALID_EXPORT_STORE) -- Invalid export store type. Valid export
 *      stores are local and object_store.
 * 110 (EXIT_SECURITY_ERROR) -- Error loading security file.
 * 111 (EXIT_NOSQL_NOPERM) -- User has no read permissions on the object.
 */
public abstract class AbstractStoreExport implements CredentialsProvider {
    static final String DEF_CONSISTENCY_TYPE = timeConsistencyFlagValue;
    static final long DEF_CONSISTENCY_TIMELAG_MS = 60000;
    static final long DEF_CONSISTENCY_TIMEOUT_SEC = 10;

    private final String storeName;
    private final String[] helperHosts;
    private final KVStoreConfig kconfig;
    private KVStore store;
    private final TableAPI tableAPI;

    /*
     * Mapping between the tables full name and table instances. Contains all
     * the tables in the source NoSql store.
     */
    private final Map<String, Table> nameToTableMap;

    /*
     * Read consistency used for export. Default is Time consistency with
     * permissible lag of 1 minute
     */
    private String consistencyType;

    /*
     * Permissible time lag when consistencyType is Time consistency
     */
    private long timeLag;

    /*
     * Request timeout in MS
     */
    private final int requestTimeoutMs;

    /*
     * The total number of partitions in the kvstore
     */
    private final int numPartitions;

    /*
     * Hook to induce schema changes. Used for testing
     */
    public static TestHook<String> CHANGE_HOOK;

    /*
     * Logger variables
     */
    protected final Logger logger;
    private final LogManager logManager;

    /*
     *  Use for authentication
     */
    private KVStoreLogin storeLogin;
    private LoginCredentials loginCreds;

    /*
     * Boolean variable which determines if the export output from CLI should
     * be in json format
     */
    private final boolean json;

    /*
     * The export handler.
     */
    private final StoreExportHandler storeExport;

    /*
     * For test purpose
     */
    public static boolean printToConsole = true;

    class CustomLogManager extends LogManager {
        CustomLogManager() {
            super();
        }
    }

    /**
     * Set size of the file segment. Used only for test purposes.
     */
    public static void setMaxFileSegmentSize(long testSize) {
        AbstractStoreOutput.setMaxFileSegmentSize(testSize);
    }

    /**
     * Exit export with the appropriate exit code.
     *
     * @param resetLogHandler if true resets the log handler.
     * @param exitCode
     * @param ps
     * @param message message to be displayed in CLI output on exit
     */
    protected void exit(boolean resetLogHandler,
                        ExitCode exitCode,
                        PrintStream ps,
                        String message) {

        if (resetLogHandler) {
            flushLogs();
            logManager.reset();
        }

        outputClose();

        exit(exitCode, ps, message, getJson());
    }

    protected static void exit(ExitCode exitCode,
                               PrintStream ps,
                               String message,
                               boolean outputAsJson) {

        Utilities.exit(exitCode, ps, message, outputAsJson,
                       "export", printToConsole);
    }

    public AbstractStoreExport(String storeName,
                               String[] helperHosts,
                               String userName,
                               String securityFile,
                               int requestTimeoutMs,
                               boolean json) {

        this.storeName = storeName;
        this.helperHosts = helperHosts;
        this.json = json;
        this.logger = Logger.getLogger(AbstractStoreExport.class.getName() +
                                       (int)(Math.random()*1000));
        this.logger.setUseParentHandlers(false);

        logManager = new CustomLogManager();
        logManager.reset();
        logManager.addLogger(this.logger);

        prepareAuthentication(userName, securityFile);

        kconfig = new KVStoreConfig(storeName, helperHosts);
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
        nameToTableMap =
            new TreeMap<String, Table>(TableNameComparator.newInstance);
        consistencyType = DEF_CONSISTENCY_TYPE;
        timeLag = DEF_CONSISTENCY_TIMELAG_MS;

        KVStoreImpl kvStoreImpl = (KVStoreImpl) store;
        Topology topology = kvStoreImpl.getTopology();
        numPartitions = topology.getPartitionMap().getNPartitions();

        storeExport = new StoreExportHandler(store,
                                             getConsistency(),
                                             requestTimeoutMs,
                                             this.logger);
    }

    public void setConsistencyType(String consistencyType) {
        this.consistencyType = consistencyType;
    }

    public void setTimeLag(int timeLag) {
        this.timeLag = timeLag;
    }

    public Consistency getConsistency() {

        switch (consistencyType) {

            case "ABSOLUTE":
                return Consistency.ABSOLUTE;
            case "NONE":
                return Consistency.NONE_REQUIRED;
            default:
                return new Consistency.Time(timeLag,
                                            TimeUnit.MILLISECONDS,
                                            DEF_CONSISTENCY_TIMEOUT_SEC,
                                            TimeUnit.SECONDS);
        }
    }

    public boolean getJson() {
        return json;
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
     * Get the number of partitions exported for the given table. Returns 0.
     * Subclasses can override this method to return the number of partitions
     * already exported so that the export can resume exporting the remaining
     * partitions
     *
     * @param tableName
     * @return
     */
    protected int getNumPartitionsExported(String tableName) {

        logger.info("Determining the number of partitions already " +
            "exported for table: " + tableName);

        return 0;
    }

    /**
     * Persist the fact that 'numPartitionsExported' partitions has been
     * exported for the given table. Does nothing but the subclasses can
     * override this method to provide a more concrete implementation
     *
     * @param tableName
     * @param numPartitionsExported
     */
    protected void persistCheckpoint(String tableName,
                                     int numPartitionsExported) {

        logger.info("Persisting the fact that " +
            numPartitionsExported + " has been exported for " + tableName);

        return;
    }

    /**
     * Export the table as json. Export the table 10 partitions at a time.
     * This is useful to resume table export on failure.
     *
     * Example: Suppose a store has 100 partitions and Table1 is exported. The
     * table will get exported 10 partitions at a time. After every 10 partitions
     * a checkpoint is persisted. Suppose the export fails for some reason
     * after 46 partitions are exported. After 40 partitions were exported, a
     * checkpoint would have been persisted. On resuming the export, it will
     * resume from partition 41
     *
     * @param pKey
     * @param tableAPIImpl
     * @param iterateOptions
     * @param tableName
     */
    public void exportTableAsJson(PrimaryKey pKey,
                                  TableAPIImpl tableAPIImpl,
                                  TableIteratorOptions iterateOptions,
                                  String tableName) {

        int numPartitionsExported = getNumPartitionsExported(tableName);
        int numCheckpointPartitions = 10;

        /*
         * All the partitions already exported. Return
         */
        if (numPartitions == numPartitionsExported) {
            return;
        }

        /*
         * Resume export from the startPartition
         */
        int startPartition = numPartitionsExported + 1;
        while (startPartition <= numPartitions) {

            int lastPartitionNumber =
                Math.min(startPartition + numCheckpointPartitions - 1,
                         numPartitions);
            Set<Integer> partitionSet = new HashSet<>();

            for (int i = startPartition; i <= lastPartitionNumber; i++) {
                partitionSet.add(i);
            }

            TableIterator<Row> tableIterator =
                tableAPIImpl.tableIterator(pKey, null, iterateOptions,
                                           partitionSet);
            String checkpointKey =
                tableName + "_" + String.valueOf(startPartition);

            while (tableIterator.hasNext()) {

                Row row = tableIterator.next();
                String jsonString = row.toJsonString(false);
                byte[] jsonStrBytes =
                    jsonString.getBytes(StandardCharsets.UTF_8);

                /*
                 * Stream the table data to the external export store.
                 */
                outputWrite(checkpointKey, jsonStrBytes);
            }

            outputWaitForTasksDone();

            /*
             * Close the tableItertor handle after the export
             */
            if (tableIterator != null) {
                tableIterator.close();
            }

            /*
             * Persist the checkpoint after a given set of partitions has been
             * successfully exported
             */
            persistCheckpoint(tableName, lastPartitionNumber);

            outputReset();

            startPartition = lastPartitionNumber + 1;
        }
    }

    /**
     * Export the table in binary format
     *
     * @param table
     */
    public void exportTableAsBytes(Table table) {

        RecordIterator iter = null;
        try {
            iter = storeExport.tableIterator(table);
            /*
             * Iterate over all the table data using table iterator and
             * stream the bytes to the external export store.
             *
             * Format of the exported table record bytes:
             * [RecordKeyLength RecordKey RecordValueLength RecordValueBytes
             * Checksum]
             */
            while (iter.hasNext()) {
                RecordBytes rb = iter.next();
                outputWrite(rb);
            }
        } finally {
            if (iter != null) {
                /*
                 * Close the RecordIterator handle after the export
                 */
                iter.close();
            }
        }
    }

    /**
     * Exports the schema definitions and the data of tables in the specified
     * namespace.
     *
     * @param namespaces The name of the tables whose schema definition and
     * data will get exported.
     *
     * @param exportAsJson flag to export the table as json
     */
    public void exportNamespace(String[] namespaces, boolean exportAsJson) {
        List<String> tables = new ArrayList<String>();
        for (String namespace : namespaces) {
            if (NameUtils.switchToInternalUse(namespace) == null) {
                /* SYSDEFAULT namespace */
                for (Table t : tableAPI.getTables(namespace).values()) {
                    /*
                     * Skip system table and those table in specific
                     * namespace
                     */
                    if (((TableImpl)t).isSystemTable() ||
                        NameUtils.switchToInternalUse(t.getNamespace()) != null) {
                        continue;
                    }
                    addTableWithChildren(tables, t);
                }
            } else {
                for (Table t : tableAPI.getTables(namespace).values()) {
                    addTableWithChildren(tables, t);
                }
            }
        }
        if (tables.isEmpty()) {
            logger.info("No table found in namespace(s): " +
                        Arrays.toString(namespaces));
            return;
        }
        exportTable(tables.toArray(new String[tables.size()]), exportAsJson);
    }

    private void addTableWithChildren(List<String> tables, Table table) {
        tables.add(table.getFullNamespaceName());
        if (!table.getChildTables().isEmpty()) {
            for (Table t : table.getChildTables().values()) {
                addTableWithChildren(tables, t);
            }
        }
    }

    /**
     * Exports the schema definitions and the data of the specified tables.
     *
     * @param tableNames The name of the tables whose schema definition and
     * data will get exported.
     */
    public void exportTable(String[] tableNames, boolean exportAsJson) {

        StringBuilder hhosts = new StringBuilder();

        for (int i = 0; i < helperHosts.length - 1; i++) {
            hhosts.append(helperHosts[i] + ",");
        }

        hhosts.append(helperHosts[helperHosts.length - 1]);

        logger.info("Starting export of table(s) from store " + storeName +
            ", helperHosts=" + hhosts.toString());

        /*
         * Calculate start time of export in UTC
         */
        Date startDate = new Date();

        SimpleDateFormat simpleDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String startDateString = simpleDateFormat.format(startDate);

        /*
         * Hook to induce schema changes before exporting table schemas
         */
        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,1");
        exportTableSchemas(tableNames);

        /*
         * Hook to induce schema changes after exporting table schemas
         */
        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,2");

        Set<String> uniqueTables =
            new TreeSet<String>(TableNameComparator.newInstance);

        Consistency consistency = getConsistency();
        logger.info("Exporting table data with configuration: consistency=" +
                    consistency + "; requestTimeout=" + requestTimeoutMs + "ms");

        for (String tableName : tableNames) {

            TableImpl table = (TableImpl)nameToTableMap.get(tableName);

            /*
             * If the table does not exist in the source nosql store, log the
             * message and continue exporting other table data.
             */
            if (table == null) {
                logger.warning("Table " + tableName +
                               " not present in the store");
                continue;
            }

            if (table.dontExport()) {
                continue;
            }

            if (!uniqueTables.add(tableName)) {
                logger.info("Data for table " + tableName +
                            " already exported. Skipping duplicate entry.");
                continue;
            }

            logger.info("Exporting " + tableName + " table data.");

            if (tableAPI.getTable(tableName) == null) {
                logger.warning("Table " + tableName + " deleted midway of " +
                               "export.");
                continue;
            }


            try {
                if (exportAsJson) {
                    TableAPIImpl tableAPIImpl = (TableAPIImpl)tableAPI;
                    PrimaryKey pKey = table.createPrimaryKey();
                    TableIteratorOptions iterateOptions =
                        new TableIteratorOptions(Direction.UNORDERED,
                                         consistency,
                                         requestTimeoutMs,
                                         ((requestTimeoutMs != 0) ?
                                            TimeUnit.MILLISECONDS : null));
                    exportTableAsJson(pKey,
                                      tableAPIImpl,
                                      iterateOptions,
                                      tableName);
                } else {
                    exportTableAsBytes(table);
                }
            } catch (RequestTimeoutException rte) {
                logger.log(Level.SEVERE, "Table iterator experienced an " +
                           "Operation Timeout. Check the network " +
                           "connectivity to the shard.", rte);
                exit(true, ExitCode.EXIT_UNEXPECTED, System.err, null);
            } catch(StoreIteratorException sie) {
                logger.log(Level.WARNING, "Table " + tableName +
                           " either dropped midway of export or the user " +
                           "does not have read privilege on the table.", sie);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "User has no read permissions on " +
                           "the table " + tableName, e);
                exit(true, ExitCode.EXIT_NOSQL_NOPERM, System.err, null);
            }
        }

        /*
         * Wait for all the threads streaming the data to the external export
         * store to complete.
         */
        try {
            outputWaitForTasksDone();
        } catch (Exception e) {
            exit(true, ExitCode.EXIT_UNEXPECTED, System.err, null);
        }

        storeExport.doTableMetadataDiff();

        /*
         * Calculate end time of export in UTC
         */
        Date endDate = new Date();
        String endDateString = simpleDateFormat.format(endDate);

        StringBuilder exportStat = new StringBuilder();

        exportStat.append("StoreName: " + storeName + "\n");
        exportStat.append("Helper Hosts: " + hhosts.toString() + "\n");
        exportStat.append("Export start time: " + startDateString + "\n");
        exportStat.append("Export end time: " + endDateString + "\n");

        generateExportStats(exportStat.toString());

        String expCompleteMsg = "Completed exporting table(s) from store " +
            storeName + ", helperHosts=" + hhosts.toString();
        logger.info(expCompleteMsg);

        /*
         * Get the export status after all the tables have been exported
         */
        ExportStatus exportStatus = getExportStatus(tableNames, numPartitions);

        if (!exportStatus.getSuccess()) {

            exit(true, ExitCode.EXIT_UNEXPECTED, System.out,
                 exportStatus.getMessage());
        }

        exit(true, ExitCode.EXIT_OK, System.out, expCompleteMsg);
    }

    /**
     * Exports the table schema definitions in json format.
     *
     * @param tableNames The names of the tables whose schema definitions will
     *        be exported.
     */
    private void exportTableSchemas(String[] tableNames) {

        for (String tableName : tableNames) {

            if (nameToTableMap.containsKey(tableName)) {

                logger.info("Table schema with the name " + tableName +
                            " already exported. Skipping duplicate entry.");
                continue;
            }

            TableImpl tableImpl = (TableImpl)tableAPI.getTable(tableName);

            /*
             * If the table does not exist in the source nosql store, log the
             * message and continue exporting remaining table schemas.
             */
            if (tableImpl == null) {

                logger.warning("Table with the name " + tableName +
                               " does not exists in the store.");
                continue;
            }

            if (tableImpl.dontExport()) {
                continue;
            }

            nameToTableMap.put(tableName, tableImpl);
        }

        Collection<Table> tables = nameToTableMap.values();
        Iterator<RecordBytes> iter =
            storeExport.tableSchemaIterator(tables, true /* jsonFormat */);
        while (iter.hasNext()) {
            RecordBytes rb = iter.next();
            outputWrite(rb);
        }
    }

    /**
     * Exports all the data/metadata from the Oracle NoSql store to the export
     * store. Application created data (excluding security data) is exported.
     * The metadata exported includes table schemas and table index definitions.
     */
    public void export() {

        StringBuilder hhosts = new StringBuilder();

        for (int i = 0; i < helperHosts.length - 1; i++) {
            hhosts.append(helperHosts[i] + ",");
        }

        hhosts.append(helperHosts[helperHosts.length - 1]);

        logger.info("Starting export of store " + storeName +
                    ", helperHosts=" + hhosts.toString());

        /*
         * Calculate start time of export in UTC
         */
        Date startDate = new Date();

        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String startDateString = simpleDateFormat.format(startDate);

        /*
         * Hook to induce schema changes before exporting table schemas
         */
        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,1");
        exportTableSchemas();

        /*
         * Hook to induce schema changes after exporting table schemas
         */
        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,2");

        logger.info("Exporting all the data. Retrieving all the records from " +
                    "the KVStore using ParallelScanIterator");

        exportAllData();

        /*
         * At this point all the data bytes in the kvstore has been transfered
         * to the RecordStream. Need to wait for all the worker threads that
         * are transferring the data bytes to the export store to complete their
         * execution.
         */

        outputWaitForTasksDone();

        /*
         * Perform a metadata (table schema) diff at the end of export
         * to check if there has been any metadata change. Report any metadata
         * change as warnings to the user.
         */
        storeExport.doMetadataDiff();

        /*
         * Calculate end time of export in UTC
         */
        Date endDate = new Date();
        String endDateString = simpleDateFormat.format(endDate);

        String expCompleteMsg = "Completed export of store " + storeName +
                                ", helperHosts=" + hhosts.toString();

        StringBuilder exportStat = new StringBuilder();

        exportStat.append("StoreName: " + storeName + "\n");
        exportStat.append("Helper Hosts: " + hhosts.toString() + "\n");
        exportStat.append("Export start time: " + startDateString + "\n");
        exportStat.append("Export end time: " + endDateString + "\n");

        generateExportStats(exportStat.toString());

        logger.info(expCompleteMsg);

        exit(true, ExitCode.EXIT_OK, System.out, expCompleteMsg);
    }

    /**
     * Exports all data entries in the store.
     */
    public void exportAllData() {
        RecordIterator iter = storeExport.storeIterator();
        try {
            while (iter.hasNext()) {
                RecordBytes rb = iter.next();
                if (rb == null) {
                    continue;
                }
                outputWrite(rb);
            }
        } catch (RequestTimeoutException rte) {
            logger.log(Level.SEVERE, "Store iterator experienced an " +
                       "Operation Timeout. Check the following:\n1) " +
                       "Network connectivity to the shard is good.\n2) " +
                       "User executing the export has READ_ANY " +
                       "privilege.", rte);
            exit(true, ExitCode.EXIT_UNEXPECTED, System.err, null);
        } finally {
            iter.close();
        }
        logger.info("All the records retrieved from the KVStore.");
    }

    /**
     * Exports table schema definitions in json format
     */
    public void exportTableSchemas() {
        Iterator<RecordBytes> iter =
            storeExport.tableSchemaIterator(true /* jsonFormat */);
        while (iter.hasNext()) {
            RecordBytes rb = iter.next();
            /*
             * Stream the schema bytes to the external export store
             */
            outputWrite(rb);
        }
    }

    protected ExportStatus getExportStatus(String[] tableNames,
                                           int partiitons) {

        logger.info("Checking if all the tables " +
            Arrays.toString(tableNames) + " have been exported. " +
            "Number of partitions: " + partiitons);

        return new ExportStatus(true, "Export Successful");
    }

    protected static class ExportStatus {

        private boolean success;
        private String message;

        public ExportStatus(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean getSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    private void outputWrite(String fileName, byte[] bytes) {
        getOutputHdl().write(fileName, bytes);
    }

    private void outputWrite(RecordBytes rb) {
        getOutputHdl().write(rb);
    }

    private void outputWaitForTasksDone() {
        getOutputHdl().waitForWriteTasksDone();
    }

    private void outputReset() {
        getOutputHdl().reset();
    }

    private void outputClose() {
        if (getOutputHandler() != null) {
            getOutputHandler().close();
        }
    }

    private AbstractStoreOutput getOutputHdl() {
        AbstractStoreOutput handler = getOutputHandler();
        if (handler == null) {
            throw new IllegalArgumentException(
                "ExportOutputHandler should not be null");
        }
        return handler;
    }

    public static void setTestFlag(boolean testFlag) {
        StoreExportHandler.testFlag = testFlag;
    }

    /******************* Abstract Methods **************************/

    /**
     * Returns the ExportOutputHandler object
     */
    protected abstract AbstractStoreOutput getOutputHandler();

    /**
     * Sets the logger handler
     */
    protected abstract void setLoggerHandler(Logger logger);

    /**
     * Flush all the log streams
     */
    protected abstract void flushLogs();

    /**
     * Write the export stats - store name, helper hosts, export start time
     * and export end time to the export store
     */
    protected abstract void generateExportStats(String exportStats);
}
