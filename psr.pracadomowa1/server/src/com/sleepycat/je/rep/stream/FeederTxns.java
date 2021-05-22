/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je.rep.stream;

import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.ACK_TXN_AVG_NS;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.LAST_COMMIT_TIMESTAMP;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.LAST_COMMIT_VLSN;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.LOCAL_TXN_AVG_NS;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.TXNS_ACKED;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.TXNS_NOT_ACKED;
import static com.sleepycat.je.rep.stream.FeederTxnStatDefinition.VLSN_RATE;

import java.util.concurrent.TimeUnit;

import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.rep.InsufficientAcksException;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.node.DurabilityQuorum;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.txn.MasterTxn;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.AtomicLongStat;
import com.sleepycat.je.utilint.LongAvgRateStat;
import com.sleepycat.je.utilint.LongAvgStat;
import com.sleepycat.je.utilint.NoClearAtomicLongStat;
import com.sleepycat.je.utilint.SimpleTxnMap;
import com.sleepycat.je.utilint.StatGroup;

/**
 * FeederTxns manages transactions that need acknowledgments.
 *
 * <p>The lastCommitVLSN, lastCommitTimestamp, and vlsnRate statistics provide
 * general information about committed transactions on the master, but are also
 * intended to be used programmatically along with other statistics for the
 * feeder to provide information about how up-to-date the replicas are.  See
 * the Feeder class for more details.
 */
public class FeederTxns {

    /** The moving average period in milliseconds */
    private static final long MOVING_AVG_PERIOD_MILLIS = 10000;

    /*
     * Tracks transactions that have not yet been acknowledged for the entire
     * replication node.
     */
    private final SimpleTxnMap<MasterTxn> txnMap;

    private final RepImpl repImpl;
    private final StatGroup statistics;
    private final AtomicLongStat txnsAcked;
    private final AtomicLongStat txnsNotAcked;
    private final NoClearAtomicLongStat lastCommitVLSN;
    private final NoClearAtomicLongStat lastCommitTimestamp;
    private final LongAvgRateStat vlsnRate;
    private final LongAvgStat ackTxnAvgNs;
    private final LongAvgStat localTxnAvgNs;

    public FeederTxns(RepImpl repImpl) {

        txnMap = new SimpleTxnMap<>(1024);
        this.repImpl = repImpl;
        statistics = new StatGroup(FeederTxnStatDefinition.GROUP_NAME,
                                   FeederTxnStatDefinition.GROUP_DESC);
        txnsAcked = new AtomicLongStat(statistics, TXNS_ACKED);
        txnsNotAcked = new AtomicLongStat(statistics, TXNS_NOT_ACKED);
        lastCommitVLSN =
            new NoClearAtomicLongStat(statistics, LAST_COMMIT_VLSN);
        lastCommitTimestamp =
            new NoClearAtomicLongStat(statistics, LAST_COMMIT_TIMESTAMP);
        vlsnRate = new LongAvgRateStat(
            statistics, VLSN_RATE, MOVING_AVG_PERIOD_MILLIS, TimeUnit.MINUTES);
        localTxnAvgNs = new LongAvgStat(statistics, LOCAL_TXN_AVG_NS);
        ackTxnAvgNs = new LongAvgStat(statistics, ACK_TXN_AVG_NS);
    }

    public AtomicLongStat getLastCommitVLSN() {
        return lastCommitVLSN;
    }

    public AtomicLongStat getLastCommitTimestamp() {
        return lastCommitTimestamp;
    }

    public LongAvgRateStat getVLSNRate() {
        return vlsnRate;
    }

    /**
     * Track the txn in the transaction map if it needs acknowledgments
     *
     * @param txn identifies the transaction.
     */
    public void setupForAcks(MasterTxn txn) {
        if (txn.getRequiredAckCount() == 0) {
            /* No acks called for, no setup needed. */
            return;
        }
        txnMap.put(txn);
    }

    /**
     * Returns the transaction if it's waiting for acknowledgments. Returns
     * null otherwise.
     */
    public MasterTxn getAckTxn(long txnId) {
        return txnMap.get(txnId);
    }

    public LongAvgStat getLocalTxnAvgNs() {
        return localTxnAvgNs;
    }

    public LongAvgStat getAckTxnAvgNs() {
        return ackTxnAvgNs;
    }

    /*
     * Clears any ack requirements associated with the transaction. It's
     * typically invoked on a transaction abort.
     */
    public void clearTransactionAcks(Txn txn) {
        txnMap.remove(txn.getId());
    }

    /**
     * Notes that an acknowledgment was received from a replica, count it down.
     *
     * @param replica the replica node supplying the ack
     * @param txnId the locally committed transaction that was acknowledged.
     *
     * @return the MasterTxn associated with the txnId, if txnId needs an ack,
     * null otherwise
     */
    public MasterTxn noteReplicaAck(final RepNodeImpl replica,
                                    final long txnId) {
        final DurabilityQuorum durabilityQuorum =
            repImpl.getRepNode().getDurabilityQuorum();
        if (!durabilityQuorum.replicaAcksQualify(replica)) {
            return null;
        }
        final MasterTxn txn = txnMap.get(txnId);
        if (txn == null) {
            return null;
        }
        txn.countdownAck();
        return txn;
    }

    /**
     * Waits for the required number of replica acks to come through.
     *
     * @param txn identifies the transaction to wait for.
     *
     * @param timeoutMs the amount of time to wait for the acknowledgments
     * before giving up.
     *
     * @throws InsufficientAcksException if the ack requirements were not met
     */
    public void awaitReplicaAcks(MasterTxn txn, int timeoutMs)
        throws InterruptedException {

        /* Record master commit information even if no acks are needed */
        final long vlsn = txn.getCommitVLSN();
        final long ackAwaitStartMs = System.currentTimeMillis();
        txn.setAckAwaitStartNs(System.nanoTime());
        txn.setAckAwaitTimeoutMs(timeoutMs);
        lastCommitVLSN.set(vlsn);
        lastCommitTimestamp.set(ackAwaitStartMs);
        vlsnRate.add(vlsn, ackAwaitStartMs);

        if (txn.getRequiredAckCount() <= 0) {
            /* Transaction does need acks */
            return;
        }

        try {
            final boolean gotAcks = txn.await(ackAwaitStartMs, timeoutMs);
            updateTxnStats(txn, gotAcks);
        } finally {
            txnMap.remove(txn.getId());
        }

        final RepNode repNode = repImpl.getRepNode();
        if (repNode != null) {
            repNode.getDurabilityQuorum().ensureSufficientAcks(txn, timeoutMs);
        }
    }

    /* Update txn stats after receipt of sync or async acks. */
    private void updateTxnStats(MasterTxn txn,
                                final boolean gotAcks) {
        if (gotAcks) {
            txnsAcked.increment();
            final long nowNs = System.nanoTime();
            ackTxnAvgNs.add(nowNs -  txn.getStartNs());
        } else {
            txnsNotAcked.increment();
        }
    }

    public StatGroup getStats() {
        StatGroup ret = statistics.cloneGroup(false);

        return ret;
    }

    public void resetStats() {
        statistics.clear();
    }

    public StatGroup getStats(StatsConfig config) {

        StatGroup cloneStats = statistics.cloneGroup(config.getClear());

        return cloneStats;
    }
}
