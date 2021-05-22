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

package oracle.kv.impl.admin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import oracle.kv.impl.admin.plan.Plan;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.fault.CommandFaultException;
import oracle.kv.impl.streamservice.ServiceMessage;
import oracle.kv.impl.streamservice.MRT.Manager;
import oracle.kv.impl.streamservice.MRT.Request;
import oracle.kv.impl.streamservice.MRT.Request.CreateTable;
import oracle.kv.impl.streamservice.MRT.Request.DropTable;
import oracle.kv.impl.streamservice.MRT.Request.UpdateTable;
import oracle.kv.impl.streamservice.MRT.Response;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.util.ErrorMessage;

import com.sleepycat.je.utilint.StoppableThread;

/**
 * Multi-region table service manager.
 *
 * This manager is used to handle messages to and from the multi-region service.
 *
 * It is assumed that request {@literal IDs > 0} are plan IDs. Request
 * {@literal IDs <= 0} are reserved.
 */
public class MRTManager extends Manager {

    /* Public for unit test. */
    public static final int CLEANING_PASS_INTERVAL_MS = 60 * 1000;

    private final Admin admin;

    private StreamServiceTableCleaner cleaner = null;

    /* True if shutdown has been called. */
    private volatile boolean shutdown = false;

    MRTManager(Admin admin) {
        super(admin.getLogger());
        this.admin = admin;
    }

    /**
     * Posts a create table message.
     */
    public void postCreateMRT(int requestId, TableImpl table,  int seqNum) {
        postRequest(new CreateTable(requestId, table, seqNum));
    }

    /**
     * Posts an update table message.
     */
    public void postUpdateMRT(int requestId, TableImpl table,  int seqNum) {
        postRequest(new UpdateTable(requestId, table, seqNum));
    }

    /**
     * Posts a drop table message.
     */
    public void postDropMRT(int requestId,
                            long tableId,
                            String tableName,
                            int seqNum) {
        postRequest(new DropTable(requestId, tableId, tableName, seqNum));
    }

    /**
     * Posts a create region message.
     */
    public void postCreateRegion(int requestId, Region region,  int seqNum) {
        postRequest(new Request.CreateRegion(requestId, region, seqNum));
    }

    /**
     * Posts a drop region message.
     */
    public void postDropRegion(int requestId, int regionId,  int seqNum) {
        postRequest(new Request.DropRegion(requestId, regionId, seqNum));
    }

    /**
     * Override for cleaning. Request cleaning each time a request is posted.
     * The cleaner will run until the request can be removed.
     */
    @Override
    protected void postRequest(Request message) {
        super.postRequest(message);
        requestCleaning();
    }

    /**
     * Override for cleaning. If a response was received, start cleaning.
     */
    @Override
    public Response getResponse(int requestId) {
        final Response response = super.getResponse(requestId);
        if (response != null) {
            requestCleaning();
        }
        return response;
    }

    /**
     * Removes the request with the specified ID. Returns true if the request
     * existed and was deleted.
     */
    private boolean deleteRequest(int requestId) {
        logger.fine(() -> this + ": removing request for " + requestId);
        return execute(() ->
                           getTableAPI().delete(createRequestKey(requestId),
                                                null /*prevRow*/,
                                                WRITE_OPTIONS));
    }

    /**
     * Removes the response with the specified ID. Returns true if the response
     * existed and was deleted.
     */
    private boolean deleteResponse(int requestId) {
        logger.fine(() -> this + ": removing response for " + requestId);
        return execute(() ->
                           getTableAPI().delete(createResponseKey(requestId),
                                                null /*prevRow*/,
                                                WRITE_OPTIONS));
    }

    @Override
    protected TableAPI getTableAPI() {
        return admin.getInternalKVStore().getTableAPI();
    }

    @Override
    protected void handleIOE(String error, IOException ioe) {
        logger.warning(error);
        throw new CommandFaultException(this + ": " + error, ioe,
                                        ErrorMessage.NOSQL_5500, null);
    }

    private synchronized void requestCleaning() {
        if (shutdown || (cleaner != null) && cleaner.requestCleaning()) {
            return;
        }
        cleaner = new StreamServiceTableCleaner();
        cleaner.start();
    }

    /**
     * Shutdown the service.
     */
    synchronized void shutdown(boolean force) {
        shutdown = true;
        if (cleaner != null) {
            cleaner.shutdown(force);
            cleaner = null;
        }
    }

    @Override
    protected boolean isShutdown() {
        return shutdown;
    }

    @Override
    public String toString() {
        return "MRTManager";
    }

    /**
     * Stream table cleaner thread. The cleaner will run as long as there
     * are request or response messages present in the tables.
     *
     * A request can be deleted once a response for that request is
     * posted, and the associated plan is in a terminal state or pruned.
     *
     * A response can be removed if the corresponding request is removed.
     *
     * TODO - Once more than one stream service is supported this thread
     * should be pulled out into a general purpose thread for all services.
     */
    private class StreamServiceTableCleaner extends StoppableThread {
        private static final int THREAD_SOFT_SHUTDOWN_MS = 5000;

        /* True if shutdown is called with force == false */
        private boolean wait = false;

        private volatile boolean isShutdown = false;
        private volatile boolean cleaningRequested = true;

        StreamServiceTableCleaner() {
            super("StreamServiceTableCleaner");
        }

        /**
         * Shutdown the thread. If force is true the call will not wait for
         * the thread to exit.
         */
        private void shutdown(boolean force) {
            wait = !force;
            shutdownThread(logger);
        }

        /* -- From StoppableThread -- */

        @Override
        protected synchronized int initiateSoftShutdown() {
            isShutdown = true;
            notifyAll();
            return wait ? 0 : THREAD_SOFT_SHUTDOWN_MS;
        }

        @Override
        protected Logger getLogger() {
            return logger;
        }

        @Override
        public void run() {
            logger.info(() -> "Starting " + this);
            while (!isShutdown) {
                if (clean()) {
                    break;
                }
                cleaningWait();
            }
            logger.info(() -> "Exiting " + this);
        }

        /*
         * Makes a pass through the response messages looking for any that
         * can be removed. Returns true if all cleaning is done.
         */
        private boolean clean() {
            logger.info(() -> this + " clean");
            boolean pendingResponses = false;

            /*
             * See if any requests/responses be deleted. This can be done
             * if the corresponding plan is gone or in a terminal state.
             */
            final MIterator itr = new MIterator(getResponseTable());
            while (itr.hasNext() && !isShutdown) {
                if (!checkForDelete(itr.next())) {
                    pendingResponses = true;
                }
            }
            if (pendingResponses) {
                return false;
            }

            /*
             * If there are no pending responses, check if there are any
             * requests.
             */
             if (new MIterator(getRequestTable()).hasNext()) {
                 return false;
            }
            return checkForDone();
        }

        /*
         * Checks whether the specified response and corresponding request
         * messages can be deleted. Returns true if both messages are removed.
         *
         * Messages can be removed if their associated plan is terminal or has
         * been removed.
         */
        private boolean checkForDelete(ServiceMessage response) {
            final int requestId = response.getRequestId();
            assert requestId > 0;
            final Plan plan = admin.getPlanById(requestId);
            /*
             * Can't remove messages if the plan is still around and in a
             * non-terminal state.
             */
            if ((plan != null) && !plan.getState().isTerminal()) {
                return false;
            }

            /* If the request is present remove it. */
            if (getRequest(requestId) != null) {

                /* If the removed failed, retry on next pass */
                if (!deleteRequest(requestId)) {
                    return false;
                }
            }

            /* Plan is gone, request is gone, safe to remove the response. */
            return deleteResponse(requestId);
        }

        /*
         * Checks whether to exit when everything is done. If true is returned
         * a new request for cleaning has come in during cleaning.
         */
        private synchronized boolean checkForDone() {
            if (cleaningRequested) {
                cleaningRequested = false;
            } else {
                isShutdown = true;
            }
            return isShutdown;
        }

        /**
         * Requests that cleaning be preformed. Returns true if the request was
         * successful. If false is returned a new cleaner thread needs to be
         * started.
         */
        synchronized boolean requestCleaning() {
            if (isShutdown) {
                return false;
            }
            cleaningRequested = true;
            notifyAll();
            return true;
        }

        private synchronized void cleaningWait() {
            if (isShutdown) {
                return;
            }
            try {
                wait(CLEANING_PASS_INTERVAL_MS);
            } catch (InterruptedException ie) {
                if (isShutdown) {
                    return;
                }
                throw new IllegalStateException("Unexpected interrupt", ie);
            }
        }

        class MIterator extends MessageIterator<ServiceMessage> {

            MIterator(Table messageTable) {
                super(messageTable, 0, 10, TimeUnit.SECONDS);
            }

            @Override
            protected ServiceMessage getMessage(Row row) throws IOException {
                return new ServiceMessage(row);
            }
        }
    }
}
