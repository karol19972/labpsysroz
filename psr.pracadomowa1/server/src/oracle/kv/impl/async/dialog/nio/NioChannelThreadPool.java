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

package oracle.kv.impl.async.dialog.nio;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.fault.AsyncEndpointGroupFaultHandler;
import oracle.kv.impl.util.KVThreadFactory;

public class NioChannelThreadPool {

    private final static AtomicInteger poolSequencer = new AtomicInteger(0);

    private final Logger logger;
    private final int id;
    private final AtomicInteger childSequencer = new AtomicInteger(0);
    private final AtomicReferenceArray<NioChannelExecutor> executors;
    private final int maxQuiescentSeconds;
    private final AtomicInteger index = new AtomicInteger(0);
    private final KVThreadFactory threadFactory;
    private final AsyncEndpointGroupFaultHandler faultHandler;
    private volatile boolean isShutdown = false;

    /**
     * Construct the thread pool.
     *
     * @param num the maximum number of executors.
     * @param maxQuiescentSeconds the maximum time in seconds an executor is in
     * quiescence before it is shut down
     * @param faultHandler the executor fault handler
     */
    public NioChannelThreadPool(Logger logger,
                                int num,
                                int maxQuiescentSeconds,
                                AsyncEndpointGroupFaultHandler faultHandler) {

        if (num <= 0) {
            throw new IllegalArgumentException(String.format(
                "Number of executors should be positive, got %d", num));
        }
        this.logger = logger;
        this.id = poolSequencer.getAndIncrement();
        this.executors = new AtomicReferenceArray<NioChannelExecutor>(num);
        this.maxQuiescentSeconds = maxQuiescentSeconds;
        this.threadFactory = new KVThreadFactory(
            NioChannelThreadPool.class.getName(), logger);
        this.faultHandler = faultHandler;
        /*
         * TODO: should we have core threads that we start at the beginning and
         * keep active? If we do care about efficiency of using threads and
         * implement this core thread idea, there is an issue with the current
         * executor assignment when creating new channel. Currently, the next()
         * choose an executor in a round-robin fashion and therefore threads
         * are more frequently destroyed and restarted.
         */
    }

    /**
     * Returns the id.
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns an executor of the group.
     *
     * @return the executor, not {@code null}
     * @throws IllegalStateException if the thread pool is shutting down
     */
    public NioChannelExecutor next() {
        final int nextId = Math.abs(
            index.getAndIncrement() % executors.length());
        while (true) {
            final NioChannelExecutor executor = createExecutor(nextId);
            if (isShutdown) {
                if (executor != null) {
                    /*
                     * If the thread pool is shut down, just shut down the
                     * executor gracefully. Note that we should not shut it
                     * down forcefully since there might be ongoing clean up
                     * tasks.
                     */
                    executor.shutdown();
                }
                throw new IllegalStateException(
                    "thread pool is shutting down");
            }
            if ((executor == null) || executor.isShuttingDownOrAfter()) {
                /* It so happens the executor is shut down, try again */
                continue;
            }
            return executor;
        }
    }

    /**
     * Returns a executor, creates one if necessary, from the executor array
     * with the specified index.
     *
     * <p>The method may return {@code null} or return a already-shutdown
     * executor when the executor previously occupying the specified position
     * in the array is shut down concurrently. The next() method will retry in
     * that case.
     */
    private NioChannelExecutor createExecutor(int childIndex) {

        final NioChannelExecutor existing = executors.get(childIndex);
        if (existing != null) {
            return existing;
        }

        try {
            final NioChannelExecutor curr = new NioChannelExecutor(
                logger, NioChannelThreadPool.this,
                childSequencer.getAndIncrement(), childIndex,
                maxQuiescentSeconds,
                faultHandler);
            final Thread thread = threadFactory.newThread(curr);
            if (executors.compareAndSet(childIndex, null, curr)) {
                thread.start();
                logger.log(Level.FINEST, () -> String.format(
                    "New executor started: %s", curr));
                if (isShutdown) {
                    /*
                     * Force shut down if the pool is shut down. No need for a
                     * graceful shut down since the executor is newly created.
                     */
                    curr.shutdownForcefully();
                    /*
                     * Not necessary to return null, but do that so that the
                     * caller does not need to shut it down again.
                     */
                    return null;
                }
                return curr;
            }
            /* Closes the executor if concurrently created */
            curr.shutdownForcefully();
            /* Returns one from the array which could be null, but is OK */
            return executors.get(childIndex);
        } catch (IOException e) {
            /*
             * The IOException is caused by failing to open a selector. This is
             * quite unexpected. There is nothing we can do here except
             * reporting it. Report it as a IllegalStateException since the
             * upper layer would not know what to do either.
             */
            throw new IllegalStateException(
                String.format("Unexpected IOException: %s", e), e);

        }
    }

    /**
     * Shutdown the group.
     *
     * @param force true if not wait for queued tasks in the executors
     */
    public void shutdown(boolean force) {
        isShutdown = true;
        for (int i = 0; i < executors.length(); ++i) {
            final NioChannelExecutor executor = executors.get(i);
            if (executor != null) {
                if (force) {
                    executor.shutdownForcefully();
                } else {
                    executor.shutdown();
                }
            }
        }
    }

    /**
     * Returns {@code true} if the thread pool is shut down.
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    /**
     * Called when a child executor is shut down.
     */
    public void onExecutorShutdown(NioChannelExecutor executor,
                                   int childIndex) {
        executors.compareAndSet(childIndex, executor, null);
    }
}
