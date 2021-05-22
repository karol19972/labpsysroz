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

package oracle.kv.impl.async.registry;

import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.async.FutureUtils.whenComplete;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import oracle.kv.impl.async.AsyncVersionedRemoteAPI;
import oracle.kv.impl.async.CreatorEndpoint;
import oracle.kv.impl.async.DialogTypeFamily;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The API for the service registry, which maps service names to service
 * endpoints.  The service registry is the asynchronous replacement for the RMI
 * registry used for synchronous operations.  This is the API that clients use
 * to register, unregister, lookup, and list the available services.  The
 * {@link ServiceRegistry} interface represents the remote interface that is
 * used to communicate requests over the network.  The {@link
 * ServiceRegistryImpl} class provides the server-side implementation.  The
 * {@link ServiceEndpoint} class is used to represent information about an
 * available remote service.
 *
 * @see ServiceRegistry
 * @see ServiceRegistryImpl
 * @see ServiceEndpoint
 */
public class ServiceRegistryAPI extends AsyncVersionedRemoteAPI {
    private final ServiceRegistry proxyRemote;

    /**
     * Creates a new instance for the specified server and serial version.
     */
    protected ServiceRegistryAPI(ServiceRegistry remote,
                                 short serialVersion) {
        super(serialVersion);
        proxyRemote = remote;
    }

    /**
     * Makes an asynchronous request to create an instance of this class,
     * returning the result as a future.
     *
     * @param endpoint the remote endpoint representing the server
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @param logger for debug logging
     * @return the future
     */
    public static CompletableFuture<ServiceRegistryAPI>
        wrap(CreatorEndpoint endpoint, long timeoutMillis, Logger logger) {

        return Factory.INSTANCE.wrap(endpoint, timeoutMillis, logger);
    }

    /**
     * A factory class for obtain ServiceRegistryAPI instances.
     */
    public static class Factory {

        static final Factory INSTANCE = new Factory();

        /**
         * Create a ServiceRegistryAPI instance.
         */
        protected ServiceRegistryAPI createAPI(ServiceRegistry remote,
                                               short serialVersion) {
            return new ServiceRegistryAPI(remote, serialVersion);
        }

        /**
         * Makes an asynchronous request to create a ServiceRegistryAPI
         * instance, returning the result as a future.
         *
         * @param endpoint the remote endpoint representing the server
         * @param timeoutMillis the timeout for the operation in milliseconds
         * @param logger for debug logging
         * @return the future
         */
        public CompletableFuture<ServiceRegistryAPI>
            wrap(CreatorEndpoint endpoint,
                 long timeoutMillis,
                 final Logger logger) {
            try {
                final ServiceRegistry initiator =
                    new ServiceRegistryInitiator(endpoint, logger);
                return computeSerialVersion(initiator, timeoutMillis)
                    .thenApply(serialVersion ->
                               createAPI(initiator, serialVersion));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        }
    }

    /**
     * Look up an entry in the registry.
     *
     * @param name the name of the entry
     * @param dialogTypeFamily the expected dialog type family, or null to
     * support returning any type
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return a future to receive the service endpoint with the requested
     * dialog type family, if specified, or {@code null} if the entry is not
     * found
     */
    public CompletableFuture<ServiceEndpoint>
        lookup(String name,
               @Nullable DialogTypeFamily dialogTypeFamily,
               long timeoutMillis) {

        try {
            return whenComplete(
                proxyRemote.lookup(getSerialVersion(), name, timeoutMillis),
                (serviceEndpoint, e) -> {
                    if (e == null) {
                        checkServiceEndpointResult(serviceEndpoint,
                                                   dialogTypeFamily);
                    }
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private void checkServiceEndpointResult(ServiceEndpoint result,
                                            @Nullable
                                            DialogTypeFamily dialogTypeFamily)
    {
        if ((result != null) && (dialogTypeFamily != null)) {
            final DialogTypeFamily serviceDialogTypeFamily =
                result.getDialogType().getDialogTypeFamily();
            if (serviceDialogTypeFamily != dialogTypeFamily) {
                throw new IllegalStateException(
                    "Unexpected dialog type family for service" +
                    " endpoint.  Expected: " + dialogTypeFamily +
                    ", found: " + serviceDialogTypeFamily);
            }
        }
    }

    /**
     * Set an entry in the registry.
     *
     * @param name the name of the entry
     * @param endpoint the endpoint to associate with the name
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return a future for when the operation is complete
     */
    public CompletableFuture<Void> bind(String name,
                                        ServiceEndpoint endpoint,
                                        long timeoutMillis) {
        return proxyRemote.bind(getSerialVersion(), name, endpoint,
                                timeoutMillis);
    }

    /**
     * Remove an entry from the registry.
     *
     * @param name the name of the entry
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return a future for when the operation is complete
     */
    public CompletableFuture<Void> unbind(String name,
                                          long timeoutMillis) {
        return proxyRemote.unbind(getSerialVersion(), name, timeoutMillis);
    }

    /**
     * List the entries in the registry.
     *
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return a future to return the entries
     */
    public CompletableFuture<List<String>> list(long timeoutMillis) {
        return proxyRemote.list(getSerialVersion(), timeoutMillis);
    }
}
