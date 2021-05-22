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

package oracle.kv.impl.measurement;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.DoubleStream;

import oracle.kv.KVVersion;
import oracle.kv.impl.async.perf.DialogResourceManagerMetrics;
import oracle.kv.impl.async.perf.EndpointMetricsComparator;
import oracle.kv.impl.async.perf.EndpointMetricsImpl;
import oracle.kv.impl.util.JsonUtils;
import oracle.kv.impl.util.ObjectUtil;
import oracle.kv.stats.EndpointGroupMetrics;
import oracle.kv.stats.EndpointMetrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.sleepycat.utilint.Latency;

/**
 * Endpoint group stats.
 */
public class EndpointGroupStats
    implements EndpointGroupMetrics, ConciseStats, Serializable {

    private static final long serialVersionUID = 1L;

    private static final KVVersion STATS_REFACTORING_VERSION = KVVersion.R20_3;

    static {
        assert KVVersion.PREREQUISITE_VERSION.
            compareTo(STATS_REFACTORING_VERSION) < 0 :
            "Checks due to incompatible serialization changes " +
            "to support the refactoring work of the stats mechanism " +
            "can be removed";
    }

    private final long intervalStart;
    private final long intervalEnd;
    /*
     * TODO: Make these field final and remove readObject method with the 23.1
     * release.
     */
    private volatile Map<String, EndpointMetricsImpl> creatorMetricsMap;
    private volatile List<EndpointMetricsImpl> sortedCreatorMetricsList;
    private volatile Map<String, EndpointMetricsImpl> responderMetricsMap;
    private volatile List<EndpointMetricsImpl> sortedResponderMetricsList;
    private volatile DialogResourceManagerMetrics dialogResourceManagerMetrics;

    /*
     * TODO: Remove the following fields with the 23.1 release.
     */
    @SuppressWarnings("unused")
    private final Map<String, EndpointMetrics> creatorMetrics =
        Collections.emptyMap();
    @SuppressWarnings("unused")
    private final List<EndpointMetrics> sortedCreatorMetrics =
        Collections.emptyList();
    @SuppressWarnings("unused")
    private final Map<String, EndpointMetrics> responderMetrics =
        Collections.emptyMap();
    @SuppressWarnings("unused")
    private final List<EndpointMetrics> sortedResponderMetrics =
        Collections.emptyList();
    @SuppressWarnings("unused")
    private final Map<String, Latency> latencyInfoMap = Collections.emptyMap();
    @SuppressWarnings("unused")
    private final Map<String, CounterGroup> counterInfoMap =
        Collections.emptyMap();
    @SuppressWarnings("unused")
    private final Map<String, List<String>> sampledDialogInfoMap =
        Collections.emptyMap();

    public static class CounterGroup implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public EndpointGroupStats(
        long intervalStart,
        long intervalEnd,
        Map<String, EndpointMetricsImpl> creatorMetricsMap,
        Map<String, EndpointMetricsImpl> responderMetricsMap,
        DialogResourceManagerMetrics dialogResourceManagerMetrics) {

        ObjectUtil.checkNull("creatorMetricsMap", creatorMetricsMap);
        ObjectUtil.checkNull("responderMetricsMap", responderMetricsMap);
        ObjectUtil.checkNull("dialogResourceManagerMetrics",
                             dialogResourceManagerMetrics);

        this.intervalStart = intervalStart;
        this.intervalEnd = intervalEnd;
        this.creatorMetricsMap = creatorMetricsMap;
        this.sortedCreatorMetricsList =
            new ArrayList<>(creatorMetricsMap.values());
        Collections.sort(
            sortedCreatorMetricsList,
            new EndpointMetricsComparator(sortedCreatorMetricsList));
        this.responderMetricsMap = responderMetricsMap;
        this.sortedResponderMetricsList =
            new ArrayList<>(responderMetricsMap.values());
        Collections.sort(
            sortedResponderMetricsList,
            new EndpointMetricsComparator(sortedResponderMetricsList));
        this.dialogResourceManagerMetrics = dialogResourceManagerMetrics;
    }

    @Override
    public long getStart() {
        return intervalStart;
    }

    @Override
    public long getEnd() {
        return intervalEnd;
    }

    @Override
    public String getFormattedStats() {
        return getFormattedStats(Integer.MAX_VALUE);
    }

    public String getFormattedStats(int maxNumEndpoints) {
        return toJson(maxNumEndpoints).toString();
    }

    public JsonObject toJson() {
        return toJson(Integer.MAX_VALUE);
    }

    public JsonObject toJson(int maxNumEndpoints) {
        final JsonObject result = new JsonObject();
        result.add("creatorEndpointMetrics",
                   getCreatorEndpointMetrics().stream()
                   .limit(maxNumEndpoints)
                   .map((m) ->
                        new AbstractMap.SimpleImmutableEntry
                        <String, JsonElement>(
                            m.getName(), m.toJson()))
                   .collect(JsonUtils.getObjectCollector()));
        result.add("responderEndpointMetrics",
                   getResponderEndpointMetrics().stream()
                   .limit(maxNumEndpoints)
                   .map((m) ->
                        new AbstractMap.SimpleImmutableEntry
                        <String, JsonElement>(
                            m.getName(), m.toJson()))
                   .collect(JsonUtils.getObjectCollector()));
        return result;
    }

    @Override
    public String getSummarizedStats() {
        return toSummarizedJson().toString();
    }

    public JsonObject toSummarizedJson() {
        final JsonObject result = new JsonObject();
        result.add("creatorEndpointSummary",
                   getEndpointsSummaryJson(getCreatorEndpointMetrics()));
        result.add("responderEndpointSummary",
                   getEndpointsSummaryJson(getResponderEndpointMetrics()));
        return result;
    }

    private JsonObject getEndpointsSummaryJson(
        List<EndpointMetricsImpl> metrics) {

        final JsonObject result = new JsonObject();
        result.addProperty("numEndpoints", metrics.size());
        if (metrics.size() > 0) {
            result.add("medianAndMaxStatsAmongEndpoints",
                       getMedianMax(metrics));
        }
        return result;
    }

    private JsonObject getMedianMax(List<EndpointMetricsImpl> metrics) {
        final JsonObject result = new JsonObject();
        result.add("dialogStartThroughput",
                   getMedianMax(metrics, m -> m.getDialogStartThroughput()));
        result.add("dialogDropThroughput",
                   getMedianMax(metrics, m -> m.getDialogDropThroughput()));
        result.add("dialogFinishThroughput",
                   getMedianMax(metrics, m -> m.getDialogFinishThroughput()));
        result.add("dialogAbortThroughput",
                   getMedianMax(metrics, m -> m.getDialogAbortThroughput()));
        result.add("avgFinishedDialogLatencyNanos",
                   getMedianMax(
                       metrics,
                       m -> m.getFinishedDialogLatencyNanos().getAverage()));
        result.add("avgAbortedDialogLatencyNanos",
                   getMedianMax(
                       metrics,
                       m -> m.getAbortedDialogLatencyNanos().getAverage()));
        result.add("avgDialogConcurrency",
                   getMedianMax(
                       metrics, m -> m.getDialogConcurrency().getAverage()));
        return result;
    }

    private JsonArray getMedianMax(
        List<EndpointMetricsImpl> metrics,
        Function<EndpointMetrics, Number> mapper) {

        final JsonArray result = new JsonArray();
        result.add(getMedian(metrics, mapper));
        result.add(getMax(metrics, mapper));
        return result;
    }

    private JsonPrimitive getMedian(List<EndpointMetricsImpl> metrics,
                                    Function<EndpointMetrics, Number> mapper) {
        final DoubleStream s = metrics.stream().
            mapToDouble((m) -> mapper.apply(m).doubleValue()).sorted();
        final int n = metrics.size();
        return new JsonPrimitive(
            ((n % 2 == 0) ?
             s.skip(n / 2 - 1).limit(2).average().orElse(0):
             s.skip(n / 2).findFirst().orElse(0)));
    }

    private JsonPrimitive getMax(List<EndpointMetricsImpl> metrics,
                                 Function<EndpointMetrics, Number> mapper) {
        return new JsonPrimitive(
            metrics.stream().
            mapToDouble((m) -> mapper.apply(m).doubleValue()).max().orElse(0));
    }

    @Override
    public String toString() {
        return getSummarizedStats();
    }

    public List<EndpointMetricsImpl> getCreatorEndpointMetrics() {
        return sortedCreatorMetricsList;
    }

    public List<EndpointMetricsImpl> getResponderEndpointMetrics() {
        return sortedResponderMetricsList;
    }

    @Override
    public List<EndpointMetrics> getEndpointMetricsList() {
        final List<EndpointMetrics> ret = new ArrayList<>();
        ret.addAll(getCreatorEndpointMetrics());
        ret.addAll(getResponderEndpointMetrics());
        return ret;
    }

    @Override
    public EndpointMetrics getEndpointMetrics(String address) {
        if (creatorMetricsMap.containsKey(address)) {
            return creatorMetricsMap.get(address);
        }
        return responderMetricsMap.get(address);
    }

    public DialogResourceManagerMetrics getDialogResourceManagerMetrics() {
        return dialogResourceManagerMetrics;
    }

    /**
     * Initialize the null fields because the object was serialized from a
     * version prior to 20.2 when the field was added.
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {

        in.defaultReadObject();
        /* Remove check when stats refactoring is complete */
        assert STATS_REFACTORING_VERSION != null;
        if (creatorMetricsMap == null) {
            creatorMetricsMap = Collections.emptyMap();
        }
        if (sortedCreatorMetricsList == null) {
            sortedCreatorMetricsList = Collections.emptyList();
        }
        if (responderMetricsMap == null) {
            responderMetricsMap = Collections.emptyMap();
        }
        if (sortedResponderMetricsList == null) {
            sortedResponderMetricsList = Collections.emptyList();
        }
        if (dialogResourceManagerMetrics == null) {
            dialogResourceManagerMetrics =
                new DialogResourceManagerMetrics(0, 0);
        }
    }
}

