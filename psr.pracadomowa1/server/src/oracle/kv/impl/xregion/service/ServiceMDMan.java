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

package oracle.kv.impl.xregion.service;

import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_AGENT_ID;
import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_CHECKPOINT;
import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_SOURCE_REGION;
import static oracle.kv.impl.xregion.init.TableInitCheckpoint.CKPT_TABLE_WRITE_OPT;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.COMPLETE;

import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import oracle.kv.FaultException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.KVVersion;
import oracle.kv.RequestLimitConfig;
import oracle.kv.StoreIteratorException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.systables.MRTableInitCkptDesc;
import oracle.kv.impl.test.ExceptionTestHook;
import oracle.kv.impl.test.ExceptionTestHookExecute;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.topo.StorageNodeId;
import oracle.kv.impl.util.PollCondition;
import oracle.kv.impl.util.VersionUtil;
import oracle.kv.impl.util.registry.RegistryUtils;
import oracle.kv.impl.xregion.init.TableInitCheckpoint;
import oracle.kv.impl.xregion.stat.TableInitStat.TableInitState;
import oracle.kv.pubsub.NoSQLSubscriberId;
import oracle.kv.table.FieldDef;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;
import oracle.nosql.common.JsonUtils;

/**
 * Object that manages the metadata in {@link XRegionService}.
 */
public class ServiceMDMan {

    /**
     * unit test only, test hook to generate failure. The hook will be called
     * when reading checkpoint from the local store. The arguments pass the
     * name of exception to throw if the hook is fired.
     */
    public static volatile ExceptionTestHook<String, RuntimeException>
        expHook = null;

    /** interval in ms to check table initialization checkpoint */
    private final static int TIC_TABLE_INTV_MS = 5 * 1000;

    /* default collector */
    static final Collector<CharSequence, ?, String> DEFAULT_COLL =
        Collectors.joining(",", "[", "]");

    /* polling internal in ms */
    private static final int POLL_INTERVAL_MS = 1000;

    /* max times to pull table instance from server */
    private static final int MAX_PULL_TABLE_RETRY = 10;

    /* private logger */
    private final Logger logger;

    /* id of the subscriber */
    private final NoSQLSubscriberId sid;

    /* the region this agent serves */
    private final RegionInfo servRegion;

    /* map of regions from config */
    private final Map<String, RegionInfo> regionMap;

    /* all multi-region tables serviced in agent */
    private final ConcurrentMap<String, Table> mrTables;

    /* all PITR tables serviced in agent */
    private final ConcurrentMap<String, Table> pitrTables;

    /* map of regions and kvstore handles */
    private final ConcurrentMap<RegionInfo, KVStore> allKVS;

    /* region id translator */
    private final RegionIdTrans ridTrans;

    /* request response manager */
    private ReqRespManager reqRespManager;

    /* region id mapper */
    private volatile RegionIdMapping ridMapping;

    /* true if closed */
    private volatile boolean closed;

    /* true if in some unit test */
    private final boolean unitTest;

    /**
     * handle to statistics from stats manager
     */
    private volatile XRegionStatistics stats;

    /**
     * json config
     */
    private final JsonConfig jsonConf;

    /**
     * encryption and decryption, null if encryption disabled
     */
    private final AESCipherHelper encryptor;

    /**
     * test hook for unit test only
     */
    private final ConcurrentMap<TableInitState, TestHook<TableInitState>>
        ckptHook = new ConcurrentHashMap<>();

    /**
     * Cached table instances from remote regions, mapped from the remote
     * region name to a map from table name to the table instance. The cached
     * table is added when the table is found from the remote region, and
     * removed when 1) the table is removed from the stream from the remote
     * region or 2) the agent of the remote region is removed when all cached
     * tables from that region are removed.
     */
    private final Map<String, Map<String, Table>> remoteTables;

    /**
     * system table for table initialization checkpoint
     */
    private final Table ticSysTable;

    /**
     * Constructs metadata manager
     *
     * @param sid      subscriber id
     * @param jsonConf json config
     * @param logger   private logger
     */
    public ServiceMDMan(NoSQLSubscriberId sid,
                        JsonConfig jsonConf,
                        Logger logger) {
        this(sid, jsonConf, false, logger);
    }

    /**
     * Constructs metadata manager
     *
     * @param sid      subscriber id
     * @param jsonConf json config
     * @param unitTest true if in unit test
     * @param logger   private logger
     */
    public ServiceMDMan(NoSQLSubscriberId sid,
                        JsonConfig jsonConf,
                        boolean unitTest,
                        Logger logger) {

        this.sid = sid;
        this.jsonConf = jsonConf;
        this.unitTest = unitTest;
        this.logger = logger;

        reqRespManager = null;
        /* create region map */
        regionMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Arrays.stream(jsonConf.getRegions())
              .forEach(r -> regionMap.put(r.getName(), r));

        /* create kvs for served store */
        if (jsonConf.isSecureStore()) {
            final File file = new File(jsonConf.getSecurity());
            if (!file.exists()) {
                throw new IllegalStateException("Security file not found: " +
                                                jsonConf.getSecurity());
            }
        }

        /* create region -> kvs map */
        allKVS = new ConcurrentHashMap<>();
        /* init region to kvs map */
        servRegion = new RegionInfo(jsonConf.getRegion(),
                                    jsonConf.getStore(),
                                    jsonConf.getHelpers());
        if (jsonConf.isSecureStore()) {
            servRegion.setSecurity(jsonConf.getSecurity());
        }
        final KVStore kvs;
        try {
            kvs = createKVS(servRegion);
        } catch (Exception exp) {
            /* cannot reach local region, upper service will retry */
            throw new UnreachableHostRegionException(servRegion, exp);
        }
        allKVS.put(servRegion, kvs);
        /* ensure the table init checkpoint system table exists */
        ticSysTable = getSysTable(kvs.getTableAPI());

        /* will be initialized when used */
        ridMapping = null;

        final TableAPI tableAPI = kvs.getTableAPI();
        final Collection<Table> allTables = tableAPI.getTables().values();
        pitrTables = new ConcurrentHashMap<>();
        fetchPITR(allTables);
        mrTables = new ConcurrentHashMap<>();
        fetchMRT(allTables);

        /* dump all managed tables */
        if (!pitrTables.isEmpty()) {
            allTables.stream().filter(t -> ((TableImpl) t).isPITR())
                     .forEach(t -> pitrTables.put(t.getFullNamespaceName(), t));
            logger.info(lm("PITR: " + pitrTables.keySet().stream()
                                                .collect(DEFAULT_COLL)));
        } else {
            logger.info(lm("PITR tables not found in region=" +
                           servRegion.getName()));
        }

        if (!mrTables.isEmpty()) {
            final StringBuilder sb = new StringBuilder("MRT: ");
            sb.append(mrTables.keySet().stream().collect(DEFAULT_COLL));
            sb.append("\nMRT by region:");
            final Map<RegionInfo, Set<String>> r2tbls = getRegionMRTMap();
            r2tbls.forEach((k, v) -> sb.append("\n")
                                       .append(k.getName()).append(": ")
                                       .append(v));
            logger.info(lm(sb.toString()));
        } else {
            logger.info(lm("Multi-region table not found in region=" +
                           servRegion.getName()));
        }

        final Map<RegionInfo, Set<String>> r2tbls = getRegionMRTMap();
        if (!r2tbls.isEmpty()) {
            logger.fine(lm("Creating KVStore for regions: " +
                           r2tbls.keySet()));
            r2tbls.keySet().forEach(r -> allKVS.put(r, createKVS(r)));
            logger.info(lm("KVStore initialized for regions: " +
                           r2tbls.keySet().stream()
                                 .map(RegionInfo::getName)
                                 .collect(Collectors.toSet())));
        }
        /* init region id translation */
        ridTrans = new RegionIdTrans(this, logger);
        closed = false;
        if (jsonConf.getEncryptTableCheckpoint() && jsonConf.isSecureStore()) {
            final String path = jsonConf.getPasswdFile();
            encryptor = new AESCipherHelper(path, logger);
            logger.info(lm("Encryptor created from file=" + path));
        } else {
            encryptor = null;
        }

        remoteTables = new HashMap<>();
    }

    /**
     * Gets the agent subscriber id
     *
     * @return the agent subscriber id
     */
    public NoSQLSubscriberId getSid() {
        return sid;
    }

    /**
     * Translates the region id from streamed operation to localized region id.
     *
     * @param source source where the op streamed from
     * @param rid    region id in streamed op
     * @return localized region id
     */
    public int translate(String source, int rid) {
        if (unitTest) {
            /* unit test without translation, create a random id > 1 */
            return 2;
        }
        if (!ridTrans.isReady()) {
            throw new IllegalStateException("Region id translation is not " +
                                            "available.");
        }
        return ridTrans.translate(source, rid);
    }

    /**
     * Returns the KVStore handle of the service region
     *
     * @return the KVStore handle of the service region
     */
    public KVStore getServRegionKVS() {
        return allKVS.get(servRegion);
    }

    /**
     * Gets an KVStore handle for a region, or create a new kvstore handle if
     * it does not exist.
     *
     * @param region a region
     * @return kvstore handle of the region
     */
    public KVStore getRegionKVS(RegionInfo region) {
        return allKVS.computeIfAbsent(region, u -> createKVS(region));
    }

    /**
     * Dumps property
     */
    public String dumpProperty(Properties prop) {
        final StringBuilder sp = new StringBuilder("\n");
        for (Object key : prop.keySet()) {
            sp.append(key).append(":")
              .append(prop.getProperty((String) key))
              .append("\n");
        }
        return sp.toString();
    }

    /**
     * Shuts down the metadata manger. Clear all cached tables and close all
     * kvstore handles.
     */
    public void shutdown() {
        closed = true;
        allKVS.values().forEach(KVStore::close);
        pitrTables.clear();
        mrTables.clear();
        ridTrans.close();
        logger.fine(() -> lm("Service md manager " + sid + " has closed"));
    }

    /**
     * Return existing tables at source region within timeout
     *
     * @param source    source regions
     * @param tables    tables
     * @param timeoutMs timeout in ms
     * @return set of found tables, or empty set if no table is found
     */
    public Set<String> tableExists(RegionInfo source,
                                   Set<String> tables,
                                   long timeoutMs) {
        final Set<String> found = new HashSet<>();
        final boolean succ =
            new PollCondition(POLL_INTERVAL_MS, timeoutMs) {
                @Override
                protected boolean condition() {
                    found.addAll(checkTable(source, tables));
                    return tables.equals(found);
                }
            }.await();
        if (!succ) {
            final String err = "Timeout (" + timeoutMs +
                               " ms) in waiting for tables=" + tables +
                               " to appear in region=" + source +
                               ", found=" + found;
            logger.fine(() -> lm(err));
        }

        return found;
    }

    /**
     * Checks if tables have compatible schema at source and target
     *
     * @param source source region
     * @param tables tables to check
     * @return null if all tables have compatible schema, or description of
     * incompatible tables
     */
    public String matchingSchema(RegionInfo source, Set<String> tables) {
        final Map<String, String> incompatible = new HashMap<>();
        for (String t : tables) {
            final Table tgtTable =
                getTableRetry(getServRegionKVS().getTableAPI(), t, 30, logger);
            final Table srcTable =
                getTableRetry(getRegionKVS(source).getTableAPI(), t, 30,
                              logger);
            if (tgtTable == null || srcTable == null) {
                final String reason = "Table not found: source=" +
                                      (srcTable == null ? "not found" : "ok") +
                                      ", target=" +
                                      (tgtTable == null ? "not found" : "ok");
                logger.warning(lm(reason));
                incompatible.put(t, reason);
                continue;
            }

            final String reason = compatibleSchema(srcTable, tgtTable);
            if (reason != null) {
                incompatible.put(t, reason);
            }
        }

        if (!incompatible.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            incompatible.forEach((key, value) ->
                                     sb.append("table=").append(key)
                                       .append(", ").append(value));
            return sb.toString();
        }

        /* all table schema compatible */
        return null;
    }

    /**
     * Returns true if the primary key of actual table matches that of
     * expected table, or false otherwise.
     *
     * @param expected expected table
     * @param actual   actual table
     * @return Returns true if the primary key matches between given tables, or
     * false otherwise.
     */
    public boolean isPKeyMatch(Table expected, Table actual) {

        final List<String> expPk = expected.getPrimaryKey();
        final List<String> actPk = actual.getPrimaryKey();

        if (expPk.size() != actPk.size()) {
            logger.fine(lm("Mismatch # cols in primary key " +
                           ", expect " + expPk.size() +
                           ", while actual " + actPk.size()));
            return false;
        }

        /* name of col must match */
        if (!actPk.containsAll(expPk)) {
            logger.fine(lm("Mismatch col names in primary key " +
                           ", expect " + expPk +
                           ", while actual " + actPk));
            return false;
        }

        /* col type must match */
        final Set<String> mismach = expPk.stream().filter(
            col -> !expected.getField(col).getType()
                            .equals(actual.getField(col).getType()))
                                         .collect(Collectors.toSet());
        if (!mismach.isEmpty()) {
            logger.fine(lm("Mismatch type for col " + mismach));
            return false;
        }

        return true;
    }

    /**
     * Returns the configuration from JSON
     *
     * @return the configuration from JSON
     */
    public JsonConfig getJsonConf() {
        return jsonConf;
    }

    /**
     * Checks if source and target table have compatible schemas
     *
     * @param src source table
     * @param tgt target table
     * @return null if compatible, or a description of incompatibility
     */
    private String compatibleSchema(Table src, Table tgt) {
        /* primary key must match */
        if (!isPKeyMatch(src, tgt)) {
            return "Primary key does not match, source pkey hash=" +
                   src.createPrimaryKey().toJsonString(false).hashCode() +
                   ", target pkey hash=" +
                   tgt.createPrimaryKey().toJsonString(false).hashCode();
        }

        /* check other fields */
        final List<String> srcCols = src.getFields();
        final List<String> tgtCols = tgt.getFields();
        /* for common col name, type must match */
        final List<String> intersect = srcCols.stream()
                                              .filter(tgtCols::contains)
                                              .collect(Collectors.toList());
        for (String col : intersect) {
            final FieldDef srcFd = src.getField(col);
            final FieldDef tgtFd = tgt.getField(col);
            if (!srcFd.equals(tgtFd)) {
                return "col=" + col +
                       ", type at source=" + srcFd.getType() +
                       ", type at target=" + tgtFd.getType();
            }
        }
        return null;
    }

    /**
     * Sets request response manager
     *
     * @param manager request response manager
     */
    void setReqRespManager(ReqRespManager manager) {
        reqRespManager = manager;
    }

    /**
     * Returns the MRT instance by its table id, or null if the table does
     * not exist in metadata manager.
     *
     * @param tableId id of table
     * @return the MRT instance, or null
     */
    Table getMRTById(long tableId) {
        for (Table t : mrTables.values()) {
            if (((TableImpl) t).getId() == tableId) {
                return t;
            }
        }
        return null;
    }

    /**
     * Returns region with given region name, or null if the region does not
     * exist
     *
     * @param regionName region name
     * @return region
     */
    RegionInfo getRegion(String regionName) {
        return regionMap.get(regionName);
    }

    /**
     * Gets the set of source regions for multi-region tables
     *
     * @return the set of source regions
     */
    public Set<RegionInfo> getSrcRegionsForMRT() {
        return getRegionMRTMap().keySet();
    }

    /**
     * Gets the region that the agent services
     *
     * @return the region that the agent services
     */
    public RegionInfo getServRegion() {
        return servRegion;
    }

    /**
     * Returns a map from region to a set of mrt on that region
     */
    public Map<RegionInfo, Set<String>> getRegionMRTMap() {
        final Map<RegionInfo, Set<String>> r2t = new TreeMap<>();
        for (Table t : mrTables.values()) {
            final Set<Integer> ids = ((TableImpl) t).getRemoteRegions();
            final Set<String> src = new HashSet<>();
            for (int id : ids) {
                final String region = getRegionName(id);
                if (region == null) {
                    final String err = "Unknown region in table md, " +
                                       " region id=" + id +
                                       " table=" + t.getFullNamespaceName();
                    logger.warning(lm(err));
                    throw new ServiceException(sid, err);
                }
                src.add(region);
            }

            src.stream()
               .map(regionMap::get)
               .forEach(r -> r2t.computeIfAbsent(r, u -> new HashSet<>())
                                .add(t.getFullNamespaceName()));

        }
        return r2t;
    }

    /**
     * Returns region name from region id, or null if region id is unknown
     *
     * @param id region id
     * @return region name or null
     */
    String getRegionName(int id) {
        if (ridMapping == null) {
            final KVStore kvs = getServRegionKVS();
            ridMapping =
                new RegionIdMapping(servRegion.getName(), kvs, sid, logger);
            if (ridMapping.getKnownRegions() == null) {
                return null;
            }
            final Set<String> sb = new HashSet<>();
            ridMapping.getKnownRegions().values()
                      .forEach(r -> {
                          final String map =
                              r + " -> " + ridMapping.getRegionIdByName(r);
                          sb.add(map);
                      });
            logger.info(lm("Create region id mapping for served region=" +
                           servRegion.getName() + ", mapping=" + sb));
        }
        return ridMapping.getRegionName(id);
    }

    /**
     * Adds or updates multi-region tables
     *
     * @param tables multi-region tables to add
     */
    public void addUpdateMRTable(Set<Table> tables) {
        if (closed) {
            throw new IllegalStateException("Metadata manager closed");
        }
        if (tables.isEmpty()) {
            return;
        }
        final Set<String> tbls = new HashSet<>();
        for (Table table : tables) {
            final String t = table.getFullNamespaceName();
            mrTables.put(t, table);
            stats.getOrCreateTableMetrics(t, ((TableImpl) table).getId());
            tbls.add(t);
        }
        if (!tbls.isEmpty()) {
            logger.fine(lm("MRT " + tbls + " added or updated"));
        }
    }

    /**
     * Removes multi-region tables
     *
     * @param tables multi-region tables to remove
     */
    public void removeMRTable(Set<Table> tables) {
        if (closed) {
            throw new IllegalStateException("Metadata manager closed");
        }
        if (tables.isEmpty()) {
            return;
        }
        final Set<String> tableNames =
            tables.stream().map(Table::getFullNamespaceName)
                  .collect(Collectors.toSet());
        final Set<String> tbls =
            tableNames.stream().filter(mrTables::containsKey)
                      .collect(Collectors.toSet());
        tbls.forEach(t -> {
            mrTables.remove(t);
            remoteTables.values().forEach(map -> map.remove(t));
            stats.removeTableMetrics(t);
        });
        logger.fine(() -> lm("Table=" + tbls + " removed from stats and md " +
                             "manager, its remote instances are cleared."));

        /* get set of non-served regions from which no table is streamed */
        final Map<RegionInfo, Set<String>> r2tbls = getRegionMRTMap();
        final Set<RegionInfo> noStreamRegion =
            allKVS.keySet().stream()
                  .filter(r -> !r2tbls.containsKey(r) && !r.equals(servRegion))
                  .collect(Collectors.toSet());

        noStreamRegion.forEach(r -> {
            /* this region no longer has any mrt */
            allKVS.get(r).close();
            allKVS.remove(r);
            logger.fine(() -> lm("KVStore to region=" + r.getName() +
                                 " closed."));
        });
    }

    /**
     * Adds PITR tables
     *
     * @param tables tables to add
     */
    public void addPITRTable(Set<Table> tables) {
        if (closed) {
            throw new IllegalStateException("Metadata manager closed");
        }
        if (tables.isEmpty()) {
            return;
        }
        final Set<String> tableNames =
            tables.stream().map(Table::getFullNamespaceName)
                  .collect(Collectors.toSet());
        final Set<String> toAdd =
            tableNames.stream().filter(t -> !pitrTables.containsKey(t))
                      .collect(Collectors.toSet());
        final TableAPI api = getServRegionKVS().getTableAPI();
        for (String t : toAdd) {
            final Table table = getTableRetry(api, t, 1, logger);
            if (table == null) {
                final String err = "PITR Table " + t + " cannot be found";
                logger.warning(lm(err));
                continue;
            }
            pitrTables.put(t, table);
        }
        logger.fine(lm("PITR table" + tableNames + " added"));
    }

    /**
     * Removes a PITR table
     *
     * @param tables PITR tables to remove
     */
    public void removePITRTable(Set<Table> tables) {
        if (closed) {
            throw new IllegalStateException("Metadata manager closed");
        }
        if (tables.isEmpty()) {
            return;
        }
        final Set<String> tableNames =
            tables.stream().map(Table::getFullNamespaceName)
                  .collect(Collectors.toSet());
        final Set<String> toRemove =
            tableNames.stream().filter(pitrTables::containsKey)
                      .collect(Collectors.toSet());
        toRemove.forEach(pitrTables::remove);
        logger.fine(lm("PITR table" + tableNames + " removed"));
    }

    /**
     * Gets a set of PITR table names
     *
     * @return PITR tables
     */
    public Set<String> getPITRTables() {
        return pitrTables.keySet();
    }

    /**
     * Gets a set of MR table names
     *
     * @return MRT table names
     */
    public Set<String> getMRTNames() {
        return mrTables.keySet();
    }

    /**
     * Gets a collection of MR tables
     *
     * @return MRT tables
     */
    public Collection<Table> getMRTables() {
        return mrTables.values();
    }

    /**
     * Returns the MR table instance
     *
     * @param tbName table name
     * @return table instance
     */
    public Table getMRT(String tbName) {
        Table ret;
        int attempts = 0;
        while (true) {
            attempts++;
            ret = mrTables.computeIfAbsent(
                tbName,
                u -> getTableRetry(
                    getServRegionKVS().getTableAPI(), tbName, 1, logger));
            if (ret != null) {
                return ret;
            }
            if (attempts == MAX_PULL_TABLE_RETRY) {
                /*
                 * cannot get table instance, check if it is dropped in
                 * the request table
                 */
                if (reqRespManager != null &&
                    reqRespManager.hasDropRequest(tbName)) {
                    return null;
                }
                /* table not dropped, just bad luck, retry */
                attempts = 0;
            }
        }
    }

    /**
     * Refreshes the table instance from local store
     * @param tbName table name
     * @return table instance
     */
    public Table refreshMRT(String tbName) {
        final TableImpl curr = (TableImpl) getMRT(tbName);
        final TableImpl refresh = (TableImpl) getTableRetry(
            getServRegionKVS().getTableAPI(), tbName, Long.MAX_VALUE, logger);
        mrTables.put(tbName, refresh);
        logger.info(lm("Refreshed table=" + tbName +
                       "(id=" + refresh.getId() + ", ver=" +
                       refresh.getTableVersion() +") from " +
                       "table=" + curr.getFullNamespaceName() +
                       "(id=" + curr.getId() +
                       ", ver=" + curr.getTableVersion() + ")"));
        return refresh;
    }

    /**
     * Returns the table id for given table
     *
     * @param tableName table name
     * @return table id
     */
    public long getTableId(String tableName) {
        final Table tbl = getMRT(tableName);
        if (tbl != null) {
            return ((TableImpl) tbl).getId();
        }
        throw new IllegalArgumentException("Table=" + tableName + " not found" +
                                           " in service metadata manager");
    }

    /**
     * Invalidates MR table instance
     *
     * @param tbName table name
     * @return invalidated table instance
     */
    public Table invalidateMRT(String tbName) {
        return mrTables.remove(tbName);
    }

    /**
     * Updates the MR table instances. A table is updated when the new table
     * version is higher than the existing one.
     *
     * @param tables MR table instances
     */
    synchronized void updateMRTable(Set<Table> tables) {
        for (Table t : tables) {
            final Table exist = mrTables.get(t.getFullNamespaceName());
            if (exist == null) {
                mrTables.put(t.getFullNamespaceName(), t);
                logger.info(lm("Add table=" + t.getFullNamespaceName() +
                               ", remote regions=" +
                               ((TableImpl) t).getRemoteRegions().stream()
                                              .map(this::getRegionName)
                                              .collect(Collectors.toSet())));
                continue;
            }

            /* update if newer */
            final int existTableVer = exist.getTableVersion();
            final int tableVer = t.getTableVersion();
            if (tableVer >= existTableVer) {
                mrTables.put(t.getFullNamespaceName(), t);
                logger.info(lm("Update table=" + t.getFullNamespaceName() +
                               " with version=" + tableVer +
                               ", old version=" + existTableVer +
                               ", remote regions in old version =" +
                               ((TableImpl) exist).getRemoteRegions().stream()
                                                  .map(this::getRegionName)
                                                  .collect(Collectors.toSet()) +
                               ", remote regions in new version =" +
                               ((TableImpl) t).getRemoteRegions().stream()
                                              .map(this::getRegionName)
                                              .collect(Collectors.toSet())));
            }
        }
    }

    /**
     * Sets the statistics
     */
    void setStats(XRegionStatistics stats) {
        if (stats == null) {
            throw new IllegalArgumentException("Null statistics");
        }
        this.stats = stats;
    }

    /**
     * Unit test only
     */
    RegionIdTrans getRidTrans() {
        return ridTrans;
    }

    /*----------- PRIVATE FUNCTIONS --------------*/
    private String lm(String msg) {
        return "[MdMan-" + sid + "] " + msg;
    }

    /**
     * Creates kvstore handle to a region
     *
     * @param region region to create kvstore
     * @return kvstore handle
     */
    private KVStore createKVS(RegionInfo region) {
        final KVStore ret = createKVS(region.getStore(),
                                      region.getHelpers(),
                                      (region.isSecureStore() ?
                                          new File(region.getSecurity()) :
                                          null));
        if (!ensureEE(ret, region)) {
            final String err =
                "XRegion Service cannot connect to region=" + region.getName() +
                ", store=" + region.getStore() +
                " because at least one SN is not on Enterprise Edition.";
            logger.warning(lm(err));
            throw new IllegalStateException(err);
        }
        // TODO - investigate using the cache within the table API
        /* Service maintains it's own cache */
        ((TableAPIImpl)ret.getTableAPI()).setCacheEnabled(false);
        return ret;
    }

    /**
     * Creates kvstore handle to a region
     *
     * @param storeName    store name
     * @param helpers      helper hosts
     * @param securityFile security file, or null if non-secure store
     * @return kvstore handle
     */
    private KVStore createKVS(String storeName,
                              String[] helpers,
                              File securityFile) {

        final KVStoreConfig conf = new KVStoreConfig(storeName, helpers);
        conf.setRequestLimit(
            new RequestLimitConfig(
                jsonConf.getNumConcurrentStreamOps(),
                RequestLimitConfig.DEFAULT_REQUEST_THRESHOLD_PERCENT,
                RequestLimitConfig.DEFAULT_NODE_LIMIT_PERCENT));
        final boolean security = (securityFile != null);
        if (security) {
            final Properties sp =
                XRegionService.setSecureProperty(conf, securityFile);
            logger.fine(lm("Set security property: " + dumpProperty(sp)));
        }

        try {
            final KVStore ret = KVStoreFactory.getStore(conf);
            logger.fine(lm("KVStore created for store " + conf.getStoreName()));
            return ret;
        } catch (Exception ex) {
            final String msg = "Cannot connect Oracle NoSQL store " +
                               conf.getStoreName() +
                               ", error: " + ex.getMessage();
            logger.warning(lm(msg));
            throw new IllegalStateException(msg, ex);
        }
    }

    /**
     * Fetches all multi-region tables, filter out all MR tables that not
     * subscribing any remote region
     *
     * @param tables all tables
     */
    private void fetchMRT(Collection<Table> tables) {
        /* dump MRT with local region only */
        final Set<String> mrtNoRemote =
            tables.stream().filter(this::isMRTLocalOnly)
                  .map(Table::getFullNamespaceName)
                  .collect(Collectors.toSet());
        if (!mrtNoRemote.isEmpty()) {
            logger.info(lm("MRT without remote region=" + mrtNoRemote));
        }

        tables.stream()
              .filter(t -> ((TableImpl) t).isMultiRegion())
              .filter(t -> !isMRTLocalOnly(t))
              .forEach(t -> mrTables.put(t.getFullNamespaceName(), t));
    }

    /**
     * Returns true if the table is MR table defined on local region only
     *
     * @param table given table
     * @return true if the table is MR table defined on local region only
     */
    private boolean isMRTLocalOnly(Table table) {
        final TableImpl t = (TableImpl) table;
        return t.isMultiRegion() && t.getRemoteRegions().isEmpty();
    }

    /**
     * Fetches all PITR tables
     *
     * @param tables all PITR tables
     */
    private void fetchPITR(Collection<Table> tables) {
        logger.finest(lm("PITR is not supported, tables=" + tables));
    }

    /**
     * Returns true if all table exist
     *
     * @param region source region
     * @param tbls   tables
     * @return set of found tables, or empty set if no table is found
     */
    public Set<String> checkTable(RegionInfo region, Set<String> tbls) {
        final KVStoreImpl kvs = (KVStoreImpl) getRegionKVS(region);
        final TableMetadata md = kvs.getDispatcher().getTableMetadata(kvs);
        /* return found tables */
        final Set<String> found = new HashSet<>();
        for (String table : tbls) {
            final TableImpl tb = md.getTable(table);
            if (tb == null) {
                /* not found */
                continue;
            }

            /* remember the table instance */
            remoteTables.computeIfAbsent(region.getName(), u -> new HashMap<>())
                        .put(table, tb);
            logger.fine(() -> lm("Add remote table=" + table + "(id=" +
                                 tb.getId() + ") from region=" +
                                 region.getName()));
            found.add(table);
        }
        return found;
    }

    /**
     * Returns the cached table instance from remote region, or null if the
     * table is not found in cache.
     *
     * @param region remote region name
     * @param table  table name
     * @return table instance from given region
     */
    public Table getRemoteTable(String region, String table) {
        final Map<String, Table> tbls = remoteTables.get(region);
        if (tbls == null) {
            return null;
        }
        return tbls.get(table);
    }

    /**
     * Removes cached remote table instance
     *
     * @param region remote region name
     * @param table  table name
     */
    public void removeRemoteTables(String region, String table) {
        final Map<String, Table> tbls = remoteTables.get(region);
        if (tbls == null) {
            return;
        }
        tbls.remove(table);
    }

    /**
     * Removes all cached tables from the remote region
     *
     * @param region remote region name
     */
    public void removeRemoteTables(String region) {
        remoteTables.remove(region);
    }

    /* Exception when the host region is not reachable */
    public static class UnreachableHostRegionException
        extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final RegionInfo region;

        UnreachableHostRegionException(RegionInfo region, Throwable cause) {
            super(cause);
            this.region = region;
        }

        public RegionInfo getRegion() {
            return region;
        }
    }

    /**
     * Returns true if all SNs at a store are on EE, false otherwise
     *
     * @param kvs    kvstore
     * @param region region
     * @return true if all SNs at a store are on EE, false otherwise
     */
    private boolean ensureEE(KVStore kvs, RegionInfo region) {
        final RegistryUtils regUtil =
            ((KVStoreImpl) kvs).getDispatcher().getRegUtils();
        if (regUtil == null) {
            final String msg = "The request dispatcher has not initialized " +
                               "itself yet, region=" + region.getName() +
                               ", store=" + region.getStore();
            logger.warning(lm(msg));
            return false;
        }
        /* Since SNs can be mixed, ping each SN */
        for (StorageNodeId snId : regUtil.getTopology().getStorageNodeIds()) {
            try {
                final KVVersion ver = regUtil.getStorageNodeAgent(snId)
                                             .ping().getKVVersion();
                if (!VersionUtil.isEnterpriseEdition(ver)) {
                    logger.warning(lm("In region=" + region.getName() +
                                      ", store=" + region.getStore() +
                                      ", sn=" + snId +
                                      " is not running Enterprise Edition, " +
                                      "ver=" + ver));
                    return false;
                }
            } catch (RemoteException | NotBoundException e) {
                logger.fine(() -> lm("Cannot reach region=" + region.getName() +
                                     ", store= " + region.getStore() +
                                     ", sn=" + snId));
            }
        }
        return true;
    }

    /**
     * Gets table with retry from store. In some cases, table exists in store
     * but TableAPI.getTable() may return null. Retry till the table is
     * found, or it has reached max attempts. If it is unable to retrieve the
     * table because of {@link FaultException}, it would retry as well.
     *
     * @param tableAPI table api
     * @param table    table name
     * @param max      max number of retry
     * @param logger   logger
     * @return table instance, or null if max attempts is reached
     */
    public static Table getTableRetry(TableAPI tableAPI, String table,
                                      long max, Logger logger) {
        Table ret = null;
        int count = 0;
        while (ret == null && count < max) {
            count++;
            try {
                ret = tableAPI.getTable(table);
            } catch (FaultException fe) {
                logger.fine(() -> "Retry get table on fault exception " +
                                  fe.getMessage());
            }
        }
        return ret;
    }

    /**
     * Writes table initialization checkpoint with given state, if fail to
     * write the checkpoint, log the failure
     */
    public boolean writeTableInitCkpt(String sourceRegion,
                                      String tableName,
                                      PrimaryKey pkey,
                                      TableInitState state,
                                      String errMsg) {
        final TableInitCheckpoint ckpt =
            buildCkpt(sourceRegion, tableName, pkey, state);
        ckpt.setMessage(errMsg);
        if (pkey != null) {
            ckpt.setPrimaryKey(encrypt(pkey.toJsonString(false)));
        }
        final TableAPI tableAPI = getServRegionKVS().getTableAPI();
        if (!TableInitCheckpoint.write(tableAPI, ckpt, logger).isPresent()) {
            logger.info(lm("Fail to write checkpoint=" + ckpt));
            return false;
        }
        logger.fine(() -> lm("Write checkpoint=" + ckpt));
        assert TestHookExecute.doHookIfSet(ckptHook.get(state), state);
        return true;
    }

    /**
     * Reads table initialization checkpoint with given source region and
     * table name
     *
     * @param src   source region
     * @param table table name
     * @return checkpoint or null if not exist
     */
    public TableInitCheckpoint readTableInitCkpt(String src, String table) {
        final TableAPI tapi = getServRegionKVS().getTableAPI();
        final Optional<TableInitCheckpoint> opt =
            TableInitCheckpoint.read(tapi, sid.toString(), src, table, logger);
        if (!opt.isPresent()) {
            return null;
        }
        final TableInitCheckpoint ckpt = opt.get();
        if (ckpt.getPrimaryKey() != null) {
            ckpt.setPrimaryKey(decrypt(ckpt.getPrimaryKey()));
        }
        return ckpt;
    }

    /**
     * Deletes checkpoint from system table
     *
     * @param region source region
     * @param table  table name
     */
    public void delTableInitCkpt(String region, String table) {
        final TableAPI tableAPI = getServRegionKVS().getTableAPI();
        final String agentId = sid.toString();
        TableInitCheckpoint.del(tableAPI, agentId, region, table, logger);
    }

    /**
     * Deletes all table checkpoints for a given region
     *
     * @param region region name
     */
    public void delAllInitCkpt(String region) {
        final TableAPI tableAPI = getServRegionKVS().getTableAPI();
        final String agentId = sid.toString();
        final TableIterator<Row> itr =
            tableAPI.tableIterator(ticSysTable.createPrimaryKey(), null, null);
        while (itr.hasNext()) {
            final Row row = itr.next();
            final String id = row.get(COL_NAME_AGENT_ID).asString().get();
            final String src = row.get(COL_NAME_SOURCE_REGION).asString().get();
            if (src.equals(region) && id.equals(agentId)) {
                /* must ensure the checkpoint is deleted */
                deleteRetry(row.createPrimaryKey());
            }
        }
    }

    /**
     * Gets the set of tables that need to resume initialization from the
     * remote region
     *
     * @param region remote region
     * @return set of tables
     */
    public Set<String> getTablesResumeInit(String region) {
        final Set<String> ret = new HashSet<>();
        final Set<TableInitCheckpoint> ckpts = readInitCkptWithRetry(region);
        for (TableInitCheckpoint ckpt : ckpts) {
            final String tableName = ckpt.getTable();
            final TableImpl table = (TableImpl) getMRT(tableName);
            if (table == null) {
                /* the table has been dropped, delete ckpt */
                delTableInitCkpt(region, tableName);
                continue;
            }

            final boolean foundRegion =
                table.getRemoteRegions().stream().map(this::getRegionName)
                     .anyMatch(region::equals);
            if (!foundRegion) {
                /* the table no longer stream from the region, delete ckpt */
                delTableInitCkpt(region, tableName);
                continue;
            }

            if (ckpt.getState().equals(COMPLETE)) {
                /* table initialization is complete */
                continue;
            }

            /* table need to resume from checkpoint */
            ret.add(tableName);
        }
        return ret;
    }

    /**
     * Reads all table init checkpoints for a given region, retry if running
     * into error
     * @param region remote region
     * @return set of table checkpoints
     */
    private Set<TableInitCheckpoint> readInitCkptWithRetry(String region) {
        while (true) {
            try {
                return readAllInitCkpt(region);
            } catch (StoreIteratorException | FaultException exp) {
                final Throwable cause = exp.getCause();
                logger.info(lm(
                    "Rescan the table checkpoints, " + exp.getMessage() +
                    (cause != null ? ", " + cause.getMessage() : "")));
            }
        }
    }

    /**
     * Writes NOT_START checkpoint. For correctness, the NOT_START checkpoint
     * has to be persisted before the table starts initialization, otherwise
     * the agent may not be able to resume initialization from failure.
     *
     * @param region source region
     * @param table  table name
     * @param pkey   primary key
     * @param st     state
     */
    public void writeCkptRetry(String region, String table,
                               PrimaryKey pkey, TableInitState st) {
        writeDelCkptRetry(region, table, pkey, st, false);
    }

    /**
     * Deletes the checkpoint row with retry
     */
    public void deleteRetry(PrimaryKey pkey) {
        writeDelCkptRetry(null, null, pkey, null, true);
    }

    /**
     * Writes or deletes a checkpoint row with retry
     */
    private void writeDelCkptRetry(String region, String table,
                                   PrimaryKey pkey, TableInitState st,
                                   boolean delete) {
        int count = 0;
        do {
            count++;
            try {
                if (delete) {
                    final TableAPI api = getServRegionKVS().getTableAPI();
                    if (api.delete(pkey, null, CKPT_TABLE_WRITE_OPT)) {
                        return;
                    }
                } else {
                    if (writeTableInitCkpt(region, table, pkey, st, null)) {
                        return;
                    }
                }
            } catch (Exception exp) {
                final String err = "Fail to " + (delete ? "delete" : "write") +
                                   " init checkpoint in attempts=" + count +
                                   ", will retry, " + exp.getMessage();
                logger.warning(lm(err));
            }
        } while (true);
    }

    /**
     * Reads all table checkpoint for given region
     */
    private Set<TableInitCheckpoint> readAllInitCkpt(String region) {
        final TableAPI tableAPI = getServRegionKVS().getTableAPI();
        final String agentId = sid.toString();
        final Set<TableInitCheckpoint> ret = new HashSet<>();
        /* test hook to throw FaultException in creating table iterator */
        fireExpTestTook(FaultException.class.getSimpleName());
        final TableIterator<Row> itr =
            tableAPI.tableIterator(ticSysTable.createPrimaryKey(), null, null);
        while (itr.hasNext()) {
            /* test hook to throw store iterator exception in iteration */
            fireExpTestTook(StoreIteratorException.class.getSimpleName());
            final Row row = itr.next();
            final String src = row.get(COL_NAME_SOURCE_REGION).asString().get();
            final String id = row.get(COL_NAME_AGENT_ID).asString().get();
            if (region.equals(src) && agentId.equals(id)) {
                final String json =
                    row.get(COL_NAME_CHECKPOINT).asString().get();
                try {
                    final TableInitCheckpoint ckpt =
                        JsonUtils.readValue(json, TableInitCheckpoint.class);
                    ret.add(ckpt);
                } catch (IOException e) {
                    logger.warning(lm("Problem reading checkpoint for" +
                                      ", agent id=" + agentId +
                                      ", region=" + region +
                                      ", json string=" + json +
                                      ", " + e.getMessage()));
                }
            }
        }
        return ret;
    }

    private void fireExpTestTook(String exp) {
        assert ExceptionTestHookExecute.doHookIfSet(expHook, exp);
    }

    private Table getSysTable(TableAPI tableAPI) throws IllegalStateException {
        while(true) {
            final Table ret = ServiceMDMan.getTableRetry(
                tableAPI, MRTableInitCkptDesc.TABLE_NAME, 1, logger);
            if (ret != null) {
                return ret;
            }
            logger.info(lm("Table=" + MRTableInitCkptDesc.TABLE_NAME +
                           " not found at local region=" +
                           servRegion.getName() + ", waiting..."));
            try {
                Thread.sleep(TIC_TABLE_INTV_MS);
            } catch (InterruptedException exp) {
                final String err = "Interrupted in waiting for table=" +
                                   MRTableInitCkptDesc.TABLE_NAME;
                logger.warning(lm(err));
                throw new IllegalStateException(err, exp);
            }
        }
    }

    private TableInitCheckpoint buildCkpt(String sourceRegion,
                                          String tableName,
                                          PrimaryKey pkey,
                                          TableInitState st) {
        final String agentId = sid.toString();
        final String targetRegion = servRegion.getName();
        final String json = (pkey == null) ? null : pkey.toJsonString(false);
        return new TableInitCheckpoint(agentId, sourceRegion, targetRegion,
                                       tableName, json,
                                       System.currentTimeMillis(), st);
    }

    private String encrypt(String msg) {
        if (encryptor == null) {
            return msg;
        }
        return encryptor.encrypt(msg);
    }

    private String decrypt(String msg) {
        if (encryptor == null) {
            return msg;
        }
        return encryptor.decrypt(msg);
    }

    /**
     * unit test only
     */
    public void setCkptHook(TableInitState st, TestHook<TableInitState> hook) {
        ckptHook.put(st, hook);
    }

    /**
     * unit test only
     */
    public TestHook<TableInitState> getCkptHook(TableInitState state) {
        return ckptHook.get(state);
    }

    /**
     * Unit test only
     */
    public AESCipherHelper getEncryptor() {
        return encryptor;
    }

    /**
     * Unit test only
     */
    public Table getTicSysTable() {
        return ticSysTable;
    }
}
