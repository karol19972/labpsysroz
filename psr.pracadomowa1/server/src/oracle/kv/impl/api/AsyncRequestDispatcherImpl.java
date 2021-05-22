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

package oracle.kv.impl.api;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static oracle.kv.impl.async.FutureUtils.complete;
import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.async.FutureUtils.whenComplete;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.FaultException;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreException;
import oracle.kv.RequestLimitConfig;
import oracle.kv.ServerResourceLimitException;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.api.rgstate.RepGroupState;
import oracle.kv.impl.api.rgstate.RepNodeState;
import oracle.kv.impl.async.AsyncOption;
import oracle.kv.impl.async.EndpointConfigBuilder;
import oracle.kv.impl.async.EndpointGroup;
import oracle.kv.impl.async.exception.DialogBackoffException;
import oracle.kv.impl.async.exception.DialogException;
import oracle.kv.impl.async.exception.DialogUnknownException;
import oracle.kv.impl.async.exception.GetUserException;
import oracle.kv.impl.fault.RNUnavailableException;
import oracle.kv.impl.security.AuthContext;
import oracle.kv.impl.security.login.LoginHandle;
import oracle.kv.impl.security.login.LoginManager;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.CommonLoggerUtils;
import oracle.kv.impl.util.KVThreadFactory;
import oracle.kv.impl.util.registry.AsyncRegistryUtils;

/**
 * An implementation of RequestDispatcher that supports asynchronous
 * operations.
 */
public class AsyncRequestDispatcherImpl extends RequestDispatcherImpl {

    /**
     * The amount of time in milliseconds to allow for a single roundtrip
     * network communication with the server.
     */
    private final long networkRoundtripTimeoutMs;

    /**
     * Whether we are already delivering a result in the current thread. If the
     * user calls a synchronous method on the future that makes another API
     * call, and the dialog layer handles the operation in the current thread,
     * then we need to use a different thread to deliver the result to avoid
     * recursion which might exceed the supported stack depth.
     */
    private final ThreadLocal<Boolean> deliveringResult =
        ThreadLocal.withInitial(() -> false);

    /**
     * The endpoint group to use to get an executor service.
     */
    private final EndpointGroup endpointGroup =
        AsyncRegistryUtils.getEndpointGroup();

    /**
     * A separate thread factory to use in case the executor service rejects a
     * request.
     */
    private final KVThreadFactory backupThreadFactory =
        new KVThreadFactory(" backup async response delivery", logger);

    /**
     * Creates RequestDispatcher for a KVStore client. As part of the creation
     * of the client side RequestDispatcher, it contacts one or more SNs from
     * the list of SNs identified by their <code>registryHostport</code>.
     *
     * @param config the KVStore configuration
     *
     * @param clientId the unique clientId associated with the KVS client
     *
     * @param loginMgr a login manager used to authenticate metadata access.
     *
     * @param exceptionHandler the handler to be associated with the state
     * update thread
     *
     * @param logger a Logger
     *
     * @throws IllegalArgumentException if the configuration specifies read
     * zones that are not found in the topology
     *
     * @throws KVStoreException if an RN could not be contacted to obtain the
     * Topology associated with the KVStore
     */
    public AsyncRequestDispatcherImpl(
        KVStoreConfig config,
        ClientId clientId,
        LoginManager loginMgr,
        UncaughtExceptionHandler exceptionHandler,
        Logger logger)
        throws KVStoreException {

        super(config, clientId, loginMgr, exceptionHandler, logger);
        networkRoundtripTimeoutMs =
            config.getNetworkRoundtripTimeout(MILLISECONDS);
    }

    /** Internal constructor used for testing KVStore clients. */
    AsyncRequestDispatcherImpl(String kvsName,
                               ClientId clientId,
                               Topology topology,
                               LoginManager regUtilsLoginMgr,
                               RequestLimitConfig requestLimitConfig,
                               UncaughtExceptionHandler exceptionHandler,
                               Logger logger,
                               String[] readZones) {
        super(kvsName, clientId, topology, regUtilsLoginMgr,
              requestLimitConfig, exceptionHandler, logger, readZones);
        this.networkRoundtripTimeoutMs =
            KVStoreConfig.DEFAULT_NETWORK_ROUNDTRIP_TIMEOUT;
    }

    @Override
    boolean isAsync() {
        return true;
    }

    /**
     * Executes a synchronous request by performing the request asynchronously
     * and waiting for the result.
     */
    @Override
    public Response execute(Request request,
                            RepNodeId targetId,
                            Set<RepNodeId> excludeRNs,
                            LoginManager loginMgr)
        throws FaultException {

        /* Grab the timeout value, since it can change during execution */
        final long timeoutMs = request.getTimeout();
        final AsyncExecuteRequest asyncRequest =
            new AsyncExecuteRequest(request, targetId, excludeRNs, loginMgr);
        return AsyncRegistryUtils.getWithTimeout(
            asyncRequest.execute(), "Request " + request,
            timeoutMs, getAsyncTimeout(timeoutMs));
    }

    /**
     * Returns the timeout in milliseconds that should be used for the async
     * dialog based on the request timeout.  The amount of time returned is
     * larger than the request timeout so that exceptions detected on the
     * server side can be propagated back to the client over the network.
     */
    long getAsyncTimeout(long requestTimeoutMs) {
        assert networkRoundtripTimeoutMs >= 0;
        long timeoutMs = requestTimeoutMs + networkRoundtripTimeoutMs;

        /* Correct for overflow */
        if ((requestTimeoutMs > 0) && (timeoutMs <= 0)) {
            timeoutMs = Long.MAX_VALUE;
        }
        return timeoutMs;
    }

    /**
     * Dispatches a request asynchronously to a suitable RN.
     *
     * <p> This implementation supports asynchronous operations.
     */
    @Override
    public CompletableFuture<Response> executeAsync(Request request,
                                                    Set<RepNodeId> excludeRNs,
                                                    LoginManager loginMgr) {
        return executeAsync(request, null, excludeRNs, loginMgr);
    }

    /**
     * Dispatches a request asynchronously, and also provides a parameter for
     * specifying a preferred target node, for testing.
     *
     * <p> This implementation supports asynchronous operations.
     */
    @Override
    public CompletableFuture<Response> executeAsync(Request request,
                                                    RepNodeId targetId,
                                                    Set<RepNodeId> excludeRNs,
                                                    LoginManager loginMgr) {
        try {
            return new AsyncExecuteRequest(request, targetId, excludeRNs,
                                           loginMgr)
                .execute();
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * The execution environment for a single asynchronous request.
     */
    private class AsyncExecuteRequest implements Runnable {

        private final Request request;
        private final RepNodeId targetId;
        private volatile Set<RepNodeId> excludeRNs;
        private final LoginManager loginMgr;
        private final CompletableFuture<Response> future =
            new CompletableFuture<>();

        private final RepGroupState rgState;
        private final int initialTimeoutMs;
        private final long limitNs;
        volatile int retryCount;

        private volatile Exception exception;
        volatile RepNodeState target;
        private volatile long retrySleepNs;
        private volatile LoginHandle loginHandle;

        private volatile long startNs;

        AsyncExecuteRequest(Request request,
                            RepNodeId targetId,
                            Set<RepNodeId> excludeRNs,
                            LoginManager loginMgr) {
            this.request = request;
            this.targetId = targetId;
            this.excludeRNs = excludeRNs;
            this.loginMgr = loginMgr;

            final RepGroupId repGroupId = startExecuteRequest(request);

            rgState = repGroupStateTable.getGroupState(repGroupId);

            initialTimeoutMs = request.getTimeout();
            limitNs = System.nanoTime() +
                MILLISECONDS.toNanos(initialTimeoutMs);
            retryCount = 0;

            exception = null;
            target = null;
            retrySleepNs = 10000000; /* 10 milliseconds */
            loginHandle = null;
        }

        CompletableFuture<Response> execute() {
            try {
                logger.finest(
                    () -> String.format(
                        "AsyncRequestDispatcherImpl.execute start" +
                        " request=%s" +
                        " targetId=%s" +
                        " stacktrace=%s",
                        request,
                        targetId,
                        CommonLoggerUtils.getStackTrace(new Throwable())));
                run();
            } catch (Throwable e) {
                onResult(null, e);
            }
            if (logger.isLoggable(Level.FINE)) {
                future.whenComplete(
                    unwrapExceptionVoid(
                        (response, e) ->
                        logger.log(
                            (e == null) ? Level.FINEST : Level.FINE,
                            () -> String.format(
                                "AsyncRequestDispatcherImpl.execute end" +
                                " request=%s" +
                                " targetId=%s" +
                                " response=%s" +
                                " exception=%s",
                                request,
                                targetId,
                                response,
                                ((e == null) ?
                                 "null" :
                                 logger.isLoggable(Level.FINEST) ?
                                 CommonLoggerUtils.getStackTrace(e) :
                                 e.toString())))));
            }
            return future;
        }

        /**
         * Make requests, finishing if it can obtain a result or failure
         * without blocking, and otherwise scheduling a new call when called
         * back after waiting for an intermediate result.
         */
        @Override
        public void run() {

            /* Retry until timeout or async handoff */
            while ((limitNs - System.nanoTime()) > 0) {
                try {
                    target = selectTarget(request, targetId, rgState,
                                          excludeRNs);
                } catch (RNUnavailableException e) {
                    onResult(null, e);
                    return;
                } catch (NoSuitableRNException e) {
                    /*
                     * NSRN exception is thrown once the dispatcher has tried
                     * all candidate RNs in the group. There are potentially
                     * exceptions that should be thrown immediately vs
                     * retrying. This method does that.
                     */
                    RuntimeException re = checkThrowNoSuitableRN(exception);
                    if (re != null) {
                        onResult(null, re);
                        return;
                    }

                    if (preferNewException(e, exception)) {
                        exception = e;
                    }
                    retrySleepNs = computeWaitBeforeRetry(limitNs,
                                                          retrySleepNs);
                    if (retrySleepNs > 0) {
                        /*
                         * TODO: Need a better way to use the endpoint group's
                         * executor service
                         */
                        while (true) {
                            try {
                                endpointGroup.getSchedExecService()
                                    .schedule(this, retrySleepNs, NANOSECONDS);
                                break;
                            } catch (RejectedExecutionException ree) {
                                if (endpointGroup.getIsShutdown()) {
                                    throw ree;
                                }
                                /* Try another executor */
                                continue;
                            }
                        }
                        return;
                    }
                    continue;
                }

                /* Have a target RN in hand */
                startNs = 0;
                try {
                    activeRequestCount.incrementAndGet();
                    final int targetRequestCount = target.requestStart();
                    startNs = latencyTracker.markStart();
                    checkStartDispatchRequest(target, targetRequestCount);
                } catch (Exception dispatchException) {
                    if (handleResponse(null, dispatchException)) {
                        return;
                    }
                }
                target.getReqHandlerRefAsync(
                    regUtils, NANOSECONDS.toMillis(limitNs - startNs))
                    .whenComplete(
                        unwrapExceptionVoid(this::handleRequestHandler));
                return;
            }
            onResult(null,
                     getTimeoutException(request, exception, initialTimeoutMs,
                                         retryCount, target));
        }

        /**
         * Deliver a result to the future, using a separate thread if needed to
         * avoid recursion.
         */
        void onResult(Response response, Throwable e) {
            if (deliveringResult.get()) {
                logger.finest("Detected recursion");
                doAsync(() -> onResult(response, e));
                return;
            }
            logger.log((e == null) ? Level.FINEST : Level.FINE,
                       () -> String.format(
                           "Done executing async" +
                           " request=%s" +
                           " targetId=%s" +
                           " response=%s" +
                           " e=%s",
                           request,
                           targetId,
                           response,
                           ((e == null) ?
                            "null" :
                            logger.isLoggable(Level.FINEST) ?
                            CommonLoggerUtils.getStackTrace(e) :
                            e.toString())));
            deliveringResult.set(true);
            try {
                complete(future, response, e);
                return;
            } finally {
                deliveringResult.set(false);
            }
        }

        private void doAsync(Runnable action) {

            /*
             * TODO: Need a better way to use the endpoint group's executor
             * service
             */
            while (true) {
                try {
                    endpointGroup.getSchedExecService().execute(action);
                    return;
                } catch (RejectedExecutionException ree) {
                    if (endpointGroup.getIsShutdown()) {
                        break;
                    }
                    /* Try another executor */
                    continue;
                }
            }

            /*
             * There must have been a race condition during store shutdown.
             * Create a new thread that just delivers the result, and don't
             * worry about the inefficiency of that approach because it should
             * happen only at shutdown.
             */
            backupThreadFactory.newThread(action);
        }

        /** Handle the result from attempt to obtain the request handler. */
        void handleRequestHandler(AsyncRequestHandlerAPI requestHandler,
                                  Throwable dispatchException) {
            if (requestHandler == null) {
                /*
                 * Save this exception unless the current one is more
                 * interesting, but don't set dispatchException, because we
                 * want to try again.
                 */
                final IllegalStateException newException =
                    new IllegalStateException(
                        "Could not establish handle to " +
                        target.getRepNodeId());
                if (preferNewException(newException, exception)) {
                    exception = newException;
                }
            } else {
                try {
                    loginHandle = prepareRequest(request, limitNs, retryCount,
                                                 target, loginMgr);
                    requestHandler.execute(request,
                                           getAsyncTimeout(
                                               request.getTimeout()))
                        .whenComplete(
                            unwrapExceptionVoid(this::handleResponseOrRun));
                    return;
                } catch (Exception e) {
                    dispatchException = e;
                }
            }
            handleResponseOrRun(null, dispatchException);
        }

        /** Handles a response, retrying asynchronously if needed */
        private void handleResponseOrRun(Response response, Throwable e) {
            try {
                if (!handleResponse(response, e)) {

                    /*
                     * Retry asynchronously to keep from recursing, which could
                     * exceed the stack depth
                     */
                    doAsync(this::run);
                }
            } catch (Throwable t) {
                onResult(null, t);
            }
        }

        /**
         * Performs operations needed when a dispatch attempt is completed,
         * including delivering the result or the exception if appropriate.
         * Returns true if the processing of the request is done, with the
         * result or exception delivered to the result handler, and returns
         * false if the request should be retried.
         */
        boolean handleResponse(Response response, Throwable e) {
            boolean done = false;
            if (e != null) {
                final Throwable throwException = dispatchFailed(e);
                if (throwException != null) {
                    e = throwException;
                    done = true;
                } else {
                    e = exception;
                }
            } else if (response != null) {
                done = true;
            }
            excludeRNs = dispatchCompleted(startNs, request, response, target,
                                           e, excludeRNs);
            if (done) {
                onResult(response, e);
            }
            return done;
        }

        /**
         * Handles an exception encountered during the dispatch of a request,
         * and returns a non-null exception that should be supplied to the
         * result handler if the request should not be retried.
         */
        private Throwable dispatchFailed(Throwable t) {
            if (!(t instanceof Exception)) {
                return t;
            }
            try {
                final Exception dispatchException =
                    (t instanceof DialogException) ?
                    handleDialogException(request, target,
                                          (DialogException) t) :
                    handleDispatchException(request, initialTimeoutMs, target,
                                            (Exception) t, loginHandle);
                if (preferNewException(dispatchException, exception)) {
                    exception = dispatchException;
                }
                return null;
            } catch (Throwable t2) {
                return t2;
            }
        }
    }

    @Override
    protected int rankException(Exception e) {
        final int superResult = super.rankException(e);
        final int localResult =

            /*
             * Next after the #1 ranked ConsistencyException from the super
             * class, prefer ServerResourceLimitException since it provides
             * more specific information than remaining exceptions
             */
            (e instanceof ServerResourceLimitException) ? 2 :

            /*
             * After that, prefer DialogBackoffException so that the caller
             * knows to back off
             */
            (e instanceof DialogBackoffException) ? 3 :
            10;

        /* Return the best (lowest) ranking of the two */
        return Math.min(superResult, localResult);
    }

    /**
     * Provide handling for dialog exceptions, checking for side effects on
     * writes, and unwrapping dialog and connection exceptions to obtain the
     * underlying exception.  Returns the exception if the request should be
     * retried, and throws an exception if it should not be retried.
     */
    Exception handleDialogException(Request request,
                                    RepNodeState target,
                                    DialogException dialogException) {
        if (dialogException instanceof DialogUnknownException) {
            throwAsFaultException("Internal error", dialogException);
        }

        /*
         * Fail if there could be side effects and this is a write operation,
         * because the side effects mean it isn't safe to retry automatically.
         */
        if (dialogException.hasSideEffect()) {
            faultIfWrite(request, "Communication problem", dialogException);
        }

        /*
         * Note the exception, which removes this RN from consideration for
         * subsequent dispatches until the state update thread re-established
         * the handle.
         */
        if (dialogException.shouldBackoff()) {
            target.noteReqHandlerException(dialogException);
        }
        return dialogException;
    }

    /**
     * Add handling for exceptions that need to be translated to a user
     * exception.
     */
    @Override
    void throwAsFaultException(String faultMessage, Exception exception)
        throws FaultException {

        if (exception instanceof GetUserException) {
            final Throwable t =
                ((GetUserException) exception).getUserException();
            if (t instanceof Error) {
                throw (Error) t;
            }
            exception = (t instanceof Exception) ?
                (Exception) t :
                new IllegalStateException("Unexpected exception: " + t, t);
        }
        super.throwAsFaultException(faultMessage, exception);
    }

    /**
     * Implement synchronous version using an asynchronous request.
     */
    @Override
    public Response executeNOP(RepNodeState rns,
                               int timeoutMs,
                               LoginManager loginMgr) {
        return AsyncRegistryUtils.getWithTimeout(
            executeNOPAsync(rns, timeoutMs, loginMgr), "NOP request",
            timeoutMs);
    }

    /**
     * Asynchronous version to dispatch the special NOP request.  Keep this
     * method up-to-date with the sync version in RequestDispatcherImpl.
     */
    public CompletableFuture<Response> executeNOPAsync(RepNodeState rns,
                                                       int timeoutMs,
                                                       LoginManager loginMgr) {
        try {
            return rns.getReqHandlerRefAsync(getRegUtils(), timeoutMs)
                .thenCompose(
                    api -> executeNOPAsync(api, rns, timeoutMs, loginMgr));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private CompletableFuture<Response>
        executeNOPAsync(AsyncRequestHandlerAPI ref,
                        RepNodeState rns,
                        int timeoutMs,
                        LoginManager loginMgr) {
        if (ref == null) {
            /* needs to be resolved. */
            return completedFuture(null);
        }

        long startTimeNs = System.nanoTime();
        Request nop = null;
        CompletableFuture<Response> future = null;
        try {
            rns.requestStart();
            activeRequestCount.incrementAndGet();
            startTimeNs = latencyTracker.markStart();
            final int topoSeqNumber =
                getTopologyManager().getTopology().getSequenceNumber();
            nop = Request.createNOP(topoSeqNumber,
                                    getDispatcherId(),
                                    timeoutMs);
            nop.setSerialVersion(rns.getRequestHandlerSerialVersion());
            if (loginMgr != null) {
                nop.setAuthContext(
                    new AuthContext(
                        loginMgr.getHandle(
                            rns.getRepNodeId()).getLoginToken()));
            }
            future = ref.execute(nop, getAsyncTimeout(nop.getTimeout()));
        } catch (Throwable e) {
            future = failedFuture(e);
        }
        final long startTimeNsFinal = startTimeNs;
        final Request nopFinal = nop;
        return whenComplete(
            future.thenCompose(
                response -> {
                    if (response != null) {
                        processResponse(startTimeNsFinal, nopFinal, response);
                        return completedFuture(response);
                    }
                    return completedFuture(null);
                }),
            (response, exception) -> {
                /*
                 * If it is a communications problem (a DialogException) that
                 * asks that the caller back off, then note the exception so we
                 * back off on this node until the state update thread tries
                 * again.
                 */
                if (exception instanceof DialogException) {
                    final DialogException de = (DialogException) exception;
                    if (de.shouldBackoff()) {
                        rns.noteReqHandlerException(de);
                    }
                }

                rns.requestEnd();
                activeRequestCount.decrementAndGet();
                latencyTracker.markFinish(OpCode.NOP, startTimeNsFinal);
            });
    }

    /**
     * This implementation returns the default local dialog layer limit.
     */
    @Override
    protected int getMaxActiveRequests() {
        return EndpointConfigBuilder.getOptionDefault(
            AsyncOption.DLG_LOCAL_MAXDLGS);
    }

    @Override
    public long getNetworkRoundtripTimeoutMs() {
        return networkRoundtripTimeoutMs;
    }
}
