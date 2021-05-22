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

package oracle.kv.impl.rep.monitor;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import static oracle.kv.impl.util.NumberUtil.longToIntOrLimit;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import oracle.kv.KVVersion;
import oracle.kv.impl.measurement.ConciseStats;
import oracle.kv.impl.measurement.EndpointGroupStats;
import oracle.kv.impl.measurement.EnvStats;
import oracle.kv.impl.measurement.JVMStats;
import oracle.kv.impl.measurement.LatencyInfo;
import oracle.kv.impl.measurement.LatencyInfoSummarizer;
import oracle.kv.impl.measurement.LatencyResult;
import oracle.kv.impl.measurement.Measurement;
import oracle.kv.impl.measurement.PerfStatType;
import oracle.kv.impl.measurement.RepEnvStats;
import oracle.kv.impl.measurement.TableInfo;
import oracle.kv.impl.monitor.Metrics;
import oracle.kv.impl.util.FormatUtils;
import oracle.kv.impl.util.JsonUtils;

import com.sleepycat.je.utilint.MapStat;
import com.sleepycat.je.utilint.Stat;
import com.sleepycat.je.utilint.StatDefinition;
import com.sleepycat.je.utilint.StatGroup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;




/**
 * A set of stats from a single measurement period.
 *
 * The RepNodes keep stats per type of API operation, as defined by
 * oracle.kv.impl.api.ops.InternalOperation. These are the base, detailed
 * interval stats. These stats can then be aggregated in two dimensions: (a)
 * over time to create cumulative stats, which cover the duration of this
 * repNode's uptime and (b) by type, so that several types of operations are
 * combined.
 *
 * Currently we summarize all user operations by interval and cumulative. To
 * illustrate, suppose there are these interval collections:
 *
 * {@literal
 * interval 1: base stats collected for get, deleteIfVersion.
 *    summarized cumulative stats encompass interval 1, all operations
 *    summarized interval stats encompass interval 1, all operations
 * interval 2: base stats collected for get, multiget
 *    summarized cumulative stats encompass interval 1 & 2, all operations
 *    summarized interval stats encompass interval 2, all operation.
 * interval 3: base stats collected for putIfAbsent, putIfVersion
 *    summarized cumulative stats encompass interval 1, 2, 3, all operations
 *    summarized interval stats encompass interval 3, all operations
 * }
 *
 * The summarized cumulative stats must be calculated on the RepNode, so that
 * they reflect the lifetime of the RepNode instance. The interval stats
 * could be calculated either on the RepNode or in the Admin/Monitor, but are
 * calculated in the RepNode for code consistency.
 *
 * The summarized stats are the most commonly used. The base stats are also
 * shipped across the wire to the Admin/Monitor for use in the CSVView, which
 * has more limited utility. They may also be used in the future for other
 * analysis.
 *
 * These stats are also shipped to SN monitoring and management agent.
 */
public class StatsPacket implements ConciseStats, Measurement, Serializable {

    private static final long serialVersionUID = 1L;

    /** Thread local copy of formatter, since the class isn't thread safe. */
    private static final ThreadLocal<DateFormat> dateTimeMillisFormatter =
        new ThreadLocal<DateFormat>() {
            @Override
            protected DateFormat initialValue() {
                return FormatUtils.getDateTimeAndTimeZoneFormatter();
            }
        };

    private final Map<Integer, LatencyInfo> latencies;
    private EnvStats envStats;
    private RepEnvStats repEnvStats;
    private final long start;
    private final long end;
    private final Map<String, Integer> exceptionStats;
    private int activeRequests;

    /**
     * The totalRequests field is obsolete and only maintained for backwards
     * compatibility with KV 19.1 and earlier.
     */
    @Deprecated
    private int totalRequests;
    static {
        assert KVVersion.PREREQUISITE_VERSION.compareTo(KVVersion.R19_1) <= 0
            : "StatsPacket.totalRequests field can be removed";
    }

    private long totalRequestsLong;
    private int avgQueuedAsyncRequests;
    private int maxQueuedAsyncRequests;
    private long asyncRequestQueueTimeAvgNanos;
    private long asyncRequestQueueTime95thNanos;
    private long asyncRequestQueueTime99thNanos;

    /* To act as tags for aggregating */
    private final String resource;
    private final String shard;

    private final List<ConciseStats> otherStats = new ArrayList<>();

    /*
     * Table stats. Can be null if none is available, or receiving stats packet
     * from older version store.
     */
    private Set<TableInfo> tableInfo;

    public StatsPacket(long start, long end, String resource, String shard) {
        this.start = start;
        this.end = end;
        latencies = new HashMap<Integer, LatencyInfo>();
        exceptionStats = new HashMap<String, Integer>();
        this.resource = resource;
        this.shard = shard;
    }

    public void add(LatencyInfo m) {
        latencies.put(m.getPerfStatId(), m);
    }

    public void add(EnvStats stats) {
        this.envStats = stats;
    }

    public void add(RepEnvStats stats) {
        this.repEnvStats = stats;
    }

    public void add(ConciseStats stats) {
        otherStats.add(stats);
    }

    public void add(String e, int count) {
        exceptionStats.put(e, count);
    }

    public void set(Set<TableInfo> infoMap) {
        tableInfo = infoMap;
    }

    public Map<String, Integer> getExceptionStats() {
        return exceptionStats;
    }

    public String getResource() {
        return resource;
    }
    public String getShard() {
        return shard;
    }

    public int getActiveRequests() {
        return activeRequests;
    }

    public void setActiveRequests(int activeRequests) {
        this.activeRequests = activeRequests;
    }

    public long getTotalRequests() {
        return totalRequestsLong;
    }

    public void setTotalRequests(long totalRequests) {
        totalRequestsLong = totalRequests;
    }

    public int getMaxQueuedAsyncRequests() {
        return maxQueuedAsyncRequests;
    }

    public void setMaxQueuedAsyncRequests(int maxQueuedAsyncRequests) {
        this.maxQueuedAsyncRequests = maxQueuedAsyncRequests;
    }

    public long getAsyncRequestQueueTimeAverageNanos() {
        return asyncRequestQueueTimeAvgNanos;
    }

    public void setAsyncRequestQueueTimeAverageNanos(
        long asyncRequestQueueTimeAvgNanos)
    {
        this.asyncRequestQueueTimeAvgNanos = asyncRequestQueueTimeAvgNanos;
    }

    public long getAsyncRequestQueueTime95thNanos() {
        return asyncRequestQueueTime95thNanos;
    }

    public void setAsyncRequestQueueTime95thNanos(
        long asyncRequestQueueTime95thNanos)
    {
        this.asyncRequestQueueTime95thNanos = asyncRequestQueueTime95thNanos;
    }

    public long getAsyncRequestQueueTime99thNanos() {
        return asyncRequestQueueTime99thNanos;
    }

    public void setAsyncRequestQueueTime99thNanos(
        long asyncRequestQueueTime99thNanos)
    {
        this.asyncRequestQueueTime99thNanos = asyncRequestQueueTime99thNanos;
    }

    public int getAverageQueuedAsyncRequests() {
        return avgQueuedAsyncRequests;
    }

    public void setAverageQueuedAsyncRequests(int avgQueuedAsyncRequests) {
        this.avgQueuedAsyncRequests = avgQueuedAsyncRequests;
    }

    public LatencyInfo get(PerfStatType perfType) {
        return latencies.get(perfType.getId());
    }

    /**
     * Gets the map of table information. If no information is available null
     * is returned.
     *
     * @return the map of table information or null
     */
    public Set<TableInfo> getTableInfo() {
        return tableInfo;
    }

    @Override
    public long getStart() {
        return start;
    }

    @Override
    public long getEnd() {
        return end;
    }

    @Override
    public int getId() {
        return Metrics.RNSTATS.getId();
    }

    /**
     * Includes information from this packet that is not part of other stats.
     */
    @Override
    public String getFormattedStats() {
        final StringBuilder sb = new StringBuilder();

        sb.append("Requests");
        sb.append("\n\tactiveRequests=").append(activeRequests);
        sb.append("\n\ttotalRequests=").append(totalRequestsLong);
        sb.append("\n\tavgAsyncQueuedRequests=")
            .append(avgQueuedAsyncRequests);
        sb.append("\n\tmaxAsyncQueuedRequests=")
            .append(maxQueuedAsyncRequests);
        sb.append("\n\tasyncRequestQueueTimeAvgMicros=")
            .append(NANOSECONDS.toMicros(asyncRequestQueueTimeAvgNanos));
        sb.append("\n\tasyncRequestQueueTime95thMicros=")
            .append(NANOSECONDS.toMicros(asyncRequestQueueTime95thNanos));
        sb.append("\n\tasyncRequestQueueTime99thMicros=")
            .append(NANOSECONDS.toMicros(asyncRequestQueueTime99thNanos));

        if (!exceptionStats.isEmpty()) {
            boolean printedHeader = false;
            for (Map.Entry<String, Integer> entry : exceptionStats.entrySet()) {
                if (entry.getValue() > 0) {
                    if (!printedHeader) {
                        sb.append("\nExceptions");
                        printedHeader = true;
                    }
                    sb.append("\n\t").append(entry.getKey());
                    sb.append("=").append(entry.getValue());
                }
            }
        }
        return sb.toString();
    }

    public JsonObject toPacketJson() {
        final JsonObject result = new JsonObject();
        result.addProperty("activeRequests", activeRequests);
        result.addProperty("totalRequests", totalRequestsLong);
        result.addProperty("avgQueuedAsyncRequests", avgQueuedAsyncRequests);
        result.addProperty("maxQueuedAsyncRequests", maxQueuedAsyncRequests);
        result.addProperty("asyncRequestQueueTimeAvgMicros",
                           NANOSECONDS.toMicros(
                               asyncRequestQueueTimeAvgNanos));
        result.addProperty("asyncRequestQueueTime95thMicros",
                           NANOSECONDS.toMicros(
                               asyncRequestQueueTime95thNanos));
        result.addProperty("asyncRequestQueueTime99thMicros",
                           NANOSECONDS.toMicros(
                               asyncRequestQueueTime99thNanos));
        final JsonObject exceptions = exceptionStats.entrySet().stream().
            map(e ->
                new AbstractMap.SimpleImmutableEntry<String, JsonElement>(
                    e.getKey(),
                    new JsonPrimitive(e.getValue()))).
            collect(JsonUtils.getObjectCollector());
        result.add("exceptions", exceptions);
        return result;
    }

    /**
     * WriteCSVHeader would ideally be a static, but we use the presence of an
     * envStat and repEnvStat to determine whether env stat dumping is enabled.
     */
    public void writeCSVHeader(PrintStream out,
                               PerfStatType[] headerList,
                               Map<String, Long> sortedEnvStats) {
        out.print("Date,");

        for (PerfStatType perfType : headerList) {
            out.print(LatencyInfo.getCSVHeader(perfType.toString()) + ",");
        }

        for (String name : sortedEnvStats.keySet()) {
            out.print(name + ",");
        }
    }

    /** StatsPackets know how to record themselves into a .csv file. */
    public void writeStats(PrintStream out,
                           PerfStatType[] statList,
                           Map<String, Long> sortedEnvStats) {

        out.print(getFormattedDate() + ",");

        for (PerfStatType statType : statList) {
            LatencyInfo lm = latencies.get(statType.getId());
            if (lm == null) {
                out.print(LatencyInfo.ZEROS);
            } else {
                out.print(lm.getCSVStats());
            }
            out.print(",");
        }

        for (Long value : sortedEnvStats.values()) {
            out.print(value + ",");
        }
        out.println("");
    }

    private String getFormattedDate() {
        return FormatUtils.formatDateAndTime(end);
    }

    /**
     * Sort env and rep env stats by name, for use in the .csv file.  Could be
     * made more efficient, so the sorting need not be done each collection
     * time, if the .csv file generation feature becomes more commonly used.
     */
    public Map<String, Long> sortEnvStats() {

        final Collection<StatGroup> groups = new ArrayList<StatGroup>();

        if (repEnvStats != null) {
            groups.addAll(repEnvStats.getStats().getStatGroups());
        }

        if (envStats != null) {
            groups.addAll(envStats.getStats().getStatGroups());
        }

        Map<String, Long> sortedVals = new TreeMap<String, Long>();
        for (StatGroup sg : groups) {
            for (Map.Entry<StatDefinition, Stat<?>> e :
                     sg.getStats().entrySet()) {

                String name = ('"' + sg.getName() + "\n" +
                               e.getKey().getName() + '"').intern();
                Object val = e.getValue().get();
                if (val instanceof Number) {
                    sortedVals.put(name,((Number) val).longValue());
                }
            }
        }
        return sortedVals;
    }

    /**
     * Rollup the stats contained within this packet by the summary list
     * provided as an argument.
     */
    public Map<PerfStatType, LatencyInfo>
        summarizeLatencies(PerfStatType[] summaryList) {

        /* If we already have the summary stats, just add them */
        final Map<PerfStatType, LatencyInfo> rollupValues =
            Arrays.stream(summaryList).
            filter((t) -> latencies.get(t.getId()) != null).
            collect(Collectors.toMap(
                Function.identity(), (t) -> latencies.get(t.getId())));
        if (rollupValues.size() == summaryList.length) {
            return rollupValues;
        }

        /* Do rollup if the summary is not there */

        final Map<PerfStatType, LatencyInfoSummarizer> summerizers =
            new HashMap<>();
        /*
         * If this latency is a child of one of the summary stats, add the
         * child into the summary.
         */
        List<PerfStatType> summaryColumn = Arrays.asList(summaryList);
        latencies.values().forEach(
            (m) -> {
                PerfStatType parent =
                    PerfStatType.getType(m.getPerfStatId()).getParent();
                while (parent != null) {
                    final PerfStatType p = parent;
                    if (summaryColumn.contains(parent)) {
                        final LatencyInfoSummarizer summerizer =
                            summerizers.computeIfAbsent(
                                p,
                                (k) ->
                                new LatencyInfoSummarizer(
                                    p, getStart(), getEnd()));
                        summerizer.rollup(m);
                    }
                    parent = parent.getParent();
                }
            });
        return summerizers.entrySet().stream().collect(
            Collectors.toMap(e -> e.getKey(), e -> e.getValue().build()));
    }

    /**
     * Rollup the stats contained within this packet by the summary list
     * provided as an argument. The rolledup values are written to the out
     * stream, but are also returned so that unit tests can check the values.
     */
    public Map<PerfStatType, LatencyInfo>
        summarizeAndWriteStats(PrintStream out,
                               PerfStatType[] summaryList,
                               Map<String, Long> sortedEnvStats) {

        out.print(getFormattedDate() + ",");

        /* Setup a map to hold summary rollups for each summary stat */
        Map<PerfStatType, LatencyInfo> rollupValues =
            summarizeLatencies(summaryList);

        /* Dump the stats. */
        for (PerfStatType root : summaryList) {
            LatencyInfo m = rollupValues.get(root);
            if (m == null) {
                m = LatencyInfo.ZERO_MEASUREMENT;
            }
            out.print(m.getCSVStats() + ",");
        }

        for (Long value : sortedEnvStats.values()) {
            out.print(value + ",");
        }

        out.println("");
        return rollupValues;
    }

    public EnvStats getEnvStats() {
        return envStats;
    }

    public RepEnvStats getRepEnvStats() {
        return repEnvStats;
    }

    /**
     * Returns the list of other stats or {@code null}. The returned value may
     * be {@code null} if no stats were added, or due to receiving a
     * {@code StatPacket} from an older version node.
     *
     * @return the list of other stats or null
     */
    public List<ConciseStats> getOtherStats() {
        return otherStats;
    }

    /* For unit test support */
    public List<LatencyResult> getLatencies() {
        return latencies.values().stream().map((info) -> info.getLatency()).
            collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (LatencyInfo m: latencies.values()) {
            sb.append(m).append("\n");
        }
        return sb.toString();
    }

    public String toOpJsonString() {
        return toOpJson().toString();
    }

    public JsonObject toOpJson() {
        final JsonObject result = createJsonHeader();
        result.addProperty("Exception_Total_Count",
                           getExceptionStats().values().stream()
                           .mapToInt(Integer::intValue).sum());
        Arrays.stream(PerfStatType.values()).
            filter((t) -> get(t) != null).
            map((t) -> get(t)).
            forEach((info) -> addLatencyInfo( result, info));
        result.addProperty("Active_Requests", getActiveRequests());
        result.addProperty("Total_Requests", getTotalRequests());
        result.addProperty("Max_Queued_Async_Requests",
                           getMaxQueuedAsyncRequests());
        result.addProperty("Avg_Queued_Async_Requests",
                           getAverageQueuedAsyncRequests());
        result.addProperty("Async_Request_Queue_Time_Avg_Micros",
                           NANOSECONDS.toMicros(
                               getAsyncRequestQueueTimeAverageNanos()));
        result.addProperty("Async_Request_Queue_Time_95th_Micros",
                           NANOSECONDS.toMicros(
                               getAsyncRequestQueueTime95thNanos()));
        result.addProperty("Async_Request_Queue_Time_99th_Micros",
                           NANOSECONDS.toMicros(
                               getAsyncRequestQueueTime99thNanos()));

        return result;
    }

    /*
     * TODO: This is a bit bloated. A better way perhaps is to add an info
     * object by using info.toJson() instead of prefixing all the keys.
     */
    private void addLatencyInfo(JsonObject object,
                                LatencyInfo info) {
        final LatencyResult result = info.getLatency();
        final String type = info.getPerfStatType().toString();
        object.addProperty(type + "_TotalOps", result.getOperationCount());
        object.addProperty(type + "_TotalReq", result.getRequestCount());
        object.addProperty(type + "_PerSec", info.getThroughputPerSec());
        object.addProperty(
            type + "_Min",
            NANOSECONDS.toMillis(result.getMin()));
        object.addProperty(
            type + "_Max",
            NANOSECONDS.toMillis(result.getMax()));
        object.addProperty(
            type + "_Avg",
            NANOSECONDS.toMillis(result.getAverage()));
        object.addProperty(
            type + "_95th",
            NANOSECONDS.toMillis(result.getPercent95()));
        object.addProperty(
            type + "_99th",
            NANOSECONDS.toMillis(result.getPercent99()));
    }

    public String toExceptionsJsonString() {
        if (getExceptionStats().values().stream().
            mapToInt(Integer::intValue).sum() <= 0) {
            return "";
        }
        return toExceptionsJson().toString();
    }

    public JsonObject toExceptionsJson() {
        final JsonObject result = createJsonHeader();
        result.add(
            "Exceptions",
            getExceptionStats().entrySet().stream()
            .map((e) -> {
                final JsonObject exception = new JsonObject();
                exception.addProperty("Exception_Name", e.getKey());
                exception.addProperty("Exception_Count", e.getValue());
                return exception;
            }).collect(JsonUtils.getArrayCollector()));
        return result;
    }

    public String toEnvJsonString() {
        final JsonObject result = toEnvJson();
        if (result.size() == 0) {
            return "";
        }
        return result.toString();
    }

    /*
     * TODO: perhaps put the serialization code in EnvStats and RepEnvStats
     * code separately?
     */
    public JsonObject toEnvJson() {
        final JsonObject result = createJsonHeader();
        if (repEnvStats != null) {
            repEnvStats.getStats().getStatGroups().stream().
                filter((sg) -> sg != null).
                forEach((sg) -> addStatGroup(result, sg));
        }
        if (envStats != null) {
            envStats.getStats().getStatGroups().stream().
                filter((sg) -> sg != null).
                forEach((sg) -> addStatGroup(result, sg));
        }
        return result;
    }


    private void addStatGroup(JsonObject result, StatGroup sg) {
        final String groupName = sg.getName();
        sg.getStats().entrySet().forEach(
            (e) -> {
                final String name = String.format(
                    "%s_%s", groupName, e.getKey().getName());
                final Stat<?> stat = e.getValue();
                final Object value = stat.get();
                if (stat instanceof MapStat) {
                    result.add(name,
                               JsonUtils.getGson().
                               toJsonTree(((MapStat<?, ?>)stat).getMap()));
                } else if (value instanceof Boolean) {
                    result.addProperty(name, (Boolean) value);
                } else if (value instanceof Number) {
                    result.addProperty(name, (Number) value);
                } else if (value instanceof String) {
                    result.addProperty(name, (String) value);
                } else {
                    result.add(
                        name, JsonUtils.getGson().toJsonTree(stat));
                }
            });
    }

    public String toTableJsonString() {
        if ((tableInfo == null) || tableInfo.isEmpty()) {
            return "";
        }
        return toTableJson().toString();
    }

    public JsonObject toTableJson() {
        final JsonObject result = createJsonHeader();
        result.add("Tables",
                   tableInfo.stream().
                   map((t) -> t.toJson()).
                   collect(JsonUtils.getArrayCollector()));
        return result;
    }

    public String toJVMStatsJsonString() {
        return otherStats.stream().
            filter((s) -> s instanceof JVMStats).
            map(s -> toJVMStatsJson((JVMStats) s).toString()).
            findAny().orElse("");
    }

    public JsonObject toJVMStatsJson(JVMStats jvmStats) {
        final JsonObject result = jvmStats.toJson();
        addJsonHeader(result);
        return result;
    }

    public String toEndpointGroupStatsJsonString() {
        return otherStats.stream().
            filter((s) -> s instanceof EndpointGroupStats).
            map(s -> ((EndpointGroupStats) s).toJson().toString()).
            findAny().orElse("");
    }

    private JsonObject createJsonHeader() {
        final JsonObject result = new JsonObject();
        addJsonHeader(result);
        return result;
    }

    private void addJsonHeader(JsonObject result) {
        result.addProperty("resource", getResource());
        result.addProperty("shard", getShard());
        result.addProperty("reportTime", getEnd());
        result.addProperty("reportTimeHuman", formatDateTimeMillis(getEnd()));
    }

    /**
     * Converts a time in milliseconds to a standard format human-readable
     * string that includes milliseconds.
     */
    private static String formatDateTimeMillis(long timeMillis) {
        return dateTimeMillisFormatter.get().format(timeMillis);
    }

    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {

        in.defaultReadObject();

        /*
         * Set the totalRequestsLong field if reading from an older version
         * that only has the totalRequests field.
         */
        if (totalRequestsLong == 0) {
            totalRequestsLong = totalRequests;
        }
    }

    private void writeObject(ObjectOutputStream out)
        throws IOException {

        /*
         * Set the totalRequests field to support reading by older versions
         * that don't have the totalRequestsLong field.
         */
        totalRequests = longToIntOrLimit(totalRequestsLong);

        out.defaultWriteObject();
    }
}
