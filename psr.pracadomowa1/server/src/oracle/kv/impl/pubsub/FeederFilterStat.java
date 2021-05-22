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

import oracle.kv.impl.util.FormatUtils;

import com.sleepycat.je.rep.subscription.SubscriptionStat;

/**
 * Object represent the feeder filter metrics
 */
public class FeederFilterStat {
    /** last feeder commit timestamp */
    private volatile long lastCommitTimeMs;
    /** modification time of last processed operation in filter */
    private volatile long lastModTimeMs;
    /** last vlsn processed by filter */
    private volatile long lastFilterVLSN;
    /** last vlsn passing the filter */
    private volatile long lastPassVLSN;
    /** filter lagging metrics */
    private volatile StreamOpMetrics laggingMs;

    public FeederFilterStat() {
        this.lastCommitTimeMs = 0;
        this.lastModTimeMs = 0;
        this.lastFilterVLSN = 0;
        this.lastPassVLSN = 0;
        laggingMs = new StreamOpMetrics();
    }

    FeederFilterStat(FeederFilterStat other) {
        this.lastCommitTimeMs = other.lastCommitTimeMs;
        this.lastModTimeMs = other.lastModTimeMs;
        this.lastFilterVLSN = other.lastFilterVLSN;
        this.lastPassVLSN = other.lastPassVLSN;
        laggingMs = new StreamOpMetrics(other.laggingMs);
    }

    public long getLastFilterVLSN() {
        return lastFilterVLSN;
    }

    public long getLastPassVLSN() {
        return lastPassVLSN;
    }

    public long getLastCommitTimeMs() {
        return lastCommitTimeMs;
    }

    public long getLastModTimeMs() {
        return lastModTimeMs;
    }

    public StreamOpMetrics getLaggingMs() {
        return new StreamOpMetrics(laggingMs);
    }

    /**
     * Sets the feeder filter stat from the subscription stat
     * @param stat subscription stat
     */
    void setFeederFilterStat(SubscriptionStat stat) {
        lastCommitTimeMs = stat.getLastCommitTimeMs();
        lastModTimeMs = stat.getLastModTimeMs();
        lastFilterVLSN = stat.getHighVLSN();
        lastPassVLSN = stat.getLastPassVLSN();
        laggingMs.addOp(getLagging());
    }

    /**
     * Unit test only, normally it should be set via
     * {@code setFeederFilterStat(SubscriptionStat)}
     */
    public void setLaggingMs(long val) {
        laggingMs.addOp(val);
    }

    @Override
    public String toString() {
        return "last commit time=" +
               FormatUtils.formatTime(lastCommitTimeMs) + ", " +
               "modification time of filter last op=" +
               FormatUtils.formatTime(lastModTimeMs) + ", " +
               "last filter vlsn=" + lastFilterVLSN + ", " +
               "last pass vlsn=" + lastPassVLSN;
    }

    private long getLagging() {
        return Math.max(0, lastCommitTimeMs - lastModTimeMs);
    }
}
