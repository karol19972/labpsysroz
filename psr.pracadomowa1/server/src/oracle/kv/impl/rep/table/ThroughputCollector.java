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

package oracle.kv.impl.rep.table;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import oracle.kv.ReadThroughputException;
import oracle.kv.ResourceLimitException;
import oracle.kv.TableAccessException;
import oracle.kv.TableSizeLimitException;
import oracle.kv.WriteThroughputException;
import oracle.kv.impl.api.ops.InternalOperation;
import oracle.kv.impl.api.ops.ThroughputTracker;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableLimits;
import oracle.kv.impl.measurement.TableInfo;
import oracle.kv.impl.rep.RepNode;
import oracle.kv.impl.rep.admin.ResourceInfo.RateRecord;
import oracle.kv.impl.rep.admin.ResourceInfo.UsageRecord;
import oracle.kv.impl.topo.RepGroup;
import oracle.kv.impl.topo.RepGroupMap;
import oracle.kv.impl.topo.Topology;

/**
 * An object for collecting throughput data for a table. The collector records
 * throughput in two ways, in one seconds chunks and a running total. The data
 * collected in chunks can be aggregated across nodes with some accuracy. The
 * running total is better suited for monitoring throughput on a single node.
 *
 * The collector maintains a array of buckets. Each bucket represents one
 * second of throughput data. When data comes in, the current time (in
 * seconds) is used as an index into the array. The data is then recorded
 * into that bucket. The the buckets are updated, if the target bucket is
 * from an earlier second, the counts in that bucket are reset. Note that
 * since the index is based on time some buckets will be "skipped" over and
 * the buckets can appear out-of-order with regard to the second they
 * represent. This can be used to filter old buckets when collecting them
 * for aggregation.
 *
 * In addition to collecting data in buckets, the read/write bytes is summed
 * as single totals. When the totals are read (by the monitoring system) the
 * totals are reset.
 */
public class ThroughputCollector implements ThroughputTracker {
    /* Array size must be power of 2 */
    private static final int ARRAY_SIZE = 8;

    /* The mask is used to create an index from a time (in seconds). */
    private static final int INDEX_MASK = ARRAY_SIZE-1;

    /* Per read/write throughput is rounded up to this value */
    public final static int RW_BLOCK_SIZE = 1024;

    /* Use to convert bytes to GB */
    private static final long GB = 1024L * 1024L * 1024L;

    /*
     * The amount of time that a throughout cap is valid. After
     * this time, the cap should be re-calculated.
     *
     * Public for unit test.
     */
    public static final int CAP_TIME_SEC = 7;
    private static final long CAP_TIME_NANOS = SECONDS.toNanos(CAP_TIME_SEC);

    /* Minimum cap to allow some progress. */
    private static final int MIN_THROUGHPUT_CAP = 2;

    private final RepNode repNode;

    /*
     * The table and limit references are updated when the table metadata
     * is updated.
     */
    private volatile TableImpl table;

    private final RateBucket[] buckets = new RateBucket[ARRAY_SIZE];

    /* Running totals to support telemetry */
    private volatile long collectionStartMillis;
    private final AtomicInteger totalReadKB = new AtomicInteger();
    private final AtomicInteger totalWriteKB = new AtomicInteger();
    private final AtomicInteger readThroughputExceptions = new AtomicInteger();
    private final AtomicInteger writeThroughputExceptions = new AtomicInteger();
    private final AtomicInteger sizeExceptions = new AtomicInteger();
    private final AtomicInteger accessExceptions = new AtomicInteger();
    private final AtomicBoolean sizeReported = new AtomicBoolean();

    /* Last reported read and write rates. */
    private volatile int reportedReadRate;
    private volatile int reportedWriteRate;

    /*
     * Local caps are throughput limits adjusted for the local node. These are
     * the table limits adjusted down by the throughput reports. When being
     * throttled these value are adjusted by the number of nodes or shards.
     * They are also adjusted based on RN load.
     */
    private volatile int localReadCap;
    private volatile int localWriteCap;

    /*
     * The end time that the caps are valid. After this time the caps
     * need to be reset.
     */
    private volatile long capsValidNanos;

    /* Non-blocking synchronization to check whether the caps are valid */
    private final AtomicBoolean validCheckInProgress = new AtomicBoolean(false);

    /* Factors for calculating read and write throughput caps. */
    private volatile int nShards;
    private volatile int nNodes;

    /* Aggregate throughput tracker */
    private final ThroughputTracker aggregateTracker;

    // TODO - There is a risk of the table size being updated and the
    // sizeLimitExceeded set and never updated again if the aggregation service
    // fails. This will leave the table unavailable even if the size was
    // reduced. A suggestion is that we provide a timer to clear the flag
    // if the AS does not report for some period of time. Probably best done
    // in the RN or table manager.

    /*
     * Reported table size in bytes. Used for enforcing size limit. The value
     * of -1 indicates that the size is now known.
     */
    private volatile long sizeBytes = -1L;

    /* Updated when the limit changes or the size changes */
    private volatile boolean sizeLimitExceeded = false;

    /*
     * This is the difference between System.currentTimeMills() and
     * System.nanoTime() at the time of object creation. This is used to
     * synthesize the current second from nanoTime(). The nano time is
     * available in the request handler, and it avoids calling
     * currentTimeMills() in updateReadWriteBytes() which can be expensive.
     */
    private volatile long timeDeltaMills;

    ThroughputCollector(TableImpl table,
                        RepNode repNode,
                        Topology topo) {
        this.repNode = repNode;
        aggregateTracker = repNode.getAggregateThroughputTracker();

        updateTable(table, topo);

        for (int i = 0; i < ARRAY_SIZE; i++) {
            buckets[i] = new RateBucket();
        }
        collectionStartMillis = System.currentTimeMillis();
    }

    /**
     * Updates the table instance. The table instance may change due to a
     * table metadata update.
     */
    final void updateTable(TableImpl newTable, Topology topo) {
        assert newTable.isTop();
        assert newTable.getTableLimits() != null;
        assert newTable.getTableLimits().hasThroughputLimits() ||
               newTable.getTableLimits().hasSizeLimit();
        table = newTable;

        /* Set the time delta */
        final long currentMillis = System.currentTimeMillis();
        final long currentNanos = System.nanoTime();
        timeDeltaMills = currentMillis - NANOSECONDS.toMillis(currentNanos);

        updateLocalFactors(topo);

        /* This will set the sizeLimitExceeded flag */
        updateSize(sizeBytes, false /*reported*/);

        /* Set caps in case the limits changed */
        setReadWriteCap(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        currentNanos, table.getTableLimits());
    }

    /**
     * Updates the factors affecting the local caps.
     */
    private void updateLocalFactors(Topology topo) {
        /* If the topology is unavailable, use 1 for the values */
        if (topo == null) {
            nShards = 1;
            nNodes = 1;
            return;
        }

        final RepGroupMap repGroupMap = topo.getRepGroupMap();
        final int groups = repGroupMap.size();
        if (groups == 0) {
            /* Set nNodes & nShards to 1 to avoid math problems */
            nShards = 1;
            nNodes = 1;
            return;
        }
        nShards = groups;
        int nodes = 0;
        for (RepGroup rg : repGroupMap.getAll()) {
            nodes += rg.getRepNodes().size();
        }
        nNodes = (nodes == 0) ? 1 : nodes;
    }

    /*
     * Updates the size and sizeLimitExceeded flag. Method is synchronized
     * to keep sizeLimitExceeded consistent since it is called during a
     * metadata update and a rate update.
     */
    private synchronized void updateSize(long newSizeBytes, boolean reported) {
        if (newSizeBytes < 0) {
            return;
        }
        sizeBytes = newSizeBytes;
        final long limitBytes = table.getTableLimits().getSizeLimit() * GB;
        sizeLimitExceeded = sizeBytes > limitBytes;

        /*
         * If the size was via a report, set sizeReported so that the size
         * goes out in the next getTableInfo call
         */
        if (reported) {
            sizeReported.set(true);
        }
    }

    @Override
    public int addReadBytes(int bytes, boolean isAbsolute) {
        /* Record aggregated RN throughput */
        int readKB = aggregateTracker.addReadBytes(bytes, isAbsolute);
        readKB = readKBToAdd(readKB, isAbsolute);
        updateReadWriteBytes(readKB, 0);
        return readKB;
    }

    @Override
    public int addWriteBytes(int bytes, int nIndexWrites) {
        /* Record aggregated RN throughput */
        final int writeKB = aggregateTracker.addWriteBytes(bytes, nIndexWrites);
        updateReadWriteBytes(0, writeKB);
        return writeKB;
    }

    @Override
    public int getReadKBToAdd(int bytes, boolean isAbsolute) {
        /* Record aggregated RN throughput */
        int readKB = aggregateTracker.getReadKBToAdd(bytes, isAbsolute);
        return readKBToAdd(readKB, isAbsolute);
    }

    /**
     * Adds the specified read units. The table is checked for read access
     * before the units are added. A check for read throughout limit
     * exceeded is made after the units are added. A unit is 1KB.
     */
    void addReadUnits(int units) {
        final TableLimits limits = table.getTableLimits();
        checkAccess(limits, false, false);
        addReadBytes(units * RW_BLOCK_SIZE, false /* isAbsolute */);
        checkForLimitExceeded(true, false, false);
    }

    /*
     * Returns the read cost according to if using absolute consistency or not.
     */
    private int readKBToAdd(int readKB, boolean isAbsolute) {
        /* Costs are double if using absolute consistency */
        return isAbsolute ? (readKB + readKB) : readKB;
    }

    /*
     * Updates the collector with the specified throughput information. The
     * bytes are accumulated in the "current" bucket.
     */
    private void updateReadWriteBytes(int readKB, int writeKB) {
        final long second = getSecond(System.nanoTime());
        final int index = (int)second & INDEX_MASK;
        final RateBucket bucket = buckets[index];
        bucket.updateReadWriteKB(second, readKB, writeKB);

        /* Update the running totals */
        totalReadKB.addAndGet(readKB);
        totalWriteKB.addAndGet(writeKB);
    }

    /**
     * Returns the rate bucket for the specified time offset. Returns null if
     * no bucket is found.
     *
     * @return the rate bucket for the specified time offset or null
     */
    private RateBucket getBucket(long nanoTime) {
        final long second = getSecond(nanoTime);
        final int index = (int)second & INDEX_MASK;
        final RateBucket bucket = buckets[index];

        /* Return null if the bucket is not for this second */
        return (bucket.second.get() != second) ? null : bucket;
    }

    /*
     * Returns the synthesized second based on the time returned from
     * System.nanoTime().
     */
    private long getSecond(long nanoTime) {
        final long currentTimeMillis =
                            NANOSECONDS.toMillis(nanoTime) + timeDeltaMills;
        return currentTimeMillis / 1000;
    }

    /**
     * Checks if accessed is permitted and whether a limit has been exceeded.
     * If either accessed is denied or a limit exceeded a
     * ResourceExceededException is thrown. The type of access check is based
     * on the operation.
     */
    public void checkForLimitExceeded(InternalOperation internalOp) {
        checkForLimitExceeded(internalOp.performsRead(),
                              internalOp.performsWrite(),
                              internalOp.isDelete());
    }

    private void checkForLimitExceeded(boolean performsRead,
                                       boolean performsWrite,
                                       boolean isDelete) {
        final TableLimits limits = table.getTableLimits();

        /* Check access */
        if (performsWrite) {
            /* If a delete skip the size check to allow size reduction. */
            checkAccess(limits, true, !isDelete);
        } else {
            checkAccess(limits, false, false);
        }
        checkForLimitExceeded(limits, performsRead, performsWrite);
    }

    private void checkForLimitExceeded(TableLimits limits,
                                       boolean performsRead,
                                       boolean performsWrite) {
        final long now = System.nanoTime();
        if (now > capsValidNanos) {
            checkLocalLimitsValid(now);
        }

        final RateBucket bucket = getBucket(now);
        if (performsRead) {
            checkReadLimit(limits, bucket, now);
        }

        if (performsWrite) {
            checkWriteLimit(limits, bucket, now);
        }
    }

    /**
     * Returns true if there is read or write throttling at this time. If
     * checkAccess is true, access is checked and an exception is thrown if
     * access is not granted.
     */
    public boolean isThrottled(boolean checkAccess) {
        final TableLimits limits = table.getTableLimits();
        if (checkAccess) {
            /* Throws exception if access is denied */
            checkAccess(limits, true /*isWrite*/, true /*checkSize*/);
        }

        try {
            checkForLimitExceeded(limits, true, true);
        } catch (ResourceLimitException rle) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether the read rate in the specified by bucket exceeds the
     * local read limit. If the limit has been exceeded and the limits
     * are still valid, a ThroughputLimitException is thrown.
     */
    private void checkReadLimit(TableLimits limits,
                                RateBucket bucket,
                                long now) {
        if (bucket == null) {
            return;
        }
        int rate = bucket.readKB.get();
        if (rate <= localReadCap) {
            return;
        }
        /* Over, check whether the local limits are still valid */
        if (checkLocalLimitsValid(now)) {
            readThroughputExceptions.incrementAndGet();
            /*
             * If the reported rate is over the local rate, report that. Note
             * that reportedWriteRate can be 0 if this is just a local overage.
             */
            if (reportedReadRate > rate) {
                rate = reportedReadRate;
            }
            final int limit = limits.getReadLimit();
            throw new ReadThroughputException(
                                    table.getName(), rate, limit,
                                    "Read throughput rate exceeded for table " +
                                    table.getName() +
                                    ". Actual: " + rate +
                                    " KB/Sec Limit " + limit);
        }
    }

    /**
     * Checks whether the write rate in the specified by bucket exceeds the
     * local write limit. If the limit has been exceeded and the limits
     * are still valid, a ThroughputLimitException is thrown.
     */
    private void checkWriteLimit(TableLimits limits,
                                 RateBucket bucket,
                                 long now) {
        if (bucket == null) {
            return;
        }
        int rate = bucket.writeKB.get();
        if (rate <= localWriteCap) {
            return;
        }
        if (checkLocalLimitsValid(now)) {
            writeThroughputExceptions.incrementAndGet();
            if (reportedWriteRate > rate) {
                rate = reportedWriteRate;
            }
            final int limit = limits.getWriteLimit();
            throw new WriteThroughputException(
                                   table.getName(), rate, limit,
                                   "Write throughput rate exceeded for table " +
                                   table.getName() +
                                   ". Actual: " + rate +
                                   " KB/Sec Limit " + limit + " KB/Sec");
        }
    }

    /**
     * Checks how long it has been since the last time throughput rates were
     * reported. If the last report has been over the time allowed, clear the
     * read and write caps and return false.
     *
     * @return true if the read/write limits are valid.
     */
    private boolean checkLocalLimitsValid(long now) {
        /*
         * Only one thread needs to be checking. If a check is in progress
         * assume the limits are OK and return true.
         */
        if (validCheckInProgress.getAndSet(true)) {
            return true;
        }
        try {
            /*
             * There is a window between the check and reseting the rates where
             * a report can occur and be lost. This is unlikely as long as the
             * report limit is less then the poll period. In any case, it is not
             * worth the extra synchronization to close this window.
             */
            if (now < capsValidNanos) {
                return true;
            }
            /* Reset the caps */
            resetReadWriteCap(now);
            return false;
        } finally {
            validCheckInProgress.set(false);
        }
    }

    /**
     * Checks access to the table. If isWrite is true, the table must permit
     * read and write access, otherwise the table must permit read. If
     * checkSize is true the table size is also checked.
     */
    private void checkAccess(TableLimits limits,
                             boolean isWrite,
                             boolean checkSize) {
        if (!limits.isReadAllowed()) {
            accessExceptions.incrementAndGet();
            throw new TableAccessException(
                    table.getName(), limits.isReadAllowed(),
                    "Access not permitted to table " + table.getName());
        }
        if (isWrite && !limits.isWriteAllowed()) {
            accessExceptions.incrementAndGet();
            throw new TableAccessException(
                    table.getName(), limits.isReadAllowed(),
                    "Table " + table.getName() + " is read-only");
        }
        if (checkSize && sizeLimitExceeded) {
            assert isWrite;
            assert sizeBytes >= 0;
            sizeExceptions.incrementAndGet();
            /*
             * There is a tiny window between the sizeLimitExceeded test above
             * and here in which the size can change. It does not make the
             * exception invalid, but the reported size could be incorrect.
             * Not worth any effort to correct.
             */
            final int sizeGB = (int)(sizeBytes / GB);
            final int limitGB = limits.getSizeLimit();
            throw new TableSizeLimitException(
                            table.getName(), sizeGB, limitGB,
                            "Size exceeded for table " + table.getName() +
                            ", size limit: " + limitGB +
                            "GB, table size: " + sizeGB + "GB");
        }
    }

    /**
     * Collects the rate records since the specified time. The records are
     * added to the specified resource info. Each RateBucket is scanned to
     * see if it contains data newer than since. The current bucket (indexed
     * by nowSec) is skipped.
     */
    void collectRateRecords(Set<RateRecord> records,
                            long sinceSec, long nowNano) {
        final long nowSec = getSecond(nowNano);
        int currentIndex = (int)nowSec & INDEX_MASK;
        for (int i = 0; i < ARRAY_SIZE; i++) {
            /* Leave out the current record since it may be active */
            if (i == currentIndex) {
                continue;
            }

            /*
             * There is no need to filter out empty buckets. If there has not
             * been any activity during a given second, the bucket for that
             * second would not have been updated. It will remain an "old"
             * second and get filtered by the sinceSec check.
             *
             * Note that the check and creation of the RateRecord is not
             * atomic. This could lead to incorrect data. Since we are skipping
             * the current record this should be infrequent. It would require
             * additional synchronization with the request/response path to
             * close this window.
             */
            final RateBucket bucket = buckets[i];
            if (bucket.second.get() >= sinceSec) {
                records.add(new RateRecord(table,
                                           bucket.second.get(),
                                           bucket.readKB.get(),
                                           bucket.writeKB.get()));
            }
        }
    }

    /**
     * Records table size and throughput rates. Also sets the local caps based
     * on rates and other environmental factors.
     */
    public void report(UsageRecord ur, long reportTimeNanos) {
        updateSize(ur.getSize(), true /*reported*/);

        final TableLimits limits = table.getTableLimits();

        /*
         * Calculate new read and write limits if the reported rate is > 0.
         * These are used to set the local caps.
         *
         * -1 indicates that the cap does not need to be changed
         */
        int newReadLimit = -1;
        int newWriteLimit = -1;

        final int readRate = ur.getReadRate();
        if (readRate >= 0) {
            final int readLimit = limits.getReadLimit();

            /*
             * If the rate less than the limit, it means that the report
             * is to adjust the ramp-up and not an overage.
             */
            if (readRate >= readLimit) {
                reportedReadRate = readRate;
            }

            /*
             * Calculate the new limit by adjusting up or down depending
             * on the reported rate.
             *
             * dx > 0 == over the limit, dx < 0 == under
             */
            final int dx = readRate - readLimit;
            newReadLimit = readLimit - dx;

            if (newReadLimit < 0) {
                newReadLimit = 0;
            } else {
                /*
                 * While being throttled, the local caps are set to per-node
                 * values. This produces the desired average throughput when
                 * accesses are evenly distributed. Hotspot behavior will not
                 * be as smooth.
                 */
                newReadLimit /= nNodes;
            }
        }

        final int writeRate = ur.getWriteRate();
        if (writeRate >= 0) {
            final int writeLimit = limits.getWriteLimit();
            if (writeRate >= writeLimit) {
                reportedWriteRate = writeRate;
            }
            final int dx = writeRate - writeLimit;
            newWriteLimit = writeLimit - dx;

            if (newWriteLimit < 0) {
                newWriteLimit = 0;
            } else {
                newWriteLimit /= nShards;
            }
        }
        setReadWriteCap(newReadLimit, newWriteLimit, reportTimeNanos, limits);
    }

    /*
     * Resets to the read and write caps. This will attempt to increase the
     * caps by about 25% of the r/w limits baring adjustment due to
     * environmental factors.
     */
    private synchronized void resetReadWriteCap(long nowNanos) {
        final TableLimits limits = table.getTableLimits();
        setReadWriteCap(localReadCap + (limits.getReadLimit() / 4) + 1,
                        localWriteCap + (limits.getWriteLimit() / 4) + 1,
                        nowNanos, limits);
    }

    /*
     * Sets the read and write caps based on the requested limits, the
     * table's limits, as well as other environmental factors.
     */
    private synchronized void setReadWriteCap(int newReadLimit,
                                              int newWriteLimit,
                                              long nowNanos,
                                              TableLimits limits) {
        /* Record the last time the caps were set */
        capsValidNanos = nowNanos + CAP_TIME_NANOS;

        /*
         * If no change (new limit < 0) continue with the current cap which may
         * still be adjusted.
         */
        final int newReadCap = (newReadLimit < 0) ? localReadCap : newReadLimit;
        localReadCap = getLocalCap(newReadCap, limits.getReadLimit(), nNodes);

        /*
         * If the cap has returned to the non-throttled value, clear the
         * reported rate.
         */
        if (localReadCap >= limits.getReadLimit()) {
            reportedReadRate = 0;
        }

        final int newWriteCap = (newWriteLimit < 0) ? localWriteCap :
                                                      newWriteLimit;
        localWriteCap = getLocalCap(newWriteCap, limits.getWriteLimit(), nShards);
        if (localWriteCap >= limits.getWriteLimit()) {
            reportedWriteRate = 0;
        }
    }

    /**
     * Returns the local cap value based on inputs and load. The requested cap is
     * clamped to the max cap which is based on the table limit and load. The
     * max cap a value between the per-node limit and table limit:
     *
     *  ----- table limit
     *    |                 less load
     *    |                     ^
     *    |                     |
     *     }- range of max cap -+
     *    |                     |
     *    |                     v
     *    |                  higher load
     *  --+-- per-node limit
     *    |
     *    |
     *  --+-- MIN_THROUGHPUT_CAP
     *    |
     *  ----- 0
     *
     * In all cases, the minimum return value will be the higher of
     * per-node-limit (1/Nth) and MIN_THROUGHPUT_CAP (in cases of very low
     * throughput tables, per-node limit can go below MIN_THROUGHPUT_CAP).
     *
     * @param requestedCap the requested cap
     * @param limit the table's throughout limit
     * @param factor topology factor (nShards/nNodes)
     * @return the local cap
     */
    private int getLocalCap(int requestedCap, int limit, int factor) {
        assert requestedCap >= 0;
        if (limit == 0) {
            return 0;
        }

        /*
         * Calculate the maximum local cap by starting with the local limit
         * (table limit / node or shard) and then adding part or all of
         * difference between the local limit and the table limit. The amount
         * of the difference added is based on load.
         */
        int localLimit = limit / factor;
        if (localLimit < MIN_THROUGHPUT_CAP) {
            localLimit = MIN_THROUGHPUT_CAP;
        }

        final int extra = limit - localLimit;

        /* Adjust due to load, the larger appPercent is the busier */
        final int appPercent = repNode.getTaskCoordinator().getAppPermitPercent();
        assert appPercent <= 100;

        /* If no load (appPercent == 0) then maxCap is the table limit */
        final int maxCap = localLimit + ((extra * (100 - appPercent)) / 100);

        /* If maxCap is less than min, return min. */
        if (maxCap <= MIN_THROUGHPUT_CAP) {
            return MIN_THROUGHPUT_CAP;
        }

        /* never let the local cap get below its own local limit */
        /* See KVSTORE-181 for more details */
        if (requestedCap < localLimit) {
            requestedCap = localLimit;
        }

        return Math.min(maxCap, requestedCap);
    }

    /**
     * Gets a TableInfo object. If there has been no activity since the last
     * time getTableInfo() was called, null is returned. Otherwise the TableInfo
     * object is filled-in with the data collected since that time.
     *
     * @return a TableInfo object or null
     */
    TableInfo getTableInfo(long currentTimeMillis) {
        final long startTime = collectionStartMillis;
        final long duration = currentTimeMillis - collectionStartMillis;
        /* Avoid math problems later */
        if (duration <= 0) {
            return null;
        }
        collectionStartMillis = currentTimeMillis;
        final int readKB = totalReadKB.getAndSet(0);
        final int writeKB = totalWriteKB.getAndSet(0);
        final int rte = readThroughputExceptions.getAndSet(0);
        final int wte = writeThroughputExceptions.getAndSet(0);
        final int se = sizeExceptions.getAndSet(0);
        final int ae = accessExceptions.getAndSet(0);
        final boolean sr = sizeReported.getAndSet(false);
        if (sr || (readKB > 0) || (writeKB > 0) ||
            (rte > 0) || (wte > 0) || (se > 0) || (ae > 0)) {
            return new TableInfo(table.getFullNamespaceName(),
                                 table.getId(),
                                 startTime, duration,
                                 readKB, writeKB, sizeBytes,
                                 rte, wte, se, ae);
        }
        return null;
    }

    @Override
    public String toString() {
        return "ThroughputCollector[" + table.getId() +
               ", caps: " + localReadCap + " " + localWriteCap + "]";
    }

    /**
     * An object to collect rate information for one second.
     */
    private class RateBucket {
        private final AtomicLong second = new AtomicLong();
        private final AtomicInteger readKB = new AtomicInteger();
        private final AtomicInteger writeKB = new AtomicInteger();

        private void updateReadWriteKB(long currentSecond,
                                       int currentReadKB,
                                       int currentWriteKB) {

            /*
             * If this is a new second set the bytes to the current bytes to act
             * as a reset, otherwise add the current bytes. Note that there is a
             * window between the second.getAndSet and read/write add/set calls
             * where data can be lost if two threads hit his gap at the same
             * time. This loss is not worth the cost of additional
             * synchronization.
             */
            final long oldSecond = second.getAndSet(currentSecond);
            if (oldSecond == currentSecond) {
                readKB.addAndGet(currentReadKB);
                writeKB.addAndGet(currentWriteKB);
            } else {
                readKB.set(currentReadKB);
                writeKB.set(currentWriteKB);
            }
        }

        @Override
        public String toString() {
            return "RateBucket[" + second + ", " + readKB + ", "
                   + writeKB + "]";
        }
    }
}
