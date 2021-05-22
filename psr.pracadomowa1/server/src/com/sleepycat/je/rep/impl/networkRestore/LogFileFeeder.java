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

package com.sleepycat.je.rep.impl.networkRestore;

import static com.sleepycat.je.utilint.VLSN.NULL_VLSN;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.networkRestore.FeederManager.Lease;
import com.sleepycat.je.rep.impl.networkRestore.Protocol.FeederInfoReq;
import com.sleepycat.je.rep.impl.networkRestore.Protocol.FileInfoReq;
import com.sleepycat.je.rep.impl.networkRestore.Protocol.FileInfoResp;
import com.sleepycat.je.rep.impl.networkRestore.Protocol.FileReq;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.net.DataChannel;
import com.sleepycat.je.rep.utilint.BinaryProtocol.ClientVersion;
import com.sleepycat.je.rep.utilint.BinaryProtocol.ProtocolException;
import com.sleepycat.je.rep.utilint.NamedChannel;
import com.sleepycat.je.rep.utilint.RepUtils;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.util.DbBackup;
import com.sleepycat.je.utilint.LogVerifier;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StoppableThread;

/**
 * The LogFileFeeder supplies log files to a client. There is one instance of
 * this class per client that's currently active. LogFileFeeders are created by
 * the FeederManager and exist for the duration of the session with the client.
 */
public class LogFileFeeder extends StoppableThread {

    /**
     * Time to wait for the next request from the client, 5 minutes.
     */
    private static final int SOCKET_TIMEOUT_MS = 5 * 60 * 1000;

    /*
     * 8K transfer size to take advantage of increasingly prevalent jumbo
     * frame sizes and to keep disk i/o contention to a minimum.
     */
    static final int TRANSFER_BYTES = 0x2000;

    /*
     * The parent FeederManager that creates and maintains LogFileFeeder
     * instances.
     */
    private final FeederManager feederManager;

    /* The channel on which the feeder communicates with the client. */
    private final NamedChannel namedChannel;

    /* The client node requesting the log files. */
    private int clientId;

    /*
     * The dbBackup instance that's used to manage the list of files that will
     * be transferred. It's used to ensure that a consistent set is transferred
     * over to the client. If an open dbBackup exists for the client, it's
     * established in the checkProtocol method immediately after the client has
     * been identified.
     */
    private DbBackup dbBackup = null;

    /* Used to compute a SHA256 during a transfer, or if client requests it. */
    final MessageDigest messageDigest;

    /* Logger shared with the FeederManager. */
    final private Logger logger;

    public LogFileFeeder(final FeederManager feederManager,
                         final DataChannel channel)
        throws DatabaseException {
        super(feederManager.getEnvImpl(), "Log File Feeder");

        this.feederManager = feederManager;
        logger = feederManager.logger;
        this.namedChannel = new NamedChannel(channel, feederManager.nameIdPair);

        final String algorithm = getHashAlgorithm();
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            LoggerUtils.severe(logger, feederManager.getEnvImpl(),
                               "The " + algorithm + " algorithm was not " +
                               "made available by the security provider");
            throw EnvironmentFailureException.unexpectedException(e);
        }
    }

    public void shutdown() {
        if (shutdownDone(logger)) {
            return;
        }

        shutdownThread(logger);
        feederManager.feeders.remove(clientId);
        LoggerUtils.info(logger, feederManager.getEnvImpl(),
                         "Log file feeder for client:" + clientId +
                         " is shutdown.");
    }

    @Override
    protected int initiateSoftShutdown() {
        /*
         * The feeder will get an I/O exception and exit, since it can't use
         * the channel after it has been closed.
         */
        RepUtils.shutdownChannel(namedChannel);
        return SOCKET_TIMEOUT_MS;
    }

    /**
     * The main driver loop that enforces the protocol message sequence and
     * implements it.
     */
    @Override
    public void run() {
        final NameIdPair remoteNameIdPair =
            new NameIdPair(RepUtils.getRemoteHost(namedChannel.getChannel()));
        try {
            configureChannel();
            final Protocol protocol = negotiateProtocol(remoteNameIdPair);
            if (protocol == null) {
                return; /* Server not compatible with client. */
            }
            checkFeeder(protocol, remoteNameIdPair);
            sendFileList(protocol);
            sendRequestedFiles(protocol);

            /* Done, cleanup */
            dbBackup.endBackup();
            dbBackup = null;
        } catch (ClosedByInterruptException e) {
            LoggerUtils.fine
                (logger, feederManager.getEnvImpl(),
                 "Ignoring ClosedByInterruptException normal shutdown");
        } catch (IOException e) {
            LoggerUtils.warning(logger, feederManager.getEnvImpl(),
                                " IO Exception: " + e.getMessage());
        } catch (ProtocolException e) {
            LoggerUtils.severe(logger, feederManager.getEnvImpl(),
                               " Protocol Exception: " + e.getMessage());
        } catch (Exception e) {
            throw new EnvironmentFailureException
                (feederManager.getEnvImpl(),
                 EnvironmentFailureReason.UNCAUGHT_EXCEPTION,
                 e);
        } finally {
            try {
                namedChannel.getChannel().close();
            } catch (IOException e) {
                LoggerUtils.warning(logger, feederManager.getEnvImpl(),
                                    "Log File feeder io exception on " +
                                    "channel close: " + e.getMessage());
            }
            shutdown();

            if (dbBackup != null) {
                if (feederManager.shutdown.get()) {
                    dbBackup.endBackup();
                } else {

                    /*
                     * Establish lease so client can resume within the lease
                     * period.
                     */
                    @SuppressWarnings("unused")
                    final Lease lease =
                        feederManager.new Lease(clientId,
                                                feederManager.leaseDuration,
                                                dbBackup);
                    LoggerUtils.info(logger, feederManager.getEnvImpl(),
                                     "Lease created for node: " + clientId);
                }
            }
            LoggerUtils.info
                (logger, feederManager.getEnvImpl(),
                 "Log file feeder for client: " + clientId + " exited");
        }
    }

    /**
     * Implements the message exchange used to determine whether this feeder
     * is suitable for use the client's backup needs. The feeder may be
     * unsuitable if it's already busy, or it's not current enough to service
     * the client's needs.
     */
    private void checkFeeder(final Protocol protocol,
                             final NameIdPair remoteNameIdPair)
        throws IOException, DatabaseException {

        protocol.read(namedChannel.getChannel(), FeederInfoReq.class);
        int feeders = feederManager.getActiveFeederCount() -
                      1 /* Exclude this one */;
        long rangeFirst = NULL_VLSN;
        long rangeLast = NULL_VLSN;
        if (feederManager.getEnvImpl() instanceof RepImpl) {
            /* Include replication stream feeders as a load component. */
            final RepImpl repImpl = (RepImpl) feederManager.getEnvImpl();
            feeders +=
                repImpl.getRepNode().feederManager().activeReplicaCount();
            final VLSNRange range = repImpl.getVLSNIndex().getRange();
            rangeFirst = range.getFirst();
            rangeLast = range.getLast();
        }
        final String msg =
            String.format("Network restore responding to node: %s" +
                " feeders:%,d" +
                " vlsn range:%,d-%,d",
                remoteNameIdPair, feeders,
                rangeFirst, rangeLast);
        LoggerUtils.info(logger, feederManager.getEnvImpl(), msg);
        protocol.write(
            protocol.new FeederInfoResp(
                feeders, rangeFirst, rangeLast, getProtocolLogVersion()),
            namedChannel);
    }

    /**
     * Send files in response to request messages. The request sequence looks
     * like the following:
     *
     *  [FileReq | FileInfoReq]+ Done
     *
     * The response sequence to a FileReq looks like:
     *
     *  FileStart <file byte stream> FileEnd
     *
     *  and that for a FileInfoReq, is simply a FileInfoResp
     */
    private void sendRequestedFiles(final Protocol protocol)
        throws IOException, ProtocolException, DatabaseException {

        String prevFileName = null;

        /* Loop until Done message causes ProtocolException. */
        while (true) {
            try {
                final FileReq fileReq = protocol.read(
                    namedChannel.getChannel(), FileReq.class);
                final String fileName = fileReq.getFileName();

                /*
                 * Calculate the full path for a specified log file name,
                 * especially when this Feeder is configured to run with sub
                 * directories.
                 */
                final FileManager fMgr =
                    feederManager.getEnvImpl().getFileManager();
                final File file = new File(fMgr.getFullFileName(fileName));

                if (!file.exists()) {
                    throw EnvironmentFailureException.unexpectedState
                        ("Log file not found: " + fileName);
                }
                /* Freeze the length and last modified date. */
                final long length = file.length();
                final long lastModified = file.lastModified();
                final byte[] digest;
                final FileInfoResp resp;
                Protocol.FileInfoResp cachedResp =
                    feederManager.statResponses.get(fileName);
                final byte[] cachedDigest =
                    ((cachedResp != null) &&
                     (cachedResp.getFileLength() == length) &&
                     (cachedResp.getLastModifiedTime() == lastModified)) ?
                    cachedResp.getDigestSHA256() : null;

                if (fileReq instanceof FileInfoReq) {
                    if  (cachedDigest != null) {
                        digest = cachedDigest;
                    } else if (((FileInfoReq) fileReq).getNeedSHA256()) {
                        digest = getSHA256Digest(file,
                            length, protocol.getHashAlgorithm()).digest();
                    } else {
                        // Digest not requested
                        digest = new byte[0];
                    }
                    resp = protocol.new FileInfoResp
                        (fileName, length, lastModified, digest);
                } else {
                    /* Allow deletion of previous file. */
                    if (prevFileName != null &&
                        !fileName.equals(prevFileName)) {
                        dbBackup.removeFileProtection(prevFileName);
                    }
                    prevFileName = fileName;

                    protocol.write(protocol.new FileStart
                                   (fileName, length, lastModified),
                                   namedChannel);
                    digest = sendFileContents(file, length);
                    if ((cachedDigest != null) &&
                         !Arrays.equals(cachedDigest, digest)) {
                        throw EnvironmentFailureException.unexpectedState
                            ("Inconsistent cached and computed digests");
                    }
                    resp = protocol.new FileEnd
                        (fileName, length, lastModified, digest);
                }
                /* Cache for subsequent requests, if it was computed. */
                if (digest.length > 0) {
                    feederManager.statResponses.put(fileName, resp);
                }
                protocol.write(resp, namedChannel);
            } catch (ProtocolException pe) {
                if (pe.getUnexpectedMessage() instanceof Protocol.Done) {
                    return;
                }
                throw pe;
            }
        }
    }

    /**
     * Returns the SHA256 has associated with the file.
     */
    static MessageDigest getSHA256Digest(
    final File file, final long length, final String algorithm)
        throws IOException, DatabaseException {

        final MessageDigest messageDigest;

        try {
            messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw EnvironmentFailureException.unexpectedException(e);
        }
        try (FileInputStream fileStream = new FileInputStream(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(TRANSFER_BYTES);
            for (long bytes = length; bytes > 0; ) {
                final int readSize = (int) Math.min(TRANSFER_BYTES, bytes);
                final int readBytes =
                    fileStream.read(buffer.array(), 0, readSize);
                if (readBytes == -1) {
                    throw new IOException("Premature EOF. Was expecting: " +
                        readSize);
                }
                messageDigest.update(buffer.array(), 0, readBytes);
                bytes -= readBytes;
            }
        }
        return messageDigest;
    }

    /**
     * Sends over the contents of the file and computes the SHA-1 hash. Note
     * that the method does not rely on EOF detection, but rather on the
     * promised file size, since the final log file might be growing while the
     * transfer is in progress. The client uses the length sent in the FileResp
     * message to maintain its position in the network stream. It expects to
     * see a FileInfoResp once it has read the agreed upon number of bytes.
     *
     * Since JE log files are append only, there is no danger that we will send
     * over any uninitialized file blocks.
     *
     * @param file the log file to be sent.
     * @param length the number of bytes to send
     * @return the digest associated with the file that was sent
     */
    private byte[] sendFileContents(final File file, final long length)
        throws IOException {

        final LogVerifier verifier =
            new LogVerifier(feederManager.getEnvImpl(), file.getName(), -1L);

        try (FileInputStream fileStream = new FileInputStream(file)) {
            final FileChannel fileChannel = fileStream.getChannel();
            messageDigest.reset();
            final ByteBuffer buffer =
                ByteBuffer.allocateDirect(TRANSFER_BYTES);
            final byte[] array =
                (buffer.hasArray()) ? buffer.array() : new byte[TRANSFER_BYTES];
            int transmitBytes = 0;

            while (true) {
                buffer.clear();
                if (fileChannel.read(buffer) < 0) {
                    verifier.verifyAtEof();
                    break;
                }

                buffer.flip();
                final int lim = buffer.limit();
                final int off;
                if (buffer.hasArray()) {
                    off = buffer.arrayOffset();
                } else {
                    off = 0;
                    buffer.get(array, 0, lim);
                    buffer.rewind();
                }
                verifier.verify(array, off, lim);
                messageDigest.update(array, off, lim);
                transmitBytes += namedChannel.getChannel().write(buffer);
            }

            if (transmitBytes != length) {
                String msg = "File length:" + length + " does not match the " +
                    "number of bytes that were transmitted:" +
                    transmitBytes;

                throw new IllegalStateException(msg);
            }

            final String msg =
                String.format("Sent file: %s Length:%,d bytes to client:%d",
                    file, length, clientId);
            LoggerUtils.info(logger, feederManager.getEnvImpl(), msg);
        }
        return messageDigest.digest();
    }

    /**
     * Processes the request for the list of files that constitute a valid
     * backup. If a leased DbBackup instance is available, it uses it,
     * otherwise it creates a new instance and uses it instead.
     */
    private void sendFileList(final Protocol protocol)
        throws IOException, ProtocolException, DatabaseException {

        /* Wait for the request message. */
        protocol.read(namedChannel.getChannel(), Protocol.FileListReq.class);

        if (dbBackup == null) {
            dbBackup = new DbBackup(feederManager.getEnvImpl());
            dbBackup.setNetworkRestore();
            dbBackup.startBackup();
        } else {
            feederManager.leaseRenewalCount++;
        }

        /*
         * Remove the subdirectory header of the log files, because the nodes
         * that need to copy those log files may not configure the spreading
         * log files into sub directories feature.
         */
        final String[] files = dbBackup.getLogFilesInBackupSet();
        for (int i = 0; i < files.length; i++) {
            if (files[i].contains(File.separator)) {
                files[i] = files[i].substring
                    (files[i].indexOf(File.separator) + 1);
            }
        }

        protocol.write(protocol.new FileListResp(files), namedChannel);
    }

    /**
     * Returns a protocol that is compatible, or null if no compatible version
     * is possible and the connection should simply be closed.
     */
    private Protocol negotiateProtocol(final NameIdPair remoteNameIdPair)
        throws IOException, ProtocolException {

        /* The initial protocol uses MIN_VERSION to support old clients. */
        Protocol protocol =
            makeProtocol(Protocol.MIN_VERSION, remoteNameIdPair);

        /* The client sends the highest version it supports. */
        final ClientVersion clientVersion =
            protocol.read(namedChannel.getChannel(),
                          Protocol.ClientVersion.class);
        clientId = clientVersion.getNodeId();

        final FeederManager.Lease lease = feederManager.leases.get(clientId);
        if (lease != null) {
            dbBackup = lease.terminate();
        }

        final LogFileFeeder prev = feederManager.feeders.put(clientId, this);
        if (prev != null) {
            final SocketAddress prevFeederAddress =
                prev.namedChannel.getChannel().getRemoteAddress();
            LoggerUtils.warning(logger, feederManager.getEnvImpl(),
                                "Log file feeder with client id:" + clientId +
                                " already present; originated from " +
                                prevFeederAddress +
                                " new connection originated from:" +
                                namedChannel.getChannel().getRemoteAddress());
        }

        if (clientVersion.getVersion() < Protocol.VERSION_3 &&
            getProtocolMaxVersion() >= Protocol.VERSION_3) {
            /*
             * VERSION_2 client does not support version negotiation and also
             * does not support this server's file format. The only way to
             * cause the client to remove this server from its list (so it
             * doesn't retry forever when there is no other compatible server)
             * is to close the connection by returning null here.
             *
             * For testing, however, we allow a V2 client when this provider
             * is configured for testing to use V2. This simulates the
             * behavior of V2 code.
             */
            LoggerUtils.warning(logger, feederManager.getEnvImpl(),
                "Cannot provide log file feeder for client id=" + clientId +
                    ". Client version=" + clientVersion.getVersion() +
                    " does not support server's log format and connection " +
                    "must be closed to cause rejection by older client.");
            return null;
        }

        /*
         * The client supports version negotiation. Use the highest version
         * allowed by client and server, that is at least the minimum version
         * supported by the server. If the client doesn't support this
         * version, it will not attempt to use the server.
         */
        protocol = makeProtocol(
            Math.max(
                Protocol.MIN_VERSION,
                Math.min(
                    getProtocolMaxVersion(),
                    clientVersion.getVersion())),
            remoteNameIdPair);

        protocol.write(protocol.new ServerVersion(), namedChannel);

        return protocol;
    }

    private Protocol makeProtocol(final int version,
                                  final NameIdPair remoteNameIdPair) {
        return new Protocol(
            feederManager.nameIdPair, remoteNameIdPair, version,
            feederManager.getEnvImpl());
    }

    /**
     * Sets up the channel to facilitate efficient transfer of large log files.
     */
    private void configureChannel()
        throws IOException {

        LoggerUtils.fine
            (logger, feederManager.getEnvImpl(),
             "Log File Feeder accepted connection from " + namedChannel);
        namedChannel.getChannel().socket().setSoTimeout(SOCKET_TIMEOUT_MS);

        /*
         * Enable Nagle's algorithm since throughput is important for the large
         * files we will be transferring.
         */
        namedChannel.getChannel().socket().setTcpNoDelay(false);
    }

    /**
     * @see StoppableThread#getLogger
     */
    @Override
    protected Logger getLogger() {
        return logger;
    }

    /**
     * Should be used instead of {@link Protocol#MAX_VERSION} to allow
     * overriding with {@link FeederManager#setTestProtocolMaxVersion}.
     */
    private int getProtocolMaxVersion() {
        final int testVersion = feederManager.getTestProtocolMaxVersion();
        return (testVersion != 0) ? testVersion : Protocol.MAX_VERSION;
    }

    /**
     * Should be used instead of {@link LogEntryType#LOG_VERSION} to allow
     * overriding with {@link FeederManager#setTestProtocolLogVersion}.
     */
    private int getProtocolLogVersion() {
        final int testVersion = feederManager.getTestProtocolLogVersion();
        return (testVersion != 0) ? testVersion : LogEntryType.LOG_VERSION;
    }

    /**
     * Gets the hash algorithm.  Is always SHA-256 in practice since log files
     * are not backwards compatible with older JE versions that expect a SHA1
     * hash, but SHA1 can be returned in testing when simulating talking to
     * older systems.  See {@link FeederManager#setTestProtocolMaxVersion}.
     * @return
     */
    private String getHashAlgorithm() {
        if (getProtocolMaxVersion() >= Protocol.VERSION_3) {
            return "SHA-256";
        }
        return "SHA1";
    }
}
