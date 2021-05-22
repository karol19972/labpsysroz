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

package oracle.kv.util.migrator.impl.source.ondb;

import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import oracle.kv.Direction;
import oracle.kv.KVStore;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIteratorOptions;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordBytes;
import oracle.kv.util.migrator.impl.util.OndbUtils;
import oracle.kv.util.migrator.impl.util.OndbUtils.TableNameComparator;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.DataSourceFactory;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.FactoryBase;
import oracle.nosql.common.migrator.impl.source.DataSourceBaseImpl;

/**
 * The factory that create OndbSource instances to read records from NoSQL
 * database.
 */
public class OndbSourceFactory extends FactoryBase
    implements DataSourceFactory {

    private final static String TABLE_SCHEMA = "tableSchema";
    private final static String STORE_DATA = "storeData";

    /*
     * Hook to induce schema changes. Used for testing
     */
    public static TestHook<String> CHANGE_HOOK;

    public OndbSourceFactory() {
        super(ONDB_TYPE, OndbSourceConfig.COMMAND_ARGS);
    }

    @Override
    public DataSource[] createDataSources(DataSourceConfig config,
                                          String sinkType,
                                          Logger logger) {

        final OndbSourceConfig cfg = (OndbSourceConfig)config;
        SourceManager factory;
        if (sinkType.equals(ONDB_BINARY_TYPE)) {
            factory = new BinarySourceManager(cfg, logger);
        } else {
            factory = new RowSourceManager(cfg, logger);
        }
        return factory.getDataSources();
    }

    private static Collection<Table> getTablesToExport(KVStore store,
                                                       OndbSourceConfig config) {
        final Map<String, Table> tableSet =
            new TreeMap<String, Table>(TableNameComparator.newInstance);

        TableAPIImpl tableAPI = (TableAPIImpl)store.getTableAPI();

        if (config.getTables() == null && config.getNamespaces() == null) {
            /* export all tables */
            for (Table table : tableAPI.getTables().values()) {
                if (((TableImpl)table).isSystemTable()) {
                    continue;
                }
                addTableAndItsChild(tableSet, table);
            }
        } else {
            if (config.getTables() != null) {
                /* export specified tables */
                for (String tableName : config.getTables()) {
                    Table table = tableAPI.getTable(tableName);
                    if (table != null) {
                        tableSet.put(tableName, table);
                    }
                }
            }
            if (config.getNamespaces() != null) {
                /* export tables in specified namespaces */
                for (String ns : config.getNamespaces()) {
                    boolean isSysDef =
                        (NameUtils.switchToInternalUse(ns) == null);
                    for (Table t : tableAPI.getTables(ns).values()) {
                        if (isSysDef) {
                            /*
                             * If namespace is SysDefault, skip system tables
                             * and tables belonged to non-sysdefault namespace.
                             */
                            if (((TableImpl)t).isSystemTable() ||
                                NameUtils.switchToInternalUse(t.getNamespace())
                                    != null) {
                                continue;
                            }
                        }
                        addTableAndItsChild(tableSet, t);
                    }
                }
            }
        }
        return tableSet.values();
    }

    private static void addTableAndItsChild(Map<String, Table> tables,
                                            Table table) {
        tables.put(table.getFullNamespaceName(), table);
        if (!table.getChildTables().isEmpty()) {
            for (Table child : table.getChildTables().values()) {
                addTableAndItsChild(tables, child);
            }
        }
    }

    @Override
    public DataSourceConfig parseJson(InputStream in, int configVersion) {
        return new OndbSourceConfig(in, configVersion);
    }

    @Override
    public DataSourceConfig createConfig(MigratorCommandParser parser) {
        return new OndbSourceConfig(parser);
    }

    /**
     * A manager of data sources responsible for:
     *  1. Creates DataSource objects.
     *  2. Manages the shared resource by DataSource objects.
     */
    public static abstract class SourceManager {

        KVStore store;
        OndbSourceConfig config;
        Logger logger;
        private Map<String, DataSource> sources;

        public SourceManager(OndbSourceConfig config, Logger logger) {
            this.config = config;
            this.logger = logger;
            store = OndbUtils.connectToStore(config.getStoreName(),
                                             config.getHelperHosts(),
                                             config.getUsername(),
                                             config.getSecurity(),
                                             config.getRequestTimeoutMs());
            sources = new HashMap<String, DataSource>();
        }

        public DataSource[] getDataSources() {
            if (sources.isEmpty()) {
                return null;
            }
            return sources.values().toArray(new DataSource[sources.size()]);
        }

        protected void addSource(DataSource source) {
            sources.put(source.getName(), source);
        }

        public void release(DataSource source) {
            sources.remove(source.getName());
            if (sources.isEmpty()) {
                close();
            }
        }

        KVStore getStore() {
            return store;
        }

        public void close() {
            if (store != null) {
                store.close();
                store = null;
            }
        }
    }

    /**
     * The manager creates OndbBinarySource instances that suppling binary
     * record entries.
     */
    static class BinarySourceManager extends SourceManager {
        private final StoreExportHandler export;
        private boolean allDone;

        public BinarySourceManager(OndbSourceConfig config, Logger logger) {
            super(config, logger);
            export = new StoreExportHandler(store,
                                            config.getReadConsistency(),
                                            config.getRequestTimeoutMs(),
                                            logger);
            initDataSources();
            allDone = true;
        }

        private void initDataSources() {
            /* Run test hook before export schemas */
            runTestHookBeforeExportSchemas();

            DataSource source;
            if (config.migrateAll()) {
                /* Export entire store */
                addSource(OndbBinarySource.getSchemaSource(
                            this, TABLE_SCHEMA, null, logger));
                source = OndbBinarySource.getDataSource(this,
                            STORE_DATA, logger);
                ((DataSourceBaseImpl)source).setPriority(1);
                addSource(source);
            } else {
                /* Export the specified tables */
                Collection<Table> tables = getTablesToExport(store, config);
                if (tables.isEmpty()) {
                    return;
                }

                addSource(OndbBinarySource.getSchemaSource(
                            this, TABLE_SCHEMA, tables, logger));
                for (Table table : tables) {
                    source = OndbBinarySource.getDataSource(this,
                        table.getFullNamespaceName(), logger);
                    ((DataSourceBaseImpl)source).setPriority(1);
                    addSource(source);
                }
            }
        }

        Iterator<RecordBytes> createDataIterator(String name) {
            if (name.equals(STORE_DATA)) {
                return export.storeIterator();
            }
            return export.tableIterator(name);
        }

        Iterator<RecordBytes> createSchemaIterator(Collection<Table> tables) {
            if (tables != null) {
                return export.tableSchemaIterator(tables);
            }
            return export.tableSchemaIterator();
        }

        @Override
        public void release(DataSource source) {
            super.release(source);
            if (allDone && !((OndbBinarySource)source).isDone()) {
                allDone = false;
            }
        }

        @Override
        public void close() {
            if (allDone) {
                if (config.migrateAll()) {
                    export.doMetadataDiff();
                } else {
                    export.doTableMetadataDiff();
                }
            }
            super.close();
        }
    }

    /**
     * The manager creates OndbKVSource that suppling data entries.
     */
    static class RowSourceManager extends SourceManager {

        private final TableIteratorOptions iteratorOptions;
        private Collection<Table> tables;

        public RowSourceManager(OndbSourceConfig config, Logger logger) {
            super(config, logger);
            tables = getTablesToExport(store, config);
            iteratorOptions =
                new TableIteratorOptions(Direction.UNORDERED,
                                         config.getReadConsistency(),
                                         config.getRequestTimeoutMs(),
                                         TimeUnit.MILLISECONDS);
            initDataSources();
        }

        private void initDataSources() {
            if (!tables.isEmpty()) {
                addSource(OndbRowSource.getSchemaSource(this, TABLE_SCHEMA,
                                                        tables, logger));
                for (Table table : tables) {
                    String tableName = table.getFullNamespaceName();
                    addSource(OndbRowSource.getDataSource(this, tableName,
                                                          logger));
                }
            }
        }

        Iterator<Row> createDataIterator(String tableName) {
            TableAPI tableAPI = store.getTableAPI();
            Table table = tableAPI.getTable(tableName);
            if (table == null) {
                logger.warning("Table is not present in store: " + tableName);
                return null;
            }
            return tableAPI.tableIterator(table.createPrimaryKey(),
                                          null /* getOptions */,
                                          iteratorOptions);
        }
    }

    public static void setSchemaChangeHook(TestHook<String> testHook) {
        CHANGE_HOOK = testHook;
    }

    private static void runTestHookBeforeExportSchemas() {
        /*
         * Hook to induce schema changes before exporting table schemas
         */
        assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,1");
    }
}
