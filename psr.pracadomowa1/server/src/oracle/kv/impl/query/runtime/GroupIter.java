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

import static oracle.kv.impl.util.SerialVersion.QUERY_VERSION_10;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.DoubleValueImpl;
import oracle.kv.impl.api.table.EmptyValueImpl;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.FloatValueImpl;
import oracle.kv.impl.api.table.IntegerValueImpl;
import oracle.kv.impl.api.table.LongValueImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.api.table.NumberValueImpl;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.TupleValue;
import oracle.kv.impl.async.IterationHandleNotifier;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.compiler.FunctionLib.FuncCode;
import oracle.kv.impl.query.compiler.ExprGroup;
import oracle.kv.impl.query.runtime.CloudSerializer.FieldValueWriter;
import oracle.kv.impl.util.SerializationUtil;
import oracle.kv.impl.util.SizeOf;

public class GroupIter extends PlanIter {

    private static class GroupTuple {

        FieldValueImpl[] theValues;

        GroupTuple(int numGBColumns) {
            theValues = new FieldValueImpl[numGBColumns];
        }

        @Override
        public boolean equals(Object other) {

            GroupTuple o = (GroupTuple)other;

            for (int i = 0; i < theValues.length; ++i) {

                if (!theValues[i].equal(o.theValues[i])) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int hashCode() {
            int code = 1;
            for (int i = 0; i < theValues.length; ++i) {
                code = 31 * code + theValues[i].hashcode();
            }
            return code;
        }

        long sizeof() {

            long size = (SizeOf.OBJECT_OVERHEAD +
                         SizeOf.ARRAY_OVERHEAD +
                         (theValues.length + 1) * SizeOf.OBJECT_REF_OVERHEAD);
            for (FieldValueImpl val : theValues) {
                size += val.sizeof();
            }

            return size;
        }
    }

    private static class AggrValue {

        FieldValueImpl theValue;
        boolean theGotNumericInput;

        long sizeof() {
            return (SizeOf.OBJECT_OVERHEAD +
                    SizeOf.OBJECT_REF_OVERHEAD +
                    theValue.sizeof() +
                    1);
        }

        AggrValue(PlanIterKind kind) {

            switch (kind) {
            case FUNC_COUNT:
            case FUNC_COUNT_STAR:
            case FUNC_SUM:
                theValue = FieldDefImpl.longDef.createLong(0);
                break;
            case FUNC_MIN_MAX:
                theValue = NullValueImpl.getInstance();
                break;
            default:
                assert(false);
            }
        }

        void add(
            RuntimeControlBlock rcb,
            FieldValueImpl val,
            boolean countMemory,
            MathContext ctx) {

            BigDecimal bd;
            long sz = 0;

            switch (val.getType()) {
            case INTEGER: {
                theGotNumericInput = true;
                switch (theValue.getType()) {
                case LONG: {
                    long sum = ((LongValueImpl)theValue).get();
                    sum += ((IntegerValueImpl)val).get();
                    ((LongValueImpl)theValue).setLong(sum);
                    break;
                }
                case DOUBLE: {
                    double sum = ((DoubleValueImpl)theValue).get();
                    sum += ((IntegerValueImpl)val).get();
                    ((DoubleValueImpl)theValue).setDouble(sum);
                    break;
                }
                case NUMBER: {
                    BigDecimal sum = ((NumberValueImpl)theValue).get();
                    bd = new BigDecimal(((IntegerValueImpl)val).get());
                    sum = sum.add(bd, ctx);
                    ((NumberValueImpl)theValue).setDecimal(sum);
                    break;
                }
                default:
                    assert(false);
                }
                break;
            }
            case LONG: {
                theGotNumericInput = true;
                switch (theValue.getType()) {
                case LONG: {
                    long sum = ((LongValueImpl)theValue).get();
                    sum += ((LongValueImpl)val).get();
                    ((LongValueImpl)theValue).setLong(sum);
                    break;
                }
                case DOUBLE: {
                    double sum = ((DoubleValueImpl)theValue).get();
                    sum += ((LongValueImpl)val).get();
                    ((DoubleValueImpl)theValue).setDouble(sum);
                    break;
                }
                case NUMBER: {
                    BigDecimal sum = ((NumberValueImpl)theValue).get();
                    bd = new BigDecimal(((LongValueImpl)val).get());
                    sum = sum.add(bd, ctx);
                    ((NumberValueImpl)theValue).setDecimal(sum);
                    break;
                }
                default:
                    assert(false);
                }
                break;
            }
            case FLOAT: {
                theGotNumericInput = true;
                switch (theValue.getType()) {
                case LONG: {
                    double sum = ((LongValueImpl)theValue).get();
                    sum += ((FloatValueImpl)val).get();
                    if (!rcb.isServerRCB() && countMemory) {
                        sz = theValue.sizeof();
                    }
                    theValue = FieldDefImpl.doubleDef.createDouble(sum);
                    if (!rcb.isServerRCB() && countMemory) {
                        rcb.incMemoryConsumption(theValue.sizeof() - sz);
                    }
                    break;
                }
                case DOUBLE: {
                    double sum = ((DoubleValueImpl)theValue).get();
                    sum += ((FloatValueImpl)val).get();
                    ((DoubleValueImpl)theValue).setDouble(sum);
                    break;
                }
                case NUMBER: {
                    BigDecimal sum = ((NumberValueImpl)theValue).get();
                    bd = new BigDecimal(((FloatValueImpl)val).get());
                    sum = sum.add(bd, ctx);
                    ((NumberValueImpl)theValue).setDecimal(sum);
                    break;
                }
                default:
                    assert(false);
                }
                break;
            }
            case DOUBLE: {
                theGotNumericInput = true;
                switch (theValue.getType()) {
                case LONG: {
                    double sum = ((LongValueImpl)theValue).get();
                    sum += ((DoubleValueImpl)val).get();
                    if (!rcb.isServerRCB() && countMemory) {
                        sz = theValue.sizeof();
                    }
                    theValue = FieldDefImpl.doubleDef.createDouble(sum);
                    if (!rcb.isServerRCB() && countMemory) {
                        rcb.incMemoryConsumption(theValue.sizeof() - sz);
                    }
                    break;
                }
                case DOUBLE: {
                    double sum = ((DoubleValueImpl)theValue).get();
                    sum += ((DoubleValueImpl)val).get();
                    ((DoubleValueImpl)theValue).setDouble(sum);
                    break;
                }
                case NUMBER: {
                    BigDecimal sum = ((NumberValueImpl)theValue).get();
                    bd = new BigDecimal(((DoubleValueImpl)val).get());
                    sum = sum.add(bd, ctx);
                    ((NumberValueImpl)theValue).setDecimal(sum);
                    break;
                }
                default:
                    assert(false);
                }
                break;
            }
            case NUMBER: {
                theGotNumericInput = true;
                switch (theValue.getType()) {
                case LONG: {
                    BigDecimal sum =
                        new BigDecimal(((LongValueImpl)theValue).get());
                    bd = ((NumberValueImpl)val).get();
                    sum = sum.add(bd, ctx);
                    if (!rcb.isServerRCB() && countMemory) {
                         sz = theValue.sizeof();
                    }
                    theValue = FieldDefImpl.numberDef.createNumber(sum);
                    if (!rcb.isServerRCB() && countMemory) {
                        rcb.incMemoryConsumption(theValue.sizeof() - sz);
                    }
                    break;
                }
                case DOUBLE: {
                    BigDecimal sum =
                        new BigDecimal(((DoubleValueImpl)theValue).get());
                    sum = sum.add(((NumberValueImpl)val).get(), ctx);
                    if (!rcb.isServerRCB() && countMemory) {
                        sz = theValue.sizeof();
                    }
                    theValue = FieldDefImpl.numberDef.createNumber(sum);
                    if (!rcb.isServerRCB() && countMemory) {
                        rcb.incMemoryConsumption(theValue.sizeof() - sz);
                    }
                    break;
                }
                case NUMBER: {
                    BigDecimal sum = ((NumberValueImpl)theValue).get();
                    sum = sum.add(((NumberValueImpl)val).get(), ctx);
                    ((NumberValueImpl)theValue).setDecimal(sum);
                    break;
                }
                default:
                    assert(false);
                }
                break;
            }
            default:
                break;
            }
        }
    }

    private static class GroupIterState extends PlanIterState {

        final HashMap<GroupTuple, AggrValue[]> theResults;

        Iterator<Map.Entry<GroupTuple, AggrValue[]>> theResultsIter;

        GroupTuple theGBTuple;

        public GroupIterState(GroupIter iter) {
            super();
            theResults = new HashMap<GroupTuple, AggrValue[]>(4096);
            theGBTuple = new GroupTuple(iter.theNumGBColumns);
        }

        @Override
        public void done() {
            super.done();
            theResultsIter = null;
            theResults.clear();
            theGBTuple = null;
        }

        @Override
        public void reset(PlanIter iter) {
            super.reset(iter);
            theResultsIter = null;
            theResults.clear();
        }

        @Override
        public void close() {
            super.close();
            theResults.clear();
            theResultsIter = null;
            theGBTuple = null;
        }
    }

    private static final FieldValueImpl one =
        FieldDefImpl.longDef.createLong(1);

    private final String theVarName;

    private PlanIter theInput;

    private final int theNumGBColumns;

    private final PlanIter[] theColumnIters;

    private final boolean theComputeColumns;

    private final RecordDefImpl theTypeDef;

    private final int[] theTupleRegs;

    private final boolean theIsDistinct;

    private final boolean theRemoveProducedResult;

    private final boolean theCountMemory;

    public GroupIter(
        ExprGroup e,
        int resultReg,
        int[] tupleRegs,
        PlanIter input,
        PlanIter[] columnIters,
        int numGBColumns,
        boolean isTopBlockingIter,
        boolean countMemory,
        boolean forCloud) {

        super(e, resultReg, forCloud);

        theTypeDef = (RecordDefImpl)e.getType().getDef();
        theVarName = e.getVar().getName();
        theInput = input;
        theNumGBColumns = numGBColumns;
        theColumnIters = columnIters;
        theComputeColumns = e.getComputeFields();
        theTupleRegs = tupleRegs;
        theIsDistinct = e.isDistinct();
        theRemoveProducedResult = !isTopBlockingIter;
        theCountMemory = countMemory;
    }

    @Override
    public void writeForCloud(
        DataOutput out,
        short driverVersion,
        FieldValueWriter valWriter) throws IOException {

        assert(theIsCloudDriverIter);
        writeForCloudCommon(out, driverVersion);

        theInput.writeForCloud(out, driverVersion, valWriter);

        out.writeInt(theNumGBColumns);

        int numColumns = theTypeDef.getNumFields();
        String[] columnNames = new String[numColumns];
        for (int i = 0; i < numColumns; ++i) {
            columnNames[i] = theTypeDef.getFieldName(i);
        }
        CloudSerializer.writeStringArray(columnNames, out);

        for (int i = theNumGBColumns; i < theColumnIters.length; ++i) {
            PlanIter aggrIter = theColumnIters[i];
            FuncCode aggrFunc;
            switch (aggrIter.getKind()) {
            case FUNC_MIN_MAX:
            case FUNC_COUNT:
                aggrFunc = aggrIter.getFuncCode();
                break;
            case FUNC_COUNT_STAR:
                aggrFunc = FuncCode.FN_COUNT_STAR;
                break;
            case FUNC_SUM:
                aggrFunc = FuncCode.FN_SUM;
                break;
            default:
                throw new QueryStateException(
                    "Unexpected kind of iterator: " + aggrIter.getKind());
            }

            out.writeShort(aggrFunc.ordinal());
        }

        out.writeBoolean(theIsDistinct);
        out.writeBoolean(theRemoveProducedResult);
        out.writeBoolean(theCountMemory);
    }

    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {

        if (theIsCloudDriverIter) {
            theInput.writeFastExternal(out, serialVersion);
            return;
        }

        super.writeFastExternal(out, serialVersion);

        SerializationUtil.writeString(out, serialVersion, theVarName);
        serializeIter(theInput, out, serialVersion);
        out.writeInt(theNumGBColumns);
        serializeIters(theColumnIters, out, serialVersion);
        serializeFieldDef(theTypeDef, out, serialVersion);
        serializeIntArray(theTupleRegs, out, serialVersion);
        out.writeBoolean(theIsDistinct);

        if (serialVersion > QUERY_VERSION_10) {
            out.writeBoolean(theComputeColumns);
        }
    }

    GroupIter(DataInput in, short serialVersion) throws IOException {

        super(in, serialVersion);

        theVarName = SerializationUtil.readString(in, serialVersion);
        theInput = deserializeIter(in, serialVersion);
        theNumGBColumns = in.readInt();
        theColumnIters = deserializeIters(in, serialVersion);
        theTypeDef = (RecordDefImpl)deserializeFieldDef(in, serialVersion);
        theTupleRegs = deserializeIntArray(in, serialVersion);
        theIsDistinct = in.readBoolean();
        theRemoveProducedResult = false;
        theCountMemory = false;

        if (serialVersion > QUERY_VERSION_10) {
            theComputeColumns = in.readBoolean();
        } else {
            theComputeColumns = false;
        }
    }

    @Override
    public PlanIterKind getKind() {
        return PlanIterKind.GROUP;
    }

    @Override
    public int[] getTupleRegs() {
        return theTupleRegs;
    }

    @Override
    public PlanIter getInputIter() {
        return theInput;
    }

    public void setInputIter(PlanIter iter) {
        theInput = iter;
    }

    @Override
    public void setIterationHandleNotifier(
        RuntimeControlBlock rcb,
        IterationHandleNotifier iterHandleNotifier) {
        theInput.setIterationHandleNotifier(rcb, iterHandleNotifier);
    }

    @Override
    public void open(RuntimeControlBlock rcb) {

        GroupIterState state = new GroupIterState(this);
        rcb.setState(theStatePos, state);

        theInput.open(rcb);

        if (theTupleRegs != null) {
            TupleValue tuple = new TupleValue(theTypeDef,
                                              rcb.getRegisters(),
                                              theTupleRegs);
            rcb.setRegVal(theResultReg, tuple);
        }

        if (theComputeColumns) {
            for (PlanIter iter : theColumnIters) {
                iter.open(rcb);
            }
        }
    }

    @Override
    public void reset(RuntimeControlBlock rcb) {

        GroupIterState state = (GroupIterState)rcb.getState(theStatePos);
        state.reset(this);
        theInput.reset(rcb);

        if (theComputeColumns) {
            for (PlanIter iter : theColumnIters) {
                iter.reset(rcb);
            }
        }
    }

    @Override
    public void close(RuntimeControlBlock rcb) {

        PlanIterState state = rcb.getState(theStatePos);
        if (state == null) {
            return;
        }

        theInput.close(rcb);

        if (theComputeColumns) {
            for (PlanIter iter : theColumnIters) {
                iter.close(rcb);
            }
        }
        state.close();
    }

    @Override
    public Throwable getCloseException(RuntimeControlBlock rcb) {
        return theInput.getCloseException(rcb);
    }

    @Override
    public boolean next(RuntimeControlBlock rcb) {
        return nextInternal(rcb, false);
    }

    @Override
    public boolean nextLocal(RuntimeControlBlock rcb) {
        return nextInternal(rcb, true);
    }

    private boolean nextInternal(RuntimeControlBlock rcb, boolean local) {

        GroupIterState state = (GroupIterState)rcb.getState(theStatePos);

        if (state.isDone()) {
            return false;
        }

        while (true) {
            if (state.theResultsIter != null) {

                if (state.theResultsIter.hasNext()) {
                    Map.Entry<GroupTuple, AggrValue[]> res =
                        state.theResultsIter.next();
                    GroupTuple gbTuple = res.getKey();
                    AggrValue[] aggrTuple = res.getValue();

                    int i;
                    for (i = 0; i < theNumGBColumns; ++i) {
                        rcb.setRegVal(theTupleRegs[i], gbTuple.theValues[i]);
                    }

                    for (; i < theColumnIters.length; ++i) {
                        FieldValueImpl aggr = getAggrValue(aggrTuple, i);
                        rcb.setRegVal(theTupleRegs[i], aggr);
                    }

                    if (theRemoveProducedResult) {
                        state.theResultsIter.remove();
                    }

                    return true;
                }

                if (!rcb.getReachedLimit()) {
                    state.done();
                }
                return false;
            }

            boolean more = (local ?
                            theInput.nextLocal(rcb) :
                            theInput.next(rcb));

            if (!more) {

                if (local && !theInput.isDone(rcb)) {
                    return false;
                }

                if (!rcb.isServerRCB() &&
                    theNumGBColumns == theColumnIters.length) {
                    state.done();
                    return false;
                }

                state.theResultsIter = state.theResults.entrySet().iterator();
                continue;
            }

            int i;
            FieldValueImpl v = rcb.getRegVal(theInput.getResultReg());

            for (i = 0; i < theNumGBColumns; ++i) {

                FieldValueImpl colValue = getColumnValue(rcb, v, i);

                if (colValue.isEMPTY()) {
                    if (theIsDistinct) {
                        colValue = NullValueImpl.getInstance();
                    } else {
                        break;
                    }
                }

                state.theGBTuple.theValues[i] = colValue;
            }

            if (i < theNumGBColumns) {
                continue;
            }

            AggrValue[] aggrTuple = state.theResults.get(state.theGBTuple);

            if (aggrTuple == null) {

                GroupTuple gbTuple = new GroupTuple(theNumGBColumns);
                aggrTuple = new AggrValue[theColumnIters.length - theNumGBColumns];
                long aggrTupleSize = 0;

                for (i = theNumGBColumns; i < theColumnIters.length; ++i) {
                    PlanIter aggrIter = theColumnIters[i];
                    AggrValue aggrVal = new AggrValue(aggrIter.getKind());
                    aggrTuple[i - theNumGBColumns] = aggrVal;
                    if (!rcb.isServerRCB() && theCountMemory) {
                        aggrTupleSize += aggrVal.sizeof();
                    }
                }

                for (i = 0; i < theNumGBColumns; ++i) {
                    gbTuple.theValues[i] = state.theGBTuple.theValues[i];
                }

                if (!rcb.isServerRCB() && theCountMemory) {
                    long sz = (gbTuple.sizeof() + aggrTupleSize +
                               SizeOf.HASHMAP_ENTRY_OVERHEAD);
                    rcb.incMemoryConsumption(sz);
                }

                for (; i < theColumnIters.length; ++i) {
                    aggregate(rcb, aggrTuple, i, getColumnValue(rcb, v, i));
                }

                state.theResults.put(gbTuple, aggrTuple);

                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Started new group:\n" +
                              printResult(gbTuple, aggrTuple));
                }

                if (!rcb.isServerRCB() &&
                    theNumGBColumns == theColumnIters.length) {

                    for (i = 0; i < theNumGBColumns; ++i) {
                        rcb.setRegVal(theTupleRegs[i], gbTuple.theValues[i]);
                    }
                    return true;
                }

                if (rcb.isServerRCB() &&
                    state.theResults.size() == rcb.getBatchSize()) {
                    state.theResultsIter = state.theResults.entrySet().iterator();
                    continue;
                }

            } else {
                for (i = theNumGBColumns; i < theColumnIters.length; ++i) {
                    aggregate(rcb, aggrTuple, i, getColumnValue(rcb, v, i));
                }

                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Updated existing group:\n" +
                              printResult(state.theGBTuple, aggrTuple));
                }
            }
        }
    }

    private FieldValueImpl getColumnValue(
        RuntimeControlBlock rcb,
        FieldValueImpl inTuple,
        int colIdx) {

        if (theComputeColumns) {
            PlanIter colIter = (colIdx < theNumGBColumns ?
                                theColumnIters[colIdx] :
                                theColumnIters[colIdx].getInputIter());
            colIter.reset(rcb);
            boolean more = colIter.next(rcb);
            if (!more) {
                return EmptyValueImpl.getInstance();
            }
            return rcb.getRegVal(colIter.getResultReg());
        }

        return inTuple.getElement(colIdx);
    }

    private void aggregate(
        RuntimeControlBlock rcb,
        AggrValue[] aggrValues,
        int column,
        FieldValueImpl val) {

        AggrValue aggrValue = aggrValues[column - theNumGBColumns];
        PlanIter aggrIter = theColumnIters[column];

        switch (aggrIter.getKind()) {
        case FUNC_COUNT:
            if (val.isNull()) {
                return;
            }

            if (aggrIter.getFuncCode() == FuncCode.FN_COUNT_NUMBERS &&
                !val.isNumeric()) {
                return;
            }

            aggrValue.add(rcb, one, theCountMemory, rcb.getMathContext());
            return;

        case FUNC_COUNT_STAR:
            aggrValue.add(rcb, one, theCountMemory, rcb.getMathContext());
            return;

        case FUNC_SUM:
            if (val.isNull()) {
                return;
            }

            if (val.isNumeric()) {
               aggrValue.add(rcb, val, theCountMemory, rcb.getMathContext());
            }
            return;

        case FUNC_MIN_MAX:
            if (val.isNull() || val.isJsonNull() || val.isEMPTY()) {
                return;
            }

            switch (val.getType()) {
            case BINARY:
            case FIXED_BINARY:
            case RECORD:
            case MAP:
            case ARRAY:
                return;
            default:
                break;
            }

            if (aggrValue.theValue.isNull()) {

                if (rcb.getTraceLevel() >= 3) {
                    rcb.trace("Setting min/max to " + val);
                }

                if (!rcb.isServerRCB() && theCountMemory) {
                    rcb.incMemoryConsumption(val.sizeof() -
                                             aggrValue.theValue.sizeof());
                }
                aggrValue.theValue = val;
                return;
            }

            int cmp = FieldValueImpl.compareKeyValues(aggrValue.theValue, val,
                                                      true);


            if (rcb.getTraceLevel() >= 3) {
                rcb.trace("Compared values: \n" + aggrValue.theValue + "\n" +
                          val + "\ncomp res = " + cmp);
            }

            if (aggrIter.getFuncCode() == FuncCode.FN_MIN) {
                if (cmp <= 0) {
                    return;
                }
            } else if (cmp >= 0) {
                return;
            }

            if (val.getType() != aggrValue.theValue.getType()) {
                if (!rcb.isServerRCB() && theCountMemory) {
                    rcb.incMemoryConsumption(val.sizeof() -
                                             aggrValue.theValue.sizeof());
                }
            }

            if (rcb.getTraceLevel() >= 3) {
                rcb.trace("Setting min/max to " + val);
            }

            aggrValue.theValue = val;
            return;
        default:
            throw new QueryStateException(
                "Method not implemented for iterator " +
                aggrIter.getKind());
        }
    }

    private FieldValueImpl getAggrValue(
        AggrValue[] aggrTuple,
        int column) {

        AggrValue aggrValue = aggrTuple[column - theNumGBColumns];

        if (theColumnIters[column].getKind() == PlanIterKind.FUNC_SUM &&
            !aggrValue.theGotNumericInput) {
            return NullValueImpl.getInstance();
        }

        return aggrValue.theValue;
    }

    private String printResult(GroupTuple gbTuple, AggrValue[] aggrValues) {

        StringBuilder sb = new StringBuilder();

        sb.append("[ ");

        for (int i = 0; i < gbTuple.theValues.length; ++i) {
            sb.append(gbTuple.theValues[i]);
            sb.append(" ");
        }

        sb.append("- ");
        for (int i = 0; i < aggrValues.length; ++i) {
            sb.append(aggrValues[i].theValue);
            sb.append(" ");
        }

        sb.append("]");
        return sb.toString();
    }

    @Override
    protected void displayContent(
        StringBuilder sb,
        DisplayFormatter formatter,
        boolean verbose) {

        if (verbose) {
            formatter.indent(sb);
            sb.append("\"is distinct\" : ");
            sb.append(theIsDistinct);
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"remove produced result\" : ");
            sb.append(theRemoveProducedResult);
            sb.append(",\n");
            formatter.indent(sb);
            sb.append("\"count memory consumption\" : ");
            sb.append(theCountMemory);
            sb.append(",\n");
        }

        formatter.indent(sb);
        sb.append("\"input variable\" : \"").append(theVarName);
        sb.append("\",\n");

        displayInputIter(sb, formatter, verbose, theInput);

        sb.append(",\n");

        int i = 0;
        formatter.indent(sb);
        sb.append("\"grouping expressions\" : [\n");

        formatter.incIndent();
        for (; i < theNumGBColumns; ++i) {
            theColumnIters[i].display(sb, formatter, verbose);
            if (i < theNumGBColumns - 1) {
                sb.append(",\n");
            }
        }
        formatter.decIndent();
        sb.append("\n");
        formatter.indent(sb);
        sb.append("],\n");

        formatter.indent(sb);
        sb.append("\"aggregate functions\" : [\n");

        formatter.incIndent();
        for (; i < theColumnIters.length; ++i) {
            theColumnIters[i].display(sb, formatter, verbose);
            if (i < theColumnIters.length - 1) {
                sb.append(",\n");
            }
        }
        formatter.decIndent();
        sb.append("\n");
        formatter.indent(sb);
        sb.append("]");
    }
}
