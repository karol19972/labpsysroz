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

import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.compiler.Expr.ExprKind;
import oracle.kv.impl.query.compiler.FunctionLib.FuncCode;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.runtime.FuncVersionIter;
import oracle.kv.impl.query.types.TypeManager;

/**
 * Function to return the version of a row
 */
public class FuncVersion extends Function {

    FuncVersion(String name) {
        super(FuncCode.FN_VERSION, name,
              TypeManager.ANY_RECORD_ONE(),
              TypeManager.BINARY_ONE());
    }

    @Override
    boolean isRowProperty() {
        return true;
    }

    @Override
    boolean mayReturnNULL(ExprFuncCall caller) {
        return true;
    }

    @Override
    Expr normalizeCall(ExprFuncCall funcCall) {

        Expr arg = funcCall.getArg(0);

        if (arg.getKind() == ExprKind.VAR && ((ExprVar)arg).getTable() != null) {
            return funcCall;
        }

        throw new QueryException(
            "The argument to the version function must " +
            "be a row variable", funcCall.getLocation());
    }

    @Override
    PlanIter codegen(CodeGenerator codegen,
                     ExprFuncCall caller,
                     PlanIter[] argIters) {

        int resultReg = codegen.allocateResultReg(caller);

        Expr arg = caller.getArg(0);

        if (arg.getKind() != ExprKind.VAR || ((ExprVar)arg).getTable() == null) {
            throw new QueryException(
                "The argument to the version function must " +
                "be a row variable", caller.getLocation());
        }

        return new FuncVersionIter(caller, resultReg, argIters[0]);
    }
}
