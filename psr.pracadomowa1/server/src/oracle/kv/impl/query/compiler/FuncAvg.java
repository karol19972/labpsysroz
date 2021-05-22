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

package oracle.kv.impl.query.compiler;

import oracle.kv.impl.query.types.TypeManager;

import oracle.kv.impl.query.compiler.FunctionLib.FuncCode;

import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryStateException;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.types.ExprType;
import oracle.kv.table.FieldDef;

/*
 * The avg() function is converted to sum / count_numbers
 */
class FuncAvg extends Function {

    FuncAvg() {
        super(FuncCode.FN_AVG, "avg",
              TypeManager.ANY_STAR(),
              TypeManager.ANY_ATOMIC_ONE()); /* RetType */
    }

    @Override
    ExprType getRetType(ExprFuncCall caller) {

        FieldDefImpl inType = caller.getInput().getType().getDef();
        FieldDef.Type inTypeCode = inType.getType();

        switch (inTypeCode) {
        case INTEGER:
        case LONG:
        case FLOAT:
        case DOUBLE:
            return TypeManager.DOUBLE_ONE();
        case NUMBER:
            return TypeManager.NUMBER_ONE();
        case ANY:
        case JSON:
        case ANY_ATOMIC:
        case ANY_JSON_ATOMIC:
            return theReturnType;
        default:
            throw new QueryException(
                "Invalid input type for the avg aggregate function:\n" +
                inType.getDDLString(), caller.getLocation());
        }
    }

    @Override
    boolean mayReturnNULL(ExprFuncCall caller) {
        return true;
    }

    @Override
    boolean isAggregate() {
        return true;
    }

    @Override
    Expr normalizeCall(ExprFuncCall fncall) {

        ExprType inType = fncall.getArg(0).getType();

        if (!inType.isNumeric() && !inType.isWildcard()) {
            throw new QueryException(
                "Invalid input type for the avg aggregate function:\n" +
                inType.getDef().getDDLString(), fncall.getLocation());
        }

        return fncall;
    }

    @Override
    PlanIter codegen(
        CodeGenerator codegen,
        ExprFuncCall caller,
        PlanIter[] argIters) {

        throw new QueryStateException("No iterator for avg function");
    }
}
