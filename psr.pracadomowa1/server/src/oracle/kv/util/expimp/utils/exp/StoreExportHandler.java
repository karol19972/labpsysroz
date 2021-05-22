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

import static oracle.kv.util.expimp.utils.DataSerializer.lobDataReferenceIdentifier;
import static oracle.kv.util.expimp.utils.DataSerializer.noneDataIdentifier;
import static oracle.kv.util.expimp.utils.DataSerializer.tableDataIdentifier;
import static oracle.kv.util.expimp.utils.DataSerializer.UTF8;
import static oracle.kv.util.expimp.utils.DataSerializer.writeNoneFormatData;
import static oracle.kv.util.expimp.utils.DataSerializer.writeTableData;
import static oracle.kv.util.expimp.utils.DataSerializer.writeTableSchemaData;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import oracle.kv.Consistency;
import oracle.kv.Direction;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.KeyValueVersion;
import oracle.kv.ParallelScanIterator;
import oracle.kv.StoreIteratorConfig;
import oracle.kv.Value;
import oracle.kv.ValueVersion;
import oracle.kv.Version;
import oracle.kv.Key.BinaryKeyIterator;
import oracle.kv.Value.Format;
import oracle.kv.impl.api.KeySerializer;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.lob.InputStreamVersion;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;
import oracle.kv.table.TableIteratorOptions;

/**
 * The class manages the data stream to export including table schema data,
 * table/kv and lob data.
 *
 * It provides methods to return iterators over data entries to export, the
 * entries are in binary format.
 */
public class StoreExportHandler {

    /*
     * Schema definitions (table schemas) will get exported in SchemaDefinition
     * file in the export package. Lob file references, None format data will
     * get exported in 'OtherData' file. The table data will get exported in
     * files whose name is same as the table name. For example: Table EMPLOYEE
     * data will get exported in EMPLOYEE file in the export package.
     */
    private static final String schemaFileName = "SchemaDefinition";
    private static final String otherDataFile = "OtherData";

    private static final String lobFileSuffix = "LOBFile";
    private static final int STORE_ITERATOR_MAX_CURRENT_REQUESTS = 5;

    private static KeySerializer keySerializer =
        KeySerializer.PROHIBIT_INTERNAL_KEYSPACE;

    private final KVStore store;
    private final TableAPI tableAPI;

    /*
     * Holds instances of all the topmost level tables in source NoSql store.
     * The key to the map is the unique table id (tableImpl.getIdString()).
     */
    private final Map<String, TableImpl> tableMap;

    /*
     * Mapping between the tables full name and the version of the table at the
     * time of export
     */
    private final Map<String, Integer> exportedTableVersion;

    /* The key suffix of LOB */
    private final String lobSuffix;

    /* Index of the lob file being exported. */
    private int lobFileNumber = 0;

    /* Read consistency used for export. */
    private final Consistency consistency;

    private final int requestTimeoutMs;

    /* TableIteratorOptions used for export. */
    private final TableIteratorOptions iteratorOptions;

    private final Logger logger;

    /* For unit test */
    /*
     * Hook to induce schema changes. Used for testing
     */
    public static TestHook<String> CHANGE_HOOK;

    /*
     * Variables used only during testing
     */
    public static Boolean testFlag = false;
    private static final String testTable = "Table4";

    public StoreExportHandler(KVStore store,
                              Consistency consistency,
                              int requestTimeoutMs,
                              Logger logger) {

        this.logger = logger;
        this.consistency = consistency;
        this.requestTimeoutMs = requestTimeoutMs;
        this.store = store;
        tableAPI = store.getTableAPI();
        lobSuffix = ((KVStoreImpl)store).getDefaultLOBSuffix();
        iteratorOptions =
            new TableIteratorOptions(Direction.UNORDERED,
                                     consistency,
                                     requestTimeoutMs,
                                     (requestTimeoutMs != 0 ?
                                         TimeUnit.MILLISECONDS : null));

        tableMap = new HashMap<String, TableImpl>();
        exportedTableVersion = new HashMap<String, Integer>();
    }

    public Map<String, Integer> getExportedTableVersion() {
        return exportedTableVersion;
    }

    /**
     * Creates an iterator for all table schema records.
     */
    public Iterator<RecordBytes> tableSchemaIterator() {
        return new TableSchemaIterator();
    }

    /**
     * Creates an iterator for all table schema records.
     */
    public Iterator<RecordBytes> tableSchemaIterator(boolean jsonFormat) {
        return new TableSchemaIterator(jsonFormat);
    }

    /**
     * Creates an iterator for the table schema records for the specified
     * tables.
     */
    public Iterator<RecordBytes> tableSchemaIterator(Collection<Table> tables) {
        return new TableSchemaIterator(tables);
    }

    /**
     * Creates an iterator for the table schema records for the specified
     * tables.
     */
    public Iterator<RecordBytes> tableSchemaIterator(Collection<Table> tables,
                                                     boolean jsonFormat) {
        return new TableSchemaIterator(tables, jsonFormat);
    }

    /**
     * Creates an iterator that iterates the entire store data.
     */
    public RecordIterator storeIterator() {
        return new StoreDataIterator();
    }

    /**
     * Creates an iterator for the table records of the specified table.
     */
    public RecordIterator tableIterator(String tableName) {
        Table table = getTable(tableName);
        if (table == null) {
            return null;
        }
        return tableIterator(table);
    }

    public RecordIterator tableIterator(Table table) {
        return new TableDataIterator(table);
    }

    private Table getTable(String tableName) {
        Table table = getTableWithRetry(tableName);
        /*
         * If the table does not exist in the source nosql store, log the
         * message and continue exporting other table data.
         */
        if (table == null) {
            logger.warning("Table " + tableName +
                           " not present in the store");
            return null;
        }

        if (((TableImpl)table).dontExport()) {
            return null;
        }
        return table;
    }

    private Table getTableWithRetry(String tableName) {
        final int maxRetries = 5;

        int retry = 0;
        do {
            Table table = tableAPI.getTable(tableName);
            if (table != null) {
                return table;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        } while (++retry < maxRetries);

        logger.warning("Table '" + tableName + "' not found after " +
                retry +  (retry == 1 ? " retry." : " retries."));
        return null;
    }

    /**
     * Performs a table metadata diff. This method checks if the any of the
     * exported table metadata has been deleted or altered at the end of
     * export.
     */
    public void doTableMetadataDiff() {

        logger.info("Generating Metadata Difference.");

        /*
         * Holds all the deleted tables at the end of export
         */
        List<String> deletedTables = new ArrayList<String>();

        /*
         * Holds all the tables that have been altered.
         */
        List<String> evolvedTables = new ArrayList<String>();

        for (Entry<String, Integer> entry : exportedTableVersion.entrySet()) {

            String tableName = entry.getKey();
            Table table = tableAPI.getTable(tableName);

            /*
             * Exported table no longer present in the KVStore
             */
            if (table == null) {
                deletedTables.add(tableName);
                continue;
            }

            int exportTableVersion = entry.getValue();
            int postExportTableVersion = table.numTableVersions();

            /*
             * Table version mismatch during export and at the end of export.
             */
            if (exportTableVersion != postExportTableVersion) {
                evolvedTables.add(tableName);
            }
        }

        if (deletedTables.isEmpty() && evolvedTables.isEmpty()) {

            logger.info("Exported Table Metadata did " +
                        "not change during export!");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Metadata change found!\n");

        if (!deletedTables.isEmpty()) {
            message.append("Table deleted:\n");

            for (String tableName : deletedTables) {
                message.append(tableName + " ");
            }
            message.append("\n");
        }

        if (!evolvedTables.isEmpty()) {
            message.append("Table evolved:\n");

            for (String tableName : evolvedTables) {
                message.append(tableName + " ");
            }
            message.append("\n");
        }

        logger.info(message.toString());
    }

    private void doChildTableMetadataDiff(TableImpl parent,
                                          List<String> addedTables) {

        for (Map.Entry<String, Table> entry :
                parent.getChildTables().entrySet()) {

            TableImpl tableImpl = (TableImpl)entry.getValue();
            String tableName = tableImpl.getFullName();

            if (!exportedTableVersion.containsKey(tableName)) {
                addedTables.add(tableName);
            }

            doChildTableMetadataDiff(tableImpl, addedTables);
        }
    }

    /**
     * Performs table schema metadata diff. This method checks if any of the
     * table schemas have been evolved during or at the end of export. It also
     * checks if any new table schemas have been added. For tables it checks if
     * any of the exported tables have been deleted.
     */
    public void doMetadataDiff() {

        logger.info("Generating Metadata Difference.");

        /*
         * Holds all the deleted tables at the end of export
         */
        List<String> deletedTables = new ArrayList<String>();

        /*
         * Holds all the tables that have been altered since export started
         */
        List<String> evolvedTables = new ArrayList<String>();

        /*
         * Holds all the tables that have been added since export started
         */
        List<String> addedTables = new ArrayList<String>();

        /*
         * Find all the tables that have been deleted and/or evolved since
         * export started
         */
        for (Entry<String, Integer> entry : exportedTableVersion.entrySet()) {

            String tableName = entry.getKey();

            Table table = tableAPI.getTable(tableName);

            if (table == null) {
                deletedTables.add(tableName);
                continue;
            }

            int exportTableVersion = entry.getValue();
            int postExportTableVersion = table.numTableVersions();

            if (exportTableVersion != postExportTableVersion) {
                evolvedTables.add(tableName);
            }
        }

        /*
         * Find all the tables that have been added since export started
         */
        for (Map.Entry<String, Table> entry : tableAPI.getTables().entrySet()) {

            String tableName = entry.getKey();
            TableImpl tableImpl = (TableImpl)entry.getValue();

            if (tableImpl.dontExport()) {
                continue;
            }

            if (!exportedTableVersion.containsKey(tableName)) {
                addedTables.add(tableName);
            }

            /*
             * Find all the child tables that have been added since export
             */
            doChildTableMetadataDiff(tableImpl, addedTables);
        }

        if (deletedTables.isEmpty() && evolvedTables.isEmpty() &&
            addedTables.isEmpty()) {

            logger.info("Metadata did not change during export!");
            return;
        }

        StringBuilder message = new StringBuilder();

        message.append("Metadata change found!\n");

        if (!deletedTables.isEmpty()) {

            message.append("Table deleted:\n");

            for (String tableName : deletedTables) {
                message.append(tableName + " ");
            }
            message.append("\n");
        }

        if (!addedTables.isEmpty()) {

            message.append("New tables added:\n");

            for (String tableName : addedTables) {
                message.append(tableName + " ");
            }
            message.append("\n");
        }

        if (!evolvedTables.isEmpty()) {

            message.append("Table evolved:\n");

            for (String tableName : evolvedTables) {
                message.append(tableName + " ");
            }
            message.append("\n");
        }

        logger.info(message.toString());
    }

    private class TableSchemaIterator implements Iterator<RecordBytes> {

        private final Iterator<Table> iterator;
        private final Collection<Table> allTables;
        private final boolean jsonFormat;

        public TableSchemaIterator() {
            this(false);
        }

        public TableSchemaIterator(boolean jsonFormat) {
            this(null, jsonFormat);
        }

        TableSchemaIterator(Collection<Table> tables) {
            this(tables, false);
        }

        TableSchemaIterator(Collection<Table> tables, boolean jsonFormat) {
            if (tables == null) {
                allTables = new ArrayList<Table>();
                collectTables(null);
                iterator = allTables.iterator();
            } else {
                allTables = tables;
                iterator = allTables.iterator();
            }
            this.jsonFormat = jsonFormat;
        }

        private void collectTables(Map<String, Table> tableSet) {
            if (tableSet == null) {
                tableSet = tableAPI.getTables();
            }
            for (Entry<String, Table> entry : tableSet.entrySet()) {
                TableImpl table = (TableImpl)entry.getValue();
                if (table.dontExport()) {
                    continue;
                }
                allTables.add(table);
                if (table.getParent() == null) {
                    tableMap.put(table.getIdString(), table);
                }
                /*
                 * If this table has any child tables, export the child table
                 * schema definitions.
                 */
                collectTables(table.getChildTables());
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public RecordBytes next() {
            if (!hasNext()) {
                return null;
            }

            TableImpl tableImpl = (TableImpl)iterator.next();
            RecordBytes tsr = createTableSchemaRecord(tableImpl);

            String tableFullName = tableImpl.getFullNamespaceName();
            int tableVersion = tableImpl.getTableVersion();
            exportedTableVersion.put(tableFullName, tableVersion);

            logger.info("Exporting table schema: " + tableFullName +
                          ". TableVersion: " + tableVersion);
            return tsr;
        }

        private RecordBytes createTableSchemaRecord(Table table) {
            RecordBytes rb = new RecordBytes(schemaFileName);
            if (jsonFormat) {
                writeTableSchemaData(rb.getOutput(), tableDataIdentifier, table,
                                     ((TableAPIImpl)tableAPI).getRegionMapper());
            } else {
                writeTableSchemaData(rb.getOutput(), table,
                                     ((TableAPIImpl)tableAPI).getRegionMapper());
            }
            return rb;
        }
    }

    /**
     * The interface of Iterator that supplies RecordBytes object, it contains
     * close() method to release the associated resource.
     */
    public static interface RecordIterator extends Iterator<RecordBytes> {
        void close();
    }

    /**
     * The iterator of whole store data.
     */
    private class StoreDataIterator implements RecordIterator {

        private final ParallelScanIterator<KeyValueVersion> iterator;
        private RecordBytes recordBytes;

        public StoreDataIterator() {
            /*
             * Use multi-threading for store iteration and limit the number
             * of threads (degree of parallelism) to 5.
             */
            StoreIteratorConfig sic = new StoreIteratorConfig();
            sic.setMaxConcurrentRequests(STORE_ITERATOR_MAX_CURRENT_REQUESTS);
            /*
             * ParallelScanIterator used to saturate the disks of all the RNS in
             * the KVStore.
             */
            iterator = store.storeIterator(Direction.UNORDERED,
                                           0, /* batchSize */
                                           null, /* parentKey */
                                           null, /* subRange */
                                           null, /* depth */
                                           consistency,
                                           requestTimeoutMs,
                                           (requestTimeoutMs != 0 ?
                                               TimeUnit.MILLISECONDS : null),
                                           sic);
            recordBytes = null;

            logger.info("Exporting store data with configuration: " +
                        "consistency=" + consistency + "; requestTimeout=" +
                        requestTimeoutMs + "ms");
        }

        @Override
        public boolean hasNext() {
            while (recordBytes == null && iterator.hasNext()) {
                KeyValueVersion kvv = iterator.next();
                recordBytes = getRecordBytes(kvv);
            }
            return recordBytes != null;
        }

        @Override
        public RecordBytes next() {
            if (!hasNext()) {
                return null;
            }
            RecordBytes ret = recordBytes;
            recordBytes = null;
            return ret;
        }

        @Override
        public void close() {
            if (iterator != null) {
                iterator.close();
            }
        }

        private RecordBytes getRecordBytes(KeyValueVersion kvv) {
            Key key = kvv.getKey();

            Value value = kvv.getValue();
            Version version = kvv.getVersion();
            ValueVersion vv = new ValueVersion(value, version);

            /*
             * Fan out the store iterator depending on the format of the
             * value: Table format, None format.
             *
             * For key-only data, try to export the key only data assuming
             * it is belongs to a key only table. Otherwise, export it as a
             * NONE format data.
             */
            if (value.getValue().length == 0 ||
                Format.isTableFormat(value.getFormat())) {
                /* Table record */
                return createExportTableRecord(key, vv, kvv.getExpirationTime());
            } else if (value.getFormat().equals(Value.Format.NONE)){
                /* None format record */
                return createNoneFormatRecord(key, value);
            }
            return null;
        }

        /**
         * Exports the table record. Key and value are exported as bytes.
         */
        private RecordBytes createExportTableRecord(Key key,
                                                    ValueVersion vv,
                                                    long expirationTime) {

            Value value = vv.getValue();

            /*
             * If value length is 0, there is no format byte that allows us
             * to find the format of data. Use table membership to check if
             * key only data belongs to a key only table.
             *
             * Try to export the key only data assuming it is belongs to
             * a key only table. Returns 0 if it belonged to a key only
             * table.
             */
            boolean keyOnlyReocrd = (value.getValue().length == 0);

            /*
             * keyIter is used to iterate over the fields in key. The first field
             * in key is the IdString of the top most level table
             */
            BinaryKeyIterator keyIter = new BinaryKeyIterator(key.toByteArray());

            /*
             * Get hold of the top most level table
             */
            String tableIdString = keyIter.next();
            TableImpl topTable = tableMap.get(tableIdString);

            /*
             * Check if table data introduced after export was started. If true
             * such records wont get exported
             */
            if (topTable == null) {
                /*
                 * Key only data belongs to a NONE format, export it as a NONE
                 * format data.
                 */
                if (keyOnlyReocrd) {
                    return createNoneFormatRecord(key, value);
                }
                return null;
            }

            TableImpl tableToUse = topTable;

            /*
             * Iterate over the key to get hold of the table representing this
             * record to be exported.
             */
            while (!keyIter.atEndOfKey()) {

                int numComponentsToSkip = tableToUse.getPrimaryKeySize();

                if (tableToUse.getParent() != null) {
                    TableImpl parentTable = (TableImpl) tableToUse.getParent();
                    numComponentsToSkip -= parentTable.getPrimaryKeySize();
                }

                for (int i = 0; i < numComponentsToSkip; i++) {
                    keyIter.next();
                }

                if (keyIter.atEndOfKey()) {
                    break;
                }

                /*
                 * Get the next level child tableId in this key
                 */
                final String tableId = keyIter.next();

                /*
                 * Get hold of the child table whose table id is tableId
                 */
                for (Map.Entry<String, Table> entry : tableToUse.getChildTables()
                        .entrySet()) {

                    TableImpl childTable = (TableImpl)entry.getValue();

                    if (childTable.getIdString().equals(tableId)) {
                        tableToUse = childTable;
                        break;
                    }
                }

                if (tableToUse.equals(topTable)) {
                    /*
                     * Key only data belongs to a NONE format, export it as a
                     * NONE format data.
                     */
                    if (keyOnlyReocrd) {
                        return createNoneFormatRecord(key, value);
                    }
                }
            }

            if (tableToUse.dontExport()) {
                return null;
            }

            String tableFullName = tableToUse.getFullNamespaceName();
            /*
             * Induce schema changes when the store iterator is importing data to
             * export store
             */
            if (testFlag == true) {
                if (tableToUse.getFullName().equals(testTable)) {
                    assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,3");
                    testFlag = false;
                }
            }

            int exportTableVersion = exportedTableVersion.get(tableFullName);
            return createTableRecord(tableToUse, exportTableVersion, key,
                                     value, vv.getVersion(), expirationTime);
        }
    }


    /**
     * The table data iterator for the given table.
     *
     * It assumes the table schema has been exported before exporting the data,
     * if not found exported table schema in ExportedTableVersion map, skip
     * exporting the table data.
     */
    private class TableDataIterator implements RecordIterator {
        private final Table table;
        private final TableIterator<KeyValueVersion> iterator;
        private Integer tableVersion = null;

        TableDataIterator(Table table) {
            this.table = table;
            TableAPIImpl tableAPIImpl = (TableAPIImpl)tableAPI;
            iterator = tableAPIImpl.tableKVIterator(table.createPrimaryKey(),
                                                    null,
                                                    iteratorOptions);

            /*
             * Induce schema changes when table iterator is importing data
             * to export store. testFlag variable set to true only in tests
             */
            if (testFlag == true) {
                if (table.getFullName().equals(testTable)) {
                    assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,3");
                    testFlag = false;
                }
            }

            logger.info("Exporting table data with configuration: " +
                        "consistency=" + iteratorOptions.getConsistency() +
                        "; requestTimeout=" + iteratorOptions.getTimeout() +
                        "ms");
        }

        @Override
        public boolean hasNext() {
            /*
             * ExportedTableVersion contains the tables whose table schema has
             * been exported, if the table is not found in ExportedTableSchema,
             * skip exporting the records of the table.
             */
            if (tableVersion == null) {
                tableVersion = exportedTableVersion.get(
                    table.getFullNamespaceName());
            }
            return (iterator.hasNext() && tableVersion != null);
        }

        @Override
        public RecordBytes next() {
            if (!hasNext()) {
                return null;
            }
            KeyValueVersion kvv = iterator.next();
            long expirationTime = kvv.getExpirationTime();
            return createTableRecord(table, tableVersion,
                                     kvv.getKey(), kvv.getValue(),
                                     kvv.getVersion(), expirationTime);
        }

        @Override
        public void close() {
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    private RecordBytes createNoneFormatRecord(Key key, Value value) {

        RecordBytes rbytes;
        if (key.toString().endsWith(lobSuffix)) {
            /*
             * Spawn a separate thread to transfer the LOB data bytes from
             * Oracle NoSql store to export store.
             */
            String lobFileName = getNextLobFileName();
            InputStreamVersion isv = store.getLOB(key, consistency,
                                                  5, TimeUnit.SECONDS);
            InputStream lobStream = isv.getInputStream();

            rbytes = new RecordLobBytes(otherDataFile, lobFileName, lobStream);
            writeNoneFormatData(rbytes.getOutput(),
                                lobDataReferenceIdentifier,
                                key,
                                lobFileName.getBytes(UTF8));
        } else {
            rbytes = new RecordBytes(otherDataFile);
            writeNoneFormatData(rbytes.getOutput(),
                                noneDataIdentifier,
                                key,
                                value.getValue());
        }
        return rbytes;
    }

    private RecordBytes createTableRecord(Table table,
                                          int exportTableVersion,
                                          Key key,
                                          Value value,
                                          Version version,
                                          long expirationTime) {

        RecordBytes rbytes = new RecordBytes(table.getFullNamespaceName());
        writeTableData(rbytes.getOutput(), keySerializer, (TableImpl)table,
                       exportTableVersion, key, value, version, expirationTime);
        return rbytes;
    }

    private String getNextLobFileName() {
        return lobFileSuffix + (++lobFileNumber);
    }

    /**
     * The data entry to export.
     */
    public static class RecordBytes {

        private final String fileName;
        private final ByteArrayOutputStream bos;

        public RecordBytes(String fileName) {
            this.fileName = fileName;
            bos = new ByteArrayOutputStream();
        }

        public String getFileName() {
            return fileName;
        }

        public ByteArrayOutputStream getOutput() {
            return bos;
        }

        public byte[] getBytes(){
            return bos.toByteArray();
        }

        public boolean isLob() {
            return false;
        }

        public String getLobFileName() {
            return null;
        }

        public InputStream getLobStream() {
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[RecordBytes]fileName=");
            sb.append(fileName);
            sb.append(";recordLength=");
            sb.append(getBytes().length);
            return sb.toString();
        }
    }

    /**
     * The lob data entry to export.
     */
    public static class RecordLobBytes extends RecordBytes {

        private final String lobFileName;
        private final InputStream lobStream;

        public RecordLobBytes(String fileName,
                              String lobFileName,
                              InputStream lobStream) {
            super(fileName);
            this.lobFileName = lobFileName;
            this.lobStream = lobStream;
        }

        @Override
        public boolean isLob() {
            return true;
        }

        @Override
        public String getLobFileName() {
            return lobFileName;
        }

        @Override
        public InputStream getLobStream() {
            return lobStream;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            sb.append(";lobname=");
            sb.append(lobFileName);
            sb.append(", stream=");
            sb.append((lobStream != null) ? "non-null":"null");
            return sb.toString();
        }
    }
}
