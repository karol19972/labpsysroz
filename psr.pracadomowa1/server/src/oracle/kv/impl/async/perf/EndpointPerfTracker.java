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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import oracle.kv.impl.async.dialog.DialogContextImpl;
import oracle.kv.impl.async.perf.MetricStatsImpl;
import oracle.kv.impl.util.ObjectUtil;
import oracle.kv.impl.util.WatcherNames;
import oracle.nosql.common.sklogger.measure.LatencyElement;
import oracle.nosql.common.sklogger.measure.LongCappedPercentileElement;
import oracle.nosql.common.sklogger.measure.ThroughputElement;

/**
 * Tracks the dialog event metrics for an endpoint across mutliple connections.
 *
 * <p>The perf tracker is expected to be polled from time to time by calling
 * the {@link #obtain} method.
 *
 * <p>Note that, on the creator side, one endpoint is created for each remote
 * address (host/port) and hence one perf tracker is assigned for each remote
 * address (host/port). On the responder side, endpoints spawn from the same
 * listening port are assigned to the same perf tracker. This is because we
 * cannot distinguish different interfaces in the dialog layer when a new
 * connection is established. In a word, there are as many perf tracker as the
 * number of listening ports. Typically, we will have one perf tracker per
 * JVM.
 *
 * <p>This means that all the metrics collected here is shared among all the
 * endpoints on the responder side. The rationale is that at the responder
 * side, all the dialogs are executed within the same environment. Plus there
 * is only one important dialogs that have a performance impact: the
 * AsyncRequestHandler interface. Therefore, this overview stats should
 * suffice. Finer-grain stats may be built at different layers.
 *
 * <h1>Dialog Events Metrics</h1>
 *
 * <p>It is useful to present a dialog event breakdown for diaganosis
 * especially for performance issues. The {@link DialogEventPerf} object
 * collect event time and information for dialog events such as init,
 * read/write, send/receive and finish/abort. These dialog event breakdown are
 * reported here in two approaches: (1) a stats on the latency between two
 * consecutive events, (2) a series of records for dialog events about specific
 * dialogs. The first approach provides a statistical overview; the second a
 * more intuitive picture for specific dialogs.
 *
 * <h2>Event Records</h2>
 *
 * <p>The dialog event records are queued up waiting to be polled. To prevent
 * from memory explosion, the number of queued event records, between two
 * consecutive polling, is designed to be fixed as {@code
 * maxNumPollingEventRecords} and extra records are dropped.
 *
 * <p>The queueing mechanism resulted in the limitation that dialog event
 * records will always be cleared no matter the {@code clear} flag in the
 * {@code getMetrics} call.
 *
 * <p>To mitigate the performance impact, we should not generate more records
 * than necessary. It also makes more sense to generate records that are
 * separated in time (e.g., versus crowded around the boundary of polling
 * time). For the above two reasons, the dialogs are also sampled for log
 * records which is controlled by a {@code eventRecordSampleRate}. The {@code
 * eventRecordSampleRate} is adjusted according to the {@code
 * maxNumPollingEventRecords} and the throughput of the last polling interval
 * (monitor log interval which is a constant).
 *
 * <p>The dialogs on the creator endpoints are sampled exactly with the {@code
 * eventRecordSampleRate}. We would like to correlate the event records of the
 * same dialog on both the creator and responder sides and therefore a flag
 * indicating the dialog is sampled for event record is sent over the wire.
 *
 * <p>The sample rate for dialog event records on the responder endpoints is
 * applied on the sampled dialogs from the creator endpoints, i.e.,  one dialog
 * is sampled for event record every {@code eventRecordSampleRate}
 * creator-sampled. With this mechanism we can ensure most of the event records
 * logged at the responder endpoint will apppear on the creator endpoint log.
 *
 */
public class EndpointPerfTracker {

    /**
     * A hidden system property to specify the {@code eventLatencySampleRate}.
     * We do not expect the value to change. The value adjust the performance
     * impact for collecting event latency stats. The default value should make
     * it sufficient that the impact is negeligible. The actual value used is
     * always the maximum pow of 2 value that is less than or equal to the
     * specified.
     */
    public static final String EVENT_LATENCY_SAMPLE_RATE =
        "oracle.kv.async.perf.endpoint.event.latency.sample.rate";
    private static final int EVENT_LATENCY_SAMPLE_RATE_DEFAULT = 1024;

    /**
     * A hidden system property to specify the {@code
     * maxNumPollingEventRecords}. We do not expect the value to change. We
     * expect people would just accept the values we pick as. Showing more less
     * event records only marginally adds more information. And our values are
     * already small and format pretty enough so that people should not be
     * annoyed by the records.
     */
    private static final String MAX_NUM_POLLING_EVENT_RECORDS =
        "oracle.kv.async.perf.max.num.polling.event.records";

    /**
     * The default creator-side maxNumPollingEventRecords. To give an example,
     * with a default value of 4, a default monitor logging interval of 1
     * minute and a kvstore of 18 shards with rf=3. We will generate 18(shards)
     * * 3(rf) * 4(log records) = 216 log records per minute.
     */
    private static final int
        DEFAULT_CREATOR_MAX_NUM_POLLING_EVENT_RECORDS = 4;
    /**
     * The default responder-side maxNumPollingEventRecords. To give an
     * example, with a default value of 16, a default stats file collect
     * interval of 1 minute, we will generate 16 log records per minute. The
     * default of RN_ACCEPT_MAX_ACTIVE_CONNS_DEFAULT is 8192, which means
     * averagely speaking, every 8192(connections) / 16(log records) = 512
     * sampled dialogs from all the connections, one will be picked.
     */
    private static final int
        DEFAULT_RESPONDER_MAX_NUM_POLLING_EVENT_RECORDS = 16;

    private final String name;
    private final boolean isCreator;

    /* General metrics */

    /* Throughput metrics */
    private final ThroughputElement dialogStartThroughput;
    private final ThroughputElement dialogDropThroughput;
    private final ThroughputElement dialogFinishThroughput;
    private final ThroughputElement dialogAbortThroughput;

    /* Latency metrics */
    private final LatencyElement finishedDialogLatency;
    private final LatencyElement abortedDialogLatency;

    /* Dialog concurrency metrics */
    private final AtomicInteger activeDialogCounter = new AtomicInteger(0);
    private final LongCappedPercentileElement dialogConcurrency;

    /* Event latency metrics */

    /* The event latency sample rate specified by a system property */
    private final int eventLatencySampleRate =
        Integer.getInteger(EVENT_LATENCY_SAMPLE_RATE,
                           EVENT_LATENCY_SAMPLE_RATE_DEFAULT);
    /*
     * The adjusted event latency sample rate which is the largest power of 2
     * less or equal to eventLatencySampleRate, equivalently, the value with
     * the highest single one-bit of eventLatencySampleRate.
     */
    private final long eventLatencySampleRateHighestOneBit =
        getSampleRateHighestOneBit(eventLatencySampleRate);
    /* A map of events to latency metrics */
    private final Map<DialogEventPerf.EventSpan, LatencyElement>
        eventLatencyNanosMap = new ConcurrentHashMap<>();

    /* Event record metrics */
    /*
     * The adjusted event record sample rate which is the largest power of 2
     * less or equal to a computed sample rate. The sample rate is computed
     * according to the throughput of last period. Initialize using 1024.
     */
    private volatile long eventRecordSampleRateHighestOneBit =
        getSampleRateHighestOneBit(1024);
    /*
     * Indicates the sampled list is full to stop sampling. Volatile for read,
     * modify inside the synchronziation block of the sampled list.
     */
    private volatile boolean isEventRecordQueueFull = false;
    /* A counter on started dialogs for event latency sampling */
    private final AtomicLong eventLatencySampleCounter = new AtomicLong(0);
    /* A counter for dialogs sampled by the endpoints for event record */
    private final AtomicLong creatorEventRecordCounter = new AtomicLong(0);
    /* The maximum number of event records we hold between two polling */
    private final int maxNumPollingEventRecords;
    /* Sampled dialog perf.  Access must synchronized on the list. */
    private final Queue<DialogEventPerf> eventRecordQueue = new ArrayDeque<>();
    
    enum SampleDecision {
        NONE(false, false),
        SAMPLE(true, false),
        SAMPLE_FOR_RECORD(true, true);
        final boolean shouldSample;
        final boolean sampleForRecord;
        SampleDecision(boolean shouldSample, boolean sampleForRecord) {
            this.shouldSample = shouldSample;
            this.sampleForRecord = sampleForRecord;
        }
        static SampleDecision get(boolean shouldSample,
                                  boolean sampleForRecord) {
            if (sampleForRecord) {
                return SAMPLE_FOR_RECORD;
            }
            if (shouldSample) {
                return SAMPLE;
            }
            return NONE;
        }
    }

    /**
     * Constructs a dialog endpoint perf tracker.
     */
    public EndpointPerfTracker(String name,
                               boolean isCreator,
                               int maxDialogConcurrency) {
        ObjectUtil.checkNull("name", name);
        this.name = name;
        this.isCreator = isCreator;

        this.dialogStartThroughput = new ThroughputElement();
        this.dialogDropThroughput = new ThroughputElement();
        this.dialogFinishThroughput = new ThroughputElement();
        this.dialogAbortThroughput = new ThroughputElement();

        this.finishedDialogLatency = new LatencyElement();
        this.abortedDialogLatency = new LatencyElement();

        this.dialogConcurrency =
            new LongCappedPercentileElement(maxDialogConcurrency);

        this.maxNumPollingEventRecords = Integer.getInteger(
            MAX_NUM_POLLING_EVENT_RECORDS,
            isCreator ?
            DEFAULT_CREATOR_MAX_NUM_POLLING_EVENT_RECORDS :
            DEFAULT_RESPONDER_MAX_NUM_POLLING_EVENT_RECORDS);
    }

    /**
     * Called when a dialog is started on a creator endpoint.
     */
    public void onDialogStarted(DialogContextImpl context) {
        countDialogStartAndSample(context, shouldSampleForCreator());
    }

    private SampleDecision shouldSampleForCreator() {
        final long n = eventLatencySampleCounter.incrementAndGet();
        final boolean sampleForLatency = shouldSampleForEventLatency(n);
        final boolean sampleForRecord = shouldSampleForEventRecord(n);
        return SampleDecision.get(sampleForLatency, sampleForRecord);
    }

    private void countDialogStartAndSample(DialogContextImpl context,
                                           SampleDecision sampleDecision) {
        dialogConcurrency.observe(activeDialogCounter.incrementAndGet());
        dialogStartThroughput.observe(1);
        if (sampleDecision.shouldSample) {
            context.sample(sampleDecision.sampleForRecord);
        }
    }

    private boolean shouldSampleForEventLatency(long count) {
        return ((count & (eventLatencySampleRateHighestOneBit - 1)) == 0);
    }

    private boolean shouldSampleForEventRecord(long count) {
        return (((count & (eventRecordSampleRateHighestOneBit - 1)) == 0) &&
                (!isEventRecordQueueFull));
    }

    /**
     * Called when a dialog is started on a responder endpoint.
     */
    public void onDialogStarted(DialogContextImpl context,
                                boolean sampledOnCreatorEndpoint) {
        countDialogStartAndSample(
            context, shouldSampleForResponder(sampledOnCreatorEndpoint));
    }

    private SampleDecision shouldSampleForResponder(
        boolean sampledOnCreatorEndpoint) {

        final long n = eventLatencySampleCounter.incrementAndGet();
        final boolean sampleForLatency = shouldSampleForEventLatency(n);
        final boolean sampleForRecord =
            sampledOnCreatorEndpoint &&
            shouldSampleForEventRecord(
                creatorEventRecordCounter.incrementAndGet());
        return SampleDecision.get(sampleForLatency, sampleForRecord);
    }

    /**
     * Called when a dialog is dropped.
     */
    public void onDialogDropped() {
        dialogDropThroughput.observe(1);
    }

    /**
     * Called when a dialog is finished.
     *
     * @param context the dialog context
     */
    public void onDialogFinished(DialogContextImpl context) {
        dialogFinishThroughput.observe(1);
        activeDialogCounter.decrementAndGet();
        finishedDialogLatency.observe(context.getLatencyNanos());
        addEventPerf(context);
    }

    /**
     * Adds the event perf if present.
     */
    private void addEventPerf(DialogContextImpl context) {
        context.getPerf().ifPresent((p) -> {
            addEventLatency(p);
            if (p.isSampledForRecord()) {
                addEventRecord(p);
            }
        });
    }

    /**
     * Adds to the event latency map.
     */
    private void addEventLatency(DialogEventPerf perf) {
        final Map<DialogEventPerf.EventSpan, Long> map =
            perf.getEventLatencyNanosMap();
        for (Map.Entry<DialogEventPerf.EventSpan, Long> entry :
             map.entrySet()) {

            final DialogEventPerf.EventSpan key = entry.getKey();
            final long val = entry.getValue();
            final LatencyElement element =
                eventLatencyNanosMap.computeIfAbsent(
                    key,  (k) -> new LatencyElement());
            element.observe(val);
        }
    }

    /**
     * Adds a sampled dialog perf.
     */
    private void addEventRecord(DialogEventPerf perf) {
        synchronized(eventRecordQueue) {
            if (isEventRecordQueueFull) {
                return;
            }
            if (eventRecordQueue.size() < maxNumPollingEventRecords) {
                eventRecordQueue.add(perf);
            } else {
                isEventRecordQueueFull = true;
            }
        }
    }

    /**
     * Called when a dialog is aborted.
     *
     * @param context the dialog context
     */
    public void onDialogAborted(DialogContextImpl context) {
        dialogAbortThroughput.observe(1);
        activeDialogCounter.decrementAndGet();
        abortedDialogLatency.observe(context.getLatencyNanos());
        addEventPerf(context);
    }

    /**
     * Returns the endpoint metrics since last time.
     */
    public EndpointMetricsImpl obtain(String watcherName, boolean clear) {
        final double startThroughput =
            dialogStartThroughput.obtain(watcherName, clear).
            getThroughputPerSecond();
        final EndpointMetricsImpl metricsImpl = new EndpointMetricsImpl(
            name, isCreator,
            startThroughput,
            dialogDropThroughput.obtain(watcherName, clear)
            .getThroughputPerSecond(),
            dialogFinishThroughput.obtain(watcherName, clear)
            .getThroughputPerSecond(),
            dialogAbortThroughput.obtain(watcherName, clear)
            .getThroughputPerSecond(),
            new MetricStatsImpl(
                finishedDialogLatency.obtain(watcherName, clear)),
            new MetricStatsImpl(
                abortedDialogLatency.obtain(watcherName, clear)),
            new MetricStatsImpl(
                dialogConcurrency.obtain(watcherName, clear)),
            eventLatencyNanosMap.entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey(),
                    e -> new MetricStatsImpl(
                        e.getValue().obtain(watcherName, clear)))),
            eventRecordSampleRateHighestOneBit,
            getEventRecords(watcherName));
        computeEventRecordSampleRate((long) startThroughput);
        return metricsImpl;
    }

    /**
     * Returns the queue of sampled dialog perf objects and clears the queue.
     */
    private Collection<DialogEventPerf> getEventRecords(String watcherName) {
        /*
         * Event records are only collected for two watchers: the client-side
         * kvstats monitor and the server-side stats collection.
         */
        if (!(watcherName.equals(WatcherNames.KVSTATS_MONITOR) ||
              watcherName.equals(
                  WatcherNames.SERVER_STATS_TRACKER))) {
            return Collections.emptyList();
        }
        synchronized(eventRecordQueue) {
            final Queue<DialogEventPerf> ret =
                new ArrayDeque<>(eventRecordQueue);
            eventRecordQueue.clear();
            isEventRecordQueueFull = false;
            return ret;
        }
    }

    private void computeEventRecordSampleRate(long startThroughput) {
        final long throughput = isCreator ?
            startThroughput : creatorEventRecordCounter.get();
        final long eventRecordSampleRate = Math.max(
            (maxNumPollingEventRecords == 0) ?
            Long.MAX_VALUE : throughput / maxNumPollingEventRecords,
            /*
             * If the last interval has a very small throughput, we end up
             * sampling every dialog for event record, but that is fine since
             * we have a small event record queue.
             */
            1);
        eventRecordSampleRateHighestOneBit =
            getSampleRateHighestOneBit(eventRecordSampleRate);
    }

    /**
     * Returns the the maximum power of 2 less than or equal to the given
     * {@code sampleRate}.
     *
     * <p>A sample rate of less or equal than 0 is treated as Long.MAX_VALUE.
     */
    private long getSampleRateHighestOneBit(long sampleRate) {
        return (sampleRate <= 0) ?
            Long.highestOneBit(Long.MAX_VALUE) :
            Long.highestOneBit(sampleRate);
    }
}
