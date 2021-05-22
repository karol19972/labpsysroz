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

import com.sleepycat.je.rep.stream.ChangeResultHandler;
import com.sleepycat.je.rep.stream.FeederFilterChangeResult;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * Object that represents the callback to process the stream change result
 */
class StreamChangeResultHandler implements ChangeResultHandler {

    /* change result from feeder */
    private FeederFilterChangeResult result;

    /* parent worker thread to initiate the request */
    private final StoppableThread changeThread;

    StreamChangeResultHandler(StoppableThread changeThread){
        this.changeThread = changeThread;
        result = null;
    }

    @Override
    public synchronized void onResult(FeederFilterChangeResult res) {
        if (res == null) {
            throw new IllegalArgumentException("Result cannot be null");
        }
        result = res;
        notifyAll();
    }

    /**
     * Returns the change result or null if not available before timing out.
     *
     * @param timeoutMs the timeout in milliseconds
     *
     * @return the change result or null if timeout
     */
    synchronized FeederFilterChangeResult getResult(long timeoutMs) {
        final long stop = System.currentTimeMillis() + timeoutMs;
        while (!isResultReady()) {

            /* timeout */
            long waitMs = stop - System.currentTimeMillis();
            if (waitMs <= 0) {
                /* timeout */
                return null;
            }

            try {
                wait(500);
            } catch (InterruptedException e) {
                break;
            }

            /* change thread has shutdown or died, return immediately */
            if (changeThread.isShutdown()) {
                return null;
            }

        }
        return result;
    }


    /**
     * Returns the change result or null if not ready
     *
     * @return  the change result, or null
     */
    synchronized FeederFilterChangeResult getResult() {
        return result;
    }

    /**
     * Returns true if the change result from feeder is ready, false otherwise.
     *
     * @return true if the change result from feeder is ready, false otherwise.
     */
    synchronized private boolean isResultReady() {
        return result != null;
    }
}
