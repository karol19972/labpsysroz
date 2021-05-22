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
import static oracle.kv.impl.async.FutureUtils.checkedComplete;
import static oracle.kv.impl.async.FutureUtils.checkedCompleteExceptionally;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import oracle.kv.impl.async.AsyncVersionedRemote.MethodCall;
import oracle.kv.impl.async.AsyncVersionedRemote.ResponseType;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.SerializeExceptionUtil;

/**
 * Dialog handler used by initiators (clients) for asynchronous service
 * interfaces.
 *
 * @param <R> the type of the dialog result
 */
class AsyncVersionedRemoteDialogInitiator<R> extends AsyncBasicLogging
        implements DialogHandler {

    private static final String LOG_PREFIX = "async-remote-initiator: ";

    private final MessageOutput request;
    private final MethodCall<R> methodCall;
    private final CompletableFuture<R> future;
    private volatile State state = State.BEFORE_ON_START;

    enum State { BEFORE_ON_START, BEFORE_WRITE, BEFORE_READ, DONE; }

    /**
     * Creates an instance of this class.  The request parameter should already
     * have all of the data for the method invocation written to it.
     *
     * @param request the output stream for the request
     * @param logger for debug logging
     * @param methodCall for reading the result
     * @param future the future to accept the result
     */
    AsyncVersionedRemoteDialogInitiator(MessageOutput request,
                                        Logger logger,
                                        MethodCall<R> methodCall,
                                        CompletableFuture<R> future) {
        super(logger);
        this.request = checkNull("request", request);
        this.methodCall = checkNull("methodCall", methodCall);
        this.future = checkNull("future", future);
    }

    /* -- From DialogHandler -- */

    @Override
    public void onStart(DialogContext context, boolean aborted) {
        logger.finest(() -> LOG_PREFIX + "Dialog start" +
                      getDialogInfo(context));
        try {
            if (aborted) {
                logger.finest(() -> LOG_PREFIX + "Dialog end: Start aborted" +
                              getDialogInfo(context));
                return;
            }
            if (state != State.BEFORE_ON_START) {
                throw new IllegalStateException(
                    "Expected state BEFORE_ON_START, was " + state);
            }
            state = State.BEFORE_WRITE;
            if (context.write(request, true /* finished */)) {
                state = State.BEFORE_READ;
            }
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX + "Dialog end:" +
                           " Unexpected exception starting request" +
                           getDialogInfo(context) +
                           getExceptionLogging(e, WARNING));
            checkedCompleteExceptionally(future, e, logger);
            throw e;
        }
    }

    @Override
    public void onCanWrite(DialogContext context) {
        try {
            if (state != State.BEFORE_WRITE) {
                throw new IllegalStateException(
                    "Expected state BEFORE_WRITE, was " + state);
            }
            if (context.write(request, true /* finished */)) {
                state = State.BEFORE_READ;
            }
            logger.finest(() -> LOG_PREFIX + "Write" + getDialogInfo(context));
        } catch (Throwable e) {
            logger.warning(LOG_PREFIX +
                           "Unexpected exception writing request" +
                           getDialogInfo(context) +
                           getExceptionLogging(e, WARNING));
            checkedCompleteExceptionally(future, e, logger);
            throw e;
        }
    }

    @Override
    public void onCanRead(DialogContext context, boolean finished) {
        MessageInput response = null;
        try {
            if (state != State.BEFORE_READ) {
                throw new IllegalStateException(
                    "Expected state BEFORE_READ, was " + state);
            }
            if (!finished) {
                throw new IllegalStateException("Only expect one response");
            }
            response = context.read();
            if (response == null) {
                throw new IllegalStateException("Unexpected null read");
            }
            state = State.DONE;
            final ResponseType responseType =
                ResponseType.readFastExternal(response, SerialVersion.MINIMUM);
            switch (responseType) {
            case SUCCESS: {
                final short serialVersion = response.readShort();
                final R result =
                    methodCall.readResponse(response, serialVersion);
                logger.finest(() -> String.format(
                                  LOG_PREFIX + "Dialog end: Completed" +
                                  "%s" +
                                  " result=%s",
                                  getDialogInfo(context),
                                  result));
                checkedComplete(future, result, logger);
                break;
            }
            case FAILURE: {
                final short serialVersion = response.readShort();
                final Throwable exception =
                    SerializeExceptionUtil.readException(response,
                                                         serialVersion);
                logger.fine(() -> LOG_PREFIX + "Dialog end: Failed" +
                            getDialogInfo(context) +
                            getExceptionLogging(exception));
                checkedCompleteExceptionally(future, exception, logger);
                break;
            }
            default:
                throw new IllegalStateException("Unexpected response type: " +
                                                responseType);
            }
        } catch (Throwable e) {
            logger.warning(() -> LOG_PREFIX + "Dialog end:" +
                           " Unexpected exception reading response" +
                           getDialogInfo(context) +
                           getExceptionLogging(e, WARNING));
            checkedCompleteExceptionally(future, e, logger);
            try {
                throw e;
            } catch (IOException ioe) {
                throw new IllegalStateException(
                    "Unexpected exception: " + ioe, ioe);
            }
        } finally {
            if (response != null) {
                response.discard();
            }
        }
    }

    @Override
    public void onAbort(DialogContext context, Throwable exception) {
        state = State.DONE;
        logger.fine(() -> LOG_PREFIX + "Dialog end: Aborted" +
                    getDialogInfo(context) +
                    getExceptionLogging(exception));
        checkedCompleteExceptionally(future, exception, logger);
    }

    /* -- From Object -- */

    @Override
    public String toString() {
        return String.format("%s@%x[methodCall=%s]",
                             getAbbreviatedClassName(),
                             hashCode(),
                             methodCall.describeCall());
    }

    /* -- Other methods -- */

    private String getDialogInfo(DialogContext context) {
        return String.format(": dialogHandler=%s" +
                             " dialogId=%x:%x" +
                             " peer=%s" +
                             " state=%s",
                             this,
                             context.getDialogId(),
                             context.getConnectionId(),
                             context.getRemoteAddress(),
                             state);
    }
}
