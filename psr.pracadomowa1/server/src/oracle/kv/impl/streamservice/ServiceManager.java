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

package oracle.kv.impl.streamservice;

import static oracle.kv.impl.systables.StreamServiceTableDesc.COL_SERVICE_TYPE;
import static oracle.kv.impl.systables.StreamServiceTableDesc.COL_REQUEST_ID;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.Direction;
import oracle.kv.Durability;
import oracle.kv.FaultException;
import oracle.kv.impl.streamservice.ServiceMessage.ServiceType;
import oracle.kv.impl.systables.StreamRequestDesc;
import oracle.kv.impl.systables.StreamResponseDesc;
import oracle.kv.table.FieldRange;
import oracle.kv.table.MultiRowOptions;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.ReadOptions;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;
import oracle.kv.table.TableIteratorOptions;
import oracle.kv.table.WriteOptions;

/**
 * Base class for constructing stream service managers.
 */
public abstract class ServiceManager<R extends ServiceMessage,
                                     S extends ServiceMessage> {

    protected static final ReadOptions READ_OPTIONS =
            new ReadOptions(Consistency.ABSOLUTE, 0, null);

    protected static final WriteOptions WRITE_OPTIONS =
            new WriteOptions(Durability.COMMIT_SYNC, 0, null);

    private static final int N_OP_RETRIES = 10;

    /*
     * Cached handles to the request and response tables. These fields should
     * only be accessed through the appropriate get* methods.
     */
    private Table requestTable;
    private Table responseTable;

    protected final Logger logger;

    protected ServiceManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Gets the service type of this service.
     */
    protected abstract ServiceType getServiceType();

    /**
     * Returns the table API handle.
     */
    protected abstract TableAPI getTableAPI();

    /**
     * Returns a request messages from the specified row.
     */
    protected abstract R getRequestFromRow(Row row) throws IOException;

    /**
     * Returns a response messages from the specified row.
     */
    protected abstract S getResponseFromRow(Row row) throws IOException;

    /**
     * Handles IOExceptions thrown when converting a Row to a message. The error
     * is a case specific string. If this method returns the conversion will
     * return null.
     *
     * @param error the description of when the exception occured
     * @param ioe the exception
     */
    protected abstract void handleIOE(String error, IOException ioe);

    /**
     * Writes the specified service request. If a request with the same ID
     * already exists it is not overwritten.
     */
    protected void postRequest(R message) {
        final Row row;
        try {
            row = message.toRow(getRequestTable());
        } catch (IOException ioe) {
            handleIOE("exception generating row from " + message, ioe);
            return;
        }
        execute(() ->
                getTableAPI().putIfAbsent(row, null /*prevRow*/, WRITE_OPTIONS));
    }

    /**
     * Writes the specified service response. If a response with the same ID
     * already exists it is not overwritten.
     */
    protected void postResponse(S message) {
        final Row row;
        try {
            row = message.toRow(getResponseTable());
        } catch (IOException ioe) {
            handleIOE("exception generating row from " + message, ioe);
            return;
        }
        execute(() ->
                getTableAPI().putIfAbsent(row, null /*prevRow*/, WRITE_OPTIONS));
    }

    /**
     * Gets the request for the specified ID if it exists. If no request is
     * found null is returned.
     */
    public R getRequest(int requestId) {
        final Row row = execute(() ->
                getTableAPI().get(createRequestKey(requestId), READ_OPTIONS));
        try {
            return row == null ? null : getRequestFromRow(row);
        } catch (IOException ioe) {
            handleIOE("exception parsing request " + requestId +
                      " from " + getServiceType() + " service.", ioe);
        }
        return null;
    }

    /**
     * Gets the response for the specified ID if it exists. If no response is
     * found null is returned.
     */
    public S getResponse(int requestId) {
        final Row row = execute(() ->
                getTableAPI().get(createResponseKey(requestId), READ_OPTIONS));
        try {
            return row == null ? null : getResponseFromRow(row);
        } catch (IOException ioe) {
            handleIOE("exception parsing response to " + requestId +
                      " from " + getServiceType() + " service.", ioe);
        }
        return null;
    }

    /**
     * Creates a primary key for the request table. If {@literal requestID > 0}
     * then the request ID component of the key is set.
     */
    protected PrimaryKey createRequestKey(int requestId) {
        return createKey(getRequestTable(), requestId);
    }

    /**
     * Creates a primary key for the response table. If {@literal requestID >
     * 0} then the request ID component of the key is set.
     */
    protected PrimaryKey createResponseKey(int requestId) {
        return createKey(getResponseTable(), requestId);
    }

    /**
     * Creates a primary key for the specified table. If {@literal requestID >
     * 0} then the request ID component of the key is set.
     */
    private PrimaryKey createKey(Table messageTable, int requestId) {
        final PrimaryKey key = messageTable.createPrimaryKey();
        key.put(COL_SERVICE_TYPE, getServiceType().ordinal());
        if (requestId > 0) {
            key.put(COL_REQUEST_ID, requestId);
        }
        return key;
    }

    /**
     * Throws FaultException if the manager is not ready.
     */
    public void checkForReady() {
        try {
            getRequestTable();
            getResponseTable();
        } catch (FaultException fe) {
            throw new FaultException("Multi-region service is not ready: " +
                                     fe.getMessage(), fe, false);
        }
    }

    /**
     * Gets the request table. Throws FaultException if the system table has
     * not been initialized.
     */
    protected synchronized Table getRequestTable() {
        if (requestTable == null) {
            requestTable = getTable(StreamRequestDesc.TABLE_NAME);
        }
        return requestTable;
    }

    /**
     * Gets the response table. Throws FaultException if the system table has
     * not been initialized.
     */
    protected synchronized Table getResponseTable() {
        if (responseTable == null) {
            responseTable = getTable(StreamResponseDesc.TABLE_NAME);
        }
        return responseTable;
    }

    private Table getTable(String tableName) {
        final Table table = getTableAPI().getTable(tableName);
        if (table != null) {
            return table;
        }
        throw new FaultException("Unable to acquire handle to system table " +
                                 tableName + " store may not be initialized",
                                 false /*isRemote*/);
    }

    /**
     * Executes the specified operation, retrying if possible.
     */
    protected <T> T execute(Supplier<T> op) {
        FaultException lastException = null;
        int nRetries = N_OP_RETRIES;
        while (nRetries > 0) {
            try {
                return op.get();
            } catch (FaultException e) {
                lastException = e;
            }
            if (isShutdown()) {
                throw new FaultException("manager shutdown", false);
            }
            nRetries--;
        }
        throw lastException != null ? lastException :
                               new FaultException("operation timed out", false);
    }

    /**
     * Returns true if the manager is shutdown. Manager subclasses can override
     * to abort retries on shutdown.
     */
    protected boolean isShutdown() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Base class for message iterators.
     */
    protected abstract class MessageIterator<T extends ServiceMessage>
                                                         implements Iterator<T> {
        private final TableIterator<Row> tableItr;

        /**
         * Constructs an iteration over all of the records in the message table
         * with the service type returned from getServiceType().
         */
        protected MessageIterator(Table messageTable,
                                  long timeout,
                                  TimeUnit timeoutUnit) {
            this(messageTable, 0, timeout, timeoutUnit);
        }

        /**
         * Constructs an iteration over the records in the message table
         * with the service type returned from getServiceType(). If
         * startId == 0, then the iteration is over all records, otherwise the
         * iteration starts with the message whos requestID is equal to or
         * greater then startId.
         *
         * If messageTable is null the iterator will be empty.
         */
        protected MessageIterator(Table messageTable,
                                  int startId,
                                  long timeout,
                                  TimeUnit timeoutUnit) {
            /**
             * messageTable could be null if the manager is started before the
             * system tables are created. In that case create an empty iterator.
             */
            if (messageTable == null) {
                tableItr = null;
                return;
            }
            MultiRowOptions getOptions = null;
            if (startId > 0) {
                FieldRange range = messageTable.createFieldRange(COL_REQUEST_ID);
                range.setStart(startId, true /*isInclusive*/);
                getOptions = new MultiRowOptions(range);
            }
            final TableIteratorOptions options =
                                   new TableIteratorOptions(Direction.FORWARD,
                                                            Consistency.ABSOLUTE,
                                                            timeout, timeoutUnit);

            tableItr = getTableAPI().tableIterator(createKey(messageTable, 0),
                                                   getOptions, options);
        }

        @Override
        public boolean hasNext() {
            return tableItr != null && tableItr.hasNext();
        }

        @Override
        public T next() {
            if (tableItr == null) {
                throw new NoSuchElementException();
            }
            try {
                Row row = tableItr.next();
                if (row == null) {
                   throw new IllegalStateException("Unexpected null row from" +
                                                   " table iterator");
                }
                T m = getMessage(row);
                if (m == null) {
                   throw new IllegalStateException("Unable to get message from" +
                                                   " row: " + row);
                }
                return m;
            } catch (IOException ioe) {
                handleIOE("exception iterating messages from " +
                          getServiceType() + " service.", ioe);
            }
            throw new NoSuchElementException();
        }

        /**
         * Get a service message from the specified row.
         */
        protected abstract T getMessage(Row row) throws IOException;

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }
}
