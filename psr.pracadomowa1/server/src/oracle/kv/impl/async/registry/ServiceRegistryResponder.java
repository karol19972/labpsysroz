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

import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import oracle.kv.UnauthorizedException;
import oracle.kv.impl.async.AsyncVersionedRemote.MethodCall;
import oracle.kv.impl.async.AsyncVersionedRemote.MethodOp;
import oracle.kv.impl.async.AsyncVersionedRemoteDialogResponder;
import oracle.kv.impl.async.DialogContext;
import oracle.kv.impl.async.NetworkAddress;
import oracle.kv.impl.async.StandardDialogTypeFamily;
import oracle.kv.impl.async.registry.ServiceRegistry.BindCall;
import oracle.kv.impl.async.registry.ServiceRegistry.GetSerialVersionCall;
import oracle.kv.impl.async.registry.ServiceRegistry.ListCall;
import oracle.kv.impl.async.registry.ServiceRegistry.LookupCall;
import oracle.kv.impl.async.registry.ServiceRegistry.RegistryMethodOp;
import oracle.kv.impl.async.registry.ServiceRegistry.UnbindCall;

/**
 * A responder (server-side) dialog handler for {@link ServiceRegistry}
 * dialogs.  As with the standard RMI registry, this implementation rejects
 * modifications to the registry unless the caller is on the same host as the
 * server.
 *
 * @see ServiceRegistryImpl
 */
class ServiceRegistryResponder
        extends AsyncVersionedRemoteDialogResponder {

    /**
     * A set of network addresses that are known to be the local host.  Used to
     * enforce local access for modify operations.
     */
    private static final Set<InetAddress> checkedLocalAddresses =
        Collections.synchronizedSet(new HashSet<>());

    private final ServiceRegistry server;

    ServiceRegistryResponder(ServiceRegistry server,
                             Logger logger) {
        super(StandardDialogTypeFamily.SERVICE_REGISTRY, logger);
        this.server = server;
    }

    @Override
    protected MethodOp getMethodOp(int methodOpValue) {
        return RegistryMethodOp.valueOf(methodOpValue);
    }

    @Override
    protected void handleRequest(final short serialVersion,
                                 final MethodCall<?> methodCall,
                                 final long timeoutMillis,
                                 final DialogContext contextIgnore) {
        final RegistryMethodOp methodOp =
            (RegistryMethodOp) methodCall.getMethodOp();
        switch (methodOp) {
        case GET_SERIAL_VERSION:
            getSerialVersion(serialVersion, (GetSerialVersionCall) methodCall,
                             timeoutMillis, server);
            break;
        case LOOKUP:
            lookup(serialVersion, (LookupCall) methodCall, timeoutMillis);
            break;
        case BIND:
            bind(serialVersion, (BindCall) methodCall, timeoutMillis);
            break;
        case UNBIND:
            unbind(serialVersion, (UnbindCall) methodCall, timeoutMillis);
            break;
        case LIST:
            list(serialVersion, (ListCall) methodCall, timeoutMillis);
            break;
        default:
            throw new IllegalArgumentException(
                "Unexpected method op: " + methodOp);
        }
    }

    /**
     * Check that the specified modify operation is permitted.  This
     * implementation sends an UnauthorizedException if the remote connection
     * is not being made by the local host.  This behavior is inspired by the
     * implementation of the RMI registry.
     *
     * @param serialVersion the serial version to use for communications
     * @param method the method being called
     * @return whether the check passed
     */
    private boolean checkAccess(short serialVersion,
                                String method) {
        final DialogContext context =
            checkNull("savedDialogContext", getSavedDialogContext());
        final NetworkAddress remoteAddress = context.getRemoteAddress();
        final InetAddress inetAddress;
        try {
            inetAddress = remoteAddress.getInetAddress();
        } catch (UnknownHostException e) {
            sendException(new UnauthorizedException(
                              "Call to ServiceRegistry." + method +
                              " is unauthorized: caller host is unknown",
                              e),
                          serialVersion);
            return false;
        }
        if (checkedLocalAddresses.contains(inetAddress)) {
            return true;
        }
        if (inetAddress.isAnyLocalAddress()) {
            sendException(new UnauthorizedException(
                              "Call to ServiceRegistry." + method +
                              " is unauthorized: caller address is unknown"),
                          serialVersion);
            return false;
        }
        try {
            new ServerSocket(0, 10, inetAddress).close();
            /* If we can bind to this address, then it is local */
            checkedLocalAddresses.add(inetAddress);
            return true;
        } catch (IOException e) {
            sendException(new UnauthorizedException(
                              "Call to ServiceRegistry." + method +
                              " is unauthorized: caller has non-local host",
                              e),
                          serialVersion);
            return false;
        }
    }

    private void lookup(final short serialVersion,
                        final LookupCall lookup,
                        final long timeoutMillis) {
        server.lookup(serialVersion, lookup.name, timeoutMillis)
            .whenComplete(
                unwrapExceptionVoid(sendResponse(serialVersion, lookup)));
    }

    private void bind(final short serialVersion,
                      final BindCall bind,
                      final long timeoutMillis) {
        if (!checkAccess(serialVersion, "bind")) {
            return;
        }
        server.bind(serialVersion, bind.name, bind.endpoint, timeoutMillis)
            .whenComplete(
                unwrapExceptionVoid(sendResponse(serialVersion, bind)));
    }

    private void unbind(final short serialVersion,
                        final UnbindCall unbind,
                        final long timeoutMillis) {
        if (!checkAccess(serialVersion, "unbind")) {
            return;
        }
        server.unbind(serialVersion, unbind.name, timeoutMillis)
            .whenComplete(
                unwrapExceptionVoid(sendResponse(serialVersion, unbind)));
    }

    private void list(final short serialVersion,
                      final ListCall list,
                      final long timeoutMillis) {
        server.list(serialVersion, timeoutMillis)
            .whenComplete(
                unwrapExceptionVoid(sendResponse(serialVersion, list)));
    }
}
