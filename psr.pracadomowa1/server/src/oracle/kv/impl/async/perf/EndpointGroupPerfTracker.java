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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oracle.kv.impl.async.perf.DialogResourceManagerPerfTracker;
import oracle.kv.impl.measurement.EndpointGroupStats;


/**
 * Tracks the performance metrics of an endpoint group.
 */
public class EndpointGroupPerfTracker {

    /* Maximum number of concurrent dialogs */
    private final int maxDialogConcurrency;
    /* Resource manager perf tracker */
    private final DialogResourceManagerPerfTracker
        dialogResourceManagerPerfTracker =
        new DialogResourceManagerPerfTracker();
    /*
     * A map of endpoint perf name and the perf tracker. Creator endpoints will
     * use the remote IP address as the name. Responder endpoints will use the
     * local listening port number.
     *
     * Note that the entries in the map is never removed. We do not expect the
     * map to be very large. The number of creator endpoints is as many as the
     * number of RNs which should not exceeds several hundreds. The number of
     * responder endpoints is as many as the number of service which should not
     * exceeds a hundred.
     */
    private final Map<String, EndpointPerfTracker>
        endpointPerfTrackers = new ConcurrentHashMap<>();

    private volatile long currIntervalStart;

    /** Constructs the tracker. */
    public EndpointGroupPerfTracker(int maxDialogConcurrency) {

        this.maxDialogConcurrency = maxDialogConcurrency;
    }

    /** Returns an endpoint perf tracker. */
    public EndpointPerfTracker getEndpointPerfTracker(String endpointPerfName,
                                                      boolean isCreator) {
        return endpointPerfTrackers.computeIfAbsent(
            endpointPerfName,
            (name) ->
            new EndpointPerfTracker(
                name, isCreator, maxDialogConcurrency));
    }

    /** Returns the dialog resource manager perf tracker. */
    public DialogResourceManagerPerfTracker
        getDialogResourceManagerPerfTracker() {

        return dialogResourceManagerPerfTracker;
    }

    public EndpointGroupStats obtain(String watcherName, boolean clear) {
        final long ts = currIntervalStart;
        final long te = System.currentTimeMillis();
        currIntervalStart = te;
        final Map<String, EndpointMetricsImpl> creatorMetrics =
            new HashMap<>();
        final Map<String, EndpointMetricsImpl> responderMetrics =
            new HashMap<>();
        endpointPerfTrackers.values().forEach(
            (tracker) -> {
                final EndpointMetricsImpl metrics =
                    tracker.obtain(watcherName, clear);
                if (metrics.isCreator()) {
                    creatorMetrics.put(metrics.getName(), metrics);
                } else {
                    responderMetrics.put(metrics.getName(), metrics);
                }
        });
        return new EndpointGroupStats(
            ts, te, creatorMetrics, responderMetrics,
            dialogResourceManagerPerfTracker.getMetrics());
    }

    /**
     * Clears the endpoint perf trackers for testing.
     *
     * The endpoint group is shared by the process which sometimes cause
     * problem for testing.
     */
    public void clear() {
        endpointPerfTrackers.clear();
    }
}
