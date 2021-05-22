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
import oracle.kv.impl.query.compiler.Expr;

/**
 *
 */
public class FuncCurrentTimeMillisIter extends PlanIter {

    public FuncCurrentTimeMillisIter(Expr e, int resultReg) {
        super(e, resultReg);
    }

    /**
     * FastExternalizable constructor.
     */
    FuncCurrentTimeMillisIter(DataInput in, short serialVersion)
        throws IOException {
        super(in, serialVersion);
    }

    /**
     * FastExternalizable writer.  Must call superclass method first to
     * write common elements.
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {

        super.writeFastExternal(out, serialVersion);
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.FUNC_CURRENT_TIME_MILLIS;
    }

    @Override
    public void open(RuntimeControlBlock rcb) {
        rcb.setState(theStatePos, new PlanIterState());
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        long time = System.currentTimeMillis();
        rcb.setRegVal(theResultReg, FieldDefImpl.longDef.createLong(time));
        state.done();
        return true;
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {
        PlanIterState state = rcb.getState(theStatePos);
        state.reset(this);
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);
        if (state == null) {
            return;
        }

        state.close();
    }

    @Override
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {
    }
}
