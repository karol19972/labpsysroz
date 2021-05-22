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

package oracle.kv.impl.xregion.service;

import static oracle.kv.impl.xregion.agent.RegionAgentConfig.StartMode.INIT_TABLE;
import static oracle.kv.impl.xregion.agent.RegionAgentConfig.StartMode.STREAM;
import static oracle.kv.impl.xregion.agent.RegionAgentConfig.Type.MRT;
import static oracle.kv.impl.xregion.agent.RegionAgentConfig.Type.PITR;
import static oracle.kv.impl.xregion.agent.RegionAgentStatus.INITIALIZING_TABLES;
import static oracle.kv.impl.xregion.agent.RegionAgentStatus.STREAMING;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.CHANGE_PARAM;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.PITR_ADD;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.PITR_REMOVE;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.REGION_ADD;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.SHUTDOWN;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.KVStoreConfig;
import oracle.kv.impl.security.util.KVStoreLogin;
import oracle.kv.impl.test.TestStatus;
import oracle.kv.impl.util.PollCondition;
import oracle.kv.impl.xregion.agent.RegionAgentConfig;
import oracle.kv.impl.xregion.agent.RegionAgentStatus;
import oracle.kv.impl.xregion.agent.RegionAgentThread;
import oracle.kv.pubsub.NoSQLSubscriberId;
import oracle.kv.pubsub.PublisherFailureException;
import oracle.kv.stats.ServiceAgentMetrics;

import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * The service gateway to create and manage all inbound streams from remote
 * regions for KV Multi-Master (KMM). The gateway starts up with
 * parameters to connect local KVS, and pull all remote regions, and
 * establish the inbound streams from each remote region.
 */
public class XRegionService extends StoppableThread {

    /*
     * TODO: The current scheme assigns a fixed limit on the number of stream
     * operations across the agents for all regions, and also sets the same
     * limit for each region by using RequestLimitConfig. If the total number
     * of requests goes over the limit, the agents will get
     * RequestLimitExceptions, and will retry after a delay. We may want to
     * tune this differently or provide a better way to limit each agent's
     * maximum concurrency in the future in order to get better performance.
     */

    /* soft shutdown waiting time in ms */
    private static final int SOFT_SHUTDOWN_WAIT_MS = 10 * 1000;

    /* size of request queue */
    private static final int MAX_REQ_QUEUE_SIZE = 10 * 1024;

    /* timeout in ms to poll request queue */
    private static final int REQ_QUEUE_POLL_TIMEOUT_MS = 1000;

    /* poll interval in ms to checking change result */
    private static final int CHANGE_RESULT_POLL_INTV_MS = 100;

    /* poll timeout in ms to checking change result */
    private static final int CHANGE_RESULT_TIMEOUT_MS = 10 * 60 * 1000;

    /* max number of concurrent requests */
    private static final int MAX_NUM_CONCURRENT_REQ = 2;

    /* poll interval in ms */
    private static final int POLL_INTERVAL_MS = 1000;

    /* poll timeout in waiting for region agent shutdown in ms */
    private static final int SHUTDOWN_TIMEOUT_MS = 30 * 1000;

    /* queue of requests */
    private final BlockingQueue<XRegionRequest> reqQueue;

    /* json configuration */
    private final JsonConfig config;

    /* subscriber id */
    private final NoSQLSubscriberId sid;

    /* private logger */
    private final Logger logger;

    /* map of all region agents for each region */
    private final ConcurrentMap<String, RegionAgentThread> agents;

    /* metadata management */
    private final ServiceMDMan mdMan;

    /* request generation thread */
    private final ReqRespManager reqRespManager;

    /* stat reporting manager */
    private final StatsManager statMan;

    /* response handler map */
    private final Map<Long, XRegionRespHandlerThread> responses;

    /* status updater */
    private final StatusUpdater statusUpd;

    /* executor for scheduled task */
    private final ExecutorService ec;

    /* true if the service agent is requested to shut down */
    private volatile boolean shutDownRequested;

    /* true if the service agent is running */
    private volatile boolean running;

    /**
     * Constructs agent thread from configuration
     *
     * @param config json configuration
     */
    public XRegionService(JsonConfig config, Logger logger) {

        super("XRS-" + config.getAgentGroupSize() + "-" + config.getAgentId());
        this.config = config;
        this.logger = logger;

        sid = new NoSQLSubscriberId(config.getAgentGroupSize(),
                                    config.getAgentId());
        agents = new ConcurrentSkipListMap<>();
        reqQueue = new ArrayBlockingQueue<>(MAX_REQ_QUEUE_SIZE);
        running = false;
        shutDownRequested = false;
        responses = new HashMap<>();
        statusUpd = new DefaultStatusUpdater();
        mdMan = new ServiceMDMan(sid, config, logger);
        statMan = new StatsManager(this, logger);
        reqRespManager = new ReqRespManager(sid, reqQueue, mdMan, logger);
        mdMan.setReqRespManager(reqRespManager);
        ec = Executors.newFixedThreadPool(MAX_NUM_CONCURRENT_REQ);
        logger.info(lm("Service created"));
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void run() {

        logger.info(lm("Start cross-region service agent for region=" +
                       mdMan.getServRegion().getName()));
        running = true;
        try {

            statMan.scheduleStatReport();

            initMRT();

            initPITR();

            /* start thread generating requests */
            reqRespManager.startPollingTask();

            while (!shutDownRequested) {

                final XRegionRequest req = reqQueue.poll(
                    REQ_QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (req == null) {
                    logger.finest(lm("Unable to dequeue request for " +
                                     REQ_QUEUE_POLL_TIMEOUT_MS + " ms"));
                } else {

                    if (req.getReqType().equals(SHUTDOWN)) {
                        logger.info(lm("Receive stop request, service will " +
                                       "shut down"));
                        break;
                    }
                    if (req.getReqType().isRegionRequest()) {
                        processRegion(req);
                    } else if (req.getReqType().isMRTRequest()) {
                        processMRT(req);
                    } else if (req.getReqType().isPITRRequest()) {
                        processPITR(req);
                    } else if (req.getReqType().equals(CHANGE_PARAM)) {
                        logger.warning(lm("Change parameter is not supported"));
                    } else {
                        throw new IllegalArgumentException("Unsupported " +
                                                           "request type=" +
                                                           req.getReqType());
                    }
                }

                /* ensure all region agents are green */
                checkRegionAgent();
            }
        } catch (ServiceException sae) {
            final Set<RegionInfo> failReg = sae.getRegions();
            logger.warning(lm("Service agent shuts down because " +
                              sae.getMessage() +
                              ", failed regions=" +
                              failReg.stream().map(RegionInfo::getName)
                                     .collect(Collectors.toSet())));
            for (RegionInfo r : failReg) {
                final String err = "Region " + r + ":" +
                                   "\nAffected tables: " +
                                   sae.getAffectdTables(r) +
                                   "\nReason of failure:" +
                                   (sae.getCause(r) == null ? "n/a" :
                                       sae.getCause(r).getMessage()) +
                                   "\nStack: " +
                                   (sae.getCause() == null ?
                                       LoggerUtils.getStackTrace(sae) :
                                       LoggerUtils.getStackTrace(
                                           sae.getCause()));
                logger.warning(lm(err));
            }
        } catch (InterruptedException ie) {
            if (shutDownRequested) {
                /* interrupted in shutdown, ignore */
                return;
            }

            logger.warning(lm("Interrupted " + ie.getMessage() +
                              "\nStack: " + LoggerUtils.getStackTrace(ie)));
        } catch (Exception exp) {
            logger.warning(lm("Service agent shuts down because " +
                              exp.getMessage() +
                              "\nStack: " + LoggerUtils.getStackTrace(exp)));

        } finally {
            close();
        }
    }

    @Override
    protected int initiateSoftShutdown() {
        logger.fine(lm("Signal thread " + getName() + " to shutdown" +
                       ", wait up to " + SOFT_SHUTDOWN_WAIT_MS +
                       " ms to let it exit"));
        return SOFT_SHUTDOWN_WAIT_MS;
    }

    /**
     * Reads security parameters from config file and set the security
     * property in kvstore config
     *
     * @param kvConfig kvstore config
     * @param file     security parameter file
     * @return the security properties
     */
    public static Properties setSecureProperty(KVStoreConfig kvConfig,
                                               File file) {
        if (!file.exists()) {
            final String err = "Cannot find security configuration file: " +
                               file.getAbsolutePath();
            throw new IllegalStateException(err);
        }

        kvConfig.setSecurityProperties(
            KVStoreLogin.createSecurityProperties(file.getAbsolutePath()));
        return kvConfig.getSecurityProperties();
    }

    /**
     * Returns true if thread is running, false otherwise
     *
     * @return true if thread is running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Shuts down the cross-region service agent
     */
    public void shutdown() {
        if (!running) {
            logger.fine(lm("service already stopped"));
            return;
        }

        if (shutDownRequested) {
            logger.fine(lm("Shutdown already signalled"));
            return;
        }
        shutDownRequested = true;
        /* notify main loop to shut down */
        reqQueue.offer(XRegionRequest.getShutdownReq());
        shutdownThread(logger);
    }

    /**
     * Returns the statistics associated with the agent
     *
     * @return the statistics associated with the agent
     */
    public ServiceAgentMetrics getAgentMetrics() {
        return statMan.getStats().getServiceMetrics();
    }

    /**
     * Returns the table metrics, or null if not exist
     * @param tableName name of table
     * @return the table metrics
     */
    public MRTableMetrics getTableMetrics(String tableName) {
        return statMan.getStats().getTableMetrics(tableName);
    }

    /**
     * Returns the stats report manager
     * @return the stats report manager
     */
    public StatsManager getStatMan() {
        return statMan;
    }

    /**
     * Gets the status updater
     *
     * @return the status updater
     */
    public StatusUpdater getStatusUpdater() {
        return statusUpd;
    }

    /**
     * Returns the subscriber id
     *
     * @return the subscriber id
     */
    public NoSQLSubscriberId getSid() {
        return sid;
    }

    /**
     * Returns the request queue
     *
     * @return the request queue
     */
    public BlockingQueue<XRegionRequest> getReqQueue() {
        return reqQueue;
    }

    /**
     * Unit test only
     */
    public ServiceMDMan getMdMan() {
        return mdMan;
    }

    /**
     * Unit test only
     */
    public ReqRespManager getReqRespMan() {
        return reqRespManager;
    }

    /**
     * Returns all region agents
     */
    public Collection<RegionAgentThread> getAllAgents() {
        return agents.values();
    }

    /**
     * Returns region agent thread for given region, or null if not exist
     * @param region region name
     * @return region agent thread, or null if not exist
     */
    public RegionAgentThread getRegionAgent(String region) {
        return agents.get(region.toLowerCase());
    }

    /**
     * Removes a region agent if exists
     * @param region name of region
     */
    public void removeRegionAgent(String region) {
        agents.remove(region.toLowerCase());
    }

    /**
     * Adds or replaces a region agent
     * @param region name of region
     * @param ra     region agent
     */
    public void addRegionAgent(String region, RegionAgentThread ra) {
        agents.put(region.toLowerCase(), ra);
    }

    /**
     * Returns true if the map has the region agent
     * @param region name of region
     * @return true if the map has the region agent, false otherwise
     */
    public boolean hasRegionAgent(String region) {
        return agents.containsKey(region.toLowerCase());
    }

    /*--------------------*
     * Private functions  *
     *--------------------*/

    /**
     * Adds logger header
     *
     * @param msg logging msg
     * @return logging msg with header
     */
    private String lm(String msg) {
        return "[XRS-" + sid + "] " + msg;
    }

    /**
     * Shuts down all components and free up resources
     */
    private void close() {
        running = false;

        logger.fine(() -> lm("Service starts to close..."));

        /* shut down stat reporting */
        statMan.shutdown();

        /* shut down ongoing responses */
        responses.values().forEach(XRegionRespHandlerThread::shutdown);

        /* terminate all region agent */
        agents.values().forEach(RegionAgentThread::shutDown);
        /* wait for response */
        final boolean succ =
            new PollCondition(POLL_INTERVAL_MS, SHUTDOWN_TIMEOUT_MS) {
                @Override
                protected boolean condition() {
                    return regionAgentsStopped();
                }
            }.await();

        if (!succ) {
            logger.info("timeout in waiting for region agent to shutdown," +
                        " continue shutdown the service");
        } else {
            logger.fine(() -> lm("All region agents shut down"));
        }

        /* shut down request generator */
        if (reqRespManager != null) {
            reqRespManager.shutdown();
        }

        /* shut down metadata manager */
        if (mdMan != null) {
            mdMan.shutdown();
        }

        if (ec != null) {
            ec.shutdownNow();
            logger.fine(() -> lm("All response handler threads shutdown"));
        }

        logger.info(lm("Service shuts down" +
                       ", statistics:\n" + statMan.getStats()));
    }

    /**
     * Returns true if all region agent stops, false otherwise
     *
     * @return true if all region agent stops, false otherwise
     */
    private boolean regionAgentsStopped() {
        for (RegionAgentThread value : agents.values()) {
            if (!value.isCanceled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Initialize all PITR tables at start up. Wait till done or timeout
     *
     * @throws ServiceException     if fail to initialize the tables
     * @throws IOException          if fail to ensure the path
     * @throws InterruptedException if interrupted
     */
    private void initPITR()
        throws ServiceException, IOException, InterruptedException {
        final Set<String> tbs = mdMan.getPITRTables();
        if (tbs.isEmpty()) {
            logger.info(lm("No PITR table"));
            return;
        }

        logger.info(lm("Initializing PITR tables=" + tbs));
        final RegionInfo source = mdMan.getServRegion();
        try {
            /* start/resume the agent from last checkpoint */
            createRegionAgent(source, null, tbs, PITR, STREAM);
        } catch (TimeoutException te) {
            final String err = "Timeout in creating region for tables=" +
                               tbs + " from=" + source;
            final ServiceException sae =
                new ServiceException(sid, err);
            sae.addRegionFailure(source, tbs, te);
            throw sae;
        }
        logger.info(lm("PITR agent started for table=" + tbs + " from=" +
                       source));
    }

    /**
     * Resumes streams for mrt tables at start up. Wait till done or timeout
     *
     * @throws ServiceException     if fail to initialize the tables
     * @throws IOException          if fail to ensure the path
     * @throws InterruptedException if interrupted
     */
    private void initMRT()
        throws ServiceException, IOException, InterruptedException {

        if (mdMan.getMRTNames().isEmpty()) {
            logger.info(lm("No MRT table with remote region"));
            return;
        }

        logger.info((lm("Initializing MRT")));
        /* build a map from region to tables */
        final Map<RegionInfo, Set<String>> r2t = mdMan.getRegionMRTMap();
        logger.fine(lm("List of regions and its tables:\n" +
                       r2t.keySet().stream()
                          .map(r -> r.getName() + ": " + r2t.get(r))
                          .collect(Collectors.joining("\n"))));

        /* submit request for each region */
        for (RegionInfo source : r2t.keySet()) {
            final Set<String> tbs = r2t.get(source);
            try {
                /* start/resume the agent from last checkpoint */
                createRegionAgent(source, mdMan.getServRegion(), tbs, MRT,
                                  STREAM);
                logger.fine(lm("MRT region agent started for tables=" + tbs +
                               " from=" + source.getName()));
            } catch (TimeoutException te) {
                final String err = "Timeout in creating region for tables=" +
                                   tbs + " from=" + source.getName();
                final ServiceException sae =
                    new ServiceException(sid, err);
                sae.addRegionFailure(source, tbs, te);
                throw sae;
            } catch (PublisherFailureException pfe) {
                final String err = "Fail to create publisher for tables=" +
                                   tbs + " from=" + source.getName();
                final ServiceException sae =
                    new ServiceException(sid, err);
                sae.addRegionFailure(source, tbs, pfe);
            }
        }
        logger.info((lm("Streams for MRT tables=" + mdMan.getMRTNames() +
                        " started")));
    }

    /**
     * Checks all region agent to ensure they are good. If a region agent is
     * canceled due to error, throw exception to let the service agent shut
     * down.
     *
     * @throws ServiceException if any region agent is down due to error.
     */
    private void checkRegionAgent() throws ServiceException {

        final Set<RegionAgentThread> failAgents =
            agents.values().stream()
                  .filter(/* agent is canceled without explicit request */
                      agent -> agent.isCanceled() &&
                               !agent.isShutdownRequested())
                  .collect(Collectors.toSet());
        if (failAgents.isEmpty()) {
            logger.finest(lm("All region agents are green."));
            return;
        }

        final ServiceException sae =
            new ServiceException(sid, "Region agent failed");
        failAgents.forEach(fa -> {
            final Set<String> tbl = fa.getTables();
            final Throwable cause = fa.getCauseOfFaillure();
            final RegionInfo srcRegion = fa.getSourceRegion();
            sae.addRegionFailure(srcRegion, tbl, cause);
        });

        throw sae;
    }

    /**
     * Processes multi-region table in the request.
     *
     * @param req request
     * @throws ServiceException if fail to process MRT
     * @throws IOException      if fail to ensure the path
     */
    private synchronized void processMRT(XRegionRequest req)
        throws ServiceException, IOException {

        final XRegionRequest.RequestType type = req.getReqType();
        if (!type.isMRTRequest()) {
            throw new IllegalArgumentException("Not multi-region table " +
                                               "request, type=" + type);
        }

        final long reqId = req.getReqId();
        /* only one MRT in each request */
        final String tbl = req.getTables().iterator().next();
        /* response handler */
        final XRegionRespHandlerThread rht = req.getResp();
        /* add the table to each region, create an agent if necessary */
        for (RegionInfo src : req.getSrcRegions()) {

            try {
                final RegionAgentThread ag = getRegionAgent(src.getName());
                if (ag != null && !ag.isShutdownRequested()) {
                    logger.fine(() -> lm("Find a live agent for region=" +
                                         src.getName()));
                    /*
                     * there is already a live agent from the source,
                     * simply add or remove the table
                     */
                    switch (type) {
                        case MRT_ADD:
                            ag.addTable(req.getResp(), tbl);
                            break;
                        case MRT_REMOVE:
                            ag.removeTable(req.getResp(), tbl);
                            break;
                        default:
                            throw new IllegalStateException(
                                "Unsupported request type=" + type);

                    }
                    logger.fine(lm("Relay request id=" + req.getReqId() +
                                   " to agent=" + ag.getAgentId() +
                                   " type= " + req.getReqType() +
                                   " table=" + tbl));
                    continue;
                }

                /* no existing agent for the source */
                switch (type) {
                    case MRT_ADD:
                        /* create a new agent to initialize */
                        createRegionAgent(src, mdMan.getServRegion(),
                                          req.getTables(),
                                          MRT, INIT_TABLE);
                        final String msg = "MRT agent created from=" +
                                           src.getName() +
                                           " to stream table=" + tbl;
                        logger.info(lm(msg));
                        req.getResp().regionSucc(src, msg);
                        break;
                    case MRT_REMOVE:
                        /*
                         * a bit strange to remove a table from the source that
                         * there is no agent or agent has been shutdown.
                         */
                        req.getResp().regionSucc(
                            src, "Ignore removing table=" + tbl +
                                 ", there is no stream from " +
                                 "source=" + src.getName());
                        break;
                    default:
                        throw new IllegalStateException(
                            "Unsupported request type=" + type);
                }

            } catch (PublisherFailureException pfe) {
                final String err = "Cannot create publisher for table=" +
                                   tbl + " from=" + src.getName() + ", " +
                                   pfe.getMessage();
                logger.warning(lm(err));
                req.getResp().regionFail(src, pfe);
                /* if one region failed, no need to continue */
                break;
            } catch (TimeoutException te) {
                final String err = "Timeout in creating MRT agent for table=" +
                                   tbl + " from=" + src.getName();
                logger.warning(lm(err));
                req.getResp().regionFail(src, te);
                /* if one region failed, no need to continue */
                break;
            } catch (InterruptedException ie) {
                final String err;
                err = "Interrupted in req=" + req.getReqType() +
                      " table=" + tbl + " from=" + src.getName();
                logger.warning(lm(err));
                req.getResp().regionFail(src, ie);
                final ServiceException sae =
                    new ServiceException(sid, err);
                sae.addRegionFailure(src, req.getTables(), ie);
                req.getResp().regionFail(src, ie);
                throw sae;
            }
        }
        /* start response handler thread to wait for results */
        responses.put(reqId, rht);
        rht.start();
    }

    /**
     * Processes region request
     *
     * @param req request
     * @throws ServiceException if fail to add/remove any region
     */
    private synchronized void processRegion(XRegionRequest req)
        throws ServiceException {

        final XRegionRequest.RequestType type = req.getReqType();
        if (!type.isRegionRequest()) {
            throw new IllegalArgumentException("Not a region request, " +
                                               "request type=" + type);
        }

        final long reqId = req.getReqId();
        final boolean add = req.getReqType().equals(REGION_ADD);
        /* response handler */
        final XRegionRespHandlerThread rht = req.getResp();
        final RegionInfo region = req.getSrcRegions().iterator().next();
        if (add) {
            /*
             * the agent does not need do much when adding region, other than
             * just ack the server.
             */
            rht.postSuccResp();
            logger.info(lm("Region=" + region.getName() + " added in" +
                           " request id=" + reqId));
        } else {
            /*
             * if region is removed, the agent will check if any live streams
             * from the removed region. If no, the agent would post SUCC
             * response. If yes, the agent would terminate all such streams.
             */
            final String rname = region.getName();
            if (!hasRegionAgent(rname)) {
                rht.postSuccResp();
                logger.info(lm("No live streams from region=" +
                               region.getName() + ", region dropped in " +
                               "request id=" + reqId));
            } else {
                final RegionAgentThread rthread = getRegionAgent(rname);
                rthread.shutDown();
                removeRegionAgent(rname);
                /* delete all table checkpoints from that region */
                mdMan.delAllInitCkpt(region.getName());
                mdMan.removeRemoteTables(region.getName());
                rht.postSuccResp();
                logger.info(lm("Request id=" + reqId + ", type=" + type +
                               ", region=" + region.getName() +
                               ", stream shutdown and region agent removed"));
            }
        }
    }

    /**
     * Creates a region agent from source with given tables
     *
     * @param source source region
     * @param target target region
     * @param tables tables
     * @param type   region agent type
     * @param mode   region agent start mode
     * @throws PublisherFailureException if fail to create publisher
     * @throws TimeoutException          if timeout in wait for agent ready
     * @throws IOException               if fail to ensure the path
     * @throws InterruptedException      if interrupted
     */
    private void createRegionAgent(RegionInfo source,
                                   RegionInfo target,
                                   Set<String> tables,
                                   RegionAgentConfig.Type type,
                                   RegionAgentConfig.StartMode mode)
        throws PublisherFailureException, TimeoutException, IOException,
        InterruptedException {

        final RegionInfo host = type.equals(PITR) ? source : target;
        final RegionAgentConfig.Builder builder =
            new RegionAgentConfig.Builder(sid, type, mode, host, source,
                                          target, config)
                .setTables(tables)
                .setSecurityConfig(source.getSecurity())
                /* disable checkpoint if in unit test */
                .setEnableCheckpoint(!TestStatus.isActive());
        final RegionAgentConfig conf = builder.build();
        final RegionAgentThread ag = new RegionAgentThread(conf, mdMan,
                                                           statusUpd, logger);
        ag.start();

        final boolean succ = new PollCondition(CHANGE_RESULT_POLL_INTV_MS,
                                               CHANGE_RESULT_TIMEOUT_MS) {
            @Override
            protected boolean condition() {
                /* wait for agent to start streaming or table copy */
                return STREAMING.equals(ag.getStatus()) ||
                       INITIALIZING_TABLES.equals(ag.getStatus()) ||
                       shutDownRequested;
            }
        }.await();

        if (shutDownRequested) {
            throw new InterruptedException("in shutdown");
        }

        if (!succ) {
            final String err = "Timeout in creating region agent for " +
                               " tables=" + tables +
                               " from region=" + source.getName() +
                               " type=" + type +
                               " agent status=" + ag.getStatus();
            /* shut down unsuccessful region agent to avoid dangling threads */
            ag.shutDown();
            throw new TimeoutException(err);
        }
        addRegionAgent(source.getName(), ag);
    }

    /**
     * Processes PITR request
     *
     * @param req request body
     */
    private void processPITR(XRegionRequest req) {
        final XRegionRequest.RequestType type = req.getReqType();
        if (!PITR_ADD.equals(type) && !PITR_REMOVE.equals(type)) {
            throw new IllegalArgumentException("Only add or remove " +
                                               "PITR table is allowed, but " +
                                               "get type=" + type);
        }

        //TODO: process PITR request
    }

    /**
     * Default status updater
     */
    private class DefaultStatusUpdater implements StatusUpdater {
        @Override
        public void post(RegionInfo region,
                         RegionAgentStatus status,
                         Set<String> tables) {
            //TODO: post agent status
            logger.fine(lm("Agent(region=" + region.getName() +
                           ", table=" + tables +
                           ") status=" + status));

        }
    }

    /**
     * Unit test only
     */
    public boolean isShutDownRequested() {
        return shutDownRequested;
    }
}
