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

package oracle.kv.util.migrator.impl.sink.ondb;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.BulkWriteOptions;
import oracle.kv.EntryStream;
import oracle.kv.Key;
import oracle.kv.KeyValueVersion;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.table.FieldDef;
import oracle.kv.table.RecordDef;
import oracle.kv.table.Table;
import oracle.kv.util.expimp.utils.DataSerializer.KeyValueBytes;
import oracle.kv.util.expimp.utils.imp.StoreImportHandler;
import oracle.kv.util.expimp.utils.imp.StoreImportHandler.TableDataTransformer;
import oracle.kv.util.migrator.impl.data.ondbbinary.KeyValueBytesEntry;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig.TableInfo;
import oracle.kv.util.migrator.impl.source.ondbbinary.OndbBinaryFileSource;
import oracle.kv.util.migrator.impl.util.OndbUtils;
import oracle.kv.util.migrator.impl.util.OndbUtils.TableNameComparator;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.DataTransform;
import oracle.nosql.common.migrator.StateHandler;
import oracle.nosql.common.migrator.data.Entry;

/**
 * The OndbBinarySink that writes binary data entries supplied by
 * OndbBinaryFileSource source to NoSQL DB.
 */
public class OndbBinarySink extends OndbSink<KeyValueVersion> {
    /*
     * The names of the tables associated with the sources processed.
     */
    private final Set<String> loadedTables;

    /*
     * The names of the tables skipped to import due to some reasons.
     */
    private final Set<String> skippedTables;

    /*
     * The StoreImportHandler which encapsulates common functionality with
     * Import utility.
     */
    private final StoreImportHandler storeImport;

    /*
     * Hook to induce schema changes. Used for testing
     */
    private static TestHook<String> CHANGE_HOOK;

    public OndbBinarySink(OndbSinkConfig config,
                          StateHandler stateHandler,
                          Logger logger) {
        super(config, stateHandler, logger);

        loadedTables = new TreeSet<String>(TableNameComparator.newInstance);
        skippedTables = new TreeSet<String>(TableNameComparator.newInstance);

        storeImport = new StoreImportHandler(store, logger);
    }

    /**
     * Initialize the BulkPut EntryStreams for supplied data source.
     *   o Initialize BulkPut EntryStreams for table data or other data
     *     including NonFormat Data.
     *   o Create target table for table data source.
     *   o For table data source, skip importing to the table if matches:
     *      1. The writer schema associated with the table was missing from
     *         Schema file.
     *      2. The target table is not present in the store due to some reasons:
     *          o create table failed but continueOnError is true.
     *          o the table happen to be dropped by other application.
     *      3. The target table is present the store but has different primary
     *         key definition with that defined in SchemaFile.
     */
    @Override
    public List<EntryStream<KeyValueVersion>> createBulkPutStreams(
            DataSource[] sources) {

        induceImportChange("BEFORE");

        final List<EntryStream<KeyValueVersion>> kvstreams =
            new ArrayList<EntryStream<KeyValueVersion>>(sources.length);

        for (DataSource source : sources) {
            String fullNamespaceName = source.getTargetTable();
            TableInfo ti = null;
            Table table = null;
            boolean needReserialize = false;

            if (fullNamespaceName != null) {

                if (!loadedTables.contains(fullNamespaceName)) {
                    loadedTables.add(fullNamespaceName);
                }

                /*
                 * Skip loading the source for the specified table if its
                 * parent table has been skipped.
                 */
                String pTableName =
                    OndbUtils.getParentTableName(fullNamespaceName);
                if (pTableName != null) {
                    if (skippedTables.contains(pTableName)) {
                        String msg = "[Skipped] Migrating to its parent " +
                           "table has been skipped due to some reason, " +
                           "Skipping the records of the table: " +
                           fullNamespaceName;
                        log(Level.WARNING, msg);
                        if (!skippedTables.contains(fullNamespaceName)) {
                            skippedTables.add(fullNamespaceName);
                        }
                        continue;
                    }
                }

                /*
                 * Skip loading the source for the specified table if its writer
                 * schema is null
                 */
                if (!config.hasTableWriterSchema(fullNamespaceName)) {
                    String msg = "[Skipped] Missing writer schema " +
                        "associated the source input, skipping the " +
                        "records of the table: " + fullNamespaceName;
                    log(Level.WARNING, msg);
                    if (!skippedTables.contains(fullNamespaceName)) {
                        skippedTables.add(fullNamespaceName);
                    }
                    continue;
                }

                ti = config.getTableInfo(fullNamespaceName);
                table = createTable(ti);
                /*
                 * If table is not present in the store, skipping loading data
                 * to this table. The possible case is:
                 *  o Table creation failed but ContinueOnDdlError is true.
                 *  o Table happen to be dropped by other application.
                 */
                if (table == null) {
                    String msg = "[Skipped] Table not present in store, " +
                        "skipping the records of the table: " +
                        fullNamespaceName;
                    log(Level.WARNING, msg);
                    if (!skippedTables.contains(fullNamespaceName)) {
                        skippedTables.add(fullNamespaceName);
                    }
                    continue;
                }

                /*
                 * The specified table has been existing and its primary key is
                 * not compatible with the table specified with the input data,
                 * skip loading to this table.
                 *
                 * Compatibility means 2 tables has same number of primary
                 * key fields and the type of primary key fields are exactly
                 * same, but field name can be different.
                 */
                TableImpl tableInSchema =
                    config.getParsedTable(fullNamespaceName);
                if (!checkPrimarykeyMatch(table, tableInSchema)) {
                    String msg = "[Skipped] Mismatch between the primary " +
                        "key of the table schema in the target nosql " +
                        "store and the table data being imported. " +
                        "Skipping the records of the table: " +
                        fullNamespaceName;
                    log(Level.WARNING, msg);
                    if (!skippedTables.contains(fullNamespaceName)) {
                        skippedTables.add(fullNamespaceName);
                    }
                    continue;
                }

                if(!checkShardkeyMatch(table,tableInSchema)) {
                    log(Level.WARNING,"Shard key is different. Reserializing the table data");
                    needReserialize = true;
                }
            }

            /* Add transformer */
            DataTransform transform = getTransform(source, table, needReserialize);
            if (transform != null) {
                source.addTransform(transform);
            }

            /* Populates EntryStream */
            kvstreams.add(new DataKVVStream(source));
        }

        induceImportChange("AFTER");

        return kvstreams;
    }

    private static boolean checkPrimarykeyMatch(Table table1, Table table2) {

        List<String> pKeys1 = table1.getPrimaryKey();
        List<String> pKeys2 = table2.getPrimaryKey();

        if (pKeys1.size() != pKeys2.size()) {
            return false;
        }
        for (int i = 0; i < pKeys1.size(); i++) {
            String fieldName1 = pKeys1.get(i);
            FieldDef fieldDef1 = table1.getField(fieldName1);

            String fieldName2 = pKeys2.get(i);
            FieldDef fieldDef2 = table2.getField(fieldName2);

            if (!fieldDef1.getType().equals(fieldDef2.getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Performs bulk put with TTL reference time specified.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void performBulkPut(List<EntryStream<KeyValueVersion>> kvstreams,
                               BulkWriteOptions bwo) {

        List<EntryStream<KeyValueVersion>> streams =
                new ArrayList<EntryStream<KeyValueVersion>>();
        for (EntryStream<?> stream : kvstreams) {
            streams.add((EntryStream<KeyValueVersion>)stream);
        }
        ((KVStoreImpl)store).put(streams, config.getReferenceTime(), bwo);
    }

    /**
     * Do below post works: wait for completion of loading LOBs.
     */
    @Override
    public void doPostWork() {

        if (storeImport != null) {
            if (config.migrateAll()) {
                storeImport.waitForLobTasks();
            }
            storeImport.close();
        }

        super.doPostWork();
    }

    /**
     * Returns the DataTranform for table data or other data.
     */
    private DataTransform getTransform(DataSource source, Table table, boolean reserialize) {
        if (((OndbBinaryFileSource)source).isTableData()) {
            String tableFullName = table.getFullNamespaceName();
            RecordDef writerDef = config.getTableWriterSchema(tableFullName);
            return new TableRecordTransformer(table, writerDef, reserialize);
        }
        return null;
    }

    /**
     * The data stream that supplies KeyValueVersion values.
     *  1. Skip the expired entry
     *  2. For lob entry, start put lob task and skip the lob entry.
     */
    private class DataKVVStream extends DataStream<KeyValueVersion> {
        public DataKVVStream(DataSource source) {
            super(source);
        }

        @Override
        public Entry readNext() {
            KeyValueBytesEntry kvbEntry;
            while(true) {
                kvbEntry = (KeyValueBytesEntry)source.readNext();
                if (kvbEntry == null) {
                    break;
                }

                KeyValueBytes kvb = kvbEntry.getData();
                if (kvb.getType() == KeyValueBytes.Type.TABLE) {
                    /* Skip the expired entry */
                    if (kvb.getExpirationTime() > 0 &&
                        config.getReferenceTime() > kvb.getExpirationTime()) {
                        log(Level.INFO, "Skip expired record: " +
                            Key.fromByteArray(kvb.getKeyBytes()) +
                            " [referenceTimeMs=" + config.getReferenceTime() +
                            ", expirationTimeMs=" + kvb.getExpirationTime() +
                            "]");
                        continue;
                    }
                }

                /* Skip the lob entry and start put lob task */
                if (kvb.getType() == KeyValueBytes.Type.LOB) {
                    storeImport.importLob(kvb.getInputStream(),
                                          Key.fromByteArray(kvb.getKeyBytes()));
                    continue;
                }

                break;
            }
            return kvbEntry;
        }

        @Override
        KeyValueVersion convertData(Entry entry) {
            assert(entry instanceof KeyValueBytesEntry);
            return ((KeyValueBytesEntry)entry).getData().toKeyValueVersion();
        }

        @Override
        String keyStringOfData(KeyValueVersion kvv) {
            return kvv.getKey().toString();
        }
    }

    /**
     * The transformer for table data:
     *  o Refill in tableId in key bytes.
     *  o Update the table version in value bytes.
     *  o Re-serialize the table record encoded with different version of
     *    definition.
     */
    private class TableRecordTransformer extends TableDataTransformer
        implements DataTransform {

        public TableRecordTransformer(Table table, RecordDef writeValueDef, boolean reserialize) {
            super(table, writeValueDef, reserialize);
        }

        @Override
        public Entry transform(Entry entry) {
            KeyValueBytesEntry kvEntry = (KeyValueBytesEntry) entry;
            KeyValueBytes kvBytes = kvEntry.getData();
            kvBytes = transform(kvBytes);
            kvEntry.setKeyValueBytes(kvBytes);
            return kvEntry;
        }
    }

    public static void setSchemaChangeHook(TestHook<String> testHook) {
        CHANGE_HOOK = testHook;
    }

    private void induceImportChange(String when) {

        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, when);
    }

    private static boolean checkShardkeyMatch(Table table1, Table table2) {
        return table1.getShardKey().size() == table2.getShardKey().size();
    }
}
