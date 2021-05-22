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

package oracle.kv.impl.async.perf;

import java.util.concurrent.atomic.AtomicLong;

import com.sleepycat.je.utilint.DoubleExpMovingAvg;

/**
 * A perf tracker for the resource manager.
 */
public class DialogResourceManagerPerfTracker {

    /**
     * The count of the operations, for use in computing the moving average.
     */
    private final AtomicLong opCount = new AtomicLong();

    /**
     * Tracks an exponential moving average over the last 1 million operations
     * of the number of available permits .
     */
    private final DoubleExpMovingAvg avgAvailablePermits =
        new DoubleExpMovingAvg("avgAvailablePermits", 1000000);

    /**
     * Tracks an exponential moving average over the last 1 million operations
     * of the available permit available percentage, i.e., availableNumPermits
     * / totalNumPermits.
     */
    private final DoubleExpMovingAvg avgAvailablePercentage =
        new DoubleExpMovingAvg("avgAvailablePercentage", 1000000);

    /**
     * Updates the stats upon an operation.
     */
    public void update(int availableNumPermits, int totalNumPermits) {
        final long count = opCount.incrementAndGet();
        avgAvailablePermits.add(availableNumPermits, count);
        avgAvailablePercentage.add(
            ((double) availableNumPermits) / totalNumPermits, count);
    }

    /**
     * Returns the metrics.
     */
    public DialogResourceManagerMetrics getMetrics() {
        return new DialogResourceManagerMetrics(
            avgAvailablePermits.get(), avgAvailablePercentage.get());
    }
}
