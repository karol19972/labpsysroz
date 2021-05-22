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
import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.util.ObjectUtil.checkNull;
import static oracle.kv.impl.util.SerializationUtil.readPackedInt;
import static oracle.kv.impl.util.SerializationUtil.readPackedLong;

import java.io.IOException;
import java.util.Formatter;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import oracle.kv.impl.async.AsyncVersionedRemote.AbstractGetSerialVersionCall;
import oracle.kv.impl.async.AsyncVersionedRemote.MethodCall;
import oracle.kv.impl.async.AsyncVersionedRemote.MethodOp;
import oracle.kv.impl.async.AsyncVersionedRemote.ResponseType;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.SerializeExceptionUtil;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Base class used to implement remote calls by responders (servers) for
 * asynchronous service interfaces. Subclasses should implement {@link
 * #getMethodOp} to identify the method and {@link #handleRequest
 * handleRequest} to supply the result of executing the request.
 *
 * @see AsyncVersionedRemote
 */
public abstract class AsyncVersionedRemoteDialogResponder
        extends AsyncBasicLogging
        implements DialogHandler {

    private static final String LOG_PREFIX = "async-remote-responder: ";

    private final DialogTypeFamily dialogTypeFamily;
    private volatile @Nullable DialogContext savedContext;
    private volatile @Nullable MessageOutput savedResponse;

    /** Creates an instance of this class. */
    public AsyncVersionedRemoteDialogResponder(
        DialogTypeFamily dialogTypeFamily, Logger logger) {

        super(logger);
        this.dialogTypeFamily =
            checkNull("dialogTypeFamily", dialogTypeFamily);
    }

    /**
     * Returns the {@link MethodOp} associated with an integer value.
     *
     * @param methodOpValue the integer value for the method operation
     * @return the method op
     * @throws IllegalArgumentException if the value is not found
     */
    protected abstract MethodOp getMethodOp(int methodOpValue);

    /**
     * Execute the request and arrange to write the response.
     *
     * @param serialVersion the serialVersion
     * @param methodCall the method call
     * @param timeoutMillis the dialog timeout
     * @param context the dialog context for the call
     */
    protected abstract void handleRequest(short serialVersion,
                                          MethodCall<?> methodCall,
                                          long timeoutMillis,
                                          DialogContext context);

    /* -- From DialogHandler -- */

    @Override
    public void onStart(DialogContext context, boolean aborted) {
        savedContext = context;
    }

    @Override
    public void onCanWrite(DialogContext context) {
        try {
            write();
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "Unexpected exception writing response" +
                           getDialogInfo(context) +
                           getExceptionLogging(e, WARNING));
            throw e;
        }
    }

    @Override
    public void onCanRead(DialogContext context, boolean finished) {
        MessageInput request = null;
        try {
            request = context.read();
            if (request == null) {
                return;
            }
            if (!finished) {
                throw new IllegalStateException(
                    "Expected request to be finished");
            }
            final int methodOpVal;
            try {
                methodOpVal = readPackedInt(request);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Unexpected problem reading method op: " + e, e);
            }
            final MethodOp methodOp = getMethodOp(methodOpVal);
            logger.finest(() -> String.format(LOG_PREFIX + "Started" +
                                              "%s" +
                                              " methodOp=%s",
                                              getDialogInfo(context),
                                              methodOp));
            final short serialVersion;
            final MethodCall<?> methodCall;
            final long timeoutMillis;
            try {
                serialVersion = request.readShort();
                methodCall = methodOp.readRequest(request, serialVersion);
                timeoutMillis = methodCall.includesTimeout() ?
                    methodCall.getTimeoutMillis() :
                    readPackedLong(request);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Problem deserializing request for " + methodOp + ": " + e,
                    e);
            }
            logger.finest(() -> String.format(LOG_PREFIX + "Handle request" +
                                              "%s" +
                                              " serialVersion=%d" +
                                              " methodCall=%s" +
                                              " timeoutMillis=%d",
                                              getDialogInfo(context),
                                              serialVersion,
                                              methodCall.describeCall(),
                                              timeoutMillis));
            handleRequest(serialVersion, methodCall, timeoutMillis, context);
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "Unexpected exception handling request" +
                           getDialogInfo(context) +
                           getExceptionLogging(e, WARNING));
            sendException(e, SerialVersion.MINIMUM);
            throw e;
        } finally {
            if (request != null) {
                request.discard();
            }
        }
    }

    @Override
    public void onAbort(DialogContext context, Throwable cause) {
        logger.fine(() -> LOG_PREFIX + "Aborted" + getDialogInfo(context) +
                    getExceptionLogging(cause));
    }

    /* -- From Object -- */

    @Override
    public String toString() {
        return String.format("%s@%x[dialogTypeFamily=%s]",
                             getAbbreviatedClassName(),
                             hashCode(),
                             dialogTypeFamily);
    }

    /* -- Other methods -- */

    /**
     * Creates a consumer that will send a response for the specified method.
     *
     * @param serialVersion the serialVersion
     * @param methodCall the method call
     */
    protected <R>
        BiConsumer<R, Throwable> sendResponse(short serialVersion,
                                              MethodCall<R> methodCall) {
        return (result, exception) ->
            sendResponse(serialVersion, methodCall, result, exception);
    }

    /**
     * Sends an exception response.
     *
     * @param exception the exception
     * @param serialVersion the serial version to use for communications
     */
    protected void sendException(Throwable exception, short serialVersion) {
        try {
            logger.fine(() -> LOG_PREFIX + "Failed" +
                        getDialogInfo(savedContext) +
                        getExceptionLogging(exception));
            final MessageOutput out = new MessageOutput();
            try {
                ResponseType.FAILURE.writeFastExternal(out, serialVersion);
                out.writeShort(serialVersion);
                SerializeExceptionUtil.writeException(exception, out,
                                                      serialVersion);

            } catch (IOException e) {
                throw new IllegalStateException(
                    "Unexpected exception while serializing exception: " + e,
                    e);
            }
            write(out);
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "Unexpected exception sending exception response" +
                           getDialogInfo(savedContext) +
                           getExceptionLogging(e, WARNING));
            throw e;
        }
    }

    /**
     * Helper method to get the local serial version in response to a remote
     * call to getSerialVersion. Requests the serial version from the server
     * and delivers the result to the original result handler.
     *
     * @param serialVersion the serial version of the initiator
     * @param methodCall the method call
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @param server the local server implementation
     */
    protected void getSerialVersion(short serialVersion,
                                    AbstractGetSerialVersionCall methodCall,
                                    long timeoutMillis,
                                    AsyncVersionedRemote server) {
        final BiConsumer<Short, Throwable> sendResponse =
            sendResponse(serialVersion, methodCall);
        if (serialVersion < SerialVersion.MINIMUM) {
            sendResponse.accept(
                null,
                SerialVersion.clientUnsupportedException(
                    serialVersion, SerialVersion.MINIMUM));
        } else {
            server.getSerialVersion(serialVersion, timeoutMillis)
                .whenComplete(unwrapExceptionVoid(sendResponse));
        }
    }

    /**
     * Returns the dialog context saved from the call to {@link #onStart}.  The
     * value should be non-null within the context of any callback method after
     * onStart.
     */
    protected @Nullable DialogContext getSavedDialogContext() {
        return savedContext;
    }

    /** Sends a response. */
    private <R> void sendResponse(short serialVersion,
                                  MethodCall<R> methodCall,
                                  @Nullable R result,
                                  @Nullable Throwable exception) {
        logger.finest(() -> String.format(
                          LOG_PREFIX + "Writing result" +
                          "%s" +
                          " methodCall=%s" +
                          " result=%s" +
                          "%s",
                          getDialogInfo(getSavedDialogContext()),
                          methodCall.describeCall(),
                          result,
                          getExceptionLogging(exception)));
        if (exception != null) {
            sendException(exception, serialVersion);
            return;
        }
        try {
            final MessageOutput response = new MessageOutput();
            try {
                ResponseType.SUCCESS.writeFastExternal(response,
                                                       serialVersion);
                response.writeShort(serialVersion);
                methodCall.writeResponse(result, response, serialVersion);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Unexpected exception: " + e, e);
            }
            write(response);
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "Unexpected exception writing response" +
                           getDialogInfo(getSavedDialogContext()) +
                           getExceptionLogging(e, WARNING));
            throw e;
        }
    }

    /**
     * Writes the specified response as a successful result.
     *
     * @param response the response
     * @throws IllegalStateException if there has already been an attempt to
     * write a response
     */
    private void write(MessageOutput response) {
        if (savedResponse != null) {
            throw new IllegalStateException(
                "Unexpected repeated attempt to write a response");
        }
        savedResponse = response;
        write();
    }

    /** Writes the saved response, if any. */
    private void write() {
        final MessageOutput sr = savedResponse;
        if (sr != null) {
            final DialogContext sc = checkNull("savedContext", savedContext);
            if (sc.write(sr, true /* finished */)) {
                logger.finest(() -> LOG_PREFIX + "Completed" +
                              getDialogInfo(sc));
                savedResponse = null;
            }
        }
    }

    /** Returns information to log about the current dialog. */
    private String getDialogInfo(@Nullable DialogContext context) {
        final StringBuilder sb = new StringBuilder();
        try (final Formatter fmt = new Formatter(sb)) {
            fmt.format(": responder=%s",
                       this);
            if (context != null) {
                fmt.format(" dialogId=%x:%x" +
                           " peer=%s",
                           context.getDialogId(),
                           context.getConnectionId(),
                           context.getRemoteAddress());
            }
            return sb.toString();
        }
    }
}
