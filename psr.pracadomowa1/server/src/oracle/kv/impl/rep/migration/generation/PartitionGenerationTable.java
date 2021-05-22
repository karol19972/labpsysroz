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

package oracle.kv.impl.rep.migration.generation;

import static com.sleepycat.je.utilint.VLSN.INVALID_VLSN;
import static com.sleepycat.je.utilint.VLSN.NULL_VLSN;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.impl.pubsub.NoSQLStreamFeederFilter;
import oracle.kv.impl.rep.RepNode;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.PartitionMap;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.Topology;

import com.sleepycat.je.Database;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.stream.FeederReplicaSyncup;
import com.sleepycat.je.utilint.VLSN;

/**
 * Object represents a conceptual table that records migration history of
 * partitions hosted by the RN. The table is shard owned, e.g., each shard
 * maintains its own partition generation table. The table is initialized
 * when RN starts up or the RN becomes master, from an internal replicated JE
 * database which replicates from master to to replicas.
 *
 * The table is only used and updated at master RN. In particular, open/close
 * of the table is managed in two places:
 * 1. When a master RN starts up (shutdown), the table is opened (closed)
 * when the partition db is opened (closed) in PartitionManager;
 * 2. In RN state transition, open/close of the table is managed in the state
 * tracker of MigrationManager. The state tracker tracks the state transition
 * of RN and opens the table when RN becomes master and closes it when the RN
 * is not longer the master.
 */
public class PartitionGenerationTable {

    /* private logger */
    private final Logger logger;

    /* manager dealing with underlying je database */
    private final PartitionGenDBManager dbManager;

    /**
     * generation table indexed by partition id. For each partition, a sorted
     * map on partition generation number is used to represent the history for
     * that partition
     */
    private final Map<PartitionId,
        SortedMap<PartitionGenNum, PartitionGeneration>> genTable;

    /* set to true after the table is initialized */
    private volatile boolean ready;

    /* parent rep node */
    private final RepNode repNode;

    public PartitionGenerationTable(RepNode repNode, Logger logger) {

        this.repNode = repNode;
        this.logger = logger;

        this.dbManager = new PartitionGenDBManager(repNode, logger);
        genTable = new HashMap<>();
        ready = false;
    }

    /**
     * Returns true if the table is initialized and ready to use, false
     * otherwise.
     *
     * @return true if the table is initialized and ready to use, false
     * otherwise.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Scans the db and init in memory generation table.
     */
    private synchronized void scanDB() throws PartitionMDException {

        final Database dbHandle = repNode.getMigrationManager()
                                         .getGenerationDB();
        if (dbHandle == null) {
            throw new IllegalStateException("Generation db not open on " +
                                            repNode.getRepNodeId());
        }

        final List<PartitionGeneration> allHistory = dbManager.scan();
        for (PartitionGeneration pg : allHistory) {
            addGeneration(pg);
        }

        logger.fine(() -> "Generation table with " + genTable.size() +
                          " partitions has been initialized from scanning " +
                          "generation db, # open generations: " +
                          getOpenGens().size() +
                          ", list of open generations:" +
                          Arrays.toString(getOpenGens().keySet().toArray()));
    }

    /**
     * Closes the partition generation table
     */
    public synchronized void close() {
        if (!isReady()) {
            return;
        }
        genTable.clear();
        ready = false;
        logger.log(Level.INFO, "Partition generation table closed");
    }

    /**
     * Returns the highest VLSN of all closed generations in the table
     *
     * @return the highest VLSN of all closed generations
     */
    public long getHighVLSNClosedGen() {
        long ret = NULL_VLSN;
        for (PartitionId pid : genTable.keySet()) {
            final PartitionGeneration pg = getLastGen(pid);
            if (pg.isOpen()) {
                continue;
            }

            if (pg.getEndVLSN() > ret) {
                ret = pg.getEndVLSN();
            }
        }
        return ret;
    }

    /**
     * Unit test only
     */
    String dumpTable() {
        final StringBuilder sb = new StringBuilder();
        if (isReady()) {
            sb.append("Open generations:")
              .append(getOpenGens().keySet()).append("\n");
            sb.append("List of all generations:\n");
            for (SortedMap<PartitionGenNum,
                PartitionGeneration> hist : genTable.values()) {
                for (PartitionGeneration pg : hist.values()) {
                    sb.append(" ").append(pg);
                }
                sb.append("\n");
            }
        } else {
            sb.append("Unavailable");
        }

        final String ret = sb.toString();
        logger.log(Level.INFO,
                   "Partition generation table: {0}",
                   new Object[]{ret});

        return ret;
    }

    /* query the table */

    /**
     * Returns true if the underlying JE base contains the generation of given
     * partition, either open or closed.
     *
     * @param pid partition id
     *
     * @return  true if the table has the partition
     *
     * @throws IllegalStateException if called before the partition
     * generation table is initialized and ready
     */
    public synchronized boolean hasPartition(PartitionId pid)
        throws IllegalStateException {

        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        return genTable.containsKey(pid);
    }

    /**
     * Returns the generation for a partition with specific generation
     * number, or null if the record does not exist in database
     *
     * @param pid id of partition
     * @param pgn generation number of a partition
     *
     * @return the generation for a partition with specific generation
     * number, or null
     */
    synchronized PartitionGeneration getGen(PartitionId pid,
                                            PartitionGenNum pgn) {

        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        final Map<PartitionGenNum, PartitionGeneration> his = genTable.get(pid);
        if (his == null) {
            return null;
        }

        return his.get(pgn);
    }

    /**
     * Returns the set of generations hosted by RN at the given VLSN, or
     * empty set if no generation covers the given VLSN.  Note for each
     * partition, at most one generation can cover the given vlsn.
     *
     * @param vlsn VLSN
     *
     * @return a set of generations hosted by RN at the given VLSN, sorted by
     * the generation number
     */
    synchronized Set<PartitionGeneration> getGensWithVLSN(long vlsn) {
        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        if (vlsn == INVALID_VLSN || VLSN.isNull(vlsn)) {
            throw new IllegalArgumentException("VLSN cannot be null");
        }

        return genTable.values().stream()
                       .flatMap(m -> m.values().stream())
                       .filter(pg -> pg.inGeneration(vlsn))
                       .collect(Collectors.toSet());
    }

    /* update the table */

    /**
     * Opens a generation for a given partition with given generation number
     * at a start vlsn.
     *
     * @param pid          partition id
     * @param pgn          partition generation number
     * @param prevGenShard shard of previous generation, null if very first
     *                     generation
     * @param prevLastVLSN last vlsn of previous generation, if exists
     * @param txn          parent txn if called within a txn, null if not
     *
     * @throws PartitionMDException if fail to open a partition generation
     */
    public synchronized void openGeneration(PartitionId pid,
                                            PartitionGenNum pgn,
                                            RepGroupId prevGenShard,
                                            long prevLastVLSN,
                                            Transaction txn)
        throws PartitionMDException {
        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        if (pgn.getNumber() > 0 && prevGenShard.isNull()) {
            throw new IllegalArgumentException("Must be specify a valid " +
                                               "previous generation for " +
                                               "non-zero generation number " +
                                               pgn);
        }

        if (getOpenGen(pid) != null) {
            throw new IllegalStateException("Partition " + pid + " " +
                                            "already has an open " +
                                            "generation: " + getOpenGen(pid));
        }

        if (hasPartition(pid)) {
            final PartitionGenNum lastGenNum = getHistory(pid).lastKey();
            /* new PGN must be greater than any previous generation */
            if (pgn.compareTo(lastGenNum) <= 0) {
                throw new IllegalStateException("Partition " + pid + " " +
                                                "has the last generation " +
                                                lastGenNum + " newer than " +
                                                pgn);
            }
        }

        /*
         * get the start vlsn for the new generation, for the very first
         * generation, the start VLSN is null, for other generations, start
         * VLSN is the latest VLSN from VLSN index
         */
        final PartitionGeneration pg;
        if (pgn.equals(PartitionGenNum.generationZero())) {
            pg = new PartitionGeneration(pid);
        } else {
            /* open an new generation, write into JE database */
            pg = new PartitionGeneration(pid, pgn, getLastVLSN(),
                                         prevGenShard, prevLastVLSN);
        }

        /* persist to je database */
        try {
            if (txn != null) {
                dbManager.put(txn, pid, pg);
            } else {
                dbManager.put(pid, pg);
            }
            logger.log(Level.INFO, () -> "Open generation " + pg);
            logger.log(Level.FINE, this::dumpTable);
        } catch (PartitionMDException pmde) {
            logger.log(Level.WARNING, () -> "Fail to open generation " + pg);
            /* let caller deal with it */
            throw pmde;
        }

        /* update in-memory structure after db is updated successfully */
        addGeneration(pg);
    }

    /**
     * Creates the very first open generation for given partition
     *
     * @param pid         partition id
     */
    private synchronized void openGeneration(PartitionId pid)
        throws PartitionMDException {
        openGeneration(pid, PartitionGenNum.generationZero(),
                       RepGroupId.NULL_ID, NULL_VLSN, null);
    }

    /**
     * Closes an open generation with end VLSN. Usually called when a
     * partition migrates out of a shard.
     *
     * @param pid        partition id
     * @param endVLSN    end vlsn to close the generation
     */
    public synchronized void closeGeneration(PartitionId pid,
                                             long endVLSN)
        throws PartitionMDException {

        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        /* get the last generation */
        PartitionGeneration pg = getLastGen(pid);
        if (pg == null) {
            throw new IllegalArgumentException("No generation exists for " +
                                               "partition " + pid);
        }

        /*
         * In partition migration, there is a window on the target where a
         * failure can occur between receiving the EoD and the partition made
         * permanent, in this case the partition be re-instated on the source
         * even the EoD has been sent successfully. That means, we may end up
         * close a generation and update the end VLSN multiple times, until
         * the migration is completely over.
         */
        if (!pg.isOpen()) {
            final String msg = "Partition " + pid  + " generation " + pg  +
                               " already closed at VLSN " + pg.getEndVLSN() +
                               ", update its end VLSN to " + endVLSN;
            logger.info(msg);
        }

        /* make a clone of open generation  */
        final PartitionGeneration pgClone =
            new PartitionGeneration(pg.getPartId(), pg.getGenNum(),
                                    pg.getStartVLSN(), pg.getPrevGenRepGroup(),
                                    pg.getPrevGenEndVLSN());
        pgClone.close(endVLSN);
        /*
         * If all partitions are closed on this shard, mark in the
         * generation to pass the info to stream client in rep stream
         */
        final Map<PartitionId,PartitionGeneration> openPids = getOpenGens();
        if (openPids.size() == 1 && openPids.containsKey(pg.getPartId())) {
            /*
             * pg is the last partition in the open generation, mark it and
             * let client to close the RSC
             */
            pgClone.setAllPartClosed();
            logger.log(Level.INFO,
                       "All partitions closed after closing " + pid);
        } else {
            logger.log(Level.FINE,
                       "current partition to close " + pg.getPartId() +
                       ", open partitions " + openPids);
        }
        try {
            dbManager.put(pid, pgClone);
            logger.log(Level.INFO,
                       pid + ": close generation " + pgClone.getGenNum() +
                       " at VLSN " + endVLSN);
            logger.log(Level.FINE, this::dumpTable);
        } catch (PartitionMDException pmde) {
            logger.log(Level.WARNING,
                       "Fail to close " + pid + " generation " +
                       pgClone.getGenNum() + " at VLSN " + endVLSN);
            /* let caller deal with it */
            throw pmde;
        }

        /* close in-memory structure after db updated successfully */
        pg.close(endVLSN);
    }

    /**
     * Reopen the last generation of a given partition. The last generation
     * must be already closed. The operation is in the parent transaction if
     * provided
     *
     * @param pid   partition id
     * @param txn   parent txn
     */
    public synchronized void reOpenLastGeneration(PartitionId pid,
                                                  Transaction txn) {
        final PartitionGeneration closedGen = getLastGen(pid);
        if (closedGen.isOpen()) {
            throw new IllegalStateException("Generation already open " +
                                            closedGen);
        }

        /* build an open gen from the closed */
        final PartitionGeneration openGen =
            new PartitionGeneration(closedGen.getPartId(),
                                    closedGen.getGenNum(),
                                    closedGen.getStartVLSN(),
                                    closedGen.getPrevGenRepGroup(),
                                    closedGen.getPrevGenEndVLSN());

        /* persist to je database */
        try {
            if (txn != null) {
                dbManager.put(txn, pid, openGen);
            } else {
                dbManager.put(pid, openGen);
            }
            logger.info(() -> "Reopened generation persisted: " + openGen);
        } catch (PartitionMDException pmde) {
            logger.warning(() -> "Fail to open generation " + openGen);
            /* let caller deal with it */
            throw pmde;
        }

        /* add to table and replace the closed one */
        addGeneration(openGen);
        logger.info(() -> "Reopened generation " + openGen);
        logger.fine(this::dumpTable);
    }

    /**
     * Returns the last VLSN from VLSN index
     *
     * @return the last VLSN from VLSN index
     */
    public long getLastVLSN() {
        return dbManager.getLastVLSN();
    }

    /**
     * Returns the open generation for given partition, or null if no open
     * generation exists for given partition
     *
     * @param pid partition id
     *
     * @return the open generation for given partition, or null
     */
    public PartitionGeneration getOpenGen(PartitionId pid) {
        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        final PartitionGeneration ret = getLastGen(pid);
        if (ret == null) {
            logger.log(Level.FINE,
                       () -> "Generation does not exist for partition " + pid);
            return null;
        }

        if (!ret.isOpen()) {
            logger.log(Level.FINE,
                       () -> "No open generation exists for partition " + pid +
                             ", last closed generation: " + ret);

            return null;
        }
        return ret;
    }

    /**
     * Returns a list of generation records for a given partition id, or null
     * if the table does not have any record the partition.
     *
     * @param pid partition
     *
     * @return a sorted set of generation records for a given partition id, or
     * null if the table does not have any record the partition.
     */
    public SortedMap<PartitionGenNum, PartitionGeneration>
    getHistory(PartitionId pid) {
        if (!isReady()) {
            throw new IllegalStateException("Partition generation table is " +
                                            "not available");
        }

        return genTable.get(pid);
    }

    /**
     * Returns the last generation for a given partition, or null if the
     * table does not have any record for the given partition.
     *
     * @param pid given partition id
     * @return  the last generation for a given partition
     */
    public PartitionGeneration getLastGen(PartitionId pid) {
        final SortedMap<PartitionGenNum, PartitionGeneration> history =
            genTable.get(pid);
        if (history == null) {
            return null;
        }
        return history.get(history.lastKey());
    }

    /**
     * Returns the db manager
     */
    PartitionGenDBManager getDbManager() {
        return dbManager;
    }

    /**
     * Adds a generation to the table
     *
     * @param pg partition generation
     */
    private void addGeneration(PartitionGeneration pg) {
        genTable.computeIfAbsent(pg.getPartId(), (unused) -> new TreeMap<>())
                .put(pg.getGenNum(), pg);
    }

    /**
     * Returns a list of open generations in table
     */
    private Map<PartitionId, PartitionGeneration> getOpenGens() {
        return genTable.values().stream()
                       .flatMap(map -> map.values().stream())
                       .filter(PartitionGeneration::isOpen)
                       .collect(Collectors.toMap(PartitionGeneration::getPartId,
                                                 pg -> pg));
    }

    /**
     * Initializes the partition generation table. The db must exist, scan the
     * db and populate the in-memory structure.
     *
     * After this call,
     * - partition generation db is opened and scanned
     * - all owned partitions are opened
     * - all in-memory structures are initialized
     * - all stream feeder filters are notified if exist
     */
    public synchronized void initialize() {

        if (ready) {
            /* avoid re-init the table */
            logger.log(Level.FINE,
                       () -> {
                           final Set<PartitionId> pids = getOpenGens().keySet();
                           return
                               "Generation table with " + genTable.size() +
                               " partitions has been already initialized. " +
                               "# open generations: " + pids.size() + ", " +
                               "list of open generations:" +
                               Arrays.toString(pids.toArray());
                       });
            return;
        }

        /* init the table from generation db */
        scanDB();

        /* set ready to true to allow open generations */
        ready = true;

        /* let each stream filter know and initialize the owned partition */
        final Topology topo = repNode.getTopology();
        final int gid = repNode.getRepNodeId().getGroupId();
        final Set<PartitionId> ownedParts = getOwnedParts(topo, gid);
        /*
         * open every partition owned by the RN
         *
         * if a new db is created we need open every owned partitions; if the
         * db already exists, e.g., a replica promoted to master and runs
         * migrations, the new master may already has the generation db and
         * opened generations, in this case, we check if any missing
         * partition in the db and open it if any.
         */
        final Set<PartitionId> opened = new HashSet<>();
        final Set<PartitionId> exists = new HashSet<>();
        ownedParts.forEach(pid -> {
            if (hasPartition(pid)) {
                exists.add(pid);
            } else {
                opened.add(pid);
            }
        });

        opened.forEach(this::openGeneration);

        /* let feeder know that db is created and construct the white list */
        notifyFeederFilter(ownedParts);

        logger.info(() -> "Generation table initialized with " +
                          "owned partitions " +
                          ownedParts.stream()
                                    .map(PartitionId::getPartitionId)
                                    .collect(Collectors.toSet()));
        logger.fine(() -> "Generations already existed in db (last gen) " +
                          exists.stream().map(this::getLastGen)
                                .collect(Collectors.toSet()) +
                          ", generations opened: " +
                          opened.stream().map(this::getLastGen)
                                .collect(Collectors.toSet()));

    }

    /**
     * Notifies each stream feeder filter that a partition generation db
     * has been created.
     *
     * @param ownedParts  list of partitions owned by the RN
     */
    private void notifyFeederFilter(Set<PartitionId> ownedParts) {

        final ReplicatedEnvironment repEnv = repNode.getEnv(1);
        if (repEnv == null) {
            logger.info("No stream filter to notify since the rep env has " +
                        "closed on " + repNode.getRepNodeId());
            return;
        }

        final RepImpl repImpl = RepInternal.getRepImpl(repEnv);
        final com.sleepycat.je.rep.impl.node.RepNode rn = repImpl.getRepNode();
        if (rn == null || !rn.isMaster() ||  rn.feederManager() == null) {
            return;
        }

        /* notify each stream feeder filter for external node  */
        final String dbName = PartitionGenDBManager.getDBName();
        final DatabaseId dbId = FeederReplicaSyncup.getDBId(repImpl, dbName);
        rn.feederManager().activeReplicasMap().values().stream()
          /* look for external node feeder with stream filter */
          .filter(f -> f.getReplicaNode().getType().isExternal() &&
                       f.getFeederFilter() != null &&
                       f.getFeederFilter() instanceof NoSQLStreamFeederFilter)
          .forEach(f -> {
              final NoSQLStreamFeederFilter strFilter =
                  (NoSQLStreamFeederFilter) f.getFeederFilter();
              strFilter.initPartGenTableFromMigration(dbId, ownedParts);
              logger.info(() -> "Whitelist for stream filter to " +
                                f.getReplicaNode().getHostName() +
                                ": " + ownedParts);
          });
    }

    /**
     * Returns the list of partition ids owned by the given rep group
     *
     * @param topo  topology
     * @param gid   replication group id
     *
     * @return list of owned partitions, or
     */
    private Set<PartitionId> getOwnedParts(Topology topo, int gid) {
        final PartitionMap pmap = topo.getPartitionMap();
        return pmap.getAllIds().stream()
                   .filter(pid -> pmap.getRepGroupId(pid).getGroupId() == gid)
                   .collect(Collectors.toSet());
    }
}
