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

import static oracle.kv.impl.async.FutureUtils.failedFuture;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import oracle.kv.impl.async.AsyncVersionedRemoteAPI;
import oracle.kv.impl.async.CreatorEndpoint;
import oracle.kv.impl.async.DialogType;

/**
 * API to make an asynchronous request on an RN.
 *
 * @see RequestHandlerAPI
 * @see AsyncRequestHandler
 */
public class AsyncRequestHandlerAPI extends AsyncVersionedRemoteAPI {

    private final AsyncRequestHandler remote;

    private AsyncRequestHandlerAPI(AsyncRequestHandler remote,
                                   short serialVersion) {
        super(serialVersion);
        this.remote = remote;
    }

    /**
     * Makes an asynchronous request to create an instance of this class,
     * returning the result as a future.
     *
     * @param endpoint the remote endpoint representing the server
     * @param dialogType the dialog type
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @param logger for debug logging
     * @return the future
     */
    public static CompletableFuture<AsyncRequestHandlerAPI>
        wrap(CreatorEndpoint endpoint,
             DialogType dialogType,
             long timeoutMillis,
             Logger logger) {
        try {
            final AsyncRequestHandler initiator =
                new AsyncRequestHandlerInitiator(endpoint, dialogType, logger);
            return computeSerialVersion(initiator, timeoutMillis)
                .thenApply(version ->
                           new AsyncRequestHandlerAPI(initiator, version));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Executes the request.
     *
     * @param request the request to be executed
     * @param timeoutMillis the remote execution timeout in milliseconds
     * @return a future that returns the result
     * @see AsyncRequestHandler#execute
     */
    public CompletableFuture<Response> execute(Request request,
                                               long timeoutMillis) {
        return remote.execute(request, timeoutMillis);
    }
}
