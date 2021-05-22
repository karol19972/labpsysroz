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

package oracle.kv.impl.util.registry;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static oracle.kv.impl.async.FutureUtils.checked;
import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.async.FutureUtils.handleFutureGetException;
import static oracle.kv.impl.async.FutureUtils.unwrapException;
import static oracle.kv.impl.async.StandardDialogTypeFamily.ASYNC_REQUEST_HANDLER;
import static oracle.kv.impl.async.StandardDialogTypeFamily.SERVICE_REGISTRY_DIALOG_TYPE;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

import oracle.kv.AuthenticationFailureException;
import oracle.kv.FaultException;
import oracle.kv.KVStoreConfig;
import oracle.kv.RequestTimeoutException;
import oracle.kv.impl.api.AsyncRequestHandlerAPI;
import oracle.kv.impl.api.ClientId;
import oracle.kv.impl.async.AsyncOption;
import oracle.kv.impl.async.DialogHandler;
import oracle.kv.impl.async.DialogHandlerFactory;
import oracle.kv.impl.async.DialogType;
import oracle.kv.impl.async.DialogTypeFamily;
import oracle.kv.impl.async.EndpointConfig;
import oracle.kv.impl.async.EndpointConfigBuilder;
import oracle.kv.impl.async.EndpointGroup;
import oracle.kv.impl.async.EndpointGroup.ListenHandle;
import oracle.kv.impl.async.IOBufferPool;
import oracle.kv.impl.async.ListenerConfig;
import oracle.kv.impl.async.NetworkAddress;
import oracle.kv.impl.async.dialog.nio.NioEndpointGroup;
import oracle.kv.impl.async.exception.DialogException;
import oracle.kv.impl.async.registry.ServiceEndpoint;
import oracle.kv.impl.async.registry.ServiceRegistry;
import oracle.kv.impl.async.registry.ServiceRegistryAPI;
import oracle.kv.impl.async.registry.ServiceRegistryImpl;
import oracle.kv.impl.fault.AsyncEndpointGroupFaultHandler;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.topo.RepNode;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.ResourceId;
import oracle.kv.impl.topo.StorageNode;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.CommonLoggerUtils;

/**
 * Utilities for managing asynchronous services.
 */
public class AsyncRegistryUtils extends AsyncControl {

    /**
     * The endpoint group, for connecting to dialog-layer-based services, or
     * null if not initialized.  Synchronize on the class when accessing this
     * field.
     */
    private static EndpointGroup endpointGroup = null;

    /**
     * The host name to use when registering async service endpoints for
     * services created on this host, or null if not yet set.
     *
     * @see #getServerHostName
     * @see #setServerHostName
     */
    private static String serverHostName = null;

    /** A ServiceRegistryAPI factory that wraps some exceptions. */
    private static final ServiceRegistryAPI.Factory registryFactory =
        new TranslatingExceptionsRegistryFactory();

    /**
     * A test hook that is called at the start of getRequestHandler with the RN
     * ID of the handler to get. If the hook throws a runtime exception, then
     * that exception will be returned as the result of the call.
     */
    public static volatile TestHook<RepNodeId> getRequestHandlerHook;

    /**
     * A test hook that is called by getRequestHandler with the
     * EndpointConfigBuilder used to create the configuration for the
     * AsyncRequestHandler's creator endpoint.
     */
    public static volatile TestHook<EndpointConfigBuilder>
        getRequestHandlerEndpointConfigBuilderHook;

    private AsyncRegistryUtils() {
        throw new AssertionError();
    }

    /**
     * Returns the endpoint group to use for asynchronous operations.  Throws
     * an IllegalStateException if the endpoint group is not available.
     *
     * @return the endpoint group
     * @throws IllegalStateException if the endpoint group is not available
     */
    public static synchronized EndpointGroup getEndpointGroup() {
        if (endpointGroup == null) {
            throw new IllegalStateException(
                "The async EndpointGroup is not initialized");
        }
        return endpointGroup;
    }

    /**
     * Returns the endpoint group to use for asynchronous operations, or null
     * if the endpoint group is not available.
     *
     * @return the endpoint group or null
     */
    public static synchronized EndpointGroup getEndpointGroupOrNull() {
        return endpointGroup;
    }

    /**
     * Creates a new endpoint group to use for asynchronous operations, if one
     * does not already exist.  Note that maintaining a single endpoint group
     * for the life of the JVM is necessary so that server sockets shared with
     * RMI continue to be usable, since RMI expects server sockets to remain
     * open until it closes them.
     *
     * @param logger the logger to be used by the endpoint group
     * @param numThreads the number of threads
     * @param maxQuiescentSeconds the maximum time in seconds an executor is in
     * quiescence before it is shut down
     * @param forClient true if the endpoint group is needed for use for a
     * client call, else false for server side use. If false, then the endpoint
     * group will only be created if async is enabled for the server.
     * @param maxPermits the maximum number of permits that the group's dialog
     * resource manager should grant, which controls the maximum number of
     * concurrent async calls
     * @throws RuntimeException if a problem occurs creating the endpoint group
     */
    public static synchronized
        void initEndpointGroup(Logger logger,
                               int numThreads,
                               int maxQuiescentSeconds,
                               boolean forClient,
                               int maxPermits) {
        initEndpointGroup(
            logger, numThreads, maxQuiescentSeconds, forClient,
            maxPermits, AsyncEndpointGroupFaultHandler.DEFAULT);
    }

    /**
     * Creates a new endpoint group to use for asynchronous operations, if one
     * does not already exist.  Note that maintaining a single endpoint group
     * for the life of the JVM is necessary so that server sockets shared with
     * RMI continue to be usable, since RMI expects server sockets to remain
     * open until it closes them.
     *
     * @param logger the logger to be used by the endpoint group
     * @param numThreads the number of threads
     * @param maxQuiescentSeconds the maximum time in seconds an executor is in
     * quiescence before it is shut down
     * @param forClient true if the endpoint group is needed for use for a
     * client call, else false for server side use. If false, then the endpoint
     * group will only be created if async is enabled for the server.
     * @param maxPermits the maximum number of permits that the group's dialog
     * resource manager should grant, which controls the maximum number of
     * concurrent async calls
     * @param faultHandler the fault handler
     * @throws RuntimeException if a problem occurs creating the endpoint group
     */
    public static synchronized
        void initEndpointGroup(Logger logger,
                               int numThreads,
                               int maxQuiescentSeconds,
                               boolean forClient,
                               int maxPermits,
                               AsyncEndpointGroupFaultHandler faultHandler) {
        checkNull("logger", logger);
        if (endpointGroup != null) {
            return;
        }
        if (!forClient && !serverUseAsync) {
            return;
        }
        IOBufferPool.setLogger(logger);
        try {
            endpointGroup = new NioEndpointGroup(
                logger, numThreads, maxQuiescentSeconds,
                maxPermits, faultHandler);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Unexpected exception creating the async endpoint group: " +
                e.getMessage(),
                e);
        }
    }

    /**
     * Returns the host name to use when registering async service endpoints
     * for services created on this host.  Returns the value specified by the
     * most recent call to setServerHostName, if any, or else returns the host
     * name of the address returned by {@link InetAddress#getLocalHost}.
     *
     * @return the host name
     * @throws UnknownHostException if there is a problem obtaining the host
     * name of the local host
     */
    public static synchronized String getServerHostName()
        throws UnknownHostException {

        if (serverHostName != null) {
            return serverHostName;
        }
        return InetAddress.getLocalHost().getHostName();
    }

    /**
     * Sets the host name for use when registering remote objects, either RMI
     * or async ones, created on this host.
     *
     * @param hostName the server host name
     */
    public static synchronized void setServerHostName(String hostName) {

        /* Set the name used for async services */
        serverHostName = checkNull("hostName", hostName);

        /* Set the hostname to be associated with RMI stubs */
        System.setProperty("java.rmi.server.hostname", hostName);
    }

    /**
     * Get the API wrapper for the async request handler for the RN identified
     * by {@code repNodeId}. Returns {@code null} if the RN is not present in
     * the topology, and otherwise returns exceptionally if the request handler
     * cannot be obtained within the requested timeout. Callers should include
     * the resource ID of the caller, or null if none is available.
     */
    public static CompletableFuture<AsyncRequestHandlerAPI>
        getRequestHandler(Topology topology,
                          RepNodeId repNodeId,
                          ResourceId callerId,
                          long timeoutMs,
                          Logger logger) {

        try {
            if (timeoutMs <= 0) {
                return completedFuture(null);
            }

            final long stopTime = System.currentTimeMillis() + timeoutMs;

            assert TestHookExecute.doHookIfSet(getRequestHandlerHook,
                                               repNodeId);

            final RepNode repNode = topology.get(repNodeId);
            if (repNode == null) {
                return completedFuture(null);
            }
            final EndpointConfig registryEndpointConfig =
                getRegistryEndpointConfig(topology.getKVStoreName(),
                                          getClientId(callerId));
            final StorageNode sn = topology.get(repNode.getStorageNodeId());
            final String bindingName =
                RegistryUtils.bindingName(topology.getKVStoreName(),
                                          repNodeId.getFullName(),
                                          RegistryUtils.InterfaceType.MAIN);
            return getRegistry(topology.getKVStoreName(),
                               getClientId(callerId),
                               sn.getHostname(), sn.getRegistryPort(),
                               registryEndpointConfig, timeoutMs, logger)
                .thenCompose(registry ->
                             getServiceEndpoint(registry, stopTime,
                                                bindingName))
                .thenCompose(endpoint ->
                             getRequestHandler(endpoint, repNodeId, callerId,
                                               stopTime, logger));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private static CompletableFuture<ServiceEndpoint>
        getServiceEndpoint(ServiceRegistryAPI registry,
                           long stopTime,
                           String bindingName) {
        try {
            if (registry == null) {
                throw new IllegalArgumentException("Registry is null");
            }
            final long timeout = stopTime - System.currentTimeMillis();
            if (timeout <= 0) {
                return completedFuture(null);
            }
            return registry.lookup(bindingName, ASYNC_REQUEST_HANDLER,
                                   timeout);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private static CompletableFuture<AsyncRequestHandlerAPI>
        getRequestHandler(ServiceEndpoint serviceEndpoint,
                          RepNodeId repNodeId,
                          ResourceId callerId,
                          long stopTime,
                          Logger logger) {
        try {
            if (serviceEndpoint == null) {
                return completedFuture(null);
            }
            final ClientSocketFactory csf =
                serviceEndpoint.getClientSocketFactory();
            final EndpointConfigBuilder endpointConfigBuilder =
                csf.getEndpointConfigBuilder()
                .configId(callerId);
            assert TestHookExecute.doHookIfSet(
                getRequestHandlerEndpointConfigBuilderHook,
                endpointConfigBuilder);
            final EndpointConfig endpointConfig =
                endpointConfigBuilder.build();
            final long timeout = stopTime - System.currentTimeMillis();
            if (timeout <= 0) {
                return completedFuture(null);
            }
            return AsyncRequestHandlerAPI.wrap(
                getEndpointGroup().getCreatorEndpoint(
                    repNodeId.getFullName(),
                    serviceEndpoint.getNetworkAddress(),
                    NetworkAddress.convert(csf.getLocalAddr()),
                    endpointConfig),
                serviceEndpoint.getDialogType(), timeout, logger);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private static ClientId getClientId(ResourceId rid) {
        return (rid instanceof ClientId) ? (ClientId) rid : null;
    }

    private static EndpointConfig getRegistryEndpointConfig(String storeName,
                                                            ClientId clientId)
        throws IOException {

        final ClientSocketFactory csf =
            RegistryUtils.getRegistryCSF(storeName, clientId);
        if (csf != null) {
            return csf.getEndpointConfig();
        }
        return ClientSocketFactory.getEndpointConfigBuilder(
            KVStoreConfig.DEFAULT_REGISTRY_OPEN_TIMEOUT,
            KVStoreConfig.DEFAULT_REGISTRY_READ_TIMEOUT)
            .build();
    }

    private static CompletableFuture<ServiceRegistryAPI>
        getRegistry(String storeName,
                    ClientId clientId,
                    String hostname,
                    int port,
                    EndpointConfig endpointConfig,
                    long timeout,
                    Logger logger) {
        try {
            final ClientSocketFactory csf =
                RegistryUtils.getRegistryCSF(storeName, clientId);

            /* This CSF can be null in tests */
            final InetSocketAddress localAddr =
                (csf != null) ? csf.getLocalAddr() : null;
            return registryFactory.wrap(
                getEndpointGroup().getCreatorEndpoint(
                    String.format("getRegistry:%s", hostname),
                    new NetworkAddress(hostname, port),
                    NetworkAddress.convert(localAddr),
                    endpointConfig),
                timeout, logger)
                .exceptionally(
                    unwrapException(
                        checked(e -> { throw translateException(e); })));
        } catch (Throwable t) {
            return failedFuture(t);
        }
    }

    /** A ServiceRegistryAPI factory that translates exceptions. */
    private static class TranslatingExceptionsRegistryFactory
            extends ServiceRegistryAPI.Factory {
        @Override
        protected ServiceRegistryAPI createAPI(ServiceRegistry remote,
                                               short serialVersion) {
            return new ExceptionWrappingServiceRegistryAPI(
                remote, serialVersion);
        }
    }

    /**
     * Translates an underlying SSLHandshakeException into an
     * AuthenticationFailureException.
     */
    private static Throwable translateException(Throwable exception) {
        if (exception instanceof DialogException) {
            final Throwable underlyingException =
                ((DialogException) exception).getUnderlyingException();
            if (underlyingException instanceof SSLHandshakeException) {
                return new AuthenticationFailureException(underlyingException);
            }
        }
        return exception;
    }

    /**
     * A ServiceRegistryAPI that provides better exceptions, performing the
     * same alterations as RegistryUtils.ExceptionWrappingRegistry.
     */
    private static class ExceptionWrappingServiceRegistryAPI
            extends ServiceRegistryAPI {
        ExceptionWrappingServiceRegistryAPI(ServiceRegistry remote,
                                            short serialVersion) {
            super(remote, serialVersion);
        }
        @Override
        public CompletableFuture<ServiceEndpoint>
            lookup(String name,
                   DialogTypeFamily family,
                   long timeout) {
            try {
                return translate(super.lookup(name, family, timeout));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        }
        @Override
        public CompletableFuture<Void>
            bind(String name,
                 ServiceEndpoint endpoint,
                 long timeout) {
            try {
                return translate(super.bind(name, endpoint, timeout));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        }
        @Override
        public CompletableFuture<Void> unbind(String name, long timeout) {
            try {
                return translate(super.unbind(name, timeout));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        }
        @Override
        public CompletableFuture<List<String>> list(long timeout) {
            try {
                return translate(super.list(timeout));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        }
        private <T> CompletableFuture<T>
            translate(CompletableFuture<T> future)
        {
            return future.exceptionally(
                unwrapException(
                    checked(e -> { throw translateException(e); })));
        }
    }

    /** Create an async service registry. */
    public static ListenHandle
        createRegistry(@SuppressWarnings("unused") String hostname,
                       int port,
                       ServerSocketFactory ssf,
                       Logger logger)
        throws IOException {

        checkNull("ssf", ssf);

        if (port != 0) {
            ssf = ssf.newInstance(port);
        }

        final ServiceRegistryImpl server = new ServiceRegistryImpl(logger);
        class ServiceRegistryDialogHandlerFactory
            implements DialogHandlerFactory {
            @Override
            public DialogHandler create() {
                return server.createDialogHandler();
            }
            @Override
            public void onChannelError(ListenerConfig config,
                                       Throwable e,
                                       boolean channelClosed) {
            }
        }
        return getEndpointGroup().listen(
            ssf.getListenerConfig(),
            SERVICE_REGISTRY_DIALOG_TYPE.getDialogTypeId(),
            new ServiceRegistryDialogHandlerFactory());
    }

    /**
     * Bind a service entry in the async service registry, throwing
     * RequestTimeoutException if the operation times out.
     */
    public static
        ListenHandle rebind(String hostname,
                            int registryPort,
                            String storeName,
                            final String serviceName,
                            final DialogTypeFamily dialogTypeFamily,
                            final DialogHandlerFactory dialogHandlerFactory,
                            final ClientSocketFactory clientSocketFactory,
                            final ServerSocketFactory serverSocketFactory,
                            Logger logger) {
        final EndpointConfig endpointConfig;
        try {
            endpointConfig = getRegistryEndpointConfig(storeName,
                                                       null /* clientId */);
        } catch (IOException e) {
            return null;
        }
        final long timeout =
            endpointConfig.getOption(AsyncOption.DLG_IDLE_TIMEOUT).longValue();
        return getWithTimeout(
            rebind(hostname, registryPort, storeName, serviceName,
                   dialogTypeFamily, dialogHandlerFactory,
                   clientSocketFactory, serverSocketFactory,
                   endpointConfig, timeout, logger),
            "AsyncRegistryUtils.rebind",
            /* Two registry operations: get registry and bind */
            timeout * 2);
    }

    private static CompletableFuture<ListenHandle>
        rebind(String hostname,
               int registryPort,
               String storeName,
               String serviceName,
               DialogTypeFamily dialogTypeFamily,
               DialogHandlerFactory dialogHandlerFactory,
               ClientSocketFactory clientSocketFactory,
               ServerSocketFactory serverSocketFactory,
               EndpointConfig endpointConfig,
               long timeout,
               Logger logger) {
        return getRegistry(storeName, null /* clientId */, hostname,
                           registryPort, endpointConfig, timeout,
                           logger)
            .thenCompose(registry ->
                         listenAndRebind(serviceName,
                                         dialogTypeFamily,
                                         dialogHandlerFactory,
                                         registry,
                                         timeout,
                                         clientSocketFactory,
                                         serverSocketFactory));
    }

    /**
     * Gets the value of a future, waiting for the specified amount of time.
     *
     * @param <T> the type of the result
     * @param future the future to wait for
     * @param operation a description of the operation being performed
     * @param timeoutMillis the number of milliseconds to wait for the result
     * @return the result
     * @throws RuntimeException an unchecked exception thrown while attempting
     * to perform the operation will be rethrown
     * @throws FaultException if the future was canceled or interrupted, or if
     * a checked exception is thrown while attempting to perform the operation
     * @throws RequestTimeoutException if the timeout is reached before the
     * operation completes
     */
    public static <T> T getWithTimeout(CompletableFuture<T> future,
                                       String operation,
                                       long timeoutMillis) {
        return getWithTimeout(future, operation, timeoutMillis, timeoutMillis);
    }

    /**
     * Gets the value of a future, waiting for the specified amount of time,
     * specifying the original timeout for use in any timeout exception.
     *
     * @param <T> the type of the result
     * @param future the future to wait for
     * @param operation a description of the operation being performed
     * @param originalTimeoutMillis the original number of milliseconds
     * specified for waiting
     * @param timeoutMillis the number of milliseconds to wait for the result
     * @return the result
     * @throws RuntimeException an unchecked exception thrown while attempting
     * to perform the operation will be rethrown
     * @throws FaultException if the future was canceled or interrupted, or if
     * a checked exception is thrown while attempting to perform the operation
     * @throws RequestTimeoutException if the timeout is reached before the
     * operation completes
     */
    public static <T> T getWithTimeout(CompletableFuture<T> future,
                                       String operation,
                                       long originalTimeoutMillis,
                                       long timeoutMillis) {
        try {
            try {
                return future.get(timeoutMillis, MILLISECONDS);
            } catch (Throwable t) {
                final Exception e = handleFutureGetException(t);
                throw new FaultException("Unexpected exception: " + e, e,
                                         false);
            }
        } catch (CancellationException e) {
            throw new FaultException(
                operation + " was canceled while waiting for result",
                e, false);
        } catch (InterruptedException e) {
            throw new FaultException(
                operation + " was interrupted while waiting for result",
                e, false);
        } catch (TimeoutException e) {
            throw new RequestTimeoutException(
                (int) (originalTimeoutMillis), operation + " timed out", e,
                false);
        } catch (RuntimeException|Error e) {

            /*
             * Append the current stack trace so that exceptions originally
             * thrown in other threads can include the current thread's stack
             * trace, to help with debugging.
            */
            CommonLoggerUtils.appendCurrentStack(e);
            throw e;
        }
    }

    /**
     * Establish a listener for the service and bind the service endpoint in
     * the async service registry.
     */
    private static CompletableFuture<ListenHandle>
        listenAndRebind(String serviceName,
                        DialogTypeFamily dialogTypeFamily,
                        DialogHandlerFactory
                        dialogHandlerFactory,
                        ServiceRegistryAPI registry,
                        long timeout,
                        ClientSocketFactory csf,
                        ServerSocketFactory ssf) {
        try {
            final DialogType dialogType = new DialogType(dialogTypeFamily);
            final ListenHandle listenHandle = getEndpointGroup().listen(
                ssf.getListenerConfig(), dialogType.getDialogTypeId(),
                dialogHandlerFactory);
            final NetworkAddress localAddr = listenHandle.getLocalAddress();
            final NetworkAddress addr = new NetworkAddress(
                getServerHostName(),
                (localAddr != null) ? localAddr.getPort() : 0);
            return registry.bind(serviceName,
                                 new ServiceEndpoint(addr, dialogType, csf),
                                 timeout)
                .thenApply(v -> listenHandle);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Unbind a service entry in the async service registry, throwing
     * RequestTimeoutException if the operation times out.
     */
    public static void unbind(String hostname,
                              int registryPort,
                              String storeName,
                              final String serviceName,
                              Logger logger)
        throws IOException
    {
        final EndpointConfig endpointConfig =
            getRegistryEndpointConfig(storeName, null /* clientId */);
        final long timeout =
            endpointConfig.getOption(AsyncOption.DLG_IDLE_TIMEOUT).longValue();
        getWithTimeout(
            getRegistry(storeName, null /* clientId */, hostname,
                        registryPort, endpointConfig, timeout, logger)
            .thenCompose(registry ->
                         unbind(registry, serviceName, timeout)),
            "AsyncRegistryUtils.unbind",
            /* Two registry operations: get registry and unbind */
            timeout * 2);
    }

    private static CompletableFuture<Void>
        unbind(ServiceRegistryAPI registry,
               String serviceName,
               long timeout) {
        try {
            if (registry == null) {
                throw new IllegalArgumentException("Registry is null");
            }
            return registry.unbind(serviceName, timeout);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }
}
