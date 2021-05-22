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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.table.Table;

import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * Object to represent the response of a request submitted to cross-region
 * service
 */
public abstract class XRegionRespHandlerThread extends StoppableThread {

    /* soft shutdown waiting time in ms */
    private static final int SOFT_SHUTDOWN_WAIT_MS = 10 * 1000;

    /* default timeout in ms */
    private final static long DEFAULT_TIMEOUT_MS = 60 * 60 * 1000;

    /* sleep in ms between polling and putting */
    private static final int SLEEP_MS = 1000;

    /* logger */
    private final Logger logger;

    /* region failures */
    private final ConcurrentMap<String, Throwable> regionFail;

    /* region and payload of success response */
    private final ConcurrentMap<String, Object> regionSucc;

    /* request id */
    protected final long reqId;

    /* request type */
    private final XRegionRequest.RequestType reqType;

    /* tables in the request */
    private final Set<Table> tables;

    /* all regions of response, or null if no region involved */
    private final Set<RegionInfo> regions;

    /* metadata management */
    private final ServiceMDMan mdMan;

    /* time out in ms */
    private volatile long timeoutMS;

    /* true if shutdown */
    private volatile boolean shutdown;

    /* true if result is ready */
    private volatile boolean ready;

    /**
     * Creates cross-region response handler thread
     *
     * @param reqId       request id
     * @param reqType     request type
     * @param regions     source regions
     * @param tables      tables in request
     * @param mdMan       metadata manager
     * @param logger      logger
     */
    public XRegionRespHandlerThread(long reqId,
                                    XRegionRequest.RequestType reqType,
                                    Set<RegionInfo> regions,
                                    Set<Table> tables,
                                    ServiceMDMan mdMan,
                                    Logger logger) {
        super("XRHT" + reqId);
        this.reqId = reqId;
        this.reqType = reqType;
        this.regions = regions;
        this.tables = tables;
        this.mdMan = mdMan;
        this.logger = logger;

        shutdown = false;
        ready = false;
        regionFail = new ConcurrentHashMap<>();
        regionSucc = new ConcurrentHashMap<>();
        timeoutMS = DEFAULT_TIMEOUT_MS;
    }

    /**
     * Posts failure response for a request from a region
     *
     * @param msg  summary of error
     */
    public abstract void postFailResp(String msg);

    /**
     * Posts success response
     */
    public abstract void postSuccResp();

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected int initiateSoftShutdown() {
        if (!shutdown) {
            shutdown = true;
        }
        logger.fine(lm("Signal thread " + getName() + " to shutdown" +
                       ", wait for " + SOFT_SHUTDOWN_WAIT_MS +
                       " ms to let it exit"));
        return SOFT_SHUTDOWN_WAIT_MS;
    }

    @Override
    public void run() {

        final long start = System.currentTimeMillis();
        logger.fine(lm("Response thread started"));
        try {
            while (!shutdown) {

                if (System.currentTimeMillis() - start >= timeoutMS) {
                    throw new TimeoutException("Timeout in waiting for result");
                }

                /* check if any region failed */
                if (!regionFail.keySet().isEmpty()) {
                    final String err = "Request " + reqId
                                       + " failed at regions " +
                                       regionFail.keySet();
                    logger.warning(lm(err));

                    final StringBuilder detail = new StringBuilder();
                    regionFail.forEach((region, cause) ->
                                           detail.append("\n")
                                                 .append(region).append(": ")
                                                 .append(cause.getMessage()));
                    logger.fine(lm(detail.toString()));
                    postFailResp(err + ", " + detail);
                    return;
                }

                /* if all region return succ */
                if (regionSucc.keySet().containsAll(getRegionNames())) {
                    logger.fine(lm("Request is success from all regions " +
                                   regionSucc.keySet()));
                    ready = true;
                    postSuccResp();
                    updateMdMan();
                    return;
                }

                logger.fine(lm("Success regions " + getRegionSucc() +
                               ", waiting for " +
                               getRegionNames()
                                   .stream()
                                   .filter(r -> !regionSucc.containsKey(r))
                                   .collect(Collectors.toSet())));

                Thread.sleep(SLEEP_MS);
            }
            /*
             * In order keep the incomplete request open in request table, we
             * should not post failure response when the service shuts down.
             */
            logger.fine(() -> lm("Response handler shuts down before all " +
                                 "results are ready"));

        } catch (Exception exp) {
            if (!shutdown) {
                logger.warning(lm("Fail to process request=" + reqId +
                                  ", error=" + exp.getMessage()));
                logger.fine(() -> lm(LoggerUtils.getStackTrace(exp)));
                postFailResp(exp.getMessage());
            } else {
                logger.info(lm("Abort post response in shutdown, " +
                               "req id=" + reqId));
            }
        } finally {
            logger.fine(lm("Response handler thread exits"));
        }
    }

    /**
     * Gets success regions
     */
    public Set<String> getRegionSucc() {
        return regionSucc.keySet();
    }

    /**
     * Returns true if response is ready, false otherwise
     *
     * @return true if response is ready, false otherwise
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns request id
     * @return request id
     */
    public long getReqId() {
        return reqId;
    }

    /**
     * Shuts down the cross-region service agent
     */
    public void shutdown() {
        if (shutdown) {
            logger.fine(lm("Shutdown already signalled"));
            return;
        }
        shutdown = true;
        shutdownDone(logger);
        logger.fine(lm("Response thread shuts down."));
    }

    /**
     * Posts success response for a request from a region
     *
     * @param region region of response
     * @param obj    object associated with success message
     */
    public void regionSucc(RegionInfo region, Object obj) {
        logger.fine(() -> lm("Success, id=" + reqId +
                             ", region=" + region.getName() +
                             ", obj=" + obj));
        regionSucc.put(region.getName(), obj);
    }

    /**
     * Posts failure response for a request from a region
     *
     * @param region region of response
     * @param cause  cause if failure, null if success
     */
    public void regionFail(RegionInfo region, Throwable cause) {
        logger.info(lm("Fail, id=" + reqId + ", region=" + region.getName() +
                       ", cause=" + cause.getMessage()));
        regionFail.put(region.getName(), cause);
    }

    /**
     * Unit test only
     *
     * Sets the timeout in ms, if not set, default time out is used.
     *
     * @param toMs the timeout in ms
     */
    public void setTimeoutMS(long toMs) {
        if (toMs < 0) {
            throw new IllegalArgumentException("Invalid timeout " + toMs);
        }
        timeoutMS = toMs;
    }

    /**
     * Returns the set of tables in request
     *
     * @return set of tables
     */
    Set<String> getTables() {
        if (tables == null) {
            return null;
        }
        return tables.stream().map(Table::getFullNamespaceName)
                     .collect(Collectors.toSet());
    }

    /**
     * Returns the set of regions
     *
     * @return set of regions
     */
    Set<RegionInfo> getRegions() {
        return regions;
    }

    /*--------------------*
     * Private functions  *
     *--------------------*/
    /**
     * Adds logger header
     *
     * @param msg logging msg
     *
     * @return logging msg with header
     */
    private String lm(String msg) {
        return "[XRHT-Req-" + reqId + "] " + msg;
    }

    /**
     * Returns a set of region names
     *
     * @return a set of region names
     */
    private Set<String> getRegionNames(){
        return regions.stream().map(RegionInfo::getName)
                      .collect(Collectors.toSet());
    }

    /**
     * Update metadata manager after operation is success
     */
    private void updateMdMan() {

        if (mdMan == null) {
            /* unit test */
            return;
        }
        switch (reqType) {
            case PITR_REMOVE:
                mdMan.removePITRTable(tables);
                break;
            case PITR_ADD:
                mdMan.addPITRTable(tables);
                break;
            case MRT_REMOVE:
                mdMan.removeMRTable(tables);
                break;
            case MRT_ADD:
            case MRT_UPDATE:
                mdMan.addUpdateMRTable(tables);
                break;
            default:
        }
    }
}
