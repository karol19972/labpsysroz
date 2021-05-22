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

package oracle.kv.impl.async.exception;

import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import oracle.kv.impl.async.NetworkAddress;

/**
 * The exception is caused by IOException.
 */
public class ConnectionIOException extends ConnectionException {

    private static final long serialVersionUID = 1L;

    /** The remote address for the connection. */
    private final NetworkAddress remoteAddress;

    /** Whether to backoff on connection retries. */
    private boolean shouldBackoff;

    /**
     * Creates the exception based on the cause.
     *
     * @param cause the cause of the exception
     * @param remoteAddress the remote address of the connection
     */
    public ConnectionIOException(IOException cause,
                                 NetworkAddress remoteAddress) {
        this(cause, remoteAddress,
             (cause instanceof ConnectException) ? false :
             (cause instanceof UnknownHostException) ? true :
             false);
    }

    /**
     * Creates the exception based on the cause and specifying whether to
     * back off on connection retries.
     *
     * @param cause the cause of the exception
     * @param remoteAddress the remote address of the connection
     * @param shouldBackoff whether to back off on connection retries
     */
    public ConnectionIOException(IOException cause,
                                 NetworkAddress remoteAddress,
                                 boolean shouldBackoff) {
        super(false, cause.getMessage(), cause);
        this.remoteAddress = checkNull("remoteAddress", remoteAddress);
        this.shouldBackoff = shouldBackoff;
    }

    /**
     * Creates the exception representing that the remote aborts the connection
     * due to IO exception. For example, the remote side may abort the
     * conneciton due to missing heartbeat.
     *
     * @param cause the cause of the exception
     * @param remoteAddress the remote address of the connection
     */
    public ConnectionIOException(String cause,
                                 NetworkAddress remoteAddress) {
        super(true, cause, new IOException(cause));
        this.remoteAddress = checkNull("remoteAddress", remoteAddress);
        /*
         * We notify the dialog to backoff. The retionale is that since the
         * remote side usually choose to abort the connection, either because
         * itself or the network traffic is busy.
         */
        this.shouldBackoff = true;
    }

    /**
     * Returns the remote address of the connection
     *
     * @return the remote address of the connection
     */
    public NetworkAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean shouldBackoff() {
        return shouldBackoff;
    }

    /**
     * Add more information about the target address if the cause is a
     * {@link ConnectException}.
     */
    @Override
    public Throwable getUserException() {
        if (getCause() instanceof ConnectException) {
            final NetworkAddress address = getRemoteAddress();
            final ConnectException connectException =
                new ConnectException("Unable to connect to host " +
                                     address.getHostName() +
                                     ", port " + address.getPort());
           connectException.initCause(this);
           return connectException;
        }
        return super.getUserException();
    }
}
