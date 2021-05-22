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

package oracle.kv.impl.xregion.agent.mrt;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.xregion.agent.RegionAgentStatus.INITIALIZING_TABLES;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.FaultException;
import oracle.kv.KVStore;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.pubsub.NoSQLSubscriptionImpl;
import oracle.kv.impl.pubsub.PublishingUnit;
import oracle.kv.impl.test.ExceptionTestHook;
import oracle.kv.impl.test.ExceptionTestHookExecute;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.util.FormatUtils;
import oracle.kv.impl.util.KVThreadFactory;
import oracle.kv.impl.util.RateLimitingLogger;
import oracle.kv.impl.util.server.LoggerUtils;
import oracle.kv.impl.xregion.agent.BaseRegionAgentSubscriber;
import oracle.kv.impl.xregion.agent.RegionAgentThread;
import oracle.kv.impl.xregion.agent.TablePollingThread;
import oracle.kv.impl.xregion.agent.TargetTableEvolveException;
import oracle.kv.impl.xregion.service.JsonConfig;
import oracle.kv.impl.xregion.service.MRTableMetrics;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.pubsub.NoSQLStreamMode;
import oracle.kv.pubsub.NoSQLSubscription;
import oracle.kv.pubsub.NoSQLSubscriptionConfig;
import oracle.kv.pubsub.ShardTimeoutException;
import oracle.kv.pubsub.StreamOperation;
import oracle.kv.pubsub.StreamOperation.SequenceId;
import oracle.kv.pubsub.StreamPosition;
import oracle.kv.pubsub.SubscriptionInsufficientLogException;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.WriteOptions;

import com.sleepycat.je.dbi.TTL;

import org.reactivestreams.Subscription;

/**
 * Object that represents the subscriber of region agent for multi-region
 * table.
 */
public class MRTSubscriber extends BaseRegionAgentSubscriber {

    /**
     * A test hook that can be used to inject exceptions after async put or
     * delete calls. Called with the stream operation that produced the call.
     * For testing.
     */
    public static volatile ExceptionTestHook<StreamOperation, Exception>
        asyncExceptionHook;

    /**
     * A test hook that can be used by test to make expired put operations
     */
    public static volatile TestHook<Row> expiredPutHook = null;

    /* wait before retry in ms */
    private static final long WAIT_BEFORE_RETRY_MS = 1000;

    /* metadata management */
    private final ServiceMDMan mdMan;

    /* instance of a table api to target store */
    private final TableAPIImpl tgtTableAPI;

    /** The name of the target store. */
    private final String tgtStoreName;

    /* source region name */
    private final String srcRegion;

    /*
     * TODO: Provide a way to make sure that operations have been sync'ed to
     * disk before creating a checkpoint when the operation durability is less
     * than COMMIT_SYNC.
     */
    /** Write options to persist writes to target region. */
    private final WriteOptions writeOptions;

    /**
     * A scheduled executor service for retrying operations after fault
     * exceptions and to do checkpoints as needed when there is no activity.
     */
    private final ScheduledExecutorService executor =
        Executors.newScheduledThreadPool(1, new MRTSubscriberThreadFactory());

    /**
     * An array of queues of StreamOperations, with array elements indexed by
     * the hash of the operation's primary key modulo the number of queues.
     * These queues are used to make sure that operations on the same key are
     * performed in order.
     *
     * Since Java guarantees that access to an immutable object stored in a
     * final field is thread safe after construction, access to the array
     * elements is safe because they are only set in the constructor. But that
     * guarantee is not extended to the contents of the array elements, so
     * synchronize on each element when accessing it. All elements are of type
     * {@code Queue<StreamOperations>}, but Java doesn't allow parameterized
     * types for array elements.
     */
    private final Queue<?>[] pendingOperations;

    /** Whether a checkpoint is currently underway. */
    private final AtomicBoolean checkpointUnderway = new AtomicBoolean();

    /**
     * Information about an operation that should remain pending while earlier
     * operations are completed so that it can be used for a checkpoint.
     *
     * The subscriber needs to make sure that all operations in the stream
     * prior to the checkpoint have been performed to the local store first
     * before performing the checkpoint. To do this, the subscriber selects an
     * operation to be checkpointed, records its stream position, and delays
     * performing it until all prior operations have been performed.
     */
    private final AtomicReference<PendingCheckpointInfo> pendingCheckpoint =
        new AtomicReference<>();

    /**
     * Whether a call to doCheckpointIfNeeded is currently scheduled. A new
     * call is scheduled when a checkpoint has been completed, but this flag is
     * needed to coordinate between checkpoints initiated by
     * doCheckpointIfNeeded calls and ones initiated by onNext calls.
     */
    private final AtomicBoolean scheduledDoCheckpointIfNeeded =
        new AtomicBoolean();

    /** Whether the subscriber has been shutdown. */
    private volatile boolean shutdown;

    /** # of concurrent stream ops */
    private final int numConcurrentStreamOps;

    /* rate limiting logger */
 	private final RateLimitingLogger<String> rlLogger;

    public MRTSubscriber(RegionAgentThread agentThread,
                         NoSQLSubscriptionConfig config,
                         KVStore tgtKVS,
                         Logger logger) {
        super(agentThread, config, logger);

        srcRegion = agentThread.getSourceRegion().getName();
        mdMan = agentThread.getMdMan();
        writeOptions =
            new WriteOptions(mdMan.getJsonConf().durabilitySetting(), 0,
                             MILLISECONDS)
                .setUpdateTTL(true); /* allow update TTL */
        tgtTableAPI = ((KVStoreImpl) tgtKVS).getTableAPIImpl();
        tgtStoreName = ((KVStoreImpl) tgtKVS).getTopology().getKVStoreName();
        numConcurrentStreamOps = agentThread.getNumConcurrentStreamOps();
        pendingOperations = new Queue<?>[numConcurrentStreamOps];
        Arrays.setAll(pendingOperations, i -> new ArrayDeque<>());
        rlLogger = agentThread.getRlLogger();
        logger.info(lm("Subscriber created with config " + config));
    }

    @Override
    public void onSubscribe(Subscription s) {
        super.onSubscribe(s);

        if (!scheduleDoCheckpointIfNeeded()) {
            throw new IllegalStateException(
                "Problem scheduling initial checkpoint if needed task");
        }
        final NoSQLSubscriptionImpl subImpl =
            (NoSQLSubscriptionImpl) getSubscription();
        logger.fine(lm("Streaming tables " + config.getTables() +
                       " from " + subImpl.getCoveredShards() +
                       " from position: " + subImpl.getInitPos()));
    }

    /**
     * Schedule a doCheckpointIfNeeded call if one is not already scheduled,
     * and return true if newly scheduled.
     */
    private boolean scheduleDoCheckpointIfNeeded() {
        if (!scheduledDoCheckpointIfNeeded.compareAndSet(false, true)) {
            return false;
        }
        schedule(this::doCheckpointIfNeeded, ckptIntvSecs, SECONDS);
        return true;
    }

    /** Schedule an operation, handling exceptions and shutdowns */
    private void schedule(Runnable task, long time, TimeUnit units) {
        try {
            executor.schedule(
                () -> {
                    if (shutdown) {
                        return;
                    }
                    try {
                        task.run();
                    } catch (Throwable t) {
                        /* Handle unexpected exceptions */
                        operationFailed(t);
                    }
                },
                time, units);
        } catch (RejectedExecutionException e) {
            if (!shutdown) {
                throw e;
            }
        }
    }

    @Override
    public void onNext(StreamOperation source) {

        /* Make sure type is valid */
        final StreamOperation.Type type = source.getType();
        if (!type.equals(StreamOperation.Type.PUT) &&
            !type.equals(StreamOperation.Type.DELETE)) {
            throw new IllegalArgumentException(
                "Only PUT or DELETE is supported, invalid type " + type);
        }

        /* add table metrics if not existing */
        final Row row = type.equals(StreamOperation.Type.PUT) ?
            source.asPut().getRow() :
            source.asDelete().getPrimaryKey();
        parent.getAddTable(getTableName(row), getTableId(row));

        /* update last message time */
        getMetrics().setLastMsgTime(source.getRepGroupId(),
                                    System.currentTimeMillis());

        /*
         * Record the operation in the queue, checking first to see if the
         * queue was empty
         */
        final Queue<StreamOperation> queue = getQueue(source);
        final boolean wasEmpty;
        synchronized (queue) {
            wasEmpty = queue.isEmpty();
            queue.add(source);
        }

        /* Wait if the operation is not at the head of the queue */
        if (!wasEmpty) {
            getMetrics().incrNumOpsQueued();
            logger.finest(() -> lm("Queued operation: " + source));
            return;
        }

        processOperation(source, 1);
    }

    /** Process a stream operation that is at the head of the queue. */
    private void processOperation(StreamOperation source, int attempts) {

        /* Check for existing pending checkpoint or add if needed */
        final PendingCheckpointInfo pendingInfo;
        final PendingCheckpointInfo prevPendingInfo = pendingCheckpoint.get();
        if ((prevPendingInfo != null) || !needCkpt()) {
            pendingInfo = prevPendingInfo;
        } else {
            final PendingCheckpointInfo newPendingInfo =
                new PendingCheckpointInfo(
                    source, getSubscription().getCurrentPosition());
            if (pendingCheckpoint.compareAndSet(null, newPendingInfo)) {
                pendingInfo = newPendingInfo;
                logger.finest(() -> lm("Pending checkpoint added: " +
                                       newPendingInfo));
            } else {
                pendingInfo = null;
            }
        }

        /* Check pending checkpoint if it is for this operation */
        if ((pendingInfo != null) && (pendingInfo.streamOperation == source)) {
            if (!pendingInfo.checkpointReady()) {
                logger.finest(() -> lm("Pending checkpoint not ready: " +
                                       pendingInfo));
                return;
            }
            if (!pendingInfo.active.compareAndSet(false, true)) {
                logger.finest(
                    () -> lm("Pending checkpoint already active: " +
                             pendingInfo));
                return;
            }
            logger.finest(() -> lm("Handling ready pending checkpoint: " +
                                   pendingInfo));
        }

        final StreamOperation.Type type = source.getType();
        Row srcRow = null;
        final Row tgtRow;
        try {
            /* get source row */
            if (type.equals(StreamOperation.Type.PUT)) {
                srcRow = source.asPut().getRow();
            } else {
                srcRow = source.asDelete().getPrimaryKey();
            }

            /* convert to target row */
            tgtRow = transformRow(srcRow, type, true);
            if (tgtRow == null) {
                /* Couldn't process operation, so operation is done */
                operationCompleted(source);
                return;
            }
        } catch (FaultException fe) {
            /* store may be down, retry, wait */
            final String tableName =
                (srcRow != null) ? "=" + getTableName(srcRow) : "";
            final String msg = "Cannot write to table" + tableName +
                               " at target store=" + tgtStoreName +
                               " after # of attempts=" + attempts +
                               ", " + fe;
            /* avoid repeated warnings for same table and exception class */
            rlLogger.log(tableName + fe.getFaultClassName(),
                         Level.WARNING, lm(msg));

            scheduleRetry(source, attempts);
            /* Operation is not done, so don't request another operation */
            return;
        } catch (MetadataNotFoundException mnfe) {
            /*
             * drop table in admin is not synced with agent, and the
             * table may have been already dropped before the table
             * is removed from the underlying stream, in this case,
             * the agent may see table not found exception.
             */
            /*
             * TODO: What if this RN hasn't gotten the metadata for this table
             * yet?
             */
            logger.finest(lm("table already dropped, " + mnfe.getMessage()));
            /* Table is gone, so operation is done */
            operationCompleted(source);
            return;
        }

        /* persistence */
        writeTarget(source, srcRow, tgtRow, attempts);
    }

    /**
     * Schedule a retry, but skipping the operation if it is associated with a
     * pending checkpoint and is already active.
     */
    private void scheduleRetry(StreamOperation source, int attempts) {
        final PendingCheckpointInfo pendingInfo = pendingCheckpoint.get();
        if ((pendingInfo != null) && (pendingInfo.streamOperation == source)) {
            pendingInfo.active.set(false);
        }

        /*
         * TODO: processOperation will also transform the row again. We might
         * want to optimize that out later in cases where it isn't needed.
         */
        schedule(() -> processOperation(source, attempts + 1),
                 WAIT_BEFORE_RETRY_MS, MILLISECONDS);
    }

    private String getTableName(Row row) {
        return row.getTable().getFullNamespaceName();
    }

    private long getTableId(Row row) {
        return ((TableImpl) row.getTable()).getId();
    }

    /**
     * Return the queue for the specified stream operation. Use the same queue
     * for operations associated with the same key to insure the proper
     * ordering of operations.
     */
    private Queue<StreamOperation> getQueue(StreamOperation op) {

        /* Get the primary key associated with the operation */
        final PrimaryKey key =
            (op.getType() == StreamOperation.Type.DELETE) ?
            op.asDelete().getPrimaryKey() :
            op.asPut().getRow().createPrimaryKey();

        return getQueue(getKeyQueueIndex(key, numConcurrentStreamOps));
    }

    /** Return the queue for the specified index. */
    private Queue<StreamOperation> getQueue(int index) {
        @SuppressWarnings("unchecked")
        final Queue<StreamOperation> queue =
            (Queue<StreamOperation>) pendingOperations[index];
        return queue;
    }

    /**
     * Computes the index of the queue used for the specified key. This method
     * is public for use in tests.
     *
     * @param key the key
     * @param numConcurrentStreamOps number of concurrent stream operations
     * @return the index of the queue for the key
     */
    public static int getKeyQueueIndex(PrimaryKey key,
                                       int numConcurrentStreamOps) {

        /*
         * Compute the index of the queue associated with the key by hashing
         * the key and taking the value modulo the number of queues. Note that
         * Math.floorMod is like the '%' modulus operator, but, for a positive
         * modulus, always returns a value between 0 and mod-1 inclusive.
         */
        final int hash = key.hashCode();
        return Math.floorMod(hash, numConcurrentStreamOps);
    }

    /**
     * Unit test only
     */
    public static int getKeyQueueIndex(PrimaryKey key) {
        return getKeyQueueIndex(key,
                                JsonConfig.DEFAULT_MAX_CONCURRENT_STREAM_OPS);
    }

    /**
     * Log an unexpected exception that occurs during an operation and that
     * means that the subscription should be canceled.
     */
    private void operationFailed(Throwable t) {
        logger.warning(lm("Cannot process operation, " +
                          t.getMessage() +
                          ", call stack\n" +
                          LoggerUtils.getStackTrace(t)));
        final Subscription subscription = getSubscription();
        if (subscription != null) {
            subscription.cancel();
        }
    }

    /**
     * Call this method when a stream operation has been completed, so we can
     * request more and possibly run another queued operation.
     */
    private void operationCompleted(StreamOperation op) {
        logger.finest(() -> lm("Operation completed: " + op));
        final Queue<StreamOperation> queue = getQueue(op);
        StreamOperation next;
        synchronized (queue) {
            final StreamOperation top = queue.poll();
            if (!op.equals(top)) {
                throw new IllegalStateException(
                    "Expected top of queue to be " + op + ", found " + top);
            }
            next = queue.peek();
        }

        /*
         * Check if the completion of this operation means that a pending
         * operation is now ready
         */
        final PendingCheckpointInfo pendingInfo = pendingCheckpoint.get();
        if (pendingInfo != null) {
            if (pendingInfo.streamOperation == op) {
                if (!pendingCheckpoint.compareAndSet(pendingInfo, null)) {
                    throw new IllegalStateException(
                        "Unable to clear completed pending checkpoint");
                }
                logger.finest(() -> lm("Pending checkpoint completed: " +
                                       pendingInfo));
            } else if (pendingInfo.checkpointReadyAfterOpCompleted(op)) {
                logger.finest(() -> lm("Processing pending checkpoint: " +
                                       pendingInfo));
                processOperation(pendingInfo.streamOperation, 1);
                if (pendingInfo.streamOperation == next) {
                    /* Already handled */
                    next = null;
                }
            }
        }

        /* Process next operation in the queue, if any. */
        if (next != null) {
            processOperation(next, 1);
        }

        /* Regardless, request a new operation to replace the completed one */
        getSubscription().request(1);
    }

    private void processSILE() {
        try {
            /* notify agent to initialize all mr tables from source */
            parent.initFromRegion();
        } catch (InterruptedException ie) {
            if (!parent.isShutdownRequested()) {
                logger.warning(lm("interrupted in queuing a msg to agent to " +
                                  "initialize\n" +
                                  LoggerUtils.getStackTrace(ie)));
            }
        }
    }

    @Override
    public void onError(Throwable t) {

        /* remember the cause */
        cause = t;
        if (t instanceof SubscriptionInsufficientLogException) {
            /*
             * Stream failed or cannot be created because of insufficient log,
             * agent to initialize all tables from that region
             */
            logger.warning(lm("Agent to initialize region=" + srcRegion +
                              " because " + t.getMessage()));
            processSILE();
            return;
        }
        if (t instanceof PublishingUnit.EmptyStreamExpireException) {
            final PublishingUnit.EmptyStreamExpireException ese =
                (PublishingUnit.EmptyStreamExpireException) t;
            logger.info(lm("stream (sid=" + ese.getSid() + ") expires " +
                           "because it is empty for " +
                           getSubscriptionConfig().getEmptyStreamSecs() +
                           " seconds, no restart."));
            return;
        }

        /* all subscribed MR tables */
        final Set<String> tbs = mdMan.getMRTNames();
        final NoSQLSubscription subscription = getSubscription();
        if (subscription == null) {
            logger.warning(lm("Stream is not created, agent to create " +
                              "a new one for table=" + tbs +
                              ", " + getError(t)));
        } else {
            logger.warning(lm("Running stream shut down due to error, " +
                              "agent to create a new one for tables " + tbs +
                              ", " + getError(t)));
        }

        try {
            /* retry with the configured stream mode */
            final NoSQLStreamMode mode = config.getStreamMode();
            if (RegionAgentThread.getInitTableStreamMode().equals(mode)) {
                logger.info(lm("To re-initialize from region=" + srcRegion));
                parent.initFromRegion();
                return;
            }
            logger.info(lm("To resume the stream from checkpoint"));
            parent.createStream(tbs.toArray(new String[]{}));
        } catch (InterruptedException e) {
            if (!parent.isShutdownRequested()) {
                logger.warning(lm("interrupted in queuing a msg to agent to " +
                                  "create a new stream"));
            }
        }
    }

    @Override
    public void onWarn(Throwable t) {
        String warning = t.getMessage();
        if (t instanceof ShardTimeoutException) {
            warning += ", last message time=" +
                       formatTime(((ShardTimeoutException) t).getLastMsgTime());
        } else {
            warning += ", stack:\n" + LoggerUtils.getStackTrace(t);
        }
        logger.warning(lm(warning));
    }

    @Override
    public void onChangeResult(StreamPosition pos, Throwable exp) {
        super.onChangeResult(pos, exp);
        if (exp != null) {
            logger.warning(lm("Fail to change the stream, cause=" +
                              exp.getClass().getCanonicalName() +
                              ", " + exp.getMessage() +
                              (logger.isLoggable(Level.FINE) ?
                                  LoggerUtils.getStackTrace(exp) : "")));
            return;
        }
        logger.fine(() -> "Succeed to change the stream at position=" + pos);
    }

    @Override
    public void onCheckpointComplete(StreamPosition sp, Throwable failure) {

        /*
         * TODO: Consider storing the time and number of operations at the
         * start of the checkpoint, not the end, so that the time and number of
         * operations between checkpoints isn't extended by what happens while
         * the checkpoint is underway.
         */
        lastCkptStreamedOps = getMetrics().getTotalStreamOps();
        lastCkptTimeMs = System.currentTimeMillis();
        getMetrics().incrNumCheckpoints();
        checkpointUnderway.set(false);
        scheduleDoCheckpointIfNeeded();
        if (failure == null) {
            ckptPos = sp;
            logger.fine(() -> lm("Successful checkpoint at " + sp));
            return;
        }
        final Throwable exp = failure.getCause();
        logger.fine(lm("Skip checkpoint at " + sp + ", " +
                       failure.getMessage() +
                       (exp != null ?
                        "\nCause: " + exp.getMessage() + "\n" +
                        LoggerUtils.getStackTrace(exp) :
                        "")));
    }

    /**
     * Shut down the retry executor.
     */
    @Override
    public void shutdown() {
        shutdown = true;
        super.shutdown();
        executor.shutdown();
    }

    @Override
    public long getInitialRequestSize() {
        return numConcurrentStreamOps;
    }

    /**
     * Transforms the row from source region to a row in the target region.
     * The transformation includes schema reconciliation and region id
     * translation.
     *
     * @param srcRow row from source region
     * @param type   operation type
     * @param stream true if row from stream, false if from table transfer
     * @return row of target region or null if the row cannot be transformed
     */
    Row transformRow(Row srcRow, StreamOperation.Type type, boolean stream) {

        /* get target table */
        if (tgtTableAPI == null) {
            throw new IllegalStateException("For MRT subscriber, the target " +
                                            "table API cannot be null");
        }

        final String tableName = getTableName(srcRow);
        final Table target = mdMan.getMRT(tableName);
        if (target == null) {
            final String err = "Cannot convert row from source=" + srcRegion +
                               " because table=" + tableName +
                               " not found in target";
            rlLogger.log(err, Level.WARNING, lm(err));
            return null;
        }
        /* convert to target row */
        final Row tgtRow;
        try {
            tgtRow = convertRow(srcRow, target, type);
        } catch (IllegalArgumentException exp) {
            final String tbName = getTableName(srcRow);
            final String err =
                "Cannot convert row from table=" + tbName +
                ", source region=" + srcRegion + ", " +
                "skip writing to target, " + exp.getMessage();
            rlLogger.log(err, Level.WARNING, lm(err));
            getMetrics().getTableMetrics(tbName).incrIncompatibleRows(1);
            return null;
        }

        /* translate the region id */
        int srcRid;
        try {
            srcRid = ((RowImpl) srcRow).getRegionId();
        } catch (UnsupportedOperationException uoe) {
            final String err = "Row may not be from a multi-region table " +
                               ", error=" + uoe.getMessage();
            rlLogger.log(tableName + err + uoe.getClass().getName(),
                         Level.WARNING, lm(err));
            logger.warning(lm(err));
            return null;
        }

        if (srcRid == Region.NULL_REGION_ID ||
            srcRid == Region.UNKNOWN_REGION_ID) {
            final String err = "Invalid region id =" + srcRid +
                               " from region=" +
                               parent.getSourceRegion().getName();
            rlLogger.log(tableName + err, Level.WARNING, lm(err));
            srcRid = Region.LOCAL_REGION_ID;
        }

        final int tgtRid = translate(parent.getSourceRegion(), srcRid);
        final String msg = "table=" + tableName +
                           ", src=" + parent.getSourceRegion().getName() +
                           ", src id=" + srcRid + " -> tgt id=" + tgtRid;
        rlLogger.log(msg, Level.FINE, lm(msg));
        if (tgtRid == Region.UNKNOWN_REGION_ID ||
            tgtRid == Region.NULL_REGION_ID) {
            final String err =
                "Cannot translate source region id=" + srcRid +
                " from source region=" + parent.getSourceRegion().getName();
            rlLogger.log(tableName + err, Level.WARNING, lm(err));
            return null;
        }
        if (tgtRid == Region.LOCAL_REGION_ID) {
            /* in cascading topo, may see local originated write */
            final String err = "Ignore locally originated row" +
                               " from table=" + tableName +
                               " from region=" +
                               parent.getSourceRegion().getName();
            rlLogger.log(err, Level.FINE, lm(err));
            return null;
        }
        /* set new region id */
        ((RowImpl) tgtRow).setRegionId(tgtRid);

        /* set modification time */
        final long ts;
        try {
            ts = srcRow.getLastModificationTime();
        } catch (UnsupportedOperationException uoe) {
            logger.warning(lm("Row from table=" + tableName +
                              ", remote region=" +
                              parent.getSourceRegion().getName() +
                              " has no modification time" +
                              ", key hash=" +
                              srcRow.createPrimaryKey().hashCode() +
                              ", version=" + srcRow.getVersion()));
            /* let it surface after warning */
            throw uoe;
        }
        ((RowImpl) tgtRow).setModificationTime(ts);

        /*
         * Set expiration time. Expired put will be put in the same way that
         * non-expired key is put, and will expire instantly
         */
        if (StreamOperation.Type.PUT.equals(type)) {
            /* unit test only: expire puts if set */
            assert TestHookExecute.doHookIfSet(expiredPutHook, srcRow);
            ((RowImpl) tgtRow).setExpirationTime(srcRow.getExpirationTime());
        }

        return tgtRow;
    }

    /**
     * Returns the configured write options for write operations on the local
     * store.
     */
    WriteOptions getWriteOptions() {
        return writeOptions;
    }

    /* private functions */
    private String lm(String msg) {
        return "[MRTSubscriber-" + srcRegion + "-" +
               config.getSubscriberId() + "] " + msg;
    }

    /**
     * Translates the region id from streamed operation to localized region id.
     *
     * @param region region the op streamed from
     * @param rid    region in streamed op
     * @return localized region id
     */
    private int translate(RegionInfo region, int rid) {
        if (mdMan == null) {
            throw new IllegalStateException("Metadata man is not ready");
        }
        return mdMan.translate(region.getName(), rid);
    }

    /**
     * Convert the row to target table and reconciles schema of source table to
     * that of target table.
     *
     * @param srcRow row from source region
     * @param type   operation type
     * @return the row of target region
     */
    private Row convertRow(Row srcRow, Table tgtTable,
                           StreamOperation.Type type) {

        if (!mdMan.isPKeyMatch(srcRow.getTable(), tgtTable)) {
            final String err =
                "Mismatch primary keys," +
                "src table=" + srcRow.getTable().getFullNamespaceName() +
                ", while tgt table=" + tgtTable.getFullNamespaceName();
            rlLogger.log(err, Level.WARNING, lm(err));
            throw new IllegalArgumentException("Primary key does not match");
        }

        if (StreamOperation.Type.PUT.equals(type)) {
            /* convert source row to json */
            final String json = srcRow.toJsonString(false);
            /* convert json to target table row and reconcile the difference */
            return tgtTable.createRowFromJson(json, false/* no exact match */);
        }

        if (StreamOperation.Type.DELETE.equals(type)) {
            final PrimaryKey pkey = (PrimaryKey) srcRow;
            /* convert source pkey to json */
            final String json = pkey.toJsonString(false);
            /* convert json to key and reconcile the difference */
            return tgtTable.createPrimaryKeyFromJson(json,
                                                     false/* no exact match */);
        }

        throw new IllegalStateException("Unsupported type=" + type);
    }

    /**
     * Returns true if checkpoint is needed, false otherwise
     *
     * @return true if checkpoint is needed, false otherwise
     */
    private boolean needCkpt() {
        return (nextCheckpointDelaySecs() <= 0) ||
               (getOpsSinceCheckpoint() > ckptIntvNumOps);
    }

    /**
     * Returns the number of seconds until the next checkpoint is needed.
     */
    private long nextCheckpointDelaySecs() {
        final long now = System.currentTimeMillis();
        return ckptIntvSecs - ((now - lastCkptTimeMs) / 1000);
    }

    /**
     * Returns the number of stream operations received as compared to the
     * count at the end of the last checkpoint.
     */
    private long getOpsSinceCheckpoint() {
        return getMetrics().getTotalStreamOps() - lastCkptStreamedOps;
    }

    /**
     * Do a checkpoint if there have been stream operations received since the
     * last checkpoint and a checkpoint has not been done recently enough. This
     * check is needed to handle cases when the stream of operations stops for
     * a while.
     */
    private void doCheckpointIfNeeded() {
        logger.finest(() -> lm("Checking scheduled checkpoint"));
        if (!scheduledDoCheckpointIfNeeded.compareAndSet(true, false)) {
            throw new IllegalStateException(
                "Expected scheduledDoCheckpointIfNeeded to be set");
        }
        if (pendingCheckpoint.get() != null) {
            logger.finest(() -> lm("Pending checkpoint already present"));
            return;
        }

        final long delay = nextCheckpointDelaySecs();
        if (delay > 0) {
            logger.finest(() -> lm("Scheduled checkpoint not needed for " +
                                   delay + " seconds"));
            scheduleDoCheckpointIfNeeded();
            return;
        }

        if (getOpsSinceCheckpoint() <= 0) {
            logger.finest(
                () -> lm("No new operations for scheduled checkpoint"));
            scheduleDoCheckpointIfNeeded();
            return;
        }

        if (!getPendingOperationsEmpty()) {
            logger.finest(() -> lm("Operations queued, scheduled checkpoint" +
                                   " not needed"));
            scheduleDoCheckpointIfNeeded();
            return;
        }

        final NoSQLSubscription stream = getSubscription();
        if (stream.isCanceled()) {
            return;
        }

        /*
         * Get stream position before checking if there are any pending
         * operations since we need a position for which we are sure all
         * operations have been performed on the local store.
         */
        final StreamPosition pos = stream.getCurrentPosition();
        if (getPendingOperationsEmpty()) {
            maybeDoCheckpoint(pos);
        }
        /*
         * If we find that there are pending operations after already
         * determining that a time-based checkpoint is needed, then we can
         * depend on those operations to kick off the checkpoint.
         */
    }

    /** Check if there are any operations in the pending operation queues. */
    private boolean getPendingOperationsEmpty() {
        for (int i = 0; i < pendingOperations.length; i++) {
            final Queue<StreamOperation> queue = getQueue(i);
            synchronized (queue) {
                if (!queue.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Do a checkpoint if one is pending and ready. */
    private void doPendingCheckpointIfNeeded() {
        final PendingCheckpointInfo pendingInfo = pendingCheckpoint.get();
        if (pendingInfo == null) {
            return;
        }

        if (!pendingInfo.checkpointReady()) {
            return;
        }

        maybeDoCheckpoint(pendingInfo.streamPosition);
    }

    private void maybeDoCheckpoint(StreamPosition streamPosition) {
        final NoSQLSubscription stream = getSubscription();
        if (stream.isCanceled()) {
            return;
        }
        //TODO: remove after [KVSTORE-321] is fixed
        if (inInitialization()) {
            /* skip checkpoint if in initialization */
            return;
        }
        if (checkpointUnderway.compareAndSet(false, true)) {
            logger.finest(() -> lm("Performing checkpoint"));
            /* checkpoint at the given or higher position */
            stream.doCheckpoint(streamPosition, false);
        }
    }

    private boolean inInitialization() {
        if (INITIALIZING_TABLES.equals(parent.getStatus())) {
            /* agent is initializing tables */
            return true;
        }
        final TablePollingThread thread = parent.getTablePollingThread();
        /* true if polling thread is trying to initializing tables */
        return thread != null && !thread.getTablesToCheck().isEmpty();
    }

    /* get size of the PUT or DEL */
    private long getSize(Row row, StreamOperation.Type type) {
        long sz = ((RowImpl) row).getKeySize();
        if (type.equals(StreamOperation.Type.PUT)) {
            sz += ((RowImpl) row).getDataSize();
        }
        return sz;
    }

    private static String formatTime(long time) {
        return FormatUtils.formatDateAndTime(time);
    }

    private void writeTarget(StreamOperation source,
                             Row srcRow,
                             Row tgtRow,
                             int attempts) {

        logger.finest(() -> lm("Write target: " + source + " attempts: " +
                               attempts));

        final CompletableFuture<Boolean> future;
        if (source.getType().equals(StreamOperation.Type.PUT)) {
            /* a put with optional expiration time */
            future = tgtTableAPI.putResolveAsync(tgtRow, null, writeOptions);
        } else {
            /* a delete */
            future = tgtTableAPI.deleteResolveAsync(
                (PrimaryKey) tgtRow, null, writeOptions);
        }

        /* TODO: Use dialog layer executor */
        future.whenCompleteAsync(
            unwrapExceptionVoid(
                (srcWin, ex) -> {
                    try {
                        putDelResolveComplete(
                            source, srcRow, tgtRow, (srcWin != null) && srcWin,
                            ex, attempts);
                    } catch (Throwable t) {
                        /* Handle unexpected exceptions */
                        operationFailed(t);
                    }
                }));
    }

    private void putDelResolveComplete(StreamOperation source,
                                       Row srcRow,
                                       Row tgtRow,
                                       boolean srcWin,
                                       Throwable exception,
                                       int attempts) {
        try {
            assert ExceptionTestHookExecute.doHookIfSet(asyncExceptionHook,
                                                        source);
        } catch (Exception e) {
            exception = e;
        }
        final Throwable ex = exception;
        logger.finest(() -> lm("putDelResolveComplete" +
                               " source: " + source +
                               " srcWin: " + srcWin +
                               " exception: " + ex));
        final String tbName = getTableName(srcRow);
        if (ex instanceof FaultException) {
            final String fault = ((FaultException) ex).getFaultClassName();
            if (TargetTableEvolveException.class.getName().equals(fault)) {
                /* local table has evolved, refresh table */
                /* refresh the table */
                final Table old = mdMan.invalidateMRT(tbName);
                final String oldVer = (old == null) ? "N/A" :
                    Integer.toString(old.getTableVersion());
                final Table refresh = mdMan.getMRT(tbName);
                final String newVer = (refresh == null) ? "N/A" :
                    Integer.toString(refresh.getTableVersion());
                logger.info(lm("Target table refreshed from ver=" +
                               oldVer + " to ver=" + newVer));
                /*
                 * Try again immediately, which includes transforming the row
                 * again
                 */
                processOperation(source, attempts + 1);
                return;
            }

            /* store may be down, retry, wait */
            final FaultException fe = (FaultException) ex;
            final String msg = "Cannot write to table=" + tbName +
                               " at target store=" + tgtStoreName +
                               " after # of attempts=" + attempts +
                               ", will retry, " + fe;
            rlLogger.log(tbName + fe.getFaultClassName(), Level.WARNING,
                         lm(msg));
            scheduleRetry(source, attempts);
            /* This operation is not done, so don't request another operation */
            return;
        }

        if (ex instanceof MetadataNotFoundException) {
            /*
             * TODO: What if the table was newly added and we've gotten this
             * exception from an RN that hasn't heard about it yet?
             */
            /* table dropped, shall not retry  */
            final String msg = "Table=" + tbName +
                               " dropped, " + ex.getMessage();
            rlLogger.log(msg, Level.FINE, lm(msg));
            operationCompleted(source);
            return;
        }

        if (ex != null) {
            final String msg = "Cannot write to table=" + tbName +
                               " at target store=" + tgtStoreName +
                               " after # of attempts=" + attempts +
                               ", will cancel stream, " + ex.getMessage() +
                               ", stack:" + LoggerUtils.getStackTrace(ex);
            rlLogger.log(tbName + ex.getClass().getName(), Level.WARNING,
                         lm(msg));
            ((NoSQLSubscriptionImpl) getSubscription()).cancel(ex);
            return;
        }
        logger.finest(() -> lm("Write to table=" + tbName +
                               " at target store=" + tgtStoreName +
                               " after # of attempts=" + attempts));

        final StreamOperation.Type type = source.getType();
        if (type.equals(StreamOperation.Type.PUT)) {
            /* for debugging only, shall be the lowest level */
            logger.finest(() -> lm("Pushed PUT, key hash=" +
                                   tgtRow.createPrimaryKey()
                                         .toJsonString(false).hashCode() +
                                   ", src region id=" +
                                   ((RowImpl) srcRow).getRegionId() +
                                   ", translated tgt region id=" +
                                   ((RowImpl) tgtRow).getRegionId() +
                                   ", source win=" + srcWin));
        } else {
            /* for debugging only, shall be the lowest level */
            logger.finest(() -> lm("Pushed DEL key hash=" +
                                   tgtRow.toJsonString(false).hashCode() +
                                   ", region id=" +
                                   ((RowImpl) tgtRow).getRegionId() +
                                   ", source win=" + srcWin));
        }

        /* stats update */
        long sz = getSize(srcRow, type);
        final MRTableMetrics tbm = getMetrics().getTableMetrics(tbName);
        tbm.incrStreamBytes(sz);
        if (type.equals(StreamOperation.Type.PUT)) {
            tbm.incrPuts(1);
            if (TTL.isExpired(srcRow.getExpirationTime())) {
                tbm.incrExpiredPuts(1);
            }
        } else {
            tbm.incrDels(1);
        }

        if (srcWin) {
            sz = getSize(tgtRow, type);
            tbm.incrPersistStreamBytes(sz);
            if (type.equals(StreamOperation.Type.PUT)) {
                tbm.incrWinPuts(1);
            } else {
                tbm.incrWinDels(1);
            }
        }

        /* update stat */
        final int shardId = source.getRepGroupId();
        final long ts = srcRow.getLastModificationTime();
        getMetrics().setLastModTime(shardId, ts);
        final long latency = Math.max(0, System.currentTimeMillis() - ts);
        getMetrics().addLatency(shardId, latency);
        doPendingCheckpointIfNeeded();
        operationCompleted(source);
    }

    /**
     * A KV thread factory that logs and cancels the subscription if a thread
     * gets an unexpected exception.
     */
    private class MRTSubscriberThreadFactory extends KVThreadFactory {
        MRTSubscriberThreadFactory() {
            super("MRTSubscriberThread", logger);
        }
        @Override
        public UncaughtExceptionHandler makeUncaughtExceptionHandler() {
            return (thread, ex) -> operationFailed(ex);
        }
    }

    /**
     * Count the number of stream operations prior to the supplied one that are
     * still underway.
     */
    private int countPendingPreviousOperations(StreamOperation streamOp) {
        final SequenceId streamOpId = streamOp.getSequenceId();
        int count = 0;
        for (int i = 0; i < pendingOperations.length; i++) {
            final Queue<StreamOperation> queue = getQueue(i);
            synchronized (queue) {
                for (final StreamOperation queueOp : queue) {
                    if (queueOp.getSequenceId().compareTo(streamOpId) < 0) {
                        count++;
                        logger.finest(
                            () -> lm("countPendingPreviousOperations" +
                                     " streamOp: " + streamOp +
                                     " queueOp: " + queueOp));
                    }
                }
            }
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(lm("countPendingPreviousOperations" +
                             " streamOp: " + streamOp + " count: " + count));
        }
        return count;
    }

    /**
     * Store information about a stream operation that is being delayed until
     * all previous operations are completed, so that it can be followed by a
     * checkpoint.
     *
     * When multiple previous operations complete around the same time, there
     * could be a race for who should perform the checkpoint operation, so only
     * the one that sets active to true does the checkpoint. If the operation
     * needs to be retried, say because of a FaultException, then clear active
     * so that the retry can make it active again.
     */
    private class PendingCheckpointInfo {
        final StreamOperation streamOperation;
        final StreamPosition streamPosition;
        final AtomicBoolean active = new AtomicBoolean(false);
        private final AtomicInteger pendingOpCount;
        private final AtomicBoolean handlingAfterReady =
            new AtomicBoolean(false);

        PendingCheckpointInfo(StreamOperation streamOperation,
                              StreamPosition streamPosition) {
            this.streamOperation = streamOperation;
            this.streamPosition = streamPosition;
            this.pendingOpCount = new AtomicInteger(-1);
        }

        @Override
        public String toString() {
            return "PendingCheckpointInfo[" +
                "streamOperation=" + streamOperation +
                " active=" + active +
                " pendingOpCount=" + pendingOpCount +
                "]";
        }

        /**
         * Returns whether all previous operations are complete and this
         * operation is ready to be performed and then checkpointed. Use
         * synchronization to make sure that we don't call this method
         * concurrently since that could update the pending count incorrectly.
         */
        synchronized boolean checkpointReady() {

            /*
             * The count could be negative because of a race between counting
             * operations in the queues and operations being completed during
             * the count. This method includes decrements that occur during the
             * tally, meaning that a decrement may occur even though the option
             * was not tallied, so the count might be low. If the value is
             * greater than 0, then we know the pending operations are not
             * done, but otherwise we need to check again.
             */
            final int currentCount = pendingOpCount.get();
            if (currentCount > 0) {
                logger.finest(() -> lm("checkpointReady " + this +
                                       " currentCount=" + currentCount +
                                       " return=false"));
                return false;
            }

            /* Count pending operations again */
            pendingOpCount.set(0);
            final int newCount =
                countPendingPreviousOperations(streamOperation);
            if (newCount == 0) {

                /*
                 * If we found no pending previous operations, then we're done,
                 * even if there was a race decrementing the count.
                 */
                logger.finest(() -> lm("checkpointReady " + this +
                                       " count=0 return=true"));
                return true;
            }

            pendingOpCount.addAndGet(newCount);
            logger.finest(() -> lm("checkpointReady " + this +
                                   " newCount=" + newCount + " return=false"));
            return false;
        }

        /**
         * Returns whether the operation is a previous one this operation is
         * waiting for.
         */
        boolean isPendingOperation(StreamOperation otherOp) {
            return otherOp.getSequenceId().compareTo(
                streamOperation.getSequenceId()) < 0;
        }

        /**
         * Updates the pending count given that the specified operation has
         * been completed, and returns if this operation is now ready and
         * should be performed by the caller.
         */
        boolean checkpointReadyAfterOpCompleted(StreamOperation completedOp) {
            if (!isPendingOperation(completedOp)) {
                logger.finest(() -> lm("Not pending op" +
                                       " completedOp: " + completedOp +
                                       " pending: " + this));
                return false;
            }
            pendingOpCount.decrementAndGet();
            logger.finest(() -> lm("Decremented pending count" +
                                   " completedOp: " + completedOp +
                                   " pending: " + this));
            if (!checkpointReady()) {
                return false;
            }

            /*
             * The caller should only perform the operation if it is the first
             * one to notice that it is ready, to avoid multiple invocations.
             * [KVSTORE-760]
             */
            return handlingAfterReady.compareAndSet(false, true);
        }
    }

    /**
     * Gets a rate limiting logger
     */
    RateLimitingLogger<String> getRlLogger() {
        return rlLogger;
    }

    private String getError(Throwable t) {
        if (t == null) {
            return "NA";
        }
        return "error=" + t.getClass().getName() + ", " + t.getMessage() +
               (logger.isLoggable(Level.FINE) ?
                   ", " + LoggerUtils.getStackTrace(t) : "");
    }

    /*
     * Stat reference may change if interval stat is collected, thus get
     * the reference from parent instead of keeping a cached reference
     */
    private MRTAgentMetrics getMetrics() {
        return (MRTAgentMetrics) parent.getMetrics();
    }
}
