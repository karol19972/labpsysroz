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

package oracle.kv.impl.async.dialog.nio;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.async.AbstractEndpointGroup;
import oracle.kv.impl.async.AbstractListener;
import oracle.kv.impl.async.AsyncOption;
import oracle.kv.impl.async.DialogHandlerFactory;
import oracle.kv.impl.async.EndpointConfig;
import oracle.kv.impl.async.ListenerConfig;
import oracle.kv.impl.async.NetworkAddress;
import oracle.kv.impl.fault.AsyncEndpointGroupFaultHandler;
import oracle.kv.impl.param.ParameterState;
import oracle.kv.impl.util.CommonLoggerUtils;
import oracle.kv.impl.util.KVThreadFactory;

public class NioEndpointGroup extends AbstractEndpointGroup {

    /**
     * A backup executor standing by for when a designated executor becomes
     * unavailable.
     *
     * <p>The nio endpoint group manages its own thread pool to support all
     * asynchronous execution. It is possible that the thread pool is getting
     * shutdown with the endpoint group and some clean-up tasks need to be
     * finished in an asynchronous manner.  Furthermore, to actively reclaim
     * resources, the thread pool shuts down executors if it is idle for a long
     * period. Race can happen when the executor is being shut down and some
     * tasks appear. These two cases motivate this backup executor. That is,
     * with a backup executor always stands by, we can reduce the code
     * complexity to handle these rare corner cases.
     *
     * <p>Use a corePoolSize of 1 so that we have one active thread standing
     * by.
     *
     * <p>Tasks submitted to the backup executor should handle their
     * exceptions. Any exception thrown to the executor thread is silently
     * dropped.
     */
    public static volatile ScheduledExecutorService BACKUP_EXECUTOR =
        newBackupExecutor();

    private static ScheduledExecutorService newBackupExecutor() {
        return Executors.newScheduledThreadPool(
            1,
            new KVThreadFactory(
                String.format("%s.backup", NioEndpointGroup.class.getSimpleName()),
                /*
                 * We cannot obtain a logger here, the task should not
                 * throw any exception.
                 */
                null));
    }

    /*
     * The maximum number of consecutive IOExceptions occurred before we give
     * up and treats it as unrecoverable.  TODO: probably should make it a rate
     * rather than an absolute count.
     */
    private static final int MAX_NUM_CONSECUTIVE_IOEXCEPTION = 16;

    private final NioChannelThreadPool channelThreadPool;

    public NioEndpointGroup(Logger logger,
                            int nthreads,
                            int maxQuiescentSeconds,
                            int numPermits,
                            AsyncEndpointGroupFaultHandler faultHandler)
        throws Exception {
        super(logger, numPermits);
        this.channelThreadPool =
            new NioChannelThreadPool(
                logger, nthreads, maxQuiescentSeconds, faultHandler);
    }

    /* For testing */
    public NioEndpointGroup(Logger logger,
                            int nthreads)
        throws Exception {
        this(logger, nthreads,
             ParameterState
             .SN_ENDPOINT_GROUP_MAX_QUIESCENT_SECONDS_VALUE_DEFAULT,
             Integer.MAX_VALUE,
             AsyncEndpointGroupFaultHandler.DEFAULT);
    }

    @Override
    public ScheduledExecutorService getSchedExecService() {
        return channelThreadPool.next();
    }

    @Override
    protected NioCreatorEndpoint
        newCreatorEndpoint(String perfName,
                           NetworkAddress remoteAddress,
                           NetworkAddress localAddress,
                           EndpointConfig endpointConfig) {

        return new NioCreatorEndpoint(
            this, channelThreadPool, perfName,
            remoteAddress, localAddress, endpointConfig);
    }

    @Override
    protected NioListener newListener(AbstractEndpointGroup endpointGroup,
                                      ListenerConfig listenerConfig,
                                      Map<Integer, DialogHandlerFactory>
                                      dialogHandlerFactories) {

        return new NioListener(
                endpointGroup, listenerConfig, dialogHandlerFactories);
    }

    @Override
    protected void shutdownInternal(boolean force) {
        channelThreadPool.shutdown(force);
    }

    class NioListener extends AbstractListener {

        /*
         * Assigned inside the parent listening channel lock. Volatile for
         * read.
         */
        private volatile ServerSocketChannel listeningChannel = null;
        /* Assigned inside synchronization block. Volatile for read.  */
        private volatile NioChannelExecutor channelExecutor = null;
        private final NioChannelAccepter channelAccepter =
            new NioChannelAccepter();
        /*
         * A thread-local to prevent onAccept recursion. The recursion occurs
         * when a new endpoint assigning to a channel executor is rejected and
         * being retried during which clean-up occurs and triggers an onAccept.
         */
        private final ThreadLocal<Boolean> insideOnAccept =
            ThreadLocal.withInitial(() -> false);

        /*
         * A clean-up task to deal with a live-lock issue caused by the server
         * backlog and connection timeout.
         *
         * We de-register the server channel when there are too many
         * connections. Even if de-registered, there are still client endpoints
         * establishing connections which are queued up in the server backlog.
         * These connections may be timed-out before the server channel is
         * registered again. If these connections are later accepted but are
         * already timed-out at the client side, the server side may still need
         * certain amount of time to discover the client is disconnected during
         * which time newly queued-up connections may be timed out again in the
         * backlog. Hence resulting in a live-lock situation. We allieviate
         * this issue by periodically clean up the backlog if the server
         * channel is de-registered.
         */
        private final Runnable clearBacklogTask = new Runnable() {
            @Override
            public void run() {
                final ServerSocketChannel serverChannel = listeningChannel;
                if (serverChannel == null) {
                    return;
                }
                while (true) {
                    try {
                        final SocketChannel clientChannel =
                            serverChannel.accept();
                        if (clientChannel == null) {
                            break;
                        }
                        clientChannel.close();
                    } catch (IOException e) {
                        break;
                    }
                }
            }
        };
        /* Accessed by synchronized on clearBacklogTask */
        private Future<?> clearBacklogFuture = null;

        NioListener(AbstractEndpointGroup endpointGroup,
                    ListenerConfig listenerConfig,
                    Map<Integer, DialogHandlerFactory>
                    dialogHandlerFactories) {
            super(endpointGroup, listenerConfig, dialogHandlerFactories);
        }

        @Override
        public NetworkAddress getLocalAddressInternal() {
            ensureListeningChannelLockHeld();
            final ServerSocketChannel ch = listeningChannel;
            if (ch == null) {
                /*
                 * This method will not be called before the listening channel
                 * is created, so if the channel is null, then this means we are
                 * shutting down. Returns the any local address.
                 */
                return NetworkAddress.ANY_LOCAL_ADDRESS;
            }
            return NioUtil.getLocalAddress(listeningChannel);
        }

        /**
         * Creates the listening channel if not existing yet.
         *
         * The method is called inside a synchronization block of the parent
         * endpoint group.
         */
        @Override
        protected void createChannelInternal() throws IOException {
            ensureListeningChannelLockHeld();
            if (listeningChannel == null) {
                listeningChannel = NioUtil.listen(listenerConfig);
                try {
                    listeningChannel.configureBlocking(false);
                    channelExecutor = channelThreadPool.next();
                    channelExecutor.registerAcceptInterest(
                        listeningChannel, channelAccepter);
                } catch (IOException e) {
                    listeningChannel.close();
                    /*
                     * Something wrong with the channel so that we cannot
                     * register. Wake up the executor to be safe.
                     */
                    channelExecutor.wakeup();
                    listeningChannel = null;
                    throw e;
                }
            }
        }

        /**
         * Close the created listening channel.
         *
         * The method is called inside a synchronization block of the parent
         * endpoint group.
         */
        @Override
        protected void closeChannel() {
            if (listeningChannel == null) {
                return;
            }
            synchronized(clearBacklogTask) {
                if (clearBacklogFuture != null) {
                    clearBacklogFuture.cancel(false);
                }
            }
            try {
                listeningChannel.close();
                channelExecutor.wakeup();
            } catch (IOException e) {
                getLogger().log(Level.INFO,
                        "Error closing server channel: {0}", e);
            }
            listeningChannel = null;
            channelExecutor = null;
        }

        @Override
        public String toString() {
            return String.format(
                    "NioListener[listeningChannel=%s]", listeningChannel);
        }

        @Override
        protected boolean tryAccept() throws IOException {
            if (insideOnAccept.get()) {
                return false;
            }
            final ServerSocketChannel serverChannel = listeningChannel;
            if (serverChannel == null) {
                return false;
            }
            final SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                return false;
            }
            channelAccepter.onAccept(clientChannel);
            return true;
        }

        @Override
        protected void registerAccept() throws IOException {
            synchronized(clearBacklogTask) {
                if (clearBacklogFuture != null) {
                    clearBacklogFuture.cancel(false);
                    clearBacklogFuture = null;
                }
            }
            final ServerSocketChannel ch = listeningChannel;
            if (ch != null) {
                channelExecutor.registerAcceptInterest(ch, channelAccepter);
            }
        }

        @Override
        protected void deregisterAccept() throws IOException {
            final NioChannelExecutor executor = channelExecutor;
            if (executor == null) {
                return;
            }
            final ServerSocketChannel ch = listeningChannel;
            if (ch != null) {
                executor.deregister(ch);
            }
            synchronized(clearBacklogTask) {
                final int interval =
                    getListenerConfig().
                    getOption(AsyncOption.DLG_CLEAR_BACKLOG_INTERVAL);
                clearBacklogFuture =
                    executor.scheduleWithFixedDelay(
                        clearBacklogTask, interval, interval,
                        TimeUnit.MILLISECONDS);
            }
        }

        /**
         * Accepter for the listener.
         */
        class NioChannelAccepter implements ChannelAccepter {

            private final AtomicInteger numConsecutiveIOException =
                new AtomicInteger(0);

            @Override
            public void onAccept(final SocketChannel socketChannel) {

                insideOnAccept.set(true);
                try {
                    onAcceptInternal(socketChannel);
                } catch (IOException e) {
                    /*
                     * Cannot create a new endpoint for the channel, simply
                     * close the socket channel
                     */
                    try {
                        socketChannel.close();
                    } catch (Throwable t) {
                        getLogger().log(Level.INFO, () -> String.format(
                            "Error closing channel after IO Exception: %s", t));
                    }
                    final int count = numConsecutiveIOException.getAndIncrement();
                    if (count > MAX_NUM_CONSECUTIVE_IOEXCEPTION) {
                        cancel(e);
                        rethrowUnhandledError(e);
                    }
                } catch (Throwable t) {
                    /*
                     * Other kind of exceptions are fatal, we need to propagate
                     * the exception down to the channel executor which will
                     * restart the process. Call shutdown just to be clean.
                     */
                    cancel(t);
                    rethrowUnhandledError(t);
                } finally {
                    insideOnAccept.set(false);
                }
            }

            private void onAcceptInternal(final SocketChannel socketChannel)
                throws IOException {

                final Logger logger = getLogger();
                logger.log(Level.FINEST, () -> String.format(
                    "%s accepting a connection: %s",
                    getClass().getSimpleName(), socketChannel.socket()));

                NioUtil.configureSocketChannel(socketChannel, endpointConfig);
                final NetworkAddress remoteAddress =
                    NioUtil.getRemoteAddress(socketChannel);
                /*
                 * Creates the endpoint handler and assign it to an executor.
                 * Retries if the executor happens to be shutting itself down.
                 *
                 * TODO: there might be a common pattern here, consider move the
                 * logic of dealing with RejectedExecutionException to the
                 * NioChannelThreadPool for code sharing.
                 */
                while (true) {
                    final NioChannelExecutor executor =
                        channelThreadPool.next();
                    try {
                        startEndpoint(executor, remoteAddress, socketChannel);
                        break;
                    } catch (RejectedExecutionException e) {
                        if (channelThreadPool.isShutdown()) {
                            /* Shutting down, nothing to be done */
                            logger.log(Level.FINE, () -> String.format(
                                "%s cannot accept a connection " +
                                "due to shutdown: %s",
                                getClass().getSimpleName(),
                                socketChannel.socket()));
                            break;
                        }
                        if (!executor.isShutdownOrAfter()) {
                            logger.log(Level.WARNING, () -> String.format(
                                "Executor rejects execution " +
                                "but is not shut down\n" +
                                "%s\nerror=%s\n",
                                executor,
                                CommonLoggerUtils.getStackTrace(e)));
                        }
                        /*
                         * If the thread pool is not shut down, then it so
                         * happens that the executor is shutting down itself.
                         * Just try another executor.
                         */
                        continue;
                    }
                }
            }

            private void startEndpoint(NioChannelExecutor executor,
                                       NetworkAddress remoteAddress,
                                       SocketChannel socketChannel) {
                final NioResponderEndpoint endpoint =
                    new NioResponderEndpoint(
                        NioEndpointGroup.this, remoteAddress,
                        listenerConfig, NioListener.this,
                        endpointConfig, executor, socketChannel);
                cacheResponderEndpoint(endpoint);
                acceptResponderEndpoint(endpoint);
                try {
                    executor.execute(
                        () -> endpoint.getHandler().onConnected());
                } catch (RejectedExecutionException e) {
                    /*
                     * Shuts down the endpoint which will shuts down the
                     * handler and clears the references to this endpoint in
                     * the endpoint group.
                     */
                    endpoint.shutdown(
                        String.format(
                            "Rejected when scheduling " +
                            "handler onConnected: %s", e),
                        true);
                    throw e;
                } catch (Throwable t) {
                    final String msg = String.format(
                        "Unexpected exception " +
                        "when start responder endpoint: %s", t);
                    endpoint.shutdown(msg, true);
                    throw new IllegalStateException(msg, t);
                }
            }

            @Override
            public void cancel(Throwable t) {
                /* Notify the handlers about the error. */
                onChannelError(t, true /* mark as closed */);
            }
        }

    }

    /* For testing */

    /**
     * Shuts down the backup executor, waits for termination and restart the
     * executor.
     *
     * <p>
     * I want a method to wait for all tasks to finish in the back up executor,
     * but there is no convenient way to do that.
     */
    public static void awaitBackupExecutorQuiescence(long timeout,
                                                     TimeUnit unit)
        throws InterruptedException {

        BACKUP_EXECUTOR.shutdown();
        BACKUP_EXECUTOR.awaitTermination(timeout, unit);
        BACKUP_EXECUTOR = newBackupExecutor();
    }
}
