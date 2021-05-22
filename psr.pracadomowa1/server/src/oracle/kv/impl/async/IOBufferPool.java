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

package oracle.kv.impl.async;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.utilint.LongAvgRate;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A byte buffer pool to reduce the allocation pressure of input and output
 * buffers.
 *
 * The pool employs a leaking detection mechanism. For each pool we constantly
 * collect the min of the number of allocated-but-not-deallocated over a
 * period. If the min is zero, then we are sure there is no leak before the
 * time of that period, otherwise, there is a possibility of leaking and we
 * print the stats to warn.
 *
 * TODO: Currently we do not discard and gc byte buffers in the pool once they
 * are created. This is fine since we have a limited amount of buffers in the
 * pool. If this becomes a problem in the future, we will want to discard and
 * them. However, when discarded, these buffers will become tenured generation
 * garbage, which may need some thinking on the impact of gc.
 */
public class IOBufferPool {

    /* The logger for the leak detection warning message. */
    private static volatile @Nullable Logger logger;
    /* Enable the logging of leak detection by system property. */
    private static final String WARN_LEAK = "oracle.kv.async.bufpool.warn.leak";
    private static final boolean warnLeak = Boolean.getBoolean(WARN_LEAK);
    /* The timer for all pools to collect stats and log warning. */
    private static final Timer timer =
        new Timer("IOBufferPool leak detection", true /* isDaemon */);
    /* The leak detection period */
    private static final long detectionPeriodMillis = 1000;
    /* The warning logging period */
    private static final int numPeriodsPerLogging = 60;

    /* The property to enable pooling */
    private static final String BUFPOOL_DISABLED =
        "oracle.kv.async.bufpool.disabled";
    private static final boolean bufPoolDisabled =
        Boolean.getBoolean(BUFPOOL_DISABLED);

    /* Properties for setting pool buffer size */
    private static final String CHANNEL_INPUT_BUF_SIZE =
        "oracle.kv.async.bufpool.channelinput.bufsize";
    private static final int CHANNEL_INPUT_BUF_SIZE_DEFAULT = 4096;
    private static final int channelInputBufSize = Integer.getInteger(
            CHANNEL_INPUT_BUF_SIZE, CHANNEL_INPUT_BUF_SIZE_DEFAULT);

    private static final String MESSAGE_OUTPUT_BUF_SIZE =
        "oracle.kv.async.bufpool.messageoutput.bufsize";
    private static final int MESSAGE_OUTPUT_BUF_SIZE_DEFAULT = 128;
    private static final int messageOutputBufSize = Integer.getInteger(
            MESSAGE_OUTPUT_BUF_SIZE, MESSAGE_OUTPUT_BUF_SIZE_DEFAULT);

    private static final String CHANNEL_OUTPUT_BUF_SIZE =
        "oracle.kv.async.bufpool.channeloutput.bufsize";
    private static final int CHANNEL_OUTPUT_BUF_SIZE_DEFAULT = 64;
    private static final int channelOutputBufSize = Integer.getInteger(
            CHANNEL_OUTPUT_BUF_SIZE, CHANNEL_OUTPUT_BUF_SIZE_DEFAULT);

    /*
     * Properties for pool size.
     *
     * Currently, the pool will never shrink after it is expanded. We limit the
     * size of the pool with a maximum pool size, which is the min of a fixed
     * size and a certain percentage of the JVM memory.
     *
     * Alternatively, we could add a mechanism that shrinks the pool when most
     * of the free buffers are not likely to be used. However, shrinking means
     * throwing the buffers as garbage which may likely to be of long-term.
     * Therefore we will need an algorithm to make sure the shrink operation
     * will not burden the garbage collector too much which is difficult.
     * Currently we do not implement these mechansims.
     */
    private static final String IO_BUFPOOL_SIZE_HEAP_PERCENT =
        "oracle.kv.async.bufpool.size.heap.percentage";
    private static final int IO_BUFPOOL_SIZE_HEAP_PERCENT_DEFAULT = 1;
    private static final long IO_BUFPOOL_SIZE_MAX_DEFAULT = 1024 * 1024 * 1024;
    private static final int poolSizeHeapPercent = Integer.getInteger(
            IO_BUFPOOL_SIZE_HEAP_PERCENT, IO_BUFPOOL_SIZE_HEAP_PERCENT_DEFAULT);
    private static final long maxPoolBytes =
        Math.min((Runtime.getRuntime().maxMemory() * poolSizeHeapPercent) /
                 100,
                 IO_BUFPOOL_SIZE_MAX_DEFAULT);

    /* The buffer pool for all channel input */
    public static final IOBufferPool CHNL_IN_POOL =
        new IOBufferPool("Channel input", channelInputBufSize);
    /* The buffer pool for all message output */
    public static final IOBufferPool MESG_OUT_POOL =
        new IOBufferPool("Message output", messageOutputBufSize);
    /* The buffer pool for all channel output */
    public static final IOBufferPool CHNL_OUT_POOL =
        new IOBufferPool("Channel output", channelOutputBufSize);

    /* Name of the pool */
    private final String name;
    /* Size of the pool buffers */
    protected final int bufsize;
    /* Buffers that can be allocated for use */
    private final Deque<ByteBuffer> freeBufs = new ConcurrentLinkedDeque<>();
    /* Maximum amount of buffers to allocate */
    private final long maxPoolSize;
    /*
     * Current amount of buffers allocated that can be put in freeBufs,
     * currPoolSize <= maxPoolSize
     */
    private final AtomicInteger currPoolSize = new AtomicInteger(0);
    /*
     * The number of buffers allocated in use, i.e., not in freeBufs. Total
     * number of buffers not garbage = inUse + freeBufs.size(). Used for leak
     * detection
     */
    private final AtomicInteger inUse = new AtomicInteger(0);
    /*
     * The min of the number of in use during a period, used for leak detection
     */
    private final AtomicInteger minInUse = new AtomicInteger(0);

    public static void setLogger(Logger l) {
        if (logger != null) {
            throw new IllegalStateException("Logger already set");
        }
        logger = l;
        if (warnLeak) {
            logger.log(Level.INFO, "Warning enabled for IO buffer pool leak");
        }
    }

    /**
     * Constructs the pool.
     */
    protected IOBufferPool(String name, int bufsize) {
        this.name = name;
        this.bufsize = bufsize;
        this.maxPoolSize = maxPoolBytes / bufsize;
        timer.schedule(new LeakDetectionTask(), 0, detectionPeriodMillis);
    }

    /**
     * Allocates a byte buffer from the pool.
     *
     * The byte buffer should be deallocated after use.
     *
     * @return the byte buffer, null if the pool cannot allocate any
     */
    @Nullable ByteBuffer allocPooled() {
        if (bufPoolDisabled) {
            return allocDiscarded();
        }
        final ByteBuffer buf = allocate();
        if (buf != null) {
            inUse.incrementAndGet();
        }
        return buf;
    }

    private @Nullable ByteBuffer allocate() {
        while (true) {
            ByteBuffer buf = freeBufs.poll();
            if (buf != null) {
                return buf;
            }
            final int size = currPoolSize.get();
            if (size >= maxPoolSize) {
                return null;
            }
            if (currPoolSize.compareAndSet(size, size + 1)) {
                return ByteBuffer.allocate(bufsize);
            }
        }
    }

    /**
     * Allocates a byte buffer from the heap.
     *
     * The byte buffer should not be deallocated after use.
     *
     * @return the byte buffer
     */
    ByteBuffer allocDiscarded() {
        return ByteBuffer.allocate(bufsize);
    }

    /**
     * Deallocates a byte buffer.
     *
     * @param buffer the byte buffer
     */
    void deallocate(ByteBuffer buffer) {
        if (bufPoolDisabled) {
            return;
        }
        buffer.clear();
        freeBufs.push(buffer);
        /* Update for leak detection */
        final int val = inUse.decrementAndGet();
        updateMinInUse(val);
    }

    private void updateMinInUse(int val) {
        minInUse.updateAndGet(curr -> Math.min(curr, val));
    }

    /**
     * The task to collect the stats and logs warning.
     */
    private class LeakDetectionTask extends TimerTask {

        private long numPeriodsLeaking;
        private final LongAvgRate leakingRate =
            new LongAvgRate("leaking rate",
                            detectionPeriodMillis,
                            TimeUnit.SECONDS);

        @Override
        public void run() {
            int min = getMinNumInUse();
            if (min == 0) {
                /*
                 * We have a moment in the last period that everything got
                 * deallocated. We can be certain there is no leak.
                 */
                numPeriodsLeaking = 0;
                return;
            }

            numPeriodsLeaking ++;
            leakingRate.add(min, System.currentTimeMillis());
            if ((numPeriodsLeaking % numPeriodsPerLogging == 0) &&
                (logger != null) &&
                (warnLeak)) {
                /*
                 * When leak happens, some buffers are never returned to the
                 * freeBufs and will be throw out as garbage. Therefore, the
                 * impact of a leak is as if the buffer pool mechanism is not
                 * in place for some buffer.
                 *
                 * TODO: Notify with the stats mechanism (the JVMStats class)
                 * instead of logging.
                 */
                logger.log(Level.INFO,
                           () ->
                           String.format(
                               "Possible buffer pool [%s] leaking " +
                               "for the last %s seconds, " +
                               "min number of buffers in use " +
                               "during the last %s second: %s, " +
                               "leaking rate: %s" +
                               "remaining slices: %s",
                               name,
                               (numPeriodsLeaking * detectionPeriodMillis) /
                               1000,
                               detectionPeriodMillis / 1000,
                               min,
                               leakingRate.get(),
                               IOBufSliceImpl.remainingSlicesToString()));
            }
        }
    }

    /**
     * Gets the min of number of allocated-but-not-deallocated buffers during
     * last period.
     *
     * Returns the min number during last period and updates the min stats with
     * the currently in use stats for the next period.
     */
    public int getMinNumInUse() {
        final int min = minInUse.get();
        final int use = inUse.get();
        updateMinInUse(use);
        return min;
    }

    /**
     * Returns the current number of allocated-but-not-deallocated buffers.
     *
     * For testing.
     */
    public int getNumInUse() {
        return inUse.get();
    }

    /**
     * Clears the number for tests.
     */
    public void clearUse() {
        minInUse.set(0);
        inUse.set(0);
        freeBufs.clear();
    }
}
