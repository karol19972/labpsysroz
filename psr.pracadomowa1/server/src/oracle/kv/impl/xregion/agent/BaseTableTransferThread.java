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

package oracle.kv.impl.xregion.agent;

import static oracle.kv.impl.xregion.service.JsonConfig.DEFAULT_BATCH_SIZE_PER_REQUEST;
import static oracle.kv.impl.xregion.service.JsonConfig.DEFAULT_ROWS_REPORT_PROGRESS_INTV;
import static oracle.kv.impl.xregion.service.JsonConfig.DEFAULT_THREADS_TABLE_ITERATOR;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.COMPLETE;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.ERROR;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.IN_PROGRESS;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.NOT_START;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.SHUTDOWN;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.Direction;
import oracle.kv.FaultException;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.StoreIteratorException;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.test.ExceptionTestHook;
import oracle.kv.impl.test.ExceptionTestHookExecute;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.util.Pair;
import oracle.kv.impl.util.UserDataControl;
import oracle.kv.impl.xregion.init.TableInitCheckpoint;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.impl.xregion.stat.TableInitStat;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableIterator;
import oracle.kv.table.TableIteratorOptions;

import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * Object represents the base class of table transfer thread
 */
abstract public class BaseTableTransferThread extends StoppableThread {

    /**
     * A test hook that can be used by test when the table transfer
     * completes, the parameter is the name of table being transferred.
     */
    public static volatile TestHook<String> transCompleteHook = null;
    /**
     * A test hook that can be used by test during table transfer, the
     * parameter is pair of 1) the name of table being transferred and 2) the
     * number of transferred rows in that table.
     */
    public static volatile TestHook<Pair<String, Long>>
        transInProgressHook = null;

    /**
     * unit test only, test hook to generate failure. The hook will be called
     * after writing a transferred row from source to the target store. The
     * arguments pass the number of transferred rows and exception thrown if
     * the hook is fired.
     */
    public static volatile ExceptionTestHook<Long, Exception> expHook = null;

    /* soft shutdown waiting time in ms */
    private static final int SOFT_SHUTDOWN_WAIT_MS = 5000;

    /* private logger */
    protected final Logger logger;

    /* parent region agent */
    protected final RegionAgentThread parent;

    /* table to initialize */
    protected final String tableName;

    /* source region */
    protected final RegionInfo srcRegion;

    /* cause of failure */
    protected volatile Exception cause;

    /* if the thread has been shutdown */
    protected volatile boolean isShutdown;

    /* true if transfer is complete */
    protected volatile boolean complete;

    /* source Table API */
    private final TableAPIImpl srcAPI;

    /**
     * table iterator option
     */
    private final TableIteratorOptions iter_opt;

    /**
     * checkpoint primary key to resume table scan, or an empty key
     * {@link Table#createPrimaryKey()} for a full table scan. If null,
     * either the checkpoint is corrupted or the the table initialization is
     * done, in either case there is no need to resume the table copy.
     */
    private volatile PrimaryKey ckptKey;

    /**
     * the table to scan
     */
    private final Table srcTable;

    /**
     * unit test only
     */
    private volatile BaseRegionAgentMetrics metrics = null;

    /**
     * last primary persisted to target store
     */
    private volatile PrimaryKey lastPersistPkey;

    /**
     * True if check redundant table transfer
     */
    private volatile boolean checkRedundant;

    /**
     * Constructs an instance of table transfer thread
     *
     * @param threadName thread name
     * @param parent     parent region agent
     * @param tableName  name of table to transfer
     * @param srcRegion  source region to transfer table from
     * @param srcAPI     source region table api
     * @param logger     private logger
     */
    public BaseTableTransferThread(String threadName,
                                   RegionAgentThread parent,
                                   String tableName,
                                   RegionInfo srcRegion,
                                   TableAPIImpl srcAPI,
                                   Logger logger) {

        super(threadName);

        this.parent = parent;
        this.tableName = tableName;
        this.srcRegion = srcRegion;
        this.srcAPI = srcAPI;
        this.logger = logger;

        isShutdown = false;
        complete = false;
        cause = null;
        setUncaughtExceptionHandler(new ExceptionHandler());

        if (parent == null) {
            /* unit test only */
            srcTable = srcAPI.getTable(tableName);
        } else {
            /*
             * the region agent starts up a table transfer only after the
             * table has been ensured to exist at remote region, therefore,
             * the table instance must be cached.
             */
            srcTable = parent.getMdMan().getRemoteTable(srcRegion.getName(),
                                                        tableName);
        }

        if (srcTable == null) {
            final String err = "Table " + tableName + " does not exist at" +
                               " region=" + srcRegion.getName();
            throw new IllegalStateException(err);
        }
        /* verify that the table from remote region has the right name */
        if (!tableName.equals(srcTable.getFullNamespaceName())) {
            final String err =
                "Source table=" + srcTable.getFullNamespaceName() +
                " does not match the requested table=" + tableName;
            throw new IllegalStateException(err);
        }
        ckptKey = null;
        iter_opt = new TableIteratorOptions(
            Direction.FORWARD,
            Consistency.ABSOLUTE,
            /* iterator timeout upper bounded by store read timeout */
            srcAPI.getStore().getReadTimeoutMs(),
            TimeUnit.MILLISECONDS,
            getTableThreads(),
            getTableBatchSz());

        /* create initialization stats */
        final BaseRegionAgentMetrics currentMetrics = getMetrics();
        if (currentMetrics == null) {
            /* unit test only */
            return;
        }
        /* create a table metrics if not exists, and new init state */
        if (parent != null) {
            parent.getAddTable(tableName, ((TableImpl) srcTable).getId())
                  .getRegionInitStat(srcRegion.getName());

        }
        checkRedundant = true;
    }

    /**
     * Pushes the row from the source region
     *
     * @param srcRow row of source region
     */
    abstract protected void pushRow(Row srcRow);

    /**
     * Returns the stat summary of the thread
     *
     * @return the stat summary of the thread
     */
    abstract protected String dumpStat();

    /**
     * Adds prefix for log messages
     *
     * @param msg log message
     * @return log message with prefix
     */
    protected String lm(String msg) {
        /* use thread name */
        return "[" + getName() + "] " + msg;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void run() {
        final long startTime = System.currentTimeMillis();
        final String region = srcRegion.getName();

        if (checkRedundant && isRedundantTransfer()) {
            logger.info(lm("Skip transfer because there is already an " +
                           "existing transfer thread for table=" +
                           tableName + " from region=" + region));
            return;
        }

        try {
            ckptKey = getCheckpointKey();
            if (ckptKey == null) {
                logger.info(lm("Skip transfer for table=" + tableName));
                return;
            }
        } catch (CheckpointKeyException exp) {
            cause = exp;
            final String msg =
                "Fail to get checkpoint key for table=" + tableName +
                " from region=" + srcRegion.getName() +
                " due to " + cause.getMessage() +
                (cause.getCause() == null ? "" :
                    ", stack trace\n" +
                    LoggerUtils.getStackTrace(cause.getCause()));
            logger.warning(lm(msg));
            return;
        }

        /* set start time */
        getMetrics().getTableMetrics(tableName)
                    .getRegionInitStat(region)
                    .setTransferStartMs(startTime);
        int attempts = 0;

        /*
         * the thread will indefinitely retry until transfer is complete, or
         * service shuts down, or encounters unexpected failure
         */
        while (!complete && !isShutdown) {
            logger.info(lm("Starting transfer table=" + tableName +
                           "(id=" + ((TableImpl) srcTable).getId() +
                           ") from region=" + region +
                           (isFullScan(srcTable, ckptKey) ? ", full scan" :
                           ", from checkpoint key hash=" +
                           getKeyHash(ckptKey)) + ", attempts=" + attempts));
            try {
                transTable(++attempts);
            } catch (StoreIteratorException sie) {
                final Throwable err = sie.getCause();
                if (err instanceof MetadataNotFoundException) {
                    /* if table is dropped, no need to retry */
                    logger.warning(lm("Missing table=" + tableName +
                                      " at region=" + region +
                                      ", might be dropped"));
                    cause = sie;
                    break;
                }
                /* retry on other cases */
                retryLog(sie, attempts);
            } catch (FaultException fe) {
                /* always retry */
                retryLog(fe, attempts);
            } catch (Exception exp) {
                /* no retry on hard failures */
                if (!isShutdown &&
                    (parent != null && !parent.isShutdownRequested())) {
                    cause = exp;
                }
                break;
            }
        }

        /* success */
        if (parent == null) {
            /*
             * in some unit test without parent region agent, no need to
             * update the stats and checkpoint below
             */
            return;
        }

        /* normal case */
        final ServiceMDMan mdMan = parent.getMdMan();
        if (complete) {
            /* if table transfer started by the polling thread, remove it */
            final TablePollingThread tpt = parent.getTablePollingThread();
            tpt.removeTableFromInTrans(Collections.singleton(tableName));
            /* update table init checkpoint to complete */
            mdMan.writeCkptRetry(region, tableName, null, COMPLETE);
            getMetrics().getTableMetrics(tableName).getRegionInitStat(region)
                        .setTransferCompleteMs(System.currentTimeMillis());
            logger.info(lm("Complete transferring table=" + tableName +
                           " in attempts=" + attempts));
            logger.fine(() -> lm(dumpStat()));
            /* unit test only, test hook at completion time */
            assert TestHookExecute.doHookIfSet(transCompleteHook, tableName);
            return;
        }

        /* unexpected failure, no retry */
        if (cause != null) {
            getMetrics().getTableMetrics(tableName).getRegionInitStat(region)
                        .setState(ERROR);
            mdMan.writeTableInitCkpt(region, tableName, lastPersistPkey, ERROR,
                                     cause.getMessage());
            final String msg = "Fail to copy table=" + tableName +
                               " from region=" + srcRegion.getName() +
                               " in attempts=" + attempts +
                               ", cause=" +
                               cause.getClass().getCanonicalName() +
                               ", " + cause.getMessage() +
                               ", " + dumpStat() +
                               (logger.isLoggable(Level.FINE) ? "\n" +
                                   LoggerUtils.getStackTrace(cause) : "");
            logger.warning(lm(msg));
            return;
        }

        /* shutdown requested */
        getMetrics().getTableMetrics(tableName).getRegionInitStat(region)
                    .setState(SHUTDOWN);
        logger.info(lm("Shutdown requested before completing" +
                       " transfer table=" + tableName +
                       " in attempts=" + attempts));
        logger.fine(() -> lm(dumpStat()));
    }

    @Override
    protected int initiateSoftShutdown() {
        if (!isShutdown) {
            isShutdown = true;
        }

        logger.fine(lm("Signal thread " + getName() + " to shutdown" +
                       ", wait for " + SOFT_SHUTDOWN_WAIT_MS +
                       " ms to let it exit"));
        return SOFT_SHUTDOWN_WAIT_MS;
    }

    /**
     * Shuts down the transfer thread
     */
    public void shutdown() {

        if (isShutdown) {
            logger.fine(lm("Shutdown already signalled"));
            return;
        }

        shutdownThread(logger);
        logger.info(lm("Shuts down TI thread " + getName() + " completely."));
    }

    /**
     * Gets the cause of failure, or null if transfer is complete or shutdown
     * by the parent agent
     *
     * @return the cause of failure, or null.
     */
    public Exception getCause() {
        return cause;
    }

    /**
     * Returns true if the transfer is complete, false otherwise
     *
     * @return true if the transfer is complete, false otherwise
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Returns true if the transfer has failed, false otherwise
     *
     * @return true if the transfer has failed, false otherwise
     */
    public boolean hasFailed() {
        return cause != null;
    }

    /**
     * Returns true if need report progress, false otherwise
     *
     * @param rowsTrans # rows already transferred
     */
    private boolean reportProgress(long rowsTrans, long lastReported) {
        return (rowsTrans > 0) &&
               (rowsTrans % getTableReportIntv() == 0) &&
               rowsTrans > lastReported; /* report only when progress is made */
    }

    /**
     * Transfer table from source region. The transfer will start from a
     * given start key each time and update the start key during transfer.
     * It will return
     * - transfer is complete, or
     * - shutdown is requested, or
     * - transfer fails because an exception is thrown
     *
     * @param attempts # of attempts
     */
    private void transTable(int attempts) {
        TableIterator<Row> iterator = null;
        long lastReportedRows = 0; /* # of transferred rows in last report */
        try {
            complete = false;

            //TODO: because the table api does not support iterator
            // to resume from a particular key, a client-side filtering is
            // implemented as temporary fix. That is, the client streams all
            // rows from the table and filter out those before the
            // checkpoint. This saves the persistent cost for rows that
            // have been streamed. In future when the table api support
            // resume table iterator, we should use that to implement
            // more efficient server-side filtering table scan [KVSTORE-613].

            /* create iterator from given start key */
            iterator = srcAPI.tableIterator(
                srcTable.createPrimaryKey(), null, iter_opt);
            while (!isShutdown && iterator.hasNext()) {
                final Row srcRow = iterator.next();
                if (!isFullScan(srcTable, ckptKey) &&
                    equalOrBefore(srcRow.createPrimaryKey(), ckptKey)) {
                    continue;
                }

                pushRow(srcRow);
                lastPersistPkey = srcRow.createPrimaryKey();

                /* report progress */
                final String region = srcRegion.getName();
                final TableInitStat tm = getMetrics().getTableMetrics(tableName)
                                                     .getRegionInitStat(region);
                final long rows = tm.getTransferRows();
                if (reportProgress(rows, lastReportedRows)) {
                    if (parent != null) {
                        /* update checkpoint if not unit test  */
                        parent.getMdMan().writeTableInitCkpt(
                            region, tableName, lastPersistPkey, IN_PROGRESS,
                            null);
                    }
                    lastReportedRows = rows;
                    logger.info(lm("Table=" + tableName + "(id=" +
                                   getMetrics().getTableMetrics(tableName)
                                               .getTableId() + ")" +
                                   " transfer progress" +
                                   " in attempts=" + attempts +
                                   ": # rows transferred=" +
                                   tm.getTransferRows() +
                                   ", # rows persisted=" +
                                   tm.getPersistRows()));
                }

                /* unit test only: simulate failure */
                fireTestHookInTransfer(rows);
            }

            if (!isShutdown) {
                complete = true;
            }
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    private int getTableThreads() {
        if (parent == null) {
            /* unit test */
            return DEFAULT_THREADS_TABLE_ITERATOR;
        }
        return parent.getMdMan().getJsonConf().getTableThreads();
    }

    private int getTableBatchSz() {
        if (parent == null) {
            /* unit test */
            return DEFAULT_BATCH_SIZE_PER_REQUEST;
        }
        return parent.getMdMan().getJsonConf().getTableBatchSz();
    }

    private int getTableReportIntv() {
        if (parent == null) {
            /* unit test */
            return DEFAULT_ROWS_REPORT_PROGRESS_INTV;
        }
        return parent.getMdMan().getJsonConf().getTableReportIntv();
    }

    /*
     * Stat reference may change if interval stat is collected, thus get
     * the reference from parent instead of keeping a constant reference
     */
    protected BaseRegionAgentMetrics getMetrics() {
        if (parent == null) {
            /* unit test only */
            return metrics;
        }
        return parent.getMetrics();
    }

    /**
     * unit test only
     */
    protected void setMetrics(BaseRegionAgentMetrics val) {
        metrics = val;
    }

    /**
     * Fires test hook, unit test only
     *
     * @param rows # rows transferred
     */
    private void fireTestHookInTransfer(long rows) {
        try {
            /* test hook to throw exception */
            assert ExceptionTestHookExecute.doHookIfSet(expHook, rows);
            /* test hook in transfer */
            assert TestHookExecute.doHookIfSet(transInProgressHook,
                                               new Pair<>(tableName, rows));
        } catch (Exception exp) {
            final String err = exp.getMessage();
            logger.warning(lm("TEST ONLY: cause=" + err));
            throw new IllegalStateException(err, exp);
        }
    }

    private String getKeyHash(PrimaryKey key) {
        if (key == null) {
            return "null";
        }
        return UserDataControl.getHash(key.toJsonString(false).getBytes());
    }

    private boolean isFullScan(Table table, PrimaryKey primaryKey) {
        return primaryKey.equals(table.createPrimaryKey());
    }

    /**
     * Builds primary key from checkpoint
     */
    private PrimaryKey buildPrimaryKey(TableInitCheckpoint ckpt) {
        PrimaryKey ret;
        if (NOT_START.equals(ckpt.getState())) {
            ret = srcTable.createPrimaryKey();
        } else {
            final String json = ckpt.getPrimaryKey();
            ret = srcTable.createPrimaryKeyFromJson(json, true);
        }
        logger.info(lm("Table=" + tableName +
                       (isFullScan(srcTable, ret) ?
                           " requires full scan" :
                           " has start key hash=" + getKeyHash(ret))));
        return ret;
    }

    /**
     * Returns true if the key1 is before or equal to the key2 in ascending
     * table iteration order
     */
    private boolean equalOrBefore(PrimaryKey pkey1, PrimaryKey pkey2) {
        final TableImpl impl1 = (TableImpl) pkey1.getTable();
        final TableImpl impl2 = (TableImpl) pkey2.getTable();
        if (impl1.getId() != impl2.getId()) {
            throw new IllegalStateException(
                "Key1 is from table=" + impl1.getFullNamespaceName() +
                "(id=" + impl1.getId() + ")" +
                " while key2 is from table=" +
                pkey2.getTable().getFullNamespaceName() +
                "(id=" + impl2.getId() + ")");
        }
        return pkey1.compareTo(pkey2) <= 0;
    }

    /**
     * Reads the primary key from checkpoint table
     *
     * @return primary key to resume scan, or null if table initialization is
     * complete, or the checkpoint is gone in the checkpoint table.
     * @throws CheckpointKeyException if unable to read the checkpoint
     */
    private PrimaryKey getCheckpointKey() throws CheckpointKeyException {
        /* build primary key from checkpoint, or a full table scan */
        if (parent == null) {
            /* some unit test only, always full table scan */
            return srcTable.createPrimaryKey();
        }

        /* normal case */
        final String region = srcRegion.getName();
        final TableInitCheckpoint ckpt;
        try {
            ckpt = parent.getMdMan().readTableInitCkpt(region, tableName);
        } catch (IllegalStateException exp) {
            final String err = "Cannot read checkpoint for table=" + tableName +
                               ", " + exp.getMessage();
            throw new CheckpointKeyException(err, exp);
        }

        if (ckpt == null) {
            final String msg = "No checkpoint for table=" + tableName +
                               ", table might be dropped from region=" +
                               srcRegion.getName();
            logger.info(lm(msg));
            return null;
        }
        if (COMPLETE.equals(ckpt.getState())) {
            final String msg = "Skip transfer because initialization " +
                               "of table=" + tableName + " is already " +
                               "complete";
            logger.info(lm(msg));
            return null;
        }
        /* get the primary key from checkpoint */
        return buildPrimaryKey(ckpt);
    }

    /**
     * Exception thrown when unable to get the primary key from the checkpoint
     */
    private static class CheckpointKeyException extends IllegalStateException {
        private static final long serialVersionUID = 1;
        CheckpointKeyException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Uncaught exception handler
     */
    private class ExceptionHandler implements UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.warning(lm("Uncaught exception in transfer thread for" +
                              " table=" + tableName +
                              " from region=" + srcRegion.getName() +
                              ", thread=" + t.getName() +
                              ", id=" + t.getId() +
                              ", " + e.getMessage() +
                              "\n" + LoggerUtils.getStackTrace(e)));
        }
    }

    /**
     * Skips check redundant table transfer. The polling thread should call
     * it to disable the check, because transfer thread submitted by the
     * polling thread cannot be redundant.
     */
    protected void skipCheckRedundant() {
        checkRedundant = false;
    }

    private boolean isRedundantTransfer() {
        if (parent == null) {
            /* unit test only */
            return false;
        }
        /*
         * since the region agent only transfers one table at a time, when
         * this thread is scheduled to run, all previous transfers have
         * either been complete or terminated because of errors. In either
         * case we can look at the state of the checkpoint table to determine
         * if a table has been transferred. Here we only need to check the
         * polling thread to see if the table has been scheduled to transfer.
         */
        return parent.inPolling(tableName);
    }

    private void retryLog(Throwable exp, int attempts) {
        /* retry transfer */
        final String msg =
            "Unable to copy table=" + tableName +
            "(id=" + ((TableImpl) srcTable).getId() +
            ", ver=" + srcTable.getTableVersion() + ")" +
            " from region=" + srcRegion.getName() +
            " in attempts=" + attempts +
            ", reason=" + exp.getClass().getCanonicalName() +
            ", " + exp.getMessage() +
            ", will retry from checkpoint key hash=" +
            ckptKey.hashCode() +
            (logger.isLoggable(Level.FINE) ?
                LoggerUtils.getStackTrace(exp) : "");
        logger.info(lm(msg));
    }
}
