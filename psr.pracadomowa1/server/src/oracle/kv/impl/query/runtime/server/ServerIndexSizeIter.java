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

import static oracle.kv.impl.api.ops.InternalOperationHandler.MIN_READ;
import static oracle.kv.impl.api.ops.OperationHandler.CURSOR_DEFAULT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import oracle.kv.impl.api.ops.IndexKeysIterateHandler;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.api.ops.InternalOperationHandler;
import oracle.kv.impl.api.ops.OperationHandler;
import oracle.kv.impl.api.ops.TableQuery;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.IndexImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.runtime.FuncIndexStorageSizeIter;
import oracle.kv.impl.query.runtime.PlanIterState;
import oracle.kv.impl.query.runtime.RuntimeControlBlock;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationResult;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;

public class ServerIndexSizeIter extends FuncIndexStorageSizeIter {

    private static class KeyComparator implements Comparator<byte[]>{

        @Override
        public int compare(byte[] v1, byte[] v2) {

            if (Arrays.equals(v1, v2)) {
                return 0;
            }

            return 1;
        }

    }

    ServerIndexSizeIter(FuncIndexStorageSizeIter parent) {
        super(parent);
    }

    @Override
    public void open(RuntimeControlBlock rcb) {

        rcb.setState(theStatePos, new IndexSizeState(this));
        theInput.open(rcb);
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);
        if (state == null) {
            return;
        }
        theInput.close(rcb);
        state.close();
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {

        theInput.reset(rcb);
        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {

        IndexSizeState state = (IndexSizeState)rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        boolean more = theInput.next(rcb);

        if (!more) {
            state.done();
            return false;
        }

        FieldValueImpl row = rcb.getRegVal(theInput.getResultReg());
        int size = 0;

        if (row == NullValueImpl.getInstance()) {
             rcb.setRegVal(theResultReg, row);
             state.done();
             return true;
        }

        if (row.isTuple()) {
            TupleValue tuple = (TupleValue)row;
            IndexImpl index = tuple.getIndex();

            if (index != null &&
                theIndexName.equals(index.getName()) &&
                !index.isMultiKey()) {

                size = tuple.getIndexStorageSize();

                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("index entry storage size from index = " + size);
                }

                if (size < 0) {
                    throw new QueryStateException(
                        "index storage size not set");
                }

                rcb.setRegVal(theResultReg,
                              FieldDefImpl.integerDef.createInteger(size));
                state.done();
                return true;
            }

            if (tuple.isIndexEntry() && index != null) {
                throw new QueryStateException(
                    "Index " + index.getName() + " cannot be " +
                    "used to evaluate index_storage_size() function");
            }
        }

        RowImpl row2 = (row.isTuple() ?
                        ((TupleValue)row).toRow() :
                        (RowImpl)row);

        TableImpl table = row2.getTable();
        IndexImpl index = (IndexImpl)table.getIndex(theIndexName);
        TableQuery qop = rcb.getQueryOp();

        List<byte[]> keys;
        TreeSet<byte[]> distinctKeys = null;

        if (index.isMultiKey()) {
            keys = index.extractIndexKeys(row2);
            distinctKeys = new TreeSet<byte[]>(new KeyComparator());
        } else {
            keys = new ArrayList<byte[]>(1);
            keys.add(index.serializeIndexKey(row2, -1));
        }

        ServerIterFactoryImpl opCtx =
            (ServerIterFactoryImpl)
            rcb.getServerIterFactory();

        Transaction txn = opCtx.getTxn();
        OperationHandler handlersMgr = opCtx.getOperationHandler();

        IndexKeysIterateHandler opHandler =
            (IndexKeysIterateHandler)
            handlersMgr.getHandler(OpCode.INDEX_KEYS_ITERATE);

        SecondaryDatabase db = opHandler.getSecondaryDatabase(
            table.getInternalNamespace(),
            table.getFullName(),
            index.getName());

        SecondaryCursor cursor = db.openCursor(txn, CURSOR_DEFAULT);

        try {
            byte[] primKey = row2.getPrimaryKey(false).toByteArray();
            DatabaseEntry secKeyEntry = new DatabaseEntry();
            DatabaseEntry primKeyEntry = new DatabaseEntry(primKey);

            for (byte[] key : keys) {

                if (distinctKeys != null && !distinctKeys.add(key)) {
                    continue;
                }

                qop.addReadBytes(MIN_READ);

                secKeyEntry.setData(key);

                OperationResult res =
                    cursor.get(secKeyEntry,
                               primKeyEntry,
                               null,
                               Get.SEARCH_BOTH,
                               LockMode.DEFAULT.toReadOptions());
                if (res == null) {
                    continue;
                }

                int entrySize = InternalOperationHandler.getStorageSize(cursor);
                size += entrySize;

                if (rcb.getTraceLevel() > 3) {
                    rcb.trace("index entry storage size from row = " + entrySize);
                }
            }
        } finally {
            cursor.close();
        }

        if (rcb.getTraceLevel() >= 3) {
            rcb.trace("total index entry storage size from row = " + size);
        }

        rcb.setRegVal(theResultReg,
                      FieldDefImpl.integerDef.createInteger(size));
        state.done();
        return true;
    }
}
