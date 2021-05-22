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

package oracle.kv.impl.api.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import oracle.kv.FaultException;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.impl.api.table.TableVersionException;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.fault.RNUnavailableException;
import oracle.kv.impl.fault.WrappedClientException;
import oracle.kv.impl.metadata.Metadata.MetadataType;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryRuntimeException;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.runtime.PartitionUnionIter;
import oracle.kv.impl.query.runtime.PartitionUnionIter.PartitionedResults;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.runtime.PlanIter.PlanIterKind;
import oracle.kv.impl.query.runtime.ResumeInfo;
import oracle.kv.impl.query.runtime.RuntimeControlBlock;
import oracle.kv.impl.query.runtime.server.ServerIterFactoryImpl;
import oracle.kv.impl.security.KVStorePrivilege;
import oracle.kv.impl.security.SystemPrivilege;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.query.ExecuteOptions;

import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.Transaction;

/**
 * Server handler for {@link TableQuery}.
 */
public class TableQueryHandler extends InternalOperationHandler<TableQuery> {

    TableQueryHandler(OperationHandler handler, OpCode opCode) {
        super(handler, opCode, TableQuery.class);
    }

    @Override
    List<? extends KVStorePrivilege> getRequiredPrivileges(TableQuery op) {
        /*
         * Checks the basic privilege for authentication here, and leave the
         * keyspace checking and the table access checking in
         * {@code verifyTableAccess()}.
         */
        return SystemPrivilege.usrviewPrivList;
    }

    @Override
    Result execute(TableQuery op,
                   Transaction txn,
                   PartitionId partitionId) {

        TableMetadataHelper mdHelper = getMetadataHelper();
        ExecuteOptions options = new ExecuteOptions();
        RuntimeControlBlock rcb = new RuntimeControlBlock(
            getLogger(),
            mdHelper,
            options,
            op,
            this,
            new ServerIterFactoryImpl(txn, operationHandler));

        return executeQueryPlan(op, rcb, partitionId);
    }

    /**
     * Returns a TableMetadataHelper instance available on this node.
     */
    private TableMetadataHelper getMetadataHelper() {

        final TableMetadata md =
            (TableMetadata) getRepNode().getMetadata(MetadataType.TABLE);

        if (md == null) {
            final String msg = "Query execution unable to get metadata";
            getLogger().warning(msg);

            /*
             * Wrap this exception into one that can be thrown to the client.
             */
            new QueryStateException(msg).throwClientException();
        }
        return md;
    }

    private Result executeQueryPlan(
        TableQuery op,
        RuntimeControlBlock rcb,
        PartitionId pid) {

        ResumeInfo ri = op.getResumeInfo();
        ri.setRCB(rcb);
        ri.setCurrentPid(pid.getPartitionId());

        int batchSize = op.getBatchSize();
        PlanIter queryPlan = op.getQueryPlan();
        boolean partUnion = false;
        boolean inSortPhase1 = false;
        PartitionUnionIter partUnionIter = null;
        boolean noException = false;
        boolean more = false;
        List<FieldValueImpl> results = null;
        int[] pids = null;
        int[] numResultsPerPid = null;
        ResumeInfo[] resumeInfos = null;

        if (op.getOpCode() == OpCode.QUERY_MULTI_PARTITION) {
            if (queryPlan.getKind() == PlanIterKind.PARTITION_UNION) {
                partUnion = true;
                partUnionIter = (PartitionUnionIter)queryPlan;
                inSortPhase1 = ri.isInSortPhase1();
            } else if (queryPlan.getKind() == PlanIterKind.GROUP &&
                       queryPlan.getInputIter().getKind() ==
                       PlanIterKind.PARTITION_UNION) {
                partUnion = true;
            }
        }

        try {
            queryPlan.open(rcb);

            if (inSortPhase1 && partUnionIter != null) {

                partUnionIter.next(rcb);

                PartitionedResults res = partUnionIter.getPartitionedResults(rcb);
                results = res.results;
                pids = res.pids;
                numResultsPerPid = res.numResultsPerPid;
                resumeInfos = res.resumeInfos;
                ri = rcb.getResumeInfo();

            } else {

                if (rcb.getTraceLevel() >= 1) {
                    rcb.trace("TableQueryHandler: Executing query on partition " + pid);
                    rcb.trace(queryPlan.display(true));
                    rcb.trace("inSortPhase1 = " + inSortPhase1);
                    rcb.trace("Resume Info:\n" + ri);
                    rcb.trace("Batch size:\n" + batchSize);
                }

                results = new ArrayList<FieldValueImpl>(batchSize);

                while ((batchSize == 0 || results.size() < batchSize) &&
                       queryPlan.next(rcb)) {
                    addResult(rcb, ri, queryPlan, results);
                }

                if (partUnion) {
                    more = !queryPlan.isDone(rcb);
                } else {
                    byte[] primResumeKey = ri.getPrimResumeKey();
                    byte[] secResumeKey = ri.getSecResumeKey();

                    if (rcb.getReachedLimit()) {
                        more = true;
                    } else if (results.size() == batchSize) {
                        if (primResumeKey != null || secResumeKey != null) {
                            more = true;
                        } else if (ri.getGBTuple() != null) {
                            more = queryPlan.next(rcb);
                            assert(more);
                            addResult(rcb, ri, queryPlan, results);
                            assert(ri.getGBTuple() == null);
                            more = false;
                        } else {
                            more = false;
                        }
                    } else {
                        more = false;
                    }
                }

                ri.setNumResultsComputed(results.size());

                if (rcb.getTraceLevel() >= 1) {
                    rcb.trace("TableQueryHandler: Produced a batch of " +

                              results.size() + " results on partition " + pid +
                              " number of KB read = " + op.getReadKB() +
                              " more results = " + more);

                    if (rcb.getTraceLevel() >= 2) {
                        rcb.trace(ri.toString());
                    }
                }
            }

            noException = true;

        /*
         * The following multi-catch is so that some expected RuntimeExceptions
         * can be propagated up to allow the request handler to deal with
         * them as it does for other operations. Such exceptions, if not
         * caught here, will be wrapped below in the catch of RuntimeException
         * and will be logged (with stack trace) and re-thrown. As new
         * exceptions of this nature are found, please add to this catch.
         *
         * FaultExceptions are likely ResourceLimitExceptions and should
         * be passed up to the user as-is.
         *
         * A RNUE is likely due to the RN being in the middle of updating
         * state associated with tables and indexes.
         *
         * TableVersionException is due to the client and store table
         * metadata being out of sync with each other.
         */
        } catch (FaultException |
                 RNUnavailableException |
                 TableVersionException re) {
            throw re;
        } catch (LockConflictException lce) {
            /* let the caller handle this */
            throw lce;
        } catch (QueryException qe) {

            /*
             * For debugging and tracing this can temporarily use level INFO
             */
            getLogger().fine("Query execution failed: " + qe);

            /*
             * Turn this into a wrapped IllegalArgumentException so that it can
             * be passed to the client.
             */
            throw qe.getWrappedIllegalArgument();
        } catch (QueryStateException qse) {

            /*
             * This exception indicates a bug or problem in the engine. Log
             * it. It will be thrown through to the client side.
             */
            getLogger().warning(qse.toString());

            /*
             * Wrap this exception into one that can be thrown to the client.
             */
            qse.throwClientException();

        } catch (IllegalArgumentException e) {
            throw new WrappedClientException(e);
        } catch (RuntimeException re) {

            /*
             * RuntimeException should not be caught here. REs should be
             * propagated up to the request handler as many are explicitly
             * handled there. The issue is that the query code can throw REs
             * which it should catch and turn into something specific (such as
             * an IAE). Until that time this needs to remain to avoid the
             * RN restarting due to some minor query issue.
             *
             * Detect NullPointerException and log it as SEVERE. NPEs
             * should not be considered user errors.
             */
            if (re instanceof NullPointerException) {
                getLogger().severe("NullPointerException during query: " +
                                   re);
            }
            throw new QueryRuntimeException(re);
        } finally {
            try {
                queryPlan.close(rcb);
            } catch (RuntimeException re) {
                if (noException) {
                    throw new QueryRuntimeException(re);
                }
            }
        }

        ri.addReadKB(op.getReadKB());

        return new Result.QueryResult(getOpCode(),
                                      op.getReadKB(),
                                      op.getWriteKB(),
                                      results,
                                      op.getResultDef(),
                                      op.mayReturnNULL(),
                                      more,
                                      ri,
                                      rcb.getReachedLimit(),
                                      pids,
                                      numResultsPerPid,
                                      resumeInfos);
    }

    private void addResult(
        RuntimeControlBlock rcb,
        ResumeInfo ri,
        PlanIter queryPlan,
        List<FieldValueImpl> results) {

        FieldValueImpl res = rcb.getRegVal(queryPlan.getResultReg());
        if (res.isTuple()) {
            res = ((TupleValue)res).toRecord();
        }

        if (rcb.getTraceLevel() >= 2) {
            rcb.trace("TableQueryHandler: Produced result on " +
                      "partition " + ri.getCurrentPid() +
                      " :\n" + res);
        }

        results.add(res);
    }

    public PartitionId[] getShardPids() {
        Set<PartitionId> pids =
            new TreeSet<PartitionId>(getRepNode().getPartitions());
        return pids.toArray(new PartitionId[pids.size()]);
    }

    public int getNumPartitions() {
        return getRepNode().getTopology().getNumPartitions();
    }
}
