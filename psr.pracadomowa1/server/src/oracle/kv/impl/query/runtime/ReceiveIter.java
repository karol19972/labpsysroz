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

package oracle.kv.impl.query.runtime;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;
import static oracle.kv.impl.util.SerialVersion.UNKNOWN;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullByteArray;
import static oracle.kv.impl.util.SerialVersion.QUERY_VERSION_10;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import oracle.kv.Consistency;
import oracle.kv.Depth;
import oracle.kv.Direction;
import oracle.kv.Durability;
import oracle.kv.StoreIteratorException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.Request;
import oracle.kv.impl.api.StoreIteratorParams;
import oracle.kv.impl.api.ops.Result;
import oracle.kv.impl.api.ops.Result.QueryResult;
import oracle.kv.impl.api.ops.TableQuery;
import oracle.kv.impl.api.parallelscan.PartitionScanIterator;
import oracle.kv.impl.api.parallelscan.ShardScanIterator;
import oracle.kv.impl.api.query.PreparedStatementImpl.DistributionKind;
import oracle.kv.impl.api.table.BinaryValueImpl;
import oracle.kv.impl.api.table.BooleanValueImpl;
import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.IntegerValueImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.api.table.NumberValueImpl;
import oracle.kv.impl.api.table.PrimaryKeyImpl;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RecordValueImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TimestampValueImpl;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.async.AsyncTableIterator;
import oracle.kv.impl.async.IterationHandleNotifier;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.compiler.ExprReceive;
import oracle.kv.impl.query.compiler.FuncCompOp;
import oracle.kv.impl.query.compiler.FunctionLib.FuncCode;
import oracle.kv.impl.query.compiler.SortSpec;
import oracle.kv.impl.query.runtime.CloudSerializer.FieldValueWriter;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.SerializationUtil;
import oracle.kv.impl.util.SizeOf;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.stats.DetailedMetrics;

/**
 * ReceiveIter are placed at the boundaries between parts of the query that
 * execute on different "machines". Currently, there can be only one ReceiveIter
 * in the whole query plan. It executes at a "client machine" and its child
 * subplan executes at a "server machine". The child subplan may actually be
 * replicated on several server machines (RNs), in which case the ReceiveIter
 * acts as a UNION ALL expr, collecting and propagating the results it receives
 * from its children. Furthermore, the ReceiveIter may perform a merge-sort over
 * its inputs (if the inputs return sorted results).
 *
 * If the ReceiveIter is the root iter, it just propagates to its output the
 * FieldValues (most likely RecordValues) it receives from the RNs. Otherwise,
 * if its input iter produces tuples, the ReceiveIter will recreate these tuples
 * at its output by unnesting into tuples the RecordValues arriving from the RNs.
 */
public class ReceiveIter extends PlanIter {

    private class ReceiveIterState extends PlanIterState {

        int thePid;

        int theMinPid;
        int theMaxPid;

        int theMinSid;
        int theMaxSid;

        boolean theAlwaysFalse;

        AsyncTableIterator<FieldValueImpl> theRemoteResultsIter;

        Throwable theRemoteResultsIterCloseException;

        HashSet<BinaryValueImpl> thePrimKeysSet;

        long theMemoryConsumption = theFixedMemoryConsumption;

        volatile IterationHandleNotifier theAsyncNotifier;

        ReceiveIterState(boolean eliminateIndexDups) {

            theMinPid = theMinPartition;
            theMaxPid = theMaxPartition;
            theMinSid = theMinShard;
            theMaxSid = theMaxShard;

            if (eliminateIndexDups) {
                thePrimKeysSet = new HashSet<BinaryValueImpl>(1000);
            }
        }

        @Override
        public void done() {
            super.done();
            clear();
        }

        @Override
        public void reset(PlanIter iter) {
            super.reset(iter);
            clear();
            theMemoryConsumption = theFixedMemoryConsumption;
        }

        @Override
        public void close() {
            super.close();
            if (theRemoteResultsIter != null) {
                theRemoteResultsIterCloseException =
                    theRemoteResultsIter.getCloseException();
            }
            clear();
            theRemoteResultsIter = null;
        }

        void clear() {
            if (theRemoteResultsIter != null) {
                theRemoteResultsIter.close();
            }
            if (thePrimKeysSet != null) {
                thePrimKeysSet.clear();
            }
        }
    }

    private static class CachedBinaryPlan {

        private byte[] thePlan = null;
        private short theSerialVersion = UNKNOWN;
        private boolean theIsProxyPlan;

        private CachedBinaryPlan(
            byte[] plan,
            short serialVersion,
            boolean isProxyPlan) {
            thePlan = plan;
            theSerialVersion = serialVersion;
            theIsProxyPlan = isProxyPlan;
        }

        public static CachedBinaryPlan create(
            byte[] plan,
            short serialVersion,
            boolean isProxyPlan) {
            return new CachedBinaryPlan(plan, serialVersion, isProxyPlan);
        }

        byte[] getPlan() {
            return thePlan;
        }

        short getSerialVersion() {
            return theSerialVersion;
        }

        boolean isProxyPlan() {
            return theIsProxyPlan;
        }
    }

    private static final long theFixedMemoryConsumption =
        (SizeOf.OBJECT_REF_OVERHEAD +
         SizeOf.HASHSET_OVERHEAD +
         8);

    private final PlanIter theInputIter;

    private transient volatile CachedBinaryPlan theSerializedInputIter = null;

    private final FieldDefImpl theInputType;

    /* added in QUERY_VERSION_2 */
    private final boolean theMayReturnNULL;

    private final int[] theSortFieldPositions;

    private final SortSpec[] theSortSpecs;

    private final int[] thePrimKeyPositions;

    private final int[] theTupleRegs;

    private final DistributionKind theDistributionKind;

    private final RecordValueImpl thePrimaryKey;

    private final PartitionId thePartitionId;

    private final int theMinPartition;

    private final int theMaxPartition;

    private final int theMinShard;

    private final int theMaxShard;

    private final PlanIter[] thePartitionsBindExprs;

    private final PlanIter[] theShardsBindExprs;

    private final long theTableId;

    private final String theTableName;

    private final String theNamespace;

    private final PlanIter[] theShardKeyExternals;

    private final int theNumRegs;

    private final int theNumIters;

    /* added in QUERY_VERSION_5 */
    private final boolean theIsUpdate;

    public ReceiveIter(
        ExprReceive e,
        int resultReg,
        PlanIter input,
        FieldDefImpl inputType,
        boolean mayReturnNULL,
        int[] sortFieldPositions,
        SortSpec[] sortSpecs,
        int[] primKeyPositions,
        DistributionKind distrKind,
        PrimaryKeyImpl primKey,
        PlanIter[] partitionsBindIters,
        PlanIter[] shardsBindIters,
        PlanIter[] shardkeyExternals,
        int numRegs,
        int numIters,
        boolean isUpdate,
        boolean forCloud) {

        super(e, resultReg, forCloud);

        theInputIter = input;
        theInputType = inputType;
        theMayReturnNULL = mayReturnNULL;
        theSortFieldPositions = sortFieldPositions;
        theSortSpecs = sortSpecs;
        thePrimKeyPositions = primKeyPositions;
        theDistributionKind = distrKind;
        theShardKeyExternals = shardkeyExternals;
        theTableId = e.getQCB().getTargetTableId();

        if (primKey != null) {
            thePrimaryKey = primKey;
            theTableName = primKey.getTable().getFullName();
            theNamespace = primKey.getTable().getInternalNamespace();
        } else {
            thePrimaryKey = null;
            theTableName = null;
            theNamespace = null;
        }

        /*
         * If it's a SINGLE_PARTITION query with no external vars, compute
         * the partition id now. Otherwise, it is computed in the open().
         */
        if (theDistributionKind == DistributionKind.SINGLE_PARTITION) {
            if (e.getPartitionId() > 0) {
                thePartitionId = new PartitionId(e.getPartitionId());

                int numPartitions = e.getQCB().getStore().getNPartitions();
                if (e.getPartitionId() > numPartitions) {
                    throw new QueryStateException(
                        "Partition id is greater than the number of partitions");
                }
            } else if (primKey != null &&
                       (theShardKeyExternals == null ||
                        theShardKeyExternals.length == 0)) {
                thePartitionId =
                    primKey.getPartitionId(e.getQCB().getStore());
            } else {
                thePartitionId = null;
            }
        } else {
            thePartitionId = null;
        }

        theMinPartition = e.getMinPartition();
        theMaxPartition = e.getMaxPartition();
        theMinShard = e.getMinShard();
        theMaxShard = e.getMaxShard();
        thePartitionsBindExprs = partitionsBindIters;
        theShardsBindExprs = shardsBindIters;

        /*
         * If the ReceiveIter is the root iter, it just propagates to its
         * output the FieldValues (most likely RecordValues) it receives from
         * the RNs. Otherwise, if its input iter produces tuples, the
         * ReceiveIter will recreate these tuples at its output by unnesting
         * into tuples the RecordValues arriving from the RNs.
         */
        if (input.producesTuples() && e.getQCB().getRootExpr() != e) {
            theTupleRegs = input.getTupleRegs();
        } else {
            theTupleRegs = null;
        }

        theNumRegs = numRegs;
        theNumIters = numIters;
        theIsUpdate = isUpdate;
    }

    /*
     * This constructor is used at the proxy to deserialize a ReceiveIter that
     * was created at the proxy originally, then shipped to the driver and
     * sent back from the driver to the proxy.
     *
     * serialVersion: the kvstore version at the proxy where the ReceiveIter
     * was constructed originally.
     */
    public ReceiveIter(DataInput in, short serialVersion) throws IOException {

        super(in, serialVersion);

        theNumRegs = readPositiveInt(in);
        theNumIters = readPositiveInt(in);
        theInputType = (FieldDefImpl) deserializeFieldDef(in, serialVersion);

        theMayReturnNULL = in.readBoolean();

        theSortFieldPositions = deserializeIntArray(in, serialVersion);
        theSortSpecs = deserializeSortSpecs(in, serialVersion);
        thePrimKeyPositions = deserializeIntArray(in, serialVersion);
        theTupleRegs = deserializeIntArray(in, serialVersion);

        short ordinal = in.readShort();
        theDistributionKind = DistributionKind.values()[ordinal];

        theTableId = in.readLong();

        theTableName = SerializationUtil.readString(in, serialVersion);
        if (theTableName != null) {
            theNamespace = SerializationUtil.readString(in, serialVersion);
            thePrimaryKey = deserializeKey(in, serialVersion);
        } else {
            thePrimaryKey = null;
            theNamespace = null;
        }

        theShardKeyExternals = deserializeIters(in, serialVersion);

        theIsUpdate = in.readBoolean();

        if (serialVersion > QUERY_VERSION_10) {
            int pid = in.readInt();
            if (pid > 0) {
                thePartitionId = new PartitionId(pid);
            } else {
                thePartitionId = null;
            }

            theMinPartition = in.readInt();
            theMaxPartition = in.readInt();
            theMinShard = in.readInt();
            theMaxShard = in.readInt();
            thePartitionsBindExprs = deserializeIters(in, serialVersion);
            theShardsBindExprs = deserializeIters(in, serialVersion);

        } else {
            if (theDistributionKind == DistributionKind.SINGLE_PARTITION &&
                (theShardKeyExternals == null ||
                 theShardKeyExternals.length == 0)) {
                thePartitionId = new PartitionId(in.readInt());
            } else {
                thePartitionId = null;
            }

            theMinPartition = -1;
            theMaxPartition = -1;
            theMinShard = -1;
            theMaxShard = -1;
            thePartitionsBindExprs = null;
            theShardsBindExprs = null;
        }

        byte[] bytes = SerializationUtil.readNonNullByteArray(in);
        setSerializedIter(bytes, serialVersion);

        /* keeps compiler happy regarding final members */
        theInputIter = null;
    }

    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {

        super.writeFastExternal(out, serialVersion);

        out.writeInt(theNumRegs);
        out.writeInt(theNumIters);

        serializeFieldDef(theInputType, out, serialVersion);

        out.writeBoolean(theMayReturnNULL);

        serializeIntArray(theSortFieldPositions, out, serialVersion);
        serializeSortSpecs(theSortSpecs, out, serialVersion);
        serializeIntArray(thePrimKeyPositions, out, serialVersion);
        serializeIntArray(theTupleRegs, out, serialVersion);

        out.writeShort(theDistributionKind.ordinal());

        out.writeLong(theTableId);
        SerializationUtil.writeString(out, serialVersion, theTableName);
        if (theTableName != null) {
            SerializationUtil.writeString(out, serialVersion, theNamespace);
            serializeKey(thePrimaryKey, out, serialVersion);
        }
        serializeIters(theShardKeyExternals, out, serialVersion);

        out.writeBoolean(theIsUpdate);

        if (thePartitionId != null) {
            out.writeInt(thePartitionId.getPartitionId());
        } else {
            out.writeInt(-1);
        }

        out.writeInt(theMinPartition);
        out.writeInt(theMaxPartition);
        out.writeInt(theMinShard);
        out.writeInt(theMaxShard);
        serializeIters(thePartitionsBindExprs, out, serialVersion);
        serializeIters(theShardsBindExprs, out, serialVersion);

        byte[] bytes = ensureSerializedIter(serialVersion);
        SerializationUtil.writeNonNullByteArray(out, bytes);
    }

    @Override
    public void writeForCloud(
        DataOutput out,
        short driverVersion,
        FieldValueWriter valWriter) throws IOException {

        assert(theIsCloudDriverIter);
        writeForCloudCommon(out, driverVersion);

        out.writeShort(theDistributionKind.ordinal());

        String[] sortFields = null;
        String[] primKeyFields = null;

        if (theSortFieldPositions != null) {

            RecordDefImpl recDef = (RecordDefImpl)theInputType;
            sortFields = new String[theSortFieldPositions.length];

            for (int i = 0; i < sortFields.length; ++i) {
                sortFields[i] = recDef.getFieldName(theSortFieldPositions[i]);
            }
        }

        if (thePrimKeyPositions != null) {

            RecordDefImpl recDef = (RecordDefImpl)theInputType;
            primKeyFields = new String[thePrimKeyPositions.length];

            for (int i = 0; i < primKeyFields.length; ++i) {
                primKeyFields[i] = recDef.getFieldName(thePrimKeyPositions[i]);
            }
        }

        CloudSerializer.writeStringArray(sortFields, out);
        CloudSerializer.writeSortSpecs(theSortSpecs, out);
        CloudSerializer.writeStringArray(primKeyFields, out);
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.RECEIVE;
    }

    /*
     * These are public so that PreparedStatementImpl can reconstruct itself from
     * a serialized format.
     */
    public int getNumRegisters() {
        return theNumRegs;
    }

    public int getNumIterators() {
        return theNumIters;
    }

    /*
     * This should be compile-time only so it should be safe to *not* include
     * theInputIter in serialization/deserialization.
     */
    @Override
    public int[] getTupleRegs() {
        return theTupleRegs;
    }

    public boolean doesSort() {
        return (theSortFieldPositions != null);
    }

    public boolean hasSortPhase1Result(RuntimeControlBlock rcb) {

        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);

        if (theDistributionKind == DistributionKind.ALL_PARTITIONS &&
            doesSort()) {

            /* Note: this method is called by the proxy twice: once immediately
             * after open() and before next() or nextLocal() is called, and
             * another time after the query is done. In the 1st case
             * state.theRemoteResultsIter will be null. */
            if (state.theRemoteResultsIter == null) {
                ResumeInfo ri = rcb.getResumeInfo();
                BitSet partitionsBitmap = ri.getPartitionsBitmap();

                if (partitionsBitmap == null) {
                    return true;
                }

                return ri.isInSortPhase1();
            }
            return ((AllPartitionsIterator)state.theRemoteResultsIter).
                hasSortPhase1Result();
        }

        return false;
    }

    public int writeSortPhase1ResultInfo(
        RuntimeControlBlock rcb,
        DataOutput out) throws IOException {

        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);
        AllPartitionsIterator iter =
            (AllPartitionsIterator)state.theRemoteResultsIter;
        return iter.writeSortPhase1ResultInfo(rcb, out);
    }

    public boolean eliminatesDuplicates() {
        return thePrimKeyPositions != null;
    }

    public FieldDefImpl getResultDef() {
        return theInputType;
    }

    @Override
    public void setIterationHandleNotifier(
        RuntimeControlBlock rcb,
        IterationHandleNotifier iterHandleNotifier) {

        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);

        state.theAsyncNotifier = iterHandleNotifier;
    }

    /**
     * Sets or updates the cached serialized version of the query plan under
     * this ReceiveIter.
     *
     * The method is called from TableQuery.writeFastExternal(). This implies
     * that it will be called by each parallel-scan stream that is created
     * by "this", which further implies that it can be called concurrently by
     * multiple threads.
     *
     * The method cannot be called earlier, because each stream must generate
     * its own serialized plan. This is because the plan generated depends on
     * the version of the RN that the stream connects with (and the RN may
     * change every time the stream requests a new batch of results). Of course,
     * unless a system upgrade is going on, all RNs will have the same version,
     * and all streams will generate the same binary plan. So, to avoid the
     * same plan to be generated again and again, by each stream and each batch
     * of results, whenever one stream generates a binary plan, that plan, as
     * well as the version used for its generation, are cached in the
     * ReceiveIter. If another stream finds a cached plan whose version is the
     * same as the current version used by the stream, the stream can use the
     * cached plan, instead of generating it again.
     */
    public byte[] ensureSerializedIter(short serialVersion) {

        CachedBinaryPlan cachedPlan = theSerializedInputIter;

        if (cachedPlan != null &&
            cachedPlan.getPlan() != null &&
            (cachedPlan.isProxyPlan() ||
             cachedPlan.getSerialVersion() == serialVersion)) {
            return cachedPlan.thePlan;
        }

        synchronized (this) {
            try {
                final ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();
                final DataOutput dataOut = new DataOutputStream(baos);

                PlanIter.serializeIter(theInputIter, dataOut, serialVersion);
                byte[] ba = baos.toByteArray();
                cachedPlan = CachedBinaryPlan.create(ba, serialVersion, false);
                theSerializedInputIter = cachedPlan;

                return ba;
            }
            catch (IOException ioe) {
                throw new QueryException(ioe);
            }
        }
    }

    public void setSerializedIter(byte[] bytes,
                                  short serialVersion) {
        assert theSerializedInputIter == null;
        theSerializedInputIter = CachedBinaryPlan.
                                 create(bytes, serialVersion, true);
    }

    /**
     * This method executes a query on the server side and stores in the
     * iterator state a ParalleScanIterator over the results.
     *
     * At some point a refactor of how parallel scan and index scan work may
     * be necessary to take into consideration these facts:
     *  o a query may be an update or read-only (this can probably be known
     *  ahead of time once the query is prepared). In any case the type of
     *  query and Durability specified will affect routing of the query.
     *  o some iterator params are not relevant (direction, keys, ranges, Depth)
     */
    private void ensureIterator(
        RuntimeControlBlock rcb,
        ReceiveIterState state) {

        if (state.theRemoteResultsIter != null) {
            return;
        }

        switch (theDistributionKind) {
        case SINGLE_PARTITION:
            state.theRemoteResultsIter = runOnOnePartition(rcb);
            break;
        case ALL_PARTITIONS:
            state.theRemoteResultsIter = runOnAllPartitions(rcb, state);
            break;
        case ALL_SHARDS:
            state.theRemoteResultsIter = runOnAllShards(rcb, state);
            break;
        default:
            throw new QueryStateException(
                "Unknown distribution kind: " + theDistributionKind);
        }

        rcb.setTableIterator(state.theRemoteResultsIter);
    }

    /**
     * Execute the child plan of this ReceiveIter on all partitions
     */
    private AsyncTableIterator<FieldValueImpl> runOnAllPartitions(
        final RuntimeControlBlock rcb,
        final ReceiveIterState state) {

        ExecuteOptions options = rcb.getExecuteOptions();

        if (options.isProxyQuery() ||
            rcb.getUseBytesLimit() ||
            rcb.getUseBatchSizeAsLimit()) {

            if (options.getDriverQueryVersion() >=
                ExecuteOptions.DRIVER_QUERY_V2) {
                return new AllPartitionsIterator(rcb);
            }

            throw new QueryStateException(
                "Query must be recompiled with a newer driver");
        }

        /*
         * Compute the direction to be stored in the BaseParallelScanIterator.
         * Because the actual comparisons among the query results are done by
         * the streams, the BaseParallelScanIterator just needs to know whether
         * sorting is needed or not in order to invoke the comparison method or
         * not. So, we just need to pass UNORDERED or FORWARD.
         */
        Direction dir = (theSortFieldPositions != null ?
                         Direction.FORWARD :
                         Direction.UNORDERED);

        StoreIteratorParams params =
            new StoreIteratorParams(
                dir,
                rcb.getBatchSize(),
                null, // key bytes
                null, // key range
                Depth.PARENT_AND_DESCENDANTS,
                rcb.getConsistency(),
                rcb.getTimeout(),
                rcb.getTimeUnit(),
                rcb.getPartitionSet(),
                true /* excludeTombstones */);

        return new PartitionScanIterator<FieldValueImpl>(
            rcb.getStore(), options, params, state.theAsyncNotifier) {

            @Override
            protected QueryPartitionStream createStream(
                RepGroupId groupId,
                int partitionId) {
                return new QueryPartitionStream(groupId, partitionId);
            }

            @Override
            protected TableQuery generateGetterOp(byte[] resumeKey) {
                throw new QueryStateException("Unexpected call");
            }

            @Override
            protected void convertResult(
                Result result,
                List<FieldValueImpl> elementList) {

                List<FieldValueImpl> queryResults = result.getQueryResults();

                // TODO: try to avoid this useless loop
                for (FieldValueImpl res : queryResults) {
                    elementList.add(res);
                }
            }

            @Override
            protected int compare(FieldValueImpl one, FieldValueImpl two) {
                throw new QueryStateException("Unexpected call");
            }

            class QueryPartitionStream extends PartitionStream {

                private ResumeInfo theResumeInfo = new ResumeInfo(rcb);

                QueryPartitionStream(RepGroupId groupId, int partitionId) {
                    super(groupId, partitionId, null);
                }

                @Override
                protected Request makeReadRequest() {

                    TableQuery op = new TableQuery(
                        DistributionKind.ALL_PARTITIONS,
                        theInputType,
                        theMayReturnNULL,
                        SerialVersion.CURRENT,
                        ReceiveIter.this,
                        rcb.getExternalVars(),
                        theNumIters,
                        theNumRegs,
                        theTableId,
                        rcb.getMathContext(),
                        rcb.getTraceLevel(),
                        rcb.getBatchSize(),
                        0, /* maxReadKB*/
                        0, /* maxCurrentReadKB */
                        0, /* maxCurrentWriteKB */
                        theResumeInfo,
                        1, /* emptyReadFactor */
                        rcb.getDeleteLimit());

                    if (theIsUpdate) {
                        final Request req =
                            storeImpl.makeWriteRequest(
                                op,
                                new PartitionId(partitionId),
                                rcb.getDurability(),
                                rcb.getTimeout(),
                                rcb.getTimeUnit());

                        if (options != null) {
                            req.setAuthContext(options.getAuthContext());
                            req.setLogContext(options.getLogContext());
                        }
                        return req;
                    }

                    final Request req =
                        storeImpl.makeReadRequest(
                            op,
                            new PartitionId(partitionId),
                            storeIteratorParams.getConsistency(),
                            storeIteratorParams.getTimeout(),
                            storeIteratorParams.getTimeoutUnit());
                    if (options != null) {
                        req.setAuthContext(options.getAuthContext());
                        req.setLogContext(options.getLogContext());
                    }
                    return req;
                }

                @Override
                protected void setResumeKey(Result result) {

                    QueryResult res = (QueryResult)result;
                    theResumeInfo.refresh(res.getResumeInfo());

                    if (rcb.getTraceLevel() >= 1) {
                        rcb.trace("Received " + res.getNumRecords() +
                                  " results from group : " + groupId +
                                  " partition " + partitionId);
                    }

                    if (rcb.getTraceLevel() >= 4) {
                        rcb.trace(theResumeInfo.toString());
                    }
                }

                @Override
                protected int compareInternal(Stream o) {

                    QueryPartitionStream other = (QueryPartitionStream)o;
                    int cmp;

                    FieldValueImpl v1 =
                        currentResultSet.getQueryResults().
                        get(currentResultPos);

                    FieldValueImpl v2 =
                        other.currentResultSet.getQueryResults().
                        get(other.currentResultPos);

                    if (theInputType.isRecord()) {
                        RecordValueImpl rec1 = (RecordValueImpl)v1;
                        RecordValueImpl rec2 = (RecordValueImpl)v2;
                        cmp = SortIter.compareRecords(rec1, rec2,
                                                      theSortFieldPositions,
                                                      theSortSpecs);
                    } else {
                        cmp = SortIter.compareAtomics(v1, v2, 0, theSortSpecs);
                    }

                    if (cmp == 0) {
                        return (partitionId < other.partitionId ? -1 : 1);
                    }

                    return cmp;
                }
            }
        };
    }

    /**
     * Execute the child plan of this ReceiveIter on a single partition
     */
    private AsyncTableIterator<FieldValueImpl> runOnOnePartition(
        final RuntimeControlBlock rcb) {

        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);
        return new SinglePartitionIterator(rcb, new PartitionId(state.thePid));
    }

    /**
     * Execute the child plan of this ReceiveIter on all shards
     * TODO: remove duplicates in result
     */
    private AsyncTableIterator<FieldValueImpl> runOnAllShards(
        final RuntimeControlBlock rcb,
        final ReceiveIterState state) {

        ExecuteOptions options = rcb.getExecuteOptions();

        if (options.isProxyQuery() ||
            rcb.getUseBytesLimit() ||
            rcb.getUseBatchSizeAsLimit()) {
            /* If size limit is specified, scan shards sequentially. */
            return new SequentialShardsIterator(rcb);
        }

        /*
         * Compute the direction to be stored in the BaseParallelScanIterator.
         * Because the actual comparisons among the query results are done by
         * the streams, the BaseParallelScanIterator just needs to know whether
         * sorting is needed or not in order to invoke the comparison method or
         * not. So, we just need to pass UNORDERED or FORWARD.
         */
        Direction dir = (theSortFieldPositions != null ?
                         Direction.FORWARD :
                         Direction.UNORDERED);

        return new ShardScanIterator<FieldValueImpl>(
             rcb.getStore(), rcb.getExecuteOptions(), dir, rcb.getShardSet(),
             state.theAsyncNotifier) {

            @Override
            protected QueryShardStream createStream(RepGroupId groupId) {
                return new QueryShardStream(groupId);
            }

            @Override
            protected TableQuery createOp(
                byte[] resumeSecondaryKey,
                byte[] resumePrimaryKey) {
                throw new QueryStateException("Unexpected call");
            }

            @Override
            protected void convertResult(
                Result result,
                List<FieldValueImpl> elementList) {

                List<FieldValueImpl> queryResults = result.getQueryResults();
                for (FieldValueImpl res : queryResults) {
                    elementList.add(res);
                }
            }

            @Override
            protected int compare(FieldValueImpl one, FieldValueImpl two) {
                throw new QueryStateException("Unexpected call");
            }

            class QueryShardStream extends ShardStream {

                private ResumeInfo theResumeInfo = new ResumeInfo(rcb);

                QueryShardStream(RepGroupId groupId) {
                    super(groupId, null, null);
                }

                @Override
                protected Request makeReadRequest() {

                    TableQuery op = new TableQuery(
                        DistributionKind.ALL_SHARDS,
                        theInputType,
                        theMayReturnNULL,
                        SerialVersion.CURRENT,
                        ReceiveIter.this,
                        rcb.getExternalVars(),
                        theNumIters,
                        theNumRegs,
                        theTableId,
                        rcb.getMathContext(),
                        rcb.getTraceLevel(),
                        rcb.getBatchSize(),
                        0, /* maxReadKB */
                        0, /* maxCurrentReadKB */
                        0, /* maxCurrentWriteKB */
                        theResumeInfo,
                        1, /* emptyReadFactor */
                        rcb.getDeleteLimit());

                    final ExecuteOptions exeOptions =
                        rcb.getExecuteOptions();
                    if (theIsUpdate) {
                        final Request req =
                            storeImpl.makeWriteRequest(
                                op, groupId, rcb.getDurability(),
                                requestTimeoutMs, MILLISECONDS);

                        if (exeOptions != null) {
                            req.setLogContext(exeOptions.getLogContext());
                            req.setAuthContext(exeOptions.getAuthContext());
                        }
                        return req;
                    }

                    final Request req =
                        storeImpl.makeReadRequest(op,
                                                  groupId,
                                                  consistency,
                                                  requestTimeoutMs,
                                                  MILLISECONDS);
                    if (exeOptions != null) {
                        req.setLogContext(exeOptions.getLogContext());
                        req.setAuthContext(exeOptions.getAuthContext());
                    }
                    return req;
                }

                @Override
                protected void setResumeKey(Result result) {

                    QueryResult res = (QueryResult)result;
                    theResumeInfo.refresh(res.getResumeInfo());

                    if (rcb.getTraceLevel() >= 1) {
                        rcb.trace("Received " + res.getNumRecords() +
                                  " results from shard : " + groupId);
                    }

                    if (rcb.getTraceLevel() >= 4) {
                        rcb.trace(theResumeInfo.toString());
                    }
                }

                @Override
                protected int compareInternal(Stream o) {

                    QueryShardStream other = (QueryShardStream)o;
                    int cmp;

                    FieldValueImpl v1 =
                        currentResultSet.getQueryResults().
                        get(currentResultPos);

                    FieldValueImpl v2 =
                        other.currentResultSet.getQueryResults().
                        get(other.currentResultPos);

                    if (theInputType.isRecord()) {
                        RecordValueImpl rec1 = (RecordValueImpl)v1;
                        RecordValueImpl rec2 = (RecordValueImpl)v2;
                        cmp = SortIter.compareRecords(rec1, rec2,
                                                      theSortFieldPositions,
                                                      theSortSpecs);
                    } else {
                        cmp = SortIter.compareAtomics(v1, v2, 0, theSortSpecs);
                    }

                    if (cmp == 0) {
                        return getGroupId().compareTo(other.getGroupId());
                    }

                    return cmp;
                }
            }
        };
    }

    @Override
    public void open(RuntimeControlBlock rcb) {

        ReceiveIterState state =
            new ReceiveIterState(thePrimKeyPositions != null);

        if (theDistributionKind == DistributionKind.SINGLE_PARTITION) {

            if (thePartitionId == null &&
                thePartitionsBindExprs != null &&
                thePartitionsBindExprs.length > 0) {

                computeExternalCid(rcb, state, false, true, true);

            } else if (thePartitionId == null &&
                       theShardKeyExternals != null &&
                       theShardKeyExternals.length > 0) {
                /*
                 * Make a copy of thePrimaryKey in order to replace its
                 * "dummy", placeholder values with the corresponding values
                 * of the external-variable expressions
                 *
                 * Optimize the local case where thePrimaryKey is an actual
                 * PrimaryKeyImpl, avoiding a potentially costly getTable()
                 * call.
                 */
                PrimaryKeyImpl primaryKey;
                TableImpl table;
                if (thePrimaryKey instanceof PrimaryKeyImpl) {
                    primaryKey = (PrimaryKeyImpl) thePrimaryKey.clone();
                    table = primaryKey.getTable();
                } else {
                    table = rcb.getMetadataHelper().
                        getTable(theNamespace, theTableName);

                    if (table == null) {
                        StringBuilder sb = new StringBuilder().append("Table ");
                        if (theNamespace != null && !theNamespace.equals("")) {
                            sb.append(theNamespace).append(":");
                        }
                        sb.append(theTableName).append(" does not exist");
                        throw new QueryException(sb.toString(), theLocation);
                    }

                    primaryKey = table.createPrimaryKey(thePrimaryKey);
                }

                int size = theShardKeyExternals.length;

                for (int i = 0; i < size; ++i) {

                    PlanIter iter = theShardKeyExternals[i];

                    if (iter == null) {
                        continue;
                    }

                    iter.open(rcb);
                    iter.next(rcb);
                    FieldValueImpl val = rcb.getRegVal(iter.getResultReg());
                    iter.close(rcb);

                    if (val.isNull()) {
                        state.theAlwaysFalse = true;
                        break;
                    }

                    FieldValueImpl newVal = BaseTableIter.castValueToIndexKey(
                        table, null, i, val, FuncCode.OP_EQ);

                    if (newVal != val) {
                        if (newVal == BooleanValueImpl.falseValue) {
                            state.theAlwaysFalse = true;
                            break;
                        }

                        val = newVal;
                    }

                    String colName = table.getPrimaryKeyColumnName(i);
                    primaryKey.put(colName, val);
                }

                state.thePid = primaryKey.getPartitionId(rcb.getStore()).
                               getPartitionId();
            } else {
                state.thePid = thePartitionId.getPartitionId();
            }

            if (theShardsBindExprs != null && theShardsBindExprs.length > 0) {
                assert(state.theMinSid < 0 && state.theMaxSid < 0);
                computeExternalCid(rcb, state, true, true, false);
                if (theShardsBindExprs.length > 1) {
                    computeExternalCid(rcb, state, true, false, true);
                } else {
                    state.theMaxSid = state.theMinSid;
                }
            }

            if (!state.theAlwaysFalse && state.theMinSid > 0) {
                assert(state.theMaxSid > 0);
                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Shard range : [" + state.theMinSid + ", " +
                              state.theMaxSid + "]");
                }

                Topology topo = rcb.getStore().getTopology();
                Set<Integer> partSet = topo.
                    getPartitionsInShards(state.theMinSid,
                                          state.theMaxSid);

                if (!partSet.contains(state.thePid)) {
                    state.theAlwaysFalse = true;
                }
            }

        } else if (theDistributionKind == DistributionKind.ALL_PARTITIONS) {

            if (thePartitionsBindExprs != null &&
                thePartitionsBindExprs.length > 0) {
                assert(state.theMinPid < 0 && state.theMaxPid < 0);
                computeExternalCid(rcb, state, false, true, false);
                computeExternalCid(rcb, state, false, false, true);

            }

            if (theShardsBindExprs != null && theShardsBindExprs.length > 0) {
                assert(state.theMinSid < 0 && state.theMaxSid < 0);
                computeExternalCid(rcb, state, true, true, false);
                if (theShardsBindExprs.length > 1) {
                    computeExternalCid(rcb, state, true, false, true);
                } else {
                    state.theMaxSid = state.theMinSid;
                }
            }

            if (state.theMaxPid < state.theMinPid ||
                state.theMaxSid < state.theMinSid) {
                state.theAlwaysFalse = true;
            }

            Set<Integer> partSet = null;

            if (!state.theAlwaysFalse && state.theMinSid > 0) {
                assert(state.theMaxSid > 0);
                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Shard range : [" + state.theMinSid + ", " +
                              state.theMaxSid + "]");
                }

                Topology topo = rcb.getStore().getTopology();
                partSet = topo.getPartitionsInShards(state.theMinSid,
                                                     state.theMaxSid);
            }

            if (!state.theAlwaysFalse && state.theMinPid > 0) {
                assert(state.theMaxPid > 0);
                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Partition range : [" + state.theMinPid + ", " +
                              state.theMaxPid + "]");
                }

                Set<Integer> partSet2 = new TreeSet<Integer>();
                for (int i = state.theMinPid; i <= state.theMaxPid; ++i) {
                    partSet2.add(i);
                }

                if (partSet == null) {
                    partSet = partSet2;
                } else {
                    partSet.retainAll(partSet2);
                }
            }

            rcb.setPartitionSet(partSet);

        } else {
            if (theShardsBindExprs != null && theShardsBindExprs.length > 0) {
                assert(state.theMinSid < 0 && state.theMaxSid < 0);
                computeExternalCid(rcb, state, true, true, false);
                if (theShardsBindExprs.length > 1) {
                    computeExternalCid(rcb, state, true, false, true);
                } else {
                    state.theMaxSid = state.theMinSid;
                }
            }

            if (state.theMaxSid < state.theMinSid) {
                state.theAlwaysFalse = true;
            }

            Set<RepGroupId> shardSet = null;

            if (!state.theAlwaysFalse && state.theMinSid > 0) {
                assert(state.theMaxSid > 0);
                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Shard range : [" + state.theMinSid + ", " +
                              state.theMaxSid + "]");
                }

                shardSet = new TreeSet<RepGroupId>();
                for (int i = state.theMinSid; i <= state.theMaxSid; ++i) {
                    shardSet.add(new RepGroupId(i));
                }
            }

            if (shardSet != null) {
                rcb.setShardSet(shardSet);
            }
        }

        rcb.setState(theStatePos, state);
        rcb.incMemoryConsumption(state.theMemoryConsumption);

        if (theTupleRegs != null) {
            TupleValue tuple = new TupleValue((RecordDefImpl)theInputType,
                                              rcb.getRegisters(),
                                              theTupleRegs);
            rcb.setRegVal(theResultReg, tuple);
        }

        if (state.theAlwaysFalse) {
            state.done();
        }
    }

    private void computeExternalCid(
        RuntimeControlBlock rcb,
        ReceiveIterState state,
        boolean isShard,
        boolean isMin,
        boolean isMax) {

        int numContainers;
        PlanIter iter;

        if (isShard) {
            numContainers = rcb.getStore().getTopology().getRepGroupIds().size();
            iter = (isMin ?
                    theShardsBindExprs[0] :
                    theShardsBindExprs[1]);
        } else {
            numContainers = rcb.getStore().getNPartitions();
            iter = (isMin ?
                    thePartitionsBindExprs[0] :
                    thePartitionsBindExprs[1]);
        }

        if (iter == null) {

            if (isMin && isMax) {
                throw new QueryStateException(
                    "No iterator to compute bind value for partition id");
            }

            if (isMin) {
                if (isShard) {
                    state.theMinSid = 1;
                } else {
                    state.theMinPid = 1;
                }
                return;
            }

            if (isMax) {
                if (isShard) {
                    state.theMaxSid = numContainers;
                } else {
                    state.theMaxPid = numContainers;
                }
            }
            return;
        }

        iter.open(rcb);
        iter.next(rcb);
        FieldValueImpl val = rcb.getRegVal(iter.getResultReg());
        boolean more = iter.next(rcb);
        iter.close(rcb);

        if (more || val.isNull()) {
            state.theAlwaysFalse = true;
            return;
        }

        FieldValueImpl newVal = FuncCompOp.castConstInCompOp(
                FieldDefImpl.integerDef,
                false, /*allowJsonNull*/
                false, /*nullable*/
                true, /*scalar*/
                val,
                FuncCode.OP_EQ,
                false/*strict*/);
            
        if (newVal != val) {
            if (newVal == BooleanValueImpl.falseValue) {
                state.theAlwaysFalse = true;
                return;
            }

            val = newVal;
        }

        if (!val.isInteger()) {
            throw new QueryStateException(
                "Non integer partition or shard id : " + val);
        }

        int cid = ((IntegerValueImpl)val).get();

        if (cid <= 0) {
            if (isMin) {
                cid = 1;
            } else {
                state.theAlwaysFalse = true;
                return;
            }
        } else if (cid > numContainers) {
            if (isMax) {
                cid = numContainers;
            } else {
                state.theAlwaysFalse = true;
                return;
            }
        }

        if (isShard) {
            if (isMin) {
                state.theMinSid = cid;
            } else {
                state.theMaxSid = cid;
            }
        } else {
            if (isMin && isMax) {
                state.thePid = cid;
            } else if (isMin) {
                state.theMinPid = cid;
            } else {
                state.theMaxPid = cid;
            }
        }
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {
        return nextInternal(rcb, false /* localOnly */);
    }

    @Override
    public boolean nextLocal(RuntimeControlBlock rcb) {
        return nextInternal(rcb, true /* localOnly */);
    }

    private boolean nextInternal(RuntimeControlBlock rcb, boolean localOnly) {

        /*
         * Catch StoreIteratorException and if the cause is a QueryException,
         * throw that instead to provide more information to the caller.
         */
        try {
            ReceiveIterState state =
                (ReceiveIterState)rcb.getState(theStatePos);

            if (state.isDone()) {
                return false;
            }

            ensureIterator(rcb, state);

            FieldValueImpl res;

            do {
                if (localOnly) {
                    res = state.theRemoteResultsIter.nextLocal();

                    if (res == null) {
                        if (rcb.getTraceLevel() >= 3) {
                            rcb.trace(
                                "ReceiveIter: no local result. Is closed = " +
                                state.theRemoteResultsIter.isClosed());
                        }
                        if (state.theRemoteResultsIter.isClosed() &&
                            !state.isClosed()) {
                            state.done();
                        }
                        return false;
                    }

                    if (rcb.getTraceLevel() >= 3) {
                        rcb.trace("ReceiveIter: received result " + res);
                    }

                } else {
                    boolean more = state.theRemoteResultsIter.hasNext();

                    if (!more) {

                        if (state.theRemoteResultsIter instanceof
                            AllPartitionsIterator) {
                            AllPartitionsIterator iter =
                                (AllPartitionsIterator)state.theRemoteResultsIter;
                            if (!iter.hasSortPhase1Result()) {
                                state.done();
                            }
                        } else {
                            state.done();
                        }
                        return false;
                    }

                    res = state.theRemoteResultsIter.next();

                    if (rcb.getTraceLevel() >= 3) {
                        rcb.trace("ReceiveIter: received result " + res);
                    }
                }

                /* Eliminate index duplicates */
                if (thePrimKeyPositions != null) {
                    BinaryValueImpl binPrimKey = createBinaryPrimKey(res);
                    boolean added = state.thePrimKeysSet.add(binPrimKey);
                    if (!added) {
                        continue;
                    }
                    long sz = (binPrimKey.sizeof() +
                               SizeOf.HASHSET_ENTRY_OVERHEAD_32);
                    state.theMemoryConsumption += sz;
                    rcb.incMemoryConsumption(sz);
                }

                break;

            } while (true);

            boolean convertEmptyToNull = (doesSort() &&
                                          res.isRecord() &&
                                          !rcb.isProxyQuery());

            if (theTupleRegs != null) {
                TupleValue tuple = (TupleValue)rcb.getRegVal(theResultReg);
                tuple.toTuple((RecordValueImpl)res, convertEmptyToNull);
            } else if (convertEmptyToNull) {
                ((RecordValueImpl)res).convertEmptyToNull();
                rcb.setRegVal(theResultReg, res);
            } else {
                rcb.setRegVal(theResultReg,
                              res.isEMPTY() ? NullValueImpl.getInstance() : res);
            }

            return true;

        } catch (StoreIteratorException sie) {
            final Throwable cause = sie.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unexpected exception: " + cause,
                                            cause);
        }
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {
        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);
        rcb.decMemoryConsumption(state.theMemoryConsumption -
                                 theFixedMemoryConsumption);
        state.reset(this);
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        ReceiveIterState state = (ReceiveIterState)rcb.getState(theStatePos);
        if (state == null) {
            return;
        }

        state.close();
    }

    @Override
    public Throwable getCloseException(RuntimeControlBlock rcb) {

        final ReceiveIterState state =
            (ReceiveIterState) rcb.getState(theStatePos);

        if (state == null) {
            return null;
        }

        if (state.theRemoteResultsIter != null) {
            return state.theRemoteResultsIter.getCloseException();
        }

        return state.theRemoteResultsIterCloseException;
    }

    private BinaryValueImpl createBinaryPrimKey(FieldValueImpl result) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutput out = new DataOutputStream(baos);

        try {
            if (!result.isRecord()) {
                assert(thePrimKeyPositions.length == 1);
                writeValue(out, result, 0);

            } else {
                for (int i = 0; i < thePrimKeyPositions.length; ++i) {

                    FieldValueImpl fval =
                        ((RecordValueImpl)result).get(thePrimKeyPositions[i]);

                    writeValue(out, fval, i);
                }
            }
        } catch (IOException e) {
            throw new QueryStateException(
                "Failed to create binary prim key due to IOException:\n" +
                e.getMessage());
        }

        byte[] bytes = baos.toByteArray();
        return FieldDefImpl.binaryDef.createBinary(bytes);
    }

    private void writeValue(DataOutput out, FieldValueImpl val, int i)
        throws IOException {

        switch (val.getType()) {
        case INTEGER:
            SerializationUtil.writePackedInt(out, val.getInt());
            break;
        case LONG:
            SerializationUtil.writePackedLong(out, val.getLong());
            break;
        case DOUBLE:
            out.writeDouble(val.getDouble());
            break;
        case FLOAT:
            out.writeFloat(val.getFloat());
            break;
        case STRING:
            /* Use the current format */
            SerializationUtil.writeString(
                out, SerialVersion.CURRENT, val.getString());
            break;
        case ENUM:
            out.writeShort(val.asEnum().getIndex());
            break;
        case TIMESTAMP:
            TimestampValueImpl ts = (TimestampValueImpl)val;
            writeNonNullByteArray(out, ts.getBytes());
            break;
        case NUMBER:
            NumberValueImpl num = (NumberValueImpl)val;
            writeNonNullByteArray(out, num.getBytes());
            break;
        default:
            throw new QueryStateException(
                "Unexpected type for primary key column : " +
                val.getType() + ", at result column " + i);
        }
    }

    @Override
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {

        formatter.indent(sb);
        sb.append("\"distribution kind\" : \"").append(theDistributionKind);
        sb.append("\"");

        if (verbose) {
            if (thePartitionId != null && thePartitionId.getPartitionId() > 0) {
                sb.append(",\n");
                formatter.indent(sb);
                sb.append("\"partition id\" : ");
                sb.append(thePartitionId.getPartitionId());
            }
        }

        if (theMinPartition > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"minimum partition id\" : ");
            sb.append(theMinPartition);
        }

        if (theMaxPartition > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"maximum partition id\" : ");
            sb.append(theMaxPartition);
        }

        if (thePartitionsBindExprs != null &&
            thePartitionsBindExprs.length > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"partition id bind expressions\" : [\n");
            formatter.incIndent();

            for (int i = 0; i < thePartitionsBindExprs.length; ++i) {

                PlanIter iter = thePartitionsBindExprs[i];

                if (iter != null) {
                    iter.display(sb, formatter, verbose);
                } else {
                    formatter.indent(sb);
                    sb.append("null");
                }

                if (i < thePartitionsBindExprs.length - 1) {
                    sb.append(",\n");
                }
            }

            formatter.decIndent();
            sb.append("\n");
            formatter.indent(sb);
            sb.append("]");
        }

        if (theShardsBindExprs != null &&
            theShardsBindExprs.length > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"shard id bind expressions\" : [\n");
            formatter.incIndent();

            for (int i = 0; i < theShardsBindExprs.length; ++i) {

                PlanIter iter = theShardsBindExprs[i];

                if (iter != null) {
                    iter.display(sb, formatter, verbose);
                } else {
                    formatter.indent(sb);
                    sb.append("null");
                }

                if (i < theShardsBindExprs.length - 1) {
                    sb.append(",\n");
                }
            }

            formatter.decIndent();
            sb.append("\n");
            formatter.indent(sb);
            sb.append("]");
        }

        if (theMinShard > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"minimum shard id\" : ");
            sb.append(theMinShard);
        }

        if (theMaxShard > 0) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"maximum shard id\" : ");
            sb.append(theMaxShard);
        }

        if (theSortFieldPositions != null) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"order by fields at positions\" : [ ");
            for (int i = 0; i < theSortFieldPositions.length; ++i) {
                sb.append(theSortFieldPositions[i]);
                if (i < theSortFieldPositions.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(" ]");
        }

        if (thePrimKeyPositions != null) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"distinct by fields at positions\" : [ ");
            for (int i = 0; i < thePrimKeyPositions.length; ++i) {
                sb.append(thePrimKeyPositions[i]);
                if (i < thePrimKeyPositions.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(" ]");
        }

        if (theShardKeyExternals != null) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"primary key bind expressions\" : [\n");
            formatter.incIndent();

            for (int i = 0; i < theShardKeyExternals.length; ++i) {

                PlanIter iter = theShardKeyExternals[i];

                if (iter != null) {
                    iter.display(sb, formatter, verbose);
                } else {
                    formatter.indent(sb);
                    sb.append("null");
                }

                if (i < theShardKeyExternals.length - 1) {
                    sb.append(",\n");
                }
            }

            formatter.decIndent();
            sb.append("\n");
            formatter.indent(sb);
            sb.append("]");
        }

        if (verbose) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"number of registers\" : \"").append(theNumRegs);
            sb.append("\",\n");
            formatter.indent(sb);
            sb.append("\"number of iterators\" : \"").append(theNumIters);
            sb.append("\"");
        }

        if (theInputIter != null) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"input iterator\" :\n");
            theInputIter.display(sb, formatter, verbose);
        }
    }

    /**
     * An iterator that scans a single partition
     */
    private class SinglePartitionIterator
        implements AsyncTableIterator<FieldValueImpl> {

        private final RuntimeControlBlock theRCB;

        private AbstractScanIterator thePartitionIter;

        SinglePartitionIterator(RuntimeControlBlock rcb, PartitionId pid) {

            theRCB = rcb;
            thePartitionIter = new AbstractScanIterator(theRCB,
                                                        pid,
                                                        null,/* group id */
                                                        1/*emptyReadFactor*/);
        }

        @Override
        public boolean hasNext() {

            if (thePartitionIter == null) {
                return false;
            }

            if (!thePartitionIter.hasNext()) {

                if (!thePartitionIter.hasMoreRemoteResults()) {
                    close();
                    theRCB.setContinuationKey(null);
                    return false;
                }

                assert(theRCB.getReachedLimit());
                theRCB.createContinuationKey();
                return false;
            }

            return true;
        }

        @Override
        public FieldValueImpl nextLocal() {

            if (isClosed()) {
                return null;
            }

            FieldValueImpl res = thePartitionIter.nextLocal();

            if (res != null) {
                return res;
            }

            if (!thePartitionIter.hasMoreRemoteResults()) {
                close();
                theRCB.setContinuationKey(null);
                return null;
            }

            if (theRCB.getReachedLimit()) {
                theRCB.createContinuationKey();
                close();
                return null;
            }

            thePartitionIter.sendAsyncRemoteRequest();
            return null;
        }

        @Override
        public FieldValueImpl next() {
            return thePartitionIter.next();
        }

        @Override
        public void close() {
            if (thePartitionIter != null) {
                thePartitionIter.close();
            }
        }

        @Override
        public boolean isClosed() {
            return thePartitionIter == null || thePartitionIter.isClosed();
        }

        @Override
        public Throwable getCloseException() {
            return (thePartitionIter != null ?
                    thePartitionIter.getCloseException() :
                    null);
        }

        @Override
        public List<DetailedMetrics> getPartitionMetrics() {
            return Collections.emptyList();
        }

        @Override
        public List<DetailedMetrics> getShardMetrics() {
            return Collections.emptyList();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The partitions iterator that scans all partitions sequentially (but not
     * in partition-id order). During an RN visit it may scan more than one
     * partition. It is used for executing ALL_PARTITTIONS queries in the cloud.
     * For more details, see the javadoc of PartitionUnionIter.
     */
    private class AllPartitionsIterator
        implements AsyncTableIterator<FieldValueImpl> {

        private final RuntimeControlBlock theRCB;

        private int theNumShards;

        private int theNumPartitions;

        private PartitionId theCurrentPid;

        private AbstractScanIterator thePartitionIter;

        private int theNumRemoteTrips = 1;

        private boolean theInSortPhase1 = false;

        private boolean theInSortPhase2 = false;

        private QueryResult theSortPhase1Result;

        private Iterator<FieldValueImpl> theSortPhase1ResultIter;

        AllPartitionsIterator(RuntimeControlBlock rcb) {

            assert(theDistributionKind == DistributionKind.ALL_PARTITIONS);

            theRCB = rcb;
            theNumShards = rcb.getStore().getTopology().getNumRepGroups();
            theNumPartitions = rcb.getStore().getNPartitions();
            ResumeInfo ri = rcb.getResumeInfo();
            BitSet partitionsBitmap = ri.getPartitionsBitmap();
            int pid = ri.getCurrentPid();

            if (partitionsBitmap == null) {
                if (rcb.getTraceLevel() >= 1) {
                    rcb.trace("AllPartitionsIterator constructor. First batch");
                }
                partitionsBitmap = new BitSet(theNumPartitions + 1);
                ri.setPartitionsBitmap(partitionsBitmap);

                if (rcb.getPartitionSet() != null) {
                    ReceiveIterState state = (ReceiveIterState)
                        rcb.getState(theStatePos);
                    pid = state.theMinPid;
                    for (int i = 1; i <= theNumPartitions; ++i) {
                        if (!rcb.getPartitionSet().contains(i)) {
                            partitionsBitmap.set(i);
                        }
                    }
                } else {
                    pid = 1;
                }

                theCurrentPid = new PartitionId(pid);
                ri.setCurrentPid(pid);
                theInSortPhase1 = doesSort();
                theInSortPhase2 = false;
                ri.setIsInSortPhase1(theInSortPhase1);
            } else {
                if (rcb.getTraceLevel() >= 1) {
                    rcb.trace("AllPartitionsIterator constructor. " +
                              "ResumeInfo =\n" + ri);
                }
                theCurrentPid = new PartitionId(pid);
                if (doesSort()) {
                    theInSortPhase1 = ri.isInSortPhase1();
                    theInSortPhase2 = !theInSortPhase1;
                }
            }

            if (pid <= 0 || pid > theNumPartitions) {
                throw new IllegalStateException(
                    "Invalid partition id in continuation key: " + pid);
            }

            thePartitionIter = new AbstractScanIterator(theRCB,
                                                        theCurrentPid,
                                                        null,/* group id */
                                                        0/*emptyReadFactor*/);
        }

        boolean hasSortPhase1Result() {
            return theInSortPhase1;
        }

        @Override
        public boolean hasNext() {

            if (thePartitionIter == null) {
                return false;
            }

            ResumeInfo ri = theRCB.getResumeInfo();

            if (theInSortPhase1) {

                if (theSortPhase1ResultIter != null) {
                    return theSortPhase1ResultIter.hasNext();
                }

                if (theRCB.getTraceLevel() >= 1) {
                    theRCB.trace("AllPartitionsIterator sort phase 1 start. " +
                                 "ResumeInfo =\n" + ri);
                }

                thePartitionIter.hasNext();

                theSortPhase1Result = thePartitionIter.getQueryResult();
                theSortPhase1ResultIter = theSortPhase1Result.
                                          getQueryResults().iterator();

                assert(ri == theRCB.getResumeInfo());

                if (!ri.isInSortPhase1()) {

                    int pid = getNextPid(ri);

                    if (theRCB.getTraceLevel() >= 1) {
                        theRCB.trace("AllPartitionsIterator chose new " +
                                     " resume pid " + pid);
                    }

                    ri.reset();
                    ri.setNumResultsComputed(0);
                    ri.setCurrentPid(pid);
                    if (pid > 0) {
                        ri.setIsInSortPhase1(true);
                    }
                }

                if (theRCB.getTraceLevel() >= 1) {
                    theRCB.trace("AllPartitionsIterator sort phase 1 done. " +
                                 "ResumeInfo =\n" + ri);
                }

                theRCB.createContinuationKey(false);
                return theSortPhase1ResultIter.hasNext();
            }

            while (!thePartitionIter.hasNext()) {

                if (!thePartitionIter.hasMoreRemoteResults()) {
                    int pid;
                    if (theInSortPhase2) {
                        pid = -1;
                    } else {
                        pid = getNextPid(ri);
                        ri.setCurrentPid(pid);
                        theCurrentPid = new PartitionId(pid);
                        ++theNumRemoteTrips;
                    }

                    if (pid == -1) {
                        close();
                        theRCB.setContinuationKey(null);
                        return false;
                    }
                }

                if (theRCB.getReachedLimit()) {
                    theRCB.createContinuationKey();
                    close();
                    return false;
                }

                int emptyReadFactor = 0;
                if (theRCB.getReadKB() == 0 &&
                    theNumRemoteTrips == theNumShards) {
                    emptyReadFactor = 1;
                }

                theCurrentPid = new PartitionId(ri.getCurrentPid());

                thePartitionIter.initForNextPartition(theCurrentPid,
                                                      emptyReadFactor);
            }

            return true;
        }

        private int getNextPid(ResumeInfo ri) {

            BitSet partitionsBitmap = ri.getPartitionsBitmap();

            if (partitionsBitmap.cardinality() == theNumPartitions) {
                return -1;
            }

            int pid = theCurrentPid.getPartitionId();

            int i = pid;
            int mark = pid;

            do {
                if (!partitionsBitmap.get(i)) {
                    return i;
                }
                ++i;
                if (i > theNumPartitions) {
                    i = 1;
                }
            } while (i != mark);

            return -1;
        }

        @Override
        public FieldValueImpl next() {

            if (theSortPhase1ResultIter != null) {
                return theSortPhase1ResultIter.next();
            }

            return thePartitionIter.next();
        }

        @Override
        public FieldValueImpl nextLocal() {

            if (isClosed()) {
                return null;
            }

            if (theInSortPhase1) {

                if (theSortPhase1ResultIter != null) {

                    if (theSortPhase1ResultIter.hasNext()) {
                        return theSortPhase1ResultIter.next();
                    }

                    close();
                    return null;
                }

                ResumeInfo ri = theRCB.getResumeInfo();

                if (thePartitionIter.hasNewAsyncResult()) {

                    theSortPhase1Result = thePartitionIter.getQueryResult();
                    theSortPhase1ResultIter = theSortPhase1Result.
                                              getQueryResults().iterator();

                    if (!ri.isInSortPhase1()) {

                        int pid = getNextPid(ri);

                        if (theRCB.getTraceLevel() >= 1) {
                            theRCB.trace("AllPartitionsIterator chose new " +
                                         " resume pid " + pid);
                        }

                        ri.reset();
                        ri.setNumResultsComputed(0);
                        ri.setCurrentPid(pid);
                        if (pid > 0) {
                            ri.setIsInSortPhase1(true);
                        }
                    }

                    if (theRCB.getTraceLevel() >= 1) {
                        theRCB.trace("AllPartitionsIterator sort phase 1 done. " +
                                     "ResumeInfo =\n" + ri);
                    }

                    theRCB.createContinuationKey(false);

                    if (theSortPhase1ResultIter.hasNext()) {
                        return theSortPhase1ResultIter.next();
                    }

                    close();
                    return null;
                }

                if (theRCB.getTraceLevel() >= 1) {
                    theRCB.trace("AllPartitionsIterator sort phase 1 start. " +
                                 "ResumeInfo =\n" + ri);
                }

                thePartitionIter.sendAsyncRemoteRequest();
                return null;
            }

            FieldValueImpl res = thePartitionIter.nextLocal();

            if (res != null) {
                return res;
            }

            if (!thePartitionIter.hasMoreRemoteResults()) {

                ResumeInfo ri = theRCB.getResumeInfo();

                int pid;
                if (theInSortPhase2) {
                    pid = -1;
                } else {
                    pid = getNextPid(ri);
                    ri.setCurrentPid(pid);
                    theCurrentPid = new PartitionId(pid);
                    ++theNumRemoteTrips;
                }

                if (pid == -1) {
                    close();
                    theRCB.setContinuationKey(null);
                    return null;
                }

                if (!theRCB.getReachedLimit()) {

                    int emptyReadFactor = 0;
                    if (theRCB.getReadKB() == 0 &&
                        theNumRemoteTrips == theNumShards) {
                        emptyReadFactor = 1;
                    }

                    theCurrentPid = new PartitionId(ri.getCurrentPid());

                    thePartitionIter.initForNextPartition(theCurrentPid,
                                                          emptyReadFactor);
                }
            }

            if (theRCB.getReachedLimit()) {
                theRCB.createContinuationKey();
                close();
                return null;
            }

            thePartitionIter.sendAsyncRemoteRequest();
            return null;
        }

        @Override
        public void close() {
            if (thePartitionIter != null) {
                thePartitionIter.close();
            }
        }

        @Override
        public boolean isClosed() {
            return thePartitionIter == null || thePartitionIter.isClosed();
        }

        @Override
        public Throwable getCloseException() {
            return (thePartitionIter != null ?
                    thePartitionIter.getCloseException() :
                    null);
        }

        public int writeSortPhase1ResultInfo(
            RuntimeControlBlock rcb,
            DataOutput out) throws IOException {

            ResumeInfo ri = rcb.getResumeInfo();
            int[] pids = theSortPhase1Result.getPids();
            int[] numResultsPerPid = theSortPhase1Result.getNumResultsPerPid();
            out.writeBoolean(ri.isInSortPhase1());
            CloudSerializer.writeIntArray(pids, true, out);

            if (pids != null) {
                CloudSerializer.writeIntArray(numResultsPerPid, true, out);
                for (int i = 0; i < pids.length; ++i) {
                    ri = theSortPhase1Result.getResumeInfo(i);
                    byte[] contKey = null;
                    if (ri != null) {
                        contKey = RuntimeControlBlock.
                            createContinuationKey(pids[i], ri);
                    }
                    CloudSerializer.writeByteArray(out, contKey);
                }
            }

            return 0;
        }

        @Override
        public List<DetailedMetrics> getPartitionMetrics() {
            return Collections.emptyList();
        }

        @Override
        public List<DetailedMetrics> getShardMetrics() {
            return Collections.emptyList();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * SequentialShardsIterator queries either a single specified shard or all
     * of the shards. In the later case, the shards are queried sequentially.
     * The single-shard case applies when the query does sorting. Otherwise, the
     * all-shards case applies.
     */
    private class SequentialShardsIterator
        implements AsyncTableIterator<FieldValueImpl> {

        private final RuntimeControlBlock theRCB;

        private final RepGroupId[] theShards;

        /* The current shard scan iterator */
        private AbstractScanIterator theShardIter;

        SequentialShardsIterator(RuntimeControlBlock rcb) {

            theRCB = rcb;

            Set<RepGroupId> allShardIds = null;
            Set<RepGroupId> shardIds = rcb.getShardSet();
            if (shardIds == null) {
                shardIds = rcb.getStore().getTopology().getRepGroupIds();
                allShardIds = shardIds;
            } else {
                assert(shardIds.size() == 1);
            }

            theShards = shardIds.toArray(new RepGroupId[shardIds.size()]);

            int shardIdx = theRCB.getShardIdx();

            if (shardIdx < 0 || shardIdx >= theShards.length) {
                throw new IllegalArgumentException(
                    "Invalid shard id in continuation key: " +
                    shardIdx);
            }

            /* If it' a sorting query, and no read KBs have been consumed so far,
             * and the current shard is the last one, set emptyReadFactor to 1. */
            int emptyReadFactor = 0;
            if (theShards.length == 1 &&
                rcb.getResumeInfo().getTotalReadKB() == 0) {

                if (allShardIds == null) {
                    allShardIds = rcb.getStore().getTopology().getRepGroupIds();
                }

                if (theShards[0].getGroupId() == allShardIds.size()) {
                    emptyReadFactor = 1;
                }
            }

            theShardIter = new AbstractScanIterator(theRCB,
                                                    null, /*partition id*/
                                                    theShards[shardIdx],
                                                    emptyReadFactor);
        }

        @Override
        public boolean hasNext() {

            if (theShardIter == null) {
                return false;
            }

            while (!theShardIter.hasNext()) {

                if (!theShardIter.hasMoreRemoteResults()) {

                    int shardIdx = theRCB.incShardIdx();

                    if (shardIdx == theShards.length) {
                        close();
                        theRCB.setContinuationKey(null);
                        return false;
                    }

                    if (!theRCB.getReachedLimit()) {

                        /* Open iterator on next shard. Set emptyReadFactor to 1
                         * if no entry read until scan on the last shard. */
                        int emptyReadFactor =
                            (theRCB.getReadKB() == 0 &&
                             shardIdx == theShards.length - 1 ? 1 : 0);

                        theShardIter.initForNextShard(theShards[shardIdx],
                                                      emptyReadFactor);
                    }
                }

                if (theRCB.getReachedLimit()) {
                    theRCB.createContinuationKey();
                    close();
                    return false;
                }
            }

            return true;
        }

        @Override
        public FieldValueImpl next() {
            return theShardIter.next();
        }

        @Override
        public FieldValueImpl nextLocal() {

            if (isClosed()) {
                return null;
            }

            FieldValueImpl res = theShardIter.nextLocal();

            if (res != null) {
                return res;
            }

            if (!theShardIter.hasMoreRemoteResults()) {

                int shardIdx = theRCB.incShardIdx();

                if (shardIdx == theShards.length) {
                    close();
                    theRCB.setContinuationKey(null);
                    return null;
                }

                if (!theRCB.getReachedLimit()) {

                    int emptyReadFactor =
                        (theRCB.getReadKB() == 0 &&
                         shardIdx == theShards.length - 1 ? 1 : 0);

                    theShardIter.initForNextShard(theShards[shardIdx],
                                                  emptyReadFactor);
                }
            }

            if (theRCB.getReachedLimit()) {
                theRCB.createContinuationKey();
                close();
                return null;
            }

            theShardIter.sendAsyncRemoteRequest();
            return null;
        }

        @Override
        public void close() {
            if (theShardIter != null) {
                theShardIter.close();
            }
        }

        @Override
        public boolean isClosed() {
            return theShardIter == null || theShardIter.isClosed();
        }

        @Override
        public Throwable getCloseException() {
            return (theShardIter != null ?
                    theShardIter.getCloseException() :
                    null);
        }

        @Override
        public List<DetailedMetrics> getPartitionMetrics() {
            return Collections.emptyList();
        }

        @Override
        public List<DetailedMetrics> getShardMetrics() {
            return Collections.emptyList();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Implements iterative table scan in a single partition/shard. Used by
     * the SequentialPartitionsIterator and SequentialShardsIterator.
     *
     * Note: No synchronization is needed for async mode, because there can
     * only a single pending remote request in the cases where an
     * AbstractScanIterator is used.
     */
    private class AbstractScanIterator {

        private final RuntimeControlBlock theRCB;

        private PartitionId thePid;

        private RepGroupId theGroupId;

        private int theMaxResults;

        private int theMaxReadKB;

        private int theMaxWriteKB;

        private int theEmptyReadFactor;

        private QueryResult theResult;

        private Iterator<FieldValueImpl> theResultsIter;

        private boolean theMoreRemoteResults;

        private Throwable theAsyncException;

        private boolean theHasNewAsyncResult;

        private boolean theAsyncRequestExecuting;

        private boolean theIsClosed;

        public AbstractScanIterator(
            RuntimeControlBlock rcb,
            PartitionId pid,
            RepGroupId gid,
            int emptyReadFactor) {

            theRCB = rcb;
            thePid = pid;
            theGroupId = gid;
            theMaxResults = theRCB.getBatchSize();
            theMaxReadKB = theRCB.getMaxReadKB();
            theMaxWriteKB = theRCB.getMaxWriteKB();

            theMoreRemoteResults = true;
            theResultsIter = null;

            theEmptyReadFactor = emptyReadFactor;
        }

        void initForNextPartition(PartitionId pid, int emptyReadFactor) {
            initForNextScan(pid, null, emptyReadFactor);
        }

        void initForNextShard(RepGroupId gid, int emptyReadFactor) {
            initForNextScan(null, gid, emptyReadFactor);
        }

        private void initForNextScan(
            PartitionId pid,
            RepGroupId gid,
            int emptyReadFactor) {

            thePid = pid;
            theGroupId = gid;

            theRCB.getResumeInfo().reset();
            theRCB.getResumeInfo().setNumResultsComputed(0);

            theMoreRemoteResults = true;
            theResultsIter = null;

            theEmptyReadFactor = emptyReadFactor;
        }

        /* Create request for TableQuery operation */
        Request createRequest() {

            TableQuery op = new TableQuery(
                        theDistributionKind,
                        theInputType,
                        theMayReturnNULL,
                        (theSerializedInputIter == null ?
                         SerialVersion.CURRENT :                         
                         theSerializedInputIter.getSerialVersion()),
                        ReceiveIter.this,
                        theRCB.getExternalVars(),
                        theNumIters,
                        theNumRegs,
                        theTableId,
                        theRCB.getMathContext(),
                        theRCB.getTraceLevel(),
                        theMaxResults,
                        theRCB.getMaxReadKB(),
                        theMaxReadKB,
                        theMaxWriteKB,
                        theRCB.getResumeInfo(),
                        theEmptyReadFactor,
                        theRCB.getDeleteLimit());

            final Consistency consistency = theRCB.getConsistency();
            final Durability durability = theRCB.getDurability();
            final long timeout = theRCB.getTimeout();
            final TimeUnit timeUnit = theRCB.getTimeUnit();
            final KVStoreImpl store = theRCB.getStore();
            final ExecuteOptions execOptions = theRCB.getExecuteOptions();

            if (thePid != null) {

                if (theIsUpdate) {
                    final Request req = store.makeWriteRequest(
                        op, thePid, durability, timeout, timeUnit);
                    if (execOptions != null) {
                        req.setLogContext(execOptions.getLogContext());
                        req.setAuthContext(execOptions.getAuthContext());
                    }
                    return req;
                }

                final Request req = store.makeReadRequest(
                    op, thePid, consistency, timeout, timeUnit);
                if (execOptions != null) {
                    req.setLogContext(execOptions.getLogContext());
                    req.setAuthContext(execOptions.getAuthContext());
                }
                return req;
            }

            if (theIsUpdate) {
                final Request req = store.makeWriteRequest(
                    op, theGroupId, durability, timeout, timeUnit);
                if (execOptions != null) {
                    req.setLogContext(execOptions.getLogContext());
                    req.setAuthContext(execOptions.getAuthContext());
                }
                return req;
            }

            final Request req = store.makeReadRequest(
                op, theGroupId, consistency, timeout, timeUnit);
            if (execOptions != null) {
                req.setLogContext(execOptions.getLogContext());
                req.setAuthContext(execOptions.getAuthContext());
            }
            return req;
        }

        boolean hasMoreRemoteResults() {
            return theMoreRemoteResults;
        }

        QueryResult getQueryResult() {
            return theResult;
        }

        boolean hasNewAsyncResult() {
            return theHasNewAsyncResult;
        }

        boolean hasNext() {

            if (theResultsIter != null && theResultsIter.hasNext()) {
                return true;
            }

            theResultsIter = null;

            if (!theMoreRemoteResults || theRCB.getReachedLimit()) {
                return false;
            }

            Request req = createRequest();

            if (theRCB.getTraceLevel() >= 2) {
                if (thePid != null) {
                    theRCB.trace("AbstractScanIterator: Executing remote " +
                                 "request for partition " + thePid +
                                 " with read limit = " + theMaxReadKB);
                } else {
                    theRCB.trace("AbstractScanIterator: Executing remote " +
                                 "request for shard " + theGroupId +
                                 " with read limit = " + theMaxReadKB);
                }
            }

            KVStoreImpl store = theRCB.getStore();
            theResult = (QueryResult)store.executeRequest(req);

            return processResults();
        }

        FieldValueImpl next() {
            if (!theResultsIter.hasNext()) {
                throw new NoSuchElementException();
            }
            return theResultsIter.next();
        }

        FieldValueImpl nextLocal() {

            if (theResultsIter != null && theResultsIter.hasNext()) {
                return theResultsIter.next();
            }

            return null;
        }

        void sendAsyncRemoteRequest() {

            /* Initiate a request if one isn't already underway */
            if (theAsyncRequestExecuting) {
                return;
            }

            theAsyncRequestExecuting = true;

            Request request = createRequest();

            if (theRCB.getTraceLevel() >= 2) {
                if (thePid != null) {
                    theRCB.trace("AbstractScanIterator: Executing remote " +
                                 "request for partition " + thePid +
                                 " with read limit = " + theMaxReadKB);
                } else {
                    theRCB.trace("AbstractScanIterator: Executing remote " +
                                 "request for shard " + theGroupId +
                                 " with read limit = " + theMaxReadKB);
                }
            }

            theRCB.getStore().executeRequestAsync(request)
                .whenComplete(
                    unwrapExceptionVoid(
                        (r, e) ->
                        handleAsyncResult((QueryResult) r, e)))
                .whenComplete(
                    unwrapExceptionVoid(
                        e -> theRCB.getLogger().log(
                            Level.WARNING, "Unexpected exception: " + e, e)));

            return;
        }

        private void handleAsyncResult(QueryResult r, Throwable e) {

            assert !Thread.holdsLock(this);

            ReceiveIterState state =
                (ReceiveIterState) theRCB.getState(theStatePos);

            if (r != null) {
                theResult = r;
                processResults();
                theHasNewAsyncResult = true;
            } else {
                theAsyncException = e;
                close();

                if (theRCB.getTraceLevel() >= 2) {
                    theRCB.trace(
                        "AbstractScanIterator: Got remote exception:\n" + e);
                }
            }

            theAsyncRequestExecuting = false;
            state.theAsyncNotifier.notifyNext();
        }

        private boolean processResults() {

            final List<FieldValueImpl> results = theResult.getQueryResults();

            theMoreRemoteResults = theResult.hasMoreElements();

            theRCB.getResumeInfo().refresh(theResult.getResumeInfo());

            theRCB.tallyReadKB(theResult.getReadKB());
            theRCB.tallyWriteKB(theResult.getWriteKB());
            if (theRCB.getUseBatchSizeAsLimit()) {
                theRCB.tallyResultSize(results.size());
            }

            if (theRCB.getTraceLevel() >= 2) {
                theRCB.trace("AbstractScanIterator: received " +
                             results.size() + " results from partition " +
                             thePid + ". Num bytes read = " +
                             theResult.getReadKB() + " more remote results = " +
                             theMoreRemoteResults + " reached byte limit =" +
                             theResult.getExceededSizeLimit());
            }

            /* Calculate the remaining maxRead/WriteKB for next fetch, if any */
            if (!theResult.getExceededSizeLimit()) {
                theMaxReadKB -= theResult.getReadKB();
                theMaxWriteKB -= theResult.getWriteKB();
                if (theRCB.getUseBatchSizeAsLimit()) {
                    theMaxResults -= results.size();
                }
            }

            boolean reachedLimit =
                (theResult.getExceededSizeLimit() ||
                 (theRCB.getMaxReadKB() > 0 && theMaxReadKB <= 0) ||
                 (theRCB.getMaxWriteKB() > 0 && theMaxWriteKB <= 0) ||
                 (theRCB.getUseBatchSizeAsLimit() && theMaxResults <= 0));

            theRCB.setReachedLimit(reachedLimit);

            if (theRCB.getTraceLevel() >= 2) {
                theRCB.trace("AbstractScanIterator: remaining limits = (" +
                             theMaxReadKB + ", " + theMaxWriteKB + ", " +
                             theMaxResults + ") reached limit = " +
                             reachedLimit);
            }

            if (results.isEmpty()) {
                assert(theResult.getExceededSizeLimit() || !theMoreRemoteResults);
                return false;
            }

            theResultsIter = results.iterator();
            if (thePid != null) {
                thePid = new PartitionId(theRCB.getResumeInfo().getCurrentPid());
            }

            return true;
        }

        void close() {
            theResultsIter = null;
            theResult = null;
            theIsClosed = true;
        }

        boolean isClosed() {
            return theIsClosed;
        }

        public synchronized Throwable getCloseException() {
            return theAsyncException;
        }
    }
}
