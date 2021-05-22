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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import static oracle.kv.impl.util.NumberUtil.longToIntOrLimit;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVVersion;
import oracle.kv.impl.api.RequestDispatcher;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.async.EndpointGroup;
import oracle.kv.impl.async.perf.MetricStatsImpl;
import oracle.kv.impl.measurement.EndpointGroupStats;
import oracle.kv.impl.measurement.LatencyResult;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.JsonUtils;
import oracle.kv.impl.util.registry.AsyncRegistryUtils;
import oracle.kv.table.TableAPI;

import com.google.gson.JsonObject;
import com.sleepycat.utilint.Latency;

/**
 * Statistics associated with accessing the KVStore from a client via the
 * KVStore handle. These statistics are from the client's perspective and can
 * therefore vary from client to client depending on the configuration and load
 * on a specific client as well as the network path between the client and the
 * nodes in the KVStore.
 *
 * @see KVStore#getStats(boolean)
 *
 * TODO: Instead of implementing Serializable, we might want to make it json
 * serializable, e.g., extends MeasureResult. This applies to other stats
 * interfaces as well.
 */
public class KVStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /*
     * A NULL_ENDPOINT_GROUP_METRICS for when the endpoint group is not
     * available, i.e., in sync mode
     */
    private static final EndpointGroupMetrics NULL_ENDPOINT_GROUP_METRICS =
        new EndpointGroupMetrics() {
            @Override
            public List<EndpointMetrics> getEndpointMetricsList() {
                return Collections.emptyList();
            }

            @Override
            public EndpointMetrics getEndpointMetrics(String address) {
                return null;
            }

            @Override
            public String getFormattedStats() {
                return "NA";
            }

            @Override
            public String getSummarizedStats() {
                return "NA";
            }
        };

    private final List<OperationMetrics> opMetrics;

    private final List<NodeMetrics> nodeMetrics;

    private final long requestRetryCount;

    private final EndpointGroupMetrics endpointGroupMetrics;

    /**
     * @hidden
     * Internal use only.
     */
    public KVStats(String watcherName,
                   boolean clear,
                   RequestDispatcher requestDispatcher) {

        final Topology topology = requestDispatcher.getTopology();
        this.requestRetryCount =
            (topology == null) ?
            0 : requestDispatcher.getTotalRetryCount(watcherName, clear);

        this.opMetrics =
            Collections.unmodifiableList(
                requestDispatcher.
                getLatencyStats(watcherName, clear).entrySet().stream().
                map((e) -> new OperationMetricsImpl(e.getKey(), e.getValue())).
                collect(Collectors.toList()));

        this.nodeMetrics =
            (topology == null) ?
            Collections.emptyList() :
            Collections.unmodifiableList(
                requestDispatcher.getRepGroupStateTable().
                getRepNodeStates().stream().
                /* Filter out the deleted RNs */
                filter((rns) -> topology.get(rns.getRepNodeId()) != null).
                map((rns) -> rns.getNodeMetrics(topology, watcherName, clear)).
                collect(Collectors.toList()));

        final EndpointGroup endpointGroup =
            AsyncRegistryUtils.getEndpointGroupOrNull();
        this.endpointGroupMetrics = (endpointGroup == null) ?
            NULL_ENDPOINT_GROUP_METRICS :
            endpointGroup.getEndpointGroupPerfTracker().
            obtain(watcherName, clear);
    }

    /**
     * Returns a list of metrics associated with each operation supported by
     * KVStore. The following table lists the method names and the name
     * associated with it by the {@link OperationMetrics#getOperationName()}.
     * <p>
     * It's worth noting that the metrics related to the Iterator methods are
     * special, since each use of an iterator call may result in multiple
     * underlying operations depending upon the <code>batchSize</code> used for
     * the iteration.
     *
     * <table border="1">
     * <caption style="display:none">List of operations and associated metrics</caption>
     * <tr>
     * <td>{@link KVStore#delete}, {@link TableAPI#delete}</td>
     * <td>delete</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#deleteIfVersion}, {@link TableAPI#deleteIfVersion}</td>
     * <td>deleteIfVersion</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#execute}, {@link TableAPI#execute}</td>
     * <td>execute</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#get}, {@link TableAPI#get}</td>
     * <td>get</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#multiDelete}, {@link TableAPI#multiDelete}</td>
     * <td>multiDelete</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#multiGet}, {@link TableAPI#multiGet}</td>
     * <td>multiGet</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#multiGetIterator}</td>
     * <td>multiGetIterator</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#multiGetKeys}, {@link TableAPI#multiGetKeys}</td>
     * <td>multiGetKeys</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#multiGetKeysIterator}</td>
     * <td>multiGetKeysIterator</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#put}, {@link TableAPI#put}</td>
     * <td>put</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#putIfAbsent}, {@link TableAPI#putIfAbsent}</td>
     * <td>putIfAbsent</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#putIfPresent}, {@link TableAPI#putIfPresent}</td>
     * <td>putIfPresent</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#putIfVersion}, {@link TableAPI#putIfVersion}</td>
     * <td>putIfVersion</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#storeIterator}, {@link TableAPI#tableIterator}</td>
     * <td>storeIterator</td>
     * </tr>
     * <tr>
     * <td>{@link KVStore#storeKeysIterator}, {@link TableAPI#tableKeysIterator}</td>
     * <td>storeKeysIterator</td>
     * </tr>
     * <tr>
     * <td>{@link TableAPI#tableIterator(oracle.kv.table.IndexKey, oracle.kv.table.MultiRowOptions, oracle.kv.table.TableIteratorOptions)}</td>
     * <td>indexIterator</td>
     * </tr>
     * <tr>
     * <td>{@link TableAPI#tableKeysIterator(oracle.kv.table.IndexKey, oracle.kv.table.MultiRowOptions, oracle.kv.table.TableIteratorOptions)}</td>
     * <td>indexKeysIterator</td>
     * </tr>
     * </table>
     *
     * @return the list of metrics. One for each of the operations listed
     * above.
     */
    public List<OperationMetrics> getOpMetrics() {
        return opMetrics;
    }

    /**
     * Returns a descriptive string, conforming to the json syntax.
     */
    @Override
    public String toString() {
        return toJson().toString();
    }

    /**
     * Returns a json representation of the the stats.
     *
     * @hidden internal use only
     */
    public JsonObject toJson() {
        final JsonObject result = new JsonObject();
        if (requestRetryCount > 0) {
            result.addProperty("requestRetryCount", requestRetryCount);
        }

        result.add("operationMetrics",
                   getOpMetrics().stream().
                   filter((m) -> m.getTotalOpsLong() > 0).
                   map((m) -> ((OperationMetricsImpl)m).toJson()).
                   collect(JsonUtils.getArrayCollector()));

        result.add("nodeMetrics",
                   getNodeMetrics().stream().
                   map((m) -> ((NodeMetricsImpl)m).toJson()).
                   collect(JsonUtils.getArrayCollector()));

        if (endpointGroupMetrics != null) {
            result.add("endpointGroupMetricsSummary",
                       ((EndpointGroupStats) endpointGroupMetrics)
                       .toSummarizedJson());
        }
        return result;
    }

    /**
     * Returns the metrics associated with each node in the KVStore.
     *
     * @return a list containing one entry for each node in the KVStore.
     */
    public List<NodeMetrics> getNodeMetrics() {
        return nodeMetrics;
    }

    /**
     * @deprecated since 3.4, no longer supported. All of the methods of the
     * returned StoreIteratorMetrics object will return 0.
     */
    @Deprecated
    public StoreIteratorMetrics getStoreIteratorMetrics() {
        return new StoreIteratorMetricsStub();
    }

    /**
     * Returns the total number of times requests were retried. A single
     * user-level request may be retried transparently at one or more nodes
     * until the request succeeds or it times out. This count reflects those
     * retry operations.
     *
     * @see KVStoreConfig#getRequestTimeout(TimeUnit)
     */
    public long getRequestRetryCount() {
        return requestRetryCount;
    }

    /**
     * Returns the async endpoint group metrics.
     *
     * @hidden Until we make async metrics public
     */
    public EndpointGroupMetrics getEndpointGroupMetrics() {
        return endpointGroupMetrics;
    }

    private static class OperationMetricsImpl
        implements OperationMetrics, Serializable {

        private static final long serialVersionUID = 1L;

        private static final KVVersion STATS_REFACTORING_VERSION =
            KVVersion.R20_3;

        static {
            assert KVVersion.PREREQUISITE_VERSION.
                compareTo(STATS_REFACTORING_VERSION) < 0 :
                "Checks due to incompatible serialization changes " +
                "to support the refactoring work of the stats mechanism " +
                "can be removed";
        }


        private final String operationName;
        /*
         * TODO: Make this field final and remove readObject method starting
         * with the 23.1 release.
         */
        private volatile LatencyResult latencyNanos;
        /*
         * TODO: Remove these with the 23.1 release.
         */
        @SuppressWarnings("unused")
        private final Latency latency =
            new Latency(0, 0, 0, 0, 0, 0, 0, 0, 0);

        OperationMetricsImpl(OpCode opCode,
                             LatencyResult latencyNanos) {
            this.operationName = opCodeToNameMap.get(opCode);
            this.latencyNanos = latencyNanos;
        }

        @Override
        public String getOperationName() {
            return operationName;
        }

        @Override
        public float getAverageLatencyMs() {
            return ((float) latencyNanos.getAverage()) / 1_000_000;
        }

        @Override
        public int getMaxLatencyMs() {
            return (int) NANOSECONDS.toMillis(latencyNanos.getMax());
        }

        @Override
        public int getMinLatencyMs() {
            return (int) NANOSECONDS.toMillis(latencyNanos.getMin());
        }

        @Override
        public int getTotalOps() {
            return longToIntOrLimit(getTotalOpsLong());
        }

        @Override
        public long getTotalOpsLong() {
            return latencyNanos.getOperationCount();
        }

        @Override
        public int getTotalRequests() {
            return longToIntOrLimit(getTotalRequestsLong());
        }

        @Override
        public long getTotalRequestsLong() {
            return latencyNanos.getRequestCount();
        }

        @Override
        public int get95thLatencyMs() {
            return (int) NANOSECONDS.toMillis(latencyNanos.getPercent95());
        }

        @Override
        public int get99thLatencyMs() {
            return (int) NANOSECONDS.toMillis(latencyNanos.getPercent99());
        }

        @Override
        public MetricStats getLatencyStatsNanos() {
            return new MetricStatsImpl(latencyNanos.getCount(),
                                       latencyNanos.getMin(),
                                       latencyNanos.getMax(),
                                       latencyNanos.getAverage(),
                                       latencyNanos.getPercent95(),
                                       latencyNanos.getPercent99());
        }

        @Override
        public String toString() {
            return toJson().toString();
        }

        public JsonObject toJson() {
            final JsonObject object = new JsonObject();
            object.addProperty("operationName", operationName);
            object.add("latencyNanos", latencyNanos.toJson());
            return object;
        }

        /**
         * Initialize the null fields because the object was serialized from a
         * version prior to 20.3 when the field was added.
         */
        private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {

            in.defaultReadObject();
            /* Remove check when stats refactoring is complete */
            assert STATS_REFACTORING_VERSION != null;
            if (latencyNanos == null) {
                latencyNanos = new LatencyResult(0, 0, 0, 0, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Converts an upper case enum name into camel case.
     */
    private static String camelCase(String enumName) {
        StringBuffer sb = new StringBuffer(enumName.length());

        for (int i=0; i < enumName.length(); i++) {

            char nchar = enumName.charAt(i);
            if (nchar == '_') {
                if (i++ >= enumName.length()) {
                    break;
                }
                nchar = Character.toUpperCase(enumName.charAt(i));
            } else {
                nchar = Character.toLowerCase(nchar);
            }
            sb.append(nchar);
        }
        return sb.toString();
    }

    private static Map<OpCode, String> opCodeToNameMap =
        new HashMap<OpCode, String>();

    static {
        for (OpCode opCode : OpCode.values()) {
            opCodeToNameMap.put(opCode, camelCase(opCode.name()));
        }
    }

    /**
     * Implements the {@link NodeMetrics}.
     *
     * @hidden internal use only.
     */
    public static class NodeMetricsImpl implements NodeMetrics {

        private static final long serialVersionUID = 1L;

        private final RepNodeId repNodeId;
        private final String datacenterName;
        private final boolean isActive;
        private final boolean isMaster;
        private final long maxNumActiveRequestCount;
        private final long requestCount;
        private final long errorCount;
        private final long averageTrailingResponseTimeNanos;
        private final long averageResponseTimeNanos;

        public NodeMetricsImpl(RepNodeId repNodeId,
                               String datacenterName,
                               boolean isActive,
                               boolean isMaster,
                               long maxNumActiveRequestCount,
                               long requestCount,
                               long errorCount,
                               long averageTrailingResponseTimeNanos,
                               long averageResponseTimeNanos) {
            this.repNodeId = repNodeId;
            this.datacenterName = datacenterName;
            this.isActive = isActive;
            this.isMaster = isMaster;
            this.maxNumActiveRequestCount = maxNumActiveRequestCount;
            this.requestCount = requestCount;
            this.errorCount = errorCount;
            this.averageTrailingResponseTimeNanos =
                averageTrailingResponseTimeNanos;
            this.averageResponseTimeNanos = averageResponseTimeNanos;
        }

        @Override
        public String getNodeName() {
            return repNodeId.toString();
        }

        @Override
        public String getDataCenterName() {
            return datacenterName;
        }

        @Override
        public String getZoneName() {
            return datacenterName;
        }

        @Override
        public boolean isActive() {
            return isActive;
        }

        @Override
        public boolean isMaster() {
            return isMaster;
        }

        @Override
        public int getMaxActiveRequestCount() {
            return (int) maxNumActiveRequestCount;
        }

        @Override
        public long getRequestCount() {
            return requestCount;
        }

        @Override
        public long getFailedRequestCount() {
            return errorCount;
        }

        @Override
        public int getAvLatencyMs() {
            return (int) NANOSECONDS.toMillis(
                averageTrailingResponseTimeNanos);
        }

        @Override
        public long getAverageLatencyNanos() {
            return averageResponseTimeNanos;
        }

        @Override
        public String toString() {
            return toJson().toString();
        }

        public JsonObject toJson() {
            final JsonObject object = new JsonObject();
            object.addProperty("nodeName", repNodeId.toString());
            object.addProperty("zoneName", datacenterName);
            object.addProperty("isActive", isActive);
            object.addProperty("isMaster", isMaster);
            object.addProperty("maxActiveRequests", maxNumActiveRequestCount);
            object.addProperty("requestCount", requestCount);
            object.addProperty("failedRequestCount", errorCount);
            object.addProperty("averageLatencyNanos",
                               averageResponseTimeNanos);
            return object;
        }
    }

    /*
     * Stub class to support deprecated API.
     */
    @SuppressWarnings("deprecation")
    private static class StoreIteratorMetricsStub
        implements StoreIteratorMetrics {

        @Override
        public long getBlockedResultsQueuePuts() { return 0L; }

        @Override
        public long getAverageBlockedResultsQueuePutTime() { return 0L; }

        @Override
        public long getMinBlockedResultsQueuePutTime() { return 0L; }

        @Override
        public long getMaxBlockedResultsQueuePutTime() { return 0L; }

        @Override
        public long getBlockedResultsQueueGets() { return 0L; }

        @Override
        public long getAverageBlockedResultsQueueGetTime() { return 0L; }

        @Override
        public long getMinBlockedResultsQueueGetTime() { return 0L; }

        @Override
        public long getMaxBlockedResultsQueueGetTime() { return 0L; }
    }
}
