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

package oracle.kv.impl.api.query;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Logger;

import oracle.kv.ExecutionSubscription;
import oracle.kv.FastExternalizableException;
import oracle.kv.StatementResult;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.async.AsyncIterationHandleImpl;
import oracle.kv.impl.async.AsyncTableIterator;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.runtime.ReceiveIter;
import oracle.kv.impl.query.runtime.RuntimeControlBlock;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.query.Statement;
import oracle.kv.stats.DetailedMetrics;
import oracle.kv.table.FieldValue;
import oracle.kv.table.RecordDef;
import oracle.kv.table.RecordValue;
import oracle.kv.table.TableIterator;

import org.reactivestreams.Subscription;

/**
 * Implementation of StatementResult when statement is a query.
 *
 * It represents the query result set, and provides the entry-point methods
 * for the computation of this result set.
 *
 * Queries can be executed in 2 modes: synchronous and asynchronous. For async
 * mode, the following diagram shows the high-level data structs participating 
 * in query execution, and their relationships (who has references to what)
 *
 *    QueryStatementResultImpl                   AsyncPublisherImpl
 *      /|\              |                              |
 *       |               |                              |
 *       |              \|/                            \|/
 *       |           AsyncExecutionHandleImpl      Subscriber
 *       |             |       /|\      /|\             |
 *       |             |        |        |              |
 *      \|/           \|/       |        |             \|/
 *    QueryResultIterator       |        ------ IterationSubscriptionImpl
 *            |                 |
 *            |                 |
 *           \|/                |
 *       Query PlanIters -------
 */
public class QueryStatementResultImpl implements StatementResult {

    private final PreparedStatementImpl statement;

    private final AsyncExecutionHandleImpl executionHandle;

    private final QueryResultIterator iterator;

    private boolean closed;

    public QueryStatementResultImpl(TableAPIImpl tableAPI,
                                    ExecuteOptions options,
                                    PreparedStatementImpl stmt,
                                    boolean async) {
        this(tableAPI, options, stmt, null, async, null, null);
    }

    public QueryStatementResultImpl(TableAPIImpl tableAPI,
                                    ExecuteOptions options,
                                    BoundStatementImpl stmt,
                                    boolean async) {
        this(tableAPI, options, stmt.getPreparedStmt(),
             stmt.getPreparedStmt().getExternalVarsArray(stmt.getVariables()),
             async, null, null);
    }

    public QueryStatementResultImpl(TableAPIImpl tableAPI,
                                    ExecuteOptions options,
                                    BoundStatementImpl stmt,
                                    boolean async,
                                    Set<Integer> partitions,
                                    Set<RepGroupId> shards) {
        this(tableAPI, options, stmt.getPreparedStmt(),
             stmt.getPreparedStmt().getExternalVarsArray(stmt.getVariables()),
             async, partitions, shards);
    }

    public QueryStatementResultImpl(TableAPIImpl tableAPI,
                                    ExecuteOptions options,
                                    PreparedStatementImpl stmt,
                                    boolean async,
                                    Set<Integer> partitions,
                                    Set<RepGroupId> shards) {
        this(tableAPI, options, stmt, null, async, partitions, shards);
    }

    private QueryStatementResultImpl(TableAPIImpl tableAPI,
                                     ExecuteOptions options,
                                     PreparedStatementImpl ps,
                                     FieldValue[] externalVars,
                                     boolean async,
                                     final Set<Integer> partitions,
                                     final Set<RepGroupId> shards) {

        if (ps.hasExternalVars() && externalVars == null) {
            throw new IllegalArgumentException(
                "The query contains external variables, none of which " +
                "has been bound. Create a BoundStatement to bind the " +
                "variables");
        }

        statement = ps;
        PlanIter iter = ps.getQueryPlan();
        RecordDef resultDef = ps.getResultDef();

        Logger logger = tableAPI.getStore().getLogger();

        executionHandle = (async ? new AsyncExecutionHandleImpl(logger) : null);

        RuntimeControlBlock rcb = new RuntimeControlBlock(
            tableAPI.getStore(),
            tableAPI.getStore().getLogger(),
            tableAPI.getTableMetadataHelper(),
            partitions,
            shards,
            options, /* ExecuteOptions */
            iter,
            ps.getNumIterators(),
            ps.getNumRegisters(),
            externalVars);

        this.iterator = new QueryResultIterator(rcb, iter, resultDef);
        closed = false;
    }

    @Override
    public void close() {
        iterator.close();
        closed = true;
    }

    @Override
    public RecordDef getResultDef() {
        if (closed) {
            throw new IllegalStateException("Statement result already closed.");
        }

        return iterator.getResultDef();
    }


    @Override
    public TableIterator<RecordValue> iterator() {

        if (executionHandle != null) {
            throw new IllegalStateException(
                "Application-driven iteration is not allowed for queries " +
                "executed in asynchronous mode");
        }

        if (closed) {
            throw new IllegalStateException("Statement result already closed.");
        }

        return iterator;
    }

    public AsyncIterationHandleImpl<RecordValue> getExecutionHandle() {
        return executionHandle;
    }

    @Override
    public int getPlanId() {
        return 0;
    }

    @Override
    public String getInfo() {
        return null;
    }

    @Override
    public String getInfoAsJson() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public boolean isDone() {
        return !iterator.hasNext();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public String getResult() {
        return null;
    }

    @Override
    public Kind getKind() {
        return Kind.QUERY;
    }

    /**
     * Returns the KB read during the execution of operation.
     */
    public int getReadKB() {
        return iterator.getReadKB();
    }

    /**
     * Returns the KB written during the execution of operation.
     */
    public int getWriteKB() {
        return iterator.getWriteKB();
    }

    /**
     * Returns the continuation key for the next execution.
     */
    public byte[] getContinuationKey() {
        return iterator.getContinuationKey();
    }

    public boolean reachedLimit() {
        return iterator.reachedLimit();
    }

    public boolean hasSortPhase1Result() {
        return iterator.hasSortPhase1Result();
    }

    public int writeSortPhase1Results(DataOutput out) throws IOException {
        return iterator.writeSortPhase1ResultInfo(out);
    }

    /*
     * QueryResultIterator is public because it is accessed by proxy code.
     */
    public class QueryResultIterator
        implements AsyncTableIterator<RecordValue> {

        private final RuntimeControlBlock rcb;

        private final PlanIter theRootIter;

        private final RecordDef theResultDef;

        private boolean theHasNext;

        private boolean theIsClosed;

        QueryResultIterator(
            RuntimeControlBlock rcb,
            PlanIter iter,
            RecordDef resultDef) {

            this.rcb = rcb;
            theRootIter = iter;
            theResultDef = resultDef;

            try {
                theRootIter.open(rcb);

                if (executionHandle != null) {

                    /*
                     * Store the notifier in the iterator, which will supply it
                     * to its children, if any. That way, children can notify
                     * the execution handle directly. Requests the handle makes
                     * to obtain more iteration results will still need to
                     * filter down to the children.
                     */
                    theRootIter.setIterationHandleNotifier(rcb, executionHandle);
                    executionHandle.setIterator(this);
                } else {
                    updateHasNext();
                }
            } catch (QueryStateException qse) {
                /*
                 * Log the exception if a logger is available.
                 */
                Logger logger = rcb.getStore().getLogger();
                if (logger != null) {
                    logger.warning(qse.toString());
                }
                throw new IllegalStateException(qse.toString());
            } catch (QueryException qe) {
                /* A QueryException thrown at the client; rethrow as IAE */
                throw qe.getIllegalArgument();
            } catch (IllegalArgumentException iae) {
                throw iae;
            } catch (FastExternalizableException fee) {
                throw fee;
            } catch (RuntimeException re) {
                /* why log this as WARNING? */
                String msg = "Query execution failed: " + re;
                Logger logger = rcb.getStore().getLogger();
                if (logger != null) {
                    logger.warning(msg);
                }
                //re.printStackTrace();
                throw re;
            }
        }

        RecordDef getResultDef() {
            return theResultDef;
        }

        public QueryStatementResultImpl getQueryStatementResult() {
            return QueryStatementResultImpl.this;
        }

        /**
         * Returns the KB read during the execution of operation.
         */
        public int getReadKB() {
            return rcb.getReadKB();
        }

        /**
         * Returns the KB written during the execution of operation.
         */
        public int getWriteKB() {
            return rcb.getWriteKB();
        }

        /**
         * Returns the continuation key for the next execution.
         */
        public byte[] getContinuationKey() {
            return rcb.getContinuationKey();
        }

        public boolean reachedLimit() {
            return rcb.getReachedLimit();
        }

        public boolean hasSortPhase1Result() {
            return (theRootIter instanceof ReceiveIter &&
                    ((ReceiveIter)theRootIter).hasSortPhase1Result(rcb));
        }

        public int writeSortPhase1ResultInfo(DataOutput out)
            throws IOException {
            return ((ReceiveIter)theRootIter).
                   writeSortPhase1ResultInfo(rcb, out);
        }

        private void updateHasNext() {
            theHasNext = theRootIter.next(rcb);
        }

        @Override
        public boolean hasNext() {

            if (executionHandle != null) {
                throw new IllegalStateException(
                    "Application-driven iteration is not allowed for queries " +
                    "executed in asynchronous mode");
            }
            return theHasNext;
        }

        @Override
        public RecordValue next() {

            if (!theHasNext) {
                throw new NoSuchElementException();
            }

            return nextInternal(false /* localOnly */);
        }

        @Override
        public RecordValue nextLocal() {

            if (!theRootIter.nextLocal(rcb)) {
                return null;
            }

            return nextInternal(true /* localOnly */);
        }

        /* Suppress Eclipse warning in assert -- see below */
        @SuppressWarnings("unlikely-arg-type")
        private RecordValue nextInternal(boolean localOnly) {

            final RecordValue record;

            try {
                FieldValueImpl resVal = rcb.getRegVal(theRootIter.getResultReg());

                if (resVal.isTuple()) {
                    assert(theResultDef == null ||
                           theResultDef.equals(resVal.getDefinition()));
                    record = ((TupleValue)resVal).toRecord();

                } else {
                    assert(resVal.isRecord());
                    record = (RecordValue)resVal;
                }

                if (!localOnly) {
                    updateHasNext();
                }

            } catch (QueryStateException qse) {
                /*
                 * Log the exception if a logger is available.
                 */
                Logger logger = rcb.getStore().getLogger();
                if (logger != null) {
                    logger.warning(qse.toString());
                }
                throw new IllegalStateException(qse.toString());
            } catch (QueryException qe) {
                /* A QueryException thrown at the client; rethrow as IAE */
                throw qe.getIllegalArgument();
            }

            return record;
        }

        @Override
        public Throwable getCloseException() {
            return theRootIter.getCloseException(rcb);
        }

        @Override
        public void close() {
            if (!theIsClosed) {
                theRootIter.close(rcb);
                theHasNext = false;
                theIsClosed = true;
            }
        }


        @Override
        public boolean isClosed() {

            /* Should be used in async mode only */
            assert(executionHandle != null);
            return theIsClosed || theRootIter.isDone(rcb);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DetailedMetrics> getPartitionMetrics() {
            if (rcb.getTableIterator() != null) {
                return rcb.getTableIterator().getShardMetrics();
            }
            return Collections.emptyList();
        }

        @Override
        public List<DetailedMetrics> getShardMetrics() {
            if (rcb.getTableIterator() != null) {
                return rcb.getTableIterator().getShardMetrics();
            }
            return Collections.emptyList();
        }
    }

    private class AsyncExecutionHandleImpl
            extends AsyncIterationHandleImpl<RecordValue> {

        AsyncExecutionHandleImpl(Logger logger) {
            super(logger);
        }

        @Override
        protected Subscription createSubscription() {
            return new ExecutionSubscriptionImpl();
        }

        private class ExecutionSubscriptionImpl
                extends IterationSubscriptionImpl
                implements ExecutionSubscription {
            @Override
            public Kind getKind() {
                return QueryStatementResultImpl.this.getKind();
            }

            @Override
            public Statement getStatement() {
                return QueryStatementResultImpl.this.statement;
            }

            @Override
            public int getPlanId() {
                return QueryStatementResultImpl.this.getPlanId();
            }

            @Override
            public String getInfo() {
                return QueryStatementResultImpl.this.getInfo();
            }

            @Override
            public String getInfoAsJson() {
                return QueryStatementResultImpl.this.getInfoAsJson();
            }
        }
    }
}
