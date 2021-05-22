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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.compiler.Expr;
import oracle.kv.impl.util.SerializationUtil;

/**
 *
 */
public class FuncMKIndexStorageSizeIter extends PlanIter {

    private final PlanIter theInput;

    private final String theIndexName;

    public FuncMKIndexStorageSizeIter(
        Expr e,
        int resultReg,
        PlanIter input,
        String indexName) {

        super(e, resultReg);
        theInput = input;
        theIndexName = indexName;
    }

    FuncMKIndexStorageSizeIter(DataInput in, short serialVersion)
        throws IOException {
        super(in, serialVersion);
        theInput = deserializeIter(in, serialVersion);
        theIndexName = SerializationUtil.readString(in, serialVersion);
    }

    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {

        super.writeFastExternal(out, serialVersion);
        serializeIter(theInput, out, serialVersion);
        SerializationUtil.writeString(out, serialVersion, theIndexName);
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.FUNC_MKINDEX_STORAGE_SIZE;
    }

    @Override
    public void open(RuntimeControlBlock rcb) {
        rcb.setState(theStatePos, new PlanIterState());
        theInput.open(rcb);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        boolean more = theInput.next(rcb);

        if (!more) {
            state.done();
            return false;
        }

        FieldValueImpl row = rcb.getRegVal(theInput.getResultReg());
        int storageSize;

        if (row == NullValueImpl.getInstance()) {
             rcb.setRegVal(theResultReg, row);
             state.done();
             return true;
        }

        if (row.isTuple()) {
            TupleValue tuple = (TupleValue)row;
            if (theIndexName.equals(tuple.getIndex().getName())) {
                storageSize = tuple.getIndexStorageSize();
            } else {
                throw new QueryException(
                    "Invalid use of row_index_storage_size() function",
                    getLocation());
            }

        } else if (row.isRecord()) {
            throw new QueryException(
                "Invalid use of index_storage_size() function",
                getLocation());
        } else {
            throw new QueryException(
                "Input to the index_storage_size() function is not a row",
                getLocation());
        }

        if (rcb.getTraceLevel() > 3) {
            rcb.trace("index entry storage size  = " + storageSize);
        }

        rcb.setRegVal(theResultReg,
                      FieldDefImpl.integerDef.createInteger(storageSize));
        state.done();
        return true;
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {

        theInput.reset(rcb);
        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
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
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {
        displayInputIter(sb, formatter, verbose, theInput);
    }
}
