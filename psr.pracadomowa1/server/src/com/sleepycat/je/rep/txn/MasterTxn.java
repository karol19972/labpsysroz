/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je.rep.txn;

import static com.sleepycat.je.EnvironmentFailureException.unexpectedState;
import static com.sleepycat.je.utilint.VLSN.NULL_VLSN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockNotAvailableException;
import com.sleepycat.je.ReplicaConsistencyPolicy;
import com.sleepycat.je.ThreadInterruptedException;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogItem;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.rep.AbsoluteConsistencyPolicy;
import com.sleepycat.je.rep.InsufficientAcksException;
import com.sleepycat.je.rep.ReplicaWriteException;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.UnknownMasterException;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.node.MasterTransfer;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.impl.node.Replay;
import com.sleepycat.je.rep.impl.node.Replica;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.txn.TxnManager;
import com.sleepycat.je.txn.WriteLockInfo;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.TestHookExecute;
import com.sleepycat.je.utilint.VLSN;

/**
 * A MasterTxn represents:
 *  - a user initiated Txn executed on the Master node, when local-write and
 *    read-only are not configured, or
 *  - an auto-commit Txn on the Master node for a replicated DB.
 *
 * This class uses the hooks defined by Txn to support the durability
 * requirements of a replicated transaction on the Master.
 */
public class MasterTxn extends Txn {
    /* Holds the commit VLSN after a successful commit. */
    private long commitVLSN = NULL_VLSN;

    /* The number of acks required by this txn commit. */
    private int requiredAckCount = -1;

    /* If this transaction requests an Arbiter ack. */
    private boolean needsArbiterAck;

    /*
     * The latch used to track transaction acknowledgments, for this master
     * txn. The latch, if needed, is set up in the pre-commit hook.
     */
    private volatile Latch ackLatch;

    /* The start relative delta time when the commit pre hook exited. */
    private int preLogCommitEndDeltaMs = 0;

    /*
     * The start relative delta time when the commit message was written to
     * the rep stream.
     */
    private int repWriteStartDeltaMs = 0;

    /* Set to the System.nanotime the ack was sent out. */
    private volatile long ackAwaitStartNs;

    /* The txn is timed out when
     * java.lang.System.currentTimeMillis() > ackAwaitTimeoutMs)
     */
    private volatile long ackAwaitTimeoutMs;

    /**
     * A lock stamp associated with the lock RepImpl.blockLatchLock. It's set
     * to non-zero when the read lock is acquired and reset to zero when it's
     * released.
     */
    private long readLockStamp = 0;

    /**
     * Flag to prevent any change to the txn's contents. Used in
     * master->replica transition. Intentionally volatile, so it can be
     * interleaved with use of the MasterTxn mutex.
     */
    private volatile boolean freeze;

    /* For unit testing */
    private TestHook<Integer> convertHook;

    /* The default factory used to create MasterTxns */
    private static final MasterTxnFactory DEFAULT_FACTORY =
        new MasterTxnFactory() {

            @Override
            public MasterTxn create(EnvironmentImpl envImpl,
                                    TransactionConfig config) {
                return new MasterTxn(envImpl, config);
            }

            @Override
            public MasterTxn createNullTxn(EnvironmentImpl envImpl,
                                           TransactionConfig config) {

                return new MasterTxn(envImpl, config) {
                    @Override
                    protected boolean updateLoggedForTxn() {
                        /*
                         * Return true so that the commit will be logged even
                         * though there are no changes associated with this txn
                         */
                        return true;
                    }

                    @Override
                    protected void checkLockInvariant() {
                        if (getCommitLsn() != DbLsn.NULL_LSN) {
                            /*
                             * The txn has been committed.
                             */
                            return;
                        }

                        if ((lastLoggedLsn == DbLsn.NULL_LSN) &&
                            getWriteLockIds().isEmpty()) {
                            /* Uncommitted, state as expected. */
                          return;
                        }

                        final String lsns = "[" +
                            getWriteLockIds().stream().
                                map((l) -> DbLsn.getNoFormatString(l)).
                                collect(Collectors.joining(",")) +
                            "]";
                        final String msg =
                            "Unexpected lock state  for null txn" +
                            " lastLoggedLsn=" +
                             DbLsn.getNoFormatString(lastLoggedLsn) +
                             " locked lsns:" + lsns + " txn=" + getId();
                        LoggerUtils.severe(envImpl.getLogger(), envImpl, msg);
                        throw unexpectedState(envImpl, msg);
                    }
                };
            }
    };

    /* The current Txn Factory. */
    private static MasterTxnFactory factory = DEFAULT_FACTORY;

    public MasterTxn(EnvironmentImpl envImpl,
                     TransactionConfig config)
        throws DatabaseException {

        super(envImpl, config, ReplicationContext.MASTER);
        startNs = System.nanoTime();
        assert !config.getLocalWrite();
    }

    @Override
    public boolean isLocalWrite() {
        return false;
    }

    /**
     * Returns the transaction commit token used to identify the transaction.
     *
     * @see com.sleepycat.je.txn.Txn#getCommitToken()
     */
    @Override
    public CommitToken getCommitToken() {
        if (VLSN.isNull(commitVLSN)) {
            return null;
        }
        return new CommitToken(getRepImpl().getUUID(),
                               commitVLSN);
    }

    private final RepImpl getRepImpl() {
        return (RepImpl)envImpl;
    }

    public long getCommitVLSN() {
        return commitVLSN;
    }

    @Override
    public long getAuthoritativeTimeout() {
        /*
         * The timeout is the lesser of the transaction timeout and the
         * insufficient replicas timeout.
         */
        final long txnTimeout = getTxnTimeout();
        long timeout = getRepImpl().getInsufficientReplicaTimeout();
        if ((txnTimeout != 0) && (txnTimeout < timeout)) {
            timeout = txnTimeout;
        }
        return timeout;
    }

    /**
     * MasterTxns use txn ids from a reserved negative space. So override
     * the default generation of ids.
     */
    @Override
    protected long generateId(TxnManager txnManager,
                              long ignore /* mandatedId */) {
        assert(ignore == 0);
        return txnManager.getNextReplicatedTxnId();
    }

    /**
     * Causes the transaction to wait until we have sufficient replicas to
     * acknowledge the commit.
     */
    @Override
    protected void txnBeginHook(TransactionConfig config)
        throws DatabaseException {

        /* AbsoluteConsistencyPolicy only applies to readonly transactions. */
        ReplicaConsistencyPolicy policy = config.getConsistencyPolicy();
        if (policy instanceof AbsoluteConsistencyPolicy) {
            throw new IllegalArgumentException
                ("AbsoluteConsistencyPolicy can only apply to read-only" +
                 " transactions.");
        }
        final RepImpl rep = getRepImpl();
        rep.checkIfInvalid();
        final ReplicaAckPolicy ackPolicy =
            getDefaultDurability().getReplicaAck();
        final RepNode node = rep.getRepNode();
        final int requiredReplicaAckCount = node.
            getDurabilityQuorum().getCurrentRequiredAckCount(ackPolicy);
        /*
         * No need to wait or authorize if this txn is not durable or there
         * are no other sites.
         */
        if (requiredReplicaAckCount == 0) {
            return;
        }

        try {
            node.awaitAuthoritativeMaster(this);
        } catch (InterruptedException e) {
            throw new ThreadInterruptedException(envImpl, e);
        }
    }

    @Override
    protected void preLogCommitHook()
        throws DatabaseException {

        ReplicaAckPolicy ackPolicy = getCommitDurability().getReplicaAck();
        requiredAckCount = getRepImpl().getRepNode().getDurabilityQuorum().
            getCurrentRequiredAckCount(ackPolicy);
        ackLatch = (requiredAckCount == 0) ?
            null :
            ((requiredAckCount == 1) ? new SingleWaiterLatch() :
                new MultiWaiterLatch(requiredAckCount));

        /*
         * TODO: An optimization we'd like to do is to identify transactions
         * that only modify non-replicated databases, so they can avoid waiting
         * for Replica commit acks and avoid checks like the one that requires
         * that the node be a master before proceeding with the transaction.
         */
        getRepImpl().preLogCommitHook(this);
        preLogCommitEndDeltaMs =
            (int) (System.currentTimeMillis() - getTxnStartMillis());
    }

    @Override
    protected boolean postLogCommitHook(LogItem commitItem)
        throws DatabaseException {

        commitVLSN = commitItem.header.getVLSN();
        try {
            return getRepImpl().postLogCommitHook(this, commitItem);
        } catch (InterruptedException e) {
            throw new ThreadInterruptedException(envImpl, e);
        }
    }

    @Override
    protected void preLogAbortHook()
        throws DatabaseException {

        getRepImpl().preLogAbortHook(this);
    }

    @Override
    protected void postLogCommitAbortHook() {
        getRepImpl().postLogCommitAbortHook(this);
    }

    @Override
    protected void postLogAbortHook() {
        getRepImpl().postLogAbortHook(this);
    }

    /**
     * Prevent this MasterTxn from taking locks if the node becomes a
     * replica. The application has a reference to this Txn, and may
     * attempt to use it well after the node has transitioned from master
     * to replica.
     */
    @Override
    public LockResult lockInternal(long lsn,
                                   LockType lockType,
                                   boolean noWait,
                                   boolean jumpAheadOfWaiters,
                                   DatabaseImpl database,
                                   CursorImpl cursor)
        throws LockNotAvailableException, LockConflictException,
               DatabaseException {
        ReplicatedEnvironment.State nodeState = ((RepImpl)envImpl).getState();
        if (nodeState.isMaster()) {
            return super.lockInternal(
                lsn, lockType, noWait, jumpAheadOfWaiters, database, cursor);
        }

        throwNotMaster(nodeState);
        return null; /* not reached */
    }

    /**
     * There is a small window between a LN logging the new entry and locking
     * the entry.  During this time it is possible for the master to transition
     * to another state, resulting in the lock failing if the regular
     * lockInternal is used, which can resulting in log corruption.  As such
     * this function is used to bypass the master status check and return the
     * lock.
     */
    @Override
    public LockResult postLogNonBlockingLock(long lsn,
                                             LockType lockType,
                                             boolean jumpAheadOfWaiters,
                                             DatabaseImpl database,
                                             CursorImpl cursor) {
        final LockResult result = super.lockInternal(
            lsn, lockType, true /*noWait*/, jumpAheadOfWaiters, database,
            cursor);
        if (result.getLockGrant() != LockGrantType.DENIED) {
            checkPreempted();
        }
        return result;
    }

    private void throwNotMaster(ReplicatedEnvironment.State nodeState) {
        if (nodeState.isReplica()) {
            throw new ReplicaWriteException
                (this, ((RepImpl)envImpl).getStateChangeEvent());
        }
        throw new UnknownMasterException
            ("Transaction " + getId() +
             " cannot execute write operations because this node is" +
             " no longer a master");
    }

    /**
     * If logging occurs before locking, we must screen out write locks here.
     */
    @Override
    public synchronized void preLogWithoutLock(DatabaseImpl database) {
        ReplicatedEnvironment.State nodeState = ((RepImpl)envImpl).getState();
        if (nodeState.isMaster()) {
            super.preLogWithoutLock(database);
            return;
        }

        throwNotMaster(nodeState);
    }

    public int getRequiredAckCount() {
        return requiredAckCount;
    }

    public void resetRequiredAckCount() {
        requiredAckCount = 0;
    }

    /*
     * The System.nanotime when the commit was sent out and the master started
     * waiting for the ack.
     */
    public long getAckAwaitStartNs() {
        return ackAwaitStartNs;
    }

    public void setAckAwaitStartNs(long ackAwaitStartNs) {
        this.ackAwaitStartNs = ackAwaitStartNs;
    }

    /**
     * Used to determine whether a txn has exceeded the time it's allowed to
     * wait for acks
     */
    public boolean ackTimeoutExceeded() {
        return System.currentTimeMillis() > ackAwaitTimeoutMs ;
    }

    /**
     * @return the ackAwaitTimeoutMs
     */
    public long getAckAwaitTimeoutMs() {
        return ackAwaitTimeoutMs;
    }

    /**
     * @param ackAwaitTimeoutMs the ackAwaitTimeoutMs to set
     */
    public void setAckAwaitTimeoutMs(long ackAwaitTimeoutMs) {
        this.ackAwaitTimeoutMs = ackAwaitTimeoutMs;
    }


    /** A masterTxn always writes its own id into the commit or abort. */
    @Override
    protected int getReplicatorNodeId() {
        return getRepImpl().getNameIdPair().getId();
    }

    @Override
    protected long getDTVLSN() {
        /*
         * For the master transaction, it should always be null, and will
         * be corrected under the write log latch on its way to disk.
         */
        return NULL_VLSN;
    }

    public void stampRepWriteTime() {
        this.repWriteStartDeltaMs =
            (int)(System.currentTimeMillis() - getTxnStartMillis());
    }

    /**
     * Returns the amount of time it took to copy the commit record from the
     * log buffer to the rep stream. It's measured as the time interval
     * starting with the time the preCommit hook completed, to the time the
     * message write to the replication stream was initiated.
     */
    public long messageTransferMs() {
        return repWriteStartDeltaMs > 0 ?

                (repWriteStartDeltaMs - preLogCommitEndDeltaMs) :

                /*
                 * The message was invoked before the post commit hook fired.
                 */
                0;
    }

    /**
     * Wrapper method enforces the invariant that the read lock must
     * have been released after the commit commit has completed.
     */
    @Override
    public long commit(Durability durability) {
        try {
            return super.commit(durability);
        } finally {
            /* Rep Invariant: read lock must have been released at txn exit */
            if (envImpl.isValid() && (readLockStamp > 0)) {
                String msg = "transaction: " + getId() +
                             " has read lock after commit";
                LoggerUtils.severe(envImpl.getLogger(), envImpl, msg);

                throw EnvironmentFailureException.
                    unexpectedState(envImpl, msg);
            }
        }
    }

    /**
     * Wrapper method enforces the invariant that the read lock must
     * have been released after the abort has completed.
     */
    @Override
    public long abort(boolean forceFlush) {
        try {
            return super.abort(forceFlush);
        } finally {
            /* Rep Invariant: read lock must have been released at txn exit */
            if (envImpl.isValid() && (readLockStamp > 0)) {
                String msg = "transaction: " + getId() +
                             " has read lock after abort";
                LoggerUtils.severe(envImpl.getLogger(), envImpl, msg);

                throw EnvironmentFailureException.
                unexpectedState(envImpl, msg);
            }
        }
    }

    /**
     * Lock out the setting of the block latch by Master Transfer while a
     * commit/abort log record, which advances the VLSN is written. This lock
     * will be released by the {@code postLogXxxHook()} functions, one of which
     * is guaranteed to be called, unless an Environment-invalidating exception
     * occurs.
     *
     * The corresponding method {@link #unlockReadBlockLatch} unlocks the latch
     */
    public void lockReadBlockLatch() throws InterruptedException {
        if (readLockStamp > 0) {
            /* Enforce assertion that read lock cannot be set more than
             * once within a txn
             */
            throw new IllegalStateException("block latch read locked for txn:" +
                getId());
        }
        final long timeoutMs = 2 * MasterTransfer.getPhase2TimeoutMs();
        readLockStamp = getRepImpl().getBlockLatchLock().
            tryReadLock(timeoutMs, TimeUnit.MILLISECONDS);
        if (readLockStamp == 0) {
            final String msg =
                "Txn:" + this.toString() +
                " failed to aquire read lock on block latch within" +
                " timeout: " + timeoutMs  + " ms";
            LoggerUtils.fullThreadDump(getRepImpl().getLogger(), getRepImpl(),
                                       Level.SEVERE);
            throw EnvironmentFailureException.
                unexpectedState(getRepImpl(), msg);
        }
    }

    /**
     * Unlocks the readLocked latch, if it has been set by
     * {@link #lockReadBlockLatch}. Note that this method is more forgiving,
     * since there are instances of abort processing during a txn commit where
     * it's not possible to tell whether the latch has been set in a definitive
     * way.
     */
    public void unlockReadBlockLatch() {
        if (readLockStamp == 0) {
            LoggerUtils.info(getRepImpl().getLogger(), getRepImpl(),
                             "ignoring unset read lock:" + getId());
            return;
        }
        getRepImpl().getBlockLatchLock().unlock(readLockStamp);
        readLockStamp = 0;
    }

    @Override
    protected boolean
        propagatePostCommitException(DatabaseException postCommitException) {
        return (postCommitException instanceof InsufficientAcksException) ?
                true :
                super.propagatePostCommitException(postCommitException);
    }

    /* The Txn factory interface. */
    public interface MasterTxnFactory {
        MasterTxn create(EnvironmentImpl envImpl,
                         TransactionConfig config);

        /**
         * Create a special "null" txn that does not result in any changes to
         * the environment. It's sole purpose is to persist and communicate
         * DTVLSN values.
         */
        MasterTxn createNullTxn(EnvironmentImpl envImpl,
                                TransactionConfig config);
    }

    /* The method used to create user Master Txns via the factory. */
    public static MasterTxn create(EnvironmentImpl envImpl,
                                   TransactionConfig config) {
        return factory.create(envImpl, config);
    }

    public static MasterTxn createNullTxn(EnvironmentImpl envImpl,
                                   TransactionConfig config) {
        return factory.createNullTxn(envImpl, config);
    }

    /**
     * Method used for unit testing.
     *
     * Sets the factory to the one supplied. If the argument is null it
     * restores the factory to the original default value.
     */
    public static void setFactory(MasterTxnFactory factory) {
        MasterTxn.factory = (factory == null) ? DEFAULT_FACTORY : factory;
    }

    /**
     * Convert a MasterTxn that has any write locks into a ReplayTxn, and close
     * the MasterTxn after it is disemboweled. A MasterTxn that only has read
     * locks is unchanged and is still usable by the application. To be clear,
     * the application can never use a MasterTxn to obtain a lock if the node
     * is in Replica mode, but may indeed be able to use a read-lock-only
     * MasterTxn if the node cycles back into Master status.
     *
     * For converted MasterTxns, all write locks are transferred to a replay
     * transaction, read locks are released, and the txn is closed. Used when a
     * node is transitioning from master to replica mode without recovery,
     * which may happen for an explicit master transfer request, or merely for
     * a network partition/election of new
     * master.
     *
     * The newly created replay transaction will need to be in the appropriate
     * state, holding all write locks, so that the node in replica form can
     * execute the proper syncups.  Note that the resulting replay txn will
     * only be aborted, and will never be committed, because the txn originated
     * on this node, which is transitioning from {@literal master -> replica}.
     *
     * We only transfer write locks. We need not transfer read locks, because
     * replays only operate on writes, and are never required to obtain read
     * locks. Read locks are released though, because (a) this txn is now only
     * abortable, and (b) although the Replay can preempt any read locks held
     * by the MasterTxn, such preemption will add delay.
     *
     * @return a ReplayTxn, if there were locks in this transaction, and
     * there's a need to create a ReplayTxn.
     */
    public ReplayTxn convertToReplayTxnAndClose(Logger logger,
                                                Replay replay) {

        /* Assertion */
        if (!freeze) {
            throw unexpectedState
                (envImpl,
                 "Txn " + getId() +
                 " should be frozen when converting to replay txn");
        }

        /*
         * This is an important and relatively rare operation, and worth
         * logging.
         */
        LoggerUtils.info(logger, envImpl,
                         "Transforming txn " + getId() +
                         " from MasterTxn to ReplayTxn");

        int hookCount = 0;
        ReplayTxn replayTxn = null;
        boolean needToClose = true;

        /*
         * Wake up any threads waiting for acks on this txn.  Calling notify()
         * on the transaction latch will not work since the code is already
         * designed to ignore any wakeup calls that come before the timeout
         * due to the problem of spurious wakeups.  This transaction will
         * throw an InsufficientAcksException when it sees that "freeze" is
         * true.
         */
        while (getPendingAcks() > 0) {
            countdownAck();
        }
        try {
            synchronized (this) {

                if (isClosed()) {
                    LoggerUtils.info(logger, envImpl,
                                     "Txn " + getId() +
                                     " is closed, no transform needed");
                    needToClose = false;
                    return null;
                }

                if (getCommitLsn() != DbLsn.NULL_LSN) {
                    /* Covers a window in Txn.commit() between the time a
                     * commit record is written and the txn is closed. Similar
                     * handling as above, except for log message.
                     */
                    LoggerUtils.info(logger, envImpl,
                                     "Txn " + getId() +
                                     " has written commit log entry, " +
                                     " no transform needed");
                    needToClose = false;
                    return null;
                }

                checkLockInvariant();

                /*
                 * Get the list of write locks, and process them in lsn order,
                 * so we properly maintain the lastLoggedLsn and firstLoggedLSN
                 * fields of the newly created ReplayTxn.
                 */
                final Set<Long> lockedLSNs = getWriteLockIds();

                /*
                 * This transaction may not actually have any write locks. In
                 * that case, we permit it to live on.
                 */
                if (lockedLSNs.size() == 0) {
                    LoggerUtils.info(logger, envImpl, "Txn " + getId() +
                                     " had no write locks, didn't create" +
                                     " ReplayTxn");
                    needToClose = false;
                    return null;
                }

                /*
                 * We have write locks. Make sure that this txn can now
                 * only be aborted. Otherwise, there could be this window
                 * in this method:
                 *  t1: locks stolen, no locks left in this txn
                 *  t2: txn unfrozen, commits and aborts possible
                 *    -- at this point, another thread could sneak in and
                 *    -- try to commit. The txn would commmit successfully,
                 *    -- because a commit w/no write locks is a no-op.
                 *    -- but that would convey the false impression that the
                 *    -- txn's write operations had commmitted.
                 *  t3: txn is closed
                 */
                setOnlyAbortable(new UnknownMasterException
                                 (envImpl.getName() +
                                  " is no longer a master"));
                replayTxn = replay.getReplayTxn(this);

                /*
                 * Order the lsns, so that the locks are taken in the proper
                 * order, and the txn's firstLoggedLsn and lastLoggedLsn fields
                 * are properly set.
                 */
                List<Long> sortedLsns = new ArrayList<>(lockedLSNs);
                Collections.sort(sortedLsns);
                LoggerUtils.info(logger, envImpl,
                                 "Txn " + getId()  + " has " +
                                 lockedLSNs.size() + " locks to transform");

                /*
                 * Transfer each lock. Note that ultimately, since mastership
                 * is changing, and replicated commits will only be executed
                 * when a txn has originated on that node, the target ReplayTxn
                 * can never be committed, and will only be aborted.
                 */
                for (Long lsn: sortedLsns) {

                    LoggerUtils.fine(logger, envImpl,
                                     "Txn " + getId() +
                                     " is transferring lock " + lsn);

                    /*
                     * Use a special method to steal the lock. Another approach
                     * might have been to have the replayTxn merely attempt a
                     * lock(); as an importunate txn, the replayTxn would
                     * preempt the MasterTxn's lock. However, that path doesn't
                     * work because lock() requires having a databaseImpl in
                     * hand, and that's not available here.
                     */
                    replayTxn.stealLockFromMasterTxn(lsn);

                    /*
                     * Copy all the lock's info into the Replay and remove it
                     * from the master. Normally, undo clears write locks, but
                     * this MasterTxn will not be executing undo.
                     */
                    WriteLockInfo replayWLI = replayTxn.getWriteLockInfo(lsn);
                    WriteLockInfo masterWLI = getWriteLockInfo(lsn);
                    replayWLI.copyAllInfo(masterWLI);
                    removeLock(lsn);
                }
                LoggerUtils.info(logger, envImpl,
                                 "Txn " + getId()  + " transformed " +
                                 lockedLSNs.size() + " locks");


                /*
                 * Txns have collections of undoDatabases and dbCleanupSet.
                 * Undo databases are normally incrementally added to the txn
                 * as locks are obtained Unlike normal locking or recovery
                 * locking, in this case we don't have a reference to the
                 * databaseImpl that goes with this lock, so we copy the undo
                 * collection in one fell swoop.
                 */
                replayTxn.copyDatabasesForConversion(this);

                /*
                 * This txn is no longer responsible for databaseImpl
                 * cleanup, as that issue now lies with the ReplayTxn, so
                 * remove the collection.
                 */
                dbCleanupSet = null;

                /*
                 * All locks have been removed from this transaction. Clear
                 * the firstLoggedLsn and lastLoggedLsn so there's no danger
                 * of attempting to undo anything; this txn is no longer
                 * responsible for any records.
                 */
                lastLoggedLsn = DbLsn.NULL_LSN;
                firstLoggedLsn = DbLsn.NULL_LSN;

                /* If this txn also had read locks, clear them */
                clearReadLocks();
            }
        } finally {

            assert TestHookExecute.doHookIfSet(convertHook, hookCount++);

            unfreeze();

            assert TestHookExecute.doHookIfSet(convertHook, hookCount++);

            /*
             * We need to abort the txn, but we can't call abort() because that
             * method checks whether we are the master! Instead, call the
             * internal method, close(), in order to end this transaction and
             * unregister it from the transactionManager. Must be called
             * outside the synchronization block.
             */
            if (needToClose) {
                LoggerUtils.info(logger, envImpl, "About to close txn " +
                                 getId() + " state=" + getState());
                close(false /*isCommit */);
                LoggerUtils.info(logger, envImpl, "Closed txn " +  getId() +
                                 " state=" + getState());
            }
            assert TestHookExecute.doHookIfSet(convertHook, hookCount++);
        }

        return replayTxn;
    }

    public void freeze() {
        freeze = true;
    }

    public boolean getFreeze() {
        return freeze;
    }

    private void unfreeze() {
        freeze = false;
    }

    /**
     * Used to hold the transaction stable while it is being cloned as a
     * ReplayTxn, during {@literal master->replica} transitions. Essentially,
     * there are two parties that now have a reference to this transaction --
     * the originating application thread, and the RepNode thread that is
     * trying to set up internal state so it can begin to act as a replica.
     *
     * The transaction will throw UnknownMasterException or
     * ReplicaWriteException if the transaction is frozen, so that the
     * application knows that the transaction is no longer viable, but it
     * doesn't attempt to do most of the follow-on cleanup and release of locks
     * that failed aborts and commits normally attempt. One aspect of
     * transaction cleanup can't be skipped though. It is necessary to do the
     * post log hooks to free up the block txn latch lock so that the
     * transaction can be closed by the RepNode thread. For example:
     * - application thread starts transaction
     * - application takes the block txn latch lock and attempts commit or
     * abort, but is stopped because the txn is frozen by master transfer.
     * - the application must release the block txn latch lock.
     * @see Replica#replicaTransitionCleanup
     */
    @Override
    protected void checkIfFrozen(boolean isCommit) {
        if (freeze) {
            try {
                ((RepImpl) envImpl).checkIfMaster(this);
            } catch (DatabaseException e) {
                if (isCommit) {
                    postLogCommitAbortHook();
                } else {
                    postLogAbortHook();
                }
                throw e;
            }
        }
    }

    /** @see #checkIfFrozen */
    @Override
    protected boolean isFrozen() {
        return freeze;
    }

    /* For unit testing */
    public void setConvertHook(TestHook<Integer> hook) {
        convertHook = hook;
    }

    @Override
    public boolean isMasterTxn() {
        return true;
    }

    public void setArbiterAck(boolean val) {
        needsArbiterAck = val;
    }

    @Override
    public boolean getArbiterAck() {
        return needsArbiterAck;
    }

    /**
     * Wait for replicas to supply the requisite acknowledgments for
     * this transaction.
     *
     * @param awaitStartMs the time at which the wait for an ack was started
     * @param timeoutMs the max time to wait for acks
     *
     * @return true of the requisite acks were received
     *
     * @throws InterruptedException
     */
    public final boolean await(long awaitStartMs, int timeoutMs)
        throws InterruptedException {

        return (ackLatch == null) || ackLatch.await(awaitStartMs, timeoutMs);
    }

    /**
     * A test entrypoint to explicitly create spurious notifies on
     * Object.wait() used by the optimized SingleWaiterLatch based
     * implementation of Latch.
     */
    public final boolean testNotify() {
        if (ackLatch != null) {
            ackLatch.testNotify();
            return true;
        }
        return false;
    }


    /* Countdown the latch, in response to an ack. */
    public final void countdownAck() {
        if (ackLatch == null) {
            return;
        }

        ackLatch.countDown();
    }

    /* Return the number of acks still to be received. */
    public final int getPendingAcks() {
        if (ackLatch == null) {
            return 0;
        }

        return (int) ackLatch.getCount();
    }

    private interface Latch {
        /**
         * Returns true if the latch was tripped, false if the timer expired
         * without tripping the latch.
         */
        boolean await(long awaitStartMs, int timeoutMs)
            throws InterruptedException;

        /**
         * Test entrypoint for spurious wakeups
         */
        void testNotify();

        /**
         * Counts down the latch.
         */
        void countDown();

        /**
         * Returns the count of waiters associated with the latch.
         */
        long getCount();
    }

    private class SingleWaiterLatch implements Latch {
        private volatile boolean done = false;

        @Override
        public synchronized void countDown() {
            done = true;
            /* Notify the single transaction in the post commit hook */
            this.notify();
        }

        @Override
        public synchronized long getCount() {
            return done ? 0 : 1l;
        }

        @Override
        public synchronized boolean await(long awaitStartMs,
                                          int timeoutMs)
            throws InterruptedException {
            if (done) {
                return done;
            }

            /* Loop to allow for spurious wakeups:
             * https://stackoverflow.com/questions/1050592/do-spurious-wakeups-in-java-actually-happen
             */
            for (long waitMs = timeoutMs; waitMs > 0; ){
                wait(waitMs);
                /*
                 * The master is becoming a replica due to MasterTransfer, so
                 * the latch was woken up early before the timeout and before
                 * it could get enough acks.
                 */
                if (freeze) {
                    return false;
                }
                if (done) {
                    return done;
                }
                /* Check for spurious wakeup. Only obtain time, when we
                 * have timed out, or there's a spurious wakeup. These should
                 * be rare occurrences and the extra overhead negligible.
                 */
                waitMs = timeoutMs -
                    (System.currentTimeMillis() - awaitStartMs);
            }
            return done; /* timed out. */
        }

        @Override
        public synchronized void testNotify() {
            this.notify();
        }
    }

    private class MultiWaiterLatch extends CountDownLatch implements Latch {

        public MultiWaiterLatch(int count) {
            super(count);
        }

        @Override
        public boolean await(long awaitStartMs,
                             int timeoutMs) throws InterruptedException {
            final boolean done = await(timeoutMs, TimeUnit.MILLISECONDS);
            /*
             * The master is becoming a replica due to MasterTransfer, so
             * the latch was woken up early before the timeout and before it
             * could get enough acks.
             */
            if (freeze) {
                return false;
            }
            return done;
        }

        @Override
        public synchronized void testNotify() {
            throw new IllegalStateException("Not a SingleWaiterLatch");
        }
    }

}
