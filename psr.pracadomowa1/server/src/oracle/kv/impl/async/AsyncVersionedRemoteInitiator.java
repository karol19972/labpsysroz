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

import static java.util.logging.Level.WARNING;
import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.util.ObjectUtil.checkNull;
import static oracle.kv.impl.util.SerializationUtil.writePackedInt;
import static oracle.kv.impl.util.SerializationUtil.writePackedLong;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.RequestTimeoutException;

/**
 * Base class used to implement remote calls by initiators (clients) of
 * asynchronous service interfaces. Subclasses should register the associated
 * dialog type family in {@link StandardDialogTypeFamily}, implement {@link
 * #getSerialVersionCall getSerialVersionCall} to support the implementation of
 * {@link #getSerialVersion getSerialVersion}, and implement other service
 * methods by calling {@link #startDialog startDialog} with method-specific
 * implementations of {@link AsyncVersionedRemote.MethodCall}.
 *
 * @see AsyncVersionedRemote
 */
public abstract class AsyncVersionedRemoteInitiator
        extends AsyncBasicLogging
        implements AsyncVersionedRemote {

    private static final String LOG_PREFIX = "async-remote-initiator: ";

    protected final CreatorEndpoint endpoint;
    protected final DialogType dialogType;

    /**
     * Creates an instance of this class.
     *
     * @param endpoint the associated endpoint
     * @param dialogType the dialog type
     * @param logger the logger
     */
    protected AsyncVersionedRemoteInitiator(CreatorEndpoint endpoint,
                                            DialogType dialogType,
                                            Logger logger) {
        super(logger);
        this.endpoint = checkNull("endpoint", endpoint);
        this.dialogType = checkNull("dialogType", dialogType);
    }

    /**
     * Returns the method call object that should be used to implement {@link
     * #getSerialVersion} calls.
     */
    protected abstract AbstractGetSerialVersionCall getSerialVersionCall();

    @Override
    public CompletableFuture<Short> getSerialVersion(final short serialVersion,
                                                     final long timeoutMillis)
    {
        return startDialog(serialVersion, getSerialVersionCall(),
                           timeoutMillis);
    }

    /**
     * Starts a dialog to perform a asynchronous remote method call.
     *
     * @param <T> the response type of the call
     * @param serialVersion the serial version
     * @param methodCall for marshaling and unmarshaling the request and
     * response
     * @param timeoutMillis the dialog timeout
     * @return a future that returns the response
     */
    protected <T> CompletableFuture<T>
        startDialog(final short serialVersion,
                    final MethodCall<T> methodCall,
                    final long timeoutMillis) {
        final Supplier<String> dialogInfo =
            () -> String.format(": initiator=%s" +
                                " serialVersion=%d" +
                                " methodCall=%s" +
                                " timeoutMillis=%s",
                                this,
                                serialVersion,
                                methodCall.describeCall(),
                                timeoutMillis);
        logger.finest(() -> LOG_PREFIX + "Start call" + dialogInfo.get());
        if (timeoutMillis <= 0) {
            logger.finest(() -> LOG_PREFIX + "End call: Timed out" +
                          dialogInfo.get());
            return failedFuture(
                new RequestTimeoutException(
                    (int) timeoutMillis, "Request timed out", null, false));
        }

        final CompletableFuture<T> future = new CompletableFuture<>();
        final DialogHandler dialogHandler;
        try {

            /*
             * Write the call data: method op, serial version, parameters, and
             * timeout
             */
            final MessageOutput out = new MessageOutput();
            try {
                writePackedInt(out, methodCall.getMethodOp().getValue());
                out.writeShort(serialVersion);
                methodCall.writeFastExternal(out, serialVersion);
                if (!methodCall.includesTimeout()) {
                    writePackedLong(out, timeoutMillis);
                }
            } catch (IOException e) {
                logger.fine(() -> LOG_PREFIX +
                            "End call: Serialization failed" +
                            dialogInfo.get() + getExceptionLogging(e));
                throw new IllegalStateException(
                    "Unexpected problem writing request: " + e, e);
            }
            dialogHandler = new AsyncVersionedRemoteDialogInitiator<>(
                out, logger, methodCall, future);
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "End call: Unexpected exception starting request" +
                           dialogInfo.get() +
                           getExceptionLogging(e, WARNING));
            return failedFuture(e);
        }
        try {
            logger.finest(() -> LOG_PREFIX + "Starting dialog" +
                          dialogInfo.get() +
                          " dialogHandler=" + dialogHandler);
            endpoint.startDialog(dialogType.getDialogTypeId(),
                                 dialogHandler, timeoutMillis);
            if (logger.isLoggable(Level.FINE)) {
                future.whenComplete(
                    unwrapExceptionVoid(
                        (result, e) ->
                        logger.log((e == null) ? Level.FINE : Level.FINEST,
                                   LOG_PREFIX + "End call" + dialogInfo.get() +
                                   " dialogHandler=" + dialogHandler +
                                   " result=" + result +
                                   getExceptionLogging(e))));
            }
            return future;
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "End call: Unexpected exception starting request" +
                           dialogInfo.get() +
                           " dialogHandler=" + dialogHandler +
                           getExceptionLogging(e, WARNING));
            return failedFuture(e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s@%x[endpoint=%s, dialogType=%s]",
                             getAbbreviatedClassName(),
                             hashCode(),
                             endpoint,
                             dialogType);
    }
}
