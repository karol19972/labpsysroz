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

package oracle.kv.util.expimp.utils.exp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.kv.impl.util.KVThreadFactory;
import oracle.kv.util.expimp.utils.Chunk;
import oracle.kv.util.expimp.utils.exp.CustomStream.CustomInputStream;
import oracle.kv.util.expimp.utils.exp.CustomStream.CustomOutputStream;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordBytes;

/**
 * The base class that manages writing data to export Store.
 */
public abstract class AbstractStoreOutput {
    /*
     * The size of the file segment stored in the export store set to 1GB
     */
    private static long maxFileSegmentSize = 1024 * 1024 * 1024;

    /*
     * This is the maximum number of finished WriteTasks. Limit it to
     * avoid running out of memory if the producer is faster than the consumer.
     * With this limit, the maximum number of CustomStreams that are referenced
     * at any point are:
     * - 1 in-use CustomStream per export type fanout (that is, one per table,
     *   other data, schemafiles)
     * - TASK_QUEUE_SIZE finished, flushed CustomStreams.
     */
    private static final int TASK_QUEUE_SIZE = 40;

    /*
     * Map that holds all the RecordStreams responsible for streaming
     * data/metadata from kvstore to external export store
     */
    private RecordStreamMap recordStreamMap;

    private final Map<String, FutureHolder> taskFutureMap;

    /*
     * Asynchronous thread pool
     */
    private final ExecutorService threadPool;

    /*
     * An ArrayBlockingQueue is used to synchronize the producer and consumers.
     * Holds the state of all the threads that are streaming the bytes from
     * Oracle NoSql store into the external export store.
     */
    BlockingQueue<FutureHolder> taskWaitQueue;

    /*
     * A utility thread that waits for tasks (stream data from source nosql
     * store to export store) to complete and aggregates status of the writer
     * threads for output and success/error reporting. If any task fails,
     * the taskWaiter will exit prematurely. The main thread must detect this.
     */
    Future<Boolean> taskWait;

    /*
     * Holds the state of all the threads that are streaming the lob bytes from
     * Oracle NoSql store into the external export store.
     */
    List<FutureHolder> lobTaskWaitList;

    protected Logger logger;

    public AbstractStoreOutput(Logger logger) {
        this.logger = logger;
        threadPool = Executors.newCachedThreadPool(
            new KVThreadFactory("Export", this.logger));
        taskFutureMap = new HashMap<String, FutureHolder>();
        lobTaskWaitList = new ArrayList<FutureHolder>(TASK_QUEUE_SIZE);
        reset();
    }

    public void reset() {
        recordStreamMap = new RecordStreamMap();
        taskWaitQueue = new ArrayBlockingQueue<FutureHolder>(TASK_QUEUE_SIZE);
        taskWait = threadPool.submit(new TaskWaiter());
    }

    public void write(RecordBytes rb) {
        write(rb.getFileName(), rb.getBytes());
        if (rb.isLob()) {
            writeLob(rb.getLobFileName(), rb.getLobStream());
        }
    }

    public void write(String fileName, byte[] bytes) {
        /*
         * Check if the schema bytes can be transferred to RecordStream
         */
        recordStreamMap.canAddRecord(fileName, bytes.length);
        /*
         * Stream the schema bytes to the external export store
         */
        recordStreamMap.insert(fileName, bytes);
    }

    private void writeLob(String lobFileName, InputStream lobStream) {
        Future<Boolean> future =
            threadPool.submit(new WriteLobTask(lobFileName, lobStream));
        lobTaskWaitList.add(new FutureHolder(future));
    }

    /**
     * Wait for all Worker Thread tasks that have been scheduled.
     */
    private Boolean waitForTasks() {

        logger.info("Waiting for WriteTasks threads to " +
                    "complete execution.");

        try {

            taskWaitQueue.put(new FutureHolder(null));
            taskWait.get();
        } catch (ExecutionException e) {
            logger.log(Level.SEVERE, "Exception waiting for WriteLobTask " +
                       "thread to complete execution", e);

            return false;
        } catch (InterruptedException ie) {
            logger.log(Level.SEVERE, "Exception waiting for WriteLobTask " +
                       "thread to complete execution", ie);

            return false;
        }

        return true;
    }

    public void waitForWriteTasksDone() {
        /*
         * LOB bytes are streamed to the export store in a separate thread.
         * Wait for these threads to complete transferring the LOB bytes to the
         * RecordStream.
         */
        try {
            waitForLobTasks();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception exporting the lob to " +
                       "external export store.", e);
        }
        /*
         * Flush any remaining bytes in the RecordStream.
         */
        recordStreamMap.flushAllStreams();
        /*
         * Wait for the worker threads to transfer all the bytes in the record
         * stream to the export store.
         */
        try {
            waitForTasks();
        } catch (RuntimeException re) {
            logger.log(Level.SEVERE, "Exception exporting the data to " +
                       "external export store. Halting export.", re);
            throw re;
        }
        recordStreamMap.performPostExportWork();
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void close() {
        if (threadPool != null) {
            threadPool.shutdown();
        }
    }

    /**
     * Wait for all the scheduled Threads transferring the LOB data bytes into
     * the RecordStream.
     */
    private Boolean waitForLobTasks()
            throws Exception {

        logger.info("Waiting for WriteLobTasks threads to " +
                    "complete execution.");

        for (FutureHolder holder : lobTaskWaitList) {
            Future<Boolean> future;

            if (holder == null || holder.getFuture() == null) {
                continue;
            }

            future = holder.getFuture();

            try {

                future.get();
            } catch (ExecutionException e) {

                logger.log(Level.SEVERE, "Exception waiting for WriteLobTask " +
                           "thread to complete execution", e);

                return false;
            } catch (InterruptedException ie) {

                logger.log(Level.SEVERE, "Exception waiting for WriteLobTask " +
                           "thread to complete execution", ie);

                return false;
            }
        }

        return true;
    }

    /**
     * Set size of the file segment. Used only for test purposes.
     */
    public static void setMaxFileSegmentSize(long testSize) {
        maxFileSegmentSize = testSize;
    }

    public static long getMaxFileSegmentSize() {
        return maxFileSegmentSize;
    }

    /**
     * This class holds all the RecordStreams. It contains methods which
     * transfer the record bytes to the RecordStream.
     */
    class RecordStreamMap {

        /*
         * Provides mapping between the name of file segment being exported and
         * the RecordStream which transfers the file bytes.
         */
        private final Map<String, RecordEntityInfo> recEntityMap;

        /*
         * A given file is transferred in chunks of 1GB to the export store.
         * Chunk class is used to keep track of the file chunk sequences that
         * are being exported. Mapping of the file and its chunk sequences
         * are captured in this map.
         *
         * TODO: Include chunks inside recEntityMap
         */
        private final Map<String, Chunk> chunks;

        public RecordStreamMap() {
            recEntityMap = new ConcurrentHashMap<String, RecordEntityInfo>();
            chunks = new ConcurrentHashMap<String, Chunk>();
        }

        /**
         * Size of file segment stored in external export store is fixated to
         * 1GB. Check if the entire record bytes with size recordLength can be
         * accommodated in this file segment. If not, flush the stream.
         */
        void canAddRecord(String fileName, long recordLength) {

            RecordEntityInfo recEntityInfo = recEntityMap.get(fileName);

            if (recEntityInfo == null) {
                return;
            }

            recEntityInfo.canAddRecord(fileName, recordLength);
        }

        /**
         * Transfer recordBytes into the file RecordStream
         */
        void insert(String fileName, byte[] recordBytes) {

            RecordEntityInfo recEntityInfo = recEntityMap.get(fileName);

            if (recEntityInfo == null) {
                synchronized (recEntityMap) {
                    recEntityInfo = recEntityMap.get(fileName);
                    if (recEntityInfo == null) {
                        recEntityInfo = new RecordEntityInfo();
                        recEntityMap.put(fileName, recEntityInfo);
                    }
                }
            }

            recEntityInfo.insert(fileName, recordBytes);
        }

        /**
         * Transfer LOB recordBytes into the LOB RecordStream
         */
        void insert(String lobName, ByteBuffer buffer) {

            RecordEntityInfo recEntityInfo = recEntityMap.get(lobName);

            if (recEntityInfo == null) {
                recEntityInfo = new RecordEntityInfo();
                recEntityMap.put(lobName, recEntityInfo);
            }

            RecordStream stream = recEntityInfo.getRecordStream();

            /*
             * If no stream has been created yet, create one and start streaming
             * the LOB record bytes.
             */
            if (stream == null ||
                stream.getFileSize() > getMaxLobFileSize()) {

                /*
                 * Adding this record will exceed the file segment size limit
                 * (1GB). Flush this stream and create a new RecordStream.
                 * Continue transferring the LOB record bytes in this new
                 * RecordStream.
                 */
                if (stream != null) {

                    logger.info("Chunk size for lob " + lobName + " exceeded."
                                + " Flushing the stream.");

                    recEntityInfo.flushStream();
                }

                CustomOutputStream out = new CustomOutputStream();
                WritableByteChannel outChannel = Channels.newChannel(out);
                CustomInputStream in = new CustomInputStream(out);

                /*
                 * Spawns the worker thread
                 */
                createTaskFromList(in, lobName);

                stream = new RecordStream(lobName, outChannel, out);
                recEntityInfo.setRecordStream(stream);
            }

            stream.addByteBuffer(buffer);
        }

        /**
         * Flush all the current running streams
         */
        public void flushAllStreams() {

            logger.info("Flushing all the RecordStreams.");

            for (Map.Entry<String, RecordEntityInfo> entry :
                    recEntityMap.entrySet()) {

                RecordEntityInfo recEntityInfo = entry.getValue();
                recEntityInfo.flushStream();
            }
        }

        /**
         * Spawns a worker thread which transfers the data bytes to the export
         * store for a given file
         */
        public void createTaskFromList(CustomInputStream in, String fileName) {

            Chunk chunk = chunks.get(fileName);

            if (chunk == null) {
                chunk = new Chunk();
                chunks.put(fileName, chunk);
            }

            String chunkSequence = chunk.next();

            logger.info("Creating a new RecordStream for " + fileName +
                        ". File segment number: " + chunk.getNumChunks() +
                        ". Chunk sequence: " + chunk.get());

            Future<Boolean> future = threadPool
               .submit(new WriteTask(in, fileName, chunkSequence));

            taskFutureMap.put(fileName, new FutureHolder(future));
        }

        /**
         * Perform any work that needs to be performed post export. The derived
         * classes provide implementation for this method.
         */
        public void performPostExportWork() {

            logger.info("Performing post export work.");

            doPostExportWork(chunks);
        }

        /**
         * Wrapper on top of RecordStream which creates a synchronization point
         * so you can safely switch over to a new RecordStream.
         * There is one per logical export stream (per table, per lob, etc) for
         * the lifetime of the export.
         */
        class RecordEntityInfo {

            private RecordStream recStream;

            void setRecordStream(RecordStream recStream) {
                this.recStream = recStream;
            }

            RecordStream getRecordStream() {
                return recStream;
            }

            /**
             * Flush the stream
             */
            synchronized void flushStream() {

                if (recStream == null) {
                    return;
                }

                CustomOutputStream out = recStream.out;
                String fileName = recStream.fileName;

                out.customFlush();
                recStream = null;

                try {
                    taskWaitQueue.put(taskFutureMap.get(fileName));
                } catch (InterruptedException e) {
                    logger.log(Level.SEVERE,
                               "Exception populating TaskWaiterQueue "
                               + "with " + fileName + " future instance.", e);
                }

                taskFutureMap.remove(fileName);
            }

            /**
             * The maximum size of a file segment stored in external export
             * store is 1GB. Check if the entire record bytes with size
             * recordLength can be accommodated in this file segment. If not,
             * flush the stream.
             */
            synchronized void canAddRecord(String fileName, long recordLength) {

                if (recStream == null) {
                    return;
                }

                if (recStream.getFileSize() + recordLength >
                    maxFileSegmentSize) {

                    logger.info("Chunk size for file " + fileName +
                                " exceeded." +
                                " Flushing the stream.");

                    flushStream();
                }
            }

            /**
             * Transfer recordBytes into the file RecordStream
             */
            synchronized void insert(String fileName, byte[] recordBytes) {

                if (recStream != null) {
                    recStream.addRecord(recordBytes);
                    return;
                }

                CustomOutputStream out = new CustomOutputStream();
                CustomInputStream in = new CustomInputStream(out);
                out.setRecordEntityInfo(this);

                /*
                 * Spawns the worker thread
                 */
                createTaskFromList(in, fileName);

                recStream = new RecordStream(fileName, out);
                recStream.addRecord(recordBytes);
            }
        }

        /**
         * Represents a channel for bytes of a given file segment. Contains
         * methods to add bytes to the stream. The worker threads read
         * bytes from this stream and transfer it to the export store.
         */
        class RecordStream {

            private final String fileName;
            private CustomOutputStream out;
            private WritableByteChannel outChannel;
            private long fileSize = 0;

            RecordStream(String fileName,
                         CustomOutputStream out) {
                this.fileName = fileName;
                this.out = out;
            }

            RecordStream(String lobName,
                         WritableByteChannel outChannel,
                         CustomOutputStream out) {
                this.fileName = lobName;
                this.outChannel = outChannel;
                this.out = out;
            }

            void setOutputStream(CustomOutputStream out) {
                this.out = out;
            }

            /**
             * Add the record bytes to the stream
             */
            void addRecord(byte[] record) {

                out.customWrite(record);
                fileSize += record.length;
            }

            /**
             * Add the LOB record bytes to the stream
             */
            void addByteBuffer(ByteBuffer byteBuffer) {

                try {
                    fileSize += outChannel.write(byteBuffer);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Exception transferring LOB " +
                               fileName + " bytes to export store.", e);
                }
            }

            public String getFileName() {
                return fileName;
            }

            public long getFileSize() {
                return fileSize;
            }

            public CustomOutputStream getStream() {
                return out;
            }

            @Override
            public String toString() {
                return "FileName: " + fileName +
                       ". Size: " + fileSize;
            }
        }
    }

    /**
     * Thread  responsible for inserting the given LOB data bytes into the
     * RecordStream.
     */
    private class WriteLobTask implements Callable<Boolean> {

        String lobName;
        InputStream in;

        public WriteLobTask(String lobName,
                            InputStream in) {
            this.lobName = lobName;
            this.in = in;
        }

        @Override
        public Boolean call() throws Exception {

            logger.info("WriteLobTask thread spawned for " + lobName);

            ReadableByteChannel inChannel = Channels.newChannel(in);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024*100);

            while (inChannel.read(buffer) >= 0 || buffer.position() > 0) {
                buffer.flip();
                recordStreamMap.insert(lobName, buffer);
                buffer.compact();
            }

            return true;
        }
    }

    /**
     * Worker thread responsible for reading the bytes from recordStreamMap and
     * transferring them to the export store.
     */
    private class WriteTask implements Callable<Boolean> {

        String fileName;
        String chunkSequence;
        CustomInputStream in;

        public WriteTask(CustomInputStream in,
                         String fileName,
                         String chunkSequence) {
            this.in = in;
            this.fileName = fileName;
            this.chunkSequence = chunkSequence;
        }

        @Override
        public Boolean call() {

            logger.info("WriteTask worker thread spawned for " + fileName);

            doExport(fileName, chunkSequence, in);
            return true;
        }
    }

    /**
     * This class holds a Future<Boolean> and exists so that a
     * BlockingQueue<FutureHolder> can be used to indicate which Worker thread
     * tasks need to be waited upon.  A FutureHolder with a null future
     * indicated the end of input and the waiting thread can exit.
     */
    class FutureHolder {
        Future<Boolean> future;

        public FutureHolder(Future<Boolean> future) {
            this.future = future;
        }

        public Future<Boolean> getFuture() {
            return future;
        }
    }

    /**
     * A Callable that reaps all of the worker thread tasks on the queue and
     * aggregates their results. It returns when an empty Future or
     * FutureHolder is found in the queue or one of the Futures fails with an
     * exception. In this case the main thread needs to occasionally check if
     * this Future is done.
     */
    private class TaskWaiter implements Callable<Boolean> {

        @Override
        public Boolean call()
            throws Exception {

            logger.info("TaskWaiter thread spawned.");

            while (true) {

                FutureHolder holder;

                try {
                    holder = taskWaitQueue.take();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                Future<Boolean> future;
                if (holder == null || holder.getFuture() == null) {
                    return true;
                }

                future = holder.getFuture();

                try {
                    future.get();
                } catch (ExecutionException e) {
                    logger.log(Level.SEVERE, "Exception in TaskWaiter.", e);
                } catch (InterruptedException ie) {
                    logger.log(Level.SEVERE, "Exception in TaskWaiter.", ie);
                }
            }
        }
    }

    /******************* Abstract Methods **************************/

    /**
     * Streams all the bytes for a given file segment from the RecordStream
     * to the export store.
     */
    public abstract boolean doExport(String fileName,
                                     String chunkSequence,
                                     CustomInputStream in);

    /**
     * Perform any work that needs to be done post export.
     */
    public abstract void doPostExportWork(Map<String, Chunk> chunks);

    /**
     * Get the max LOB file segment size that will be stored in the export
     * store.
     */
    public abstract long getMaxLobFileSize();
}
