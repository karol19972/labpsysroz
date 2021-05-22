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

package oracle.kv.impl.pubsub;

import static com.sleepycat.je.log.LogEntryType.LOG_DEL_LN;
import static com.sleepycat.je.log.LogEntryType.LOG_DEL_LN_TRANSACTIONAL;
import static com.sleepycat.je.log.LogEntryType.LOG_INS_LN;
import static com.sleepycat.je.log.LogEntryType.LOG_INS_LN_TRANSACTIONAL;
import static com.sleepycat.je.log.LogEntryType.LOG_TRACE;
import static com.sleepycat.je.log.LogEntryType.LOG_TXN_ABORT;
import static com.sleepycat.je.log.LogEntryType.LOG_TXN_COMMIT;
import static com.sleepycat.je.log.LogEntryType.LOG_UPD_LN;
import static com.sleepycat.je.log.LogEntryType.LOG_UPD_LN_TRANSACTIONAL;
import static com.sleepycat.je.utilint.VLSN.FIRST_VLSN;
import static com.sleepycat.je.utilint.VLSN.INVALID_VLSN;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.Key;
import oracle.kv.Value;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.map.HashKeyToPartitionMap;
import oracle.kv.impl.map.KeyToPartitionMap;
import oracle.kv.impl.rep.migration.generation.PartitionGenDBManager;
import oracle.kv.impl.rep.migration.generation.PartitionGeneration;
import oracle.kv.impl.rep.table.TableManager.IDBytesComparator;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.util.TxnUtil;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.entry.LNEntryInfo;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.stream.FeederFilter;
import com.sleepycat.je.rep.stream.FeederFilterChange;
import com.sleepycat.je.rep.stream.FeederFilterChangeResult;
import com.sleepycat.je.rep.stream.FeederReplicaSyncup;
import com.sleepycat.je.rep.stream.OutputWireRecord;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.VLSN;
import com.sleepycat.util.UtfOps;

/**
 * Object represents a feeder filter that will be constructed, serialized
 * and sent to the source feeder over the wire by NoSQLPublisher. At feeder
 * side, the filter is deserialized and rebuilt.
 *
 * Following entries will be filtered out by the feeder:
 * - entry from an internal db;
 * - entry from a db supporting duplicates;
 * - entry from any non-subscribed tables (table-level subscription filtering)
 *
 * Note
 * [] The partition generation db is not an internal db and all entries
 * from that db would pass the filter as entries to subscribed tables.
 *
 * [] The logger will be set by JE feeder during initialization. A JE logger
 * will be used in the class and therefore we adopt the JE logging style
 * instead of the KV style in the filter.
 */
public class NoSQLStreamFeederFilter implements FeederFilter, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * stat trace interval in seconds
     */
    private static final long STAT_INTERVAL_SECS = 10 * 60;

    /**
     * Set of table ID names for subscribed tables, or null for all tables.
     */
    private final Set<String> tableIds;

    /**
     * Match keys for subscribed tables, indexed by the root table id string,
     * or null for all tables.
     */
    private final Map<String, List<MatchKey>> tableMatchKeys;

    /**
     * Match keys with indexed by root table ID bytes, or null for all
     * tables.
     */
    private transient volatile Map<byte[], List<MatchKey>> tableMatchKeysBytes;

    /** The total partitions in the store */
    private final int nTotalParts;

    /**
     * For value bytes is encoded with format
     * {@link oracle.kv.Value.Format#MULTI_REGION_TABLE}, true if only writes
     * from local region are allowed to pass, false otherwise;
     *
     * No impact to value bytes is not encoded with format
     * {@link oracle.kv.Value.Format#MULTI_REGION_TABLE};
     */
    private final boolean localWritesOnly;

    /** start vlsn, set by feeder as the negotiated vlsn in handshake  */
    private volatile transient long startVLSN = INVALID_VLSN;

    /**
     * Set of partitions owned by the shard. The initial owned partition
     * set is constructed by querying the partition generation database with
     * the start VLSN and may evolve over time during streaming. Please refer
     * to [#26662][#26725] for more background.
     *
     * different scenarios depending on the value
     *
     * 1) null: the generation db is empty and no migration, filter shall allow
     * all entries to pass regardless of its partition
     *
     * 2) empty set: may happen in store expansion before migration is over, or
     * contraction a shard no longer owns any partition, the filter shall
     * block all entries regardless of its partition
     *
     * 3) non-empty set: there have been migrations in the store, the filter
     * shall only allow entries from the partitions in the set to pass
     */
    private transient volatile Set<PartitionId> ownedParts;

    /** key to partition map */
    private transient KeyToPartitionMap k2PartMap;

    /** partition generation table db id */
    private transient DatabaseId partGenTblDBId;

    /** list of partition generation table operation in txn */
    private transient List<LNLogEntry<?>> partGenTblOpsInTxn;

    /*- statistics -*/

    /*
     * Following stat fields are not supposed to be used at client side and
     * shall not be part of serialization/deserialization.
     */
    /** number of internal and dup db blocked by the filter */
    private transient volatile long numIntDupDBFiltered;

    /** number of txn entries passing the filter */
    private transient volatile long numTxnEntries;

    /** number of non-data entry blocked by the filter */
    private transient volatile long numNonDataEntry;

    /** number of rows passing the filter */
    private transient volatile long numRowsPassed;

    /** number of rows blocked by the filter */
    private transient volatile long numRowsBlocked;

    /** number of partitions migrated in */
    private transient volatile long numInParts;

    /** number of partitions migrated out */
    private transient volatile long numOutParts;

    /** host rn node name */
    private transient volatile String hostRN;

    /** true if internal structure is initialized, false otherwise */
    private transient volatile boolean initialized;

    /** vlsn of last record passed the filter */
    private transient volatile long lastPassedVLSN = INVALID_VLSN;

    /** last vlsn processed by filter, either blocked or passed */
    private transient volatile long lastFilterVLSN = INVALID_VLSN;

    /** modification time of last processed op */
    private transient volatile long lastOpModTime = 0;

    /**
     * Cached internal, duplicate and non-partition db ids to save filtering
     * cost. Starting from an empty set, it would cache the db id whenever we
     * saw a record from these dbs. Its overhead should be small since there
     * are not many such dbs in JE env.
     */
    private final Map<Long, Boolean> cachedIntDupNonpartDbIds;

    /** private logger, set by feeder during handshake */
    private volatile transient Logger logger;

    /** log entry info */
    private transient LNEntryInfo lnInfo = null;

    NoSQLStreamFeederFilter(Set<TableImpl> tables, int nTotalParts,
                            boolean localWritesOnly) {
        super();

        if ((tables == null) || tables.isEmpty()) {
            tableIds = null;
        } else {
            tableIds = new HashSet<>(tables.size());
            for (final TableImpl table : tables) {
                tableIds.add(table.getIdString());
            }
        }

        /* init map from string root table id to list of match keys */
        tableMatchKeys = getMatchKeys(tables);

        /* convert to map with byte[] table id as key */
        tableMatchKeysBytes = convertToBytesKey(tableMatchKeys);


        this.nTotalParts = nTotalParts;
        this.localWritesOnly = localWritesOnly;

        /*
         * Will be initialized at server side. The methods refer to these null
         * values shall not be called at client side, otherwise IAE will be
         * raised.
         */
        ownedParts = null;
        partGenTblDBId = null;
        partGenTblOpsInTxn = null;
        k2PartMap = null;
        logger = null;
        numRowsPassed = 0;
        numRowsBlocked = 0;
        numTxnEntries = 0;
        numNonDataEntry = 0;
        numIntDupDBFiltered = 0;
        numInParts = 0;
        numOutParts = 0;
        hostRN = null;
        initialized = false;
        cachedIntDupNonpartDbIds = new HashMap<>();
    }

    /**
     * Sets the start vlsn
     *
     * @param vlsn start vlsn
     */
    @Override
    public void setStartVLSN(long vlsn) {
        startVLSN = vlsn;
    }

    /* convert the map with string table id key to a map with byte[] key */
    private static TreeMap<byte[], List<MatchKey>>
    convertToBytesKey(Map<String, List<MatchKey>> tableMatchKeys) {

        if (tableMatchKeys == null) {
            return null;
        }

        final TreeMap<byte[], List<MatchKey>> ret =
            new TreeMap<>(new IDBytesComparator());
        for (Entry<String, List<MatchKey>> entry : tableMatchKeys.entrySet()) {

            ret.put(UtfOps.stringToBytes(entry.getKey()), entry.getValue());
        }

        return ret;
    }

    /**
     * Stores the information about a table key needed to determine if an entry
     * key matches a table.  The information includes the number of initial key
     * components to skip, corresponding to parent table IDs and primary key
     * components, when looking for the table ID component of the key, and the
     * table ID of the matching table.  Note that this scheme does not check
     * all parent table IDs, only the root table ID, so it may generate false
     * positive matches, but only in rare circumstances.  Those incorrect
     * entries, if any, will be filtered out by the publisher.
     */
    static class MatchKey implements Serializable {
        private static final long serialVersionUID = 1;

        /**
         * The table ID of the table, represented as a string
         */
        final String tableId;

        /**
         * The byte array form of the table ID.
         */
        transient volatile byte[] tableIdBytes;

        /**
         * The number of primary key components associated with just this
         * table, not parent tables.  This value is used to determine if the
         * key contains additional components beyond the one for this table,
         * meaning it is for a child of this table.
         */
        final int keyCount;

        /**
         * The number of key components to skip to find the table ID relative
         * to the start of the key.  Set to 0 if the table is a root table.
         */
        final int skipCount;

        /**
         * The table ID of the root table for the table, represented as a
         * string.
         */
        final String rootTableId;

        /**
         * The byte array form of the root table ID.
         */
        transient volatile byte[] rootTableIdBytes;

        /**
         * Return a match key for the specified table.
         */
        MatchKey(TableImpl table) {
            tableId = table.getIdString();
            tableIdBytes = table.getIDBytes();
            final TableImpl firstParent = (TableImpl) table.getParent();
            keyCount = (firstParent == null) ?
                table.getPrimaryKeySize() :
                table.getPrimaryKeySize() - firstParent.getPrimaryKeySize();

            /*
             * Count primary key components and table IDs to skip to find the
             * child table ID, and find the root table
             */
            int count = (firstParent == null) ?
                0 :
                /*
                 * The number of primary key components in the immediate
                 * parent, which includes components for any higher parents.
                 */
                firstParent.getPrimaryKeySize();
            TableImpl rootTable = table;

            for (TableImpl t = firstParent;
                 t != null;
                 t = (TableImpl) t.getParent()) {

                /* Skip the table ID component */
                count++;

                rootTable = t;
            }
            skipCount = count;
            rootTableId = rootTable.getIdString();
            rootTableIdBytes = rootTable.getIDBytes();
        }

        MatchKey(String rootTableId, String tableId, int keyCount,
                 int skipCount) {
            this.rootTableId = rootTableId;
            this.tableId = tableId;
            this.keyCount = keyCount;
            this.skipCount = skipCount;
            rootTableIdBytes = UtfOps.stringToBytes(rootTableId);
            tableIdBytes = UtfOps.stringToBytes(tableId);
        }

        /**
         * Returns whether the key matches the table for this instance.
         */
        boolean matches(byte[] key) {

            final int rootIdLen = getRootTableIdLength(key);

            /* the key does not have a valid root table id*/
            if (rootIdLen == 0) {
                return false;
            }

            /* check if root table id match */
            if (!equalsBytes(rootTableIdBytes, 0, rootTableIdBytes.length,
                             key, 0, rootIdLen)) {
                return false;
            }

            /* root table id must be followed by a delimiter */
            if (!Key.isDelimiter(key[rootIdLen])) {
                return false;
            }

            /* if subscribed a root table, check key count after table id */
            if (skipCount == 0) {
                return checkKeyCount(key, rootIdLen + 1, keyCount);
            }

            /* find the child table id from key */
            int start = rootIdLen + 1;
            /* Skip any additional parent components */
            for (int i = 1; i < skipCount; i++) {
                final int e = Key.findNextComponent(key, start);
                if (e == -1) {
                    return false;
                }
                start = e + 1;
            }

            /* finish skipping, now find child table id */
            final int end = Key.findNextComponent(key, start);
            if (end == -1) {
                return false;
            }

            /* now we have a valid child id, check if a match */
            if (!equalsBytes(tableIdBytes, 0, tableIdBytes.length,
                             key, start, end)) {
                return false;
            }

            /*
             * If a match, need ensure that the key components needed for
             * this table are present, but no more than that, since that
             * would mean a child table.
             */
            return checkKeyCount(key, end + 1, keyCount);
        }

        String getTableId() {
            return tableId;
        }

        String getRootTableId() {
            return rootTableId;
        }

        @Override
        public String toString() {
            return "match key [root id: " + rootTableId +
                   ", root id bytes: " + Arrays.toString(rootTableIdBytes) +
                   ", table id: " + tableId +
                   ", table id bytes: " + Arrays.toString(tableIdBytes) +
                   ", key count: " + keyCount +
                   ", skip count: " + skipCount + "]";
        }

        /**
         * Initialize the tableIdBytes and rootTableIdBytes fields.
         */
        private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {

            in.defaultReadObject();
            tableIdBytes = UtfOps.stringToBytes(tableId);
            rootTableIdBytes = UtfOps.stringToBytes(rootTableId);
        }

        /**
         * Returns true if starting from offset, key byte[] has exact number
         * of key counts as expected, false otherwise.
         */
        private static boolean checkKeyCount(byte[] key,
                                             int offset,
                                             int expKeyCount) {

            /* offset must be in range [0, length - 1] */
            if (offset < 0 || offset > key.length - 1) {
                return false;
            }

            for (int i = 0; i < expKeyCount; i++) {
                final int e = Key.findNextComponent(key, offset);
                if (e == -1) {
                    return false;
                }
                offset = e + 1;
            }

            /* adjust to make start at the delimiter or end of array */
            offset = offset - 1;
            /* Should be no more components, at the end of key */
            return (key.length == offset);
        }
    }

    /**
     * Returns a map from root table ID strings to lists of match keys for the
     * specified tables, or null to match all tables.
     */
    private static Map<String, List<MatchKey>> getMatchKeys(
        Set<TableImpl> tables) {

        if ((tables == null) || tables.isEmpty()) {
            return null;
        }

        final Map<String, List<MatchKey>> map = new HashMap<>();
        for (final TableImpl table : tables) {
            final String rootTblIdStr = table.getTopLevelTable().getIdString();
            map.computeIfAbsent(rootTblIdStr,
                                k -> new ArrayList<>(tables.size()))
               .add(new MatchKey(table));
        }

        return map;
    }

    /**
     * Gets a feeder filter with given set of subscribed tables. The start
     * VLSN will be used to construct the initial owned set of partitions by
     * filter at server side.
     * <p>
     * It is more preferable to provide start vlsn than a set of owned
     * partitions because in order to provide the set of owned partitions, it
     * need to query the server beforehand with given start vlsn, which is
     * unnecessarily since the filter can do that when it is installed at
     * feeder.
     *
     * @param tables      subscribed tables
     * @param nTotalParts total number of partitions in store
     * @param localWritesOnly true if only local write allowed to pass
     *
     * @return a feeder filter with given set of subscribed tables
     */
    public static NoSQLStreamFeederFilter getFilter(Set<TableImpl> tables,
                                                    int nTotalParts,
                                                    boolean localWritesOnly) {
        return new NoSQLStreamFeederFilter(tables, nTotalParts,
                                           localWritesOnly);
    }

    /**
     * Gets a feeder filter without subscribed table. The feeder filter will
     * pass all rows from all tables, except the internal db and dup db entries.
     *
     * @param nTotalParts total number of partitions in store
     *
     * @return a feeder filter allowing all updates to all tables
     */
    static NoSQLStreamFeederFilter getFilter(int nTotalParts,
                                             boolean localWritesOnly) {
        return new NoSQLStreamFeederFilter(null, nTotalParts, localWritesOnly);
    }

    /**
     * In test only
     */
    public Set<PartitionId> getOwnedParts() {
        return ownedParts;
    }

    /**
     * Process each record. Returns null if the record blocked, or the record
     * if it passes. It is synced with the function that modifies the filter.
     */
    @Override
    public synchronized OutputWireRecord execute(final OutputWireRecord record,
                                                 final RepImpl repImpl) {

        /* once-time initialization on very first record */
        if (!initialized) {
            hostRN = repImpl.getRepNode().getMasterName();
            initScheduledStat(repImpl);
            /* only dump once */
            LoggerUtils.info(logger, repImpl,
                             lm("Scheduled stats dump task on " + hostRN +
                                " trace interval in seconds " +
                                STAT_INTERVAL_SECS));
            try {
                initOwnedPartsFromDB(repImpl);
            } catch (EnvShutdownException ede) {
                logger.fine(() -> lm("Block entry because the environment " +
                                     "has shut down"));
                return null;
            }
            initialized = true;
        }

        /* entry from pgt db and need process, partGenTblDBId cannot be null */
        final long dbId = getReplicableDBId(record);
        if (partGenTblDBId.getId() == dbId) {
            /* pass the record to client after processing */
            return processPGTRecord(record, repImpl);
        }

        /* block all internal or duplicate or non-partition db entry */
        if (isIntDupNonpart(dbId, repImpl)) {
            numIntDupDBFiltered++;
            return null;
        }

        final LogEntryType type = LogEntryType.findType(record.getEntryType());
        lastFilterVLSN = record.getVLSN();
        lastOpModTime = Math.max(lastOpModTime, record.getTimeStamp());

        /* allow txn boundary entry pass */
        if (LOG_TXN_COMMIT.equals(type)) {
            commitPGTTxn(repImpl);
            numTxnEntries++;
            return record;
        }

        if (LOG_TXN_ABORT.equals(type)) {
            abortPGTTxn(repImpl);
            numTxnEntries++;
            return record;
        }

        /* allow trace entry for debugging or testing */
        if (LOG_TRACE.equals(type)) {
            return record;
        }

        /* block all non-data type entry */
        if (!isDataEntry(type)) {
            numNonDataEntry++;
            return null;
        }

        /* finally filter out all non-subscribed tables */
        return filter(record);
    }

    /**
     * Applies the stream filter change. Making the changes is synced with the
     * function that uses the filter.
     * <p>
     * {@link #execute(OutputWireRecord, RepImpl)}
     *
     * @param change  change request from client to apply
     * @param repImpl rep env associated with the feeder
     * @return stream filter change response
     */
    @Override
    public FeederFilterChangeResult applyChange(FeederFilterChange change,
                                                RepImpl repImpl) {

        final FeederFilterChangeResult ret;
        final StreamChangeReq req;
        try {
            req = (StreamChangeReq) change;
        } catch (ClassCastException cce) {
            /* change is not a StreamChangeReq */
            final String err = "Unsupported stream filter change request " +
                               "with id " + change.getReqId() +
                               ", expect StreamChangeReq but get " +
                               change.getClass().getSimpleName();
            LoggerUtils.warning(logger, repImpl, lm(err));
            return new FeederFilterChangeResult(
                change.getReqId(), FeederFilterChangeResult.Status.FAIL, err);
        }

        switch (req.getReqType()) {
            case ADD:
                ret = addTable(req.asSubscribeReq(), repImpl);
                break;
            case REMOVE:
                ret = removeTable(req.asUnsubscribeReq(), repImpl);
                break;
            default:
                final String err = "Unsupported stream filter change request " +
                                   "type " + req.getReqType() + " with " +
                                   "request id " + req.getReqId();
                LoggerUtils.warning(logger, repImpl, lm(err));
                return new FeederFilterChangeResult(
                    req.getReqId(), FeederFilterChangeResult.Status.FAIL, err);
        }

        LoggerUtils.info(logger, repImpl,
                         lm("Result of change (req id=" + req.getReqId() +
                            ", type=" + req.getReqType() + "): " +
                            "result=[" + ret + "]" +
                            ", table=" + req.getTableName() +
                            ", # tables=" + tableIds.size() +
                            ", idStrings=" + tableIds +
                            ", ids=" +
                            tableIds.stream().map(TableImpl::createIdFromIdStr)
                                    .collect(Collectors.toSet())));
        return ret;
    }

    /**
     * Returns a new instance of updated stream filter from given tables and
     * the existing filter.
     *
     * @param tables set of tables
     * @return a new instance of stream filter
     */
    NoSQLStreamFeederFilter updateFilter(Set<TableImpl> tables) {
        return NoSQLStreamFeederFilter.getFilter(tables, nTotalParts,
                                                 localWritesOnly);
    }

    /**
     * Adds a subscribe table to the filter
     *
     * @param req     request to add a subscribe table
     * @param repImpl rep environment impl
     * @return feeder change result
     */
    private synchronized FeederFilterChangeResult addTable(
        StreamChangeSubscribeReq req, RepImpl repImpl) {

        final String tid = req.getTableId();
        final String rid = req.getRootTableId();

        /* the table already exists, possibly added by another thread */
        if (tableIds.contains(tid)) {
            final String err = "Change not applicable because table already " +
                               "present in the filter(root id " +
                               rid + ", table id " + tid + ")";
            LoggerUtils.fine(logger, repImpl, lm(err));
            return new FeederFilterChangeResult(
                req.getReqId(), FeederFilterChangeResult.Status.NOT_APPLICABLE,
                err);
        }

        tableIds.add(tid);
        final MatchKey mkey = new MatchKey(rid, tid, req.getKeyCount(),
                                           req.getSkipCount());
        tableMatchKeys.computeIfAbsent(rid, u -> new ArrayList<>()).add(mkey);
        tableMatchKeysBytes.computeIfAbsent(UtfOps.stringToBytes(rid),
                                            u -> tableMatchKeys.get(rid));

        final long vlsn = (lastPassedVLSN == INVALID_VLSN) ?
            FIRST_VLSN : VLSN.getNext(lastPassedVLSN);
        return new FeederFilterChangeResult(req.getReqId(), vlsn,
                                            System.currentTimeMillis());

    }

    /**
     * Removes a table from the filter
     *
     * @param req     request to remove a subscribe table
     * @param repImpl rep environment impl
     */
    private synchronized FeederFilterChangeResult removeTable(
        StreamChangeUnsubscribeReq req, RepImpl repImpl) {

        final String rootTableId = req.getRootTableId();
        final String tableId = req.getTableId();

        /* the table already gone, possibly removed by another thread */
        if (!tableIds.contains(tableId)) {
            final String err = "table not found in the filter(root id " +
                               rootTableId + ", table id " + tableId + ")";
            LoggerUtils.fine(logger, repImpl, lm(err));
            return new FeederFilterChangeResult(
                req.getReqId(), FeederFilterChangeResult.Status.NOT_APPLICABLE,
                err);
        }

        tableIds.remove(tableId);
        /* remove a root table and all its child tables */
        if (tableId.equals(rootTableId)) {
            if (tableMatchKeys.containsKey(rootTableId) &&
                tableMatchKeysBytes
                    .containsKey(UtfOps.stringToBytes(rootTableId))) {
                tableMatchKeys.remove(rootTableId);
                tableMatchKeysBytes.remove(UtfOps.stringToBytes(rootTableId));
                return new FeederFilterChangeResult(
                    req.getReqId(),
                    ((lastPassedVLSN == INVALID_VLSN) ?
                     FIRST_VLSN :
                     VLSN.getNext(lastPassedVLSN)),
                    System.currentTimeMillis());
            }

            final String err = "table not found in match key list " +
                               "(root table " + "id " + rootTableId +
                               ", table id " + tableId + ")";
            LoggerUtils.fine(logger, repImpl, lm(err));
            return new FeederFilterChangeResult(
                req.getReqId(), FeederFilterChangeResult.Status.FAIL, err);
        }

        /* remove a child table if exists */
        final List<MatchKey> matchKeys = tableMatchKeys.get(rootTableId);
        if (removeMatchKey(matchKeys, tableId)) {
            /* the last table under the root table id is removed */
            if (tableMatchKeys.get(rootTableId).isEmpty()) {
                tableMatchKeys.remove(rootTableId);
                tableMatchKeysBytes.remove(UtfOps.stringToBytes(rootTableId));
            }
            return new FeederFilterChangeResult(
                req.getReqId(),
                ((lastPassedVLSN == INVALID_VLSN) ?
                 FIRST_VLSN :
                 VLSN.getNext(lastPassedVLSN)),
                System.currentTimeMillis());
        }

        final String err = "table not found in match key list " +
                           "(root table " + "id " + rootTableId +
                           ", table id " + tableId + ")";
        LoggerUtils.fine(logger, repImpl, lm(err));
        return new FeederFilterChangeResult(
            req.getReqId(), FeederFilterChangeResult.Status.FAIL, err);
    }

    /**
     * Removes a match key from the list with matching root table id and
     * table id
     *
     * @return true if the match key exists and is removed successfully from
     * the list, false otherwise.
     */
    private static boolean removeMatchKey(List<MatchKey> matchKeys,
                                          String tableId) {

        if (matchKeys == null) {
            return false;
        }

        return matchKeys.removeIf(key -> key.getTableId().equals(tableId));
    }

    @Override
    public String[] getTableIds() {
        if (tableIds == null) {
            return null;
        }
        return tableIds.toArray(new String[0]);
    }

    @Override
    public long getFilterVLSN() {
        return lastFilterVLSN;
    }

    @Override
    public long getLastModTimeMs() {
        return lastOpModTime;
    }

    @Override
    public long getLastPassVLSN() {
        return lastPassedVLSN;
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String toString() {
        return ((tableIds == null || tableIds.isEmpty()) ?
            "all user tables." :
            "ids " + Arrays.toString(tableIds.toArray())) +
               ", owned partitions: " +
               (ownedParts == null ? "all" : ownedParts);
    }

    public long getNumIntDupDBFiltered() {
        return numIntDupDBFiltered;
    }

    public long getNumTxnEntries() {
        return numTxnEntries;
    }

    public long getNumNonDataEntry() {
        return numNonDataEntry;
    }

    public long getNumRowsPassed() {
        return numRowsPassed;
    }

    public long getNumRowsBlocked() {
        return numRowsBlocked;
    }

    public long getNumInParts() {
        return numInParts;
    }

    public long getNumOutParts() {
        return numOutParts;
    }

    /* filters entries, should be as efficient as possible */
    private OutputWireRecord filter(final OutputWireRecord record) {

        final LogEntry entry = record.instantiateEntry();
        final LNLogEntry<?> lnEntry = (LNLogEntry<?>) entry;

        /*
         * All duplicate db entries have been filtered before reaching here.
         * Looks for now postFetchInit() does nothing but return for
         * non-duplicate db entry, it is probably safer to still call it here
         * in case implementation of postFetchInit() changes in future.
         */
        lnEntry.postFetchInit(false);

        /* block all non-local table writes if necessary */
        final byte[] val = lnEntry.getData();
        if (localWritesOnly && val != null && val.length > 0) {
            final Value.Format format = Value.Format.fromFirstByte(val[0]);
            if (Value.Format.isTableFormat(format) &&
                format == Value.Format.MULTI_REGION_TABLE &&
                Byte.toUnsignedInt(val[1]) != Region.LOCAL_REGION_ID) {
                return null;
            }
        }

        final byte[] key = lnEntry.getKey();

        /* check if the key belongs to owned partitions */
        if (!isKeyFromOwnedPartition(key)) {
            numRowsBlocked++;
            return null;
        }

        /* check if match for a valid root table id in key */
        if (getRootTableIdLength(key) > 0 /* a valid root table id */ &&
            matchKey(key) /* find a match */) {
            numRowsPassed++;
            lastPassedVLSN = record.getVLSN();
            return record;
        }

        numRowsBlocked++;
        return null;
    }

    /* returns true if key byte[] matches any MatchKey in the list */
    boolean matchKey(byte[] key) {

        if (tableMatchKeysBytes == null || tableMatchKeysBytes.isEmpty()) {
            /*
             * If no subscribed tables are specified in filter, we allow
             * all rows with a valid table id string to pass the filter.
             */
            return true;
        }

        final List<MatchKey> mkeys = tableMatchKeysBytes.get(key);
        if (mkeys != null) {
            for (MatchKey matchKey : mkeys) {
                if (matchKey.matches(key)) {
                    /* get a match! */
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Returns true if dbImpl does not exist, or the entry is from
     * - internal db, or
     * - duplicate db, or
     * - non-partition db
     */
    private boolean isIntDupNonpart(long id, RepImpl repImpl) {

        /* internal or duplicate db entry must have a db id */
        if (id == -1) {
            return false;
        }

        /* check if this id is an internal or dup db id we saw before */
        final Boolean cachedValue = cachedIntDupNonpartDbIds.get(id);
        if (cachedValue != null) {
            return cachedValue;
        }

        final DbTree dbTree = repImpl.getDbTree();
        final DatabaseImpl impl = dbTree.getDb(new DatabaseId(id));
        try {
            final boolean ret =
                (impl != null) &&
                (impl.getSortedDuplicates() || impl.isInternalDb() ||
                /* from non-partition db */
                !PartitionId.isPartitionName(impl.getName()));

            /* cache result for future records */
            cachedIntDupNonpartDbIds.put(id, ret);

            return ret;
        } finally {
            dbTree.releaseDb(impl);
        }
    }

    /* returns true for a data entry, e.g., put or delete */
    private static boolean isDataEntry(LogEntryType type) {

        /*
         * it looks in JE, in many places log entry types are compared via
         * equals() instead of '==', so follow the convention
         */
        return LOG_INS_LN.equals(type) ||
               LOG_UPD_LN.equals(type) ||
               LOG_DEL_LN.equals(type) ||
               LOG_INS_LN_TRANSACTIONAL.equals(type) ||
               LOG_UPD_LN_TRANSACTIONAL.equals(type) ||
               LOG_DEL_LN_TRANSACTIONAL.equals(type);
    }

    /* Compares two non-null byte[] from start inclusively to end exclusively */
    private static boolean equalsBytes(byte[] a1, int s1, int e1,
                                       byte[] a2, int s2, int e2) {
        /* must be non-null */
        if (a1 == null || a2 == null) {
            return false;
        }

        /* no underflow */
        if (s1 < 0 || s2 < 0) {
            return false;
        }

        /* no overflow */
        if (a1.length < e1 || a2.length < e2) {
            return false;
        }

        /* end must be greater than start */
        final int len = e1 - s1;
        if (len < 0) {
            return false;
        }

        /* must have same length */
        if (len != (e2 - s2)) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (a1[s1 + i] != a2[s2 + i]) {
                return false;
            }
        }

        return true;
    }

    /*
     * Returns length of the root table id in key, or 0 if the key does not
     * have a valid root table id
     */
    private static int getRootTableIdLength(byte[] key) {

        if (key == null) {
            return 0;
        }
        return Key.findNextComponent(key, 0);
    }

    /**
     * Initialize the tableMatchKeysBytes field.
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {

        in.defaultReadObject();
        tableMatchKeysBytes = convertToBytesKey(tableMatchKeys);
        k2PartMap = new HashKeyToPartitionMap(nTotalParts);
        numRowsPassed = 0;
        numRowsBlocked = 0;
        numTxnEntries = 0;
        numNonDataEntry = 0;
        numIntDupDBFiltered = 0;
        numInParts = 0;
        numOutParts = 0;
        partGenTblDBId = null;
        ownedParts = null;
        logger = null;
        hostRN = null;
        initialized = false;

        partGenTblOpsInTxn = new ArrayList<>();
    }

    /**
     * Returns true if the key is from owned partitions
     *
     * @param key the key
     * @return true if the key is from owned partitions
     */
    private boolean isKeyFromOwnedPartition(byte[] key) {
        if (ownedParts == null) {
            /* part gen db empty, allow all to pass */
            return true;
        }

        if (ownedParts.isEmpty()) {
            /*
             * the shard does not own any partition for the key this may
             * happen in store expansion that before the partition finished
             * migration, the new shard does not own any partition, and we
             * shall block all entries before the new generation is created.
             */
            return false;
        }

        if (k2PartMap == null) {
            /* in case called at client */
            throw new IllegalArgumentException("Key2Partition map is not " +
                                               "initialized.");
        }

        final PartitionId pid = k2PartMap.getPartitionId(key);
        return ownedParts.contains(pid);
    }

    /**
     * Scans the partition md database and return a list of owned
     * partitions for given start vlsn. If the generation db is empty,
     * returns null.
     *
     * @param repImpl environment
     * @param vlsn    start vlsn
     * @return a set of partitions covering the vlsn
     */
    private Set<PartitionId> initOwnedPartsFromVLSN(RepImpl repImpl,
                                                    long vlsn) {
        final String dbName = PartitionGenDBManager.getDBName();

        final TransactionConfig txnConf = new TransactionConfig();
        txnConf.setReadOnly(true);
        txnConf.setReadCommitted(true);

        final DatabaseConfig dbConf = new DatabaseConfig();
        /*
         * We shall not allow create a JE db. If the db does not exist,
         * system is not in the right state to stream, exception shall be
         * raised to caller.
         */
        dbConf.setAllowCreate(false).setTransactional(true);

        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry val = new DatabaseEntry();
        final Environment env = repImpl.getInternalEnvHandle();
        if (env == null) {
            /* Env handle can be null in shutdown, which is OK */
            final RepNode rn = repImpl.getRepNode();
            if (rn == null || rn.isShutdown()) {
                throw new EnvShutdownException("Environment is shutdown");
            }

            /* env closed unexpectedly, surface */
            final String err = "Environment is closed unexpectedly";
            logger.warning(lm(err));
            throw new IllegalStateException(err);
        }
        Transaction txn = env.beginTransaction(null, txnConf);

        /*
         * The close methods of resources in try resource block are called in
         * the opposite order of their creation, thus cursor will be closed
         * before db, which is equired to close the cursor correctly
         */
        try (
            final Database db = env.openDatabase(txn, dbName, dbConf);
            final Cursor cursor = db.openCursor(null, null)) {
            int numRecordInDb = 0;
            Set<PartitionId> ret = new HashSet<>();
            while (cursor.getNext(key, val, null)
                         .equals(OperationStatus.SUCCESS)) {
                numRecordInDb++;
                final PartitionGeneration pg =
                    PartitionGenDBManager.readPartGenFromVal(val.getData());
                final boolean inGen = pg.inGeneration(vlsn);
                if (inGen) {
                    ret.add(pg.getPartId());
                }
                LoggerUtils.fine(logger, repImpl,
                                 lm("Read partition generation " + pg +
                                    ", vlsn " + vlsn + " in generation? " +
                                    (inGen ? "Yes" : "No")));
            }
            if (numRecordInDb == 0) {
                /* if the generation db is empty, return null */
                ret = null;
            }

            cursor.close();
            txn.commit();
            txn = null;
            return ret;
        } finally {
            TxnUtil.abort(txn);
        }
    }

    //TODO: it would be nice to include stats in KV stats file
    private void dumpFilterStatistics(final RepImpl repImpl) {
        final StringBuilder sb = new StringBuilder("StreamFilter statistics:");
        if (partGenTblDBId == null) {
            sb.append("Uninitialized");
            LoggerUtils.fine(logger, repImpl, lm(sb.toString()));
            return;
        }

        sb.append("host node name:").append(hostRN).append("\n");
        sb.append("partition md db id:").append(partGenTblDBId).append("\n");
        sb.append("# rows passed: ").append(getNumRowsPassed()).append("\n");
        sb.append("# rows blocked: ").append(getNumRowsBlocked()).append("\n");
        sb.append("# rows internal or duplicate db (blocked): ")
          .append(getNumIntDupDBFiltered()).append("\n");
        sb.append("# txn entries (passed): ")
          .append(getNumTxnEntries()).append("\n");
        sb.append("# non-data entries (blocked):")
          .append(getNumNonDataEntry()).append("\n");
        sb.append("start VLSN ").append(startVLSN).append("\n");
        sb.append("# num partitions in store: ")
          .append(nTotalParts).append("\n");
        sb.append("# partitions migrated in: ")
          .append(getNumInParts()).append("\n");
        sb.append("# partitions migrated in: ")
          .append(getNumOutParts()).append("\n");
        sb.append("owned partitions: ").append(getOwnedParts());

        LoggerUtils.fine(logger, repImpl, lm(sb.toString()));
    }

    private void initScheduledStat(final RepImpl repImpl) {
        /* dump stats only if FINE or lower tracing level is on */
        if (!logger.isLoggable(Level.FINE)) {
            return;
        }
        Executors.newScheduledThreadPool(1)
                 .scheduleAtFixedRate(() -> dumpFilterStatistics(repImpl),
                                      0,
                                      STAT_INTERVAL_SECS,
                                      TimeUnit.SECONDS);
    }

    private OutputWireRecord processPGTRecord(OutputWireRecord record,
                                              RepImpl repImpl) {

        final LogEntryType type = LogEntryType.findType(record.getEntryType());

        LoggerUtils.fine(logger, repImpl,
                         lm("Process a PGT entry with type " + type));

        final LogEntry entry = record.instantiateEntry();
        final LNLogEntry<?> lnEntry = (LNLogEntry<?>) entry;
        /* non-txn op, directly apply it */
        if (LOG_INS_LN.equals(type) || LOG_UPD_LN.equals(type)) {
            applyPGTOp(lnEntry, repImpl);
            return record;
        }

        /* txn op, buffer it */
        if (LOG_INS_LN_TRANSACTIONAL.equals(type) ||
            LOG_UPD_LN_TRANSACTIONAL.equals(type)) {
            partGenTblOpsInTxn.add(lnEntry);
            return record;
        }

        /* only insert (open generation) and update (close generation) */
        return null;
    }

    private void abortPGTTxn(RepImpl repImpl) {
        if (partGenTblOpsInTxn == null) {
            throw new IllegalArgumentException("Buffer of partition " +
                                               "generation ops in txn not " +
                                               "initialized");
        }

        if (partGenTblOpsInTxn.isEmpty()) {
            return;
        }
        final int ret = partGenTblOpsInTxn.size();
        partGenTblOpsInTxn.clear();
        LoggerUtils.fine(logger, repImpl,
                         lm("Abort PGT txn with " + ret + " ops."));
    }

    private void commitPGTTxn(RepImpl repImpl) {
        if (partGenTblOpsInTxn == null) {
            throw new IllegalArgumentException("Buffer of partition " +
                                               "generation ops in txn not " +
                                               "initialized");
        }

        if (partGenTblOpsInTxn.isEmpty()) {
            return;
        }
        final int ret = partGenTblOpsInTxn.size();
        /* adjust partition filter for each operation */
        partGenTblOpsInTxn.forEach(entry -> applyPGTOp(entry, repImpl));
        partGenTblOpsInTxn.clear();
        LoggerUtils.fine(logger, repImpl,
                         lm("Commit PGT txn with " + ret + " ops"));
    }

    private void applyPGTOp(LNLogEntry<?> lnEntry, RepImpl repImpl) {
        if (ownedParts == null) {
            /* empty generation db, the commit is not for generation db */
            return;
        }

        lnEntry.postFetchInit(false);
        final PartitionGeneration pg =
            PartitionGenDBManager.readPartGenFromVal(lnEntry.getData());
        final PartitionId pid = pg.getPartId();
        if (pg.isOpen()) {

            /*
             * open a new generation
             * Note that the transaction that modifies the PGT does not
             * include any normal data updates, because we do not support
             * interleaved or overlapped transaction, all operations buffered
             * before commit or abort must from the same transaction.
             */
            ownedParts.add(pid);
            numInParts++;
            LoggerUtils.info(logger, repImpl, lm("Open generation " + pg));
        } else {
            /* close a generation */
            ownedParts.remove(pid);
            numOutParts++;
            LoggerUtils.info(logger, repImpl, lm("Close generation " + pg));
        }
    }

    private void initOwnedPartsFromDB(RepImpl repImpl) {

        assert (repImpl != null);

        final String dbname = PartitionGenDBManager.getDBName();
        try {
            partGenTblDBId = FeederReplicaSyncup.getDBId(repImpl, dbname);
            ownedParts = initOwnedPartsFromVLSN(repImpl, startVLSN);
            if (ownedParts == null) {
                LoggerUtils.info(logger, repImpl,
                                 lm("Empty partition generation db exists " +
                                    "with db id " + partGenTblDBId +
                                    ", for start vlsn " + startVLSN +
                                    " all entries should pass"));
            } else {
                LoggerUtils.info(logger, repImpl,
                                 lm("Non-empty partition generation db " +
                                    "already exists with db id " +
                                    partGenTblDBId +
                                    ", for start vlsn " + startVLSN +
                                    ", allow entries to pass from " +
                                    ownedParts));
            }
        } catch (DatabaseNotFoundException exp) {
            ownedParts = null;
            partGenTblDBId = null;
            final String err = "Cannot find partition generation db " + dbname +
                               " in node " + repImpl.getHostName();
            LoggerUtils.warning(logger, repImpl, lm(err));
            throw new IllegalStateException(err, exp);
        }
    }

    public void initPartGenTableFromMigration(DatabaseId dbId,
                                              Set<PartitionId> pids) {
        if (dbId == null) {
            throw new IllegalStateException("Partition generation db id " +
                                            "cannot be null");
        }
        if (pids == null) {
            throw new IllegalStateException("Owned partitions cannot be " +
                                            "null");
        }
        partGenTblDBId = dbId;
        ownedParts = pids;
    }

    private long getReplicableDBId(OutputWireRecord record) {
        if (lnInfo == null) {
            /* initialized once */
            lnInfo = new LNEntryInfo();
        }

        if (record.getLNEntryInfo(lnInfo)) {
            return lnInfo.databaseId;
        }

        /* not a LNLogEntry */
        return -1;
    }

    private static String lm(String msg) {
        return "[StreamFilter] " + msg;
    }

    /**
     * Exception thrown when the env is shut down.
     */
    private static class EnvShutdownException extends RuntimeException {
        private static final long serialVersionUID = 1;
        EnvShutdownException(String msg) {
            super(msg);
        }
    }
}