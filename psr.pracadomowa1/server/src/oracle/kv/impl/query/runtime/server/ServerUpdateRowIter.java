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

package oracle.kv.impl.query.runtime.server;

import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.ReturnValueVersion;
import oracle.kv.Value;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.KeySerializer;
import oracle.kv.impl.api.ops.Put;
import oracle.kv.impl.api.ops.PutHandler;
import oracle.kv.impl.api.ops.Result;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.IntegerValueImpl;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RecordValueImpl;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.compiler.ExprUpdateRow;
import oracle.kv.impl.query.runtime.InsertRowIter;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.runtime.PlanIterState;
import oracle.kv.impl.query.runtime.RuntimeControlBlock;
import oracle.kv.impl.query.runtime.UpdateRowIter;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.table.TimeToLive;

/**
 *
 */
public class ServerUpdateRowIter extends UpdateRowIter {

    ServerIterFactoryImpl theOpContext;

    PutHandler thePutHandler;

    static IntegerValueImpl one = FieldDefImpl.integerDef.createInteger(1);

    static IntegerValueImpl zero = FieldDefImpl.integerDef.createInteger(0);

    ServerUpdateRowIter(UpdateRowIter parent) {
        super(parent);
    }

    @Override
    public void open(RuntimeControlBlock rcb) {

        rcb.setState(theStatePos, new UpdateRowState(this));

        theInputIter.open(rcb);
        for (PlanIter updIter : theUpdateOps) {
            updIter.open(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.open(rcb);
        }

        theOpContext = (ServerIterFactoryImpl)rcb.getServerIterFactory();
        thePutHandler = new PutHandler(theOpContext.getOperationHandler());
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);

        theInputIter.close(rcb);
        for (PlanIter updIter : theUpdateOps) {
            updIter.close(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.close(rcb);
        }

        state.close();
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {
        theInputIter.reset(rcb);
        for (PlanIter updIter : theUpdateOps) {
            updIter.reset(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.reset(rcb);
        }

        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        boolean more = theInputIter.next(rcb);

        if (!more) {

            if (rcb.getReachedLimit()) {
                throw new QueryException(
                    "Query cannot be executed further because the " +
                    "computation of a single result consumes " +
                    "more bytes than the maximum allowed.",
                    theLocation);
            }

            if (theHasReturningClause) {
                state.done();
                return false;
            }
            rcb.setRegVal(theResultReg, zero);
            state.done();
            return true;
        }

        int inputReg = theInputIter.getResultReg();
        FieldValueImpl inVal = rcb.getRegVal(inputReg);

        if (!(inVal instanceof RowImpl)) {
            throw new QueryStateException(
                "Update statement expected a row, but got this field value:\n" +
                inVal);
        }

        RowImpl row = (RowImpl)inVal;
        boolean updated = false;
        boolean updateTTL = false;

        for (PlanIter updFieldIter : theUpdateOps) {
            if (updFieldIter.next(rcb)) {
                updated = true;
            }
            updFieldIter.reset(rcb);
        }

        TimeToLive ttlObj = InsertRowIter.
            setRowExpTime(rcb, row, theUpdateTTL, theTTLIter,
                          theTTLUnit, theLocation);

        if (ttlObj != null) {
            updated = true;
            updateTTL = true;
        }

        if (rcb.getTraceLevel() >= 1) {
            rcb.trace("Row after update =\n" + row);
        }

        Result res = null;

        if (updated) {
            KVStore kvstore = thePutHandler.getOperationHandler().
                getRepNode().getKVStore();
            TableImpl table = row.getTableImpl();
            if (table.hasIdentityColumn()) {
                RecordDefImpl rowDef = table.getRowDef();
                int idCol = table.getIdentityColumn();
                FieldValueImpl userValue = row.get(idCol);
                boolean isOnNull = table.isIdentityOnNull();
                /*fill the sequence number only if identity column is updated to
                 * NULL and the SG type is by default on null*/
                if (isOnNull && userValue.isNull()) {
                    FieldValueImpl generatedValue =
                        ((KVStoreImpl)kvstore).getIdentityNextValue(table,
                            rowDef.getFieldDef(idCol), 0, userValue, idCol);
                    if (generatedValue != null) {
                        row.putInternal(idCol, generatedValue, false);
                    }

                }
            }

            if (row.isFromMRTable()) {
                TableAPIImpl.setLocalRegionId(row);
            }

            Key rowkey = row.getPrimaryKey(false/*allowPartial*/);
            Value rowval = row.createValue();

            KeySerializer keySerializer =
                KeySerializer.PROHIBIT_INTERNAL_KEYSPACE;
            byte[] keybytes = keySerializer.toByteArray(rowkey);

            Put put = new Put(keybytes,
                              rowval,
                              ReturnValueVersion.Choice.NONE,
                              row.getTableImpl().getId(),
                              ttlObj,
                              updateTTL);

            /*
             * Configures the ThroughputTracker of Put op with the
             * ThroughputTracker of TableQuery.
             */
            put.setThroughputTracker(rcb.getQueryOp());

            PartitionId pid = new PartitionId(rcb.getResumeInfo().getCurrentPid());
            res = thePutHandler.execute(put, theOpContext.getTxn(), pid);

            /* Tally write KB to RuntimeControlBlock.writeKB */
            rcb.getQueryOp().addWriteKB(put.getWriteKB());
        }

        rcb.getResumeInfo().setPrimResumeKey(null);

        if (theHasReturningClause) {

            if (res != null) {
                row.setVersion(res.getNewVersion());
            }
            rcb.setRegVal(theResultReg, row);
        } else {
            RecordValueImpl retval =
                ExprUpdateRow.theNumRowsUpdatedType.createRecord();
            retval.put(0, (updated ? one : zero));
            rcb.setRegVal(theResultReg, retval);
        }

        state.done();
        return true;
    }
}
