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

import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import oracle.kv.BulkWriteOptions;
import oracle.kv.EntryStream;
import oracle.kv.KVStore;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.query.PrepareCallback.QueryOperation;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig.TableInfo;
import oracle.kv.util.migrator.impl.util.OndbUtils;
import oracle.kv.util.migrator.impl.util.OndbUtils.TableFilter;
import oracle.kv.util.migrator.impl.util.OndbUtils.TableNameComparator;
import oracle.kv.util.migrator.impl.util.OndbUtils.ValidateDDLPrepareCallback;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.StateHandler;
import oracle.nosql.common.migrator.TransformException;
import oracle.nosql.common.migrator.data.Entry;
import oracle.nosql.common.migrator.impl.sink.DataSinkBaseImpl;
import oracle.nosql.common.migrator.util.MigratorUtils;

/**
 * The StoreSink implements loading from DataSources to Ondb using bulk put API
 */
public abstract class OndbSink<T> extends DataSinkBaseImpl {

    final OndbSinkConfig config;
    final KVStore store;
    final TableAPI tableAPI;
    private final Set<String> tablesCreated;
    private final TableFilter tableFilter;

    public OndbSink(OndbSinkConfig config,
                    StateHandler stateHandler,
                    Logger logger) {

        super(ONDB_TYPE, makeName(config), stateHandler, logger);

        this.config = config;
        store = OndbUtils.connectToStore(config.getStoreName(),
                                         config.getHelperHosts(),
                                         config.getUsername(),
                                         config.getSecurity(),
                                         config.getRequestTimeoutMs());
        tableAPI = store.getTableAPI();
        log(Level.INFO, "Connected to target store. storeName=" +
            config.getStoreName() + "; helperHosts=" +
            Arrays.toString(config.getHelperHosts()));

        if (config.getNamespaces() != null || config.getTables() != null) {
            tableFilter = new TableFilter(config.getNamespaces(),
                                          config.getTables());
        } else {
            tableFilter = null;
        }
        tablesCreated = new TreeSet<String>(TableNameComparator.newInstance);
    }

    abstract List<EntryStream<T>> createBulkPutStreams(DataSource[] sources);
    abstract void performBulkPut(List<EntryStream<T>> kvstreams,
                                 BulkWriteOptions bwo);

    private static String makeName(OndbSinkConfig ssc) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String helperHost : ssc.getHelperHosts()) {
            if (first) {
                first = false;
            } else {
                sb.append("-");
            }
            sb.append(helperHost);
        }
        sb.append("-");
        sb.append(ssc.getStoreName());
        return sb.toString();
    }

    @Override
    public void doWrite(DataSource[] sources) {
        log(Level.INFO, "Start loading from " + sources.length + " sources");

        /**
         * Sorts the data source based on its target table level to make sure
         * parent table will be created before child table.
         */
        DataSource[] selectedSources = selectSources(sources);
        selectedSources = sortSources(selectedSources);

        final List<EntryStream<T>> kvstreams =
            createBulkPutStreams(selectedSources);
        if (kvstreams.isEmpty()) {
            logger.log(Level.INFO, "No data source to migrate");
            return;
        }

        final BulkWriteOptions bwo = createBulkWriteOptions(kvstreams.size());
        log(Level.INFO, "Start loading with bulkWriteOptions" +
            "[streamParallelism=" + bwo.getStreamParallelism() +
            ", perShardParallelism=" + bwo.getPerShardParallelism() +
            ", bulkHeapPercent=" + bwo.getBulkHeapPercent() +
            ", requestTimeoutMs=" + bwo.getTimeout() +
            ", overwrite=" + bwo.getOverwrite() + "]");

        performBulkPut(kvstreams, bwo);
    }

    @Override
    protected void setLoaded(DataSource source) {
        if (source.getTargetTable() != null) {
            TableInfo ti = config.getTableInfo(source.getTargetTable());
            String qualifiedName = config.getQualifiedName(ti);
            if (!tablesCreated.contains(qualifiedName)) {
                tablesCreated.add(config.getQualifiedName(ti));
            }
        }
    }

    /**
     * Create table based on the definition in specified TableInfo.
     */
    protected Table createTable(TableInfo ti) {
        /* Check if namespace and table name are valid for OndbSink */
        OndbUtils.validateNamespace(ti.getNamespace());
        OndbUtils.validateTableName(ti.getTableName());

        /* Execute tableStatement and indexStatements if specified */
        return executeDdls(ti);
    }

    /**
     * Returns the buld
     */
    private BulkWriteOptions createBulkWriteOptions(int numStreams) {

        final BulkWriteOptions bwo = new BulkWriteOptions();

        if (config.getPerShardConcurrency() > 0) {
            bwo.setPerShardParallelism(config.getPerShardConcurrency());
        }

        if (config.getRequestTimeoutMs() > 0) {
            bwo.setTimeout(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        }

        if (config.getBulkPutHeapPercent() > 0) {
            bwo.setBulkHeapPercent(config.getBulkPutHeapPercent());
        }

        /*
         * If streamParallelism is not configured, then set its value to
         *  Min(number of streams, number of shards * perShardParallelism)
         */
        int streamParallellism = config.getStreamConcurrency();
        if (streamParallellism <= 0) {
            int numShards =
                ((KVStoreImpl)store).getTopology().getRepGroupMap().size();
            streamParallellism =
                Math.min(numStreams, bwo.getPerShardParallelism() * numShards);
        }
        bwo.setStreamParallelism(streamParallellism);

        bwo.setOverwrite(config.getOverwrite());

        return bwo;
    }

    /**
     * Sorts the data sources according to its target table level.
     *  1. top-level table
     *  2. child table
     *  3. grand child table
     *  4. ...
     */
    private DataSource[] sortSources(DataSource[] sources) {

        Arrays.sort(sources, new Comparator<DataSource>() {
            private final String seperator =
                String.valueOf(NameUtils.CHILD_SEPARATOR);
            private final String pattern = Pattern.quote(seperator);
            @Override
            public int compare(DataSource ds1, DataSource ds2) {
                String table1 = ds1.getTargetTable();
                String table2 = ds2.getTargetTable();
                if (table1 == null) {
                    return (table2 == null) ? 0 : -1;
                } else if (table2 == null) {
                    return 1;
                }
                return table1.split(pattern).length -
                       table2.split(pattern).length;
            }
        });
        return sources;
    }

    /**
     * Select sources using TableFilter with the specified namespaces and tables
     * configured in OndbSinkConfig.
     */
    private DataSource[] selectSources(DataSource[] sources) {
        if (tableFilter == null) {
            return sources;
        }

        List<DataSource> impSources = new ArrayList<DataSource>();
        for (DataSource source : sources) {
            String targetTable = source.getTargetTable();
            if (targetTable != null) {
                TableInfo ti = config.getTableInfo(targetTable);
                if (!tableFilter.matches(config.getQualifiedName(ti))) {
                    continue;
                }
                impSources.add(source);
            }
        }
        return impSources.toArray(new DataSource[impSources.size()]);
    }

    @Override
    public void doPostWork() {
        log(Level.INFO, "Exceute post work...");
        if (config.importAllSchema() &&
            tablesCreated.size() < config.getTableNames().size()) {
            for (String tname : config.getTableNames()) {
                if (tablesCreated.contains(tname)) {
                    continue;
                }
                if (tableFilter == null || tableFilter.matches(tname)) {
                    TableInfo ti = config.getTableInfo(tname);
                    createTable(ti);
                }
            }
            return;
        }
        log(Level.INFO, "No more Table schema to import");
    }

    @Override
    public void close() {
        super.close();
        if (store != null) {
            store.close();
        }
    }

    /**
     * Executes the ddl statements specified in TableInfo.
     *
     * When execute a ddl failed, skip the failure and continue to next ddl
     * if TableInfo.continueOnDdlError is true, otherwise throw exception.
     */
    private Table executeDdls(TableInfo ti) {

        final String qualifiedName = config.getQualifiedName(ti);

        if (!tablesCreated.contains(qualifiedName)) {
            tablesCreated.add(qualifiedName);
            String ns = config.getNamespace(ti);
            final String nsMsg = (ns != null) ? ("[namespace=" + ns + "]") : "";
            ExecuteOptions options = null;
            if (ns != null) {
                ns = NameUtils.switchToInternalUse(ns);
                if (ns != null) {
                    options = new ExecuteOptions().setNamespace(ns);
                }
            }

            final List<String> ddls = getDdls(ti, ns);
            for (String ddl : ddls) {
                try {
                    log(Level.INFO, ddl + nsMsg);
                    store.executeSync(ddl, options);
                } catch (RuntimeException re) {
                    String msg = "Execute \"" + ddl + "\"" + nsMsg + " failed";
                    if (config.getContinueOnDdlError()) {
                        log(Level.WARNING,
                            "[Skipped] " + msg + ": " + re.getMessage());
                        continue;
                    }
                    log(Level.SEVERE, msg, re);
                    throw new RuntimeException(msg + ": " + re.getMessage());
                }
            }
        }
        return tableAPI.getTable(qualifiedName);
    }

    /**
     * Collects the ddls from the specified TableInfo to execute, and do
     * validation check on each statement.
     *
     * If the statement is invalid, skip it if TableInfo.continueOnDdlError
     * is true, otherwise throw exception.
     */
    private List<String> getDdls(TableInfo ti, String namespace) {
        final ValidateDDLPrepareCallback pcb = new ValidateDDLPrepareCallback();
        final ExecuteOptions options = new ExecuteOptions()
            .setPrepareCallback(pcb)
            .setNamespace(namespace);
        final String tableName = ti.getTableName();
        final List<String> ddls = new ArrayList<String>();
        String ddl = null;

        /*
         * Create non-default namespace if needed.
         */
        if (namespace != null &&
            ((TableAPIImpl)tableAPI).getTableMetadata()
                .getNamespace(namespace) == null) {
            ddl = "CREATE NAMESPACE IF NOT EXISTS " + namespace;
            ddls.add(ddl);
        }

        /*
         * Check table statement :
         *  o Only create/alter table statements are allowed here.
         *  o The table statement is for the target table.
         */
        if (ti.getCreateJsonTable()) {
            /* If table already exists, don't generate the default schema */
            if (tableAPI.getTable(config.getQualifiedName(ti)) == null) {
                ddl = makeCreateJsonTableDdl(ti);
            }
        } else {
            ddl = ti.getTableStatement();
            try {
                store.prepare(ddl, options);
                QueryOperation op = pcb.getOperation();
                if (op != QueryOperation.CREATE_TABLE &&
                    op != QueryOperation.ALTER_TABLE) {
                    throw new IllegalArgumentException(
                        "only create table or alter table can be specified " +
                        "with \"tableStatement\"");
                }
                if (!pcb.getTableName().equalsIgnoreCase(tableName)) {
                    throw new IllegalArgumentException(
                        "ddl operation is not for table " + tableName);
                }
            } catch (IllegalArgumentException iae) {
                String msg = "Invalid ddl statement \"" + ddl + "\": " +
                             iae.getMessage();
                if (config.getContinueOnDdlError()) {
                    logger.warning("[Skipped] " + msg);
                    ddl = null;
                } else {
                    throw new IllegalArgumentException(msg);
                }
            }
        }
        if (ddl != null) {
            ddls.add(ddl);
        }

        /*
         * Check index statements :
         *  o Only create/drop index are allowed here.
         *  o The index statement is for the target table.
         */
        if (ti.getIndexStatements() != null) {
            for (String idxDdl : ti.getIndexStatements()) {
                try {
                    store.prepare(idxDdl, options);
                    if (pcb.getOperation() != QueryOperation.CREATE_INDEX &&
                        pcb.getOperation() != QueryOperation.DROP_INDEX) {
                        throw new IllegalArgumentException(
                            "Only create/drop index statement can be " +
                            "specified with \"indexStatements]\": " +
                            pcb.getOperation());
                    }
                    if (!pcb.getTableName().equalsIgnoreCase(tableName)) {
                        throw new IllegalArgumentException(
                            "Not ddl operation for table " + tableName +
                            ", but for table " + pcb.getTableName());
                    }
                } catch (IllegalArgumentException iae) {
                    String msg = "Invalid ddl statement \"" + idxDdl + "\": " +
                                 iae.getMessage();
                    if (config.getContinueOnDdlError()) {
                        logger.warning("[Skipped] " + msg);
                        continue;
                    }
                    throw new IllegalArgumentException(msg);
                }
                ddls.add(idxDdl);
            }
        }
        return ddls;
    }

    /**
     * Make the create table statement: primary key fields + JSON field
     */
    private String makeCreateJsonTableDdl(TableInfo ti) {
        final String tableName = ti.getTableName();
        final Map<String, String> keyFields = config.getPrimaryKeyFields(ti);

        final String jsonField = config.getJsonFieldName(ti);

        final StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(tableName);
        sb.append("(");
        for (Map.Entry<String, String> e : keyFields.entrySet()) {
            sb.append(e.getKey());
            sb.append(" ");
            sb.append(e.getValue());
            sb.append(", ");
        }
        sb.append(jsonField);
        sb.append(" json, primary key(");
        boolean firstField = true;
        for (String field : keyFields.keySet()) {
            if (firstField) {
                firstField = false;
            } else {
                sb.append(", ");
            }
            sb.append(field);
        }
        sb.append("))");
        return sb.toString();
    }

    protected abstract class DataStream<E> implements EntryStream<E> {
        final DataSource source;
        private long readCount;
        private final AtomicLong dupCount;
        private final AtomicLong errCount;
        private long startTimeMs;
        private long endTimeMs;

        public DataStream(DataSource source) {
            this.source = source;
            readCount = 0;
            dupCount = new AtomicLong();
            errCount = new AtomicLong();
            startTimeMs = 0;
        }

        abstract E convertData(Entry entry);
        abstract String keyStringOfData(E data);

        protected Entry readNext() {
            return source.readNext();
        }

        @Override
        public E getNext() {
            /* Records the start time to read first entry */
            if (startTimeMs == 0) {
                startTimeMs = System.currentTimeMillis();
            }

            while (true) {
                Entry entry = null;
                try {
                    entry = readNext();
                    if (entry == null) {
                        return null;
                    }
                    E data = convertData(entry);
                    readCount++;
                    return data;
                } catch (RuntimeException re) {
                    if (re instanceof TransformException) {
                        entry = ((TransformException) re).getDataEntry();
                    }
                    loadError(source, ((entry == null)? "": entry.toString()),
                             "Read next entry failed", re);
                    continue;
                }
            }
        }

        @Override
        public void catchException(RuntimeException re, E data) {
            loadError(source, keyStringOfData(data), re);
            errCount.incrementAndGet();
        }

        @Override
        public void keyExists(E data) {
            loadWarning(source, "key exists", keyStringOfData(data));
            dupCount.incrementAndGet();
        }

        @Override
        public void completed() {
            log(Level.INFO, source.getName() + " done");
            endTimeMs = System.currentTimeMillis();
            setComplete(source, endTimeMs, getNumLoaded(), getStatisticInfo());
            this.close();
        }

        @Override
        public String name() {
            return "Stream " + source.getName();
        }

        public void close() {
        }

        private long getElapseTimeMs() {
            return endTimeMs > 0 ? (endTimeMs - startTimeMs) : 0;
        }

        private long getNumLoaded() {
            return readCount - errCount.get() - dupCount.get();
        }

        private String getStatisticInfo() {
            long timeMs = getElapseTimeMs();
            if (config.getOverwrite()) {
                final String fmt = "Load %,d records (%,d read; %,d failed) " +
                    "from %s: %s";
                return String.format(fmt, getNumLoaded(), readCount,
                                     errCount.get(), source.getName(),
                                     MigratorUtils.formatElapseTime(timeMs));
            }
            final String fmt =
                "Load %,d records (%,d read; %,d pre-existing; %,d failed) " +
                "from %s: %s";
            return String.format(fmt, getNumLoaded(), readCount,
                                 dupCount.get(), errCount.get(),
                                 source.getName(),
                                 MigratorUtils.formatElapseTime(timeMs));
        }
    }

}
