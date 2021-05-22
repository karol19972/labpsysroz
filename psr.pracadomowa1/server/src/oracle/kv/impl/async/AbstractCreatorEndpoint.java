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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import oracle.kv.impl.async.exception.ConnectionEndpointShutdownException;
import oracle.kv.impl.async.exception.ConnectionUnknownException;
import oracle.kv.impl.async.exception.InitialConnectIOException;
import oracle.kv.impl.async.perf.EndpointGroupPerfTracker;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract class for a creator endpoint.
 */
public abstract class AbstractCreatorEndpoint
    implements CreatorEndpoint, EndpointHandlerManager {

    /**
     * If set, called on entrance to startDialog with the dialog type as an
     * argument.
     */
    public static volatile @Nullable TestHook<Integer> startDialogHook;
    public static volatile @Nullable TestHook<Void> connectHook;

    private final AbstractEndpointGroup endpointGroup;
    protected final String perfName;
    protected final NetworkAddress remoteAddress;
    protected final NetworkAddress localAddress;
    protected final EndpointConfig endpointConfig;
    /*
     * The dialog types enabled for responding. Endpoint handlers of this
     * endpoint (e.g., future handlers) share this map. Use concurrent hash map
     * for thread-safety.
     */
    private final Map<Integer, DialogHandlerFactory> dialogHandlerFactories =
        new ConcurrentHashMap<>();
    /*
     * The reference to the endpoint handler. The handler may die due to errors
     * and flip the reference to null.
     *
     * Currently only support one handler for the endpoint. In the future, we
     * may create more handlers to exploit more network bandwidth.
     */
    private final AtomicReference<EndpointHandler> handlerRef =
        new AtomicReference<>(null);
    /*
     * Indicates whether the endpoint is shut down. The endpoint is shut down
     * because the parent endpoint group is shut down. Once set to true, it
     * will never be false again. All access should be inside a synchronization
     * block of this object.
     */
    private boolean isShutdown = false;

    protected AbstractCreatorEndpoint(AbstractEndpointGroup endpointGroup,
                                      String perfName,
                                      NetworkAddress remoteAddress,
                                      NetworkAddress localAddress,
                                      EndpointConfig endpointConfig) {
        checkNull("endpointGroup", endpointGroup);
        checkNull("perfName", perfName);
        checkNull("remoteAddress", remoteAddress);
        checkNull("localAddress", localAddress);
        checkNull("endpointConfig", endpointConfig);
        this.endpointGroup = endpointGroup;
        this.perfName = perfName;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.endpointConfig = endpointConfig;
    }

    /**
     * Starts a dialog.
     */
    @Override
    public void startDialog(int dialogType,
                            DialogHandler dialogHandler,
                            long timeoutMillis) {

        assert TestHookExecute.doHookIfSet(startDialogHook, dialogType);

        checkNull("dialogHandler", dialogHandler);
        EndpointHandler handler = handlerRef.get();
        if (handler != null) {
            handler.startDialog(
                dialogType, dialogHandler, timeoutMillis);
            return;
        }
        synchronized(this) {
            if (isShutdown) {
                NullDialogStart.fail(
                    dialogHandler,
                    (new ConnectionEndpointShutdownException(
                             false, "endpoint already shutdown")).
                    getDialogException(false),
                    endpointGroup.getSchedExecService());
                return;
            }
            try {
                assert TestHookExecute.doHookIfSet(connectHook, null);
                handler = getOrConnect();
            } catch (IOException e) {
                NullDialogStart.fail(
                    dialogHandler,
                    new InitialConnectIOException(e, remoteAddress).
                    getDialogException(false),
                    NullDialogStart.IN_PLACE_EXECUTE_EXECUTOR);
                return;
            } catch (Throwable t) {
                NullDialogStart.fail(
                    dialogHandler,
                    new ConnectionUnknownException(t)
                    .getDialogException(false),
                    NullDialogStart.IN_PLACE_EXECUTE_EXECUTOR);
                return;
            }
        }
        handler.startDialog(dialogType, dialogHandler, timeoutMillis);
    }

    /**
     * Enables responding to a dialog type.
     */
    @Override
    public void enableResponding(int dialogType,
                                 DialogHandlerFactory factory) {
        checkNull("factory", factory);
        dialogHandlerFactories.put(dialogType, factory);
    }

    /**
     * Disables responding to a dialog type.
     */
    @Override
    public void disableResponding(int dialogType) {
        dialogHandlerFactories.remove(dialogType);
    }

    /**
     * Returns the endpoint group.
     */
    @Override
    public EndpointGroup getEndpointGroup() {
        return endpointGroup;
    }


    /**
     * Returns the network address of the remote endpoint.
     */
    @Override
    public NetworkAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Returns the channel factory of the endpoint.
     */
    @Override
    public EndpointConfig getEndpointConfig() {
        return endpointConfig;
    }

    /**
     * Returns the limit on the number of dialogs this endpoint can
     * concurrently start.
     */
    @Override
    public int getNumDialogsLimit() {
        final EndpointHandler handler = handlerRef.get();
        if (handler == null) {
            return -1;
        }
        return handler.getNumDialogsLimit();
    }

    /**
     * Cleans up when the handler is shutdown.
     *
     * Note that this method may be called more than once.
     */
    @Override
    public void onHandlerShutdown(EndpointHandler handler) {
        /*
         * Mark the endpoint shut down, so that no one can create new handlers.
         */
        synchronized(this) {
            isShutdown = true;
        }

        /* Remove the endpoint if it matches, else return the existing value */
        final EndpointHandler removeResult =
            handlerRef.updateAndGet(v -> handler.equals(v) ? null : v);
        if (removeResult != null) {
            /*
             * This should never happen since the handler should hold either
             * the correct reference or null.
             */
            throw new IllegalStateException(
                String.format("Wrong endpoint handler " +
                              "calling onHandlerShutdown " +
                              "for a creator endpoint, " +
                              "expected=%s, got=%s",
                              handler, removeResult));
        }

        endpointGroup.invalidateCreatorEndpoint(this);
    }


    @Override
    public EndpointGroupPerfTracker getEndpointGroupPerfTracker() {
        return endpointGroup.getEndpointGroupPerfTracker();
    }

    @Override
    public void shutdown(String detail, boolean force) {
        synchronized (this) {
            isShutdown = true;
        }
        final EndpointHandler handler = handlerRef.get();
        if (handler != null) {
            handler.shutdown(detail, force);
        }
    }

    /**
     * Returns the dialog handler factories.
     */
    public Map<Integer, DialogHandlerFactory> getDialogHandlerFactories() {
        return dialogHandlerFactories;
    }

    /**
     * Creates the endpoint handler.
     */
    protected abstract EndpointHandler newEndpointHandler() throws IOException;

    /**
     * Gets the endpoint handler if it exists or creates a new one.
     */
    private EndpointHandler getOrConnect() throws IOException {
        EndpointHandler handler = handlerRef.get();
        if (handler != null) {
            return handler;
        }
        handler = newEndpointHandler();
        if (!handlerRef.compareAndSet(null, handler)) {
            throw new AssertionError();
        }
        return handler;
    }

    /**
     * Returns the endpoint handler for testing.
     */
    public EndpointHandler getEndpointHandler() {
        return handlerRef.get();
    }

    /**
     * Shuts down the handler for testing.
     */
    public void shutdownHandler(String detail, boolean force) {
        final EndpointHandler handler = handlerRef.get();
        if (handler != null) {
            handler.shutdown(detail, force);
        }
    }

}
