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

import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.FieldDefFactory;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldMap;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.query.QueryException.Location;
import oracle.kv.impl.query.types.ExprType;
import oracle.kv.impl.query.types.ExprType.Quantifier;
import oracle.kv.impl.query.types.TypeManager;

/**
 *
 */
public class ExprDeleteRow extends Expr {

    private TableImpl theTable;

    private Expr theInput;

    private boolean theHasReturningClause;

    private int[] thePrimKeyPositions;

    ExprDeleteRow(
        QueryControlBlock qcb,
        StaticContext sctx,
        Location location,
        ExprSFW input,
        boolean hasReturningClause) {

        super(qcb, sctx, ExprKind.DELETE_ROW, location);

        theInput = input;
        input.addParent(this);
        theTable = input.getFirstFrom().getTargetTable();
        theHasReturningClause = hasReturningClause;

        if (theHasReturningClause) {
            theType = theInput.getType();
        } else {
            FieldMap fieldMap = new FieldMap();
            fieldMap.put("numRowsDeleted", FieldDefImpl.longDef, false, null);

            RecordDefImpl recDef = FieldDefFactory.createRecordDef(fieldMap,
                                                                   null);
            theType = TypeManager.createType(recDef, Quantifier.ONE);
        }
    }

    TableImpl getTable() {
        return theTable;
    }

    @Override
    int getNumChildren() {
        return 1;
    }

    @Override
    Expr getInput() {
        return theInput;
    }

    void setInput(Expr newExpr, boolean destroy) {
        theInput.removeParent(this, destroy);
        theInput = newExpr;
        newExpr.addParent(this);
        computeType(false);
    }

    boolean hasReturningClause() {
        return theHasReturningClause;
    }

    void addPrimKeyPositions(int[] positions) {
        thePrimKeyPositions = positions;
        theType = computeType();
    }

    int[] getPrimKeyPositions() {
        return thePrimKeyPositions;
    }

    @Override
    ExprType computeType() {
        return theType;
    }

    @Override
    boolean mayReturnNULL() {
        return false;
    }

    @Override
    void displayContent(StringBuilder sb, DisplayFormatter formatter) {
        theInput.display(sb, formatter);
    }
}
