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

package oracle.kv.impl.xregion.stat;

import oracle.kv.impl.pubsub.StreamOpMetrics;
import oracle.kv.impl.util.FormatUtils;

/**
 * Object represents the statistics of a shard from a remote region
 */
public class ShardMetrics {

    /** timestamp when last msg is received */
    private volatile long shardLastMsgTime = 0;
    /** modification time of the last received operation */
    private volatile long shardLastModTime = 0;
    /** latency metrics */
    private final StreamOpMetrics latencyMs = new StreamOpMetrics();

    public ShardMetrics() {
    }

    long getLastMsgTime() {
        return shardLastMsgTime;
    }

    long getLastModTime() {
        return shardLastModTime;
    }

    StreamOpMetrics getLatencyMetrics() {
        return latencyMs;
    }

    public synchronized void setLastMsgTime(long val) {
        shardLastMsgTime = Math.max(shardLastMsgTime, val);
    }

    public synchronized void setLastModTime(long val) {
        shardLastModTime = Math.max(shardLastModTime, val);
    }

    public void addLatency(long val) {
        latencyMs.addOp(val);
    }

    @Override
    public String toString() {
        return "\narrival time of last msg=" +
               FormatUtils.formatTime(shardLastMsgTime) + "\n" +
               "mod. time of last msg=" +
               FormatUtils.formatTime(shardLastModTime) + "\n" +
               "latency=[" + latencyMs + "]\n";
    }
}
