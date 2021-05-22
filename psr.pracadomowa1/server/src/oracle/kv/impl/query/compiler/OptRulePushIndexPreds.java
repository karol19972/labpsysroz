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
import java.util.Collections;
import java.util.Map;

import oracle.kv.impl.api.table.IndexImpl;
import oracle.kv.impl.api.table.PrimaryKeyImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.compiler.Expr.ExprIter;
import oracle.kv.impl.query.compiler.Expr.ExprKind;
import oracle.kv.impl.query.compiler.ExprBaseTable.IndexHint;
import oracle.kv.impl.query.compiler.ExprSFW.FromClause;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.table.Index;

/**
 * The goal of this optimization rule is convert WHERE predicates into index
 * scan conditions in order to avoing a full table scan.
 *
 * The rule analyses the predicates in a WHERE clause to find, for each index
 * associated with a given table (including the table's primary index), (a) a
 * starting and/or ending key that could be applied to a scan over that index,
 * and (b) predicates that can be evaluated during the index scan from the
 * index columns only, thus filtering the retrieved index keys further.
 *
 * The rule assumes the WHERE-clause expr is in CNF. For each index, it first
 * collects all CNF factors that are "index predicates", i.e., they can be
 * evaluated fully from the index columns only. For example, if the current
 * index is the primary-key index and C1, C2, C3 are the primary-key columns,
 * {@literal "C1 > 10" and "C2 = 3 or C3 < 20"} are primary index preds. Then,
 * for each index column, in the order that these columns appear in the index
 * (or primary key) declaration, the rule looks-for and processes index preds
 * that are comparison preds {@literal (eg, "C1 > 10" is a comparison pred, but
 * "C2 = 3 or C3 < 20" is not)}. The possible outomes of processing an index
 * pred w.r.t. an index column are listed in the PredicateStatus enum
 * below. The rule stops processing the current index as soon as it finds an
 * index column for which there is no equality pred to be pushed to the index.
 *
 * After the rule has analyzed all indexes, it chooses the "best" index to
 * use among the indexes that had something pushed down to them.
 *
 * TODO: need a "good" heuristic to choose the "best" index, as well as
 * a compiler hint or USE INDEX clause to let the user decide.
 */
class OptRulePushIndexPreds {

    // TODO move this to the Optimizer obj, when we have one
    private RuntimeException theException = null;

    private ArrayList<IndexAnalyzer> theAnalyzers;

    private boolean theCompletePrimaryKey;

    RuntimeException getException() {
        return theException;
    }

    void apply(Expr expr) {

        try {
            applyInternal(expr);
        } catch (RuntimeException e) {
            theException = e;
        }
    }

    void applyInternal(Expr expr) {

        if (expr.getKind() == ExprKind.SFW) {
            boolean applied = applyOnSFW((ExprSFW)expr);
            if (applied) {
                return;
            }
        }

        ExprIter children = expr.getChildren();

        while (children.hasNext()) {
            Expr child = children.next();
            applyInternal(child);
        }
        children.reset();

        if (expr.getKind() == ExprKind.UPDATE_ROW) {

            if (!theCompletePrimaryKey) {
                throw new QueryException(
                    "Multi-row update is not supported. A complete and " +
                    "exact primary key must be specified in the WHERE clause.");
            }

            return;
        }
    }

    boolean applyOnSFW(ExprSFW sfw) {

        ExecuteOptions opts = sfw.getQCB().getOptions();
        FromClause fc = sfw.getFirstFrom();
        ExprBaseTable tableExpr = fc.getTableExpr();

        if (tableExpr == null) {
            return false;
        }

        boolean needsSortingIndex = false;
        String sortingOp = (sfw.hasSort() ? "order-by" : "group-by");

        if (opts.isProxyQuery() &&
            opts.getDriverQueryVersion() < ExecuteOptions.DRIVER_QUERY_V3 &&
            ((sfw.hasGroupBy() && sfw.getNumGroupExprs() > 0))) {
            needsSortingIndex = true;
        }

        IndexHint forceIndexHint = tableExpr.getForceIndexHint();
        TableImpl table = tableExpr.getTargetTable();
        int tablePos = tableExpr.getTargetTablePos();
        Map<String, Index> indexes = table.getIndexes();
        IndexAnalyzer primaryAnalyzer = null;

        /*
         * Try to push predicates in the primary index. We need to do this
         * always, because we need to discover if the query has a complete
         * shard key.
         */
        primaryAnalyzer = new IndexAnalyzer(sfw, tableExpr, tablePos,
                                            null/*index*/);
        primaryAnalyzer.analyze();

        theCompletePrimaryKey = false;
        if (primaryAnalyzer.hasShardKey()) {
            PrimaryKeyImpl pk = (PrimaryKeyImpl)
                primaryAnalyzer.getIndexKeys().get(0);
            theCompletePrimaryKey = pk.isComplete();
        }

        /* No reason to continue if the WHERE expr is always false */
        if (primaryAnalyzer.theSFW == null) {
            return true;
        }

        if (theCompletePrimaryKey) {
            sfw.removeSort();
        }

        if (forceIndexHint != null) {

            IndexImpl forcedIndex = forceIndexHint.theIndex;

            IndexAnalyzer analyzer =
                (forcedIndex == null ?
                 primaryAnalyzer :
                 new IndexAnalyzer(sfw, tableExpr, tablePos, forcedIndex));

            if (analyzer != primaryAnalyzer) {
                analyzer.analyze();
            }

            if (analyzer.theSFW == null) {
                return true;
            }

            if (needsSortingIndex && !sfw.isSortingIndex(forcedIndex)) {
                throw new QueryException(
                    sortingOp + " cannot be performed because there is the " +
                    "index forced via a hint does not sort all the table " +
                    "rows in the desired order.",
                    sfw.getLocation());
            }

            if (analyzer.isRejected()) {
                String indexName = (forcedIndex == null ?
                                    "primary" :
                                    forcedIndex.getName());
                throw new QueryException(
                    "The index forced via a hint cannot be used by the query.\n" +
                    "Hint index    : " + indexName + "\n",
                    sfw.getLocation());
            }

            analyzer.apply(primaryAnalyzer);
            addGenericSortOrGroup(analyzer);
            return true;
        }

        /*
         * If the query specifies a complete primary key, use the primary
         * index to execute it and remove any order-by.
         */
        if (theCompletePrimaryKey) {
            primaryAnalyzer.apply(primaryAnalyzer);
            return true;
        }

        theAnalyzers = new ArrayList<IndexAnalyzer>(1+indexes.size());

        if (!needsSortingIndex || sfw.isSortingIndex(null)) {
            theAnalyzers.add(primaryAnalyzer);
        }

        boolean alwaysFalse = false;

        for (Map.Entry<String, Index> entry : indexes.entrySet()) {

            IndexImpl index = (IndexImpl)entry.getValue();

            IndexAnalyzer analyzer =
                new IndexAnalyzer(sfw, tableExpr, tablePos, index);

            analyzer.analyze();

            if (!analyzer.isRejected() &&
                (!needsSortingIndex || sfw.isSortingIndex(index))) {
                theAnalyzers.add(analyzer);
            }

            if (analyzer.theSFW == null) {
                alwaysFalse = true;
                break;
            }
        }

        if (theAnalyzers.isEmpty()) {

            throw new QueryException(
                sortingOp + " cannot be performed because there is no index " +
                "that orders all the table rows in the desired order",
                sfw.getLocation());
        }

        if (!alwaysFalse) {
            chooseIndex(primaryAnalyzer);
        }

        return true;
    }

    /**
     * Choose the "best" index among the applicable ones and rewrite the query to
     * use that index.
     */
    void chooseIndex(IndexAnalyzer primaryAnalyzer) {

        IndexAnalyzer bestIndex = Collections.min(theAnalyzers);
        bestIndex.apply(primaryAnalyzer);
        addGenericSortOrGroup(bestIndex);
    }

    void addGenericSortOrGroup(IndexAnalyzer analyzer) {

        ExprSFW sfw = analyzer.theSFW;

        if (!sfw.usesSortingIndex()) {

            Expr topExpr = null;
            if (sfw.hasGroupBy()) {
                topExpr = sfw.addGenericGroupBy();
            } else if (sfw.hasSort()) {
                topExpr = sfw.addGenericSort();
            }

            if (topExpr != null) {
                sfw.replace(topExpr,
                            (topExpr.getInput() == sfw ?
                             topExpr :
                             topExpr.getInput()),
                            false);
            }
        }

        if (analyzer.theOptimizeMKIndexSizeCall) {
            sfw.optimizeMKIndexSizeCall(analyzer.getIndex());
        }
    }
}
