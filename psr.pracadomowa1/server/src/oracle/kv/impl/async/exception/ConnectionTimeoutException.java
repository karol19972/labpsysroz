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

import java.io.IOException;

import oracle.kv.impl.async.AsyncOption;

/**
 * The exception is caused by the connection timed out due to
 * connection/handshake timeout (specified by {@link
 * AsyncOption#DLG_CONNECT_TIMEOUT}) or heartbeat timeout (specified by {@link
 * AsyncOption#DLG_HEARTBEAT_TIMEOUT}).
 */
public class ConnectionTimeoutException extends ConnectionException {

    private static final long serialVersionUID = 1L;

    private final boolean shouldBackoff;

    /**
     * Constructs the exception.
     *
     * @param fromRemote {@code true} if is aborted by the remote
     * @param shouldBackoff {@code true} if the caller should backoff before
     * retry on the same remote endpoint
     * @param message the message of the exception
     */
    public ConnectionTimeoutException(boolean fromRemote,
                                      boolean shouldBackoff,
                                      String message) {
        super(fromRemote, message, null);
        this.shouldBackoff = shouldBackoff;
    }

    /**
     * Returns {@code true} if should back off.
     */
    @Override
    public boolean shouldBackoff() {
        return shouldBackoff;
    }

    /**
     * Return an {@link IOException} to represent that the expected input was
     * not received.
     */
    @Override
    public Throwable getUserException() {
        assert getCause() == null;
        return new IOException(getMessage(), this);
    }

    /**
     * This exception is expected because it could be due to an expected
     * network failure or resource limitation.
     */
    @Override
    public boolean isExpectedException() {
        return true;
    }
}
