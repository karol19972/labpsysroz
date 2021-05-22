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

import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.COMPLETE;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.NOT_START;
import static oracle.kv.pubsub.NoSQLStreamMode.FROM_NOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import oracle.kv.FaultException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreException;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.StoreIteratorException;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.param.Parameter;
import oracle.kv.impl.pubsub.NoSQLSubscriptionImpl;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.util.KVThreadFactory;
import oracle.kv.impl.util.PollCondition;
import oracle.kv.impl.util.RateLimitingLogger;
import oracle.kv.impl.util.server.LoggerUtils;
import oracle.kv.impl.xregion.agent.mrt.MRTAgentMetrics;
import oracle.kv.impl.xregion.agent.mrt.MRTSubscriber;
import oracle.kv.impl.xregion.agent.mrt.MRTTableTransferThread;
import oracle.kv.impl.xregion.agent.pitr.PITRAgentMetrics;
import oracle.kv.impl.xregion.agent.pitr.PITRSubscriber;
import oracle.kv.impl.xregion.init.TableInitCheckpoint;
import oracle.kv.impl.xregion.service.MRTableMetrics;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.impl.xregion.service.StatusUpdater;
import oracle.kv.impl.xregion.service.XRegionRespHandlerThread;
import oracle.kv.impl.xregion.service.XRegionService;
import oracle.kv.impl.xregion.stat.TableInitStat;
import oracle.kv.pubsub.NoSQLPublisher;
import oracle.kv.pubsub.NoSQLPublisherConfig;
import oracle.kv.pubsub.NoSQLStreamMode;
import oracle.kv.pubsub.NoSQLSubscriberId;
import oracle.kv.pubsub.NoSQLSubscription;
import oracle.kv.pubsub.NoSQLSubscriptionConfig;
import oracle.kv.pubsub.PublisherFailureException;
import oracle.kv.pubsub.StreamPosition;
import oracle.kv.table.TableAPI;

import com.sleepycat.je.utilint.StoppableThread;


/**
 * Object serves as the agent for a source region and a target region. It is
 * responsible for
 * - streaming subscribed tables from the source region;
 * - writing streamed data to target region;
 * - initializing tables in the target region from the source region;
 * - managing underlying stream from the source region;
 * - changing parameters of subscribed tables;
 * - statistics collection, etc
 */
public class RegionAgentThread extends StoppableThread {

    /** hook to pause the execution */
    public static volatile TestHook<RegionAgentReq> pauseHook = null;

    /** default wait time to check table to accommodate stale table md */
    private static final int DEFAULT_REMOTE_TABLE_TIMEOUT_MS = 10 * 1000;

    /** rate limiting log period in ms */
    private static final int RL_LOG_PERIOD_MS = 30 * 1000;

    /** default checkpoint table name space */
    private static final String DEFAULT_CKPT_TABLE_NAMESPACE =
        TableAPI.SYSDEFAULT_NAMESPACE_NAME;

    /** empty stream expires after all tables removed from the stream */
    private static final int DEFAULT_EMPTY_STREAM_TIMEOUT_SECS = 60 * 60;

    /** sleep in ms before reconnect if remote store is unreachable  */
    private static final int SLEEP_MS_BEFORE_RECONNECT = 10 * 1000;

    /** polling internal in ms */
    private static final int POLL_INTERVAL_MS = 1000;

    /** indefinite # of retry when source region is not reachable */
    private static final long DEFAULT_MAX_RECONNECT = Long.MAX_VALUE;

    /** timeout in in last ckpt */
    //TODO: configuring this value
    private static final int LAST_CKPT_TIMEOUT_MS = 1000;

    /** default collector */
    private static final Collector<CharSequence, ?, String> DEFAULT_COLL =
        Collectors.joining(",", "[", "]");

    /** wait time in ms during soft shutdown, give enough time for last ckpt */
    private static final int SOFT_SHUTDOWN_WAIT_MS = 10 * LAST_CKPT_TIMEOUT_MS;

    /** max number of message in the queue */
    private static final int MAX_MSG_QUEUE_SIZE = 1024;

    /** timeout in ms to poll message queue */
    private static final int MSG_QUEUE_POLL_TIMEOUT_MS = 1000;

    /** timeout in ms to put message queue */
    private static final int MSG_QUEUE_PUT_TIMEOUT_MS = 1000;

    /** prefix of region id */
    private static final String ID_PREFIX = "RA";

    /** prefix of checkpoint table name */
    private static final String CKPT_PREFIX = "RegionAgentCkpt";

    /** default stream mode to start from checkpoint */
    private final static NoSQLStreamMode DEFAULT_STREAM_MODE =
        NoSQLStreamMode.FROM_EXACT_CHECKPOINT;

    /** special request to notify the agent to shut down */
    private static final RegionAgentReq SHUTDOWN_REQ = new ShutDownRequest();

    /** stream mode if init is needed to start from now */
    final static NoSQLStreamMode INIT_TABLE_STREAM_MODE = FROM_NOW;

    /** id of the region agent */
    private final String agentId;

    /** private logger */
    private final Logger logger;

    /** metadata manager */
    private final ServiceMDMan mdMan;

    /** FIFO queue of messages of actions */
    private final BlockingQueue<RegionAgentReq> msgQueue;

    /** agent configuration */
    private final RegionAgentConfig config;

    /** agent statistics */
    private final AtomicReference<BaseRegionAgentMetrics> metrics;

    /** executor for scheduled task */
    private final ScheduledExecutorService ec;

    /** internal publisher in the agent */
    private NoSQLPublisher publisher;

    /** internal subscriber */
    private BaseRegionAgentSubscriber subscriber;

    /** status updater */
    private final StatusUpdater statusUpd;

    /** true if shut down the agent */
    private volatile boolean shutdownRequested;

    /** status of the agent */
    private volatile RegionAgentStatus status;

    /** stored cause of failure */
    private volatile Throwable storedCause;

    /** rate limiting logger */
    private final RateLimitingLogger<String> rlLogger;

    /** table polling thread from source region */
    private final TablePollingThread tablePollingThread;

    /** get remote table timeout */
    private volatile int remoteTableTimeoutMs;

    /** currently processing request */
    private volatile RegionAgentReq currentReq;

    /**
     * Creates an instance of change subscriber
     *
     * @param config    stream configuration
     * @param mdMan     metadata manager
     * @param statusUpd status updater
     * @param logger    logger
     * @throws PublisherFailureException if fail to create the publisher
     */
    public RegionAgentThread(RegionAgentConfig config,
                             ServiceMDMan mdMan,
                             StatusUpdater statusUpd,
                             Logger logger)
        throws PublisherFailureException {

        super("RAThread" + config.getSource().getName());
        this.mdMan = mdMan;
        this.config = config;
        this.statusUpd = statusUpd;
        this.logger = logger;
        rlLogger = new RateLimitingLogger<>(RL_LOG_PERIOD_MS, 1024, logger);

        storedCause = null;
        final RegionAgentThreadFactory factory = new RegionAgentThreadFactory();
        ec = Executors.newSingleThreadScheduledExecutor(factory);
        shutdownRequested = false;

        final String src = config.getSource().getName();
        agentId = ID_PREFIX + config.getSubscriberId() + "." +
                  config.getType() + "." + src;

        final BaseRegionAgentMetrics st =
            createMetrics(config.getType(),
                          config.getSource().getName(),
                          config.getTarget().getName());
        metrics = new AtomicReference<>(st);
        subscriber = null;
        msgQueue = new ArrayBlockingQueue<>(MAX_MSG_QUEUE_SIZE);
        tablePollingThread = new TablePollingThread(this, logger);
        remoteTableTimeoutMs = DEFAULT_REMOTE_TABLE_TIMEOUT_MS;
        status = RegionAgentStatus.IDLE;
        currentReq = null;
        logger.fine(() -> lm("Start agent " + agentId +
                             " for table " +
                             Arrays.toString(config.getTables()) +
                             " with mode " + config.getStartMode()));
    }

    /* Public APIs */
    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected int initiateSoftShutdown() {
        logger.fine(() -> lm("Signal thread " + getName() + " to shutdown" +
                             ", wait up to " + SOFT_SHUTDOWN_WAIT_MS +
                             " ms to let it exit"));
        return SOFT_SHUTDOWN_WAIT_MS;
    }

    @Override
    public void run() {

        logger.info(lm("Region agent starts, mode=" + config.getStartMode() +
                       ", tables=" + Arrays.toString(config.getTables())));

        try {
            initializeAgent();

            while (!isShutdownRequested()) {

                currentReq = msgQueue.poll(MSG_QUEUE_POLL_TIMEOUT_MS,
                                           TimeUnit.MILLISECONDS);
                if (currentReq == null) {
                    logger.finest(() -> lm("Unable to dequeue request for " +
                                           MSG_QUEUE_POLL_TIMEOUT_MS + " ms"));
                    continue;
                }

                if (currentReq instanceof
                        ShutDownRequest || isShutdownRequested()) {
                    logger.fine(() -> lm("Shutdown requested"));
                    break;
                }

                /* unit test: pause execution if set */
                assert TestHookExecute.doHookIfSet(pauseHook, currentReq);

                logger.fine(() -> lm("Request " + currentReq.toString()));
                switch (currentReq.reqType) {

                    case STREAM:
                        createStreamFromSource(currentReq);
                        break;

                    case INITIALIZE_FROM_REGION:
                        initializeRegion(currentReq);
                        break;

                    case INITIALIZE_TABLES:
                        initializeTables(currentReq);
                        break;

                    case ADD:
                        addTablesToStream(currentReq);
                        break;

                    case REMOVE:
                        removeTablesFromStream(currentReq);
                        break;

                    case CHANGE_PARAM:
                        changeParam(currentReq);
                        break;

                    default:
                        final String err = "Unsupported request:" +
                                           ", type=" + currentReq.reqType +
                                           ", id=" +
                                           currentReq.getResp().getReqId() +
                                           ", tables=" + currentReq.getTables();
                        logger.warning(lm(err));
                        throw new IllegalStateException(err);
                }
            }
        } catch (TransferTableException tte) {
            storedCause = tte;
            String error = "Shut down agent because fail to transfer table=" +
                           tte.getFailed() + ", completed=" +
                           tte.getTransferred() + ", remaining=" +
                           tte.getRemaining();
            if (tte.getCause() != null) {
                final Throwable cause = tte.getCause();
                error += ", cause=" + cause.getClass().getCanonicalName() +
                         "error=" + cause.getMessage() +
                         (logger.isLoggable(Level.FINE) ?
                             LoggerUtils.getStackTrace(cause) : "");
            }
            logger.warning(lm(error));
        } catch (InterruptedException ie) {
            if (!isShutdownRequested()) {
                storedCause = ie;
                logger.warning(lm("Interrupted with status=" + status +
                                  ", agent exits"));
            }
        } catch (Exception exp) {
            storedCause = exp;
            logger.warning(lm("Shut down agent due to error=" +
                              exp.getClass().getCanonicalName() + ", " +
                              exp.getMessage() + ", status=" + status +
                              ", call stack " +
                              LoggerUtils.getStackTrace(exp)));
        } finally {
            close();
            logger.info(lm("Agent exits"));
        }
    }

    public static NoSQLStreamMode getInitTableStreamMode() {
        return INIT_TABLE_STREAM_MODE;
    }

    /**
     * Returns true if shut down is requested, false otherwise
     */
    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    /**
     * Returns metadata manager
     *
     * @return metadata manager
     */
    public ServiceMDMan getMdMan() {
        return mdMan;
    }

    /**
     * Stops the agent thread from another thread without error
     */
    public void shutDown() {

        if (shutdownRequested) {
            logger.fine(() -> lm("Agent is in shutdown or already shut " +
                                 "down, current stream status " + status));
            return;
        }
        shutdownRequested = true;

        closePendingRequests();

        if (subscriber != null) {
            subscriber.shutdown();
        }

        /* notify main loop to shut down */
        msgQueue.offer(SHUTDOWN_REQ);
        synchronized (msgQueue) {
            msgQueue.notifyAll();
        }

        shutdownThread(logger);
    }

    /**
     * Returns region agent id
     *
     * @return region agent id
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns agent status
     *
     * @return agent status
     */
    public RegionAgentStatus getStatus() {
        return status;
    }

    /**
     * Returns the subscriber
     *
     * @return the subscriber
     */
    public BaseRegionAgentSubscriber getSubscriber() {
        return subscriber;
    }

    /**
     * Gets the metrics of underlying stream
     *
     * @return the metrics of underlying stream
     */
    public BaseRegionAgentMetrics getMetrics() {
        return metrics.get();
    }

    /**
     * Gets the metrics of underlying stream, and refresh if required.
     * @return the metrics of underlying stream
     */
    public synchronized BaseRegionAgentMetrics getMetricsRefresh() {
        /* refresh the stat and return the old one */
        final MRTAgentMetrics stat = getRefresh(metrics.get());
        /* plug in refreshed stat atomically */
        return metrics.getAndSet(stat);
    }

    /**
     * Returns a refreshed metrics built from this one. This is used in
     * reporting interval stat. After report, the region stat will be
     * refreshed in new cycle. This method is only public for use in unit
     * testing. Otherwise, this method should only be called if the
     * RegionAgentThread lock is held.
     * @return a refreshed metrics
     */
    public static MRTAgentMetrics getRefresh(BaseRegionAgentMetrics agentSt) {
        final String sourceRegion = agentSt.getSourceRegion();
        final String targetRegion = agentSt.getTargetRegion();
        final MRTAgentMetrics refreshed =
            new MRTAgentMetrics(sourceRegion, targetRegion);
        for (String s : agentSt.getTables()) {
            /* create a new table in new stats */
            final MRTableMetrics oldTM = agentSt.getTableMetrics(s);
            final MRTableMetrics newTM = new MRTableMetrics(targetRegion, s,
                                                            oldTM.getTableId());
            final Map<String, TableInitStat> stat = oldTM.getInitialization();
            for (String region : stat.keySet()) {
                /* create init statistics */
                final TableInitStat tis = newTM.getRegionInitStat(region);

                /* carry the transfer start,  end time, and state */
                final TableInitStat oldTis = stat.get(region);
                tis.setTransferStartMs(oldTis.getTransferStartMs());
                tis.setTransferCompleteMs(oldTis.getTransferCompleteMs());
                tis.setState(oldTis.getState());
            }

            /* add it to refreshed metrics */
            refreshed.addTable(newTM);
        }
        return refreshed;
    }

    /**
     * Returns table metrics, create one if not exist. Synchronized with the
     * stats refresh in {@link #getMetricsRefresh()}
     * @param tbl table name
     * @return table metrics
     */
    public synchronized MRTableMetrics getAddTable(String tbl, long tid) {
        final String region = config.getTarget().getName();
        final MRTableMetrics tm = metrics.get().getTableMetrics(tbl);
        /* verify by name and id*/
        if (tm != null && tm.getTableName().equals(tbl) &&
            tm.getTableId() == tid) {
            return tm;
        }
        /* a new table */
        final MRTableMetrics ret = new MRTableMetrics(region, tbl, tid);
        metrics.get().addTable(ret);
        return ret;
    }

    /**
     * Returns the cause of region agent failure
     *
     * @return the cause of region agent failure
     */
    public Throwable getCauseOfFaillure() {
        return storedCause;
    }

    /**
     * Returns true if the agent has been canceled, false otherwise.
     *
     * @return true if the agent has been canceled, false otherwise.
     */
    public boolean isCanceled() {
        return RegionAgentStatus.CANCELED.equals(status);
    }

    /**
     * Gets set of tables which are currently in streaming, or null if the
     * agent has been canceled.
     *
     * @return tables in streaming, or null if agent is canceled.
     */
    public Set<String> getTables() {
        if (isCanceled()) {
            return null;
        }
        if (subscriber == null) {
            return null;
        }
        final NoSQLSubscriptionImpl subImpl =
            (NoSQLSubscriptionImpl) subscriber.getSubscription();
        return subImpl.getSubscribedTables();
    }

    /**
     * Adds a table to the internal stream and wait for result
     *
     * @param resp      response handler
     * @param tableName name of table to add
     * @throws InterruptedException if interrupted during enqueue the message
     */
    public void addTable(XRegionRespHandlerThread resp, String tableName)
        throws InterruptedException {
        changeStream(ReqType.ADD, resp, tableName);
    }

    /**
     * Removes a table from the internal stream and wait for result
     *
     * @param resp      response handler
     * @param tableName name of table to add
     * @throws InterruptedException if interrupted during enqueue the message
     */
    public void removeTable(XRegionRespHandlerThread resp, String tableName)
        throws InterruptedException {
        changeStream(ReqType.REMOVE, resp, tableName);
    }

    /**
     * Changes parameters of tables
     *
     * @param param     parameters to change
     * @param tableName name of tables to to change parameter
     * @throws InterruptedException if interrupted during enqueue the message
     */
    public void changeParams(Set<Parameter> param, String... tableName)
        throws InterruptedException {
        enqueue(new ChangeParamRegionAgentReq(param, tableName));
    }

    /**
     * Initializes given tables from the source region
     *
     * @throws InterruptedException if fail to enqueue the message
     */
    void initTables(String[] tables) throws InterruptedException {
        enqueue(new RegionAgentReq(ReqType.INITIALIZE_TABLES, tables));
    }

    /**
     * Initializes all MR tables from the source region
     *
     * @throws InterruptedException if fail to enqueue the message
     */
    public void initFromRegion() throws InterruptedException {
        enqueue(new RegionAgentReq(ReqType.INITIALIZE_FROM_REGION));
    }

    /**
     * Creates a stream with given tables
     *
     * @param tables tables to stream
     * @throws InterruptedException if interrupted during enqueue
     */
    public void createStream(String[] tables) throws InterruptedException {
        enqueue(new RegionAgentReq(ReqType.STREAM, tables));
    }

    /**
     * Returns the source region from which to stream
     *
     * @return source region
     */
    public RegionInfo getSourceRegion() {
        return config.getSource();
    }

    /**
     * Returns the target region
     *
     * @return target region
     */
    RegionInfo getTargetRegion() {
        return config.getTarget();
    }

    /**
     * Gets a rate limiting logger
     */
    public RateLimitingLogger<String> getRlLogger() {
        return rlLogger;
    }

    /**
     * Gets the checkpoint interval in seconds
     *
     * @return the checkpoint interval in seconds
     */
    long getCkptIntvSecs() {
        return config.getCkptIntvSecs();
    }

    /**
     * Gets the checkpoint interval in terms of number of streamed ops
     *
     * @return the checkpoint interval in terms of number of streamed ops
     */
    long getCkptIntvNumOps() {
        return config.getCkptIntvNumOps();
    }

    /**
     * Gets number of concurrent stream ops
     * @return number of concurrent stream ops
     */
    public int getNumConcurrentStreamOps() {
        return config.getNumConcurrentStreamOps();
    }

    /**
     * Returns subscriber id of the agent
     * @return subscriber id of the agent
     */
    NoSQLSubscriberId getSid() {
        return config.getSubscriberId();
    }

    /*--------------------*
     * Private functions  *
     *--------------------*/
    private String lm(String msg) {
        return "[RA-MRT-from-" + config.getSource().getName() + "-" +
               config.getSubscriberId() + "] " + msg;
    }

    /**
     * Creates a stream from source with the table specified in the request,
     * with the default stream mode. If the stream cannot be created, it
     * would turn to initialization.
     *
     * @param req request body
     */
    private void createStreamFromSource(RegionAgentReq req)
        throws InterruptedException {
        final Set<String> tables = req.getTables();
        final XRegionRespHandlerThread resp = req.getResp();
        final String region = config.getSource().getName();
        final Set<String> found = ensureTable(req, tables);
        if (found.isEmpty()) {
            /* no table is found, skip creating stream */
            final String err = reqPrefix(req) +
                               "Cannot find any tables=" + tables +
                               "at region=" + region +
                               ", to initialize by polling thread";
            logger.warning(lm(err));
            status = RegionAgentStatus.INITIALIZING_TABLES;
            return;
        }

        final Set<String> notFound =
            tables.stream().filter(t -> !found.contains(t))
                  .collect(Collectors.toSet());
        if (notFound.isEmpty()) {
            logger.info(lm(reqPrefix(req) +
                           "All tables found, will create streams for " +
                           "tables=" + found + " from region=" + region));
        } else {
            logger.info(lm(reqPrefix(req) + "Create stream from region=" +
                           config.getSource().getName() +
                           ", not all tables found in time(ms)=" +
                           remoteTableTimeoutMs +
                           ", will create streams for found tables=" + found +
                           ". For tables not found=" + notFound +
                           ", the agent will continue polling and start " +
                           "initialization once the table is found."));
        }

        /* start stream with default mode */
        statusUpd.post(getSourceRegion(), status, found);
        final NoSQLStreamMode mode = DEFAULT_STREAM_MODE;
        if (startStream(mode, found, req)) {
            status = RegionAgentStatus.STREAMING;
            /* resume initialization */
            final Set<String> tbs = resumeTableInitialization(req);
            /* successfully created the stream */
            if (notFound.isEmpty()) {
                postSucc(resp);
            }
            /* dump trace */
            if (logger.isLoggable(Level.FINE)) {
                final NoSQLSubscriptionImpl handle =
                    ((NoSQLSubscriptionImpl)
                        subscriber.getSubscription());
                final String startPos = getStartPos(handle);
                logger.fine(() -> lm(
                    reqPrefix(req) + "Stream started from region=" +
                    config.getSource().getName() +
                    ", shards=" + handle.getCoveredShards() +
                    ", tables=" + found + ", mode=" + mode +
                    ", from position=" + startPos +
                    ", resuming initialization for tables=" + tbs));
            }
            return;
        }

        /*
         * If we fail to create a stream from remote region, the error will be
         * processed in
         * {@link oracle.kv.pubsub.NoSQLSubscriber#onError(Throwable)}.
         */

        /* fail because of other reasons */
        postFail(resp, subscriber.getFailure());
    }

    /**
     * Initializes all MR tables from source region. This usually happens when
     * the agent encounter insufficient log entry exception that the stream
     * cannot resume because the log entry has been cleaned at source. The
     * initialization will pull all MR tables in table metadata from the
     * source after reestablishes the stream with mode "now".
     * <p>
     * Note that this call may take long to return. After it returns, all MR
     * tables are initialized and the stream is recovered.
     *
     * @param req request body
     * @throws InterruptedException if interrupted in waiting
     */
    private void initializeRegion(RegionAgentReq req)
        throws InterruptedException {
        /* initialize all MR tables */
        logger.info(lm("Start region initialization"));
        final Set<String> tables =
            mdMan.getRegionMRTMap().get(config.getSource());
        initializeTablesFromSource(req, tables);
    }

    /**
     * Initializes the tables in request from the source region
     *
     * @param req request body
     * @throws InterruptedException if interrupted in waiting
     */
    private void initializeTables(RegionAgentReq req)
        throws InterruptedException {
        /* initialize MR tables in request */
        initializeTablesFromSource(req, req.getTables());
    }

    /**
     * Initializes MR tables from source.
     *
     * @param req    request body
     * @param tables tables to initialize
     * @throws InterruptedException if interrupted in waiting
     */
    private void initializeTablesFromSource(RegionAgentReq req,
                                            Set<String> tables)
        throws InterruptedException {
        statusUpd.post(getSourceRegion(), status, tables);

        if (tables == null || tables.isEmpty()) {
            logger.info(lm(reqPrefix(req) +
                           "No table to initialize, post succ"));
            postSucc(req.getResp());
            return;
        }

        /* reset table init checkpoints */
        resetInitCkpt(tables);
        logger.info(lm(reqPrefix(req) +
                       "Reset init checkpoint for tables=" + tables));

        /* ensure all tables are existing at source */
        final String region = config.getSource().getName();
        final Set<String> found = ensureTable(req, tables);
        if (found.isEmpty()) {
            final String err = "Initialization failed because it cannot " +
                               "find any table=" + tables +
                               " at region=" + region +
                               ", to initialize by polling thread";
            logger.warning(lm(reqPrefix(req) + err));
            status = RegionAgentStatus.INITIALIZING_TABLES;
            return;
        }

        final Set<String> notFound =
            tables.stream().filter(t -> !found.contains(t))
                  .collect(Collectors.toSet());
        if (notFound.isEmpty()) {
            logger.info(lm(reqPrefix(req) +
                           "All tables found, will start initialization " +
                           "for tables=" + found + " from region=" + region));
        } else {
            logger.info(lm(reqPrefix(req) + "Init tables from region=" +
                           config.getSource().getName() +
                           ", not all tables found in ms=" +
                           remoteTableTimeoutMs +
                           ", will start initializing tables=" + found +
                           ", for tables not found=" + notFound +
                           ", the agent will continue polling and start " +
                           "initialization once it is found."));
        }

        /* cancel existing stream if any */
        cancelRunningStream(req);

        logger.info(lm(reqPrefix(req) + "Start initializing tables=" + found));

        /* create a new stream from now */
        final NoSQLStreamMode mode = INIT_TABLE_STREAM_MODE;
        if (!startStream(mode, found, req)) {
            final String err = "Initialization failed because it cannot " +
                               "create stream from " +
                               "region=" + config.getSource().getName() +
                               ", mode=" + mode +
                               ", tables=" + found;
            logger.warning(lm(reqPrefix(req) + err));
            postFail(req.getResp(), new IllegalStateException(err));
            return;
        }

        logger.info(lm(reqPrefix(req) +
                       "Done creating stream for tables=" + found));
        /* start transfer tables */
        status = RegionAgentStatus.INITIALIZING_TABLES;
        try {
            transferTables(found, req);
            status = RegionAgentStatus.STREAMING;
            statusUpd.post(getSourceRegion(), status, found);
            if (notFound.isEmpty()) {
                postSucc(req.getResp());
            }
        } catch (TransferTableException tte) {
            processTransTableExp(req, tte);
        }
    }

    private void processTransTableExp(RegionAgentReq req,
                                      TransferTableException tte) {
        if (isShutdownRequested()) {
            logger.fine(() -> reqPrefix(req) +
                              "Table transfer aborted in shutdown, " +
                              ", failed=" + tte.getFailed() +
                              ", completed=" + tte.getTransferred() +
                              ", remaining=" + tte.getRemaining());
            return;
        }

        final Throwable exp = tte.getCause();
        final String err = reqPrefix(req) +
                           "Fail to transfer table=" + tte.getFailed() +
                           ", completed=" + tte.getTransferred() +
                           ", remaining=" + tte.getRemaining() +
                           ", exception=" + exp.getClass().getCanonicalName() +
                           ", error=" + exp.getMessage();
        logger.warning(lm(err) + "\n" +
                       /* stack might already be dumped in transfer thread */
                       (logger.isLoggable(Level.FINE) ?
                           LoggerUtils.getStackTrace(exp) : ""));
        cancelRunningStream(req);
        postFail(req.getResp(), new IllegalStateException(err));

        /* let main loop capture it */
        throw tte;
    }

    /**
     * Cancels the current stream if it is running
     */
    private void cancelRunningStream(RegionAgentReq req) {
        final NoSQLSubscription current =
            (subscriber == null) ? null : subscriber.getSubscription();
        if (current != null && !current.isCanceled()) {
            current.cancel();
            logger.fine(() -> lm(reqPrefix(req) + "Current stream canceled"));
        }
    }

    /**
     * Adds a table to an existing stream if exists, or create a new stream
     * if not. Steps to add a new table to existing stream:
     * <p>
     * 1. wait till the table exists at source and ensure compatible schema;
     * 2. if the stream already exists, add the table into the stream;
     * 3. if the stream does not exist, create a new stream with mode "now";
     * 4. start transfer the table from source;
     * 5. wait till the transfer complete
     *
     * @param req request body
     * @throws InterruptedException if interrupted in waiting
     */
    private void addTablesToStream(RegionAgentReq req)
        throws InterruptedException {

        final Set<String> tables = ensureTable(req, req.getTables());
        if (tables.isEmpty()) {
            final String err = reqPrefix(req) +
                               "Cannot find tables=" + req.getTables() +
                               " at region=" + config.getSource().getName() +
                               ", to initialize by polling thread";
            logger.warning(lm(err));
            return;
        }

        /* table exists, adding table */
        status = RegionAgentStatus.ADDING_TABLES;
        statusUpd.post(getSourceRegion(), status, tables);

        if (!isStreamOn()) {
            /* start a new stream */
            if (!startStream(INIT_TABLE_STREAM_MODE, tables, req)) {
                final String err =
                    reqPrefix(req) +
                    "Cannot create stream with mode=" + INIT_TABLE_STREAM_MODE +
                    " from region=" + config.getSource().getName() +
                    ", tables=" + tables;
                logger.warning(lm(err));
                postFail(req.getResp(), new IllegalStateException(err));
                return;
            }
            logger.info(lm("Stream created for tables=" + tables + " with " +
                           "mode=" + INIT_TABLE_STREAM_MODE));
        } else {
            /* stream is on, just add each table to existing stream */
            try {
                for (String t : tables) {
                    final StreamPosition sp =
                        changeStream(req.getResp(), t, ReqType.ADD);
                    logger.info(lm(reqPrefix(req) + "Table=" + t +
                                   " added to stream at pos=" + sp));
                }
            } catch (ChangeStreamException cse) {
                final String err = reqPrefix(req) +
                                   "Request id=" + cse.getReqId() +
                                   ", fail to add table=" + cse.getTable() +
                                   ", " + cse.getError();
                logger.warning(lm(err));
                postFail(req.getResp(), cse);
                return;
            }
        }

        /* skip the table already in polling thread or already complete */
        final Set<String> tbls = tables.stream()
                                      .filter(table -> !inPolling(table))
                                      .filter(table -> !transComplete(table))
                                      .collect(Collectors.toSet());
        if (tbls.isEmpty()) {
            status = RegionAgentStatus.STREAMING;
            statusUpd.post(getSourceRegion(), status, tables);
            postSucc(req.getResp());
            return;
        }

        /* start transferring table */
        resetInitCkpt(tbls);
        logger.info(lm(reqPrefix(req) +
                       "To initialize=" + tbls + " after reset checkpoint"));
        /* start transfer tables */
        try {
            transferTables(tbls, req);
            status = RegionAgentStatus.STREAMING;
            statusUpd.post(getSourceRegion(), status, tables);
            postSucc(req.getResp());
            logger.fine(() -> lm(reqPrefix(req) +
                                 "Transfer complete, tables=" + tbls));
        } catch (TransferTableException tte) {
            processTransTableExp(req, tte);
        }
    }

    /**
     * Removes tables from existing stream. If the table exists in stream,
     * remove it from the stream and the agent will no longer see the writes
     * from the table.
     *
     * @param req request body
     * @throws InterruptedException if interrupted in waiting
     */
    private void removeTablesFromStream(RegionAgentReq req)
        throws InterruptedException {

        final Set<String> tables = req.getTables();
        status = RegionAgentStatus.REMOVING_TABLES;
        statusUpd.post(getSourceRegion(), status, tables);

        /* remove table in polling thread */
        final Set<String> inPolling =
            tables.stream().filter(tablePollingThread::inPolling)
                  .collect(Collectors.toSet());
        if (!inPolling.isEmpty()) {
            tablePollingThread.removeTableFromCheckList(inPolling);
            logger.info(lm(reqPrefix(req) +
                           "Tables=" + inPolling + " removed from the" +
                           " polling thread"));
        }
        /* deal with tables in stream */
        final Set<String> inStream =
            tables.stream().filter(t -> !inPolling.contains(t))
                  .collect(Collectors.toSet());

        final String region = getSourceRegion().getName();
        if (!isStreamOn()) {
            logger.fine(() -> lm(reqPrefix(req) +
                                 "Stream not on, no need to remove " +
                                 "tables from the stream, tables="
                                 + inStream));
            delInitCkpt(tables, req);
            postSucc(req.getResp());
            /* remove tables from the service metadata */
            tables.forEach(t -> mdMan.removeRemoteTables(region, t));
            return;
        }

        /* stream is on, just add remove table from existing stream */
        try {
            for (String t : inStream) {
                final StreamPosition sp =
                    changeStream(req.getResp(), t, ReqType.REMOVE);
                final MRTableMetrics tm = getMetrics().removeTable(t);
                logger.fine(() -> lm(reqPrefix(req) +
                                     "Table=" + t + " removed from stream at " +
                                     "pos=" + sp + ", final metrics=" + tm));
            }
            status = RegionAgentStatus.STREAMING;
            statusUpd.post(getSourceRegion(), status, tables);
            postSucc(req.getResp());
            logger.info(lm(reqPrefix(req) +
                           "Tables=" + inStream + " removed from stream, " +
                           (inPolling.isEmpty() ? "" :
                               "tables=" + inPolling + " removed from polling " +
                               "thread, ") +
                           "post success"));
        } catch (ChangeStreamException cse) {
            final String err = reqPrefix(req) +
                               "Post failure to remove table=" +
                               cse.getTable() + ", error=" + cse.getError();
            logger.warning(lm(err));
            postFail(req.getResp(), cse);
        } finally {
            delInitCkpt(tables, req);
            tables.forEach(t -> mdMan.removeRemoteTables(region, t));
        }
    }

    /**
     * Changes the parameters
     *
     * @param req request body
     */
    private void changeParam(RegionAgentReq req) {
        final ChangeParamRegionAgentReq cpm = (ChangeParamRegionAgentReq) req;
        if (isStreamOn()) {
            //TODO: change parameters for tables
            logger.fine(() -> lm(
                reqPrefix(req) + "Not supported: change parameters=" +
                cpm.getParams() +
                "for tables=" + cpm.getTables()));
        } else {
            logger.fine(() -> lm(reqPrefix(req) + "Stream is not on"));
        }
    }

    /**
     * Changes the stream by adding or removing tables
     *
     * @param reqType   request type
     * @param resp      response handler
     * @param tableName name of table to add
     * @throws InterruptedException if interrupted in enqueueing the request
     */
    private void changeStream(ReqType reqType,
                              XRegionRespHandlerThread resp,
                              String tableName) throws InterruptedException {

        enqueue(new RegionAgentReq(reqType, resp, tableName));
    }

    /**
     * Creates agent metrics based on the type
     *
     * @param type type of agent
     * @param srcRegion source region name
     * @param tgtRegion target region name
     * @return agent metrics
     */
    private BaseRegionAgentMetrics createMetrics(RegionAgentConfig.Type type,
                                                 String srcRegion,
                                                 String tgtRegion) {

        final BaseRegionAgentMetrics ret;
        switch (type) {
            case MRT:
                ret = new MRTAgentMetrics(srcRegion, tgtRegion);
                break;
            case PITR:
                ret = new PITRAgentMetrics(srcRegion);
                break;
            default:
                throw new IllegalStateException("Unsupported agent type " +
                                                type);
        }
        return ret;
    }

    /**
     * Does the last checkpoint and wait before cancel
     */
    private void doLastCkptWait() {
        final StreamPosition sp = getLastPersistedPosition();
        subscriber.getSubscription().doCheckpoint(sp);
        final boolean succ =
            new PollCondition(POLL_INTERVAL_MS, LAST_CKPT_TIMEOUT_MS) {
                @Override
                protected boolean condition() {
                    return sp.equals(subscriber.getLastCkpt());
                }
            }.await();

        if (!succ) {
            /* cannot do the last ckpt, log it and exit */
            final String err = "Timeout (" + LAST_CKPT_TIMEOUT_MS +
                               " ms) in last checkpoint " + sp;
            logger.info(lm(err));
        }
    }

    /**
     * Gets the position of last persisted to target store
     *
     * @return the position of last persisted to target store
     */
    private StreamPosition getLastPersistedPosition() {
        //TODO: in V1 we use the current stream position from stream because
        //it is updated after onNext() and each call of onNext() would persist
        // one stream operation to local store. In post-V1, if we optimize the
        // onNext() to use the bulkPut, we should not use the current stream
        // position, instead, we should maintain a stream position of last
        // persisted operation and use that as checkpoint.

        final NoSQLSubscription stream = subscriber.getSubscription();
        return stream.getCurrentPosition();
    }

    /**
     * Shuts down the agent completely
     */
    private void close() {

        logger.fine(() -> lm("Start shutting down the agent"));
        status = RegionAgentStatus.CANCELED;
        statusUpd.post(getSourceRegion(), status, getTables());
        /* shutdown auxiliary threads */
        if (tablePollingThread != null) {
            tablePollingThread.shutDown();
        }
        if (msgQueue != null && msgQueue.size() > 0) {
            logger.fine(() -> lm(
                "Clear the message queue, remaining messages: " +
                msgQueue.stream().map(Object::toString)
                        .collect(DEFAULT_COLL)));
            msgQueue.clear();
        }

        if (subscriber != null &&
            subscriber.getSubscription() != null &&
            !subscriber.getSubscription().isCanceled()) {
            if (subscriber.getSubscriptionConfig().isCkptEnabled()) {
                doLastCkptWait();
            }
            subscriber.getSubscription().cancel();
            subscriber = null;
            logger.fine(() -> lm("Stream from region=" +
                                 config.getSource().getName() + " canceled."));
        }

        if (publisher != null && !publisher.isClosed()) {
            publisher.close(true);
            publisher = null;
            logger.fine(() -> lm("Publisher in agent closed."));
        }

        if (ec != null) {
            ec.shutdownNow();
            logger.fine(() -> lm("Executor shutdown"));
        }

        if (storedCause != null) {
            logger.warning(lm("Agent has been shut down with error " +
                              storedCause.getMessage()));
            logger.fine(() -> lm("Call stack\n" +
                                 LoggerUtils.getStackTrace(storedCause)));
        }
    }

    void postSucc(XRegionRespHandlerThread resp) {
        if (resp != null) {
            final NoSQLSubscription stream = subscriber.getSubscription();
            final String msg;
            if (stream == null) {
                msg = "stream unavailable";
            } else {
                msg = "position=" + stream.getCurrentPosition() +
                      "(canceled=" + stream.isCanceled() + ")";
            }
            resp.regionSucc(config.getSource(), msg);
        }
    }

    private void postFail(XRegionRespHandlerThread resp, Throwable cause) {
        if (resp != null) {
            resp.regionFail(config.getSource(), cause);
        }
    }

    /**
     * Starts the inbound stream
     *
     * @param streamMode stream mode
     * @param tables     tables to stream
     * @param req        region agent request
     * @return true if stream starts up successfully, false otherwise
     * @throws InterruptedException if interrupted in creating stream
     */
    boolean startStream(NoSQLStreamMode streamMode, Set<String> tables,
                        RegionAgentReq req)
        throws InterruptedException {

        /* if shutdown, simply returns */
        if (isShutdownRequested()) {
            return false;
        }

        /* ensure publisher */
        if (publisher == null || publisher.isClosed()) {
            final String err = reqPrefix(req) + "Publisher from " +
                               config.getSource().getName() +
                               " has closed.";
            logger.warning(lm(err));
            throw new IllegalArgumentException(err);
        }

        /* cancel current stream if exists */
        final NoSQLSubscription curr =
            subscriber == null ? null : subscriber.getSubscription();
        if (curr != null && !curr.isCanceled()) {
            final String err = reqPrefix(req) + "Cancel existing stream from " +
                               config.getSource().getName();
            logger.info(lm(err));
            curr.cancel();
        }

        /* create subscriber instance */
        subscriber = createSubscriber(config.getType(), streamMode,
                                      tables.toArray(new String[]{}));

        publisher.subscribe(subscriber);

        final boolean succ =
            new PollCondition(POLL_INTERVAL_MS, Long.MAX_VALUE) {
                @Override
                protected boolean condition() {
                    return subscriber.isSubscriptionSucc() ||
                           subscriber.getFailure() != null ||
                           isShutdownRequested();
                }
            }.await();

        if (isShutdownRequested()) {
            throw new InterruptedException(
                "in shutdown, give up starting stream");
        }

        if (!succ) {
            /* since wait forever, false means it is interrupted */
            final String err = reqPrefix(req) +
                               "Interrupted in waiting for stream to start up" +
                               " from region=" + config.getSource().getName() +
                               ", tables=" + tables + ", mode=" + streamMode;
            logger.fine(() -> lm(err));
            throw new InterruptedException(err);
        }

        /* poll returns true, either success or failure to start stream */
        if (subscriber.isSubscriptionSucc()) {
            logger.fine(() -> lm(reqPrefix(req) +
                                 "stream created from region=" +
                                 config.getSource().getName() +
                                 ", tables=" + tables +
                                 ", mode=" + streamMode));
            return true;
        }

        final Throwable failure = subscriber.getFailure();
        if (failure != null) {
            logger.fine(() -> lm(
                reqPrefix(req) + "stream cannot start up from region=" +
                config.getSource().getName() +
                ", tables=" + tables + ", mode=" + streamMode +
                ", onError signaled: " + failure.getMessage()));
        }
        return false;
    }

    private String getStartPos(NoSQLSubscriptionImpl subscription) {
        return subscription.getSubscriptionMetrics().getAckStartVLSN()
                           .entrySet()
                           .stream()
                           .map(entry -> "shard " + entry.getKey() +
                                         ": " + entry.getValue())
                           .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Creates subscriber based on the type specified
     *
     * @param type       type of subscriber
     * @param streamMode stream mode
     * @param tables     tables to subscribe
     * @return subscriber based on the type specified
     */
    private BaseRegionAgentSubscriber createSubscriber(
        RegionAgentConfig.Type type,
        NoSQLStreamMode streamMode,
        String[] tables) {

        final NoSQLSubscriptionConfig conf =
            new NoSQLSubscriptionConfig.Builder(getCkptTableName())
                .setStreamMode(streamMode)
                .setCreateNewCheckpointTable(true)
                .setSubscribedTables(tables)
                .setLocalWritesOnly(config.isLocalWritesOnly())
                .setEmptyStreamDuration(DEFAULT_EMPTY_STREAM_TIMEOUT_SECS)
                .setMaxReconnect(DEFAULT_MAX_RECONNECT)
                .build();

        if (!config.isEnableCkptTable()) {
            /* test only */
            conf.disableCheckpoint();
        }

        /* set test hook */
        conf.setILETestHook(config.getTestHook());

        BaseRegionAgentSubscriber ret;
        switch (type) {
            case MRT:
                ret = new MRTSubscriber(this, conf,
                                        mdMan.getRegionKVS(config.getTarget()),
                                        logger);
                break;
            case PITR:
                ret = new PITRSubscriber(this, conf, logger);
                break;
            default:
                throw new IllegalStateException("Unsupported agent type " +
                                                type);
        }
        return ret;
    }

    /**
     * Transfers a set of tables from source region to target region.
     *
     * @param tables name of tables to transfer
     * @param req    request
     */
    private void transferTables(Set<String> tables, RegionAgentReq req) {

        logger.fine(() -> lm("Transferring table " + tables + " from " +
                             config.getSource().getName()));
        final Set<String> complete = new HashSet<>();
        final Set<String> dropped = new HashSet<>();
        /* create table copy threads */
        for (String table : tables) {
            if (tablePollingThread.inPolling(table)) {
                logger.info(lm(reqPrefix(req) +
                               "Table=" + table + " already in polling " +
                               "thread check list"));
                continue;
            }
            if (tablePollingThread.inTrans(table)) {
                logger.info(lm(reqPrefix(req) +
                               "Table=" + table + " already submitted to " +
                               "transfer in transfer polling thread"));
                continue;
            }
            try {
                transferTable(table, config.getSource(), config.getTarget(),
                              req);
                /* transfer is complete */
                complete.add(table);
                final MRTableMetrics tm = metrics.get().getTableMetrics(table);
                final TableInitStat st =
                    tm.getRegionInitStat(getSourceRegion().getName());
                logger.info(lm(
                    reqPrefix(req) + "Done transfer table=" + table +
                    ", #transferred=" +
                    st.getTransferRows() +
                    ", #persisted=" +
                    st.getPersistRows() +
                    ", transferred=" + complete +
                    ", remaining=" +
                    tables.stream().filter(t -> !complete.contains(t))
                          .collect(DEFAULT_COLL)));
            } catch (MRTTableTransferThread.MRTableNotFoundException exp) {
                dropped.add(exp.getTable());
                logger.warning(lm("Cannot transfer table=" + table +
                                  ", might already be dropped at region=" +
                                  exp.getRegion() +
                                  ", all dropped=" + dropped));
            } catch (StoreIteratorException sie) {
                /*
                 * If unable to read the source table, log error and move to
                 * the next table
                 */
                final Throwable cause = sie.getCause();
                logger.warning(lm(reqPrefix(req) +
                                  "Unable to read table=" + table + " at " +
                                  "region=" + config.getSource().getName() +
                                  ", error=" + sie.getMessage() +
                                  (cause == null ? "" :
                                      ", cause=" + cause.getClass().getName() +
                                      ", " + cause.getMessage())));
            } catch (MetadataNotFoundException mnfe) {
                dropped.add(table);
                /*
                 * multi-region table can be dropped at any time at remote
                 * regions, therefore the agent might encounter MNFE during
                 * table scan. This is an error which should be logged but it
                 * should not bring down the agent.
                 */
                final Throwable cause = mnfe.getCause();
                logger.warning(lm(reqPrefix(req) +
                                  "Table=" + table + " missing at source" +
                                  ", dropped=" + dropped +
                                  ", error=" + mnfe.getMessage() +
                                  (cause == null ? "" :
                                      ", cause=" +
                                      cause.getClass().getCanonicalName() +
                                      ", " + cause.getMessage())));
            } catch (Exception exp) {
                /* surface all other hard failures */
                logger.warning(lm(reqPrefix(req) +
                                  "Out of all tables=" + tables +
                                  ", fail to transfer table=" + table +
                                  ", error=" + exp.getMessage() +
                                  LoggerUtils.getStackTrace(exp)));
                throw new TransferTableException(tables, complete, table, exp);
            }
        }
        logger.info(lm(reqPrefix(req) +
                       "Done initializing tables=" + complete));
    }

    /**
     * Builds the checkpoint table name for the agent
     *
     * @return the checkpoint table name
     */
    private String getCkptTableName() {
        final String name =
            CKPT_PREFIX + "_from_" + config.getSource().getName() + "_to_" +
            config.getHost().getName() + "_" + config.getSubscriberId();
        return NameUtils.makeQualifiedName(DEFAULT_CKPT_TABLE_NAMESPACE, name);
    }

    /**
     * Initialize the agent thread. Create the publisher for remote region,
     * will retry endlessly if fail to create the publisher. It also starts
     * all auxiliary threads.
     *
     * @throws InterruptedException if interrupted
     */
    private void initializeAgent() throws InterruptedException {
        /* create publisher instance */
        int attempts = 0;
        while (!isShutdownRequested()) {
            try {
                attempts++;
                publisher = createAgentPublisher(config);
                logger.fine(
                    lm("Publisher (id=" + publisher.getPublisherId() + ")" +
                       " has been created in # of attempts=" + attempts +
                       ", from region=" + config.getSource().getName()));
                break;
            } catch (PublisherFailureException pfe) {
                if (shouldRetry(pfe)) {
                    /*
                     * cannot reach remote store which can be down, sleep and
                     * retry
                     *
                     * xregion service will endlessly retry, the admin
                     * should monitor the log to ensure the remote the region
                     * is up.
                     */
                    final String msg = "Cannot create publisher for region=" +
                                       config.getSource().getName() +
                                       ", will retry after ms=" +
                                       SLEEP_MS_BEFORE_RECONNECT + ", " +
                                       pfe.getMessage() +
                                       (pfe.getCause() == null ? "" :
                                           ", cause=" +
                                           pfe.getCause().getClass()
                                              .getCanonicalName()) +
                                       (logger.isLoggable(Level.FINE) ?
                                           LoggerUtils.getStackTrace(pfe) : "");
                    logger.warning(lm(msg));
                    /* dump detailed msg of cause */
                    final String sid = (pfe.getSubscriberId() == null) ? "NA" :
                        pfe.getSubscriberId().toString();
                    rlLogger.log(sid, Level.WARNING, lm(getPFECauseMsg(pfe)));

                    synchronized (msgQueue) {
                        /* may miss the notification, check shutdown */
                        if (isShutdownRequested()) {
                            break;
                        }
                        msgQueue.wait(SLEEP_MS_BEFORE_RECONNECT);
                    }
                    continue;
                }

                /* surface failure without retry */
                String err = "Surface failure to create publisher, " +
                             pfe.getMessage() + ", " + getPFECauseMsg(pfe);
                logger.warning(lm(err));
                throw pfe;
            }
        }

        if (isShutdownRequested()) {
            throw new InterruptedException("shut down requested");
        }

        /* depending on the start mode, put a msg in the queue */
        switch (config.getStartMode()) {
            case STREAM:
                createStream(config.getTables());
                break;
            case INIT_TABLE:
                initTables(config.getTables());
                break;
            case IDLE:
                break;
            default:
                logger.fine(() -> lm("No msg enqueued, agent idle"));
        }

        /* start auxiliary threads after agent initialized */
        tablePollingThread.start();

        logger.info(lm("Agent of region=" + config.getSource().getName() +
                       " initialized, mode=" + config.getStartMode() +
                       ", tables=" + Arrays.toString(config.getTables())));
    }

    /**
     * Builds an instance of publisher
     *
     * @return publisher instance
     * @throws PublisherFailureException if fail to create the publisher
     */
    private NoSQLPublisher createAgentPublisher(RegionAgentConfig conf)
        throws PublisherFailureException {

        /* create publisher instance */
        final RegionInfo region = conf.getSource();
        final String store = region.getStore();
        final String[] helper = region.getHelpers();
        /* create publisher root directory for region */
        final Path dir = Paths.get(conf.getLocalPath(), region.getName());
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException exp) {
                final String err = "Cannot create publisher directory " +
                                   dir.getFileName() +
                                   " region=" + region.getName() +
                                   ", error " + exp.getMessage();
                throw new PublisherFailureException(err, false, exp);
            }
        }
        final String path = dir.toString();
        final KVStoreConfig kvStoreConfig = new KVStoreConfig(store, helper);
        if (conf.isSecureStore()) {
            final Properties sp = XRegionService.setSecureProperty(
                kvStoreConfig, new File(conf.getSecurityConfig()));
            logger.fine(() -> lm("Set security property: " +
                                 mdMan.dumpProperty(sp)));
        }
        final NoSQLPublisherConfig cf =
            new NoSQLPublisherConfig.Builder(kvStoreConfig, path)
                /* allow preexisting directory */
                .setAllowPreexistDir(true)
                .setPublisherId(conf.getSource().getName() + "." +
                                conf.getSource().getStore())
                .build();
        return NoSQLPublisher.get(cf, logger);
    }

    /**
     * Returns true if the stream is on, false otherwise
     */
    boolean isStreamOn() {

        if (publisher == null || publisher.isClosed()) {
            logger.fine(() -> "Publisher does not exist or has shut down.");
            return false;
        }
        if (subscriber == null) {
            logger.fine(() -> ("Subscriber does not exist"));
            return false;
        }
        final NoSQLSubscription subscription = subscriber.getSubscription();
        if (subscription == null) {
            logger.fine(() -> ("Subscription is not created"));
            return false;
        }
        if (subscription.isCanceled()) {
            final Throwable failure = subscriber.getFailure();
            final String err = (failure != null) ? failure.getMessage() : "";
            logger.fine(() -> "Subscription has been canceled " + err);
            return false;
        }
        return true;
    }

    /**
     * Changes the stream by adding or removing a table and returns the
     * stream position where the table is added or removed. If the table
     * has been already added or removed, returns the current stream position.
     *
     * @param resp  response handler
     * @param table table to add or remove
     * @param type  type of request
     * @return effective position if change is successful
     * @throws InterruptedException  if interrupted in changing stream
     * @throws ChangeStreamException if fail to change the stream
     */
    synchronized StreamPosition
    changeStream(XRegionRespHandlerThread resp, String table, ReqType type)
        throws InterruptedException, ChangeStreamException {

        final long reqId = (resp == null ? 0 : resp.getReqId());
        if (!isStreamOn()) {
            final String err = logPrefix(reqId) +
                               "Cannot change stream because it is not on";
            logger.fine(() -> lm(err));
            throw new ChangeStreamException(reqId, table, err,
                                            new IllegalStateException(err));
        }

        final NoSQLSubscription subscription = subscriber.getSubscription();
        subscriber.clearChangeResult();
        switch (type) {
            case ADD:
                if (subscription.getSubscribedTables().contains(table)) {
                    logger.fine(() -> lm(logPrefix(reqId) +
                                         "Table=" + table + " already added " +
                                         "in agent"));
                    return subscription.getCurrentPosition();
                }
                logger.fine(() -> lm(
                    logPrefix(reqId) + "Adding table=" + table +
                    " to existing stream "));
                subscription.subscribeTable(table);
                break;

            case REMOVE:
                if (!subscription.getSubscribedTables().contains(table)) {
                    logger.fine(() -> lm(logPrefix(reqId) + "Table=" + table +
                                         " already removed " + "from agent"));
                    return subscription.getCurrentPosition();
                }
                logger.fine(() -> lm(logPrefix(reqId) +
                                     "Removing table=" + table +
                                     " from existing stream "));
                subscription.unsubscribeTable(table);
                break;

            default:
                throw new IllegalArgumentException("Unsupported type " + type);
        }

        /* wait for change result */
        final StreamPosition sp = waitForChangeResult(reqId, type, table);
        logger.fine(() -> lm(logPrefix(reqId) + "Done request " + reqId +
                       ": " + type + " " + table + " at " + sp));
        return sp;
    }

    /**
     * Transfers a table from source region to target region
     *
     * @param table table to transfer
     * @param src   source region
     * @param tgt   target region
     * @param req   request
     * @throws MRTTableTransferThread.MRTableNotFoundException if table not
     * found at a region
     * @throws Exception if fail to transfer
     */
    private void transferTable(String table, RegionInfo src, RegionInfo tgt,
                               RegionAgentReq req)
        throws MRTTableTransferThread.MRTableNotFoundException, Exception {

        /* create table initialization thread */
        final KVStore srcKVS = mdMan.getRegionKVS(src);
        final KVStore tgtKVS = mdMan.getRegionKVS(tgt);
        final MRTTableTransferThread
            tc = new MRTTableTransferThread(this,
                                            (MRTSubscriber) getSubscriber(),
                                            table,
                                            config.getSource(),
                                            config.getTarget(),
                                            (TableAPIImpl) srcKVS.getTableAPI(),
                                            (TableAPIImpl) tgtKVS.getTableAPI(),
                                            logger);
        ec.submit(tc);
        logger.fine(() -> lm(reqPrefix(req) +
                             "Thread to transfer table " + table + " " +
                             "submitted"));
        /* wait for table transfer done, no time out, either complete or fail */
        new PollCondition(POLL_INTERVAL_MS, Long.MAX_VALUE) {
            @Override
            protected boolean condition() {
                return tc.isComplete() || tc.hasFailed() ||
                       isShutdownRequested();
            }
        }.await();

        if (isShutdownRequested()) {
            final String err = reqPrefix(req) +
                               "In shutdown, stop transferring table=" + table;
            throw new InterruptedException(err);
        }

        if (tc.hasFailed()) {
            /* fail to transfer the table, throw failure cause to caller */
            throw tc.getCause();
        }
        logger.fine(() -> lm(reqPrefix(req) +
                             "Complete transferring table=" + table));
    }

    /**
     * Waits for change result, return effective stream position if change is
     * successful, throw exception otherwise
     *
     * @param reqId   request id
     * @param table   name of the table
     * @param reqType type of request
     * @return effective stream position if change is successful
     * @throws ChangeStreamException if fail to change the stream
     * @throws InterruptedException  if interrupted
     */
    private StreamPosition waitForChangeResult(long reqId,
                                               ReqType reqType,
                                               String table)
        throws ChangeStreamException, InterruptedException {

        final boolean succ =
            new PollCondition(POLL_INTERVAL_MS, Long.MAX_VALUE) {
                @Override
                protected boolean condition() {
                    return subscriber.isChangeResultReady() ||
                           isShutdownRequested();
                }
            }.await();

        if (isShutdownRequested()) {
            throw new InterruptedException(
                "in shutdown, give up change result");
        }

        if (!succ) {
            /* interrupted */
            final String err = logPrefix(reqId) +
                               "Interrupted in waiting for change result" +
                               ", reqId=" + reqId + ", type=" + reqType +
                               ", table=" + table;
            logger.fine(() -> lm(err));
            throw new InterruptedException(err);
        }

        final StreamPosition sp = subscriber.getEffectivePos();
        if (sp != null) {
            /* change is successful */
            return sp;
        }

        /* fail to change the stream */
        final Throwable cause = subscriber.getChangeResultExp();
        if (cause == null) {
            throw new IllegalStateException(logPrefix(reqId) +
                                            "Missing change result exception " +
                                            "when fail to " + reqType +
                                            " table " + table);
        }
        final String err = logPrefix(reqId) +
                           "cannot change the stream," + cause.getMessage();
        throw new ChangeStreamException(reqId, table, err, cause);
    }

    /**
     * Enqueues a msg for the agent to execute
     *
     * @param msg message to enqueue
     * @throws InterruptedException if interrupted in enqueue
     */
    private synchronized void enqueue(RegionAgentReq msg)
        throws InterruptedException {

        if (msg == null) {
            return;
        }

        while (true) {
            try {
                if (msgQueue.offer(msg, MSG_QUEUE_PUT_TIMEOUT_MS,
                                   TimeUnit.MILLISECONDS)) {
                    logger.finest(() -> lm("Msg " + msg + " enqueued"));
                    break;
                }

                logger.finest(() -> lm("Unable enqueue message" + msg +
                                       " for " + MSG_QUEUE_PUT_TIMEOUT_MS +
                                       "ms, keep trying..."));
            } catch (InterruptedException e) {
                /* This might have to get smarter. */
                logger.warning(lm("Interrupted offering message queue, " +
                                  "message: " + msg));
                throw e;
            }
        }
    }

    /**
     * Internal exception raised when fail to change the stream
     */
    static class ChangeStreamException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /* request id */
        private final long reqId;

        /* name of table to change the stream */
        private final String table;

        /* summary of error message */
        private final String error;

        ChangeStreamException(long reqId,
                              String table,
                              String error,
                              Throwable cause) {
            super(cause);
            this.reqId = reqId;
            this.table = table;
            this.error = error;
        }

        long getReqId() {
            return reqId;
        }

        String getTable() {
            return table;
        }

        String getError() {
            return error;
        }
    }

    /**
     * Internal exception raised when fail to transfer tables
     */
    private static class TransferTableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /* all tables */
        private final Set<String> tables;

        /* already transferred tables */
        private final Set<String> transferred;

        /* table that fail to transfer */
        private final String failed;

        TransferTableException(Set<String> tables, Set<String> transferred,
                               String failed, Throwable cause) {
            super(cause);
            this.tables = tables;
            this.transferred = transferred;
            this.failed = failed;
        }

        Set<String> getTransferred() {
            return transferred;
        }

        String getFailed() {
            return failed;
        }

        Set<String> getRemaining() {
            return tables.stream().filter(t -> !transferred.contains(t))
                         .collect(Collectors.toSet());
        }
    }

    /**
     * Returns true if tables exist at source, false otherwise
     *
     * @param req    request
     * @param tables tables
     * @return set of tables found at source region
     */
    private Set<String> ensureTable(RegionAgentReq req, Set<String> tables) {
        /* ensure table exists at source, otherwise stream cannot be created */
        final Set<String> found = mdMan.tableExists(
            config.getSource(), tables, remoteTableTimeoutMs);

        final Set<String> notFound =
            tables.stream().filter(t -> !found.contains(t))
                  .collect(Collectors.toSet());
        if (!notFound.isEmpty()) {
            resetInitCkpt(notFound);
            logger.info(lm(reqPrefix(req) +
                           "Create checkpoint for missing tables=" + notFound));
            /* add not found table to polling thread */
            tablePollingThread.addTables(req, notFound);
        }

        /*
         * check schema of tables at source, if incompatible schema is
         * found, log the warning. Stream will start and writes to incompatible
         * tables will not be persisted till the schema is fixed.
         */
        final String result = mdMan.matchingSchema(config.getSource(), found);
        if (result != null) {
            String sb = reqPrefix(req) + "Incompatible table schema, source=" +
                        config.getSource().getName() +
                        ", target=" +
                        mdMan.getServRegion().getName() +
                        ", " + result;
            logger.warning(lm(sb));
        }

        return found;
    }

    /**
     * Closes all pending requests in shutdown
     */
    private void closePendingRequests() {

        /* drain the pending requests in the queue */
        final Set<RegionAgentReq> drainReqs = new HashSet<>();
        synchronized (msgQueue) {
            msgQueue.drainTo(drainReqs);
            /* no queue request will be processed */
            msgQueue.clear();
        }

        /* request ids of success and failure response */
        final Set<Long> succ = new HashSet<>();
        final Set<Long> fail = new HashSet<>();
        drainReqs.forEach(r -> {
            /* post success for remove table, otherwise post failure */
            if (r.getReqType().equals(ReqType.REMOVE)) {
                postSucc(r.getResp());
                succ.add(r.getResp().getReqId());
                return;
            }
            final IllegalStateException ise =
                new IllegalStateException("agent shutdown");
            postFail(r.getResp(), ise);
            fail.add(r.getResp().getReqId());
        });

        /* post success or failure for each request in the queue */
        final String msg =
            "Shut down agent to region=" + getSourceRegion().getName() +
            (succ.isEmpty() ? "" :
                ", post success to queued request ids=" + succ) +
            (fail.isEmpty() ? "" :
                ", post failure to queued request ids=" + fail);
        logger.info(lm(msg));

        /* post failure msg for currently processing request */
        if (currentReq != null) {
            final XRegionRespHandlerThread resp = currentReq.getResp();
            /* post succ for remove table, otherwise post failure */
            if (currentReq.getReqType().equals(ReqType.REMOVE)) {
                postSucc(currentReq.getResp());
                logger.info(lm(reqPrefix(currentReq) +
                               "Region agent will shut down, post success " +
                               "for current request of removing table=" +
                               currentReq.getTables()));
                return;
            }
            final String err =
                "Shut down agent to region=" + getSourceRegion().getName() +
                ", post failure to currently running request id=" +
                (resp == null ? "NA" : resp.getReqId());
            logger.info(lm(reqPrefix(currentReq) + err));
            final IllegalStateException ise = new IllegalStateException(err);
            postFail(resp, ise);
        }
    }

    /**
     * Returns table polling thread
     */
    public TablePollingThread getTablePollingThread() {
        return tablePollingThread;
    }

    /**
     * Unit test only
     */
    void setRemoteTableTimeoutMs(int val) {
        remoteTableTimeoutMs = val;
    }

    /**
     * RegionAgentReq type of region agent
     */
    public enum ReqType {

        /* stream a table */
        STREAM {
            @Override
            public String toString() {
                return "stream";
            }
        },

        /* initialize given tables */
        INITIALIZE_TABLES {
            @Override
            public String toString() {
                return "initialize tables";
            }
        },

        /* initialize all tables from a region */
        INITIALIZE_FROM_REGION {
            @Override
            public String toString() {
                return "initialize region";
            }
        },

        /* add a table */
        ADD {
            @Override
            public String toString() {
                return "add";
            }
        },

        /* remove a table */
        REMOVE {
            @Override
            public String toString() {
                return "remove";
            }
        },

        /* change parameter */
        CHANGE_PARAM {
            @Override
            public String toString() {
                return "change parameter";
            }
        }
    }

    /**
     * RegionAgentReq of region agent to execute
     */
    public static class RegionAgentReq {

        /* message type to represent the action to take */
        private final ReqType reqType;

        /* name of table in action */
        private final Set<String> tables;

        /* response handler */
        private final XRegionRespHandlerThread resp;

        RegionAgentReq(ReqType reqType, String... tables) {
            this.reqType = reqType;
            this.tables = new HashSet<>(Arrays.asList(tables));

            /* internal generated request, no need to post to response table */
            this.resp = null;
        }

        RegionAgentReq(ReqType reqType,
                       XRegionRespHandlerThread resp,
                       String... tables) {
            this.reqType = reqType;
            this.resp = resp;
            this.tables = new HashSet<>(Arrays.asList(tables));
        }

        @Override
        public String toString() {
            final long reqId = (resp == null ? 0 : resp.getId());
            return "id=" + reqId + ", type=" + reqType + ", tables=" + tables;
        }

        public Set<String> getTables() {
            return tables;
        }

        public XRegionRespHandlerThread getResp() {
            return resp;
        }

        public ReqType getReqType() {
            return reqType;
        }
    }

    /**
     * RegionAgentReq of change parameters
     */
    private static class ChangeParamRegionAgentReq extends RegionAgentReq {

        /* parameters */
        private final Set<Parameter> params;

        ChangeParamRegionAgentReq(Set<Parameter> params, String... tables) {
            super(ReqType.CHANGE_PARAM, tables);
            this.params = params;
        }

        public Set<Parameter> getParams() {
            return params;
        }

        @Override
        public String toString() {
            return super.toString() + ", parameters: " + params;
        }
    }

    private static class ShutDownRequest extends RegionAgentReq {
        ShutDownRequest() {
            super(null);
        }
    }


    /**
     * A KV thread factory that logs if a thread exits on an unexpected
     * exception.
     */
    private class RegionAgentThreadFactory extends KVThreadFactory {
        RegionAgentThreadFactory() {
            super("RegionAgentThreadFactory", logger);
        }
        @Override
        public UncaughtExceptionHandler makeUncaughtExceptionHandler() {
            return (thread, ex) ->
                logger.warning(lm("Thread=" + thread.getName() +
                                  " exit unexpectedly" +
                                  ", error=" + ex.getMessage() +
                                  ", stack=" + LoggerUtils.getStackTrace(ex)));
        }
    }

    /**
     * Lets polling thread to resume table transfer in separate threads from
     * the checkpoint
     */
    private Set<String> resumeTableInitialization(RegionAgentReq req) {
        /* check if any unfinished table initialization */
        final String src = getSourceRegion().getName();
        final Set<String> tblsResume = mdMan.getTablesResumeInit(src);
        if (tblsResume.isEmpty()) {
            logger.fine(() -> lm("No table to resume initialization"));
            return Collections.emptySet();
        }
        logger.info(lm("To resume initialization for tables=" + tblsResume));

        /* delegate to polling thread to initialize to avoid block agent */
        tablePollingThread.addTables(req, tblsResume);
        return tblsResume;
    }

    /**
     * Resets init checkpoint for given tables
     */
    private void resetInitCkpt(Set<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        final String src = getSourceRegion().getName();
        tables.forEach(t -> mdMan.writeCkptRetry(src, t, null, NOT_START));
    }

    /**
     * Drops init checkpoint for given tables
     */
    private void delInitCkpt(Set<String> tables, RegionAgentReq req) {
        final String src = getSourceRegion().getName();
        tables.forEach(t -> mdMan.delTableInitCkpt(src, t));
        logger.fine(() -> lm(reqPrefix(req) +
                             "Delete init checkpoint for tables=" + tables));
    }

    /**
     * Returns true if the table already in polling thread
     */
    boolean inPolling(String table) {
        if (tablePollingThread.inPolling(table)) {
            logger.info(lm("Table=" + table + " in polling thread check " +
                           "list"));
            return true;
        }
        if (tablePollingThread.inTrans(table)) {
            logger.info(lm("Table=" + table + " submitted to transfer in " +
                           "polling thread"));
            return true;
        }
        return false;
    }

    /**
     * Returns true if the table transfer is already complete
     */
    private boolean transComplete(String table) {
        try {
            final TableInitCheckpoint ckpt =
                getMdMan().readTableInitCkpt(config.getSource().getName(),
                                             table);
            if (ckpt == null) {
                return false;
            }
            if (COMPLETE.equals(ckpt.getState())) {
                logger.info(lm("Transfer already complete for table=" + table));
                return true;
            }
        } catch (IllegalStateException ise) {
            logger.warning(lm("Cannot read the checkpoint for table=" + table +
                              ", " + ise.getMessage()));
        }
        return false;
    }

    /**
     * Returns log prefix using request id
     */
    private String reqPrefix(RegionAgentReq req) {
        if (req == null || req.getResp() == null) {
            return "";
        }
        return logPrefix(req.getResp().getReqId());
    }

    private String logPrefix(long requestId) {
        if (requestId == 0) {
            return "";
        }
        return "[requestId=" + requestId + "] ";
    }

    /** Returns true if agent should retry on the given exception */
    private boolean shouldRetry(PublisherFailureException pfe) {
        /* non-null cause */
        if (pfe.getCause() instanceof FaultException ||
            pfe.getCause() instanceof KVStoreException ) {
            return true;
        }
        /* exception may have cause=null if remote, get fault class name */
        final String faultClass = pfe.getFaultClassName();
        /* if cause is null and is fault or kvstore exception */
        return faultClass != null &&
               (faultClass.equals(FaultException.class.getName()) ||
                faultClass.equals(KVStoreException.class.getName()));
    }

    private String getPFECauseMsg(PublisherFailureException pfe) {
        final Throwable cause = pfe.getCause();
        if (cause != null) {
            return "cause=" + cause.getClass().getCanonicalName() +
                   ", " + cause.getMessage() + ", " +
                   LoggerUtils.getStackTrace(cause);
        }
        return pfe.toString();
    }
}
