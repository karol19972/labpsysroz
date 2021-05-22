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

package oracle.kv.impl.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A substitute for {@link ThreadPoolExecutor} that both permits the queue size
 * to be modified and that creates more threads up to the max pool size before
 * leaving tasks in the queue.
 *
 * <p>Although ThreadPoolExecutor permits changing the core pool size, maximum
 * pool size, and keep alive time for an existing executor, it does not provide
 * adjust the size of the work queue.
 *
 * <p>ThreadPoolExecutor also leaves tasks in the queue until the queue is full
 * before creating threads beyond the core pool size limit. That behavior
 * probably makes sense for tasks that are CPU bound, since there isn't much
 * point in creating more threads when the existing ones are busy. But, in our
 * case, long running tasks are probably blocked on I/O, so it is better to
 * create additional threads. On the other hand, there is no reason to have a
 * large number of core threads if there is a low level of activity, so
 * increasing the thread count beyond the core size only as needed seems best.
 *
 * <p>To support these improvements, the basic idea is to create an external
 * queue that can hold rejected tasks, and picking up the rejected tasks out of
 * the queue whenever an existing task completes.
 */
public class AdjustableThreadPoolExecutor extends ThreadPoolExecutor {

    /* Synchronize on this instance when accessing these fields */

    /** Queue of requests waiting for a free thread to be executed. */
    private BlockingQueue<Runnable> queue;

    /** The current queue capacity (maximum size). */
    private int queueSize;

    /**
     * If true, shrink the queue to the specified queueSize once the number of
     * items in the queue is small enough to allow it.
     */
    private boolean shrink;

    /**
     * The number of execute operations currently being performed. Use the
     * count to determine if there is a thread that will notice items added to
     * the queue.
     */
    private int executingCount;

    /**
     * The number of times an execute call needed to be retried because of the
     * timing window between when a task is done and when the executor is ready
     * for the next task.
     */
    private int retryCount;

    /**
     * Creates an instance of this class.
     *
     * @param corePoolSize the number of threads to keep in the pool, even if
     * they are idle
     * @param maxPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime when the number of threads is greater than the
     * core, this is the maximum time that excess idle threads will wait for
     * new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param queueSize the size of the work queue
     * @param threadFactory the factory to use when the executor creates a new
     * thread
     * @throws IllegalArgumentException if: <ul>
     * <li> {@code corePoolSize < 0}
     * <li> {@code maxPoolSize <= 0}
     * <li> {@code keepAliveTime < 0}
     * <li> {@code queueSize < 1}
     * <li> {@code maxPoolSize < corePoolSize}
     * </ul>
     */
    public AdjustableThreadPoolExecutor(int corePoolSize,
                                        int maxPoolSize,
                                        long keepAliveTime,
                                        TimeUnit unit,
                                        int queueSize,
                                        ThreadFactory threadFactory) {
        super(corePoolSize, maxPoolSize, keepAliveTime, unit,

              /*
               * Use a synchronous queue so that the queue blocks if no more
               * core threads are available, which causes new threads to be
               * created up to the max pool size
               */
              new SynchronousQueue<>(), threadFactory);
        if (queueSize < 1) {
            throw new IllegalArgumentException(
                "queueSize must be greater than 0");
        }
        synchronized (this) {
            this.queueSize = queueSize;
            queue = new ArrayBlockingQueue<>(queueSize);
        }
        setRejectedExecutionHandler(new AddToQueueHandler());
    }

    /**
     * Handle rejected execution by attempting to add the task to the queue.
     */
    private class AddToQueueHandler extends AbortPolicy {
        @Override
        public void rejectedExecution(Runnable r,
                                      ThreadPoolExecutor executor) {
            assert executor == AdjustableThreadPoolExecutor.this;
            if (isShutdown()) {
                super.rejectedExecution(r, executor);
            }
            synchronized (AdjustableThreadPoolExecutor.this) {

                /*
                 * If the queue is empty and there are no threads currently
                 * executing, then there was a race between when the last task
                 * was done and when the executor was ready for the next task.
                 * Try again.
                 */
                if (queue.isEmpty() && (executingCount == 0)) {
                    retryCount++;
                    throw new RetryExecutionException();
                }

                /*
                 * Reject if we are changing queue sizes and the queue is
                 * already full with respect to the new size.
                 */
                if (shrink && (queue.size() >= queueSize)) {
                    super.rejectedExecution(r, executor);
                }

                /* Otherwise, reject if we can't add to the queue */
                if (!queue.offer(r)) {
                    super.rejectedExecution(r, executor);
                }
            }
        }
    }

    private static class RetryExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1;
    }

    /**
     * Sets both the queue size and maximum thread pool size in the proper
     * order so that the handling capacity of the pool is maintained during the
     * transition.
     */
    public synchronized void setQueueAndMaximumPoolSizes(
        int newQueueSize, int newMaximumPoolSize)
    {
        /* Check values first so there are no side effects on failure */
        if (newQueueSize < 1) {
            throw new IllegalArgumentException(
                "newQueueSize must be greater than 0");
        }
        if (newMaximumPoolSize < 1) {
            throw new IllegalArgumentException(
                "newMaximumPoolSize must be greater than 0");
        }
        final int corePoolSize = getCorePoolSize();
        if (newMaximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                "newMaximumPoolSize must not be less than corePoolSize");
        }

        final int currentQueueSize = getQueueSize();
        final int currentMaximumPoolSize = getMaximumPoolSize();

        /*
         * If increasing the number of threads, do that first before modifying
         * the queue size so that the thread pool can handle any items that
         * might no longer fit in the queue. If decreasing the number of
         * threads, increase the queue size first to handle the additional
         * load.
         */
        if (currentMaximumPoolSize < newMaximumPoolSize) {
            setMaximumPoolSize(newMaximumPoolSize);
        }
        if (currentQueueSize != newQueueSize) {
            setQueueSize(newQueueSize);
        }
        if (currentMaximumPoolSize > newMaximumPoolSize) {
            setMaximumPoolSize(newMaximumPoolSize);
        }
    }

    /**
     * Returns the capacity of the work queue.
     */
    public synchronized int getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the capacity of the work queue.
     *
     * @param newSize the new size
     * @throws IllegalArgumentException if newSize is less than 1
     */
    public synchronized void setQueueSize(int newSize) {
        if (newSize < 1) {
            throw new IllegalArgumentException(
                "queueSize must be greater than 0");
        }

        /* No change */
        if (queueSize == newSize) {
            return;
        }

        queueSize = newSize;

        /* Just copy the old queue contents if they will fit */
        if (newSize > queue.size()) {
            queue = new ArrayBlockingQueue<>(newSize, false, queue);
            return;
        }

        /* Otherwise, wait for items to drain from the queue before copying */
        shrink = true;
    }

    @Override
    public void execute(Runnable command) {
        while (true) {
            try {
                super.execute(command);
                return;
            } catch (RetryExecutionException e) {

                /*
                 * Retry execution, giving a hint to the scheduler to allow
                 * other threads to run since we want the next executor thread
                 * to have a chance to get ready
                 */
                Thread.yield();
            }
        }
    }

    /**
     * Returns the number of times the execute operation was retried because
     * of a race between task completion and being ready for the next
     * operation.
     */
    synchronized int getRetryCount() {
        return retryCount;
    }

    @Override
    protected synchronized void beforeExecute(Thread t, Runnable r) {
        executingCount++;
    }

    /**
     * Execute tasks in the queue when a previous task is done. If the queue is
     * being made smaller and removing an element from the current queue makes
     * it small enough, reduce the queue size.
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        while (!isShutdown()) {
            final Runnable next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    executingCount--;
                    break;
                }
                if (shrink && (queue.size() <= queueSize)) {
                    queue = new ArrayBlockingQueue<>(queueSize, false, queue);
                    shrink = false;
                }
            }
            next.run();
        }
    }

    @Override
    public synchronized BlockingQueue<Runnable> getQueue() {
        return queue;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + " queueSize=" + queueSize + "]";
    }
}
