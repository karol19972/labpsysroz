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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.MathContext;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.Durability;
import oracle.kv.ParallelScanIterator;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.ops.TableQuery;
import oracle.kv.impl.api.ops.TableQueryHandler;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.UserDataControl;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.table.FieldValue;

/**
 *
 */
public class RuntimeControlBlock {

    /*
     * KVStoreImpl is set on the client side and is used by the dispatch
     * code that sends queries to the server side. Not applicable to the
     * server RCBs.
     */
    private final KVStoreImpl theStore;

    private final Logger theLogger;

    /*
     * TableMetadataHelper is required by operations to resolve table and
     * index names.
     */
    private final TableMetadataHelper theMetadataHelper;

    /*
     * To achieve improved client side parallelization when satisfying a
     * query, some clients (for example, the Hive/BigDataSQL mechanism)
     * will distribute requests among separate processes and then scan
     * either a set of partitions or shards; depending on the distribution
     * kind (ALL_PARTITIONS, SINGLE_PARTITION, ALL_SHARDS) computed for
     * the given query. Not applicable to the server RCBs.
     */
    private Set<Integer> thePartitions;

    private Set<RepGroupId> theShards;

    /*
     * ExecuteOptions are options set by the application and used to control
     * some aspects of database access, such as Consistency, timeouts, batch
     * sizes, etc. Not applicable to the server RCBs (for server RCBs, the
     * options are caried in theQueryOp).
     */
    private final ExecuteOptions theExecuteOptions;

    private final byte theTraceLevel;

    /* The TableQuery operation. Not applicable to the client RCB. */
    private TableQuery theQueryOp;

    /* The TableQueryHandler. Not applicable to the client RCB. */
    private TableQueryHandler theQueryHandler;

    private final PlanIter theRootIter;

    /*
     * See javadoc for ServerIterFactory. Not applicable to the client RCB.
     */
    private final ServerIterFactory theServerIterFactory;

    /*
     * The state array contains as many elements as there are PlanIter instances
     * in the query plan to be executed.
     */
    private final PlanIterState[] theIteratorStates;

    /*
     * The register array contains as many elements as required by the
     * instances in the query plan to be executed.
     */
    private final FieldValueImpl[] theRegisters;

    /*
     * An array storing the values of the extenrnal variables set for the
     * operation. These come from the map in the BoundStatement.
     */
    private final FieldValue[] theExternalVars;

    /* Not applicable to the server RCBs. */
    private byte[] theContinuationKey;

    /*
     * The following 2 fields store the deserialized values from the
     * continuation key given back to us by the app. Not applicable
     * to the server RCBs.
     */
    private int thePidOrShardIdx;

    /*
     * Not applicable to the server RCBs. (at the servers, the ResumeInfo is
     * in theQueryOp).
     */
    private ResumeInfo theResumeInfo;

    /*
     * At the proxy, these are the total readKB/writeKB consumed during
     * execution of the current query batch. Not applicable to the server RCBs.
     * (at the servers, theRead/WriteKB are* in theQueryOp).
     */
    private int theReadKB;
    private int theWriteKB;

    /* The total number of records returned */
    private int theResultSize;

    /* The flag indicates if reaches the size-based or number-based limit. */
    private boolean theReachedLimit;

    /*
     * The RCB holds the TableIterator for the current remote call from the
     * ReceiveIter if there is one. This is here so that the query results
     * objects can return partition and shard metrics for the distributed
     * query operation. Not applicable to the server RCBs.
     */
    private ParallelScanIterator<FieldValueImpl> theTableIterator;

    /*
     * The number of memory bytes consumed by the query at the client for
     * blocking operations (duplicate eleimination, sorting). Not applicable
     * to the server RCBs.
     */
    private long theMemoryConsumption;

    /**
     * Constructor used at the client only.
     */
    public RuntimeControlBlock(
        KVStoreImpl store,
        Logger logger,
        TableMetadataHelper mdHelper,
        Set<Integer> partitions,
        Set<RepGroupId> shards,
        ExecuteOptions executeOptions,
        PlanIter rootIter,
        int numIters,
        int numRegs,
        FieldValue[] externalVars) {

        theStore = store;
        theLogger = logger;
        theMetadataHelper = mdHelper;

        thePartitions = partitions;
        theShards = shards;

        theExecuteOptions = executeOptions;
        theTraceLevel = (executeOptions != null ?
                         executeOptions.getTraceLevel() :
                         0);

        theRootIter = rootIter;

        theIteratorStates = new PlanIterState[numIters];
        theRegisters = new FieldValueImpl[numRegs];
        theExternalVars = externalVars;

        theContinuationKey = (theExecuteOptions != null ?
                              theExecuteOptions.getContinuationKey() :
                              null);
        parseContinuationKey();

        theServerIterFactory = null;
    }

    /**
     * Constructor used at the RNs only.
     */
    public RuntimeControlBlock(
        Logger logger,
        TableMetadataHelper mdHelper,
        ExecuteOptions options,
        TableQuery queryOp,
        TableQueryHandler queryHandler,
        ServerIterFactory serverIterFactory) {

        theStore = null;
        thePartitions = null;
        theShards = null;

        theExecuteOptions = options;

        theLogger = logger;
        theMetadataHelper = mdHelper;

        theQueryOp = queryOp;
        theQueryHandler = queryHandler;
        theServerIterFactory = serverIterFactory;

        theTraceLevel = queryOp.getTraceLevel();
        theExternalVars = queryOp.getExternalVars();
        theRootIter = queryOp.getQueryPlan();

        theIteratorStates = new PlanIterState[queryOp.getNumIterators()];
        theRegisters = new FieldValueImpl[queryOp.getNumRegisters()];

        theResumeInfo = theQueryOp.getResumeInfo();
    }

    boolean isServerRCB() {
        return theStore == null;
    }

    public KVStoreImpl getStore() {
        return theStore;
    }

    public Logger getLogger() {
        return theLogger;
    }

    public TableMetadataHelper getMetadataHelper() {
        return theMetadataHelper;
    }

    public Set<Integer> getPartitionSet() {
        return thePartitions;
    }

    void setPartitionSet(Set<Integer> parts) {
        if (thePartitions != null) {
            thePartitions.retainAll(parts); 
        } else {
            thePartitions = parts;
        }
    }

    void setShardSet(Set<RepGroupId> shards) {
       if (theShards != null) {
            theShards.retainAll(shards); 
        } else {
            theShards = shards;
        }
    }

    public Set<RepGroupId> getShardSet() {
        return theShards;
    }

    public ExecuteOptions getExecuteOptions() {
        return theExecuteOptions;
    }

    public byte getTraceLevel() {
        return theTraceLevel;
    }

    public void trace(String msg) {
        if (!UserDataControl.hideUserData()) {
            if (isServerRCB()) {
                theLogger.info("QUERY: " + msg);
            } else {
                System.out.println(
                    "QUERY(" + Thread.currentThread().getId() + ") : " + msg);
            }
        }
    }

    Consistency getConsistency() {
        return theExecuteOptions.getConsistency();
    }

    Durability getDurability() {
        return theExecuteOptions.getDurability();
    }

    long getTimeout() {
        return theExecuteOptions.getTimeout();
    }

    TimeUnit getTimeUnit() {
        return theExecuteOptions.getTimeoutUnit();
    }

    short getCompilerVersion() {
        return (isServerRCB() ?
                theQueryOp.getCompilerVersion() :
                SerialVersion.CURRENT);
    }

    public MathContext getMathContext() {
        return (isServerRCB() ?
                theQueryOp.getMathContext() :
                theExecuteOptions.getMathContext());
    }

    int getBatchSize() {
        return (isServerRCB() ?
                theQueryOp.getBatchSize() :
                theExecuteOptions.getResultsBatchSize());
    }

    public boolean getUseBatchSizeAsLimit() {
        return theExecuteOptions.getUseBatchSizeAsLimit();
    }

    public boolean getUseBytesLimit() {
        return getMaxReadKB() > 0;
    }

    public int getMaxReadKB() {
        return (isServerRCB() ?
                theQueryOp.getMaxReadKB() :
                theExecuteOptions.getMaxReadKB());
    }

    public int getMaxWriteKB() {
        return theExecuteOptions.getMaxWriteKB();
    }

    public long getMaxMemoryConsumption() {
        return theExecuteOptions.getMaxMemoryConsumption();
    }

    public int getDeleteLimit() {
        return (isServerRCB() ?
                theQueryOp.getDeleteLimit() :
                theExecuteOptions.getDeleteLimit());
    }

    boolean isProxyQuery() {
        return theExecuteOptions.isProxyQuery();
    }
    void incMemoryConsumption(long v) {
        theMemoryConsumption += v;
        assert(theMemoryConsumption >= 0);

        if (theMemoryConsumption > getMaxMemoryConsumption()) {
            throw new QueryStateException(
                "Memory consumption at the client exceeded maximum " +
                "allowed value " + getMaxMemoryConsumption());
        }
    }

    void decMemoryConsumption(long v) {
        theMemoryConsumption -= v;
        assert(theMemoryConsumption >= 0);
    }

    public TableQuery getQueryOp() {
        return theQueryOp;
    }

    public TableQueryHandler getQueryHandler() {
        return theQueryHandler;
    }

    public int getShardId() {
        return theQueryHandler.getRepNode().getRepNodeId().getGroupId();
    }

    public ResumeInfo getResumeInfo() {
        return theResumeInfo;
    }

    void setResumeInfo(ResumeInfo ri) {
        theResumeInfo = ri;
    }

    public ServerIterFactory getServerIterFactory() {
        return theServerIterFactory;
    }

    PlanIter getRootIter() {
        return theRootIter;
    }

    public void setState(int pos, PlanIterState state) {
        theIteratorStates[pos] = state;
    }

    public PlanIterState getState(int pos) {
        return theIteratorStates[pos];
    }

    public FieldValueImpl[] getRegisters() {
        return theRegisters;
    }

    public FieldValueImpl getRegVal(int regId) {
        return theRegisters[regId];
    }

    public void setRegVal(int regId, FieldValueImpl value) {
        theRegisters[regId] = value;
    }

    FieldValue[] getExternalVars() {
        return theExternalVars;
    }

    FieldValueImpl getExternalVar(int id) {

        if (theExternalVars == null) {
            return null;
        }
        return (FieldValueImpl)theExternalVars[id];
    }

    public int getCurrentMaxReadKB() {
        return theQueryOp.getCurrentMaxReadKB();
    }

    public int getCurrentMaxWriteKB() {
        return theQueryOp.getCurrentMaxWriteKB();
    }

    public byte[] getContinuationKey() {
        return theContinuationKey;
    }

    public void setContinuationKey(byte[] key) {
        theContinuationKey = key;
    }

    public void tallyReadKB(int nkb) {
        assert(!isServerRCB());
        theReadKB += nkb;
    }

    public void tallyWriteKB(int nkb) {
        assert(!isServerRCB());
        theWriteKB += nkb;
    }

    public int getReadKB() {
        assert(!isServerRCB());
        return theReadKB;
    }

    public int getWriteKB() {
        assert(!isServerRCB());
        return theWriteKB;
    }

    public void tallyResultSize(int size) {
        theResultSize += size;
    }

    public int getResultSize() {
        return theResultSize;
    }

    public void setReachedLimit(boolean value) {
        theReachedLimit = value;
    }

    public boolean getReachedLimit() {
        /*
         * Even if theReachedLimit is true, the method should return always
         * false at the (non-cloud) fat client. theReachedLimit may be set to
         * true in this case because in ReceiveIter, the code that handles
         * single-partition queries is shared for the cloud and non-cloud cases. 
         */
        return (isServerRCB() ||
                getUseBytesLimit() ||
                getUseBatchSizeAsLimit() ?
                theReachedLimit :
                false);
    }

    int getPidIdx() {
        return thePidOrShardIdx;
    }

    int incPidIdx() {
        return (++thePidOrShardIdx);
    }

    int getShardIdx() {
        return thePidOrShardIdx;
    }

    int incShardIdx() {
        return (++thePidOrShardIdx);
    }

    private void parseContinuationKey() {

        if (theContinuationKey == null) {
            theResumeInfo = new ResumeInfo(this);
            return;
        }

        final ByteArrayInputStream bais =
            new ByteArrayInputStream(theContinuationKey);
        final DataInput in = new DataInputStream(bais);

        short v = SerialVersion.CURRENT;

        try {
            thePidOrShardIdx = in.readInt();

            theResumeInfo = new ResumeInfo(in, v);
            theResumeInfo.setRCB(this);

        } catch (IOException e) {
            throw new QueryStateException(
                "Failed to parse continuation key");
        }
    }

    void createContinuationKey() {
        createContinuationKey(true);
    }

    void createContinuationKey(boolean setReachedLimit) {

        theContinuationKey = createContinuationKey(thePidOrShardIdx,
                                                   theResumeInfo);
        if (setReachedLimit) {
            theReachedLimit = true;
        }
    }

    static byte[] createContinuationKey(int pid, ResumeInfo ri) {

        final ByteArrayOutputStream baos =
            new ByteArrayOutputStream();
        final DataOutput out = new DataOutputStream(baos);

        short v = SerialVersion.CURRENT;

        try {
            out.writeInt(pid);
            ri.writeFastExternal(out, v);
        } catch (IOException e) {
            throw new QueryStateException(
                "Failed to create continuation key. Reason:\n" +
                e.getMessage());
        }

        return baos.toByteArray();
    }

    void setTableIterator(ParallelScanIterator<FieldValueImpl> iter) {
        theTableIterator = iter;
    }

    public ParallelScanIterator<FieldValueImpl> getTableIterator() {
        return theTableIterator;
    }
}
