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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.fault.AsyncEndpointGroupFaultHandler;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.test.TestIOHook;
import oracle.kv.impl.util.CommonLoggerUtils;

/**
 * A single thread scheduled executor for nio selectable channels.
 *
 * The executor serves two purposes:
 * (1) runs the select-process loop for a selector and channels registered;
 * (2) runs tasks as a scheduled executor.
 * Since the two functionality competes for execution cycles, we must ensure
 * some kind of fairness between them. This requires thread-safety concerns
 * such that the actions of one functionality does not block the other
 * indefinetly. This also invovles some fairness strategy, although currently
 * we only implement the most simple one.
 *
 * TODO: Each connection is associated with one and only one executor. Multiple
 * connections can associated with one executor. This causes a load-balancing
 * problem, e.g., when one connection is doing heavy-IO, other connections
 * associated with the same executor will be affected while some other
 * executors are idle. To solve this, two techniques are necessary: detection
 * and migration. We need to be able to detect such situation, identify the
 * problematic connection and migrate connections to create a balanced load for
 * all executors.
 */
public class NioChannelExecutor
    extends AbstractExecutorService implements Runnable, ChannelExecutor {

    /* For testing */
    public static volatile boolean MAX_VALUE_TASK_REMOVAL_INTERVAL = false;
    public static volatile TestIOHook<Selector> selectorTestHook = null;

    private final Logger logger;

    private final NioChannelThreadPool parent;
    private final int childId;
    private final int childIndex;
    private final String id;

    /* Sequence number to break scheduling ties, and in turn to guarantee FIFO
     * order among tied entries. */
    private final static AtomicLong sequencer = new AtomicLong();

    /* Start time of this executor */
    private final long startTimeNanos = System.nanoTime();

    /* Queue for register operations */
    private final Queue<RegisterEntry> registerQueue =
        new ConcurrentLinkedQueue<RegisterEntry>();

    /* Tasks accounting for select deadline */
    private final Queue<Runnable> taskQueue =
        new ConcurrentLinkedQueue<Runnable>();
    private final PriorityQueue<ScheduledFutureTask<?>> delayedQueue =
        new PriorityQueue<ScheduledFutureTask<?>>();
    private final List<Runnable> remainingTasks = new ArrayList<Runnable>();

    /* A thread local indicating a thread is the one powering the executor */
    private final ThreadLocal<Boolean> inExecutorThread =
        ThreadLocal.withInitial(() -> false);

    /*
     * Current time. Updated from time to time since System.nanoTime() is
     * relatively expensive comparing to submitted tasks that are possibly
     * small. It is only accessed inside the channel executor thread; no
     * thread-safety concerns.
     */
    private long currTimeNanos = nanoTime();

    /*
     * Update interval. Number of submitted tasks executed before we update the
     * current time.
     */
    private final static int UPDATE_INTERVAL = 64;
    /*
     * A count down starting from UPDATE_INTERVAL. It is only accessed inside
     * the channel executor thread; no thread safety concerns.
     */
    private int updateCountdown = 0;
    /*
     * Number of tasks cancelled before we iterate through the delayed queue to
     * remove them.
     */
    private final static int REMOVE_COUNT_INTERVAL = 1024;
    /*
     * A count down for canceled tasks. PriorityQueue#remove has O(N)
     * complexity, therefore, we only remove cancelled tasks once for a while.
     * It is only accessed inside the channel executor thread; no thread safety
     * concerns.
     */
    private int cancelledTaskCountdown = REMOVE_COUNT_INTERVAL;

    /*
     * Number of nanoseconds passed before we iterate through the delayed
     * queue to remove cancelled items. Similar to REMOVE_COUNT_INTERVAL but in
     * time. Default to 100 ms. The mechanism only treat this interval as an
     * approximation: we only check and remove tasks when the selector is waked
     * up and run tasks. I think this is fine since if the selector is not
     * waked up frequent enough, then not much is going on.
     */
    private final static long REMOVE_TIME_INTERVAL_NANOS =
        TimeUnit.MILLISECONDS.toNanos(100);
    /*
     * The last time we remove. It is only accessed inside the channel executor
     * thread; no thread safety concerns.
     */
    private long lastRemoveTimeNanos = 0;

    /*
     * A short select time of 1 second. Limit the maximum select time so that
     * the executor can be more responsive for its own tasks, e.g., removing
     * cancelled tasks.
     */
    private final static long SHORT_SELECT_TIME_NANOS =
        TimeUnit.SECONDS.toNanos(1);
    /*
     * The maximum select time in nanoseconds. The value is set to the
     * SHORT_SELECT_TIME_NANOS if there are cancelled tasks, otherwise,
     * Integer.MAX_VALUE. Thread safety is not an issue because we always set
     * the value inside the executor thread.
     */
    private long maxSelectTimeNanos = Integer.MAX_VALUE;

    /*
     * The selector. All operations on the selector as well as selector rebuild
     * are inside the executor thread, and hence no synchronization necessary.
     */
    private Selector selector;

    private final AtomicBoolean pendingWakeup = new AtomicBoolean(false);

    /*
     * The default calculator that returns a constant 100 micro-seconds as the
     * maximum execution time for non-IO tasks after each IO processing.
     */
    private final TaskTimeCalculator taskTimeCalculator =
        new DefaultTaskTimeCalculator(100000L);
    /*
     * Indicates whether there are new events happen to the executor during the
     * last period. The period starts after last quiescent check. The new
     * events include task submission and IO events.
     */
    private final AtomicBoolean hasNewEventsLastPeriod =
        new AtomicBoolean(false);
    /*
     * The number of consecutive quiescent period. The executor experiences a
     * quiescent period if during that period there is no new events and there
     * is no upper-layer periodic tasks.
     */
    private final AtomicLong numQuiescentPeriods = new AtomicLong(0);
    /*
     * The maximum number of consecutive quiescent periods after which the
     * executor will be deemed inactive and shut down. Note that when there are
     * periodic tasks and IO events, e.g., channel periodic ping, the executor
     * is deemed active. Therefore, alive idle connections will prevent the
     * executor from shutting down.
     */
    private final int maxNumQuiescentPeriods;
    /* The quiescent task */
    private final QuiescentMonitorTask quiescentMonitorTask =
        new QuiescentMonitorTask();
    /* The quiescent check interval in milliseconds */
    private static final int quiescentMonitorIntervalMillis = 100;

    /* The fault handler */
    private final AsyncEndpointGroupFaultHandler faultHandler;

    enum State {
        /*
         * Accept new tasks, select and process queued tasks. Transit to
         * SHUTDOWN or STOP.
         */
        RUNNING,

        /*
         * Accept new tasks, select and process queued tasks. No new channel
         * can be registered. Transit to SHUTDOWN if no registered channel.
         */
        SHUTTINGDOWN,

        /*
         * Don't accept new tasks, don't select, process immediate one-shot
         * tasks, don't process delayed tasks, don't process periodic tasks.
         * Transit to STOP.
         */
        SHUTDOWN,

        /*
         * Don't accept new tasks, don't select, don't process tasks. Transit
         * to TERMINATED.
         */
        STOP,

        /* Cleaned up and terminated */
        TERMINATED,
    }

    /*
     * State of the executor. Updates should be inside a synchronization block
     * of this object. Volatile for read access.
     */
    private volatile State state = State.RUNNING;

    public NioChannelExecutor(Logger logger,
                              NioChannelThreadPool parent,
                              int childId,
                              int childIndex)
        throws IOException {

        this(logger, parent, childId, childIndex,
             60 /* 1 min quiescent time */,
             AsyncEndpointGroupFaultHandler.DEFAULT);
    }

    public NioChannelExecutor(Logger logger,
                              NioChannelThreadPool parent,
                              int childId,
                              int childIndex,
                              int maxQuiescentSeconds)
        throws IOException {

        this(logger, parent, childId, childIndex, maxQuiescentSeconds,
            AsyncEndpointGroupFaultHandler.DEFAULT);
    }

    public NioChannelExecutor(Logger logger,
                              NioChannelThreadPool parent,
                              int childId,
                              int childIndex,
                              int maxQuiescentSeconds,
                              AsyncEndpointGroupFaultHandler faultHandler)
        throws IOException {

        super();
        this.logger = logger;
        this.parent = parent;
        this.childId = childId;
        this.childIndex = childIndex;
        this.maxNumQuiescentPeriods =
            maxQuiescentSeconds * 1000 / quiescentMonitorIntervalMillis;
        this.id = String.format(
            "%x#%x[%d]", parent.getId(), childId,  childIndex);
        this.selector = Selector.open();
        this.faultHandler = faultHandler;
    }

    /**
     * Returns the id in the form of parentId#childId[childIndex].
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the childId.
     */
    public int getChildId() {
        return childId;
    }

    /**
     * Returns the childIndex.
     */
    public int getChildIndex() {
        return childIndex;
    }


    /* Implements ScheduledExecutorService */

    @Override
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        if (isShutdownOrAfter()) {
            rejectExecutionDueToShutdown();
        }
        FutureTask<Void> task = new PrintableFutureTask(command);
        taskQueue.add(task);
        wakeup();
        /*
         * Cancel the task if the executor is shutting down while the task is
         * being added
         */
        if (isShutdownOrAfter()) {
            if (task.cancel(false)) {
                rejectExecutionDueToShutdown();
            }
        } else {
            onNewEvent();
        }
    }

    private void rejectExecutionDueToShutdown() {
        throw new RejectedExecutionException(String.format(
            "Executor %s is shut down", id));
    }

    private void onNewEvent() {
        hasNewEventsLastPeriod.set(true);
        numQuiescentPeriods.set(0);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command,
            long delay, TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        ScheduledFutureTask<Void> task = new ScheduledFutureTask<Void>(
                command, null, getDelayedTime(delay, unit));
        addDelayedTask(task);
        return task;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
            long delay, TimeUnit unit) {
        if (callable == null || unit == null)
            throw new NullPointerException();
        ScheduledFutureTask<V> task = new ScheduledFutureTask<V>(
                callable, getDelayedTime(delay, unit));
        addDelayedTask(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
            long initialDelay, long period, TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (period <= 0L)
            throw new IllegalArgumentException();
        ScheduledFutureTask<Void> task = new ScheduledFutureTask<Void>(
                command, null, getDelayedTime(initialDelay, unit),
                unit.toNanos(period));
        addDelayedTask(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
            long initialDelay, long delay, TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (delay <= 0L)
            throw new IllegalArgumentException();
        ScheduledFutureTask<Void> task = new ScheduledFutureTask<Void>(
                command, null, getDelayedTime(initialDelay, unit),
                -unit.toNanos(delay));
        addDelayedTask(task);
        return task;

    }

    /*
     * Implements the method of ExecutorService, although the upper layer is
     * not expected to call this method directly. Shutting down is expected to
     * be done with NioChannelThreadPool#shutdown. We are not restarting the
     * executor if it is shutdown.
     */
    @Override
    public void shutdown() {
        logger.log(Level.FINEST, () -> String.format(
            "Executor (%s) shut down gracefully", getId()));
        synchronized(this) {
            if (isShuttingDownOrAfter()) {
                return;
            }
            state = State.SHUTTINGDOWN;
        }
        wakeup();
    }

    /**
     * Shuts down the executor forcefully.
     * This is a non-blocking method.
     */
    public void shutdownForcefully() {
        logger.log(Level.FINEST, () -> String.format(
            "Executor (%s) shut down forcefully", getId()));
        /* Transit to stop */
        synchronized(this) {
            if (isTerminated()) {
                return;
            }
            state = State.STOP;
        }
        /* Terminate if in executor thread. */
        if (inExecutorThread()) {
            terminate();
            return;
        }
        /*
         * Wake up from select.
         */
        wakeup();
    }

    /*
     * Note that the returned tasks may contain those that are already
     * cancelled.
     *
     * Note that this is a blocking method and waits for the executor to fully
     * shut down.
     *
     * Implements the method of ExecutorService, although the upper layer is
     * not expected to call this method directly. Shutting down is expected to
     * be done with NioChannelThreadPool#shutdown. We are not restarting the
     * executor if it is shutdown.
     */
    @Override
    public List<Runnable> shutdownNow() {
        shutdownForcefully();
        if (!inExecutorThread()) {
            /*
             * We should not wait long, so just wait for 10 secodns and if it
             * is not terminated, something is wrong.
             */
            try {
                awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                /* do nothing */
            }
        }
        if (!isTerminated()) {
            throw new IllegalStateException(
                          String.format(
                              "The executor is not terminated " +
                              "after 10 seconds; something is wrong: " +
                              "channel state:\n%s",
                              toString()));
        }
        return remainingTasks;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {

        if (inExecutorThread()) {
            throw new IllegalStateException(
                    "Cannot wait for termination inside executor thread");
        }

        if (!isShuttingDownOrAfter()) {
            throw new IllegalStateException(
                    "Should only call awaitTermination " +
                    "after executor is shutting down");
        }

        final long timeoutMillis =
            MILLISECONDS.convert(timeout, unit);
        final long deadlineMillis = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            long waitTimeMillis = deadlineMillis - System.currentTimeMillis();
            if (waitTimeMillis <= 0) {
                break;
            }
            synchronized(this) {
                final boolean done = isTerminated();
                if (done) {
                    return done;
                }
                wait(waitTimeMillis);
            }
        }
        return isTerminated();
    }

    @Override
    public boolean isShutdown() {
        return isShuttingDownOrAfter();
    }


    @Override
    public boolean isTerminated() {
        return state == State.TERMINATED;
    }

    /**
     * Returns {@code true} when executed in the thread of the executor.
     *
     * @return {@code true} if in executor thread
     */
    @Override
    public boolean inExecutorThread() {
        return inExecutorThread.get();
    }

    /*
     * If the method is executed inside the executor, the operation is done
     * when the method returns ; otherwise, the operation is queued and
     * executed in order at a later time (since {@link
     * SelectableChannel#register} blocks when selector is selecting).
     *
     * NOTE: remember to wake up the executor upon channel close. When
     * non-blocking socket and server socket channels are closed, the JVM might
     * not clean them up immediately if registered with a selector. The
     * selector must select again for them to be cleaned up. Whether the
     * channel is cleaned up was OS dependent before JDK11, but changed to
     * consistently not cleaned up after.
     */
    @Override
    public void registerAcceptInterest(ServerSocketChannel channel,
                                       ChannelAccepter handler)
        throws IOException {

        if (isShuttingDownOrAfter()) {
            throw new IOException("Executor is shutting down");
        }
        channel.configureBlocking(false);
        if (!inExecutorThread()) {
            registerQueue.add(
                    new RegisterEntry(
                        channel, SelectionKey.OP_ACCEPT, handler));
            wakeup();
        } else {
            if (!registerQueue.isEmpty()) {
                /* Make sure to do all operations submitted before this. */
                doRegister();
            }
            registerChannel(channel, SelectionKey.OP_ACCEPT, handler);
        }
        /* Check again in case there is a race */
        if (isShuttingDownOrAfter()) {
            throw new IOException("Executor is shutting down");
        }
    }

    @Override
    public void registerConnectInterest(SocketChannel channel,
                                   ChannelHandler handler) throws IOException {

        setInterest(channel, handler, SelectionKey.OP_CONNECT);
    }

    @Override
    public void setReadInterest(SocketChannel channel,
                                ChannelHandler handler,
                                boolean interest) throws IOException {
        logger.log(Level.FINEST, () -> String.format(
            "Channel %s %s for read notification%s",
            channel,
            interest ? "setting" : "cancelling",
            /* log a stacktrace if cancelling */
            (interest ?
             "" :
             ", " + CommonLoggerUtils.getStackTrace(new RuntimeException()))));
        setInterest(channel, handler,
                    getUpdatedInterest(
                        channel, SelectionKey.OP_READ, interest));
    }

    private int getUpdatedInterest(SocketChannel channel,
                                   int op,
                                   boolean interest) {
        final SelectionKey key = channel.keyFor(selector);
        final int currOps = (key == null) ? 0 : key.interestOps();
        return interest ? currOps | op : currOps & ~op;
    }

    @Override
    public void setWriteInterest(SocketChannel channel,
                                 ChannelHandler handler,
                                 boolean interest) throws IOException {
        logger.log(Level.FINEST, () -> String.format(
            "Channel %s %s for write notification%s",
            channel,
            interest ? "setting" : "cancelling",
            /* log a stacktrace if setting */
            (interest ?
             ", " + CommonLoggerUtils.getStackTrace(new RuntimeException()) :
             "")));
        setInterest(channel, handler,
                    getUpdatedInterest(
                        channel, SelectionKey.OP_WRITE, interest));
    }

    /*
     * If the method is executed inside the executor, the operation is done
     * when the method returns ; otherwise, the operation is queued and
     * executed in order at a later time (since {@link
     * SelectableChannel#register} blocks when selector is selecting).
     *
     * NOTE: remember to wake up the executor upon channel close. See the note
     * of {@link #registerAccept}.
     */
    @Override
    public void setInterest(SocketChannel channel,
                            ChannelHandler handler,
                            int ops)
        throws IOException {

        if (isShuttingDownOrAfter()) {
            rejectExecutionDueToShutdown();
        }
        channel.configureBlocking(false);
        if (!inExecutorThread()) {
            registerQueue.add(
                    new RegisterEntry(channel, ops, handler));
            wakeup();
        } else {
            if (!registerQueue.isEmpty()) {
                /* Make sure to do all operations submitted before this. */
                doRegister();
            }
            try {
                registerChannel(channel, ops, handler);
            } catch (ClosedSelectorException e) {
                /*
                 * The selector may have been closed because the executor is
                 * shutting down itself, or that there is an IOException to the
                 * selector, which the selector will be rebuilt. In either
                 * case, it is not a problem of the socket channel, but of the
                 * executor. Rejects the register operation so that it can be
                 * retried with another executor.
                 */
                rejectExecutionDueToShutdown();
            }
        }
        /* Check again in case there is a race */
        if (isShuttingDownOrAfter()) {
            rejectExecutionDueToShutdown();
        }
    }

    /**
     * Removes any registered interest of the channel.
     *
     * If the method is executed inside the executor, the operation is done
     * when the method returns ; otherwise, the operation is queued and
     * executed in order at a later time (to maintain the order w.r.t. other
     * operations).
     */
    @Override
    public void deregister(SelectableChannel channel) throws IOException {
        if (!inExecutorThread()) {
            registerQueue.add(
                    new RegisterEntry(channel, 0, null));
            wakeup();
        } else {
            if (!registerQueue.isEmpty()) {
                /* Make sure to do all operations submitted before this. */
                doRegister();
            }
            deregisterChannel(channel);
        }
    }

    /* Implements Runnable */

    /**
     * Run the executor.
     */
    @Override
    public void run() {
        inExecutorThread.set(true);

        logger.log(Level.FINEST, () -> String.format(
            "Executor (%s) starts running", getId()));
        try {
            scheduleAtFixedRate(new DumpTask(), 0, 10, TimeUnit.SECONDS);
            scheduleWithFixedDelay(
                quiescentMonitorTask, 0,
                quiescentMonitorIntervalMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException re) {
            if (!isShutdownOrAfter()) {
                throw new IllegalStateException(
                              "Executor not shut down " +
                              "but cannot schedule tasks.");
            }
            /* Executor is shutting down, do nothing */
        }
        try {
            faultHandler.execute(() -> {
                while (!isShutdownOrAfter()) {
                    try {
                        runOnce();
                        transitIfShutdown();
                    } catch (InterruptedException
                             | ClosedSelectorException
                             | CancelledKeyException
                             expected) {
                        /*
                         * The caught exceptions are expected. Simply continue.
                         */
                        logger.log(Level.FINE, () -> String.format(
                            "Exception running executor (%s): %s",
                            getId(), expected));
                        continue;
                    }
                }
                logger.log(Level.FINEST, () -> String.format(
                    "Executor (%s) stopping", getId()));
                if (!isStoppedOrAfter()) {
                    runTasks(Integer.MAX_VALUE);
                }
                logger.log(Level.FINEST, () -> String.format(
                    "Executor (%s) terminating", getId()));
                terminate();
            });
        } catch (Throwable t) {
            logger.log(Level.INFO, () -> String.format(
                "Executor encounters an error thrown by the fault handler: %s",
                CommonLoggerUtils.getStackTrace(t)));
        } finally {
            /*
             * Do nothing if normally terminated, otherwise, error is thrown
             * from the fault handler and we shuts down forcefully.
             */
            shutdownForcefully();
        }
    }

    /**
     * Run once for the select, process and run task.
     *
     * This method can be used when upper layers want to control the select
     * control flow, e.g., to run multiple select loops in a single thread.
     */
    public void runOnce() throws InterruptedException {
        long procTime = selectAndProcess();
        long taskTime = taskTimeCalculator.getTaskTimeNanos(procTime);
        runTasks(getDelayedTime(taskTime));
    }

    /* NioChannelExecutor specific */

    public boolean isShuttingDownOrAfter() {
        return state.compareTo(State.SHUTTINGDOWN) >= 0;
    }

    public boolean isShutdownOrAfter() {
        return state.compareTo(State.SHUTDOWN) >= 0;
    }

    public boolean isStoppedOrAfter() {
        return state.compareTo(State.STOP) >= 0;
    }

    /**
     * A calculator to compute the maximum execution time for running non-IO
     * tasks after IO processing.
     */
    public interface TaskTimeCalculator {
        /**
         * Gets the maximum execution time for non-IO tasks, in nanoseconds,
         * according to the IO processing time.
         *
         * @param procTimeNanos the IO processing time in nano seconds
         * @return the execution time for non-IO tasks
         */
        long getTaskTimeNanos(long procTimeNanos);
    }

    /**
     * Returns the nano time with respect to the start time.
     */
    public long nanoTime() {
         return System.nanoTime() - startTimeNanos;
    }

    /**
     * Return the string that represent the status of this executor.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName());
        builder.append("(").append(getId()).append(")");
        builder.append("\n");
        builder.append("\tnanoTime at startup=").append(startTimeNanos);
        builder.append(" elapsedNanos=").append(nanoTime());
        builder.append("\n");
        builder.append("\tthread=").append(Thread.currentThread());
        builder.append("\tselector=").append(selector);
        builder.append("\n");
        builder.append("\tstate=").append(state);
        builder.append("\t#quiescentPeriods=").
            append(numQuiescentPeriods.get());
        builder.append(" #taskQueue=").append(taskQueue.size());
        builder.append(" #delayedQueue=").append(delayedQueue.size());
        builder.append(" #remainingTasks=").append(remainingTasks.size());
        builder.append("\n");
        builder.append("\tnext task: ").append(taskQueue.peek()).append("\n");
        builder.append("\tnext delayed: ").
            append(delayedQueue.peek()).append("\n");
        builder.append("\tselecting channels:\n");
        try {
            for (SelectionKey key : selector.keys()) {
                builder.append("\t\tch=").append(key.channel()).append(" ");
                try {
                    builder.append("ops=").
                        append(Integer.toBinaryString(key.interestOps())).
                        append("\n");
                } catch (Throwable t) {
                    builder.append("error=").append(t).append("\n");
                }
            }
        } catch (Throwable t) {
            builder.append("\terror=").append(t);
        }
        return builder.toString();
    }

    /**
     * Closes all socket channels registered to the selector of this executor.
     *
     * For testing, to injest channel error in tests.
     */
    public void closeSocketChannels() {
        try {
            for (SelectionKey key : selector.keys()) {
                try {
                    SelectableChannel channel = key.channel();
                    if (channel instanceof SocketChannel) {
                        channel.close();
                    }
                } catch (Throwable t) {
                    /* ignore */
                }
            }
        } catch (Throwable t) {
            /* ignore */
        }
        wakeup();
    }

    /**
     * Waits until there is no user activity in exectuor
     *
     * For testing, to wait for things to quiet down.
     *
     * @param quiescentTimeMillis required quiescent time
     * @param timeoutMillis timeout for the wait
     */
    public void assertQuiescence(long quiescentTimeMillis, long timeoutMillis)
        throws Exception {

        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException(String.format(
                    "Executor not quiescent for %s ms " +
                    "after %d ms, %s, tasks:%s",
                    quiescentTimeMillis, timeoutMillis,
                    this, tasksToString()));
            }
            if (numQuiescentPeriods.get() * quiescentMonitorIntervalMillis >=
                quiescentTimeMillis) {
                break;
            }
            synchronized(quiescentMonitorTask) {
                quiescentMonitorTask.wait(
                    deadline - System.currentTimeMillis());
            }
        }
    }

    /**
     * Returns the string representation of executor tasks.
     */
    public String tasksToString() {
        try {
            /*
             * Submit a task to obtain the string representation of tasks since
             * delayedQueue (a priority queue) is not thread safe.
             */
            Future<String> future =
                schedule((Callable<String>)(new TasksPrintTask()),
                         0, TimeUnit.MILLISECONDS);
            return future.get(10, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            return String.format("Cannot obtain tasks: %s", t);
        }
    }

    /**
     * Wake up the selector.
     *
     * Newly submitted tasks can be run and the selector can select on new
     * keys, or closed channels can be cleaned up.
     *
     * selector.wakeup() is a relatively expensive operation; use the
     * pendingWakeup variable for optimization.
     */
    void wakeup() {
        if (!inExecutorThread() && pendingWakeup.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }


    /* Implementations */

    /**
     * Returns a delayed time in the future, in nano-seconds, relative to the
     * current time.
     */
    private long getDelayedTime(long delay, TimeUnit unit) {
        return getDelayedTime(unit.toNanos((delay < 0) ? 0 : delay));
    }

    private long getDelayedTime(long delayNanos) {
        final long curr = nanoTime();
        long result = curr + delayNanos;
        /*
         * If result is less than the curr, overflow, just set it to maximum.
         */
        result = (result < curr) ? Long.MAX_VALUE : result;
        return result;
    }

    private class ScheduledFutureTask<V>
            extends FutureTask<V> implements RunnableScheduledFuture<V> {

        private final Object innerTask;
        private volatile long execTimeNanos;
        private final long seqno;

        /*
         * A positive value indicates fixed-rate execution.
         * A negative value indicates fixed-delay execution.
         * A value of 0 indicates a non-repeating (one-shot) task.
         */
        private final long period;

        ScheduledFutureTask(Callable<V> c, long execTimeNanos) {
            super(c);
            this.innerTask = c;
            this.execTimeNanos = execTimeNanos;
            this.period = 0;
            this.seqno = sequencer.getAndIncrement();
        }

        ScheduledFutureTask(Runnable r, V result, long execTimeNanos) {
            super(r, result);
            this.innerTask = r;
            this.execTimeNanos = execTimeNanos;
            this.period = 0;
            this.seqno = sequencer.getAndIncrement();
        }

        ScheduledFutureTask(Runnable r,
                            V result,
                            long execTimeNanos,
                            long period) {
            super(r, result);
            this.innerTask = r;
            this.execTimeNanos = execTimeNanos;
            this.period = period;
            this.seqno = sequencer.getAndIncrement();
        }

        public Object getInnerTask() {
            return innerTask;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(execTimeNanos - nanoTime(), NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed delayed) {
            if (delayed == this) {
                return 0;
            }
            if (delayed instanceof ScheduledFutureTask) {
                ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) delayed;
                long diff = this.execTimeNanos - that.execTimeNanos;
                if (diff < 0) {
                    return -1;
                }
                if (diff > 0) {
                    return 1;
                }
                if (this.seqno < that.seqno) {
                    return -1;
                }
                return 1;
            }
            long diff = getDelay(NANOSECONDS) -
                delayed.getDelay(NANOSECONDS);
            return Long.signum(diff);
        }

        @Override
        public boolean isPeriodic() {
            return period != 0;
        }

        @Override
        public void run() {
            final boolean periodic = isPeriodic();
            if (!canRunTask(periodic)) {
                cancel(false);
            } else if (!periodic) {
                super.run();
            } else if (super.runAndReset()) {
                setNextTime();
                delayedQueue.add(this);
            }
        }

        @Override
        public void done() {
            /*
             * We need to report the exception if there is any. Just get the
             * result and rethrow if there is any exception and runTasks method
             * will catch it.
             */
            if (!isCancelled()) {
                try {
                    get();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(
                        "Unexpected exception: " + e, e);
                }
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                if (inExecutorThread()) {
                    removeCancelledTasksIfNeeded();
                } else {
                    execute(new PrintableFutureTask(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        removeCancelledTasksIfNeeded();
                                    }

                                    @Override
                                    public String toString() {
                                        return "Removing " + innerTask;
                                    }
                                }));
                }
            }
            return cancelled;
        }

        private void setNextTime() {
            if (period > 0) {
                execTimeNanos += period;
            } else if (period < 0) {
                execTimeNanos = getDelayedTime(-period);
            }
        }

        private boolean canRunTask(boolean periodic) {
            if (state == State.RUNNING) {
                return true;
            }
            if (innerTask instanceof ExecutorTask) {
                return true;
            }
            if ((state == State.SHUTDOWN) && (!periodic)) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder builder =
                new StringBuilder(getClass().getSimpleName());
            builder.append(": seqno=").append(seqno).
                    append(" execTimeNanos=").append(execTimeNanos).
                    append(" period=").append(period).
                    append(" task=").append(innerTask).
                    append(" cancelled=").append(isCancelled());
            return builder.toString();
        }
    }

    private void addDelayedTask(final ScheduledFutureTask<?> task) {
        if (isShutdownOrAfter()) {
            rejectExecutionDueToShutdown();
        }
        if (inExecutorThread()) {
            delayedQueue.add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    delayedQueue.add(task);
                }

                @Override
                public String toString() {
                    return "Adding to delayed queue: " + task;
                }
            });
            wakeup();
        }
        onNewEvent();
    }

    private void removeCancelledTasksIfNeeded() {
        if ((cancelledTaskCountdown --) == 0) {
            removeCancelledTasks();
        } else {
            maxSelectTimeNanos = SHORT_SELECT_TIME_NANOS;
        }
    }

    private void removeCancelledTasks() {
        Iterator<ScheduledFutureTask<?>> iter =
            delayedQueue.iterator();
        while (iter.hasNext()) {
            if (iter.next().isCancelled()) {
                iter.remove();
            }
        }
        cancelledTaskCountdown =
            MAX_VALUE_TASK_REMOVAL_INTERVAL ?
            Integer.MAX_VALUE : REMOVE_COUNT_INTERVAL;
        maxSelectTimeNanos = Integer.MAX_VALUE;
    }

    /**
     * An entry for the register operation.
     */
    private class RegisterEntry {
        final SelectableChannel channel;
        final int ops;
        final Object attach;

        RegisterEntry(SelectableChannel channel, int ops, Object attach) {
            this.channel = channel;
            this.ops = ops;
            this.attach = attach;
        }
    }

    /**
     * Transit to shut down state if we are shutting down and do not have any
     * active channels.
     */
    private void transitIfShutdown() {
        if (!isShuttingDownOrAfter()) {
            return;
        }
        for (SelectionKey key : selector.keys()) {
            if (key.channel().isOpen()) {
                return;
            }
        }
        synchronized(this) {
            if (state == State.SHUTTINGDOWN) {
                state = State.SHUTDOWN;
            }
        }
    }

    /**
     * Executes the select and the IO processing and returns the duration of IO
     * processing in nanos.
     */
    private long selectAndProcess() throws InterruptedException {

        assert inExecutorThread();

        if (isShutdownOrAfter()) {
            return 0;
        }

        while (true) {
            try {
                TestHookExecute.doIOHookIfSet(selectorTestHook, selector);

                doSelect();

                final long start = nanoTime();
                doProcess();
                currTimeNanos = nanoTime();

                return currTimeNanos - start;
            } catch (IOException e) {
                logger.log(Level.INFO,
                           e,
                           () -> String.format(
                               "Selector select and process exception"));
                /*
                 * All endpoint handlers should wrap the callbacks so that they
                 * do not throw IOException and therefore this exception means
                 * the selector is messed up. Rebuild.
                 */
                rebuildSelector();
                handleExecutorException(e);
            }
        }
    }

    /**
     * Do a select call.
     *
     * Block until but not past the next deadline.
     *
     * To ensure that we do not block past the deadline, we do two things:
     * (I) never block when there is a task in taskQueue;
     * (II) set pendingWakeup to false at the end of every select call.
     *
     * With these two actions, we will never block past the deadline. To
     * demonstrate, let's assume that a select interval SelectInterval_i:
     * (SelectStart_i, SelectEnd_i) missed the deadline of task T_j. The task
     * T_j has three steps before its execution:
     * Step1 taskQueue.add(new Runnable() { delayedQueue.add(T_j) })
     * Step2 wakeup()
     * Step3 delayedQueue.add(T_j)
     * For the interval to miss T_j, it can only happen under the following
     * conditions:
     * (1) Step3 is after SelectStart_i, otherwise, SelectStart_i will see the
     *     deadline and should not miss it.
     * (2) Step1 is after SelectStart_i, otherwise, because of (1), taskQueue
     *     will not be empty, and with (I) SelectStart_i will be non-blocking
     * (3) the compareAndSet in wakeup() call fails (i.e., pendingWakeup is
     *     already true), otherwise, because of (1) and (2), the wakeup should
     *     wake up the select interval
     * (4) According to (3) and (II), the pendingWakeup can be only set to true
     *     after the SelectEnd_i-1, therefore, it should wake up
     *     SelectInterval_i before Step2 and thus the interval should not miss
     *     the deadline of T_j
     */
    private void doSelect()
        throws InterruptedException, IOException {

        try {
            long timeoutMillis = (getUntilDeadline() - 100000L) / 1000000L;

            /*
             * Never block if we have some no-delay task or the next delayed
             * deadline is within 1ms
             */
            if (!taskQueue.isEmpty() || (timeoutMillis <= 0)) {
                selector.selectNow();
                /* Set the pendingWakeup to false */
                pendingWakeup.set(false);
                return;
            }

            while (true) {
                /* Do register operations if there is any. */
                doRegister();

                int selectedKeys = selector.select(timeoutMillis);

                /* Set the pendingWakeup to false */
                pendingWakeup.set(false);

                /**
                 * Unblocked from select for one of the following reasons:
                 * - Selected something
                 * - Thread interrupted
                 * - Woke up
                 * - Select timeout
                 * - Sporadic wake up
                 */

                /* selected something */
                if (selectedKeys != 0) {
                    return;
                }

                /* thread interrupted */
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                /* woke up by no delay task */
                if (!taskQueue.isEmpty()) {
                    return;
                }

                /* woke up by delay task or select timeout */
                timeoutMillis = (getUntilDeadline() - 100000L) / 1000000L;
                if (timeoutMillis <= 0) {
                    return;
                }

                /* woke up because of shut down */
                if (isShuttingDownOrAfter()) {
                    return;
                }

                /* woke up because new keys are registered to the selector or
                 * sporadic woke up, just continue */
            }
        } catch (CancelledKeyException e) {
            logger.log(Level.FINEST, () -> String.format(
                "CancelledKeyException during select: %s", e));
        }
    }

    /**
     * Do register operation before select.
     */
    private void doRegister() {

        while (!registerQueue.isEmpty()) {
            RegisterEntry entry = registerQueue.remove();
            if (entry.attach == null) {
                deregisterChannel(entry.channel);
                continue;
            }
            try {
                entry.channel.configureBlocking(false);
                registerChannel(entry.channel, entry.ops, entry.attach);
            } catch (IOException ioe) {
                handleNioHandlerError(
                        (NioHandler) entry.attach, ioe, entry.channel);
            }
        }

        /* Make sure new register can wake up the select. */
        pendingWakeup.set(false);

        /*
         * If someone register after while loop before pendingWakeup is set, to
         * make sure their operation is not missed, we simply wakeup our
         * selector here.
         */
        if ((!registerQueue.isEmpty()) && (!pendingWakeup.get())) {
            selector.wakeup();
        }
    }

    private void registerChannel(SelectableChannel channel,
                                 int ops,
                                 Object att) throws ClosedChannelException {
        try {
            channel.register(selector, ops, att);
        } catch (CancelledKeyException e) {
            if (ops != SelectionKey.OP_ACCEPT) {
                /* Unexpected register after a socket channel is cancelled */
                throw e;
            }
            if (!channel.isOpen()) {
                /*
                 * Unexpected register after a server socket channel is closed
                 */
                throw e;
            }
            /*
             * Re-register for OP_ACCEPT due to connection management. The
             * exception is expected. It could happen because we deregister by
             * cancelling the key and before the selector can select to make
             * the cancellation take effect, we register again, causing the
             * CancelledKeyException. Adding it back to the queue which will
             * retry after selecting again. Use a task so that adding to the
             * queue is called outside the doRegister loop.
             */
            execute(() -> {
                registerQueue.add(new RegisterEntry(channel, ops, att));
            });
        }
    }

    private void deregisterChannel(SelectableChannel channel) {

        SelectionKey key = channel.keyFor(selector);
        if (key != null) {
            key.cancel();
        }
    }

    /**
     * Returns the delay, in nano seconds, until the next delayed task due
     * time, zero if the task is overdue.
     */
    private long getUntilDeadline() {
        currTimeNanos = nanoTime();
        ScheduledFutureTask<?> task = delayedQueue.peek();
        if (task == null) {
            /*
             * We do not have a task, returns the maximum value possible. It
             * seems on some machine, select throws IllegalArgumentException on
             * Long.MAX_VALUE. Hence use Integer.MAX_VALUE here.
             */
            return Integer.MAX_VALUE;
        }
        long diff = Math.min(
            maxSelectTimeNanos, task.execTimeNanos - currTimeNanos);
        return (diff < 0) ? 0 : diff;
    }

    /**
     * Process selected keys
     */
    private void doProcess() {
        final Set<SelectionKey> selectedKeys = selector.selectedKeys();

        if (selectedKeys.isEmpty()) {
            return;
        }

        final Iterator<SelectionKey> iter = selectedKeys.iterator();
        if (iter.hasNext()) {
            onNewEvent();
        }
        while (iter.hasNext()) {
            final SelectionKey key = iter.next();
            final Object attach = key.attachment();
            iter.remove();

            int readyOps;
            try {
                readyOps = key.readyOps();
            } catch (CancelledKeyException e) {
                /*
                 * We may concurrently cancelled the key. It should be
                 * harmless, just continue.
                 */
                continue;
            }

            if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                ServerSocketChannel serverSocketChannel =
                    (ServerSocketChannel) key.channel();
                assert !serverSocketChannel.isBlocking();
                ChannelAccepter handler = (ChannelAccepter) attach;
                handleAccept(serverSocketChannel, handler);
                continue;
            }

            ChannelHandler handler = (ChannelHandler) attach;
            handler.onSelected();
            try {
                final SocketChannel socketChannel =
                    (SocketChannel) key.channel();
                /*
                 * First deal with the newly created socket channel and notify
                 * the handler if there is an error.
                 */
                try {
                    if (!socketChannel.finishConnect()) {
                        /* Channel is not connected, continue with other channels */
                        continue;
                    }
                } catch (Throwable t) {
                    handleNioHandlerError(handler, t, socketChannel);
                }

                /*
                 * Invoke the nio handler callbacks. The callbacks should deal
                 * with errors themselves. If they throw, the error is
                 * propagated to the run loop and eventually to the fault
                 * handler of this executor.
                 */
                if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    handler.onConnected();
                }

                if ((readyOps & SelectionKey.OP_READ) != 0) {
                    handler.onRead();
                }

                if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    handler.onWrite();
                }
            } finally {
                handler.onProcessed();
            }
        }
    }

    /**
     * Rebuilds the selector after an IOException during selector select and
     * process.
     */
    private void rebuildSelector() {
        final Selector oldSelector = selector;
        final Selector newSelector;

        if (oldSelector == null) {
            throw new IllegalStateException("Previous selector is null");
        }

        try {
            newSelector = Selector.open();
        } catch (Exception e) {
            logger.log(Level.WARNING,
                       e, () -> "Cannot create a new selector");
            /* return and retry later */
            return;
        }

        /* Register all channels to the new Selector */
        int numChannels = 0;
        for (SelectionKey key: oldSelector.keys()) {
            final Object a = key.attachment();
            final SelectableChannel ch = key.channel();
            try {
                if ((!key.isValid()) || (ch.keyFor(newSelector) != null)) {
                    continue;
                }

                final int interestOps = key.interestOps();
                key.cancel();
                ch.register(newSelector, interestOps, a);
                numChannels ++;
            } catch (Exception e) {
                logger.log(Level.WARNING,
                           e,
                           () -> "Failed to re-register a channel to the " +
                           "new selector");
                if (ch instanceof ServerSocketChannel) {
                    /*
                     * We cannot simply close a server socket channel, throw an
                     * IllegalStateException up so that the whole node will be
                     * restarted
                     */
                    throw new IllegalStateException(
                        "Cannot migrate a ServerSocketChannel: " + e, e);
                }
                /*
                 * Simply close the channel, upper layer will notice and
                 * retry with another connection.
                 */
                try {
                    ch.close();
                } catch (IOException ioe) {
                    logger.log(
                        Level.INFO,
                        ioe,
                        () ->
                        "Failed to close a channel after failed to migrate");
                }
            }
        }

        selector = newSelector;

        try {
            oldSelector.close();
        } catch (Throwable t) {
            logger.log(Level.WARNING, t,
                () -> "Failed to close the old selector");
        }

        final int n = numChannels;
        logger.log(Level.INFO, () -> String.format(
            "Migrated %s channel(s) to the new selector", n));
    }

    /*
     * Handles executor exceptions.
     *
     * TODO: currently only invoked with selector exception, should use it for
     * other exceptions as well.
     */
    private void handleExecutorException(Throwable t) {
        logger.log(Level.WARNING, t,
            () -> "Unexpected exception in the executor loop");

        /*
         * Prevent possible consecutive immediate failures that lead to
         * excessive CPU consumption.
         */
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    /**
     * A default time calculator that returns a constant time for the maximum
     * execution time for non-IO tasks after each IO processing.
     */
    private class DefaultTaskTimeCalculator implements TaskTimeCalculator {

        private final long timeToRunTask;

        public DefaultTaskTimeCalculator(long timeToRunTask) {
            this.timeToRunTask = timeToRunTask;
        }

        @Override
        public long getTaskTimeNanos(long procTime) {
            return timeToRunTask;
        }
    }

    private void runTasks(long until) {
        assert inExecutorThread();

        while (true) {
            fetchScheduledTasks();

            if (taskQueue.isEmpty()) {
                return;
            }

            while (!taskQueue.isEmpty()) {
                Runnable task = taskQueue.poll();
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.log(Level.INFO, () -> String.format(
                        "An error occurs to the task %s: %s",
                        task,
                        (logger.isLoggable(Level.FINE) ?
                         CommonLoggerUtils.getStackTrace(t) : t)));
                }
                updateCurrTime();
                if (currTimeNanos >= until) {
                    return;
                }
                if (isStoppedOrAfter()) {
                    return;
                }
            }
        }
    }

    private void fetchScheduledTasks() {
        currTimeNanos = nanoTime();

        /* Check remove time interval and remove canceled before fetch */
        if (currTimeNanos - lastRemoveTimeNanos >
            (MAX_VALUE_TASK_REMOVAL_INTERVAL ?
             Long.MAX_VALUE : REMOVE_TIME_INTERVAL_NANOS)) {
            removeCancelledTasks();
            lastRemoveTimeNanos = currTimeNanos;
        }

        while (true) {
            ScheduledFutureTask<?> task = delayedQueue.peek();
            if (task == null) {
                break;
            }

            if (task.execTimeNanos > currTimeNanos) {
                break;
            }

            task = delayedQueue.poll();
            taskQueue.add(task);
        }
    }

    /**
     * Update the current time if necessary.
     *
     * The updates are relatively expensive, so only update once a while.
     */
    private void updateCurrTime() {
        if (updateCountdown == 0) {
            currTimeNanos = nanoTime();
            updateCountdown = UPDATE_INTERVAL;
        }
        updateCountdown --;
    }

    /**
     * Clean up and terminate the executor.
     */
    private void terminate() {
        assert inExecutorThread();
        if (isTerminated()) {
            return;
        }
        synchronized(this) {
            if (isTerminated()) {
                return;
            }
            state = State.STOP;
        }

        remainingTasks.addAll(taskQueue);
        remainingTasks.addAll(delayedQueue);

        closeSelector();

        synchronized(this) {
            state = State.TERMINATED;
            notifyAll();
        }

        parent.onExecutorShutdown(this, childIndex);

        logger.log(Level.FINEST, () -> String.format(
            "Executor terminated: %s", this));
    }

    /**
     * Cancel selection keys and close selector.
     */
    private void closeSelector() {
        if (isTerminated()) {
            return;
        }

        for (SelectionKey key : selector.keys()) {
            key.cancel();
            final Object attach = key.attachment();
            NioHandler handler = (NioHandler) attach;
            try {
                handleNioHandlerError(
                    handler,
                    new RuntimeException("Channel executor is shut down"),
                    key.channel());
            } catch (Throwable throwable) {
                /*
                 * Ignoring any exception since we are shutting down. Also the
                 * exception might be a rethrown one.
                 */
            }
        }

        try {
            selector.close();
        } catch (IOException e) {
            logger.log(Level.FINE, () -> String.format(
                "Error close selector: %s", e));
        }
    }

    private void handleAccept(ServerSocketChannel serverSocketChannel,
                              ChannelAccepter handler) {
        /* Accept as many channels as there is */
        while (true) {
            SocketChannel socketChannel = null;
            try {
                socketChannel = serverSocketChannel.accept();
                if (socketChannel == null) {
                    break;
                }
            } catch (Throwable t) {
                /*
                 * TODO: Currently we treat all exceptions to the server socket
                 * channel the same which is fatal and would restart the
                 * process. We should consider avoid restarting the process for
                 * some errors such as resource problems. The approach is that
                 * we log the errors and over time we will observe the types of
                 * errors that we can live through and gradually add code to
                 * handle them.
                 */
                try {
                    /*
                     * Always close the socketChannel (if any) if an exception
                     * occurs.
                     */
                    if (socketChannel != null) {
                        try {
                            socketChannel.close();
                            /*
                             * Do not need to wake up here (see the note of
                             * registerAccept) since we are not selecting right
                             * now.
                             */
                        } catch (Throwable t1) {
                            /* ignore */
                        }
                    }

                } finally {
                    handleNioHandlerError(handler, t, serverSocketChannel);
                }
                break;
            }
            handler.onAccept(socketChannel);
        }
    }

    /**
     * Handles error occurred to execute operations for a NioHandler.
     *
     * Logs the error and cancel the handler. Rethrow the error through the run
     * loop to the fault handler if necessary.
     */
    private void handleNioHandlerError(NioHandler handler,
                                       Throwable t,
                                       SelectableChannel channel) {
        /* Logging with FINE for IOException, INFO for others */
        Level level = (t instanceof IOException) ? Level.FINE : Level.INFO;
        logger.log(level, () -> String.format(
            "Executor encountered error for a channel, " +
            "channel=%s, handler=%s: %s",
            channel, handler,
            (logger.isLoggable(Level.FINE) ?
             CommonLoggerUtils.getStackTrace(t) : t)));
        try {
            handler.cancel(t);
        } finally {
            rethrowNioHandlerProcessError(t, channel);
        }
    }

    private void rethrowNioHandlerProcessError(Throwable t,
                                               SelectableChannel channel) {
        /* If we are shutting down, do not throw exceptions */
        if (isShuttingDownOrAfter()) {
            return;
        }

        if (channel instanceof ServerSocketChannel) {
            /*
             * Errors from server socket channel cannot be recovered, rethrow
             * as a runtime exception.
             */
            logger.log(Level.FINE, () -> String.format(
                "Server socket channel %s encounters an error and " +
                "cannot be recovered, re-throw", channel));
            throw new RuntimeException(
                "Cannot recover error for server socket channel");
        }

        /* IOExceptions are expected */
        if (t instanceof IOException) {
            return;
        }

        /* Any other excpetions are unexpected */
        throw new IllegalStateException(
            "Unexpected exception while processing an nio handler event", t);
    }

    private abstract class ExecutorTask<V>
        implements Callable<V>, Runnable {

        @Override
        public String toString() {
            return String.format(
                "%s@Executor(%s)", getClass().getSimpleName(), getId());
        }

        @Override
        public void run() {
            try {
                call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class DumpTask extends ExecutorTask<Void> {
        @Override
        public Void call() {
            logger.log(Level.FINEST,
                       () -> "[Dump]" + NioChannelExecutor.this.toString());
            return null;
        }
    }


    private class QuiescentMonitorTask extends ExecutorTask<Void> {

        @Override
        public Void call() {
            if (isLastPeriodQuiescent()) {
                numQuiescentPeriods.incrementAndGet();
            } else {
                numQuiescentPeriods.set(0);
            }
            final long nperiods = numQuiescentPeriods.get();
            if (nperiods >= maxNumQuiescentPeriods) {
                logger.log(Level.FINEST, () -> String.format(
                    "Executor is quiescent during last %s ms, %s",
                    nperiods * quiescentMonitorIntervalMillis,
                    NioChannelExecutor.this));
                shutdownNow();
            }
            synchronized(this) {
                notifyAll();
            }
            return null;
        }

        private boolean isLastPeriodQuiescent() {
            final boolean hasNewEvents =
                hasNewEventsLastPeriod.getAndSet(false);
            if (hasNewEvents) {
                return false;
            }
            if (!taskQueue.isEmpty()) {
                return false;
            }
            for (ScheduledFutureTask<?> t : delayedQueue) {
                if (t.isCancelled()) {
                    continue;
                }
                /* Excluding the executor task when checking for quiescent */
                if (t.getInnerTask() instanceof ExecutorTask) {
                    continue;
                }
                return false;
            }
            if (!registerQueue.isEmpty()) {
                return false;
            }
            if (!selector.keys().isEmpty()) {
                return false;
            }
            return true;
        }
    }

    private class TasksPrintTask extends ExecutorTask<String> {
        @Override
        public String call() {
            StringBuilder builder = new StringBuilder();
            builder.append("\ntaskQueue=[\n");
            for (Runnable task : taskQueue) {
                builder.append("\t").append(task.toString()).append("\n");
            }
            builder.append("]");
            builder.append("\ndelayedQueue=[\n");
            for (Future<?> task : delayedQueue) {
                if (task.isCancelled()) {
                    continue;
                }
                builder.append("\t").append(task.toString()).append("\n");
            }
            builder.append("]");
            builder.append("\nremainingTasks=[\n");
            for (Runnable task : remainingTasks) {
                builder.append("\t").append(task.toString()).append("\n");
            }
            builder.append("]");
            return builder.toString();
        }
    }

    private class PrintableFutureTask extends FutureTask<Void> {

        private final Runnable r;

        public PrintableFutureTask(Runnable r) {
            super(r, null);
            this.r = r;
        }

        @Override
        public String toString() {
            return r.toString();
        }
    }

    /* Expose the selector for testing */
    public Selector getSelector() {
        return selector;
    }
}
