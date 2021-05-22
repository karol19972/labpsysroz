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

import java.io.Serializable;
import java.util.Collection;

import oracle.kv.stats.MetricStats;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * Object represents stream operation metrics
 */
public class StreamOpMetrics implements MetricStats, Serializable {
    private static final long serialVersionUID = 1L;

    private volatile long min = -1;
    private volatile long max = -1;
    private volatile long avg = -1;
    private volatile long count = 0;
    private volatile long sum = 0;

    /**
     * No args constructor for use in serialization,
     * used when constructing instance from JSON.
     */
    public StreamOpMetrics() {
    }

    StreamOpMetrics(StreamOpMetrics other) {
        min = other.min;
        max = other.max;
        avg = other.avg;
        count = other.count;
        sum = other.sum;
    }

    public synchronized void addOp(long val) {
        if (val < 0) {
            throw new IllegalArgumentException("Invalid negative value=" + val);
        }
        min = (min == -1) ? val : Math.min(min, val);
        max = (max == -1) ? val : Math.max(max, val);
        sum += val;
        count += 1;
        avg = sum / count;
    }

    @Override
    public long getCount() {
        return count;
    }

    @JsonGetter("min")
    @Override
    public long getMin() {
        return min;
    }

    @JsonGetter("max")
    @Override
    public long getMax() {
        return max;
    }

    @JsonGetter("avg")
    @Override
    public long getAverage() {
        return avg;
    }

    @JsonSetter("avg")
    public void setAverage(long avg) {
        this.avg = avg;
    }

    @JsonIgnore
    @Override
    public long getPercent95() {
        throw new UnsupportedOperationException("not supported");
    }

    @JsonIgnore
    @Override
    public long getPercent99() {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StreamOpMetrics)) {
            return false;
        }
        final StreamOpMetrics other = (StreamOpMetrics) obj;
        return min == other.min &&
               max == other.max &&
               avg == other.avg &&
               count == other.count &&
               sum == other.sum;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(min) +
               Long.hashCode(max) +
               Long.hashCode(avg) +
               Long.hashCode(count);
    }

    @Override
    public String toString() {
        return "min=" + min + ", max=" + max + ", count=" + count +
               ", sum=" + sum + ", average=" + avg;
    }

    private long sum() {
        return sum;
    }

    public static StreamOpMetrics merge(Collection<StreamOpMetrics> oms) {
        if (oms == null || oms.isEmpty()) {
            return new StreamOpMetrics();
        }
        final StreamOpMetrics ret = new StreamOpMetrics();
        ret.min = oms.stream().
            mapToLong(StreamOpMetrics::getMin).min().orElse(-1);
        ret.max = oms.stream().
            mapToLong(StreamOpMetrics::getMax).max().orElse(-1);
        ret.count = oms.stream().mapToLong(StreamOpMetrics::getCount).sum();
        ret.sum = oms.stream().mapToLong(StreamOpMetrics::sum).sum();
        ret.avg = (ret.count == 0) ? -1 : (ret.sum / ret.count);
        return ret;
    }
}
