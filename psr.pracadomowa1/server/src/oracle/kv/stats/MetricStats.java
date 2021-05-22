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

package oracle.kv.stats;

/**
 * Describes the statistics of a numeric metric.
 *
 * @hidden Until we make the async metrics public
 */
public interface MetricStats {

    /**
     * Returns the count.
     */
    long getCount();

    /**
     * Returns the minimum.
     */
    long getMin();

    /**
     * Returns the maximum.
     */
    long getMax();

    /**
     * Returns the average.
     */
    long getAverage();

    /**
     * Returns the 95th percentile.
     */
    long getPercent95();

    /**
     * Returns the 99th percentile.
     */
    long getPercent99();
}
