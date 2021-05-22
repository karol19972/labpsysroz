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

import static oracle.kv.impl.async.FutureUtils.unwrapExceptionVoid;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import oracle.kv.Durability;
import oracle.kv.Key;
import oracle.kv.ReturnValueVersion.Choice;
import oracle.kv.Value;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.KeySerializer;
import oracle.kv.impl.api.Request;
import oracle.kv.impl.api.ops.Put;
import oracle.kv.impl.api.ops.PutIfAbsent;
import oracle.kv.impl.api.ops.Result.PutResult;
import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.FieldValueSerialization;
import oracle.kv.impl.api.table.IntegerValueImpl;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RecordValueImpl;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.impl.api.table.TimestampDefImpl;
import oracle.kv.impl.async.IterationHandleNotifier;
import oracle.kv.impl.query.QueryException.Location;
import oracle.kv.impl.query.compiler.Expr;
import oracle.kv.impl.query.compiler.ExprInsertRow;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.util.SerializationUtil;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.table.TimeToLive;

/**
 *
 */
public class InsertRowIter extends PlanIter {

    public static class InsertRowState extends PlanIterState {

        RowImpl theRTRow;

        volatile IterationHandleNotifier theAsyncNotifier;

        boolean theAsyncRequestExecuting;

        PartitionId thePid;

        volatile Throwable theAsyncException;

        volatile PutResult theAsyncResult;

        public InsertRowState(RowImpl row) {
            theRTRow = row;
        }
    }

    static IntegerValueImpl one = FieldDefImpl.integerDef.createInteger(1);

    static IntegerValueImpl zero = FieldDefImpl.integerDef.createInteger(0);

    static KeySerializer keySerializer =
        KeySerializer.PROHIBIT_INTERNAL_KEYSPACE;

    final String theNamespace;

    final String theTableName;

    final RecordValueImpl theRow;

    final int[] theColPositions;

    final PlanIter[] theColIters;

    protected boolean theUpdateTTL;

    protected PlanIter theTTLIter;

    protected TimeUnit theTTLUnit;

    final boolean theIsUpsert;

    final boolean theHasReturningClause;

    public InsertRowIter(
        Expr e,
        int resultReg,
        TableImpl table,
        RecordValueImpl row,
        int[] pos,
        PlanIter[] ops,
        boolean updateTTL,
        PlanIter ttlIter,
        TimeUnit ttlUnit,
        boolean isUpsert,
        boolean hasReturningClause) {

        super(e, resultReg);
        theNamespace = table.getInternalNamespace();
        theTableName = table.getFullName();
        theRow = row;
        theColPositions = pos;
        theColIters = ops;
        theUpdateTTL = updateTTL;
        theTTLIter = ttlIter;
        theTTLUnit = ttlUnit;
        assert(theTTLIter == null || theUpdateTTL);
        theIsUpsert = isUpsert;
        theHasReturningClause = hasReturningClause;
    }

    public InsertRowIter(DataInput in, short serialVersion) throws IOException {

        super(in, serialVersion);
        theNamespace = SerializationUtil.readString(in, serialVersion);
        theTableName = SerializationUtil.readString(in, serialVersion);

        theRow = FieldValueSerialization.readRecord(null, // record def
                                                    true, // partial
                                                    in,
                                                    serialVersion);

        theColPositions = deserializeIntArray(in, serialVersion);
        theColIters = deserializeIters(in, serialVersion);
        theUpdateTTL = in.readBoolean();
        if (theUpdateTTL) {
            theTTLIter = deserializeIter(in, serialVersion);
            if (theTTLIter != null) {
                theTTLUnit = TimeToLive.readTTLUnit(in, 1);
            }
        }
        theIsUpsert = in.readBoolean();
        theHasReturningClause = in.readBoolean();
    }

    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {

        super.writeFastExternal(out, serialVersion);
        SerializationUtil.writeString(out, serialVersion, theNamespace);
        SerializationUtil.writeString(out, serialVersion, theTableName);

        FieldValueSerialization.writeRecord(theRow,
                                            true, // writeValDef
                                            true, // partial
                                            out,
                                            serialVersion);

        serializeIntArray(theColPositions, out, serialVersion);
        serializeIters(theColIters, out, serialVersion);
        out.writeBoolean(theUpdateTTL);
        if (theUpdateTTL) {
            serializeIter(theTTLIter, out, serialVersion);
            if (theTTLIter != null) {
                out.writeByte((byte) theTTLUnit.ordinal());
            }
        }
        out.writeBoolean(theIsUpsert);
        out.writeBoolean(theHasReturningClause);
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.INSERT_ROW;
    }

    @Override
    public void setIterationHandleNotifier(
        RuntimeControlBlock rcb,
        IterationHandleNotifier notifier) {

        InsertRowState state = (InsertRowState)rcb.getState(theStatePos);
        state.theAsyncNotifier = notifier;
    }

    @Override
    public void open(RuntimeControlBlock rcb) {

        for (PlanIter colIter : theColIters) {
            colIter.open(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.open(rcb);
        }

        RowImpl row;

        if (theRow instanceof RowImpl) {
            row = ((RowImpl)theRow).clone();

        } else {
            TableMetadataHelper md =  rcb.getMetadataHelper();
            TableImpl table = md.getTable(theNamespace, theTableName);

            row = table.createRow();

            int numCols = row.getNumFields();
            int idCol = table.getIdentityColumn();

            for (int i = 0; i < numCols; ++i) {

                FieldValueImpl val = theRow.get(i);
                if (val != null &&
                    !(i == idCol && val.isEMPTY())) {
                    row.put(i, val);
                }
            }
        }

        rcb.setState(theStatePos, new InsertRowState(row));
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);
        if (state == null) {
            return;
        }

        for (PlanIter colIter : theColIters) {
            colIter.close(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.close(rcb);
        }

        state.close();
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {

        for (PlanIter colIter : theColIters) {
            colIter.reset(rcb);
        }

        if (theTTLIter != null) {
            theTTLIter.reset(rcb);
        }

        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {
        return nextInternal(rcb, false);
    }

    @Override
    public boolean nextLocal(RuntimeControlBlock rcb) {
        return nextInternal(rcb, true);
    }

    public boolean nextInternal(RuntimeControlBlock rcb, boolean local) {

        final InsertRowState state = (InsertRowState)rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        if (local) {

            if (state.theAsyncRequestExecuting) {

                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("InsertRowIterator: Async request executing " +
                              "already");
                }
                return false;
            }

            if (state.theAsyncResult != null) {
                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("InsertRowIterator: Processing async result");
                }
                return processResult(rcb, state, state.theAsyncResult);
            }

            if (state.theAsyncException != null) {
                state.done();
                return false;
            }

            state.theAsyncRequestExecuting = true;
        }

        RowImpl row = state.theRTRow;

        for (int i = 0; i < theColIters.length; ++i) {

            PlanIter colIter = theColIters[i];

            if (colIter.next(rcb)) {
                FieldValueImpl val = rcb.getRegVal(colIter.getResultReg());
                row.put(theColPositions[i], val);
            } else {
                row.putNull(theColPositions[i]);
            }
        }

        KVStoreImpl store = rcb.getStore();
        TableImpl table = row.getTableImpl();
        int idCol = table.getIdentityColumn();

        if (table.hasIdentityColumn() &&
            (row.get(idCol) == null || row.get(idCol).isEMPTY() ||
            (table.isIdentityOnNull() && row.get(idCol).isNull()))) {
            /* must get the next generated value */
            RecordDefImpl rowDef = table.getRowDef();
            FieldValueImpl generatedValue =
                store.getIdentityNextValue(table, rowDef.getFieldDef(idCol), 0,
                    row.get(idCol), idCol);

            if (generatedValue != null) {
                row.putInternal(idCol, generatedValue, false);
            }
        }

        if (table.hasUUIDcolumn()) {
            table.setUUIDDefaultValue(row);
        }

        if (row.isFromMRTable()) {
            TableAPIImpl.setLocalRegionId(row);
        }

        if (rcb.getTraceLevel() >= 1) {
            rcb.trace("Row to insert =\n" + row);
        }

        long tableId = table.getId();
        Value rowval = row.createValue();
        Key rowkey = row.getPrimaryKey(false/*allowPartial*/);
        byte[] keybytes = keySerializer.toByteArray(rowkey);

        TimeToLive ttlObj;
        Put put;

        if (theIsUpsert) {

            ttlObj = setRowExpTime(rcb, row, true, theTTLIter,
                                   theTTLUnit, theLocation);

            put = new Put(keybytes, rowval, Choice.NONE, tableId,
                          ttlObj, theUpdateTTL);
        } else {

            ttlObj = setRowExpTime(rcb, row, true, theTTLIter,
                                   theTTLUnit, theLocation);

            Choice choice = (!theHasReturningClause ?
                             Choice.NONE : Choice.ALL);

            put = new PutIfAbsent(keybytes, rowval, choice, tableId,
                                  ttlObj, true);
        }

        state.thePid = store.getDispatcher().getPartitionId(keybytes);

        Durability durability = rcb.getDurability();
        long timeout = rcb.getTimeout();
        TimeUnit timeUnit = rcb.getTimeUnit();

        Request req = store.makeWriteRequest(put, state.thePid,  durability,
                                             timeout, timeUnit);
        final ExecuteOptions options = rcb.getExecuteOptions();
        if (options != null) {
            req.setLogContext(options.getLogContext());
            req.setAuthContext(options.getAuthContext());
        }

        if (local) {

            if (rcb.getTraceLevel() >= 2) {
                rcb.trace("InsertRowIterator: Sending async request");
            }

            store.executeRequestAsync(req)
                .whenComplete(
                    unwrapExceptionVoid(
                        (r, e) ->
                        handleAsyncResult(rcb, state, (PutResult) r, e)))
                .whenComplete(
                    unwrapExceptionVoid(
                        e -> rcb.getLogger().log(
                            Level.WARNING, "Unexpected exception: " + e, e)));

            return false;
        }

        PutResult res = (PutResult)store.executeRequest(req);

        return processResult(rcb, state, res);
    }

    private void handleAsyncResult(
        RuntimeControlBlock rcb,
        InsertRowState state,
        PutResult r,
        Throwable e) {

        assert !Thread.holdsLock(this);

        if (r != null) {
            state.theAsyncResult = r;

            if (rcb.getTraceLevel() >= 2) {
                rcb.trace("InsertRowIterator: Got async result");
            }
        } else {
            state.theAsyncException = e;
            state.done();

            if (rcb.getTraceLevel() >= 2) {
                rcb.trace("InsertRowIterator: Got remote exception:\n" + e);
            }
        }

        state.theAsyncRequestExecuting = false;
        state.theAsyncNotifier.notifyNext();
    }

    private boolean processResult(
        RuntimeControlBlock rcb,
        InsertRowState state,
        PutResult res) {

        rcb.tallyReadKB(res.getReadKB());
        rcb.tallyWriteKB(res.getWriteKB());

        boolean wasInsert =
            (theIsUpsert ? !res.getWasUpdate() : res.getSuccess());

        RowImpl row = state.theRTRow;

        if (theHasReturningClause) {

            if (!theIsUpsert && !wasInsert) {

                row = row.getTable().
                      initRowFromValueBytes(row,
                                            res.getPreviousValue().toByteArray(),
                                            res.getPreviousExpirationTime(),
                                            res.getPreviousModificationTime(),
                                            res.getPreviousVersion(),
                                            state.thePid.getPartitionId(),
                                            res.getShard(),
                                            res.getPreviousStorageSize());
                
                if (row == null) {
                    state.done();
                    return false;
                }

            } else {
                row.setVersion(res.getNewVersion());
                row.setExpirationTime(res.getNewExpirationTime());
                row.setModificationTime(res.getNewModificationTime());
                row.setPartition(state.thePid.getPartitionId());
                row.setShard(res.getShard());
                row.setStorageSize(res.getNewStorageSize());
            }

            rcb.setRegVal(theResultReg, row);

        } else {
            RecordValueImpl retval =
                ExprInsertRow.theNumRowsInsertedType.createRecord();
            retval.put(0, (wasInsert ? one : zero));
            rcb.setRegVal(theResultReg, retval);
        }

        state.done();
        return true;
    }

    /*
     * Note: this method is used by ServerUpdateRowIter as well.
     */
    public static TimeToLive setRowExpTime(
        RuntimeControlBlock rcb,
        RowImpl row,
        boolean updateTTL,
        PlanIter ttlIter,
        TimeUnit ttlUnit,
        Location loc) {

        if (!updateTTL) {
            return null;
        }

        TimeToLive ttlObj = null;

        if (ttlIter != null) {

            if (ttlIter.next(rcb)) {
                FieldValueImpl ttlVal = rcb.getRegVal(ttlIter.getResultReg());

                ttlVal = CastIter.castValue(ttlVal,
                                            FieldDefImpl.integerDef,
                                            loc);
                int ttl = ((IntegerValueImpl)ttlVal).get();
                if (ttl < 0) {
                    ttl = 0;
                }

                if (ttlUnit == TimeUnit.HOURS) {
                    ttlObj = TimeToLive.ofHours(ttl);
                } else {
                    ttlObj = TimeToLive.ofDays(ttl);
                }
            } else {
                ttlObj = row.getTable().getDefaultTTL();
            }
        } else {
            ttlObj = row.getTable().getDefaultTTL();
        }

        /*
         * ttlObj will be null if there is ttlIter is null and the table has
         * no default TTL.
         */
        if (ttlObj != null) {
            TimeUnit unit = ttlObj.getUnit();
            long ttl = ttlObj.getValue();
            long expTime;

            if (ttl == 0) {
                expTime = 0;
            } else if (unit == TimeUnit.DAYS) {
                expTime = ((System.currentTimeMillis() +
                            TimestampDefImpl.MILLIS_PER_DAY - 1) /
                            TimestampDefImpl.MILLIS_PER_DAY);

                expTime = (expTime + ttl) * TimestampDefImpl.MILLIS_PER_DAY;
            } else {
                expTime = ((System.currentTimeMillis() +
                            TimestampDefImpl.MILLIS_PER_HOUR - 1) /
                            TimestampDefImpl.MILLIS_PER_HOUR);

                expTime = (expTime + ttl) * TimestampDefImpl.MILLIS_PER_HOUR;
            }

            if (rcb.getTraceLevel() > 3) {
                rcb.trace("ttl = " + ttl + " expiration time = " + expTime);
            }

            row.setExpirationTime(expTime);

        } else {
            row.setExpirationTime(0);
        }

        return ttlObj;
    }

    @Override
    public Throwable getCloseException(RuntimeControlBlock rcb) {

        InsertRowState state = (InsertRowState)rcb.getState(theStatePos);
        if (state == null) {
            return null;
        }
        return state.theAsyncException;
    }

    @Override
    void displayName(StringBuilder sb) {

        if (theIsUpsert) {
            sb.append("UPSERT_ROW");
        } else {
            sb.append("INSERT_ROW");
        }
    }

    @Override
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {

        formatter.indent(sb);
        sb.append("\"row to insert (potentially partial)\" : \n");
        sb.append(theRow.toJsonString(true));
        sb.append(",\n");

        formatter.indent(sb);
        sb.append("\"value iterators\" : [\n");
        formatter.incIndent();
        for (int i = 0; i < theColIters.length; ++i) {
            theColIters[i].display(sb, formatter, verbose);
            if (i < theColIters.length - 1) {
                sb.append(",\n");
            }
        }
        formatter.decIndent();
        sb.append("\n");
        formatter.indent(sb);
        sb.append("]");

        if (theTTLIter != null) {
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"TTL iterator\" :\n");
            theTTLIter.display(sb, formatter, verbose);
        }
    }
}
