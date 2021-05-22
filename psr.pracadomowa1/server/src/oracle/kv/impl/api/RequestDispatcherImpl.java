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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.lang.Thread.UncaughtExceptionHandler;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.UnknownHostException;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import oracle.kv.AuthenticationRequiredException;
import oracle.kv.ConsistencyException;
import oracle.kv.DurabilityException;
import oracle.kv.FaultException;
import oracle.kv.KVSecurityException;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreException;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.RequestLimitConfig;
import oracle.kv.RequestLimitException;
import oracle.kv.RequestTimeoutException;
import oracle.kv.ServerResourceLimitException;
import oracle.kv.impl.admin.param.RepNodeParams;
import oracle.kv.impl.api.ops.InternalOperation;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.api.rgstate.RepGroupState;
import oracle.kv.impl.api.rgstate.RepGroupStateTable;
import oracle.kv.impl.api.rgstate.RepNodeState;
import oracle.kv.impl.api.rgstate.RepNodeStateUpdateThread;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.async.exception.GetUserException;
import oracle.kv.impl.fault.OperationFaultException;
import oracle.kv.impl.fault.RNUnavailableException;
import oracle.kv.impl.fault.TTLFaultException;
import oracle.kv.impl.fault.WrappedClientException;
import oracle.kv.impl.measurement.LatencyResult;
import oracle.kv.impl.metadata.Metadata.MetadataType;
import oracle.kv.impl.metadata.MetadataInfo;
import oracle.kv.impl.metadata.MetadataKey;
import oracle.kv.impl.param.ParameterUtils;
import oracle.kv.impl.rep.admin.RepNodeAdminAPI;
import oracle.kv.impl.rep.admin.RepNodeAdminFaultException;
import oracle.kv.impl.security.AuthContext;
import oracle.kv.impl.security.ExecutionContext;
import oracle.kv.impl.security.SessionAccessException;
import oracle.kv.impl.security.login.LoginHandle;
import oracle.kv.impl.security.login.LoginManager;
import oracle.kv.impl.security.login.LoginToken;
import oracle.kv.impl.test.ExceptionTestHook;
import oracle.kv.impl.test.ExceptionTestHookExecute;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.topo.Datacenter;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroup;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.RepNode;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.ResourceId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.topo.TopologyUtil;
import oracle.kv.impl.util.LatencyTracker;
import oracle.kv.impl.util.ObjectUtil;
import oracle.kv.impl.util.TopologyLocator;
import oracle.kv.impl.util.WaitableCounter;
import oracle.kv.impl.util.registry.RegistryUtils;
import oracle.kv.table.Table;
import oracle.nosql.common.sklogger.measure.ThroughputElement;


/**
 * An implementation of RequestDispatcher that does not support asynchronous
 * operations. The implementation does not have any dependences on RepNode or
 * the ReqestHandler, since it's also instantiated in a KV client.
 * <p>
 * The implementation has two primary components that provide the state used
 * to dispatch a request:
 * <ol>
 * <li>The Topology, which is accessed via the TopologyManager. It's updated
 * primarily via the PlannerAdmin, but could also be updated via information
 * obtained in a Response.</li>
 * <li>The  RepGroupStateTable which tracks the dynamic state of RNs within
 * the KVS. It's updated primarily via information contained in a Response,
 * as well as an internal thread that attempts to keep its information
 * current.
 * </li>
 * </ol>
 * The RequestDispatcher is the home for these components and reads the state
 * they represent. There is exactly one instance of each of these shared
 * components in a client or a RN.
 *
 * TODO: Sticky dispatch based upon partitions.
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    /**
     * The unique dispatcher id sent in dispatched requests.
     */
    final ResourceId dispatcherId;

    /**
     * Indicates whether this dispatcher is used on a remote RN for
     * forwarding, or directly on the client.
     */
    final boolean isRemote;

    /**
     * Determines if and how to limit the max requests to a node so that all
     * thread resources aren't consumed by it.
     */
    final RequestLimitConfig requestLimitConfig;

    /**
     * The topology used as the basis for requests.
     */
    final TopologyManager topoManager;

    /**
     *  Tracks the state of rep nodes.
     */
    final RepGroupStateTable repGroupStateTable;

    /**
     * The thread is only started for client nodes where it maintains the
     * RepNodeState associated with every node in the state table.
     */
    private final RepNodeStateUpdateThread stateUpdateThread;

    /**
     * The internal login manager used to authenticate client proxy calls.
     * Only non-null when in a rep-node context.
     */
    private final LoginManager internalLoginMgr;

    /**
     * The login manager that is held by regUtils and use do perform topology
     * access operations, NOP executions, and requestHandler resolves.
     */
    private volatile LoginManager regUtilsLoginMgr = null;

    /**
     * Used to create remote handles. It's maintained by RegUtilsMaintListener
     * and can change as a result of topology changes.
     */
    volatile RegistryUtils regUtils = null;

    /**
     * The number of request actively being processed by this request
     * dispatcher. It represents the sum of active requests at every RN.
     */
    final WaitableCounter activeRequestCount = new WaitableCounter();

    /**
     * The total number of requests that were retried.
     */
    final ThroughputElement totalRetryCount = new ThroughputElement();

    final LatencyTracker<InternalOperation.OpCode> latencyTracker;

    /**
     * Shutdown can only be executed once. The shutdown field protects against
     * multiple invocations.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * The exception that provoked the shutdown, if any.
     */
    private Throwable shutdownException = null;

    /* The default timeout associated with requests. */
    private final int requestQuiesceMs;

    final Logger logger;

    /**
     * The names of the zones that can be used for read requests, or null if
     * not restricted.  Set to a non-null value for client dispatchers from the
     * associated KVStoreConfig.
     */
    private final Set<String> readZones;

    /**
     * The IDs of the zones that can be used for read requests, or null if not
     * restricted.  Set based on readZones and the current topology.
     */
    private volatile int[] readZoneIds = null;

    /**
     * The maximum number of RNs to query for a topology. The larger the
     * number the more current the topology, but the longer the KVS startup
     * delay.
     */
    private static final int MAX_LOCATOR_RNS = 10;

    /**
     * The time period between update passes by the state update thread
     */
    private static final int STATE_UPDATE_THREAD_PERIOD_MS = 1000;

    /**
     * The maximum time to sleep between retries of a request. Wait times
     * are increased between successive retries until this limit is reached.
     */
    static final int RETRY_SLEEP_MAX_NS = 128000000;

    /**
     * The maximum number of topo changes to be retained at the client.
     */
    private static final int MAX_TOPO_CHANGES_ON_CLIENT = 1000;

    /**
     * Default Request quiesce ms.
     */
    private static final int REQUEST_QUIESCE_MS_DEFAULT = 10000;

    /* The period used to poll for requests becoming quiescent at shutdown. */
    private static final int REQUEST_QUIESCE_POLL_MS = 1000;

    /**
     * Special test hook -- used when testing request dispatching -- that
     * supports the introduction of a NoSuitableRNException; which is needed
     * to verify that the execute method of this class correctly handles a
     * null excludeRNs parameter. For testing only.
     */
    private TestHook<Request> requestExecuteHook;

    /**
     * Test hook used to provoke checked exceptions immediately before
     * a request dispatch. It's primarily used for injecting RMI exceptions.
     */
    volatile ExceptionTestHook<Request,Exception> preExecuteHook;

    /**
     * Creates requestDispatcher for a KVStore RepNode.
     *
     * @param kvsName the name of the KVStore associated with the RN
     *
     * @param repNodeParams the RN params used to configures this RN
     *
     * @param internalLoginMgr the RN's internal login manager.  This is also
     * used as the Metadata login manager.
     *
     * @param exceptionHandler the handler to be associated with the state
     * update thread
     *
     * @param logger an optional Logger
     */
    private RequestDispatcherImpl(String kvsName,
                                  RepNodeParams repNodeParams,
                                  LoginManager internalLoginMgr,
                                  UncaughtExceptionHandler exceptionHandler,
                                  Logger logger) {
        assert (kvsName != null);
        checkNull("exceptionHandler", exceptionHandler);
        this.logger = logger;
        this.internalLoginMgr = internalLoginMgr;
        this.regUtilsLoginMgr = internalLoginMgr;
        final RequestLimitConfig defaultRequestLimitConfig =
            ParameterUtils.getRequestLimitConfig(repNodeParams.getMap());
        requestLimitConfig =
            getRepNodeRequestLimitConfig(defaultRequestLimitConfig);
        topoManager = new TopologyManager(kvsName,
                                           repNodeParams.getMaxTopoChanges(),
                                           logger);
        final ResourceId repNodeId = repNodeParams.getRepNodeId();
        repGroupStateTable = new RepGroupStateTable(repNodeId, isAsync(),
                                                    logger);
        initTopoManager();
        dispatcherId = repNodeId;
        isRemote = true;
        stateUpdateThread =
            new RepNodeStateUpdateThread(this, repNodeId,
                                         STATE_UPDATE_THREAD_PERIOD_MS,
                                         exceptionHandler, logger);

        /* The parameters used below effectively turn off thread dumps. */
        this.latencyTracker =
            new LatencyTracker<OpCode>(
                InternalOperation.OpCode.values(), logger,
                Integer.MAX_VALUE, Long.MAX_VALUE, 0);
        requestQuiesceMs = repNodeParams.getRequestQuiesceMs();
        readZones = null;
    }

    /**
     * Creates a RequestDispatcher for use by an RN and starts up its state
     * update thread after it has been completely initialized, to avoid
     * premature access to its state before the constructor has exited.
     *
     * @param kvsName the name of the KVStore associated with the RN
     *
     * @param repNodeParams the RN params used to configures this RN
     *
     * @param internalLoginMgr the RN's internal login manager. This is also
     * used as the Metadata login manager.
     *
     * @param exceptionHandler the handler to be associated with the state
     * update thread
     *
     * @param logger an optional Logger
     */
    public static RequestDispatcherImpl
    createForRN(String kvsName,
                RepNodeParams repNodeParams,
                LoginManager internalLoginMgr,
                UncaughtExceptionHandler exceptionHandler,
                Logger logger) {

        final RequestDispatcherImpl requestDispatcherImpl =
            new RequestDispatcherImpl(kvsName,
                                      repNodeParams,
                                      internalLoginMgr,
                                      exceptionHandler,
                                      logger);

        requestDispatcherImpl.stateUpdateThread.start();

        return requestDispatcherImpl;
    }

    /**
     * Creates RequestDispatcher for a KVStore client. As part of the creation
     * of the client side RequestDispatcher, it contacts one or more SNs from
     * the list of SNs identified by their <code>registryHostport</code>.  This
     * method creates an async dispatcher based on the value of {@link
     * KVStoreConfig#getUseAsync}.
     *
     * @param config the KVStore configuration
     *
     * @param clientId the unique clientId associated with the KVS client
     *
     * @param loginMgr a login manager used to authenticate metadata access.
     * @throws IllegalArgumentException if the configuration specifies read
     * zones that are not found in the topology
     *
     * @throws KVStoreException if an RN could not be contacted to obtain the
     * Topology associated with the KVStore
     */
    public static RequestDispatcherImpl createForClient(
        KVStoreConfig config,
        ClientId clientId,
        LoginManager loginMgr,
        UncaughtExceptionHandler exceptionHandler,
        Logger logger) throws KVStoreException {

        final RequestDispatcherImpl requestDispatcher =
            config.getUseAsync() ?
            new AsyncRequestDispatcherImpl(config, clientId, loginMgr,
                                           exceptionHandler, logger) :
            new RequestDispatcherImpl(config, clientId, loginMgr,
                                      exceptionHandler, logger);
        requestDispatcher.stateUpdateThread.start();

        return requestDispatcher;
    }

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
     * @throws IllegalArgumentException if the configuration specifies read
     * zones that are not found in the topology
     *
     * @throws KVStoreException if an RN could not be contacted to obtain the
     * Topology associated with the KVStore
     */
    protected RequestDispatcherImpl(KVStoreConfig config,
                                    ClientId clientId,
                                    LoginManager loginMgr,
                                    UncaughtExceptionHandler exceptionHandler,
                                    Logger logger)
        throws KVStoreException {

        this(config.getStoreName(), clientId,
             TopologyLocator.get(
                 config.getHelperHosts(), MAX_LOCATOR_RNS, loginMgr,
                 config.getStoreName(), clientId),
             loginMgr,
             config.getRequestLimit(),
             exceptionHandler,
             logger,
             config.getReadZones(),
             (int) config.getRequestTimeout(TimeUnit.MILLISECONDS));
    }

    /**
     * Internal creation method used for a KVStore client and unit testing a
     * RequestDispatcher directly in the absence of a RepNodeService.
     *
     * Note that it does not start the state update thread.
     */
    static RequestDispatcherImpl createForClient(
        boolean useAsync,
        String kvsName,
        ClientId clientId,
        Topology topology,
        LoginManager regUtilsLoginMgr,
        RequestLimitConfig requestLimitConfig,
        UncaughtExceptionHandler exceptionHandler,
        Logger logger,
        String[] readZones) {

        return useAsync ?
            new AsyncRequestDispatcherImpl(
                kvsName, clientId, topology, regUtilsLoginMgr,
                requestLimitConfig, exceptionHandler, logger, readZones) :
            new RequestDispatcherImpl(
                kvsName, clientId, topology, regUtilsLoginMgr,
                requestLimitConfig, exceptionHandler, logger, readZones);
    }

    /**
     * Internal constructor used for a KVStore client (from above creation
     * method) and unit testing a RequestDispatcher directly in the absence of
     * a RepNodeService.
     *
     * Note that it does not start the state update thread.
     */
    RequestDispatcherImpl(String kvsName,
                          ClientId clientId,
                          Topology topology,
                          LoginManager regUtilsLoginMgr,
                          RequestLimitConfig requestLimitConfig,
                          UncaughtExceptionHandler exceptionHandler,
                          Logger logger,
                          String[] readZones) {
        this(kvsName, clientId, topology, regUtilsLoginMgr,
             requestLimitConfig, exceptionHandler, logger, readZones,
             REQUEST_QUIESCE_MS_DEFAULT);
    }

    private RequestDispatcherImpl(String kvsName,
                                  ClientId clientId,
                                  Topology topology,
                                  LoginManager regUtilsLoginMgr,
                                  RequestLimitConfig requestLimitConfig,
                                  UncaughtExceptionHandler exceptionHandler,
                                  Logger logger,
                                  String[] readZones,
                                  int requestQuiesceMs) {
        assert (kvsName != null);
        if (!topology.getKVStoreName().equals(kvsName)) {
            throw new IllegalArgumentException
                ("Specified store name, " + kvsName +
                 ", does not match store name at specified host/port, " +
                 topology.getKVStoreName());
        }

        this.logger = logger;
        this.internalLoginMgr = null;
        this.regUtilsLoginMgr = regUtilsLoginMgr;
        /* The parameters used below effectively turn off thread dumps. */
        latencyTracker = new LatencyTracker<OpCode>
            (InternalOperation.OpCode.values(), logger,
             Integer.MAX_VALUE, Long.MAX_VALUE, 0);
        this.requestLimitConfig = requestLimitConfig;

        this.requestQuiesceMs = requestQuiesceMs;

        if (readZones == null) {
            this.readZones = null;
        } else {
            final Set<String> allZones = new HashSet<String>();
            for (final Datacenter zone :
                     topology.getDatacenterMap().getAll()) {
                allZones.add(zone.getName());
            }
            final Set<String> unknownZones = new HashSet<String>();
            Collections.addAll(unknownZones, readZones);
            unknownZones.removeAll(allZones);
            if (!unknownZones.isEmpty()) {
                throw new IllegalArgumentException(
                    "Read zones not found: " + unknownZones);
            }
            this.readZones = new HashSet<String>();
            Collections.addAll(this.readZones, readZones);
            logger.log(Level.FINE, "Set read zones: {0}", this.readZones);
        }

	topoManager = new TopologyManager(kvsName,
					  MAX_TOPO_CHANGES_ON_CLIENT,
					  logger);
        repGroupStateTable = new RepGroupStateTable(clientId, isAsync(),
                                                    logger);
        initTopoManager();
        dispatcherId = clientId;
        isRemote = false;
        stateUpdateThread =
            new RepNodeStateUpdateThread(this, clientId,
                                         STATE_UPDATE_THREAD_PERIOD_MS,
                                         exceptionHandler, logger);
        topoManager.update(topology);
    }

    /**
     * Returns the request limit config used by the request dispatcher in an
     * RN. The maximum number of active requests is based upon the the max rmi
     * connections configured for the node. The percentages are fixed since
     * there is no reason to vary them. The request dispatcher in an RN should
     * typically forward requests only within its rep group or for a migrated
     * partition.
     *
     * @param defaultConfig
     */
    private RequestLimitConfig
        getRepNodeRequestLimitConfig(RequestLimitConfig defaultConfig) {

        int maxActiveRequests =
            Math.min(defaultConfig.getNodeLimit(), getMaxActiveRequests());
        return new RequestLimitConfig
            (maxActiveRequests, defaultConfig.getRequestThresholdPercent(),
             defaultConfig.getNodeLimitPercent());
    }

    /**
     * Returns the maximum number of active requests permitted when
     * communicating with a single node.  This implementation limits the value
     * based on the RMI server limit.
     */
    int getMaxActiveRequests() {
        final String maxConnectionsProperty =
            System.getProperty("sun.rmi.transport.tcp.maxConnectionThreads");
        if (maxConnectionsProperty != null) {
            try {
                return Integer.parseInt(maxConnectionsProperty);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException
                    ("RMI max connection threads: " + maxConnectionsProperty);
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public void shutdown(Throwable exception) {

        if (!shutdown.compareAndSet(false, true)) {
            /* Already shutdown. */
            return;
        }

        shutdownException = exception;
        if (stateUpdateThread.isAlive()) {
            stateUpdateThread.shutdown();
            /* No need to join the thread, it will shutdown asynchronously */
        }

        /*
         * Wait for any dispatched requests to quiesce within the
         * requestQuiesceMs period.
         */
        final boolean quiesced =
                        activeRequestCount.awaitZero(REQUEST_QUIESCE_POLL_MS,
                                                     requestQuiesceMs);

        if (!quiesced) {
            logger.info(activeRequestCount.get() +
                        " dispatched requests were in progress on close.");
        }

        logger.log((exception != null) ? Level.WARNING : Level.INFO,
                    "Dispatcher shutdown", exception);

        /* Nothing to do since RMI handles are automatically GC'd. */
    }

    /**
     * Determines whether the request dispatcher has been shutdown.
     */
    void checkShutdown() {

        if (shutdown.get()) {
            final String message = "Request dispatcher has been shutdown.";
            throw new IllegalStateException(message, shutdownException);
        }
    }

    /**
     * Returns whether this implementation supports asynchronous operations.
     */
    boolean isAsync() {
        return false;
    }

    /**
     * For testing only.
     */
    public RepNodeStateUpdateThread getStateUpdateThread() {
        return stateUpdateThread;
    }

    private void initTopoManager() {
        topoManager.addPostUpdateListener(repGroupStateTable);
        topoManager.addPostUpdateListener(new RegUtilsMaintListener());
        if (readZones != null) {
            topoManager.addPostUpdateListener(new UpdateReadZoneIds());
        }
    }

    /**
     * Executes the request at a suitable RN. The dispatch is a two part
     * process where a RG is first selected based upon the key and then a
     * particular RN from within the group is selected based upon the
     * characteristics of the request. Read requests that do not require
     * absolute consistency are load balanced across the the RG.
     * <p>
     * This method deals with all exceptions arising from the execution of a
     * remote request. Whenever possible, the request is retried with a
     * different RN until the request times out.
     * <p>
     * Each request will "fail fast" rather than retry with the same RN. This
     * is to ensure that a limited and fixed number network connections at a
     * client do not all get consumed by a single problem, thus stalling
     * operations across the entire KVS.
     *
     * @param request the request to be executed
     *
     * @param targetId the preferred target, or null in the absence of a
     * preference.
     *
     * @param excludeRNs the set of RNs to be excluded from the selection of an
     * RN at which to execute the request. If the empty set or
     * <code>null</code> is input for this parameter, then no RNs are to be
     * excluded.
     *
     * @throws FaultException
     */
    public Response execute(Request request,
                            RepNodeId targetId,
                            Set<RepNodeId> excludeRNs,
                            LoginManager loginMgr)
        throws FaultException {

        final RepGroupId repGroupId = startExecuteRequest(request);

        final RepGroupState rgState =
            repGroupStateTable.getGroupState(repGroupId);

        final int initialTimeoutMs = request.getTimeout();
        final long limitNs = System.nanoTime() +
            MILLISECONDS.toNanos(initialTimeoutMs);
        int retryCount = 0;

        Exception exception = null;
        RepNodeState target = null;
        long retrySleepNs = 10000000; /* 10 milliseconds */
        LoginHandle loginHandle = null;

        do { /* Retry until timeout. */
            try {
                target = selectTarget(request, targetId, rgState, excludeRNs);
            } catch (RNUnavailableException e) {
                throw e;
            } catch (NoSuitableRNException nsre) {
                /*
                 * NSRN exception is thrown once the dispatcher has tried all
                 * candidate RNs in the group. There are potentially exceptions
                 * that should be thrown immediately vs retrying. This method
                 * does that.
                 */
                RuntimeException re = checkThrowNoSuitableRN(exception);
                if (re != null) {
                    throw re;
                }

                if (preferNewException(nsre, exception)) {
                    exception = nsre;
                }
                retrySleepNs = computeWaitBeforeRetry(limitNs, retrySleepNs);
                if (retrySleepNs > 0) {
                    try {
                        Thread.sleep(NANOSECONDS.toMillis(retrySleepNs));
                    } catch (InterruptedException ie) {
                        throw new OperationFaultException(
                            "Unexpected interrupt", ie);
                    }
                }
                continue;
            }

            /* Have a target RN in hand */
            long startNs = 0;
            Response response = null;
            try {
                activeRequestCount.incrementAndGet();
                final int targetRequestCount = target.requestStart();
                startNs = latencyTracker.markStart();
                checkStartDispatchRequest(target, targetRequestCount);
                final RequestHandlerAPI requestHandler =
                    target.getReqHandlerRef(
                        regUtils, NANOSECONDS.toMillis(limitNs - startNs));
                if (requestHandler == null) {
                    final IllegalStateException newException =
                        new IllegalStateException(
                            "Could not establish handle to " +
                            target.getRepNodeId());
                    if (preferNewException(newException, exception)) {
                        exception = newException;
                    }
                    continue;
                }

                loginHandle = prepareRequest(request, limitNs, retryCount++,
                                             target, loginMgr);
                response = requestHandler.execute(request);
                exception = null;
                return response;
            } catch (Exception dispatchException) {
                final Exception newException =
                    handleDispatchException(request,
                                            initialTimeoutMs,
                                            target,
                                            dispatchException,
                                            loginHandle);
                if (preferNewException(newException, exception)) {
                    exception = newException;
                }
                continue;
            } finally {
                excludeRNs = dispatchCompleted(startNs, request, response,
                                               target, exception, excludeRNs);
            }

            /* Retry with corrective action until timeout. */
        } while ((limitNs - System.nanoTime()) > 0);

        /* Timed out */

       throw getTimeoutException(request, exception, initialTimeoutMs,
                                 retryCount, target);
    }

    /**
     * Check if the new exception should be retained in favor of the existing
     * one. The retained exception is the one that will be thrown to the user
     * if no RN is able to handle the request.
     */
    protected boolean preferNewException(Exception newException,
                                         Exception existingException) {
        final int newRank = rankException(newException);
        final int existingRank = rankException(existingException);

        /* Choose the exception with the lower (better) rank */
        if (newRank < existingRank) {
            return true;
        }
        if (existingRank < newRank) {
            return false;
        }

        /*
         * If the ranks are the same, take the new exception for the lowest
         * rank, assuming that these problems are probably ephemeral, and the
         * existing one for higher ranked ones, which are more likely
         * persistent, in which case an earlier one may be more interesting.
         */
        return newRank == 10;
    }

    /**
     * Return a ranking for an exception with 1 as most preferred and 10 least
     * preferred.
     */
    protected int rankException(Exception e) {

        /*
         * Prefer ConsistencyException over all others because it provides more
         * specific information to the user.
         */
        if (e instanceof ConsistencyException) {
            return 1;
        }

        if (e instanceof WrappedClientException &&
            e.getCause() instanceof MetadataNotFoundException) {
            return 4;
        }

        /*
         * SessionAccessException occurs if the server can't read a
         * login session token. Leave space for Async exceptions.
         */
        if (e instanceof SessionAccessException) {
            return 5;
        }

        return 10;
    }

    /*
     * Set up to start executing a request, returning the the group ID.  Throws
     * RNUnavailableException if the group ID is not found because the RN is
     * not initialized.
     */
    RepGroupId startExecuteRequest(Request request) {
        checkShutdown();

        checkTTL(request);

        /*
         * Select the group associated with the request. If the group ID is not
         * null, use that, otherwise select the group based on the partition.
         */
        final RepGroupId repGroupId = request.getRepGroupId().isNull() ?
            topoManager.getLocalTopology().getRepGroupId(
                request.getPartitionId()) :
            request.getRepGroupId();
        if (repGroupId == null) {
            throw new RNUnavailableException(
                "RepNode not yet initialized");
        }
        request.updateForwardingRNs(dispatcherId, repGroupId.getGroupId());
        return repGroupId;
    }

    /**
     * Attempt to select the target RN, returning state for selected the RN if
     * the target RN was obtained.
     *
     * @throws NoSuitableRNException if no RN was found, but the caller should
     * retry
     * @throws RNUnavailableException if no RN is available and the call should
     * fail
     */
    RepNodeState selectTarget(Request request,
                              RepNodeId targetId,
                              RepGroupState rgState,
                              Set<RepNodeId> excludeRNs)
        throws NoSuitableRNException {

        try {
            return (targetId != null) ?
                repGroupStateTable.getNodeState(targetId) :
                selectDispatchRN(rgState, request, excludeRNs);
        } catch (NoSuitableRNException nsre) {
            if (!request.isInitiatingDispatcher(dispatcherId) ||
                topoManager.inTransit(request.getPartitionId())) {
                throw new RNUnavailableException(nsre.getMessage());
            }
            if (excludeRNs != null) {
                excludeRNs.clear(); /* make a fresh start for retry */
            }
            throw nsre;
        }
    }

    /**
     * Checks the active request limit and that the RN is initialized.
     */
    void checkStartDispatchRequest(RepNodeState target,
                                   int targetRequestCount) {

        /*
         * TODO: It would be nice if we could use the request limit config to
         * specify a value for the DLG_LOCAL_MAXLEN option for the endpoint
         * config.  If we did decide to do that, then note that we would need
         * to handle the case where the request limit is higher than the
         * server-side DLG_LOCAL_MAXLEN value, presumably by lowering the
         * effective limit on this side.
         */

        if ((activeRequestCount.get() >
             requestLimitConfig.getRequestThreshold()) &&
            (targetRequestCount >
             requestLimitConfig.getNodeLimit())) {
            throw RequestLimitException.create(
                requestLimitConfig,
                target.getRepNodeId(),
                activeRequestCount.get(),
                targetRequestCount,
                isRemote);
        }

        if (regUtils == null) {
            throw new RNUnavailableException("RepNode not yet " +
                                             "initialized");
        }
    }

    /**
     * Update the request and other fields in preparation for performing the
     * request now that the request handler is available.  Returns the login
     * handle that should be used to perform authentication if needed, or null
     * in the non-secure case.
     */
    LoginHandle prepareRequest(Request request,
                               long limitNs,
                               int retryCount,
                               RepNodeState target,
                               LoginManager loginMgr)
        throws Exception {

        request.setTimeout(
            (int) NANOSECONDS.toMillis(limitNs - System.nanoTime()));
        if (retryCount > 0) {
            totalRetryCount.observe(1);
        }

        LoginHandle loginHandle = null;
        if (loginMgr != null) {
            loginHandle = loginMgr.getHandle(target.getRepNodeId());
            request.setAuthContext(
                new AuthContext(loginHandle.getLoginToken()));
        } else if (isRemote) {
            if (request.getAuthContext() != null) {
                updateAuthContext(request, target);
            }
        }

        request.setSerialVersion
            (target.getRequestHandlerSerialVersion());
        assert ExceptionTestHookExecute.
            doHookIfSet(preExecuteHook, request);

        return loginHandle;
    }

    /**
     * Record information about a completed dispatch attempt.  Returns the
     * updated set of excluded RNs for use on the next retry, if any.
     */
    Set<RepNodeId> dispatchCompleted(long startNs,
                                     Request request,
                                     Response response,
                                     RepNodeState target,
                                     Throwable exception,
                                     Set<RepNodeId> excludeRNs) {
        if (response != null) {
            processResponse(startNs, request, response);
            logger.log(Level.FINEST,
                       () -> {
                           final RepNodeId rnId = response.getRespondingRN();
                           final RepNodeState rns =
                               repGroupStateTable.getNodeState(rnId);
                           return "Response from " + rns.printString();
                       });
        }

        target.requestEnd();

        final int nRecords = (response != null) ?
            response.getResult().getNumRecords() : 1;
        latencyTracker.markFinish(request.getOperation().getOpCode(),
                                  startNs, nRecords);
        activeRequestCount.decrementAndGet();

        if (exception != null) {
            /* An anticipated exception during the request. */
            logger.fine(exception.getMessage());
            target.incErrorCount();
        }

        /* Exclude the node from consideration during a retry. */
        if ((response == null) || (exception != null)) {
            /*
             * Eliminate generation of an unused hash table on a successful
             * response where the request will not be retried, that is, in
             * 99.999% of all cases.
             */
            excludeRNs = excludeRN(excludeRNs, target);
        }
        return excludeRNs;
    }

    /**
     * Returns the FaultException to throw for a request that has timed out.
     */
    RuntimeException getTimeoutException(Request request,
                                         Exception exception,
                                         int initialTimeoutMs,
                                         int retryCount,
                                         RepNodeState target) {
        if (exception instanceof ConsistencyException) {
            /* Throw consistency exception, using the initial consistency */
            final ConsistencyException ce = (ConsistencyException) exception;
            ce.setConsistency(request.getConsistency());
            return ce;
        }
        if (exception instanceof ServerResourceLimitException) {
            return (ServerResourceLimitException) exception;
        }
        if (exception instanceof GetUserException) {
            final Throwable userException =
                ((GetUserException) exception).getUserException();
            if (userException instanceof Error) {
                throw (Error) userException;
            }
            exception = (Exception) userException;
        }
        if (exception instanceof WrappedClientException &&
            exception.getCause() instanceof MetadataNotFoundException)
        {
            return (MetadataNotFoundException)exception.getCause();
        }
        final String retryText = (retryCount == 1) ? " try." : " retries.";
        return new RequestTimeoutException(
            initialTimeoutMs,
            "Request dispatcher: " + dispatcherId +
            ", dispatch timed out after " + retryCount + retryText +
            " Target: " +
            ((target == null) ? "not available" : target.getRepNodeId()),
            exception, isRemote);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation throws {@link UnsupportedOperationException},
     * since it only supports synchronous operations.
     */
    @Override
    public CompletableFuture<Response> executeAsync(Request request,
                                                    Set<RepNodeId> excludeRNs,
                                                    LoginManager loginMgr) {
        return failedFuture(
            new UnsupportedOperationException(
                "Asynchronous operations are not supported"));
    }

    /**
     * Dispatches a request asynchronously, and also provides a parameter for
     * specifying a preferred target node, for testing.
     *
     * <p> This implementation throws {@link UnsupportedOperationException},
     * since it only supports synchronous operations.
     *
     * @param request the request to execute
     * @param targetId the preferred target or null for no preference
     * @param excludeRNs the RNs to exclude from consideration for dispatch, or
     * null for no exclusions
     * @return the future result
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Response> executeAsync(Request request,
                                                    RepNodeId targetId,
                                                    Set<RepNodeId> excludeRNs,
                                                    LoginManager loginMgr) {
        return failedFuture(
            new UnsupportedOperationException(
                "Asynchronous operations are not supported"));
    }

    /**
     * @hidden
     *
     * Gets the state update interval in ms
     *
     * @return the state update interval in ms
     */
    public int getStateUpdateIntervalMs() {
        return STATE_UPDATE_THREAD_PERIOD_MS;
    }

    /**
     * For a request that is being forwarded by an RN, update the
     * AuthContext on the request to reflect the original client host,
     * if needed.  If there is already a client host then we leave it as is.
     *
     * @param request a Request object that is being forwarded to another RN
     * @param target the state object representing the new target RN
     */
    void updateAuthContext(Request request, RepNodeState target) {

        final AuthContext currCtx = request.getAuthContext();
        if (currCtx != null && currCtx.getClientHost() == null) {

            /*
             * TBD: a rogue client could set the clientHost in a request to
             * a fake address to disguise its actual location.  We could
             * validate that the request is actually forwarded from another
             * RequestHandler.  However, that check is not cheap, so we
             * need to consider the cost/benefit tradeoff.
             */
            request.setAuthContext
                (new AuthContext
                 (currCtx.getLoginToken(),
                  internalLoginMgr.getHandle(
                      target.getRepNodeId()).getLoginToken(),
                  ExecutionContext.getCurrentUserHost()));
        }
    }

    /**
     * Computes the wait before retrying a request. The wait time is
     * doubled with each successive wait.
     *
     * @param limitNs the limiting time for retries
     *
     * @param prevWaitNs the previous wait time
     *
     * @return the amount of time to sleep the next time around in nanoseconds
     */
    long computeWaitBeforeRetry(final long limitNs, long prevWaitNs) {
        long thisWaitNs = Math.min(prevWaitNs << 1, RETRY_SLEEP_MAX_NS);
        final long now = System.nanoTime();
        final long maxWaitNs = limitNs - now;

        if (maxWaitNs <= 0) {
            return 0;
        }

        if (thisWaitNs > maxWaitNs) {
            thisWaitNs = maxWaitNs;
        }

        logger.fine("Retrying after wait: " +
                    NANOSECONDS.toMillis(thisWaitNs) + "ms");

        return thisWaitNs;
    }

    /**
     * Dispatches the special NOP request.  Keep this method and the async
     * version in AsyncRequestDispatcherImpl up-to-date with each other.
     * <p>
     * It only does the minimal exception processing, invalidating the handle
     * if necessary; the real work is done by the invoker. The handle
     * invalidation mirrors the handle invalidation done by
     * handleRemoteException.
     */
    @Override
    public Response executeNOP(RepNodeState rns,
                               int timeoutMs,
                               LoginManager loginMgr)
        throws Exception {

        /* Different clock mechanisms, therefore the two distinct calls. */

        final RequestHandlerAPI ref =
            rns.getReqHandlerRef(getRegUtils(), timeoutMs);

        if (ref == null) {
            /* needs to be resolved. */
            return null;
        }

        rns.requestStart();
        activeRequestCount.incrementAndGet();
        final long startTimeNs = latencyTracker.markStart();

        try {

            final int topoSeqNumber =
                getTopologyManager().getTopology().getSequenceNumber();

            final Request nop =
                Request.createNOP(topoSeqNumber, getDispatcherId(), timeoutMs);

            nop.setSerialVersion(rns.getRequestHandlerSerialVersion());

            if (loginMgr != null) {
                nop.setAuthContext(
                    new AuthContext(
                        loginMgr.getHandle(
                            rns.getRepNodeId()).getLoginToken()));
            }

            final Response response = ref.execute(nop);
            processResponse(startTimeNs, nop, response);

            return response;
        } catch (ConnectException |
                 ConnectIOException |
                 NoSuchObjectException |
                 ServerError |
                 UnknownHostException e) {
            /*
             * Make sure it's taken out of circulation so that the state
             * update thread can re-establish it.
             */
            rns.noteReqHandlerException(e);
            throw e;
        } finally {
            rns.requestEnd();
            activeRequestCount.decrementAndGet();
            latencyTracker.markFinish(OpCode.NOP, startTimeNs);
        }
    }

    /**
     * Handles all exceptions encountered during the dispatch of a request.
     * All remote exceptions are re-dispatched to handleRemoteException.
     * <p>
     * The method either throws a runtime exception, as a result of the
     * exception processing, indicating that the request should be terminated
     * immediately, or returns normally indicating that it's ok to continue
     * trying the request with other nodes.
     *
     * @param request the request that resulted in the exception
     * @param initialTimeoutMs the timeout associated with the initial request
     *                          before any retries.
     * @param target identifies the target RN for the request
     * @param dispatchException the exception to be handled
     * @param loginHandle the LoginHandle used to acquire authentication,
     *                    or null if no authentication supplied locally
     */
    Exception handleDispatchException(Request request,
                                      int initialTimeoutMs,
                                      RepNodeState target,
                                      Exception dispatchException,
                                      LoginHandle loginHandle) {
        try {
            throw dispatchException;
        } catch (RemoteException re) {
            handleRemoteException(request, target, re);
        } catch (InterruptedException ie) {
            throw new OperationFaultException("Unexpected interrupt", ie);
        } catch (RNUnavailableException rue) {
            /* Retry at a different RN. */
        } catch (SessionAccessException sae) {
            /* Retry at a different RN. */
        } catch (DurabilityException de) {
            /*
             * If its operation was known to not produce side effects, Retry.
             * Currently in most cases, JE will wait for sufficient Replicas
             * when begin transaction. JE may use up the request timeout,
             * in that case, it will not retry. Filed #27107 for JE to change
             * JE IRE wait time so that the JE operation gives up sooner to
             * permit the dispatcher to retry.
             */
            if (!(de.getNoSideEffects())) {
                throw de;
            }
        } catch (ConsistencyException ce) {
            if (!request.isInitiatingDispatcher(dispatcherId)) {

                /*
                 * Propagate the exception to the client so it can be
                 * retried.
                 */
                throw ce;
            }
            /* At top level in client, retry until timeout. */
        } catch (WrappedClientException wce) {
            if (!request.isInitiatingDispatcher(dispatcherId)) {
                /* Pass it through. */
                throw wce;
            }
            handleWrappedClientException((RuntimeException) wce.getCause(),
                                         request,
                                         loginHandle);
        } catch (RequestTimeoutException rte) {
            if (request.isInitiatingDispatcher(dispatcherId)) {
              /* Reset it to the initial value. */
              rte.setTimeoutMs(initialTimeoutMs);
            }

            throw rte;
        } catch (ServerResourceLimitException srle) {

            /*
             * The server has hit a resource constraint. Try another RN, and
             * then wait (as usual) before trying again if no RNs can satisfy
             * the request. This exception always means that there were no side
             * effects, so it is safe to retry.
             */

        } catch (FaultException fe) {

            /* If we have a TTL fault we may want to continue to retry */
            if (fe.getFaultClassName().equals
                    (TTLFaultException.class.getName())) {

                /* If back at top level in client, retry until timeout. */
                if (request.isInitiatingDispatcher(dispatcherId)) {
                    return dispatchException;
                }

                /*
                 * If we know that the targeted partition is moving, convert to
                 * a RNUnavailableException and retry until timeout.
                 */
                if (topoManager.inTransit(request.getPartitionId())) {
                    return new RNUnavailableException(fe.getMessage());
                }
            }

            throw fe;
        } catch (MetadataNotFoundException e) {

            /*
             * The RN didn't have the metadata, which may mean it hasn't
             * received the needed metadata update yet. Retry at a different
             * RN.
             */
            /*
             * TODO: If we knew the client's metadata sequence number and found
             * it to be lower than the metadata sequence number in the
             * MetadataNotFoundException from the RN, then it would be better
             * to throw the exception to the application right away since, in
             * that case, they need to refresh the table object and there is no
             * point in retrying. We might be able to get the metadata sequence
             * number from the TableAPI, which is available in the KVStoreImpl.
             * We would also probably need to add a TableAPI accessor in order
             * to discover its latest metadata sequence number.
             */
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected exception", e);
        }

        return dispatchException;
    }

    /**
     * Some wrapped exceptions need special handling. Others are re-thrown.
     */
    private void handleWrappedClientException(RuntimeException re,
                                              Request request,
                                              LoginHandle loginHandle) {

        if (re instanceof AuthenticationRequiredException) {
            handleAuthenticationRequiredException(
                request, loginHandle,(AuthenticationRequiredException) re);
            return;
        } else if (re instanceof MetadataNotFoundException) {
            /*
             * [#27606] Dispatcher should retry MetadataNotFoundException
             * On the server side, MetadataNotFoundException will be wrapped
             * as WrappedClientException to throw back to client. So if the
             * cause of WrappedClientException is MetadataNotFoundException,
             * we return here to let the dispatcher retry.
             */
            return;
        }

        throw re;
    }

    /**
     * Handles all cases of RemoteException taking appropriate action for each
     * one as described in the catch clauses in the body of the method.
     * <p>
     * The method either throws a runtime exception, as a result of the
     * exception processing, indicating that the request should be terminated
     * immediately, or returns normally indicating that it's ok to continue
     * trying the request with other nodes.
     * <p>
     * Implementation note: The code in the exception-specific catch clauses
     * is redundant wrt the general catch for RemoteException. This is done
     * deliberately to emphasize the treatment of each specific exception and
     * the motivation behind such treatment.
     *
     * @param request the request that resulted in the exception
     * @param rnState the target RN for the request
     * @param exception the exception to be handled
     */
    private void handleRemoteException(Request request,
                                       RepNodeState rnState,
                                       final RemoteException exception) {

        logger.fine(exception.getMessage());

        try {
            /* Rethrow just for the purposes of exception discrimination */
            throw exception;
        } catch (UnknownHostException uhe) {

            /*
             * RMI connection could not be created because the hostname could
             * not be resolved. This is probably due to an incorrect KVStore
             * configuration or due to some DNS glitch. The request can be
             * safely retried with a different RN.
             *
             * Remove this RN from consideration for subsequent dispatches
             * until the state update thread re-established the handle. The
             * same course of action is taken by the next three network
             * related exceptions.
             */
            rnState.noteReqHandlerException(uhe);
            return;
        } catch (ConnectException ce) {

            /*
             * The RN could not be contacted due to a ConnectException,
             * possibly because the RN was not listening on that port. The
             * request can be safely retried with a different RN (subject to
             * the requests constraints) in this case, since the request body
             * itself was not sent to the target RN. <p>
             *
             * The exception could indicate that the RN was down. Or that the
             * RN went down, came back up and was assigned a different RMI
             * server port, but the invoker still has the old port cached in
             * its remote reference and is trying to contact the RN on this
             * old port. In this case, the cached remote connection to the RN
             * will need to be updated, by contacting the registry, and the
             * request retried.
             */

            rnState.noteReqHandlerException(ce);
            return;
        } catch (ConnectIOException cie) {

            /*
             * A problem detected during the initial handshake between the
             * client and the server, for example, because the server thread
             * pool limit has been reached. It may also be due to some network
             * level IO exception. Once again, the request can safely be
             * retried.
             *
             * RMI actually throws this exception whenever there is any
             * network I/O related issue, even for example, when a client
             * cannot connect to a registry, or a service. So remove the
             * RN from consideration for subsequent request dispatches.
             */

            rnState.noteReqHandlerException(cie);
            return;
        } catch (MarshalException me) {

            /*
             * A possible network I/O error during the execution of the
             * request. In this case, the call may or may not have reached and
             * been executed by the server. If the request was for a write
             * operation, the failure is propagated back to the invoker as a
             * KVStore exception.
             */
            faultIfWrite(request, "Problem during marshalling", me);

            return;
        } catch (UnmarshalException ue) {
            faultIfWrite(request, "Problem during unmarshalling", ue);
            return;
        } catch (ServerException se) {

            /*
             * An unhandled runtime exception in the thread processing the
             * request. It's likely that the problem was confined to the
             * specific request. Since the server, in this case, is up and
             * taking requests, it is maintained in its current state in the
             * state table.
             */

            /* A problem in the LocalDispatchHandler */
            faultIfWrite(request, "Exception in server", se);
            return;
        } catch (ServerError se) {

            /*
             * An Error in the thread processing the request. An Error
             * indicates a more serious problem on the Server.
             */
            rnState.noteReqHandlerException(se);

            /* A severe problem in the LocalDispatchHandler */
            faultIfWrite(request, "Error in server", se);
            return;
        } catch (NoSuchObjectException noe) {

            /*
             * The RN object was not found in the target RN's VM. Each remote
             * reference contains an ObjId that uniquely identifies an
             * instance of the object. When a server process goes down, comes
             * back up and rebinds a remote object it may be assigned a
             * different id from the one that is cached in the client's remote
             * reference.
             */
            rnState.noteReqHandlerException(noe);
            return;
        } catch (RemoteException e) {
            /* Likely some transient network related problem. */
            faultIfWrite(request, "unexpected exception", e);
            return;
        }
    }

    /**
     * Handle an AuthenticationRequiredException in the case that we are the
     * initiating dispatcher. We attempt LoginToken renewal, if possible.  This
     * is primarily in support of access by KVSessionManager, which is normally
     * authenticated via an InternalLoginManager.  If renewal succeeds, or if a
     * SessionAccessException occurs during token renewal, this method returns
     *  silently.  Otherwise, the exception is re-thrown.
     */
    private void handleAuthenticationRequiredException(
        Request request,
        LoginHandle loginHandle,
        AuthenticationRequiredException are) {

        /*
         * Attempt token renewal, if possible.  This is relevant for the
         * internal login manager.
         */
        if (request.getAuthContext() == null || loginHandle == null) {
            throw are;
        }

        final LoginToken currToken = request.getAuthContext().getLoginToken();
        try {
            if (loginHandle.renewToken(currToken) == currToken) {
                throw are;
            }
        } catch (SessionAccessException sae) {
            /*
             * Athough the renewal could not be completed as the result
             * of a SessionAccessException, it may be posssible to
             * complete this request against another RN.
             */
            logger.fine(sae.getMessage());
        }
    }

    /**
     * Checks to see if the TTL has expired for the specified request. If so
     * an exception is thrown.
     */
    void checkTTL(Request request) {

        try {
            request.decTTL();
        } catch (TTLFaultException ttlfe) {

            /*
             * If in transit, throw  RNUnavailableException. This wil cause
             * the operation to be retried.
             */
            if (topoManager.inTransit(request.getPartitionId())) {
                throw new RNUnavailableException(ttlfe.getMessage());
            }

            /*
             * Otherwise, throw a FaultException so that the TTL exception does
             * not get serialized or thrown to the user.
             */
            throw new FaultException(ttlfe, true);
        }
    }

    /**
     * If a RemoteException, or another communication-related exception, was
     * encountered during a write request, then this throws FaultException.
     * With some remote exceptions, it's hard to say for sure whether the
     * requested operation was carried out, or was abandoned and is therefore
     * safe to retry. For example, if the exception took place during the
     * marshalling of the arguments, the transaction was never started. If the
     * exception took place while marshalling the return value or return
     * exception then it may have been carried out.  So in such cases we play
     * it safe and inform the client of the failure.
     *
     * Should we use a specific fault so the app knows that the write may have
     * completed?
     */
    void faultIfWrite(Request request,
                      String faultMessage,
                      Exception exception)
        throws FaultException {

        if (request.isWrite()) {
            throwAsFaultException(faultMessage, exception);
        }

        /* A read operation can be safely retried at some other RN. */
    }

    /**
     * Throws the appropriate FaultException using the specified message and
     * with the exception as the cause.  In particular, throws
     * RequestTimeoutException if it can determine that the failure was caused
     * by a timeout.
     */
    void throwAsFaultException(String faultMessage, Exception exception)
        throws FaultException {

        String message = null;
        Throwable cause = exception.getCause();
        while (cause != null) {
            try {
                throw cause;
            } catch (java.net.SocketTimeoutException STE) {
                message = STE.getMessage();
                break;
            } catch (Throwable T) {
                /* Continue down the cause list. */
                cause = cause.getCause();
            }
        }

        if (message == null) {
            throw new FaultException(faultMessage, exception, isRemote);
        }

        throw new RequestTimeoutException(0, message, exception, isRemote);
    }

    /**
     * Utility method to maintain the exclude set: the set of RNs, that are
     * removed from further consideration during the request dispatch.
     *
     * @return the set augmented to hold the rep node state.
     */
    Set<RepNodeId> excludeRN(Set<RepNodeId> excludeSet, RepNodeState rnState) {
        if (rnState == null) {
            return excludeSet;
        }

        /* Exclude the node from consideration during a retry. */
        if (excludeSet == null) {
            excludeSet = new HashSet<RepNodeId>();
        }
        excludeSet.add(rnState.getRepNodeId());
        return excludeSet;
    }

    /**
     * Directs the request to a specific RN. It may be forwarded by that RN
     * if it's unsuitable.
     *
     * @param request the request to be executed.
     * @param targetId the initial target for execution
     *
     * @return the response resulting from execution of the request
     */
    public Response execute(Request request,
                            RepNodeId targetId,
                            LoginManager loginMgr)
        throws FaultException {

        return execute(request, targetId, null, loginMgr);
    }

    @Override
    public Response execute(Request request,
                            Set<RepNodeId> excludeRNs,
                            LoginManager loginMgr)
        throws FaultException {

        return execute(request, null, excludeRNs, loginMgr);
    }

    @Override
    public Response execute(Request request,
                            LoginManager loginMgr)
        throws FaultException {

        return execute(request, null, null, loginMgr);
    }

    /**
     * Process the response, updating any topology or status updates resulting
     * from the response.
     * <p>
     * Note that if this request dispatcher is hosted in a replica RN, it
     * cannot save them to the LADB. It's possible that the replica RN already
     * has a copy of the updated of the topology via the the replication
     * stream. In future, a trigger mechanism may prove to be useful so that
     * we can track changes and update the in-memory copy of the Topology. For
     * now, it may be worth polling the LADB on some periodic basis to see if
     * the sequence number associated with the Topology has changed and if so
     * update the "in-memory" copy.
     * <p>
     * @param startNs the start time associated with the request
     * @param request the request associated with the response
     * @param response the response
     */
    void processResponse(long startNs, Request request, Response response) {

        final TopologyInfo topoInfo = response.getTopoInfo();

        if (topoInfo != null) {
            if (topoInfo.getChanges() != null) {
                /* Responder has more recent topology */
                topoManager.update(topoInfo);
            } else if (topoInfo.getSequenceNumber() >
                       topoManager.getTopology().getSequenceNumber()) {
                /*
                 * Responder has more recent topology but could not supply the
                 * changes in incremental form, need to pull over the entire
                 * topology.
                 */
                stateUpdateThread.pullFullTopology(response.getRespondingRN(),
                                                   topoInfo.getSequenceNumber());
            }
        }

        repGroupStateTable.
            update(request, response,
                   (int) NANOSECONDS.toMillis((System.nanoTime() - startNs)));
    }

    /**
     * Selects a suitable target RN from amongst the members of the
     * replication group.
     *
     * @param rgState the group that identifies the candidate RNs
     * @param request the request to be dispatched
     * @param excludeRNs the RNs that must be excluded either because they
     * have forwarded the request or because they are not active.
     *
     * @return a suitable non-null RN
     *
     * @throws NoSuitableRNException if an RN could not be found.
     */
    RepNodeState selectDispatchRN(RepGroupState rgState,
                                  Request request,
                                  Set<RepNodeId> excludeRNs)
        throws NoSuitableRNException {

        /* When testing, we want this method to throw a NoSuitableRNException.
         * The problem is that NoSuitableRNException is defined to be a
         * checked exception rather than a RuntimeException. Additionally,
         * it is also defined to be a private, non-static inner class.
         * Because of this, the test hook executed below cannot be defined
         * to throw an instance of NoSuitableRNException directly; because
         * NoSuitableRNException cannot be instantiated by classes (such
         * as test classes) outside of this class, and also because
         * TestHook.doHook does not throw Exception. As a result, the
         * test hook is defined to throw an instance of RuntimeException.
         * Thus, when testing, and only when testing, upon receiving a
         * RuntimeException from the test hook, this method will then throw
         * a NoSuitableRNException in response.
         */
        try {
            assert TestHookExecute.doHookIfSet(requestExecuteHook, request);
        } catch (RuntimeException e) {
            throw new NoSuitableRNException("from test");
        }

        final boolean needsMaster = request.needsMaster();
        if (needsMaster) {
            final RepNodeState master = rgState.getMaster();
            if ((master != null) &&
                (excludeRNs == null ||
                 !excludeRNs.contains(master.getRepNodeId())) &&
                request.isPermittedZone(master.getZoneId())) {
                logger.log(Level.FINEST,
                           () ->
                           "Dispatching to master: " + master.getRepNodeId());
                return master;
            }

            /*
             * Needs a master. If any of the other nodes in the RG are
             * available use them instead, they may be able to re-direct to
             * the master, since they may have more current information. It's
             * not as efficient, but is better than failing the request.
             */
        } else if (request.needsReplica()) {
            /* Exclude the master node from consideration for the request. */
            excludeRNs = excludeRN(excludeRNs, rgState.getMaster());
        }

        final RepNodeState rnState =
            rgState.getLoadBalancedRN(request, excludeRNs);

        if (rnState != null) {
            logger.log(Level.FINEST,
                       () ->
                       "Dispatching target RN: " + rnState.getRepNodeId());
            return rnState;
        }

        /*
         * No active nodes in the RG based upon the state information
         * available at this RG. This dispatcher may not have a current
         * topology and the RG itself has been removed. If that's the case
         * this dispatcher will eventually hear about the change. If all the
         * nodes in the RG are truly not active, then all we can do is retry
         * until the nodes come on line. For now, just dispatch to a random
         * node in the group where it may fail or succeed.
         */
        final RepGroupId groupId = rgState.getResourceId();
        final RepNodeState randomRN = rgState.getRandomRN(request, excludeRNs);
        if (randomRN == null) {
            final String message =
                ((needsMaster) ?
                 ("No active (or reachable) master in rep group: " +  groupId) :
                 ("No suitable node currently available to service" +
                  " the request in rep group: " +  groupId)) +
                ". Unsuitable nodes: " +
                ((excludeRNs == null) ? "none" : excludeRNs) +
                ((readZones != null) ? ". Read zones: " + readZones : "");
            logger.fine(message);

            throw new NoSuitableRNException(message);
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Dispatching to random RN for operation " +
                        request.getOperation() + ": " +
                        randomRN.getRepNodeId());
        }
        return randomRN;
    }

    /* Primarily for debugging use. */
    public void logRequestStats() {
        for (RepNodeState rnState: repGroupStateTable.getRepNodeStates()) {
            logger.info(rnState.printString());
        }
    }

    @Override
    public Topology getTopology() {
        return topoManager.getTopology();
    }

    @Override
    public TopologyManager getTopologyManager() {
        return topoManager;
    }

    @Override
    public RepGroupStateTable getRepGroupStateTable() {
        return repGroupStateTable;
    }

    @Override
    public ResourceId getDispatcherId() {
        return dispatcherId;
    }

    @Override
    public PartitionId getPartitionId(byte[] keyBytes) {
        return topoManager.getTopology().getPartitionId(keyBytes);
    }

    /* (non-Javadoc)
     * @see oracle.kv.impl.api.RequestDispatcher#getRegUtils()
     */
    @Override
    public RegistryUtils getRegUtils() {
       return regUtils;
    }

    @Override
    public UncaughtExceptionHandler getExceptionHandler() {
        return stateUpdateThread.getUncaughtExceptionHandler();
    }

    @Override
    public void setRegUtilsLoginManager(LoginManager loginMgr) {
        this.regUtilsLoginMgr = loginMgr;
        updateRegUtils();
    }

    /**
     * Provides a mechanism for updating regUtils.  Because either of the
     * topology or login manager may change, we synchronize here to ensure
     * a consistent update.
     */
    private synchronized void updateRegUtils() {
        final Topology topo = topoManager.getTopology();
        regUtils = new RegistryUtils(topo, regUtilsLoginMgr, getClientId());
    }

    /**
     * A exception used within the request dispatcher to signal that no
     * suitable RN was found.
     */
    @SuppressWarnings("serial")
    class NoSuitableRNException extends Exception {
        NoSuitableRNException(String message) {
            super(message);
        }

    }

    @Override
    public Table getTable(KVStoreImpl store,
                           String namespace,
                           String tableName)
            throws FaultException {
        return getTable(store, namespace, tableName, 0);
    }

    @Override
    public Table getTable(KVStoreImpl store,
                           String namespace,
                           String tableName,
                           int cost)
            throws FaultException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid name " + tableName);
        }
        return callRNsWithRetry(store,
                                "get table " + tableName,
                                rnid -> getTable(rnid, namespace, tableName,
                                                 cost));
    }

    /**
     * Gets a table by name from the specified RN.
     */
    private Table getTable(RepNodeId rnid,
                           String namespace,
                           String tableName,
                           int cost)
            throws RemoteException, NotBoundException {
        /* this method will never return null */
        final RepNodeAdminAPI repNodeAdmin = regUtils.getRepNodeAdmin(rnid);

        /*
         * If cost is 0, use the old way to get the table. Note that this
         * would allow the caller to get a table without any charges or
         * access checks.
         */
        if (cost == 0) {
            return (TableImpl)repNodeAdmin.getMetadata(MetadataType.TABLE,
                 new TableMetadata.TableMetadataKey(namespace, tableName), 0);
        }
        return (Table)repNodeAdmin.getTable(namespace, tableName, cost);
    }

    @Override
    public Table getTableById(KVStoreImpl store, long tableId) {
        return callRNsWithRetry(store,
                                "get table " + tableId,
                                rnid -> getTableById(rnid, tableId));
    }

    /**
     * Gets a table by ID from the specified RN.
     */
    private Table getTableById(RepNodeId rnid, long tableId)
                throws RemoteException, NotBoundException {

        /* this method will never return null */
        final RepNodeAdminAPI repNodeAdmin = regUtils.getRepNodeAdmin(rnid);
        return (Table)repNodeAdmin.getTableById(tableId);
    }

    @Override
    public TableMetadata getTableMetadata(KVStoreImpl store) {
        return callRNsWithRetry(store,
                                "get table metadata",
                                rnid -> getTableMetadata(rnid));
    }

    /**
     * Gets the table metadata from the specified RN.
     */
    private TableMetadata getTableMetadata(RepNodeId rnid)
            throws RemoteException, NotBoundException {
        final RepNodeAdminAPI repNodeAdmin = regUtils.getRepNodeAdmin(rnid);
        return (repNodeAdmin == null) ? null :
                      (TableMetadata)repNodeAdmin.getMetadata(MetadataType.TABLE);
    }

    @Override
    public MetadataInfo getMetadataInfo(KVStoreImpl store,
                                        MetadataKey key,
                                        int seqNum) {
        assert key != null;
        assert seqNum >= 0;

        return callRNsWithRetry(store,
                                "get metadata info for " + key,
                                rnid -> getMetadataInfo(rnid, key, seqNum));
    }

    /**
     * Gets metadata info from the specified RN.
     */
    private MetadataInfo getMetadataInfo(RepNodeId rnid,
                                         MetadataKey key,
                                         int seqNum)
            throws RemoteException, NotBoundException {

        /* this method will never return null */
        final RepNodeAdminAPI repNodeAdmin = regUtils.getRepNodeAdmin(rnid);
        return repNodeAdmin.getMetadata(key.getType(), key, seqNum);
    }

    /*
     * A remote function.
     */
    interface RemoteFunction<T, R> {
        R apply(T t) throws RemoteException, NotBoundException;
    }

    /**
     * Calls the specified remote function on each rep node in turn and returns
     * the result, doing retries for various exceptions. Null is returned
     * only if the target number of attempts is reached. Other failure paths
     * must throw exceptions.
     *
     * @param <V> the return type
     * @param store a store instance
     * @param operation the operation being performed
     * @param func the remote function
     * @return the result of the function or null.
     */
    private <V> V callRNsWithRetry(KVStoreImpl store,
                                   String operation,
                                   RemoteFunction<RepNodeId, ? extends V> func) {
        ObjectUtil.checkNull("store", store);
        if (regUtils == null) {
            throw new RNUnavailableException("RepNode not yet " +
                                             "initialized");
        }

        /*
         * Only the last exception is kept.
         */
        Exception cause = null;

        /* has SessionAccessException been seen */
        boolean SAEseen = false;

        /*
         * This loop will apply the function to the master in each rep group
         * until a non-null value is returned, or at least a minimum number of
         * successful groups have been called. The minimum is based on the total
         * number of groups.
         *
         * The order of the groups matches the order of metadata broadcast
         * to increase the likelihood that we access the freshest metadata
         * soonest.
         */
        final Topology topo = topoManager.getTopology();
        final List<RepGroup> groups = TopologyUtil.getOrderedRepGroups(topo);
        final int nGroups = groups.size();

        /* Minimum number of groups to try */
        int targetGroups = (nGroups < 2) ? nGroups :
                           (nGroups < 12) ? 2 : nGroups / 4;

        for (RepGroup group : groups) {
            /* Any successful call counts */
            boolean groupSuccess = false;
            /*
             * Potentially call all RNs in the group. This will ensure that a
             * master is called (baring master transfers, failures, etc.
             */
            for (RepNode rn : group.getRepNodes()) {
                final RepNodeId rnId = rn.getResourceId();
                final RepNodeState rnState =
                        repGroupStateTable.getNodeState(rnId);
                /*
                 * Skip this RN if we know it is not a master or in a secondary
                 * datacenter (they are all replicas)
                 */
                if ((rnState != null && !rnState.getRepState().isMaster()) ||
                    topo.getDatacenter(rnId).getDatacenterType().isSecondary()) {
                    continue;
                }
                try {
                    try {
                        cause = null;
                        V ret = func.apply(rnId);
                        if (ret != null) {
                            return ret;
                        }
                        groupSuccess = true;
                    } catch (RepNodeAdminFaultException rafe) {
                        if (SessionAccessException.class.getName().equals(
                            rafe.getFaultClassName())) {

                            SAEseen = true;
                        }
                        throw rafe;
                    } catch (AuthenticationRequiredException are) {
                        /* Try to re-authenticate, if successful try another */
                        final LoginManager requestLoginMgr =
                            KVStoreImpl.getLoginManager(store);
                        if (!store.tryReauthenticate(requestLoginMgr)) {
                            throw are;
                        }
                    } catch (UnsupportedOperationException uoe) {
                        /* If this is not a supported node, try another */
                    }
                } catch (Exception e) {
                    cause = e;
                }
            }
            /*
             * If a call to an RNs in the group was successful check
             * whether enough groups have been called. If so, return null.
             */
            if (groupSuccess) {
                targetGroups--;
                if (targetGroups <= 0) {
                    return null;
                }
            }
        }

        if (SAEseen) {
            LoginManager requestLoginMgr = KVStoreImpl.getLoginManager(store);
            /* try to re-authenticate */
            if (!store.tryReauthenticate(requestLoginMgr)) {
                if (cause == null) {
                    return null;
                }
                throwAsFaultException("Unable to " + operation +
                                      ": " + cause.getMessage(),
                                      cause);
                return null; /* Not reachable */
            }
        }

        /*
         * If Masters were unavailable, or we had to re-login, try again using
         * any RN, returning the first successful result
         */
        for (RepGroup group : groups) {
            for (RepNode rn : group.getRepNodes()) {
                try {
                    return func.apply(rn.getResourceId());
                } catch (Exception e) {
                    cause = e;
                }
            }
        }

        if (cause != null) {
            if ( cause instanceof KVSecurityException) {
                /* do not wrap into a FaultException if it's a KVSecurityExc */
                throw (KVSecurityException)cause;
            }
            throwAsFaultException("Unable to " + operation +
                                  ": " + cause.getMessage(),
                                  cause);
        }
        return null;
    }

    /**
     * If, after trying all RNs in the target group, none were available and
     * NoSuitableRNException has been thrown, it's possible that an exception
     * should be thrown immediately rather than retrying for the request
     * timeout period. If so, return that exception. If not, return null.
     * @param exception the highest priority (ranked) saved exception returned
     * from attempts to dispatch.
     * @return an exception or null
     */
    protected RuntimeException checkThrowNoSuitableRN(Exception exception) {
        if (exception instanceof SessionAccessException) {
            logger.fine("SessionAccessException in dispatch, " +
                        "throwing AuthenticationRequiredException");

            return new AuthenticationRequiredException(
                "Can't access login session, reauthenticate", true);
        }
        return null;
    }

    /**
     * The listener used to update the local copy of regUtils whenever there
     * is a Topology change.
     */
    private class RegUtilsMaintListener implements
        TopologyManager.PostUpdateListener {

        @Override
        public boolean postUpdate(Topology topology) {
            updateRegUtils();
            return false;
        }
    }

    /**
     * A topology post-update listener used to update the list of read zone
     * IDs.  Only used when readZones is not null.
     */
    private class UpdateReadZoneIds
            implements TopologyManager.PostUpdateListener {

        @Override
        public boolean postUpdate(final Topology topo) {
            assert readZones != null;
            final List<Integer> ids = new ArrayList<Integer>(readZones.size());
            final Set<String> unknownZones = new HashSet<String>(readZones);
            for (final Datacenter zone : topo.getDatacenterMap().getAll()) {
                if (readZones.contains(zone.getName())) {
                    ids.add(zone.getResourceId().getDatacenterId());
                    unknownZones.remove(zone.getName());
                }
            }
            if (!unknownZones.isEmpty() && logger.isLoggable(Level.WARNING)) {
                logger.warning("Some read zones not found: " + unknownZones);
            }
            final int[] array = new int[ids.size()];
            int i = 0;
            for (final int id : ids) {
                array[i++] = id;
            }
            readZoneIds = array;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Updated read zone IDs: {0}", ids);
            }
            return false;
        }
    }

    @Override
    public Map<OpCode, LatencyResult>
    getLatencyStats(String watcherName, boolean clear) {
        return latencyTracker.obtain(watcherName, (k) -> clear).
            entrySet().stream().
            collect(Collectors.toMap(
                e -> e.getKey(), e -> new LatencyResult(e.getValue())));
    }

    /* The total number of requests that were retried. */
    @Override
    public long getTotalRetryCount(String watcherName, boolean clear) {
        return (long) totalRetryCount.obtain(watcherName, clear).getVolume();
    }

    /* For testing only. */
    public void setTestHook(TestHook<Request> hook) {
        requestExecuteHook = hook;
    }

    public void setPreExecuteHook
        (ExceptionTestHook<Request, Exception> hook) {
        preExecuteHook = hook;
    }

    @Override
    public int[] getReadZoneIds() {
        return readZoneIds;
    }
}
