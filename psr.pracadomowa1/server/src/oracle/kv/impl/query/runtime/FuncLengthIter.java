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

import static oracle.kv.impl.util.SerialVersion.QUERY_VERSION_9;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.query.compiler.ExprFuncCall;
import oracle.kv.impl.query.compiler.FunctionLib;

/**
 * {@literal length(any* str) -> integer }
 *
 * length function returns the length of a given character string, as an exact
 * numeric value, in UTF characters or null if str is null. Argument str is
 * implicitly casted to string* (string sequence of any length).
 *
 * Note: If str is empty sequence or a sequence with more than one item the
 * result is null.
 *
 * Example
 * SELECT length('CANDIDE') as LengthInCharacters,
 *        length('\uD83D\uDE0B') as lengthOf32BitEncodedChar FROM t
 *
 * LengthInCharacters   lengthOf32BitEncodedChar
 * ------------------   ------------------------
 *                  7                          1
 */
public class FuncLengthIter extends PlanIter {

    private final PlanIter theArg;

    public FuncLengthIter(
        ExprFuncCall funcCall,
        int resultReg,
        PlanIter arg) {
        super(funcCall, resultReg);
        theArg = arg;
    }

    /**
     * FastExternalizable constructor.
     */
    public FuncLengthIter(DataInput in, short serialVersion)
        throws IOException {

        super(in, serialVersion);
        if (serialVersion >= QUERY_VERSION_9) {
            theArg = deserializeIter(in, serialVersion);
        } else {
            PlanIter[] args = deserializeIters(in, serialVersion);
            theArg = args[0];
        }
    }

    /**
     * FastExternalizable writer.  Must call superclass method first to
     * write common elements.
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {

        super.writeFastExternal(out, serialVersion);

        if (serialVersion >= QUERY_VERSION_9) {
            serializeIter(theArg, out, serialVersion);
        } else {
            PlanIter[] args = new PlanIter[1];
            args[0] = theArg;
            serializeIters(args, out, serialVersion);
        }
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.FUNC_LENGTH;
    }

    @Override
    FunctionLib.FuncCode getFuncCode() {
        return FunctionLib.FuncCode.FN_LENGTH;
    }

    @Override
    public void open(RuntimeControlBlock rcb) {
        rcb.setState(theStatePos, new PlanIterState());
        theArg.open(rcb);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        boolean opNext = theArg.next(rcb);

        if (!opNext) {
            rcb.setRegVal(theResultReg, NullValueImpl.getInstance());
            state.done();
            return true;
        }

        FieldValueImpl argValue = rcb.getRegVal(theArg.getResultReg());

        if (argValue.isNull() || theArg.next(rcb)) {
            rcb.setRegVal(theResultReg, NullValueImpl.getInstance());
            state.done();
            return true;
        }

        String str = argValue.asString().get();
        int resInt = str.codePointCount(0, str.length());

        FieldValueImpl res = FieldDefImpl.integerDef.createInteger(resInt);

        rcb.setRegVal(theResultReg, res);

        state.done();
        return true;
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {

        theArg.reset(rcb);
        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);
        if (state == null) {
            return;
        }

        theArg.close(rcb);
        state.close();
    }

    @Override
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {

        displayInputIter(sb, formatter, verbose, theArg);
    }
}
