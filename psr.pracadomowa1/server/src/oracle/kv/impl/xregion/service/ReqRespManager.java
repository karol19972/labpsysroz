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

import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.MRT_ADD;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.MRT_REMOVE;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.MRT_UPDATE;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.REGION_ADD;
import static oracle.kv.impl.xregion.service.XRegionRequest.RequestType.REGION_DROP;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.FaultException;
import oracle.kv.KVStore;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.streamservice.MRT.Manager;
import oracle.kv.impl.streamservice.MRT.Request;
import oracle.kv.impl.streamservice.MRT.Response;
import oracle.kv.impl.streamservice.ServiceMessage;
import oracle.kv.impl.util.RateLimitingLogger;
import oracle.kv.impl.xregion.stat.ReqRespStat;
import oracle.kv.pubsub.NoSQLSubscriberId;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;

import com.sleepycat.je.utilint.LoggerUtils;

/**
 * Object responsible for interaction with request and response table
 */
public class ReqRespManager extends Manager {

    /* rate limiting log period in ms */
    private static final int RL_LOG_PERIOD_MS = 10 * 1000;

    /* timeout in ms to put request queue */
    private static final int REQ_QUEUE_PUT_TIMEOUT_MS = 1000;

    /* max back off in ms before retry */
    private static final long MAX_BACKOFF_MS = 30 * 1000;

    /* default request polling initial delay in secs */
    private static final int DEFAULT_REQ_POLLING_INIT_DELAY_SECS = 1;

    /* table api to deal with request and response table */
    private final TableAPI tableAPI;

    /* subscriber id */
    private final NoSQLSubscriberId sid;

    /* metadata manager */
    private final ServiceMDMan mdMan;

    /* request queue */
    private final BlockingQueue<XRegionRequest> reqQueue;

    /* metrics */
    private final ReqRespStat metrics;

    /* executor for scheduled task */
    private final ScheduledExecutorService ec;

    /* requests that are in process */
    private final ConcurrentMap<Long, XRegionRequest> reqInProcess;

    /* true if requested to shut down */
    private volatile boolean shutDownRequested;

    /* rate limiting logger */
    private final RateLimitingLogger<String> rlLogger;

    ReqRespManager(NoSQLSubscriberId sid,
                   BlockingQueue<XRegionRequest> reqQueue,
                   ServiceMDMan mdMan,
                   Logger logger) {
        super(logger);
        this.sid = sid;
        this.reqQueue = reqQueue;
        this.mdMan = mdMan;
        metrics = new ReqRespStat();
        shutDownRequested = false;
        tableAPI = mdMan.getServRegionKVS().getTableAPI();
        reqInProcess = new ConcurrentHashMap<>();
        ec = Executors.newSingleThreadScheduledExecutor();
        rlLogger = new RateLimitingLogger<>(RL_LOG_PERIOD_MS, 32, logger);
    }

    /**
     * Returns true if request and response manager is running
     *
     * @return true if request and response manager is running
     */
    public boolean isRunning() {
        return !shutDownRequested;
    }

    /**
     * Starts periodic polling task
     */
    void startPollingTask() {
        ec.schedule(new PollingTask(), DEFAULT_REQ_POLLING_INIT_DELAY_SECS,
                    TimeUnit.SECONDS);
        logger.info(lm("Scheduled polling the request table" +
                       " with initial delay(secs)=" +
                       DEFAULT_REQ_POLLING_INIT_DELAY_SECS +
                       " at interval(secs)=" +
                       getReqTablePollingIntv()));
    }

    @Override
    protected TableAPI getTableAPI() {
        return tableAPI;
    }

    @Override
    protected void handleIOE(String error, IOException ioe) {
        /*
         * When agent encounters IOE in posting response (agent does not post
         * request), the IOE should be logged here instead of being thrown
         * to caller. The agent fail to post the response, and will continue.
         */
        logger.warning(lm("Fail to post response message: " + error +
                          ", IOE error " + ioe.getMessage()));
        logger.fine(lm(LoggerUtils.getStackTrace(ioe)));
    }

    public ReqRespStat getMetrics() {
        return metrics;
    }

    /**
     * Shuts down the request and response manager
     */
    void shutdown() {
        if (shutDownRequested) {
            return;
        }

        shutDownRequested = true;
        ec.shutdownNow();
        /* shut down all ongoing response threads */
        final long shutdownCount =
            reqInProcess.values().stream()
                        .filter(req -> req.getResp() != null)
                        .peek(req -> req.getResp().shutdown())
                        .count();
        logger.fine(() -> lm(shutdownCount + " response thread shuts down"));
        logger.fine(() -> lm("Request response manager shuts down"));
    }

    /**
     * Unit test only
     */
    public NoSQLSubscriberId getSid() {
        return sid;
    }

    /**
     * Submits a cross-region service request to service
     *
     * @param req  request body
     *
     * @throws InterruptedException if interrupted in enqueue
     */
    public void submit(XRegionRequest req) throws InterruptedException {
        if (req == null) {
            return;
        }
        while (!shutDownRequested) {
            try {
                if (reqQueue.offer(req, REQ_QUEUE_PUT_TIMEOUT_MS,
                                   TimeUnit.MILLISECONDS)) {
                    reqInProcess.put(req.getReqId(), req);
                    logger.finest(lm("Request " + req.getReqId() +
                                     " enqueued, type " + req.getReqType()));
                    break;
                }

                logger.finest(lm("Unable enqueue message" + req +
                                 " for " + REQ_QUEUE_PUT_TIMEOUT_MS +
                                 "ms, keep trying..."));
            } catch (InterruptedException e) {

                if (shutDownRequested) {
                    /* in shutdown, ignore */
                    logger.warning(lm("Interrupted, shut down requested, " +
                                      "current request: " + req));
                    return;
                }

                /* This might have to get smarter. */
                logger.warning(lm("Interrupted offering request queue, " +
                                  "message: " + req));
                throw e;
            }
        }
    }

    /**
     * Returns true if request table has a drop request for the table, false
     * otherwise
     *
     * @param tableName name of table
     * @return true if request table has a drop request for the table, false
     * otherwise
     */
    boolean hasDropRequest(String tableName) {
        final RequestIterator itr = getRequestIterator(10, TimeUnit.SECONDS);

        while (true) {
            final Request req;
            try {
                req = nextRequest(itr);
            } catch (InterruptedException ie) {
                return false;
            }
            if (req == null) {
                return false;
            }
            if (req.getType().equals(Request.Type.DROP_TABLE)) {
                final Request.DropTable dt = (Request.DropTable) req;
                if (tableName.equals(dt.getTableName())) {
                    return true;
                }
            }
        }
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
        return "[ReqRespMan-" + sid + "] " + msg;
    }

    /**
     * Handles the request from request table
     *
     * @param req  request from request table
     * @return true if the request is new request and processed, false
     * otherwise
     * @throws InterruptedException if interrupted
     */
    private boolean handleRequest(Request req) throws InterruptedException {
        /* filter processed request */
        if (isReqProcessed(req)) {
            return false;
        }

        /* filter in-process request */
        if (isReqInProcessing(req)) {
            return false;
        }

        switch (req.getType()) {
            case CREATE_REGION:
                final Request.CreateRegion cr = (Request.CreateRegion) req;
                logger.info(lm("To create region=" + cr.getRegion().getName() +
                               ", request=" + req.toString()));
                submitRegionRequest(cr.getRequestId(), REGION_ADD,
                                    cr.getRegion().getId());
                break;

            case DROP_REGION:
                final Request.DropRegion dr = (Request.DropRegion) req;
                logger.info(lm("To drop region, request=" + req.toString()));
                submitRegionRequest(dr.getRequestId(), REGION_DROP,
                                    dr.getRegionId());
                break;

            case CREATE_TABLE:
                final Request.CreateTable ct = (Request.CreateTable) req;
                logger.info(lm("To create table=" +
                               ct.getTable().getFullNamespaceName() +
                               ", request=" + req.toString()));
                processTableCreate(ct);
                break;

            case UPDATE_TABLE:
                /* update table, add or remove regions */
                final Request.UpdateTable ut = (Request.UpdateTable) req;
                logger.info(lm("To update table=" +
                               ut.getTable().getFullNamespaceName() +
                               ", request=" + req.toString()));
                processTableUpdate(ut);
                break;

            case DROP_TABLE:
                final Request.DropTable dt = (Request.DropTable) req;
                logger.info(lm("To drop table=" + dt.getTableName() +
                               ", request=" + req.toString()));
                processTableDrop(dt);
                break;

            default:
                throw new IllegalStateException("Unsupported request type=" +
                                                req.getType());
        }

        return true;
    }

    /**
     * Processes table creation request
     *
     * @param ct table creation request
     * @throws InterruptedException if interrupted during request submission
     */
    private void processTableCreate(Request.CreateTable ct)
        throws InterruptedException {
        final TableImpl t = ct.getTable();

        if (logger.isLoggable(Level.FINE)) {
            /* expensive so only in FINE logging level */
            final Set<RegionInfo> regs = getNotEmptyRegion(t);
            if (!regs.isEmpty()) {
                final String tbl = ct.getTable().getFullNamespaceName();
                final String msg = "Table=" + tbl + " not empty at " +
                                   "regions=" + regs;
                logger.fine(() -> lm(msg));
            }
        }

        final long reqId = ct.getRequestId();
        final Set<RegionInfo> regions = getRegions(t.getRemoteRegions());
        final Set<Table> tbs = Collections.singleton(t);
        final XRegionRequest.RequestType type = MRT_ADD;
        final XRegionResp resp = new XRegionResp(reqId, type, regions, tbs);
        submitReq(type, resp);
    }

    /**
     * Processes table drop request
     *
     * @param dt table drop request
     * @throws InterruptedException if interrupted during request submission
     */
    private void processTableDrop(Request.DropTable dt)
        throws InterruptedException {
        final long tableId = dt.getTableId();
        final long reqId = dt.getRequestId();

        final TableImpl t = (TableImpl) mdMan.getMRTById(tableId);
        if (t == null) {
            final String msg = "Table with id=" + tableId + " is " +
                               "not found, it can be dropped when the agent " +
                               "is down, ignore the request with id=" +
                               dt.getRequestId();
            logger.info(lm(msg));
            final XRegionResp resp = new XRegionResp(reqId, MRT_REMOVE,
                                                     new HashSet<>());
            resp.postSuccResp();
            return;
        }

        final Set<Table> tb = Collections.singleton(t);
        final Set<RegionInfo> reg = getRegions(t.getRemoteRegions());
        final XRegionRequest.RequestType type = MRT_REMOVE;
        final XRegionResp resp = new XRegionResp(reqId, type, reg, tb);
        submitReq(type, resp);
    }

    /**
     * Processes table update request
     *
     * @param ut table update request
     * @throws InterruptedException if interrupted during request submission
     */
    private void processTableUpdate(Request.UpdateTable ut)
        throws InterruptedException {

        final int requestId = ut.getRequestId();
        final TableImpl updated = ut.getTable();
        final String tName = updated.getFullNamespaceName();
        final long tableId = updated.getId();

        /* remember current instance */
        final TableImpl curr = (TableImpl) mdMan.getMRTById(tableId);
        /* update new table instance */
        mdMan.updateMRTable(Collections.singleton(updated));

        /* add regions */
        final Set<Integer> addRids = getAddedRegions(updated, curr);
        final Set<Integer> removeRids = getRemovedRegions(updated, curr);
        /* if region not changed, just update the table instance */
        if (addRids.isEmpty() && removeRids.isEmpty()) {
            logger.fine(() -> lm("Regions in table=" + tName +
                                 " not changed, regions=" +
                                 getRegionInfos(curr.getRemoteRegions())
                                     .stream().map(RegionInfo::getName)
                                     .collect(Collectors.toSet())));
            final Set<Table> tbls = Collections.singleton(updated);
            final XRegionResp resp =
                new XRegionResp(ut.getRequestId(), MRT_UPDATE,
                                null, /* region not changed */ tbls);
            resp.postSuccResp();
            return;
        }

        if (!addRids.isEmpty()) {
            processAddRemoveRegion(requestId, updated, MRT_ADD, addRids);
        }

        if (!removeRids.isEmpty()){
            processAddRemoveRegion(requestId, updated, MRT_REMOVE, removeRids);
        }
    }

    /**
     * Processes add or remove region in table update request
     *
     * @param requestId request id
     * @param table     table
     * @param type      type of operation
     * @param rids      set of region ids
     * @throws InterruptedException if interrupted during request submission
     */
    private void processAddRemoveRegion(long requestId,
                                        Table table,
                                        XRegionRequest.RequestType type,
                                        Set<Integer> rids)
        throws InterruptedException {
        if (rids == null || rids.isEmpty()) {
            throw new IllegalArgumentException("Null region ids");
        }
        final String tableName = table.getFullNamespaceName();
        final Set<Table> tbls = Collections.singleton(table);
        final Set<RegionInfo> rinfos = getRegionInfos(rids);
        final Set<String> rnames = rinfos.stream().map(RegionInfo::getName)
                                         .collect(Collectors.toSet());
        logger.info(lm("To " + type + " table=" + tableName +
                       ", regions=" + rnames));
        final XRegionResp resp = new XRegionResp(requestId, MRT_UPDATE,
                                                 rinfos, tbls);
        submitReq(type, resp);
    }

    /**
     * Translates region ids to region info
     *
     * @param regionIds set of region ids
     * @return set of region info
     */
    private Set<RegionInfo> getRegionInfos(Set<Integer> regionIds) {
        final Set<RegionInfo> ret = new HashSet<>();
        for (int id : regionIds) {
            final String name = mdMan.getRegionName(id);
            if (name == null) {
                throw new IllegalArgumentException("Cannot translate region " +
                                                   "id="+ id + " to region " +
                                                   "name");
            }
            final RegionInfo regionInfo = mdMan.getRegion(name);
            if (regionInfo == null) {
                throw new IllegalArgumentException(
                    "Region=" + name + " is unknown to the xregion service, " +
                    "please check the json config file");
            }
            ret.add(regionInfo);
        }
        return ret;
    }

    /**
     * Compares the updated and existent table to get a set of added regions,
     * or return empty set if no new region is found
     *
     * @param updated updated table
     * @param curr    existing table
     * @return set of ids of added regions
     */
    private Set<Integer> getAddedRegions(TableImpl updated, TableImpl curr) {
        /*
         * creating a MR table with local region only does not generate a
         * request for agent, thus when the user adds regions, it is like a
         * new table, in this case, just return list of remote regions in the
         * updated table.
         */
        if (curr == null) {
            return updated.getRemoteRegions();
        }

        if (updated.getId() != curr.getId()) {
            throw new IllegalArgumentException(
                "Table id of current table=" + curr.getId() +
                " does not match that in updated table=" + updated.getId());
        }
        /* id in the updated table but not in current table */
        return updated.getRemoteRegions().stream()
                      .filter(id -> !curr.getRemoteRegions().contains(id))
                      .collect(Collectors.toSet());
    }

    /**
     * Compares the updated and existent table to get a set of removed regions,
     * or return empty set if no region is removed
     *
     * @param updated updated table
     * @param curr    existing table
     * @return set of ids of remove regions
     */
    private Set<Integer> getRemovedRegions(TableImpl updated, TableImpl curr) {
        if (curr == null) {
            /* nothing to remove */
            return Collections.emptySet();
        }
        if (updated.getId() != curr.getId()) {
            throw new IllegalArgumentException(
                "Table id of current table=" + curr.getId() +
                " does not match that in updated table=" + updated.getId());
        }

        /* id in the current but not in updated */
        return curr.getRemoteRegions().stream()
                   .filter(id -> !updated.getRemoteRegions().contains(id))
                   .collect(Collectors.toSet());
    }

    /**
     * Submits a region request
     *
     * @param reqId request id
     * @param type  type of request
     * @param rid   region id
     *
     * @throws InterruptedException if interrupted
     */
    private void submitRegionRequest(long reqId,
                                     XRegionRequest.RequestType type, int rid)
        throws InterruptedException {
        final Set<RegionInfo> regions = getRegions(Collections.singleton(rid));
        final XRegionResp resp = new XRegionResp(reqId, type, regions);
        if (rid == Region.LOCAL_REGION_ID) {
            processLocalRegionRequest(reqId, resp, type);
            return;
        }

        if (regions.isEmpty()) {
            /* an unknown remote region */
            final String region = mdMan.getRegionName(rid);
            final String err = "Region=" + region + " in req id=" + reqId +
                               " is unknown, please add to the config file " +
                               "before creating it";
            logger.warning(lm(err));
            /*
             * the region is unknown to the agent, post failure to avoid
             * reprocessing the request
             */
            resp.postFailResp(err);
            return;
        }
        submitReq(type, resp);
    }

    /**
     * Processes a region request with local region
     *
     * @param reqId request id
     * @param resp  response handler
     * @param type  type of request
     */
    private void processLocalRegionRequest(long reqId,
                                           XRegionResp resp,
                                           XRegionRequest.RequestType type) {
        /* a local region operation */
        if (REGION_ADD.equals(type)) {
            logger.info(lm("Sets local region, reqId=" + reqId));
            resp.postSuccResp();
            return;
        }

        if (REGION_DROP.equals(type)) {
            final String err = "Trying to drop a local region, " +
                               "reqId=" + reqId;
            logger.warning(lm(err));
            resp.postFailResp(err);
            return;
        }

        throw new IllegalArgumentException("Unsupported request type=" + type +
                                           ", request id=" + reqId);
    }

    /**
     * Submits request
     *
     * @param type  request type
     * @param resp  response handler
     *
     * @throws InterruptedException if interrupted
     */
    private void submitReq(XRegionRequest.RequestType type, XRegionResp resp)
        throws InterruptedException {
        final XRegionRequest req;
        switch (type) {
            case MRT_ADD:
                req = XRegionRequest.getAddMRTReq(resp);
                break;
            case MRT_REMOVE:
                req = XRegionRequest.getRemoveMRTReq(resp);
                break;
            case CHANGE_PARAM:
                req = XRegionRequest.getChangeParamReq(resp);
                break;
            case SHUTDOWN:
                req = XRegionRequest.getShutdownReq();
                break;
            case REGION_ADD:
                req = XRegionRequest.getAddRegionReq(resp);
                break;
            case REGION_DROP:
                req = XRegionRequest.getDropRegionReq(resp);
                break;
            default:
                /* all other request will be ignored */
                return;
        }
        submit(req);
        final String regions = (resp.getRegions() == null) ? "n/a" :
            resp.getRegions().stream().map(RegionInfo::getName)
                .collect(Collectors.toSet()).toString();
        logger.info(lm("Submitted request id=" + resp.getReqId() +
                       ", type=" + type +
                       ", regions=" + regions +
                       ", tables=" + resp.getTables()));

    }

    /**
     * Gets the region info from region ids, only remote region known to the
     * agent will be returned.
     *
     * @param ids set of region ids
     *
     * @return set of remote region info
     */
    private Set<RegionInfo> getRegions(Set<Integer> ids) {
        /* translate region id to name */
        final Set<String> regs = new HashSet<>();
        for (int id : ids) {
            if (id == Region.LOCAL_REGION_ID) {
                continue;
            }

            final String region = mdMan.getRegionName(id);
            if (region == null) {
                final String err = "Unknown region id=" + id;
                logger.fine(lm(err));
                throw new ServiceException(sid, err);
            }
            regs.add(region);
        }

        final Set<RegionInfo> regions = new HashSet<>();
        for (String r : regs) {
            final RegionInfo region = mdMan.getRegion(r);
            if (region == null) {
                /* the region is unknown */
                logger.fine(lm("Unknown region=" + r));
                continue;
            }
            regions.add(region);
        }
        return regions;
    }

    /**
     * Returns true if the request has already been responded
     *
     * @param req request
     *
     * @return true if the req has already been responded, false otherwise
     */
    private boolean isReqProcessed(Request req) {
        final ServiceMessage sm = getResponse(req.getRequestId());
        if (sm == null) {
            return false;
        }
        logger.fine(lm("Req already processed," + " id=" + req.getRequestId() +
                       ", response=" + sm.toString()));
        return true;
    }

    /**
     * Returns true if the request has in being responded
     *
     * @param req request
     *
     * @return true if the req has in being responded, false otherwise
     */
    private boolean isReqInProcessing(Request req) {
        if (!reqInProcess.containsKey((long)req.getRequestId())) {
            return false;
        }
        logger.fine(lm("Req in processing " + " id=" + req.getRequestId()));
        return true;
    }

    /**
     * Returns source region where the table is not empty, or null if the
     * table is empty in all source regions
     *
     * @param table  table instance
     * @return source region that the table is not empty, or null
     */
    private Set<RegionInfo> getNotEmptyRegion(TableImpl table) {
        final Set<RegionInfo> ret = new HashSet<>();
        final String tableName = table.getFullNamespaceName();
        final Set<RegionInfo> regions = getRegions(table.getRemoteRegions());
        for (RegionInfo r : regions) {
            final KVStore kvs = mdMan.getRegionKVS(r);
            final Table tbl = kvs.getTableAPI().getTable(tableName);
            if (tbl == null) {
                /* ok, table not exist */
                continue;
            }
            TableIterator<Row> iter = null;
            try {
                iter = kvs.getTableAPI().tableIterator(
                    table.createPrimaryKey(), null, null);
                if (iter.hasNext()) {
                    ret.add(r);
                }
            } catch (RuntimeException exp) {
                /* just to create trace, OK to skip the region */
                logger.warning(lm("Cannot read table=" + tableName + " at " +
                                  "region=" + r.getName() + ", " +
                                  ", error=" + exp));
            } finally {
                if (iter != null) {
                    iter.close();
                }
            }
        }
        return ret;
    }

    /**
     * Fetches the next request from request table, retry if needed
     *
     * @param itr request table iterator
     * @return the next request or null if iterating all requests
     * @throws InterruptedException if interrupted
     */
    private Request nextRequest(RequestIterator itr)
        throws InterruptedException {
        long backOffMs = 100;
        while (true) {
            try {
                if (!itr.hasNext()) {
                    return null;
                }
                return itr.next();
            } catch (FaultException fe) {
                /* exponential back-off before retry */
                final Throwable cause = fe.getCause();
                final String err = "Back-off on " + fe.getMessage() + ", " +
                                   (cause != null ? cause.getMessage() : "");
                rlLogger.log(err, Level.FINE, lm(err));
                Thread.sleep(backOffMs);
                backOffMs = Math.min(backOffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    /**
     * Polling task
     */
    private class PollingTask implements Runnable {

        @Override
        public void run() {
            logger.fine(ReqRespManager.this.lm("Polling request starts"));
            int numReq = 0;
            try {
                final RequestIterator itr =
                    getRequestIterator(10, TimeUnit.SECONDS);
                while (true) {
                    final Request req = nextRequest(itr);
                    if (req == null) {
                        break;
                    }

                    /* ignore unsupported request */
                    final ServiceMessage.ServiceType st = req.getServiceType();
                    if (!st.equals(ServiceMessage.ServiceType.MRT) &&
                        !st.equals(ServiceMessage.ServiceType.PITR)) {
                        logger.fine(lm("Ignore unsupported service type=" +
                                       st));
                        continue;
                    }

                    try {
                        if (handleRequest(req)) {
                            /* increment request counter */
                            metrics.incrRequest();
                            numReq++;
                        }
                    } catch (InterruptedException ie) {
                        /* interrupted, let outside catch handle */
                        throw ie;
                    } catch (Exception exp) {
                        final String err = "Unable handle request=" + req +
                                           ", " + exp.getMessage();
                        logger.warning(lm(err + "\n" +
                                          LoggerUtils.getStackTrace(exp)));
                    }
                }
            } catch (InterruptedException ie) {
                logger.fine(lm("Interrupted and exit"));
            } catch (RuntimeException cause) {
                if (!shutDownRequested) {
                    //TODO: may suggest a code bug, the service really should
                    // exit if there is an unexpected exception like this. It
                    // is too high risk to make that change right now, so
                    // we just dump warning with stack and let the service
                    // continue. We should revisit this in future
                    logger.warning(lm("Error in polling request table " +
                                      "error: " + cause.getMessage() +
                                      ", call stack:\n" +
                                      LoggerUtils.getStackTrace(cause)));
                }
            } finally {
                /* schedule next polling */
                if (!shutDownRequested && ec != null && !ec.isShutdown()) {
                    ec.schedule(new PollingTask(),
                                getReqTablePollingIntv(),
                                TimeUnit.SECONDS);
                }
                if (numReq > 0) {
                    logger.fine(lm("Request polling thread exits, " +
                                   "# requests in this run=" +
                                   numReq + ", # total requests=" +
                                   metrics.getRequests() + ", next run in " +
                                   getReqTablePollingIntv() + " secs."));
                }
            }
        }
    }

    private int getReqTablePollingIntv() {
        return (mdMan == null ?
            JsonConfig.DEFAULT_REQ_POLLING_INTV_SECS /* unit test */ :
            mdMan.getJsonConf().getRequestTablePollIntvSecs());
    }

    /**
     * Response handler
     */
    private class XRegionResp extends XRegionRespHandlerThread {

        XRegionResp(long reqId,
                    XRegionRequest.RequestType reqType,
                    Set<RegionInfo> regions,
                    Set<Table> tables) {
            super(reqId, reqType, regions, tables, mdMan, logger);
        }

        XRegionResp(long reqId,
                    XRegionRequest.RequestType reqType,
                    Set<RegionInfo> regions) {
            super(reqId, reqType, regions, null, mdMan, logger);
        }

        /**
         * Posts failure response
         *
         * @param msg  summary of error
         */
        @Override
        public void postFailResp(String msg) {
            final Response.Error sm = new Response.Error((int)reqId, msg);
            postResponse(sm);
            logger.info(lm("Post failure response, id=" + sm.getRequestId() +
                           ": " + sm.getErrorMessage()));
            reqInProcess.remove(reqId);
            metrics.incrResponse();
        }

        /**
         * Posts success response
         */
        @Override
        public void postSuccResp() {
            final  Response.Success sm = new Response.Success((int)reqId);
            postResponse(sm);
            logger.info(lm("Post success response id=" + sm.getRequestId()));
            reqInProcess.remove(reqId);
            metrics.incrResponse();
        }
    }
}
