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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import oracle.kv.impl.util.ObjectUtil;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementations of {@link IOBufSlice}.
 *
 * We want different implementations for input and output to achieve memory
 * access optimization. The input logic from reading socket to MessageInput
 * reads are executed in a single thread. The output logic from MessageOutput
 * writes to socket writes can be executed in different threads. Therefore
 * input buffers can be optimized without thread-safety concerns, i.e., use
 * primitive integer instead of atomic one.
 *
 * Another implementation of the slice is the one that does not need to be
 * freed.
 */
public abstract class IOBufSliceImpl extends IOBufSliceList.Entry {

    /* Enable the tracking of slice life cycle by system property. */
    private static final String TRACK_SLICE = "oracle.kv.async.bufslice.track";
    private static final boolean trackSlice = Boolean.getBoolean(TRACK_SLICE);
    private static final int MAX_NUM_SLICES_TOSTRING = 16;

    private static final Map<Integer, SliceAndDescription> allocatedSlices =
        new ConcurrentHashMap<>();
    private static final int MAX_NUM_BYTES_TOSTRING = 8;

    private final ByteBuffer buffer;

    /**
     * Returns the content of allocatedSlices.
     */
    public static String remainingSlicesToString() {
        if (!trackSlice) {
            return String.format("unavailable (enable by -D%s=true)",
                                 TRACK_SLICE);
        }
        int cnt = 0;
        StringBuilder builder = new StringBuilder("\n");
        for (SliceAndDescription val : allocatedSlices.values()) {
            builder.append("\t").append(val).append("\n");
            if (cnt++ > MAX_NUM_SLICES_TOSTRING) {
                builder.append("...");
                break;
            }
        }
        builder.append("\n");
        return builder.toString();
    }

    protected IOBufSliceImpl(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public ByteBuffer buf() {
        return buffer;
    }

    @Override
    public IOBufSliceImpl forkAndAdvance(int len, String description) {
        if (len <= 0) {
            throw new IllegalArgumentException(
                          "Forking IOBufSlice with Invalid length: " + len);
        }
        final ByteBuffer buf = buf();
        final int advancedPos = buf.position() + len;
        final ByteBuffer childBuf = buf.duplicate();
        buf.position(advancedPos);
        childBuf.limit(advancedPos);
        return forkNewSlice(childBuf, description);
    }

    @Override
    public IOBufSliceImpl forkBackwards(int len, String description) {
        if ((len <= 0) || (len > buf().position())) {
            throw new IllegalArgumentException(
                          String.format("Forking IOBufSlice " +
                                        "with Invalid length: %d, " +
                                        "current position: %d",
                                        len, buf().position()));
        }
        final ByteBuffer buf = buf();
        final int pos = buf.position() - len;
        final int lim = buf.position();
        final ByteBuffer childBuf = buf.duplicate();
        childBuf.position(pos);
        childBuf.limit(lim);
        return forkNewSlice(childBuf, description);
    }

    /**
     * Creates a new slice with the specified byte buffer.
     *
     * @param buf the byte buffer
     * @param description the description of context for new slice
     * @return buffer slice
     */
    protected abstract IOBufSliceImpl forkNewSlice(ByteBuffer buf,
                                                   String description);

    /**
     * A buffer slice created from an input buffer pool.
     *
     * When the slice is freed, the underlying byte buffer is deallocated from
     * the pool.
     */
    public static class InputPoolBufSlice
        extends ThreadUnsafeRefCntPooledBufSlice {

        private final IOBufferPool pool;

        private InputPoolBufSlice(IOBufferPool pool,
                                  ByteBuffer buffer) {
            super(buffer);
            this.pool = pool;
        }

        @Override
        public void markFree() {
            if (decRefCnt() == 0) {
                pool.deallocate(buf());
                noteFreeSlice(this);
            }
        }

        @Override
        protected @Nullable IOBufSliceImpl parent() {
            return null;
        }

        @Override
        protected IOBufSliceImpl forkNewSlice(ByteBuffer buffer,
                                              String description) {
            return noteNewSlice(new InputPoolForkedBufSlice(this, buffer),
                                description);
        }

        /**
         * Creates a buffer slice from a input pool.
         *
         * If the pool can allocate new byte buffers, a {@link
         * InputPoolBufSlice} is created; otherwise, a {@link HeapBufSlice} is
         * created.
         */
        public static IOBufSliceImpl createFromPool(IOBufferPool pool,
                                                    String description) {
            final ByteBuffer buffer = pool.allocPooled();
            if (buffer == null) {
                return new HeapBufSlice(pool.allocDiscarded());
            }
            return noteNewSlice(new InputPoolBufSlice(pool, buffer),
                                description);
        }
    }

    /**
     * A buffer slice created from an output buffer pool.
     *
     * When the slice is freed, the underlying byte buffer is deallocated from
     * the pool.
     */
    public static class OutputPoolBufSlice
        extends ThreadSafeRefCntPooledBufSlice {

        private final IOBufferPool pool;

        private OutputPoolBufSlice(IOBufferPool pool,
                                   ByteBuffer buffer) {
            super(buffer);
            this.pool = pool;
        }

        @Override
        public void markFree() {
            if (decRefCnt() == 0) {
                pool.deallocate(buf());
                noteFreeSlice(this);
            }
        }

        @Override
        protected @Nullable IOBufSliceImpl parent() {
            return null;
        }

        @Override
        protected IOBufSliceImpl forkNewSlice(ByteBuffer buffer,
                                              String description) {
            return noteNewSlice(new OutputPoolForkedBufSlice(this, buffer),
                                description);
        }

        /**
         * Creates a buffer slice from a input pool.
         *
         * If the pool can allocate new byte buffers, a {@link
         * OutputPoolBufSlice} is created; otherwise, a {@link HeapBufSlice} is
         * created.
         */
        public static IOBufSliceImpl createFromPool(IOBufferPool pool,
                                                    String description) {
            final ByteBuffer buffer = pool.allocPooled();
            if (buffer == null) {
                return new HeapBufSlice(pool.allocDiscarded());
            }
            return noteNewSlice(new OutputPoolBufSlice(pool, buffer),
                                description);
        }
    }

    /**
     * A heap allocated buffer slice that does not need to be actually freed.
     */
    public static class HeapBufSlice extends IOBufSliceImpl {

        public HeapBufSlice(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public void markFree() {
            /* do nothing */
        }

        @Override
        protected HeapBufSlice forkNewSlice(ByteBuffer buffer,
                                            String description) {
            return new HeapBufSlice(buffer);
        }

        @Override
        public String toString() {
            return BytesUtil.toString(
                       buf(), Math.min(buf().limit(), MAX_NUM_BYTES_TOSTRING));
        }

    }

    /**
     * A pooled buffer slice that needs to be freed.
     */
    private static abstract class PooledBufSlice extends IOBufSliceImpl {

        protected PooledBufSlice(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public IOBufSliceImpl forkAndAdvance(int len, String description) {
            incRefCnt();
            return super.forkAndAdvance(len, description);
        }

        @Override
        public IOBufSliceImpl forkBackwards(int len, String description) {
            incRefCnt();
            return super.forkBackwards(len, description);
        }

        @Override
        public void markFree() {
            if (decRefCnt() == 0) {
                final IOBufSliceImpl p = parent();
                if (p != null) {
                    p.markFree();
                }
                noteFreeSlice(this);
            }
        }

        @Override
        public String toString() {
            return String.format(
                "(id=%x parent=%x next=%x, ref=%d, buf=%x)%s",
                System.identityHashCode(this),
                System.identityHashCode(parent()),
                System.identityHashCode(next()),
                getRefCnt(),
                System.identityHashCode(buf()),
                BytesUtil.toString(
                    buf(),
                    Math.min(buf().limit(), MAX_NUM_BYTES_TOSTRING)));
        }

        /**
         * Returns the parent of the buf slice.
         *
         * @return the parent, which may be null
         */
        protected abstract @Nullable IOBufSliceImpl parent();

        /**
         * Increments the reference count.
         */
        protected abstract void incRefCnt();

        /**
         * Decrements the reference count.
         *
         * @return the resulted reference count
         */
        protected abstract int decRefCnt();

        /**
         * Returns the reference count.
         */
        protected abstract int getRefCnt();

    }

    /**
     * A pooled buffer slice with thread-unsafe reference counting.
     */
    private static abstract class ThreadUnsafeRefCntPooledBufSlice
        extends PooledBufSlice {

        protected int refcnt;

        protected ThreadUnsafeRefCntPooledBufSlice(ByteBuffer buffer) {
            super(buffer);
            this.refcnt = 1;
        }

        @Override
        protected void incRefCnt() {
            ++refcnt;
        }

        @Override
        protected int decRefCnt() {
            if (refcnt <= 0) {
                throw new IllegalStateException(
                              String.format("Invalid decRefCnt() on %s",
                                            this));
            }
            return --refcnt;
        }

        @Override
        protected int getRefCnt() {
            return refcnt;
        }
    }

    /**
     * A pooled buffer slice with thread-safe reference counting.
     */
    private static abstract class ThreadSafeRefCntPooledBufSlice
        extends PooledBufSlice {

        /*
         * Use a field updater to avoid creating an AtomicInteger for each
         * instance
         */
        private static final
            AtomicIntegerFieldUpdater<ThreadSafeRefCntPooledBufSlice>
            refCntUpdater = AtomicIntegerFieldUpdater.newUpdater(
                                ThreadSafeRefCntPooledBufSlice.class,
                                "refcnt");

        @SuppressWarnings("unused")
        protected volatile int refcnt;

        protected ThreadSafeRefCntPooledBufSlice(ByteBuffer buffer) {
            super(buffer);
            refCntUpdater.set(this, 1);
        }

        @Override
        protected void incRefCnt() {
            refCntUpdater.incrementAndGet(this);
        }

        @Override
        protected int decRefCnt() {
            final int val = refCntUpdater.decrementAndGet(this);
            if (val < 0) {
                throw new IllegalStateException(
                              String.format(
                                  "Reference count value %d " +
                                  "less than zero for %s",
                                  val, getClass().getSimpleName()));
            }
            return val;
        }

        @Override
        protected int getRefCnt() {
            return refCntUpdater.get(this);
        }
    }

    /**
     * A buffer slice forked from a {@link InputPooledBufSlice} or its
     * offspring.
     */
    private static class InputPoolForkedBufSlice
        extends ThreadUnsafeRefCntPooledBufSlice {

        private final IOBufSliceImpl parent;

        protected InputPoolForkedBufSlice(IOBufSliceImpl parent,
                                          ByteBuffer buffer) {
            super(buffer);
            ObjectUtil.checkNull("parent", parent);
            this.parent = parent;
        }

        @Override
        protected @Nullable IOBufSliceImpl parent() {
            return parent;
        }

        @Override
        protected IOBufSliceImpl forkNewSlice(ByteBuffer buffer,
                                              String description) {
            return noteNewSlice(new InputPoolForkedBufSlice(this, buffer),
                                description);
        }

    }

    /**
     * A buffer slice forked from a {@link OutputPooledBufSlice} or its
     * offspring.
     */
    private static class OutputPoolForkedBufSlice
        extends ThreadSafeRefCntPooledBufSlice {

        private final IOBufSliceImpl parent;

        protected OutputPoolForkedBufSlice(IOBufSliceImpl parent,
                                           ByteBuffer buffer) {
            super(buffer);
            ObjectUtil.checkNull("parent", parent);
            this.parent = parent;
        }

        @Override
        protected @Nullable IOBufSliceImpl parent() {
            return parent;
        }

        @Override
        protected IOBufSliceImpl forkNewSlice(ByteBuffer buffer,
                                              String description) {
            return noteNewSlice(new OutputPoolForkedBufSlice(this, buffer),
                                description);
        }
    }

    private static IOBufSliceImpl noteNewSlice(IOBufSliceImpl slice,
                                               String description) {
        if (trackSlice) {
            allocatedSlices.put(
                System.identityHashCode(slice),
                new SliceAndDescription(slice, description));
        }
        return slice;
    }

    private static void noteFreeSlice(IOBufSliceImpl slice) {
        if (trackSlice) {
            allocatedSlices.remove(System.identityHashCode(slice));
        }
    }

    private static class SliceAndDescription {

        private final IOBufSliceImpl slice;
        private final String description;

        private SliceAndDescription(IOBufSliceImpl slice,
                                    String description) {
            this.slice = slice;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", description, slice);
        }
    }
}
