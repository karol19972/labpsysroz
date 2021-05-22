/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je.rep.utilint.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;


/**
 * A basic concrete extension of DataChannel.
 * This simply delegates operations directly to the underlying SocketChannel
 */
public class SimpleDataChannel extends AbstractDataChannel {

    /**
     * Constructor for general use.
     *
     * @param socketChannel A SocketChannel, which should be connected.
     */
    public SimpleDataChannel(SocketChannel socketChannel) {
        super(socketChannel);
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public boolean isTrusted() {
        return false;
    }

    @Override
    public boolean isTrustCapable() {
        return false;
    }

    /*
     * The following ByteChannel implementation methods delegate to the wrapped
     * channel object.
     */

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return socketChannel.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return socketChannel.read(dsts);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException {

        return socketChannel.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return socketChannel.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return socketChannel.write(srcs);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException {

        return socketChannel.write(srcs, offset, length);
    }

    @Override
    public boolean flush() {
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            ensureCloseForBlocking();
        } finally {
            socketChannel.close();
        }
    }

    @Override
    public boolean closeAsync() throws IOException {
        try {
            ensureCloseAsyncForNonBlocking();
        } finally {
            socketChannel.close();
        }
        return true;
    }

    @Override
    public void closeForcefully() throws IOException {
        socketChannel.close();
    }

    @Override
    public AsyncIO.ContinueAction getAsyncIOContinueAction(AsyncIO.Type type) {
        switch(type) {
        case READ:
            return AsyncIO.ContinueAction.WAIT_FOR_CHNL_READ;
        case WRITE:
            return AsyncIO.ContinueAction.WAIT_FOR_CHNL_WRITE_THEN_FLUSH;
        case FLUSH:
        case CLOSE:
            return AsyncIO.ContinueAction.RETRY_NOW;
        default:
            throw new IllegalStateException(
                          String.format(
                              "Unknown state for async IO operation: %s",
                              type));
        }
    }

    @Override
    public void executeTasks(ExecutorService executor, Runnable callback) {
        executor.submit(callback::run);
    }

    @Override
    public String toString() {
        return String.format(
                   "%s%s", addressPair, isBlocking() ? "BLK" : "NBL");
    }
}

