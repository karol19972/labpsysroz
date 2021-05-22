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

import java.util.ArrayList;
import java.util.List;

import oracle.kv.impl.api.table.DisplayFormatter;
import oracle.kv.impl.api.table.EmptyValueImpl;
import oracle.kv.impl.api.table.FieldDefFactory;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldMap;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.api.table.NullValueImpl;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.QueryException.Location;
import oracle.kv.impl.query.types.ExprType;
import oracle.kv.impl.query.types.ExprType.Quantifier;
import oracle.kv.impl.query.types.TypeManager;

/**
 * Implements the insert_statement
 *
 * Note: If there are any SET TTL clauses, only the last one is taken into
 * account (the others are ignored). This SET TTL clause is modeled as an
 * ExprUpdateField and is stored as the last entry in theArgs. However, this
 * ExprUpdateField is basically a placeholder for the TTL expr and the update
 * kind: no UpdateFieldIter is generated for it; instead, the actual work is
 * done by the UpdateRowIter.
 *
 * If the insert stmt has a RETURNING clause, another SFW is created on top
 * of the ExprInsertRow in order to do the projection over the inserted row.
 * So, an insert stmt of the form:
 *
 * insert into tab_name tab_alias values ...
 * returning select_list
 *
 * gets translated to the following form:
 *
 * select select_list
 * from (insert into tab_name tab_alias values ...) tab_alias
 */
public class ExprInsertRow extends Expr {

    /*
     * The type of the result when there is no RETURNING clause. It's a record
     * with just one field, named "NumRowsUpdated", whose value is the number
     * of rows inserted (currently it can be only 1 or 0).
     */
    public static RecordDefImpl theNumRowsInsertedType;

    static {
        FieldMap fmap = new FieldMap();
        fmap.put("NumRowsInserted", FieldDefImpl.integerDef, false,
                 FieldDefImpl.integerDef.createInteger(1));
        theNumRowsInsertedType = FieldDefFactory.createRecordDef(fmap, null);
    }

    private TableImpl theTable;

    private ArrayList<Integer> theColPositions;

    private ArrayList<Expr> theArgs;

    private UpdateKind theTTLKind;

    private boolean theIsUpsert;

    private boolean theHasReturningClause;

    private RowImpl theRow;

    ExprInsertRow(
        QueryControlBlock qcb,
        StaticContext sctx,
        Location location,
        TableImpl table,
        ArrayList<Integer> colPositions,
        boolean isUpsert,
        boolean hasReturningClause) {

        super(qcb, sctx, ExprKind.INSERT_ROW, location);

        theTable = table;
        theColPositions = colPositions;
        theRow = table.createRow();
        theArgs = new ArrayList<Expr>(theRow.getNumFields());

        theIsUpsert = isUpsert;
        theHasReturningClause = hasReturningClause;

        if (hasReturningClause) {
            theType = TypeManager.createTableRecordType(table, Quantifier.QSTN);
        } else {
            theType = TypeManager.createType(theNumRowsInsertedType,
                                             Quantifier.ONE);
        }
    }

    void addInsertClause(Expr arg, Location loc) {

        FieldValueImpl val = null;

        if ((theRow.size() + theArgs.size()) >= theRow.getNumFields()) {
            throw new QueryException(
                "Insert statements contains more VALUES expressions " +
                "than the number of table columns", loc);
        }

        if (theColPositions != null &&
            theArgs.size() >= theColPositions.size()) {
            throw new QueryException(
                "Insert statements contains more VALUES expressions " +
                "than the number of specified columns", loc);
        }

        int fpos = (theColPositions == null ?
                    theArgs.size() + theRow.size() :
                    theColPositions.get(theArgs.size()));

        FieldDefImpl ftype = theRow.getFieldDef(fpos);

        if (arg != null &&
            theTable.hasIdentityColumn() &&
            theTable.getIdentityColumn() == fpos &&
            theTable.isIdentityGeneratedAlways()) {
            throw new QueryException("Generated always identity " +
                "column must use DEFAULT construct.", loc);
        }

        /* If users specify DEFAULT in INSERT statement ...*/
        if (arg == null) {

            /* if identity column then set val to EmptyValue. Actrual value
             * will get generated later at runtime in InsertRowIter.next() */
            if (theTable.hasIdentityColumn() &&
                theTable.getIdentityColumn() == fpos) {
                theRow.putInternal(fpos, EmptyValueImpl.getInstance(), false);
                return;
            }
            /* if 'STRING AS UUID GENERATED BY DEFAULT' column,
             *  then set val to EmptyValue. Actual value
             * will get generated later at runtime in InsertRowIter.next().
             */
            if (theTable.getGeneratedColumn() == fpos) {
                theRow.putInternal(fpos, EmptyValueImpl.getInstance(), false);
                return;
            }

            if (theTable.isPrimKeyAtPos(fpos)) {
                 throw new QueryException(
                     "There is no default value for primary-key column " +
                     theRow.getFieldName(fpos), loc);
            }

            val = theRow.getDefinition().getDefaultValue(fpos);

            putValue(fpos, val);

            if (theColPositions != null) {
                theColPositions.remove(theArgs.size());
            }

            return;
        }

        /* Special treatment required for json null */
        if (arg.getKind() == ExprKind.CONST) {

            val = ((ExprConst)arg).getValue();

            if ( (val.isNull() || val.isJsonNull() ) &&
                theTable.hasIdentityColumn() &&
                theTable.getIdentityColumn() == fpos &&
                theTable.isIdentityOnNull()) {

                theRow.putInternal(fpos, NullValueImpl.getInstance(), false);

                if (theColPositions != null) {
                    theColPositions.remove(theArgs.size());
                }
                return;
            }

            if (val.isJsonNull() || val.getDefinition().equals(ftype)) {

                putValue(fpos, val);

                if (theColPositions != null) {
                    theColPositions.remove(theArgs.size());
                }
                return;
            }
        }

        arg = ExprCast.create(theQCB, theSctx, arg.getLocation(),
                              arg, ftype, Quantifier.QSTN);

        if (arg.getKind() == ExprKind.CONST) {

            val = ((ExprConst)arg).getValue();

        } else if (ConstKind.isCompileConst(arg)) {

            List<FieldValueImpl> vals = ExprUtils.computeConstExpr(arg);

            if (vals.size() > 1) {
                throw new QueryException(
                    "A VALUES expression returns more than one items",
                    loc);
            }

            val = (vals.size() == 1 ? vals.get(0) : NullValueImpl.getInstance());
        }

        if (val != null) {

            putValue(fpos, val);

            if (theColPositions != null) {
                theColPositions.remove(theArgs.size());
            }

        } else {
            theArgs.add(arg);
            arg.addParent(this);
        }
    }

    private void putValue(int fpos, FieldValueImpl val) {

        if (val.isNull()) {
            theRow.putNull(fpos);
        } else if (val.isJsonNull()) {

            FieldDefImpl ftype = theRow.getFieldDef(fpos);

            if (ftype.equals(FieldDefImpl.jsonDef)) {
                theRow.put(fpos, val);
            } else {
                theRow.putNull(fpos);
            }

        } else {
            theRow.put(fpos, val);
        }
    }

    void addTTLClause(Expr ttlExpr, UpdateKind ttlKind) {

        if (ttlExpr != null) {
            theArgs.add(ttlExpr);
            ttlExpr.addParent(this);
        }
        theTTLKind = ttlKind;
    }

    void validate() {

        int numCols = theRow.getNumFields();

        int identityColumnPos = (theTable.hasIdentityColumn() ?
                                 theTable.getIdentityColumn() : -1);

        boolean haveTTLExpr = (theTTLKind == UpdateKind.TTL_HOURS ||
                               theTTLKind == UpdateKind.TTL_DAYS);

        if (theColPositions == null) {

            int numValues = (theRow.size() + theArgs.size() -
                             (haveTTLExpr ? 1 : 0));

            if (numValues != numCols) {
                throw new QueryException(
                    "The number of VALUES expressions is not equal to the " +
                    "number of table columns", theLocation);
            }

            theColPositions = new ArrayList<Integer>(numCols);

            for (int i = 0; i < numCols; ++i) {

                if (theRow.get(i) == null) {
                    theColPositions.add(i);
                }
            }

            return;
        }

        if (theColPositions.size() != (theArgs.size() - (haveTTLExpr ? 1 : 0))) {
            throw new QueryException(
                "The number of VALUES expressions is not equal to the number " +
                "of specified table columns", theLocation);
        }

        int[] pkPositions = theTable.getPrimKeyPositions();

        for (int i = 0; i < pkPositions.length; ++i) {

            if (theRow.get(pkPositions[i]) != null) {
                continue;
            }

            int j;
            for (j = 0; j < theColPositions.size(); ++j) {
                if (theColPositions.get(j) == pkPositions[i]) {
                    break;
                }
            }

            boolean pkIsIdentity = (identityColumnPos == pkPositions[i]);
            boolean pkIsUUID =
                (theTable.getGeneratedColumn() == pkPositions[i]);

            if (j == theColPositions.size() && !pkIsIdentity && !pkIsUUID) {
                throw new QueryException(
                    "No value specified for primary key column " +
                    theRow.getFieldName(pkPositions[i]), theLocation);
            }
        }

        for (int i = 0; i < numCols; ++i) {

            if (theRow.get(i) != null ||
                theTable.isPrimKeyAtPos(i) ||
                identityColumnPos == i) {
                continue;
            }

            FieldValueImpl fv = theRow.getDefinition().getDefaultValue(i);
            theRow.putInternal(i, fv, false);
        }
    }

    TableImpl getTable() {
        return theTable;
    }

    @Override
    int getNumChildren() {
        return theArgs.size();
    }

    Expr getArg(int i) {
        return theArgs.get(i);
    }

    void setArg(int i, Expr newExpr, boolean destroy) {
        theArgs.get(i).removeParent(this, destroy);
        theArgs.set(i, newExpr);
        newExpr.addParent(this);
    }

    RowImpl getRow() {
        return theRow;
    }

    ArrayList<Integer> getColPositions() {
        return theColPositions;
    }

    boolean isUpsert() {
        return theIsUpsert;
    }

    boolean updateTTL() {
        return theTTLKind != null;
    }

    UpdateKind getTTLKind() {
        return theTTLKind;
    }

    Expr getTTLExpr() {

        if (theTTLKind == UpdateKind.TTL_HOURS ||
            theTTLKind == UpdateKind.TTL_DAYS) {
            return theArgs.get(theArgs.size() - 1);
        }
        return null;
    }

    boolean hasReturningClause() {
        return theHasReturningClause;
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

        for (int i = 1; i < theArgs.size(); ++i) {
            theArgs.get(i).display(sb, formatter);
            if (i < theArgs.size() - 1) {
                sb.append(",\n");
            }
        }

        sb.append("\n");
        theArgs.get(0).display(sb, formatter);
    }
}
