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

package oracle.kv.impl.async;

import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import oracle.kv.impl.async.DialogResourceManager;
import oracle.kv.impl.async.perf.EndpointGroupPerfTracker;
import oracle.kv.impl.util.RateLimitingLogger;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract class for the endpoint group.
 */
public abstract class AbstractEndpointGroup implements EndpointGroup {

    private final Logger logger;
    private final RateLimitingLogger<String> rateLimitingLogger;
    /*
     * Creator and responder endpoints. Use ConcurrentHashMap for thread-safety
     * and high concurrency. Entries are removed when the underlying
     * connections are closing to ensure we do not have many endpoint
     * references lying around. The endpoints cannot create new connections
     * once removed so that the upper layer will notice the removal, otherwise,
     * they can still create new connection with the removed endpoints and
     * hence creating too many connections.
     */
    private final
        ConcurrentHashMap<CreatorKey, AbstractCreatorEndpoint> creators =
        new ConcurrentHashMap<>();
    private final
        ConcurrentHashMap<ResponderKey, AbstractResponderEndpoint> responders =
        new ConcurrentHashMap<>();
    /*
     * Listeners and dialog factory maps. All access should be inside a
     * synchronization block of this endpoint group. We expect listen
     * operations are operated with low frequency.
     */
    private final HashMap<ListenerConfig, Map<Integer, DialogHandlerFactory>>
        listenerDialogHandlerFactoryMap =
        new HashMap<>();
    private final HashMap<ListenerConfig, AbstractListener> listeners =
        new HashMap<>();
    /*
     * Whether the endpoint group is shut down. Once it is set to true, it will
     * never be false again. Use volatile for thread-safety.
     */
    private volatile boolean isShutdown = false;

    /* Perf tracker */
    private final EndpointGroupPerfTracker perfTracker;

    /* Resource manager */
    private final DialogResourceManager dialogResourceManager;

    protected AbstractEndpointGroup(Logger logger, int numPermits) {
        this.logger = logger;
        this.perfTracker = new EndpointGroupPerfTracker(numPermits);
        this.rateLimitingLogger =
            new RateLimitingLogger<>(60 * 1000 /* logSamplePeriodMs */,
                                     20 /* maxObjects */,
                                     logger);
        this.dialogResourceManager = new DialogResourceManager(
            numPermits, perfTracker.getDialogResourceManagerPerfTracker());
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public EndpointGroupPerfTracker getEndpointGroupPerfTracker() {
        return perfTracker;
    }

    @Override
    public DialogResourceManager getDialogResourceManager() {
        return dialogResourceManager;
    }

    public RateLimitingLogger<String> getRateLimitingLogger() {
        return rateLimitingLogger;
    }

    /**
     * Returns a creator endpoint.
     *
     * Gets the endpoint if exists, creates and adds the endpoint otherwise.
     */
    @Override
    public AbstractCreatorEndpoint
        getCreatorEndpoint(String perfName,
                           NetworkAddress remoteAddress,
                           NetworkAddress localAddress,
                           EndpointConfig endpointConfig) {

        checkNull("perfName", perfName);
        checkNull("remoteAddress", remoteAddress);
        checkNull("localAddress", localAddress);
        checkNull("endpointConfig", endpointConfig);
        CreatorKey key = new CreatorKey(remoteAddress, endpointConfig);
        AbstractCreatorEndpoint endpoint = creators.get(key);
        if (endpoint == null) {
            endpoint = newCreatorEndpoint(
                perfName, remoteAddress, localAddress, endpointConfig);
            final AbstractCreatorEndpoint existingEndpoint =
                creators.putIfAbsent(key, endpoint);
            if (existingEndpoint != null) {
                endpoint.shutdown("Concurrent creation of endpoint", true);
                endpoint = existingEndpoint;
            }
        }
        if (isShutdown) {
            endpoint.shutdown("Endpoint group is shutdown", true);
            throw new IllegalStateException(
                    "Endpoint group is already shut down");
        }
        return endpoint;
    }

    /**
     * Removes the creator endpoint from our map.
     */
    public void invalidateCreatorEndpoint(AbstractCreatorEndpoint endpoint) {
        checkNull("endpoint", endpoint);
        CreatorKey key = new CreatorKey(
                endpoint.getRemoteAddress(), endpoint.getEndpointConfig());
        creators.remove(key);
    }

    /**
     * Returns a responder endpoint.
     */
    @Override
    public AbstractResponderEndpoint
        getResponderEndpoint(NetworkAddress remoteAddress,
                             ListenerConfig listenerConfig) {

        checkNull("remoteAddress", remoteAddress);
        checkNull("listenerConfig", listenerConfig);
        ResponderKey key = new ResponderKey(remoteAddress, listenerConfig);
        AbstractResponderEndpoint endpoint = responders.get(key);
        if (endpoint != null) {
            return endpoint;
        }
        return new AbstractResponderEndpoint.NullEndpoint(
                this, remoteAddress, listenerConfig);
    }

    /**
     * Adds the responder endpoint to our map.
     *
     * The method is called when a new connection is accepted.
     */
    public void cacheResponderEndpoint(AbstractResponderEndpoint endpoint) {

        checkNull("endpoint", endpoint);
        final ResponderKey key = new ResponderKey(
            endpoint.getRemoteAddress(), endpoint.getListenerConfig());
        final AbstractResponderEndpoint existingEndpoint =
            responders.put(key, endpoint);
        if (existingEndpoint != null) {
            /*
             * This is possible when the following events happen:
             * (1) a client connecting with port p on connection c1.
             * (2) c1 terminated, the responder handler terminates and should
             * remove the endpoint from the map, but got delayed.
             * (3) the client connecting with the same port p on a new
             * connection c2, trying to cache the endpoint, but sees the old
             * yet-to-be-removed endpoint.
             *
             * Close c1 again just to make sure we close it properly.
             */
            existingEndpoint.shutdown(
                "A rare race occured that " +
                "a connection is reconnected with the same port " +
                "before the previous connection cleans up", true);
        }
    }

    /**
     * Removes the responder endpoint from our map.
     */
    public void invalidateResponderEndpoint(
                    AbstractResponderEndpoint endpoint) {

        checkNull("endpoint", endpoint);
        final ResponderKey key = new ResponderKey(
            endpoint.getRemoteAddress(), endpoint.getListenerConfig());
        /*
         * It is possible that we are doing a delayed removal of an endpoint.
         * See the events described in #cacheResponderEndpoint. Therefore,
         * remove only if it is the endpoint in the map.
         */
        responders.remove(key, endpoint);
    }

    /**
     * Listens for incoming async connections to respond to the specified type
     * of dialogs.
     */
    @Override
    public synchronized EndpointGroup.ListenHandle
        listen(ListenerConfig listenerConfig,
               int dialogType,
               DialogHandlerFactory handlerFactory)
        throws IOException {

        checkNull("listenerConfig", listenerConfig);
        checkNull("handlerFactory", handlerFactory);
        AbstractListener listener = getListener(listenerConfig);
        return listener.newListenHandle(dialogType, handlerFactory);
    }

    /**
     * Listens for incoming sync connections.
     *
     * The method is synchronized on this class, therefore, it should not be
     * called in a critical path that requires high concurrency.
     */
    @Override
    public synchronized EndpointGroup.ListenHandle
        listen(ListenerConfig listenerConfig,
               SocketPrepared socketPrepared)
        throws IOException {

        checkNull("listenerConfig", listenerConfig);
        checkNull("socketPrepared", socketPrepared);
        AbstractListener listener = getListener(listenerConfig);
        return listener.newListenHandle(socketPrepared);
    }

    /**
     * Shuts down all endpoints and listening channels in the group.
     */
    @Override
    public void shutdown(boolean force) {
        isShutdown = true;
        /*
         * We want to make sure all creator endpoints are shut down.
         *
         * Since isShutdown is volatile, the above set statement creates a
         * memory barrier. After each put of the endpoints, there is a read on
         * isShutdown. If isShutdown is false, the put will be visible by the
         * following enumeration and the endpoint/listener is shutdown in the
         * enumeration. Otherwise, the endpoint is shut down in the get
         * methods.
         */
        while (!creators.isEmpty()) {
            final Iterator<AbstractCreatorEndpoint> iter =
                creators.values().iterator();
            if (iter.hasNext()) {
                final AbstractCreatorEndpoint endpoint = iter.next();
                iter.remove();
                endpoint.shutdown("Endpoint group is shut down", force);
            }
        }
        /*
         * Synchronization block for listeners operation. We do not need to
         * shut down responder endpoints, they are shut down by the listeners.
         */
        synchronized(this) {
            for (AbstractListener listener :
                    new ArrayList<>(listeners.values())) {
                listener.shutdown();
            }
            listeners.clear();
        }

        shutdownInternal(force);
    }

    @Override
    public boolean getIsShutdown() {
        return isShutdown;
    }

    /**
     * Removes a listener.
     */
    void removeListener(AbstractListener listener) {
        assert Thread.holdsLock(this);
        listeners.remove(listener.getListenerConfig());
    }

    /* Abstract methods. */

    /**
     * Creates a creator endpoint.
     */
    protected abstract AbstractCreatorEndpoint newCreatorEndpoint(
        String perfName,
        NetworkAddress remoteAddress,
        NetworkAddress localAddress,
        EndpointConfig endpointConfig);

    /**
     * Creates a listener.
     */
    protected abstract AbstractListener newListener(
        AbstractEndpointGroup endpointGroup,
        ListenerConfig listenerConfig,
        Map<Integer, DialogHandlerFactory> listenerDialogHandlerFactories);

    /**
     * Shuts down the actual implementation and cleans up
     * implementation-dependent resources (e.g., the executor thread pool).
     */
    protected abstract void shutdownInternal(boolean force);

    /* Private implementation methods */

    /**
     * Gets a listener if exists, creates and adds otherwise.
     */
    private AbstractListener getListener(ListenerConfig listenerConfig) {
        assert Thread.holdsLock(this);
        if (isShutdown) {
            throw new IllegalStateException(
                    "Endpoint group is already shut down");
        }
        Map<Integer, DialogHandlerFactory> listenerDialogHandlerFactories =
            listenerDialogHandlerFactoryMap.get(listenerConfig);
        if (listenerDialogHandlerFactories == null) {
            listenerDialogHandlerFactories =
                new ConcurrentHashMap<>();
        }
        AbstractListener listener = listeners.get(listenerConfig);
        if (listener == null) {
            listener = newListener(
                    this, listenerConfig, listenerDialogHandlerFactories);
            listeners.put(listenerConfig, listener);
        }
        return listener;
    }

    /* Key classes */

    /*
     * Note CreatorKey does not embody localAddress, since we would not use
     * different local addresses to talk to the same remoteAddress endpoint.
     * The KVStoreConfig allows for exactly one localAddr specification for all
     * KV traffic.
     */
    private class CreatorKey {

        private final NetworkAddress remoteAddress;
        private final EndpointConfig endpointConfig;

        public CreatorKey(NetworkAddress remoteAddress,
                          EndpointConfig endpointConfig) {
            this.remoteAddress = remoteAddress;
            this.endpointConfig = endpointConfig;
        }

        /**
         * Compares endpoints based on peer address and channel factory.
         */
        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof CreatorKey)) {
                return false;
            }
            CreatorKey that = (CreatorKey) obj;
            return (this.remoteAddress.equals(that.remoteAddress) &&
                    this.endpointConfig.equals(that.endpointConfig));
        }

        /**
         * Returns hashcode for the endpoint based on peer address and channel
         * factory.
         */
        @Override
        public int hashCode() {
            return 37 * remoteAddress.hashCode() +
                endpointConfig.hashCode();
        }
    }

    private class ResponderKey {

        private final NetworkAddress remoteAddress;
        private final ListenerConfig listenerConfig;

        public ResponderKey(NetworkAddress remoteAddress,
                            ListenerConfig listenerConfig) {
            this.remoteAddress = remoteAddress;
            this.listenerConfig = listenerConfig;
        }

        /**
         * Compares endpoints based on peer address and channel factory.
         */
        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof ResponderKey)) {
                return false;
            }
            ResponderKey that = (ResponderKey) obj;
            return (this.remoteAddress.equals(that.remoteAddress) &&
                    this.listenerConfig.equals(that.listenerConfig));
        }

        /**
         * Returns hashcode for the endpoint based on peer address and channel
         * factory.
         */
        @Override
        public int hashCode() {
            return 37 * remoteAddress.hashCode() +
                listenerConfig.hashCode();
        }
    }

    /**
     * Shuts down all creator endpoint handlers for testing.
     */
    public void shutdownCreatorEndpointHandlers(String detail, boolean force) {
        creators.values().forEach((c) -> c.shutdownHandler(detail, force));
    }
}
