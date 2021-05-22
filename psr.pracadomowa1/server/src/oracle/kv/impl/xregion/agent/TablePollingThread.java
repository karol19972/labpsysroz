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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.xregion.agent.RegionAgentThread.RegionAgentReq;
import oracle.kv.impl.xregion.agent.mrt.MRTSubscriber;
import oracle.kv.impl.xregion.agent.mrt.MRTTableTransferThread;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.pubsub.NoSQLStreamMode;
import oracle.kv.pubsub.StreamPosition;

import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * A thread that lives in {@link RegionAgentThread} and periodically polling
 * a remote region for given tables, and start initialization for any found
 * table.
 */
public class TablePollingThread extends StoppableThread {

    //TODO: make them configurable in JSON config
    /**
     * default polling interval in ms
     */
    static final int DEFAULT_POLL_INTV_MS = 30 * 1000;
    /**
     * wait time in ms during soft shutdown
     */
    private static final int SOFT_SHUTDOWN_WAIT_MS = 5 * 1000;
    /**
     * Max parallel table transfer
     */
    private static final int MAX_PARALLEL_TABLE_TRANSFER = 2;
    /**
     * Max tables waiting in queue to be transferred
     */
    private static final int MAX_WAITING_TABLE_TRANSFER = 10;
    /**
     * parent region agent thread
     */
    private final RegionAgentThread parent;
    /**
     * private logger
     */
    private final Logger logger;
    /**
     * tables to check
     */
    private final Set<String> tablesToCheck;
    /**
     * tables found and submitted to transfer
     */
    private final Set<String> tablesInTrans;
    /**
     * requests that need response after table initialized
     */
    private final Set<RegionAgentReq> requests;
    /**
     * handle to metadata manager
     */
    private final ServiceMDMan mdMan;
    /**
     * Source region
     */
    private final RegionInfo source;
    /**
     * Target region
     */
    private final RegionInfo target;
    /**
     * Executor for scheduled task
     */
    private final ThreadPoolExecutor ec;
    /**
     * true if shut down is requested
     */
    private final AtomicBoolean shutdownRequested;
    /**
     * Polling interval in ms
     */
    private volatile int pollIntvMs;


    TablePollingThread(RegionAgentThread parent, Logger logger) {
        super("TablePollingThread-" + parent.getAgentId());
        this.parent = parent;
        this.logger = logger;
        pollIntvMs = DEFAULT_POLL_INTV_MS;
        mdMan = parent.getMdMan();
        source = parent.getSourceRegion();
        target = parent.getTargetRegion();
        shutdownRequested = new AtomicBoolean(false);
        tablesToCheck = new HashSet<>();
        tablesInTrans = new HashSet<>();
        requests = new HashSet<>();
        ec = new ThreadPoolExecutor(
            MAX_PARALLEL_TABLE_TRANSFER, MAX_PARALLEL_TABLE_TRANSFER,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_WAITING_TABLE_TRANSFER));
    }

    /**
     * Returns true if the table is in checklist
     * @param table table to check
     * @return true if the table is in checklist
     */
    public boolean inPolling(String table) {
        synchronized (tablesToCheck) {
            return tablesToCheck.contains(table);
        }
    }

    /**
     * Returns true if the table is found and scheduled to transfer
     * @param table table name
     * @return true if the table is found and scheduled to transfer
     */
    boolean inTrans(String table) {
        synchronized (tablesInTrans) {
            return tablesInTrans.contains(table);
        }
    }

    /**
     * Adds the specified tables to the in-transfer set
     * @param tables the table names
     */
    void addTableInTrans(Set<String> tables) {
        synchronized (tablesInTrans) {
            tablesInTrans.addAll(tables);
        }
    }

    /**
     * Removes the specified tables from the in-transfer set
     * @param tables the table names
     */
    void removeTableFromInTrans(Set<String> tables) {
        synchronized (tablesInTrans) {
            tablesInTrans.removeAll(tables);
        }
    }

    /**
     * Adds tables to check list
     * @param tbls tables
     */
    void addTables(RegionAgentReq req, Set<String> tbls) {
        if (tbls == null || tbls.isEmpty()) {
            return;
        }
        synchronized (tablesToCheck) {
            /* do not add if already in the list */
            final Set<String> inList = new HashSet<>();
            for (String tb : tbls) {
                if (!tablesToCheck.add(tb)) {
                    inList.add(tb);
                }
            }
            if (!inList.isEmpty()) {
                logger.info(lm("Table=" + inList + " already in checklist"));
            }
            requests.add(req);
            logger.info(lm("New tables=" +
                           tbls.stream().filter(t -> !inList.contains(t))
                               .collect(Collectors.toSet()) +
                           " added to check list=" +
                           tablesToCheck + " from request=[" + req + "]"));
            tablesToCheck.notifyAll();
        }
    }

    /**
     * Removes tables from check list
     * @param tbls tables to remove
     */
    void removeTableFromCheckList(Set<String> tbls) {
        if (tbls == null || tbls.isEmpty()) {
            return;
        }
        synchronized (tablesToCheck) {
            tablesToCheck.removeAll(tbls);
            logger.fine(() -> lm("Tables=" + tbls + " removed from check " +
                                 "list=" + tablesToCheck));
        }
    }

    /**
     * Stops the thread from another thread without error
     */
    void shutDown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            logger.fine(() -> lm("Thread already shut down"));
            return;
        }
        synchronized (tablesToCheck) {
            tablesToCheck.notifyAll();
        }
        if (ec != null) {
            ec.shutdownNow();
            logger.fine(lm("Executor shutdown"));
        }
        shutdownThread(logger);
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected int initiateSoftShutdown() {
        logger.fine(lm("Wait for thread " + getName() + " to shutdown" +
                       " in time(ms)=" + SOFT_SHUTDOWN_WAIT_MS));
        return SOFT_SHUTDOWN_WAIT_MS;
    }

    @Override
    public void run() {

        logger.info(lm("Table polling thread starts, source region=" +
                       source.getName() + ", polling interval(ms)=" +
                       pollIntvMs));
        try {
            while (!shutdownRequested.get()) {
                synchronized (tablesToCheck) {
                    while (tablesToCheck.isEmpty()) {
                        tablesToCheck.wait();
                        if (shutdownRequested.get()) {
                            return;
                        }
                    }
                }
                /* check table at source region */
                final Set<String> found = checkTable();
                if (found.isEmpty()) {
                    logger.fine(() -> lm("Cannot find any of tables=" +
                                         tablesToCheck));
                    Thread.sleep(pollIntvMs);
                    continue;
                }

                final Set<String> initialized = initializeTables(found);
                removeTableFromCheckList(initialized);
                addTableInTrans(initialized);
                logger.info(lm("Tables submitted to initialize=" + initialized +
                               ", all submitted=" + tablesInTrans +
                               ", in check list=" + tablesToCheck));

                if (!initialized.isEmpty()) {
                    postSucc();
                }
                Thread.sleep(pollIntvMs);
            }
        } catch (ExecutorShutDownException ese) {
            logger.fine(() -> "Executor shuts down, " + ese.getMessage() +
                              ", table to check=" + tablesToCheck);
        } catch (InterruptedException ie) {
            logger.fine(() -> "Interrupted in waiting and exit, " +
                              ", table to check=" + tablesToCheck);
        } catch (Exception exp) {
            logger.warning(lm("Polling thread shuts down on error " +
                              exp.getMessage() + ", stack:\n" +
                              LoggerUtils.getStackTrace(exp)));
        } finally {
            logger.info(lm("Polling thread exits" +
                           ", shutdown request=" + shutdownRequested +
                           (tablesToCheck.isEmpty() ? "" :
                               ", tables=" + tablesToCheck)));
        }
    }

    /**
     * Returns tables in check list
     */
    public Set<String> getTablesToCheck() {
        return tablesToCheck;
    }

    /**
     * Unit test only
     */
    public Set<RegionAgentReq> getRequests() {
        return requests;
    }

    /**
     * Unit test only
     */
    void setPollIntvMs(int val) {
        pollIntvMs = val;
    }

    /**
     * Unit test only
     */
    int getPollIntvMs() {
        return pollIntvMs;
    }

    /*--------------------*
     * Private functions  *
     *--------------------*/
    private String lm(String msg) {
        return "[TablePollingThread-" +
               parent.getSourceRegion().getName() + "-" +
               parent.getSid() + "] " + msg;
    }

    /**
     * Initializes the given tables
     * @param tbls tables to initialize
     * @return set of tables that are initialized successfully
     * @throws ExecutorShutDownException if executor shuts down when
     * submitting new table transfer
     */
    private Set<String> initializeTables(Set<String> tbls)
        throws ExecutorShutDownException {

        /* adding found tables to running stream */
        final Map<String, StreamPosition> inStream = new HashMap<>();
        for (String t : tbls) {
            final StreamPosition sp = addTableHelper(t);
            if (sp != null) {
                inStream.put(t, sp);
                continue;
            }
            logger.info(lm("Fail to add table=" + t + " to stream"));
        }
        if (inStream.keySet().isEmpty()) {
            return Collections.emptySet();
        }

        final Set<String> transfer = new HashSet<>();
        final Set<String> skipped = new HashSet<>();
        logger.info(lm("Added tables(position) to stream=" +
                       inStream.entrySet().stream()
                               .map(e -> e.getKey() + "(pos=" +
                                         e.getValue() + ")")
                               .collect(Collectors.toSet())));
        /* start table transfer thread */
        final TableAPIImpl srcAPI =
            (TableAPIImpl) mdMan.getRegionKVS(source).getTableAPI();
        final TableAPIImpl tgtAPI =
            (TableAPIImpl) mdMan.getRegionKVS(target).getTableAPI();
        final MRTSubscriber sub = (MRTSubscriber) parent.getSubscriber();
        for(String table: inStream.keySet()) {
            try {
                /* create table initialization thread */
                final MRTTableTransferThread tc = new MRTTableTransferThread(
                    parent, sub, table, source, target, srcAPI, tgtAPI, logger);
                /* avoid confuse itself as duplicate */
                tc.skipCheckRedundant();
                ec.submit(tc);
                transfer.add(table);
            } catch (MRTTableTransferThread.MRTableNotFoundException exp) {
                logger.warning(lm("Skip table=" + exp.getTable() +
                                  " might be already dropped at region=" +
                                  exp.getRegion()));
                skipped.add(exp.getTable());
            } catch (RejectedExecutionException ree) {
                if (ec.isShutdown()) {
                    /* executor has shut down, surface and thread exits */
                    final String msg = "in submitting table=" + table;
                    throw new ExecutorShutDownException(msg);
                }

                /*
                 * Cannot submit new task, probably the queue of executor is
                 * full, return tables that are successfully submitted for
                 * transfer. The remaining will be transferred next time.
                 */
                final Throwable cause = ree.getCause();
                logger.info(lm("Cannot submit table=" + table + " for " +
                               "transfer, message=" + ree.getMessage() +
                               ", cause=" + (cause == null ? "na" : cause) +
                               ", tables submitted=" + transfer +
                               ", tables skipped=" + skipped +
                               ", tables not submitted=" +
                               inStream.keySet().stream()
                                       .filter(t -> !transfer.contains(t) &&
                                                    !skipped.contains(t))
                                       .collect(Collectors.toSet())));
                if (cause != null)  {
                    logger.fine(() -> "Cause of rejection=" + cause +
                                      ", message: " + cause.getMessage() +
                                      ", stack: " +
                                      LoggerUtils.getStackTrace(cause));
                }
                break;
            }
        }

        return transfer;
    }

    /**
     * Checks if the tables in the list are existent at source region
     * @return tables that are existent
     */
    private Set<String> checkTable() {

        /* check table at source region */
        final Set<String> found = mdMan.checkTable(source, tablesToCheck);

        /* check schema for each found table, dump warning if incompatible */
        found.forEach(this::checkSchema);

        /* dump warnings for not-found tables, hope user will catch */
        final Set<String> notFound =
            tablesToCheck.stream().filter(t -> !found.contains(t))
                         .collect(Collectors.toSet());
        if (!notFound.isEmpty()) {
            logger.warning(lm("Cannot find table=" + notFound +
                              " from region=" + source.getName()));
        }
        return found;
    }

    private void checkSchema(String table) {
        final String result =
            parent.getMdMan().matchingSchema(parent.getSourceRegion(),
                                             Collections.singleton(table));
        if (result != null) {
            String sb = "Incompatible table schema, source=" +
                        parent.getSourceRegion().getName() +
                        ", target=" +
                        parent.getMdMan().getServRegion().getName() +
                        ", result=" + result;
            logger.warning(lm(sb));
        }
    }

    /**
     * Adds the table to the stream
     * @param table table to add
     * @return stream position where the table is added, or null if fail to
     * add the table to stream
     */
    private StreamPosition addTableHelper(String table) {
        try {
            if (parent.isStreamOn()) {
                return parent.changeStream(null/* no response needed */, table,
                                           RegionAgentThread.ReqType.ADD);
            }

            /* stream already closed, need to create a new one */
            final NoSQLStreamMode mode =
                RegionAgentThread.INIT_TABLE_STREAM_MODE;
            if(parent.startStream(mode, Collections.singleton(table), null)) {
                logger.info(lm("Create a new stream from=" + source.getName() +
                               " for table=" + table + " with mode=" + mode));
                return parent.getSubscriber().getSubscription()
                             .getCurrentPosition();
            }
        } catch (RegionAgentThread.ChangeStreamException cse) {
            final String err = "Fail to add table=" + cse.getTable() +
                               ", " + cse.getError();
            logger.warning(lm(err));
        } catch (InterruptedException e) {
            final String err = "Interrupted in adding table=" + table;
            logger.warning(lm(err));
        }
        return null;
    }

    private static class ExecutorShutDownException extends RuntimeException {
        private static final long serialVersionUID = 1;
        ExecutorShutDownException(String msg) {
            super(msg);
        }
    }

    private boolean tableInitDone(RegionAgentReq req) {
        return req.getTables().stream()
                  .noneMatch(tablesToCheck::contains);
    }

    /**
     * Checks each request to see if all tables are found, if yes, post
     * success for the request
     */
    private void postSucc() {
        final Iterator<RegionAgentReq> iter = requests.iterator();
        while (iter.hasNext()) {
            final RegionAgentReq req = iter.next();
            if (tableInitDone(req)) {
                parent.postSucc(req.getResp());
                iter.remove();
            }
        }
    }
}
