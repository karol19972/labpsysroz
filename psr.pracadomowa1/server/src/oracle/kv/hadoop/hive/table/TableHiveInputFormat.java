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

package oracle.kv.hadoop.hive.table;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import oracle.kv.hadoop.table.TableInputSplit;
import oracle.kv.hadoop.table.TableRecordReader;
import oracle.kv.impl.api.query.PreparedStatementImpl;
import oracle.kv.impl.api.query.PreparedStatementImpl.DistributionKind;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.query.compiler.CompilerAPI;
import oracle.kv.query.PreparedStatement;
import oracle.kv.table.FieldDef;
import oracle.kv.table.Index;
import oracle.kv.table.IndexKey;
import oracle.kv.table.RecordValue;
import oracle.kv.table.Table;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.FunctionRegistry;
import org.apache.hadoop.hive.ql.index.IndexPredicateAnalyzer;
import org.apache.hadoop.hive.ql.index.IndexSearchCondition;
import org.apache.hadoop.hive.ql.lib.DefaultGraphWalker;
import org.apache.hadoop.hive.ql.lib.DefaultRuleDispatcher;
import org.apache.hadoop.hive.ql.lib.Dispatcher;
import org.apache.hadoop.hive.ql.lib.GraphWalker;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.lib.NodeProcessor;
import org.apache.hadoop.hive.ql.lib.NodeProcessorCtx;
import org.apache.hadoop.hive.ql.lib.Rule;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeConstantDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDescUtils;
import org.apache.hadoop.hive.ql.plan.ExprNodeFieldDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFBaseCompare;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFBridge;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFIn;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPAnd;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPEqual;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPEqualOrGreaterThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPEqualOrLessThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPGreaterThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPLessThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPOr;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToBinary;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToChar;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToDate;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToDecimal;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToUnixTimeStamp;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToUtcTimestamp;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFToVarchar;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Hadoop MapReduce version 1 InputFormat class for reading data from an
 * Oracle NoSQL Database when processing a Hive query against data written
 * to that database using the Table API.
 * <p>
 * Note that whereas this class is an instance of a version 1 InputFormat
 * class, in order to exploit and reuse the mechanisms provided by the
 * Hadoop integration classes (in package oracle.kv.hadoop.table), this class
 * also creates and manages an instance of a version 2 InputFormat.
 *
 * - Note on Logging -
 *
 * Two loggers are currently employed by this class:
 * <ul>
 *   <li> One logger based on Log4j version 1, accessed via the
 *        org.apache.commons.logging wrapper.
 *   <li> One logger based on the Log4j2 API.
 * </ul>
 *
 * Two loggers are necessary because Hive2 employs the Log4j2 logging
 * mechanism, whereas logging in Big Data SQL 4.0 is still based on Log4j
 * version 1. As a result, when one executes a Hive query and wishes to
 * log output from this class, the Log4j2-based logger specified by this
 * class must be added to the Hive2 logging configuration file. On the
 * other hand, to log output from this class when executing a Big Data SQL
 * query, the Log4j v1 logger must be added to the Big Data SQL logging
 * configuration file.
 *
 * In the future, when Big Data SQL changes its logging mechanims from
 * Log4j v1 to Log4j2, this class should be changed to employ only the
 * Log4j2-based logger.
 */
public class TableHiveInputFormat<K, V>
                 implements org.apache.hadoop.mapred.InputFormat<K, V> {

    private static final String THIS_CLASSNAME =
                                    TableHiveInputFormat.class.getName();

    private static final Log LOG = LogFactory.getLog(THIS_CLASSNAME);
    private static final Logger LOG2 = LogManager.getLogger(THIS_CLASSNAME);

    /*
     * Fields containing the info used to determine how the query will be
     * executed; that is,
     * - using the PrimaryKey via a 'table scan'; where multiple splits,
     *   each referencing disjoint partition sets, are generated by
     *   SplitBuilder.
     * - using the ShardKey via a single partition iteration; where only
     *   one split referencing that single partition is generated.
     * - using an IndexKey via an 'index scan'; where one split per shard
     *   is generated.
     * - using a native ONQL query referencing the predicate (WHERE clause);
     *   in which multiple splits, each referencing disjoint partition sets,
     *   are generated by SplitBuilder.
     * - using a native ONQL query referencing the predicate (WHERE clause);
     *   in which a single split referencing the partition associated with
     *   the ShardKey corresponding to the predicate's components is
     *   generated.
     * - using a native ONQL query referencing the predicate (WHERE clause);
     *   in which one split per shard is generated.
     */
    private static int queryBy =
                           TableInputSplit.QUERY_BY_PRIMARY_ALL_PARTITIONS;
    private static String whereClause = null;
    private static Integer shardKeyPartitionId = null;

    /*
     * The set of comparison operations that are currently supported for
     * predicate pushdown; '=', '>=', '>', '<=', '<'.
     */
    private static final Map<String, String> COMPARE_OPS =
                                           new HashMap<String, String>();
    static {
        COMPARE_OPS.put(GenericUDFOPEqual.class.getName(), "=");
        COMPARE_OPS.put(GenericUDFOPEqualOrGreaterThan.class.getName(), ">=");
        COMPARE_OPS.put(GenericUDFOPGreaterThan.class.getName(), ">");
        COMPARE_OPS.put(GenericUDFOPEqualOrLessThan.class.getName(), "<=");
        COMPARE_OPS.put(GenericUDFOPLessThan.class.getName(), "<");
    }

    /**
     * Returns the RecordReader for the given InputSplit.
     * <p>
     * Note that the RecordReader that is returned is based on version 1 of
     * MapReduce, but wraps and delegates to a YARN based (MapReduce version2)
     * RecordReader. This is done because the RecordReader provided for
     * Hadoop integration is YARN based, whereas the Hive infrastructure
     * requires a version 1 RecordReader.
     * <p>
     * Additionally, note that when query execution occurs via a MapReduce
     * job, this method is invoked by backend processes running on each
     * DataNode in the Hadoop cluster; where the splits are distributed to
     * each DataNode. When the query is simple enough to be executed by the
     * Hive infrastructure from data in the metastore -- that is, without
     * MapReduce -- this method is invoked by the frontend Hive processes;
     * once for each split. For example, if there are 6 splits and the query
     * is executed via a MapReduce job employing only 3 DataNodes, then each
     * DataNode will invoke this method twice; once for each of 2 splits in
     * the set of splits. On the other hand, if MapReduce is not employed,
     * then the Hive frontend will invoke this method 6 separate times;
     * one per different split. In either case, when this method is
     * invoked, the given Version 1 <code>split</code> has already been
     * populated with a fully populated Version 2 split; and the state of
     * that encapsulated Version 2 split can be exploited to construct the
     * necessary Version 1 RecordReader encapsulating a fully functional
     * Version 2 RecordReader, as required by YARN.
     */
    @Override
    @SuppressWarnings(value = {"unchecked",
                               /*
                                * Ignore the fact that the v2Reader resource
                                * isn't closed -- we want to return it open!
                                */
                               "resource"})
    public RecordReader<K, V> getRecordReader(InputSplit split,
                                              JobConf job,
                                              Reporter reporter)
        throws IOException {

        LOG.trace("split = " + split);
        LOG2.trace("split = " + split);

        queryBy = ((TableHiveInputSplit) split).getQueryBy();
        whereClause = ((TableHiveInputSplit) split).getWhereClause();

        final TableInputSplit v2Split =
            ((TableHiveInputSplit) split).getV2Split();

        final TableRecordReader v2Reader = new TableRecordReader();
        try {
            v2Reader.initialize(v2Split, new TableTaskAttemptContext(job));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        /*
         * Must perform an unchecked cast; otherwise an eclipse warning about
         * RecordReader being a raw type that needs to be parameterized will
         * occur. But an eclipse unchecked warning cannot be avoided because
         * of type erasure; so suppress unchecked warnings in this method.
         */
        return (RecordReader<K, V>) (new TableHiveRecordReader(job, v2Reader));
    }

    /**
     * Returns an array containing the input splits for the given job.
     * <p>
     * Implementation Note: when V1V2TableUtil.getInputFormat() is called by
     * this method to retrieve the TableInputFormat instance to use for a given
     * query, only the VERY FIRST call to V1V2TableUtil.getInputFormat() (after
     * the query has been entered on the command line and the input info for
     * the job has been reset) will construct an instance of TableInputFormat;
     * all additional calls -- while that query is executing -- will always
     * return the original instance created by that first call. Note also
     * that in addition to constructing a TableInputFormat instance, that
     * first call to V1V2TableUtil.getInputFormat() also populates the
     * splitMap; which is achieved via a call to getSplits() on the newly
     * created TableInputFormat instance.
     *
     * Since the first call to V1V2TableUtil.getInputFormat() has already
     * called TableInputFormat.getSplits() and placed the retrieved splits
     * in the splitMap, it is no longer necessary to make any additional
     * calls to TableInputFormat.getSplits(). Not only is it not necessary to
     * call TableInputFormat.getSplits(), but such a call should be avoided;
     * to avoid additional, unnecessary remote calls to KVStore. Thus, avoid
     * calls such as, V1V2TableUtil.getInputFormat().getSplits(); since such a
     * call may result in two successive calls to TableInputFormat.getSplits().
     * To avoid this situation, a two step process like the following should
     * be employed to retrieve and return the desired splits:
     * <p>
     * 1. First call V1V2TableUtil.getInputFormat(); which when called
     *    repeatedly, will always return the same instance of
     *    TableInputFormat.
     * 2. Call V1V2TableUtil.getSplitMap(), then retrieve and return the
     *    desired splits from the returned map.
     */
    @Override
    public InputSplit[] getSplits(JobConf job, int numSplits)
        throws IOException {

        V1V2TableUtil.getInputFormat(
            job, queryBy, whereClause, shardKeyPartitionId);

        final Set<TableHiveInputSplit> v1SplitKeySet =
            V1V2TableUtil.getSplitMap(
                job, queryBy, whereClause, shardKeyPartitionId).keySet();

        return v1SplitKeySet.toArray(
                   new TableHiveInputSplit[v1SplitKeySet.size()]);
    }

    /**
     * Analyzes the components of the given <code>predicate</code> with
     * respect to the columns of the Hive table (retrieved using the given
     * <code>Deserializer</code>) and if the predicate contains a valid
     * <code>PrimaryKey</code> or <code>IndexKey</code> and optional
     * <code>FieldRange</code>, then will use that information to create
     * return an <code>IndexPredicateAnalyzer</code>. The analyzer returned
     * by this method can be used to decompose the predicate into two
     * disjoint sets:
     * <ul>
     *   <li>A set of search conditions that correspond to the valid
     *       <code>PrimaryKey</code> or <code>IndexKey</code> (and optional
     *       <code>FieldRange</code>) found during the analyzis phase,
     *       that can be pushed to the KVStore server (via one of the
     *       <code>TableIterator</code>s; where filtering can be applied
     *       on the backend rather than the Hive frontend.
     *   <li>A set containing the remaining components of the given
     *       <code>predicate</code> (the <em>residual</em> predicate), which
     *       Hive will apply to the results returned after the pushed
     *       search conditions have first been applied on the backend.
     * </ul>
     *
     * If multiple (search conditions, residual predicate) pairs result from
     * the above analysis, then the analyzer that is returned will be the
     * analyzer corresponding to the optimal search conditions.
     * <p>
     * If no (search conditions, residual predicate) pairs result from the
     * above analysis, (that is, it is determined that no combination of
     * components from the given <code>predicate</code> can be pushed to
     * the backend server), then this method returns <code>null</code>;
     * in which case, all filtering should be performed by Hive on the
     * frontend.
     */
    static IndexPredicateAnalyzer sargablePredicateAnalyzer(
                                      final ExprNodeDesc predicate,
                                      final TableSerDe deserializer) {

        if (deserializer == null) {
            return null;
        }

        final Table table = deserializer.getKvTable();
        if (table == null) {
            return null;
        }
        LOG.debug("sargablePredicateAnalyzer: predicate = " + predicate);
        LOG2.debug("sargablePredicateAnalyzer: predicate = " + predicate);

        final List<String> hiveColumnNames =
            deserializer.getSerdeParams().getColumnNames();

        final List<List<String>> sargableList = new ArrayList<List<String>>();
        Integer curListIndx = 0;
        Integer indxOfMaxList = -1;
        Integer maxListSize = Integer.MIN_VALUE;

        /*
         * - IndexKeys -
         *
         * For each index, get the corresponding hive column names and
         * determine if the columns corresponding to the fields of that
         * index can be used to form a predicate that can be pushed.
         * That is, determine if those corresponding fields form a valid
         * IndexKey and/or FieldRange (as defined by the Table API).
         *
         * Note that because Hive and KVStore handle case differently column
         * name comparison is performed using lower case, but once a match
         * is found, this method defers to the case used by the actual Hive
         * column name.
         *
         * Also note that it is important that the elements of the curColNames
         * list that is populated below MUST be in the SAME ORDER as the
         * elements of the associated IndexKey before calling validKey().
         */
        final Map<String, List<String>> indexColMap =
            getIndexedColumnMapLower(table);

        for (Map.Entry<String, List<String>> entry : indexColMap.entrySet()) {

            final List<String> curColNames = new ArrayList<String>();
            for (String indexColName : entry.getValue()) {
                for (String hiveColName : hiveColumnNames) {
                    if (indexColName.equals(hiveColName.toLowerCase())) {
                        curColNames.add(hiveColName);
                    }
                }
            }

            /*
             * For the current (ordered) list of index column names, first
             * remove any invalid operations (and corresponding column name)
             * from the given predicate.
             */
            final IndexPredicateAnalyzer curAnalyzer =
                basicPredicateAnalyzer(curColNames);
            final List<IndexSearchCondition> curSearchConditions =
                new ArrayList<IndexSearchCondition>();
            curAnalyzer.analyzePredicate(predicate, curSearchConditions);
            LOG.debug("sargablePredicateAnalyzer: search conditions " +
                      "[indexKey] = " + curSearchConditions);
            LOG2.debug("sargablePredicateAnalyzer: search conditions " +
                       "[indexKey] = " + curSearchConditions);

            /*
             * Validate only those columns referenced in the current
             * search conditions. Remove duplicates, in the SAME ORDER as
             * the fields of the current IndexKey, and with "gaps" inserted.
             */
            final Set<String> searchColSet = new HashSet<String>();
            for (IndexSearchCondition cond : curSearchConditions) {
                /* Remove duplicates. */
                searchColSet.add(
                    cond.getColumnDesc().getColumn().toLowerCase());
            }

            final List<String> curSearchCols = new ArrayList<String>();
            int nAdded = 0;
            for (String curColName : curColNames) {
                if (searchColSet.contains(curColName)) {
                    curSearchCols.add(curColName);
                    nAdded++;
                    if (nAdded == searchColSet.size()) {
                        break;
                    }
                } else {
                    /* Insert a "gap". */
                    curSearchCols.add(null);
                }
            }

            boolean predicateFound = false;
            while (curSearchCols.size() > 0 && !predicateFound) {

                if (validKey(curSearchConditions, curSearchCols)) {

                    sargableList.add(curSearchCols);

                    final Integer curListSize = curSearchCols.size();
                    if (maxListSize < curListSize) {
                        maxListSize = curListSize;
                        indxOfMaxList = curListIndx;
                    }
                    curListIndx++;
                    predicateFound = true;

                } else {

                    /* Remove last element & corresponding search conditions.*/
                    final String invalidCol =
                        curSearchCols.remove(curSearchCols.size() - 1);
                    if (invalidCol != null) {
                        final List<Integer> removeIndxs =
                            new ArrayList<Integer>();
                        for (int i = 0; i < curSearchConditions.size(); i++) {
                            final IndexSearchCondition cond =
                                curSearchConditions.get(i);
                            final String col =
                                cond.getColumnDesc().getColumn().toLowerCase();
                            if (invalidCol.equals(col)) {
                                removeIndxs.add(i);
                            }
                        }

                        /* Remove from end of list for consistent indexes. */
                        for (int i = removeIndxs.size() - 1; i >= 0; i--) {
                            curSearchConditions.remove(
                                                    (int) removeIndxs.get(i));
                        }
                    }

                    /*
                     * Remove null elements from end 'til next non-null
                     * element.
                     *
                     * For example, suppose curSearchCols initially contained
                     * [color, class, model, count, make]. Then, after the last
                     * (non-null) element is removed above, suppose it becomes
                     * [color, class, null, null]; where each null element does
                     * not correspond to any elements in curSearchConditions.
                     * Then, before proceeding, the null elements at the end
                     * are removed to produce a set with non-null last element;
                     * that is, [color, class].
                     */
                    final List<Integer> removeIndxs = new ArrayList<Integer>();
                    for (int i = curSearchCols.size() - 1; i >= 0; i--) {
                        if (curSearchCols.get(i) == null) {
                            removeIndxs.add(i);
                        } else {
                            break;
                        }
                    }
                    for (int i = 0; i < removeIndxs.size(); i++) {
                        curSearchCols.remove((int) removeIndxs.get(i));
                    }
                }
            }
        }

        /*
         * - PrimaryKey -
         *
         * For the table's PrimaryKey, get the corresponding hive column names
         * and determine if the columns corresponding to the fields of that
         * key can be used to form a predicate that can be pushed. That is,
         * determine if those corresponding fields form a valid PrimaryKey
         * and/or FieldRange (as defined by the Table API).
         *
         * Note that because Hive and KVStore handle case differently column
         * name comparison is performed using lower case, but once a match
         * is found, this method defers to the case used by the actual Hive
         * column name.
         *
         * Also note that it is important that the elements of the curColNames
         * list that is populated below MUST be in the SAME ORDER as the
         * elements of the PrimaryKey before calling validKey().
         */
        final List<String> curColNames = new ArrayList<String>();
        for (String primaryColName : getPrimaryColumnsLower(table)) {
            for (String hiveColName : hiveColumnNames) {
                if (primaryColName.equals(hiveColName.toLowerCase())) {
                    curColNames.add(hiveColName);
                }
            }
        }

        /*
         * For the current (ordered) list of primary key column names, first
         * remove any invalid operations (and corresponding column name) from
         * the given predicate.
         */
        final IndexPredicateAnalyzer curAnalyzer =
            basicPredicateAnalyzer(curColNames);
        final List<IndexSearchCondition> curSearchConditions =
            new ArrayList<IndexSearchCondition>();
        curAnalyzer.analyzePredicate(predicate, curSearchConditions);
        LOG.debug("sargablePredicateAnalyzer: search conditions " +
                  "[primaryKey] = " + curSearchConditions);
        LOG2.debug("sargablePredicateAnalyzer: search conditions " +
                   "[primaryKey] = " + curSearchConditions);

        /*
         * Validate only those columns referenced in the current
         * search conditions. Remove duplicates, in the SAME ORDER as
         * the fields of the PrimaryKey, and with "gaps" inserted.
         */
        final Set<String> searchColSet = new HashSet<String>();
        for (IndexSearchCondition cond : curSearchConditions) {
            /* Remove duplicates. */
            searchColSet.add(cond.getColumnDesc().getColumn().toLowerCase());
        }

        final List<String> curSearchCols = new ArrayList<String>();
        int nAdded = 0;
        for (String curColName : curColNames) {
            if (searchColSet.contains(curColName)) {
                curSearchCols.add(curColName);
                nAdded++;
                if (nAdded == searchColSet.size()) {
                    break;
                }
            } else {
                /* Insert a "gap". */
                curSearchCols.add(null);
            }
        }

        boolean predicateFound = false;
        while (curSearchCols.size() > 0 && !predicateFound) {

            if (validKey(curSearchConditions, curSearchCols)) {

                sargableList.add(curSearchCols);

                final Integer curListSize = curSearchCols.size();
                if (maxListSize < curListSize) {

                    maxListSize = curListSize;
                    indxOfMaxList = curListIndx;
                }
                curListIndx++;
                predicateFound = true;

            } else {

                /* Remove last element and corresponding search conditions. */
                final String invalidCol =
                    curSearchCols.remove(curSearchCols.size() - 1);
                if (invalidCol != null) {
                    final List<Integer> removeIndxs = new ArrayList<Integer>();
                    for (int i = 0; i < curSearchConditions.size(); i++) {
                        final IndexSearchCondition cond =
                            curSearchConditions.get(i);
                        final String col =
                            cond.getColumnDesc().getColumn().toLowerCase();
                        if (invalidCol.equals(col)) {
                            removeIndxs.add(i);
                        }
                    }

                    /* Remove from end of list to keep indexes consistent. */
                    for (int i = removeIndxs.size() - 1; i >= 0; i--) {
                        curSearchConditions.remove((int) removeIndxs.get(i));
                    }
                }

                /*
                 * Remove null elements from end 'til next non-null element.
                 *
                 * For example, suppose curSearchCols initially contained
                 * [type, make, model, null, color]. Then, after the last
                 * (non-null) element is removed above, suppose it becomes
                 * [type, make, null, null]; where each null element does
                 * not correspond to any elements in curSearchConditions.
                 * Then, before proceeding, the null elements at the end
                 * are removed to produce a set with non-null last element;
                 * that is, [type, make].
                 */
                final List<Integer> removeIndxs = new ArrayList<Integer>();
                for (int i = curSearchCols.size() - 1; i >= 0; i--) {
                    if (curSearchCols.get(i) == null) {
                        removeIndxs.add(i);
                    } else {
                        break;
                    }
                }

                for (int i = 0; i < removeIndxs.size(); i++) {
                    curSearchCols.remove((int) removeIndxs.get(i));
                }
            }
        }

        if (indxOfMaxList >= 0 && sargableList.size() > 0) {
            return basicPredicateAnalyzer(sargableList.get(indxOfMaxList));
        }

        /* No valid keys or field range, so predicate cannot be pushed. */
        return null;
    }

    /**
     * Returns a predicate analyzer that allows all (and only) the valid
     * operations specified in this class; and operates on only the column
     * (field) names in the <code>List</code> input to this method.
     */
    static IndexPredicateAnalyzer basicPredicateAnalyzer(
                final List<String> colNames) {

        final IndexPredicateAnalyzer analyzer = new IndexPredicateAnalyzer();

        for (String compareOp : COMPARE_OPS.keySet()) {
            analyzer.addComparisonOp(compareOp);
        }

        for (String colName : colNames) {
            analyzer.allowColumnName(colName);
        }
        LOG.debug("allowable columns = " + colNames);
        LOG2.debug("allowable columns = " + colNames);
        return analyzer;
    }

    /**
     * Convenience method that returns a <code>List</code> containing all of
     * the column names (fields) that make up the <code>PrimaryKey</code> of
     * the given <code>Table</code>; where each column name is converted to
     * <em>lower case</em>, and is in valid key order (as defined by the
     * Table API).
     */
    static List<String> getPrimaryColumnsLower(final Table table) {
        final List<String> retList = new ArrayList<String>();
        if (table == null) {
            return retList;
        }

        for (String colName : table.getPrimaryKey()) {
            retList.add(colName.toLowerCase());
        }
        return retList;
    }

    /**
     * Convenience method that returns a <code>Map</code> whose key is
     * the name of one of the given <code>Table</code>'s indexes, and
     * corresponding value is a <code>List</code> whose elements are the
     * names of that index's columns (fields); where each such column name
     * is converted to <em>lower case</em>, and is in valid key order
     * (as defined by the Table API).
     */
    static Map<String, List<String>> getIndexedColumnMapLower(
                                         final Table table) {
        final Map<String, List<String>> retMap =
                                        new HashMap<String, List<String>>();
        if (table == null) {
            return retMap;
        }

        for (Index index : table.getIndexes().values()) {
            final List<String> colNames = new ArrayList<String>();
            for (String colName : index.getFields()) {
                colNames.add(colName.toLowerCase());
            }
            retMap.put(index.getName(), colNames);
        }
        return retMap;
    }

    /**
     * Convenience method called by <code>TableStorageHandlerBase</code>
     * to initialize/reset to their default values, the fields containing
     * the info used to determine how the query will be executed.
     */
    static void resetQueryInfo() {
        queryBy = TableInputSplit.QUERY_BY_PRIMARY_ALL_PARTITIONS;
        whereClause = null;
        shardKeyPartitionId = null;
    }

    /**
     * Convenience method called by the <code>decomposePredicate</code> method
     * of <code>TableStorageHandlerBase</code> class after it has determined
     * that the query consists of a predicate in which all or part of the
     * predicate can be pushed to the backend for server side filtering. If
     * the given <code>searchConditions</code> are not <code>NULL</code>,
     * then this method determines whether those search conditions are valid
     * for pushing to the backend and, if so, determines how they should be
     * pushed; by PrimaryKey, ShardKey, IndexKey, ALL_PARTITIONS native query,
     * SINGLE_PARTITION native query, or ALL_SHARDS native query.
     * <p>
     * If the given search conditions are not <code>NULL</code> and it is
     * determined that they are <code>Index</code> based, then the
     * <code>queryBy</code> field of this class is set to the
     * value <code>TableInputSplit.QUERY_BY_INDEX</code> to tell the
     * associated <code>TableInputFormat</code> to build splits (in the
     * <code>getSplits</code> method) and iterate (scan) based on the shards
     * (<code>RepGroup</code>s) of the store and the computed
     * <code>IndexKey</code>; rather than the store's partition sets and
     * the table's <code>PrimaryKey</code> or <code>ShardKey</code>.
     * <p>
     * If, on the other hand, the search conditions are <code>NULL</code>, then
     * it has previously been determined that neither an <code>IndexKey</code>
     * based nor a <code>PrimaryKey</code> or <code>ShardKey</code> based scan
     * can be performed. In this case, the <code>queryBy</code> field of this
     * class is set to one of the <code>TableInputSplit.QUERY_BY_*</code>
     * values, to tell the associated <code>TableInputFormat</code> to build
     * the splits based on either the store's partitions or shards, and to
     * iterate using the given WHERE clause in a native NoSQL DB query.
     */
    static void setQueryInfo(final TableSerDe deserializer,
                            final String queryWhereClause) {
        final List<IndexSearchCondition> emptySearchConditions =
            Collections.emptyList();
        setQueryInfo(emptySearchConditions, deserializer, queryWhereClause);
    }

    static void setQueryInfo(final List<IndexSearchCondition> searchConditions,
                             final TableSerDe deserializer,
                             final String queryWhereClause) {

        /* Set defaults */
        queryBy = TableInputSplit.QUERY_BY_PRIMARY_ALL_PARTITIONS;
        whereClause = queryWhereClause;
        shardKeyPartitionId = null;

        if (deserializer == null) {
            return;
        }

        /*
         * Determine the distribution kind, which is then used to determine
         * whether or not a single partition can be queried.
         */
        final String onqlQuery =
            "SELECT * FROM " + deserializer.getKvTableName() +
            " WHERE " + whereClause;

        final TableAPIImpl tableApi =
            (TableAPIImpl) (deserializer.getKvTableApi());

        final DistributionKind distributionKind =
            getDistributionKind(tableApi, onqlQuery);

        LOG.debug("distributionKind = " + distributionKind);
        LOG2.debug("distributionKind = " + distributionKind);

        /*
         * If the distribution kind is SINGLE_PARTITION, then the query will
         * always be handled by ShardKey table iteration, not a native query.
         * Note that the partition id of the shard key was already found
         * above, in the call to getDistributionKind.
         */
        if (DistributionKind.SINGLE_PARTITION.equals(distributionKind)) {

            queryBy = TableInputSplit.QUERY_BY_PRIMARY_SINGLE_PARTITION;
        }

        /*
         * Change to native query only if empty searchConditions is input.
         *
         * Note that empty searchConditions means that no part of the query's
         * predicate components form a valid PrimaryKey or IndexKey; either
         * partial or complete. That is, either none of the predicate's
         * components correspond to the fields of the PrimaryKey, ShardKey,
         * or an IndexKey; or the predicate consists of 'OR' statements and/or
         * 'IN' lists. As a result, the SINGLE_PARTITION case should never
         * occur when searchConditions is empty; and thus, it should never
         * be necessary to handle the SINGLE_PARTITION case with a native
         * query.
         */
        if (searchConditions.isEmpty()) {

            queryBy = TableInputSplit.QUERY_BY_ONQL_ALL_PARTITIONS;

            /*
             * Log a warning if the distribution kind contradicts the
             * searchConditions; that is, SINGLE_PARTITION with empty
             * searchConditions.
             */
            if (DistributionKind.SINGLE_PARTITION.equals(distributionKind)) {
                LOG.warn("Empty searchConditions but unexpected " +
                         DistributionKind.ALL_SHARDS + "distribution kind. " +
                         "Will proceed using a native query with " +
                         DistributionKind.ALL_PARTITIONS + "instead.");
                LOG2.warn("Empty searchConditions but unexpected " +
                          DistributionKind.ALL_SHARDS + "distribution kind. " +
                          "Will proceed using a native query with " +
                          DistributionKind.ALL_PARTITIONS + "instead.");

            } else if (DistributionKind.ALL_SHARDS.equals(distributionKind)) {
                queryBy = TableInputSplit.QUERY_BY_ONQL_SHARDS;
            }
        }

        /*
         * Index scan takes priority over the previous query types. Thus,
         * change to index scan if the conditions dictate.
         */
        if (indexKeyFromSearchConditionsNoRange(
                searchConditions, deserializer.getKvTable()) != null) {
            queryBy = TableInputSplit.QUERY_BY_INDEX;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("queryBy = " + queryBy);
            LOG.debug("whereClause = " + whereClause);
            if (shardKeyPartitionId != null) {
                LOG.debug("shardKeyPartitionId = " + shardKeyPartitionId);
            }
        }

        if (LOG2.isDebugEnabled()) {
            LOG2.debug("queryBy = " + queryBy);
            LOG2.debug("whereClause = " + whereClause);
            if (shardKeyPartitionId != null) {
                LOG2.debug("shardKeyPartitionId = " + shardKeyPartitionId);
            }
        }
    }

    /**
     * Convenience method that prepares the given query (in the form
     * 'SELECT * FROM <tablename> WHERE <predicate>') and uses the resulting
     * PreparedStatement to retrieve the corresponding distribution kind
     * (ALL_PARTITIONS, SINGLE_PARTITION, or ALL_SHARDS). If the distribution
     * kind retrieved is SINGLE_PARTITION, then this method will also use
     * the prepared statement to obtain and set the partition id of that
     * single shard.
     */
    private static DistributionKind getDistributionKind(
        final TableAPIImpl tableApi, final String queryToPrepare) {

        DistributionKind distributionKind = null;
        try {
            final PreparedStatement statement =
                CompilerAPI.prepare(
                    tableApi, queryToPrepare.toCharArray(), null);
            distributionKind =
                ((PreparedStatementImpl) statement).getDistributionKind();

            if (DistributionKind.SINGLE_PARTITION.equals(distributionKind)) {
                shardKeyPartitionId =
                    getShardKeyPartitionId((PreparedStatementImpl) statement);
            }
        } catch (Exception e) {
            LOG.warn("exception on query preparation [" + e + "]", e);
            LOG2.warn("exception on query preparation [" + e + "]", e);
        }
        return distributionKind;
    }

    /**
     * Convenience method that uses the given prepared statement (query) to
     * retreive and return the partition id corresponding to the complete
     * shard key referenced in that query. Note that this method is called
     * only when the distribution kind is SINGLE_PARTITION; which means that
     * the statement's associated query will always reference a complete
     * shard key.
     */
    private static Integer getShardKeyPartitionId(
        final PreparedStatementImpl statement) {

        if (statement == null) {
            return null;
        }
        return Integer.valueOf((statement.getPartitionId()).getPartitionId());
    }

    /**
     * Assumes the columns in the given search conditions correspond to the
     * fields of a <em>valid</em> key of an <code>Index</code> in the given
     * table, and uses that information to construct and return a partial
     * <code>IndexKey</code> that can be "pushed" to the store for scanning
     * and filtering the associated index in the backend server; or returns
     * <code>null</code> (or an "empty" key) if those search conditions
     * do not satisfy the necessary criteria for constructing the key.
     * The criteria used when constructing the key is as follows:
     *
     * For the set of columns in the search conditions that are associated
     * with the '=' operation, if those columns form a valid key (that is,
     * the first 'N' fields of the index's key, with no "gaps"), then those
     * fields are used to construct the key to return. Additionally, if the
     * search conditions reference the 'N+1st' field of the index's key as
     * a <code>FieldRange</code>, then that field is <em>not</em> included
     * in the returned key; so that the <code>FieldRange</code> can be
     * handled ("pushed") separately.
     */
    static IndexKey indexKeyFromSearchConditionsNoRange(
                       final List<IndexSearchCondition> searchConditions,
                       final Table table) {

        if (searchConditions == null || table == null) {
            return null;
        }

        final Map<String, List<String>> indexColMap =
            getIndexedColumnMapLower(table);

        if (indexColMap == null || indexColMap.isEmpty()) {
            return null; /* Happens when the table contains no indexes. */
        }

        /*
         * To help construct an ordered list of key fields, with "gaps"
         * inserted (see below), retrieve the columns referenced in the
         * given search conditions, with duplicates removed (duplicates
         * can occur when the column is associated with a range).
         */
        final Set<String> searchColSet = new HashSet<String>();
        for (IndexSearchCondition cond : searchConditions) {
            /* Remove duplicates. */
            searchColSet.add(cond.getColumnDesc().getColumn().toLowerCase());
        }

        /*
         * Find the index from which to construct the key. Note that the
         * table may contain multiple indexes with fields corresponding to
         * the columns specified in the search conditions. Thus, the index
         * that produces the optimal key (most fields that satisfy the
         * criteria) should first be found.
         */
        String indexName = null;
        int maxCols = Integer.MIN_VALUE;

        for (Map.Entry<String, List<String>> entry : indexColMap.entrySet()) {

            final String curIndexName = entry.getKey();

            final List<String> curIndexFields = entry.getValue();

            /*
             * From the searchColSet constructed above, create a list
             * containing the names of the fields of the current index
             * in the ORDER required by the index. If a field from the
             * index is not referenced by the search columns, then that
             * is considered a "gap" and null is inserted in the list.
             * If the list contains a "gap", then a valid key cannot be
             * constructed.
             */
            final List<String> orderedSearchCols = new ArrayList<String>();
            int nAdded = 0;
            for (String indexFieldName : curIndexFields) {
                if (searchColSet.contains(indexFieldName)) {
                    orderedSearchCols.add(indexFieldName);
                    nAdded++;
                    if (nAdded == searchColSet.size()) {
                        break;
                    }
                } else {
                    /* Insert a "gap". */
                    orderedSearchCols.add(null);
                }
            }

            /*
             * Select the index having the maximum number of fields referenced
             * in the search conditions that are valid.
             */
            if (validKey(searchConditions, orderedSearchCols)) {
                if (orderedSearchCols.size() > maxCols) {
                    maxCols = orderedSearchCols.size();
                    indexName = curIndexName;
                }
            }
        }

        if (indexName == null) {
            return null;
        }

        /*
         * If there is a column in the search conditions that is associated
         * with a field range, than exclude it from the key construction
         * process; so that the field range can be handled separately.
         */
        final Map<String, IndexSearchCondition> searchCondMap =
                                  new HashMap<String, IndexSearchCondition>();
        for (IndexSearchCondition cond : searchConditions) {
            final String colName =
                             cond.getColumnDesc().getColumn().toLowerCase();
            if ((GenericUDFOPEqual.class.getName()).equals(
                                                    cond.getComparisonOp())) {
                searchCondMap.put(colName, cond);
            }
        }

        /*
         * If the map constructed above is empty, then no columns in the
         * search conditions correspond to the '=' operation. This means
         * that the first field of the index's key is in a FieldRange;
         * which will be handled separately (in the RecordReader). Thus,
         * this method returns an empty IndexKey (so that filtering will
         * be performed on that range, and all remaining fields are
         * 'wildcarded').
         */
        final Index index = table.getIndex(indexName);
        final IndexKey indexKey = index.createIndexKey();

        if (searchCondMap.isEmpty()) {
            return indexKey;
        }

        /*
         * Use the fields from the index identified above that correspond
         * the search condition columns associated with '=' to construct
         * the key to return.
         */
        final List<String> fieldNames = index.getFields();
        for (String fieldName : fieldNames) {

            final IndexSearchCondition searchCond =
                              searchCondMap.get(fieldName.toLowerCase());
            if (searchCond == null) {
                /* null ==> no more elements in searchCondMap. Done. */
                return indexKey;
            }

            populateKey(fieldName, searchCond.getConstantDesc(), indexKey);
        }
        return indexKey;
    }

    /**
     * Convenience/utility method that converts the Hive based value
     * referenced by the <code>constantDesc</code> parameter to the
     * appropriate Java type and places that value in the given
     * <code>RecordValue</code> (representing either a <code>PrimaryKey</code>
     * or an <code>IndexKey</code>) corresponding to the key's given
     * <code>fieldName</code>.
     */
    static void populateKey(final String fieldName,
                            final ExprNodeConstantDesc constantDesc,
                            final RecordValue key) {

        final String typeName = constantDesc.getTypeInfo().getTypeName();
        final Object keyValue = constantDesc.getValue();

        /* Currently supports only the following primitive types . */
        if (serdeConstants.BOOLEAN_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Boolean) keyValue);
        } else if (serdeConstants.INT_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Integer) keyValue);
        } else if (serdeConstants.BIGINT_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Long) keyValue);
        } else if (serdeConstants.FLOAT_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Float) keyValue);
        } else if (serdeConstants.DECIMAL_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Float) keyValue);
        } else if (serdeConstants.DOUBLE_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (Double) keyValue);
        } else if (serdeConstants.STRING_TYPE_NAME.equals(typeName)) {

            if (((key.getDefinition()).getFieldDef(fieldName)).isType(
                                                         FieldDef.Type.ENUM)) {
                key.putEnum(fieldName, (String) keyValue);
            } else {
                key.put(fieldName, (String) keyValue);
            }

        } else if (serdeConstants.BINARY_TYPE_NAME.equals(typeName)) {
            key.put(fieldName, (byte[]) keyValue);
        }
    }

    /**
     * Determines whether the given <code>searchConditions</code> are
     * valid; where the criteria used to make this determination are
     * as follows:
     * <ul>
     *   <li> The column names (fields) referenced in the given
     *        <code>searchConditions</code> must be associated with the
     *        fields of either the table's <code>PrimaryKey</code> or
     *        the <code>IndexKey</code> of one of the table's indexes;
     *        where the names are either 'all primary' or 'all index',
     *        not a mix.
     *   <li> If the column names are 'all index', then they must belong
     *        to the same index.
     *   <li> There must be no 'missing' fields in those column names.
     *        That is, whether associated with a <code>PrimaryKey</code>
     *        or an <code>IndexKey</code>, the column names must correspond
     *        to the first N fields in the key definition; satisfying
     *        the requirements of a searchable key defined by the Table API.
     *   <li> If a range is specified, that range must be a 'single range'
     *        on the least 'significant' field of the <code>PrimaryKey</code>
     *        or <code>IndexKey</code>; again, as specified by the Table API.
     * </ul>
     * Note that the given <code>searchConditions</code> may reference column
     * names where some of the names correspond to fields of the table's
     * <code>PrimaryKey</code> and others do not, but all of the names
     * correspond to the fields of one of the table's indexes. As a result,
     * index validity is always examined first.
     */
    static boolean searchConditionsValid(
                       final List<IndexSearchCondition> searchConditions,
                       final Table table) {

        if (searchConditions == null || table == null) {
            return false;
        }

        final Set<String> searchCols = new HashSet<String>();
        for (IndexSearchCondition cond : searchConditions) {
            searchCols.add(cond.getColumnDesc().getColumn().toLowerCase());
        }

        /*
         * 1. searchCols must be all primary or all index, not a mix.
         * 2. If all index, then must belong to the same index.
         * 3. There must be no 'missing' fields.
         * 4. If a range is specified, it must be a single range on the
         *    least 'significant' field.
         *
         * Examine the index columns before the primary columns.
         */
        final Map<String, List<String>> indexedColumnMap =
                                            getIndexedColumnMapLower(table);

        if (indexedColumnMap != null && indexedColumnMap.size() > 0) {
            for (String indexName : indexedColumnMap.keySet()) {

                final List<String> kvCols = indexedColumnMap.get(indexName);
                if (kvCols.size() >= searchCols.size()) {
                    /* searchCols must contain the 1st N kvCols to be valid. */
                    boolean validIndex = true;
                    for (int i = 0; i < searchCols.size(); i++) {
                        final String curKvCol = kvCols.get(i);
                        if (!searchCols.contains(curKvCol)) {
                            validIndex = false;
                            break;
                        }
                    }
                    if (validIndex) {
                        return validKey(searchConditions, kvCols);
                    }
                }
            }
        }

        final List<String> primaryColumnNames =
                               getPrimaryColumnsLower(table);

        if (primaryColumnNames != null && !primaryColumnNames.isEmpty() &&
            primaryColumnNames.size() >= searchCols.size()) {

            /* searchCols must contain the 1st N keys to be valid. */
            for (int i = 0; i < searchCols.size(); i++) {
                final String curKvCol = primaryColumnNames.get(i);
                if (!searchCols.contains(curKvCol)) {
                    return false;
                }
            }
            return validKey(searchConditions, primaryColumnNames);
        }
        return false;
    }

    /**
     * Determines whether the given <code>searchColumns</code> represent
     * a valid key (<code>PrimaryKey</code> or <code>IndexKey</code>) with
     * (optional) <code>FieldRange</code>; and whether the associated
     * comparison operations are valid. Note that the order of the elements
     * in the given <code>orderedColumnsLower</code> parameter must be in
     * valid key order; as defined by the Table API.
     */
    private static boolean validKey(
                            final List<IndexSearchCondition> searchConditions,
                            final List<String> orderedColumnsLower) {

        if (searchConditions == null || searchConditions.isEmpty() ||
            orderedColumnsLower == null || orderedColumnsLower.isEmpty()) {

            return false;
        }

        final List<IndexSearchCondition> remainingSearchConditions =
            new ArrayList<IndexSearchCondition>(searchConditions);

        /*
         * Depends on the field elements in orderedColumnsLower being
         * in valid key order (as defined by the Table API).
         */
        for (int i = 0; i < orderedColumnsLower.size() &&
             remainingSearchConditions.size() > 0; i++) {

            final String colName = orderedColumnsLower.get(i);
            if (colName == null) {
                /* Gap in the key. */
                return false;
            }

            final ColumnPredicateInfo colInfo =
                getColumnPredicateInfo(colName, remainingSearchConditions);
            final List<String> colOps = colInfo.getColumnOps();
            final int nColOps = colOps.size();

            /*
             * The field MUST be associated with either 1 op ('=') or 2 ops
             * (is in a FieldRange); otherwise searchConditions are invalid.
             */
            if (nColOps != 1 && nColOps != 2) {

                return false;
            }

            /* If field has only 1 op, then MUST be '='; otherwise invalid. */
            if (nColOps == 1 &&
                !colOps.contains(GenericUDFOPEqual.class.getName())) {

                return false;
            }

            /*
             * If the field has 2 ops, then MUST be in a FieldRange; otherwise
             * invalid. If the field is found to be in a FieldRange, then
             * it also must be the LAST field from the ordered set of key
             * fields that is associated with a comparison op. Because the
             * fields are processed in key order, and because as each such
             * field is processed, the associated ops are removed from the
             * remainingSearchConditions, the FieldRange will be valid only
             * if the 2 ops represent a valid inequality pair, and only if
             * there are NO OTHER fields left in remainingSearchConditions
             * to analyze.
             */
            if (nColOps == 2) {

                /* For valid FieldRange, must contain '<' or '<='. */
                if (!colOps.contains(
                        GenericUDFOPLessThan.class.getName()) &&
                    !colOps.contains(
                        GenericUDFOPEqualOrLessThan.class.getName())) {

                    return false;
                }

                /*
                 * From above, contains '<' or '<='. So check for
                 * '>' or '>='.
                 */
                if (!colOps.contains(
                        GenericUDFOPGreaterThan.class.getName()) &&
                    !colOps.contains(
                        GenericUDFOPEqualOrGreaterThan.class.getName())) {

                    return false;
                }

                /*
                 * From above, contains valid FieldRange inequalities. Next
                 * check that there are NO ADDITIONAL fields to process;
                 * otherwise invalid.
                 */
                if (remainingSearchConditions.size() > 0) {

                    return false;
                }

                /*
                 * Field has 2 ops, is in a valid FieldRange, and there are
                 * no additional fields to process (remainingSearchConditions
                 * is empty); thus, the key is valid.
                 *
                 * Exit the loop and return true.
                 */
            }
        }
        return true;
    }

    /**
     * Convenience method that searches the given list of search conditions
     * for the given column (field) name, and returns an instance of the
     * ColumnPredicateInfo class containing the given column name, along
     * with a list of all of the comparison operations corresponding to that
     * column name.
     * <p>
     * Note that this method modifies the contents of the given search
     * conditions by removing each element that references the given
     * column name. This behavior is intended to provide convenient exit
     * criteria when this method is invoked in a loop; where the loop would
     * exit when the search conditions are empty. Thus, if it is important
     * to maintain the original search conditions, prior to invoking this
     * method, callers should clone the original search conditions to
     * avoid information loss.
     */

    private static ColumnPredicateInfo getColumnPredicateInfo(
        final String curColName,
        final List<IndexSearchCondition> curSearchConditions) {

        final List<String> colOps = new ArrayList<String>();
        final List<Integer> removeIndxs = new ArrayList<Integer>();
        int i = 0;
        for (IndexSearchCondition cond : curSearchConditions) {
            final String searchColName =
                cond.getColumnDesc().getColumn().toLowerCase();

            if (curColName.equals(searchColName)) {
                colOps.add(cond.getComparisonOp());
                removeIndxs.add(i);
            }
            i++;
        }

        /* Remove the search conditions corresponding to the curColName. */
        for (int j = removeIndxs.size() - 1; j >= 0; j--) {
            /*
             * NOTE: must convert what is returned by removeIndxs.get()
             *       to int; otherwise curSearchConditions.remove() will
             *       fail. This is because List defines two remove methods;
             *       remove(Integer) and remove(int). So to remove the
             *       desired element of curSearchConditions, the INDEX
             *       (not the Integer object) that element must be input
             *       must be specified.
             */
            curSearchConditions.remove((int) removeIndxs.get(j));
        }

        return new ColumnPredicateInfo(curColName, colOps);
    }

    /**
     * Local class, intended as a convenient return type data structure, that
     * associates the comparison operation(s) specified in a given predicate
     * with a corresponding column (field) name.
     */
    private static final class ColumnPredicateInfo {
        private final String columnNameLower;
        private final List<String> columnOps;

        ColumnPredicateInfo(
            final String columnName, final List<String> columnOps) {
            this.columnNameLower = (columnName == null ? null :
                                    columnName.toLowerCase());
            this.columnOps = columnOps;
        }

        List<String> getColumnOps() {
            return columnOps;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColumnPredicateInfo)) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            final ColumnPredicateInfo obj1 = this;
            final ColumnPredicateInfo obj2 = (ColumnPredicateInfo) obj;

            if (obj1.columnNameLower == null) {
                if (obj2.columnNameLower != null) {
                    return false;
                }
            } else if (!obj1.columnNameLower.equals(obj2.columnNameLower)) {
                return false;
            }

            if (obj1.columnOps == null) {
                if (obj2.columnOps != null) {
                    return false;
                }
            } else if (!obj1.columnOps.equals(obj2.columnOps)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int pm = 37;
            int hc = 11;
            int hcSum = 0;
            if (columnNameLower != null) {
                hcSum = hcSum + columnNameLower.hashCode();
            }
            if (columnOps != null) {
                hcSum = hcSum + columnOps.hashCode();
            }
            hc = (pm * hc) + hcSum;
            return hc;
        }

        @Override
        public String toString() {
            final StringBuilder buf =
                new StringBuilder(this.getClass().getSimpleName());
            buf.append(": [columnNameLower=");
            if (columnNameLower != null) {
                buf.append(columnNameLower);
            } else {
                buf.append("null");
            }

            buf.append(", columnOps=");
            if (columnOps != null) {
                buf.append(columnOps);
            } else {
                buf.append("null");
            }

            buf.append("]");
            return buf.toString();
        }
    }

    static int getQueryBy() {
        return queryBy;
    }

    static String getWhereClause() {
        return whereClause;
    }

    /**
     * Removes unsupported components from a given predicate and constructs
     * the <code>String</code> representation of the new predicate.
     *
     * The given Hive <code>ExprNodeDesc</code> parameter consists of the
     * components of a query's predicate; that is, the components of the
     * query's WHERE clause. This method constructs and returns a new
     * <code>ExprNodeDesc</code> that consists of only the components that
     * the current predicate pushdown mechanism supports; discarding all
     * components that reference an unsupported operator or conjunction.
     * Additionally, this method also populates the given
     * <code>StringBuilder</code> with the <code>String</code> representation
     * of the  Hive <code>ExprNodeDesc</code> object that is constructed
     * and returned.
     * <p>
     * To understand how this method works, consider the following query:
     * <pre>
     * SELECT * FROM vehicleTable WHERE make = 'Ford' AND
     *               (color = 'blue' OR color = 'black') AND model LIKE '%E%';
     * </pre>
     *
     * In the case, the predicate is,
     * <pre>
     * WHERE make = 'Ford' AND (color = 'blue' OR color = 'black') AND
     *                          model LIKE '%E%'
     * </pre>
     *
     * The only operation in the above predicate that is not currently
     * supported, is the 'LIKE' operation. Thus, the component
     * "model LIKE '%E%'" will not be included in the values returned
     * by this method. Specifically, the object that would be input to
     * this method for the given example would be of the form:
     *
     * <pre>
     * GenericUDFOPAnd(
     *   GenericUDFOPAnd(
     *     GenericUDFOPEqual(Column[make], Const string Ford),
     *     GenericUDFOPOr(
     *       GenericUDFOPEqual(Column[color], Const string blue),
     *       GenericUDFOPEqual(Column[color], Const string black))),
     *     GenericUDFBridge(Column[model], Const string %E%))
     * </pre>
     *
     * Note that the <code>GenericUDFBridge</code> object corresponds to the
     * 'LIKE' operator; therefore, it will be removed. Thus, the object
     * output by this method will be of the form:
     *
     * <pre>
     * GenericUDFOPAnd(
     *       GenericUDFOPEqual(Column[make], Const string Ford),
     *       GenericUDFOPOr(
     *         GenericUDFOPEqual(Column[color], Const string blue),
     *         GenericUDFOPEqual(Column[color], Const string black)))
     * </pre>
     * *
     * And the <code>StringBuilder</code> parameter will contain the value:
     *
     * <pre>
     * "(make = 'Ford') AND ((color = 'blue' OR color = 'black'))"
     * </pre>
     *
     * To achieve the results described in the example above, this method
     * 'walks' the 'graph' of 'nodes' making up the input object; examining
     * each such node for supported or unsupported constructs, and discarding
     * those nodes that are not supported.
     * <p>
     * This method is recursive. As the graph is traversed, when an 'AND'
     * conjunction is encountered, the current node is split into the
     * child node to the 'left' of the conjunction and the child node to
     * 'right' of the conjunction. Those two child nodes are then recursively
     * input to this method until a supported comparison operation ('=', '&lt;',
     * '&gt;', etc.) is encountered and the recursion stops.
     * <p>
     * If, on the other hand, an 'OR' conjunction is encountered, then
     * recursion occurs on only the first component of the 'OR' statement;
     * where again, the recursion stops when a supported comparison operation
     * finally encountered.
     * <p>
     * The predicate object returned by this method, as well as the
     * <code>String</code> representation of that predicate, can then be
     * supplied to the Hive infrastructure for "pushing" to the backend
     * ('RecordReader') for server side filtering.
     * <p>
     * Note that the value returned by this method can be <code>null</code>.
     * A <code>null</code> return value means that there is no predicate
     * that can be pushed to the backend for server side filtering; that is,
     * all filtering will be performed on the client side, using the residual.
     * <p>
     * Note also that if a caller of this method wishes to use the
     * <code>String</code> form of the computed predicate placed in the
     * given <code>StringBuilder</code>, then the caller must supply a
     * a non-<code>null</code>, empty <code>StringBuilder</code> instance.
     */
    static ExprNodeDesc buildPushPredicate(
        final ExprNodeDesc input, StringBuilder pushPredicateBuf) {

        LOG.trace("ENTERED TableHiveInputFormat.buildPushPredicate");
        LOG2.trace("ENTERED TableHiveInputFormat.buildPushPredicate");

        ExprNodeDesc retExpr = null;

        if (pushPredicateBuf == null) {
            pushPredicateBuf = new StringBuilder();
        }

        if (input instanceof ExprNodeGenericFuncDesc) {

            LOG.trace("instance of ExprNodeGenericFuncDesc");
            LOG2.trace("instance of ExprNodeGenericFuncDesc");

            final ExprNodeGenericFuncDesc inputFunc =
                (ExprNodeGenericFuncDesc) input;

            LOG.trace("inputFunc = " + inputFunc);
            LOG2.trace("inputFunc = " + inputFunc);

            if (inputFunc.getGenericUDF() instanceof GenericUDFOPAnd) {

                /* AND statement: recurse on 1st component. */
                final List<ExprNodeDesc> children = inputFunc.getChildren();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("GenericUDF = AND");
                    int i = 0;
                    for (ExprNodeDesc child : children) {
                        LOG.trace("child[" + i++ + "]  = " + child);
                    }
                }

                if (LOG2.isTraceEnabled()) {
                    LOG2.trace("GenericUDF = AND");
                    int i = 0;
                    for (ExprNodeDesc child : children) {
                        LOG2.trace("child[" + i++ + "]  = " + child);
                    }
                }

                final ExprNodeDesc firstChild = children.get(0);

                final StringBuilder firstChildBuf = new StringBuilder();
                final ExprNodeDesc exprFirst =
                    buildPushPredicate(firstChild, firstChildBuf);
                final List<ExprNodeDesc> exprList =
                    new ArrayList<ExprNodeDesc>();

                boolean needOpenParens = true;
                if (firstChildBuf.length() > 0) {
                    pushPredicateBuf.append("(");
                    needOpenParens = false;
                    pushPredicateBuf.append(firstChildBuf);
                    exprList.add(exprFirst);
                }

                for (int i = 1; i < children.size(); i++) {
                    final ExprNodeDesc nextChild = children.get(i);
                    final StringBuilder nextChildBuf = new StringBuilder();
                    final ExprNodeDesc exprNext =
                        buildPushPredicate(nextChild, nextChildBuf);

                    if (nextChildBuf.length() > 0) {
                        if (needOpenParens) {
                            pushPredicateBuf.append("(");
                            needOpenParens = false;
                        } else {
                            pushPredicateBuf.append(" AND ");
                        }
                        pushPredicateBuf.append(nextChildBuf);
                        exprList.add(exprNext);
                    }
                }

                if (!needOpenParens) {
                    pushPredicateBuf.append(")");
                }

                /*
                 * In this part of the if-block, the input expression is an
                 * AND statement; where we know that AND statements have
                 * only 2 children.
                 *
                 * Prior to recursing into this method (while looping over
                 * the children of the original expression), if one of the
                 * children of the original expression is rejected in one
                 * of the recursed calls, that child will not be included
                 * in the predicate-to-push that is produced by this
                 * if-block. That rejected child will instead be included
                 * in the residual. For that case, the exprList from which
                 * the retExpr will be constructed below will contain
                 * only 1 element.
                 *
                 * When the resulting exprList contains only 1 element,
                 * then simply return that single element.
                 *
                 * If exprList contains 2 elements, then wrap those elements in
                 * an AND expression (via the TypeInfoFactory.booleanTypeInfo
                 * and inputFunc.getGenericUDF() parameters), so that the
                 * retExpr will have 2 children (as all AND statements should).
                 *
                 * Finally, if exprList is empty, then this means that both
                 * children in the original AND statement were rejected during
                 * each recursive call. For this case, null is returned,
                 * so that the residual will be the whole expression that
                 * was originally input to this method for processing.
                 */
                if (!exprList.isEmpty()) {

                    if (exprList.size() == 1) {
                        retExpr = exprList.get(0);
                    } else {
                        retExpr = new ExprNodeGenericFuncDesc(
                                          TypeInfoFactory.booleanTypeInfo,
                                          inputFunc.getGenericUDF(),
                                          exprList);
                    }
                }

            } else if (inputFunc.getGenericUDF() instanceof GenericUDFOPOr) {

                /* OR statement: recurse on 1st component. */
                final List<ExprNodeDesc> children = inputFunc.getChildren();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("GenericUDF = OR");
                    int i = 0;
                    for (ExprNodeDesc child : children) {

                        LOG.trace("child[" + i++ + "]  = " + child);
                    }
                }

                if (LOG2.isTraceEnabled()) {
                    LOG2.trace("GenericUDF = OR");
                    int i = 0;
                    for (ExprNodeDesc child : children) {

                        LOG2.trace("child[" + i++ + "]  = " + child);
                    }
                }

                final ExprNodeDesc firstChild = children.get(0);

                final StringBuilder firstChildBuf = new StringBuilder();
                final ExprNodeDesc exprFirst =
                    buildPushPredicate(firstChild, firstChildBuf);
                final List<ExprNodeDesc> exprList =
                    new ArrayList<ExprNodeDesc>();

                boolean needOpenParens = true;
                if (firstChildBuf.length() > 0) {
                    pushPredicateBuf.append("(");
                    needOpenParens = false;
                    pushPredicateBuf.append(firstChildBuf);
                    exprList.add(exprFirst);
                }

                for (int i = 1; i < children.size(); i++) {
                    final ExprNodeDesc nextChild = children.get(i);
                    final StringBuilder nextChildBuf = new StringBuilder();
                    final ExprNodeDesc exprNext =
                        buildPushPredicate(nextChild, nextChildBuf);

                    if (nextChildBuf.length() > 0) {
                        if (needOpenParens) {
                            pushPredicateBuf.append("(");
                            needOpenParens = false;
                        } else {
                            pushPredicateBuf.append(" OR ");
                        }
                        pushPredicateBuf.append(nextChildBuf);
                        exprList.add(exprNext);
                    }
                }

                if (!needOpenParens) {
                    pushPredicateBuf.append(")");
                }

                if (!exprList.isEmpty()) {
                    retExpr = new ExprNodeGenericFuncDesc(
                                      TypeInfoFactory.booleanTypeInfo,
                                      inputFunc.getGenericUDF(),
                                      exprList);
                }

            } else if (COMPARE_OPS.keySet().contains(
                          (inputFunc.getGenericUDF()).getClass().getName())) {
                /*
                 * Valid OP. Must be of the form 'column OP value' or the
                 * the form 'val OP col'
                 */
                final ExprNodeDesc inputLeft = inputFunc.getChildren().get(0);
                final ExprNodeDesc inputRight = inputFunc.getChildren().get(1);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("valid compare OP (not OR/AND): " +
                       (inputFunc.getGenericUDF()).getClass().getSimpleName());
                    LOG.trace("inputLeft  = " + inputLeft);
                    LOG.trace("inputRight = " + inputRight);
                }

                if (LOG2.isTraceEnabled()) {
                    LOG2.trace("valid compare OP (not OR/AND): " +
                       (inputFunc.getGenericUDF()).getClass().getSimpleName());
                    LOG2.trace("inputLeft  = " + inputLeft);
                    LOG2.trace("inputRight = " + inputRight);
                }

                if ((inputLeft instanceof ExprNodeColumnDesc) &&
                    (inputRight instanceof ExprNodeConstantDesc)) {

                    /* It's of the form 'column OP value' (ex. 'count > 3') */
                    pushPredicateBuf.append(
                        ((ExprNodeColumnDesc) inputLeft).getColumn());
                    pushPredicateBuf.append(" ");
                    pushPredicateBuf.append(
                        COMPARE_OPS.get(
                            (inputFunc.getGenericUDF()).getClass().getName()));
                    pushPredicateBuf.append(" ");
                    pushPredicateBuf.append(((ExprNodeConstantDesc) inputRight)
                           .getExprString());
                    retExpr = input;

                } else if ((inputLeft instanceof ExprNodeConstantDesc) &&
                           (inputRight instanceof ExprNodeColumnDesc)) {

                    /* It's of the form 'value OP column' (ex. '3 < count') */
                    pushPredicateBuf.append(((ExprNodeConstantDesc) inputLeft)
                           .getExprString());
                    pushPredicateBuf.append(" ");
                    pushPredicateBuf.append(
                        COMPARE_OPS.get(
                            (inputFunc.getGenericUDF()).getClass().getName()));
                    pushPredicateBuf.append(" ");
                    pushPredicateBuf.append(
                        ((ExprNodeColumnDesc) inputRight).getColumn());
                    retExpr = input;

                } else {

                    /* It's a valid OP, but unexpected form. So do nothing. */
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(
                            "valid OP, but not of the required form " +
                            "'col OP val' or 'val OP col' [ " +
                            inputLeft.getExprString() +
                            " " +
                            COMPARE_OPS.get(
                            (inputFunc.getGenericUDF()).getClass().getName()) +
                            " " +
                            inputRight.getExprString() +
                            "]");
                    }

                    if (LOG2.isTraceEnabled()) {
                        LOG2.trace(
                            "valid OP, but not of the required form " +
                            "'col OP val' or 'val OP col' [ " +
                            inputLeft.getExprString() +
                            " " +
                            COMPARE_OPS.get(
                            (inputFunc.getGenericUDF()).getClass().getName()) +
                            " " +
                            inputRight.getExprString() +
                            "]");
                    }
                }

            } else {
                LOG.trace("invalid conjunction & OP [ " +
                          (inputFunc.getGenericUDF()).getClass().getName() +
                          "]: exclude from WHERE clause");
                LOG2.trace("invalid conjunction & OP [ " +
                           (inputFunc.getGenericUDF()).getClass().getName() +
                           "]: exclude from WHERE clause");
            }
        } else {
            LOG.trace("not instance of ExprNodeGenericFuncDesc: " +
                      "exclude from WHERE clause");
            LOG2.trace("not instance of ExprNodeGenericFuncDesc: " +
                       "exclude from WHERE clause");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("hive push predicate:  " + retExpr);
            LOG.trace("push predicate string:  " +
                      pushPredicateBuf.toString());
            LOG.trace("EXITED TableHiveInputFormat.buildPushPredicate");
        }

        if (LOG2.isTraceEnabled()) {
            LOG2.trace("hive push predicate:  " + retExpr);
            LOG2.trace("push predicate string:  " +
                      pushPredicateBuf.toString());
            LOG2.trace("EXITED TableHiveInputFormat.buildPushPredicate");
        }
        return retExpr;
    }

    /**
     * Factory method that constructs an instance of the extended Hive
     * IndexPredicateAnalyzer class.
     */
    static ExtendedPredicateAnalyzer createPredicateAnalyzerForOnql(
                                         final TableSerDe deserializer) {
        if (deserializer == null) {
            return null;
        }

        final Table table = deserializer.getKvTable();
        if (table == null) {
            return null;
        }

        final List<String> hiveColumnNames =
            deserializer.getSerdeParams().getColumnNames();

        try {
            return ExtendedPredicateAnalyzer.createAnalyzer(hiveColumnNames);
        } catch (SemanticException e) {
            LOG.warn(
                "failure creating ExtendedPredicateAnalyzer [" + e + "]", e);
            LOG2.warn(
                "failure creating ExtendedPredicateAnalyzer [" + e + "]", e);
        }
        return null;
    }

    /**
     * Sub-class of the Hive IndexPredicateAnalyzer class that provides
     * additional functionality to support pushing predicates that may
     * contain conjunctions other than AND, a Hive IN list, etc.
     */
    static final class ExtendedPredicateAnalyzer
                           extends IndexPredicateAnalyzer {

        private final Set<String> udfNames;
        private final Map<String, Set<String>> columnToUDFs;
        private FieldValidator fieldValidator;
        private boolean acceptsFields;

        private static final Map<Class<? extends GenericUDF>, String>
            VALID_CONJUNCTION_MAP =
                new HashMap<Class<? extends GenericUDF>, String>();
        static {
            VALID_CONJUNCTION_MAP.put(GenericUDFOPAnd.class, "and");
            VALID_CONJUNCTION_MAP.put(GenericUDFOPOr.class, "or");
        }

        private final Map<Class<? extends GenericUDF>, GenericUDF>
            conjunctionMap =
                new HashMap<Class<? extends GenericUDF>, GenericUDF>();

        /*
         * Private constructor to prevent direct instantiation and require
         * the use of a factory method.
         */
        private ExtendedPredicateAnalyzer(final List<String> colNames)
            throws SemanticException {

            super();

            udfNames = new HashSet<String>();
            columnToUDFs = new HashMap<String, Set<String>>();

            for (String compareOp : COMPARE_OPS.keySet()) {
                this.addComparisonOp(compareOp);
            }

            for (String colName : colNames) {
                this.allowColumnName(colName);
            }

            /* FunctionRegistry.getFunctionInfo throws SemanticException. */
            for (Map.Entry<Class<? extends GenericUDF>, String> entry :
                     VALID_CONJUNCTION_MAP.entrySet()) {
                final Class<? extends GenericUDF> conjClass = entry.getKey();
                final String conjStr = entry.getValue();
                conjunctionMap.put(
                    conjClass,
                    FunctionRegistry.getFunctionInfo(conjStr).getGenericUDF());
            }
        }

        public static ExtendedPredicateAnalyzer createAnalyzer(
            final List<String> colNames) throws SemanticException {
            return new ExtendedPredicateAnalyzer(colNames);
        }

        /**
         * Overloaded version of the <code>analyzePredicate</code> method
         * of the <code>IndexPredicateAnalyzer</code> parent class. Like the
         * version of analyzePredicate specified by the parent class, this
         * version of the method also takes the predicate of the original query
         * and computes and returns an instance of <code>ExprNodeDesc</code>
         * representing the <em>residual</em> of the input predicate. In
         * addition to computing and returning the residual for client-side
         * filtering, this method also populates an <code>ArrayDeque</code>
         * in which the first element inserted corresponds to the full
         * predicate to push; that is, the compliment of the residual that
         * will be returned.
         */
        public ExprNodeDesc analyzePredicate(
            final ExprNodeDesc predicate,
            final ArrayDeque<ExprNodeDesc> pushPredicateDeque) {

            /* Convert any IN statements to an OR statement. */
            final ExprNodeDesc predToProcess = replaceInClauses(predicate);

            final Map<Rule, NodeProcessor> opRules =
                new LinkedHashMap<Rule, NodeProcessor>();

            /* BEGIN NodeProcessor class implementation. */
            final NodeProcessor nodeProcessor = new NodeProcessor() {

                @Override
                public Object process(Node nd,
                                      Stack<Node> stack,
                                      NodeProcessorCtx procCtx,
                                      Object... nodeOutputs)
                    throws SemanticException {

                    /*
                     * Only push predicates consisting of valid conjunctions;
                     * for example, combinations of AND and OR. Reject all
                     * other conjunctions; like CASE, etc.
                     */
                    for (Node ancestor : stack) {

                        if (nd == ancestor) {
                            break;
                        }

                        if (!isValidConjunction((ExprNodeDesc) ancestor)) {
                            return nd;
                        }
                    }
                    return analyzeExpr(
                               (ExprNodeGenericFuncDesc) nd,
                                pushPredicateDeque,
                                nodeOutputs);
                }
            }; /* END NodeProcessor class implementation. */

            /* Compute the predicate to process from the input predicate. */
            final Dispatcher disp =
                new DefaultRuleDispatcher(nodeProcessor, opRules, null);
            final GraphWalker ogw = new DefaultGraphWalker(disp);

            final ArrayList<Node> topNodes = new ArrayList<Node>();
            topNodes.add(predToProcess);

            /* Map from predicate-to-process to the residual predicate. */
            final HashMap<Node, Object> nodeOutput =
                new HashMap<Node, Object>();

            /* Walk the top-level nodes and populate the nodeOutput Map. */
            try {
                ogw.startWalking(topNodes, nodeOutput);

            } catch (SemanticException ex) {
                LOG.warn("failure on predicate analysis [" + ex + "]", ex);
                LOG2.warn("failure on predicate analysis [" + ex + "]", ex);
                pushPredicateDeque.clear();
                return null;
            }

            /*
             * Return the residual predicate corresponding the
             * predicate-to-process computed above.
             */
            final ExprNodeDesc residualPredicate =
                (ExprNodeDesc) nodeOutput.get(predToProcess);

            if (LOG.isDebugEnabled()) {
                LOG.debug("queryBy = " + queryBy);
                LOG.debug("whereClause = " + whereClause);

                LOG.debug(
                    "analyzePredicate: input predicate    = " + predicate);
                LOG.debug(
                    "analyzePredicate: computed predicate = " + predToProcess);
                LOG.debug(
                    "analyzePredicate: residual predicate = " +
                    residualPredicate);
            }

            if (LOG2.isDebugEnabled()) {
                LOG2.debug("queryBy = " + queryBy);
                LOG2.debug("whereClause = " + whereClause);

                LOG2.debug(
                    "analyzePredicate: input predicate    = " + predicate);
                LOG2.debug(
                    "analyzePredicate: computed predicate = " + predToProcess);
                LOG2.debug(
                    "analyzePredicate: residual predicate = " +
                    residualPredicate);
            }
            return residualPredicate;
        }

        /**
         * Overloaded version of translateSearchConditions. Like the version
         * of <code>translateSearchConditions</code> specified by the parent
         * class, which uses a given list of "search conditions" to construct
         * the predicate to push in a form Hive understands (an instance of
         * <code>ExprNodeGenericFuncDesc</code>), this version of the method
         * constructs that predicate to push from the queue of predicates
         * produced by a prior call to the <code>analyzePredicate</code>
         * method of this class. This method also populates the given
         * buffer with the String version of the constructed expression.
         */
        public ExprNodeGenericFuncDesc translateSearchConditions (
                   final ArrayDeque<ExprNodeDesc> predicateQueue,
                   final StringBuilder predicateBuf) {

            if (predicateQueue == null || predicateQueue.isEmpty()) {
                return null;
            }

            StringBuilder whereClauseBuf = predicateBuf;
            if (whereClauseBuf == null) {
                whereClauseBuf = new StringBuilder();
            }

            /*
             * Currently, the element at the 'top' of the queue (the last
             * element added to the queue by the analyzePredicate method)
             * is the object from which to construct the predicate to push.
             */
            return (ExprNodeGenericFuncDesc) buildPushPredicate(
                       predicateQueue.getFirst(), whereClauseBuf);
        }

        @Override
        public void setFieldValidator(FieldValidator fieldValidator) {
            super.setFieldValidator(fieldValidator);
            this.fieldValidator = fieldValidator;
        }

        @Override
        public void setAcceptsFields(boolean acceptsFields) {
            super.setAcceptsFields(acceptsFields);
            this.acceptsFields = acceptsFields;
        }

        @Override
        public void clearAllowedColumnNames() {
            super.clearAllowedColumnNames();
            columnToUDFs.clear();
        }

        @Override
        public void allowColumnName(String columnName) {
            super.allowColumnName(columnName);
            columnToUDFs.put(columnName, udfNames);
        }

        @Override
        public void addComparisonOp(String udfName) {
            super.addComparisonOp(udfName);
            udfNames.add(udfName);
        }

        @Override
        public void addComparisonOp(String columnName, String... udfs) {

            super.addComparisonOp(columnName, udfs);

            final Set<String> allowed = columnToUDFs.get(columnName);
            if (allowed == null || allowed == udfNames) {
                columnToUDFs.put(columnName,
                                 new HashSet<String>(Arrays.asList(udfs)));
            } else {
                allowed.addAll(Arrays.asList(udfs));
            }
        }

        private ExprNodeDesc analyzeExpr(
                             ExprNodeGenericFuncDesc expr,
                             final ArrayDeque<ExprNodeDesc> pushPredicateDeque,
                             Object... nodeOutputs) {

            if (isValidConjunction(expr)) {

                pushPredicateDeque.push(expr);

                /* The residualList contains possible residuals in expr. */
                final List<ExprNodeDesc> residualList =
                    new ArrayList<ExprNodeDesc>();

                for (Object nodeOutput : nodeOutputs) {
                    residualList.add((ExprNodeDesc) nodeOutput);
                }

                /* Remove any null elements from the possible residuals. */
                final List<ExprNodeDesc> residuals =
                    new ArrayList<ExprNodeDesc>();

                for (ExprNodeDesc residual : residualList) {
                    if (residual != null) {
                        residuals.add(residual);
                    }
                }

                /* When no possible residuals, return null. */
                if (residuals.isEmpty()) {
                    return null;
                }

                /* When only 1 residual, return that single element. */
                if (residuals.size() == 1) {
                    return residuals.get(0);
                }

                /* When multiple residuals, return each in a conjunction. */
                return new ExprNodeGenericFuncDesc(
                               TypeInfoFactory.booleanTypeInfo,
                               getGenericUDFForConjunction(expr),
                               residuals);
            }

            GenericUDF genericUDF = expr.getGenericUDF();

            if (!(genericUDF instanceof GenericUDFBaseCompare)) {
                return expr;
            }

            ExprNodeDesc expr1 = (ExprNodeDesc) nodeOutputs[0];
            ExprNodeDesc expr2 = (ExprNodeDesc) nodeOutputs[1];

            if (expr1.getTypeInfo().equals(expr2.getTypeInfo())) {
                expr1 = getColumnExpr(expr1);
                expr2 = getColumnExpr(expr2);
            }

            final ExprNodeDesc[] extracted =
                ExprNodeDescUtils.extractComparePair(expr1, expr2);

            if (extracted == null || (extracted.length > 2 &&
                !acceptsFields)) {
                return expr;
            }

            ExprNodeColumnDesc columnDesc;
            /* ExprNodeConstantDesc constantDesc; (for ref: not used yet) */

            if (extracted[0] instanceof ExprNodeConstantDesc) {
                genericUDF = genericUDF.flip();
                columnDesc = (ExprNodeColumnDesc) extracted[1];
                /* constantDesc = (ExprNodeConstantDesc) extracted[0]; */
            } else {
                columnDesc = (ExprNodeColumnDesc) extracted[0];
                /* constantDesc = (ExprNodeConstantDesc) extracted[1]; */
            }

            final Set<String> allowed =
                columnToUDFs.get(columnDesc.getColumn());

            if (allowed == null) {
                return expr;
            }

            if (!allowed.contains(genericUDF.getUdfName())) {
                return expr;
            }

            String[] fields = null;
            if (extracted.length > 2) {
                final ExprNodeFieldDesc fieldDesc =
                    (ExprNodeFieldDesc) extracted[2];
                if (!isValidField(fieldDesc)) {
                    return expr;
                }
                fields = ExprNodeDescUtils.extractFields(fieldDesc);
            }

            /*
             * Comment from Hive code: "We also need to update the expr so
             * that the index query can be generated. Note that, hive does
             * not support UDFToDouble etc in the query text."
             */
            final List<ExprNodeDesc> list = new ArrayList<ExprNodeDesc>();
            list.add(expr1);
            list.add(expr2);

            expr = new ExprNodeGenericFuncDesc(
                expr.getTypeInfo(), expr.getGenericUDF(), list);

            /*
             * The expression was converted to a search condition, so remove
             * it from the residual predicate.
             *
             * If null is returned (when fields == null), then there is no
             * residual predicate. When there is no residual predicate, and
             * the input expr is a single valid comparison operation
             * (ex. color = 'red', make < 'Chrysler', etc.), then add the
             * expression to the pushPredicateDeque so that buildPushPredicate
             * will add it to the predicate to push.
             *
             * Note that genericUDF was retrieved from expr earlier in this
             * method.
             */
            if (fields == null) {
                if (genericUDF instanceof GenericUDFBaseCompare) {
                    pushPredicateDeque.push(expr);
                }
                return null;
            }
            return expr;
        }

        private ExprNodeDesc getColumnExpr(ExprNodeDesc expr) {

            if (expr instanceof ExprNodeColumnDesc) {
                return expr;
            }
            ExprNodeGenericFuncDesc funcDesc = null;
            if (expr instanceof ExprNodeGenericFuncDesc) {
                funcDesc = (ExprNodeGenericFuncDesc) expr;
            }
            if (null == funcDesc) {
                return expr;
            }

            final GenericUDF udf = funcDesc.getGenericUDF();

            /* Check if its a simple cast expression. */
            if ((udf instanceof GenericUDFBridge ||
                 udf instanceof GenericUDFToBinary ||
                 udf instanceof GenericUDFToChar ||
                 udf instanceof GenericUDFToVarchar ||
                 udf instanceof GenericUDFToDecimal ||
                 udf instanceof GenericUDFToDate ||
                 udf instanceof GenericUDFToUnixTimeStamp ||
                 udf instanceof GenericUDFToUtcTimestamp) &&
                 funcDesc.getChildren().size() == 1 &&
                 funcDesc.getChildren().get(0) instanceof ExprNodeColumnDesc) {
                return expr.getChildren().get(0);
            }
            return expr;
        }

        private static Class<?> getUDFClassFromExprDesc(ExprNodeDesc desc) {
            if (!(desc instanceof ExprNodeGenericFuncDesc)) {
                return null;
            }
            final ExprNodeGenericFuncDesc genericFuncDesc =
                (ExprNodeGenericFuncDesc) desc;
            final GenericUDF genericUDF = genericFuncDesc.getGenericUDF();
            if (genericUDF instanceof GenericUDFBridge) {
                return ((GenericUDFBridge) genericUDF).getUdfClass();
            }
            return genericUDF.getClass();
        }

        private boolean isValidField(ExprNodeFieldDesc field) {
            return fieldValidator == null || fieldValidator.validate(field);
        }


        private boolean isValidConjunction(ExprNodeDesc desc) {
            return conjunctionMap.containsKey(
                       getUDFClassFromExprDesc(desc));
        }

        private GenericUDF getGenericUDFForConjunction(ExprNodeDesc desc) {
            return conjunctionMap.get(getUDFClassFromExprDesc(desc));
        }

        private boolean isOpIn(ExprNodeDesc desc) {
            return GenericUDFIn.class == getUDFClassFromExprDesc(desc);
        }

        private boolean containsInClause(ExprNodeDesc expr) {
            if (expr == null) {
                return false;
            }
            if (GenericUDFIn.class == getUDFClassFromExprDesc(expr)) {
                return true;
            }

            final List<ExprNodeDesc> exprChildren = expr.getChildren();

            if (exprChildren == null) {
                return false;
            }

            for (ExprNodeDesc curChild : exprChildren) {
                if (containsInClause(curChild)) {
                    return true;
                }
            }
            return false;
        }

        private ExprNodeDesc inClauseToOrClause(ExprNodeDesc expr) {
            if (expr == null) {
                return expr;
            }

            if (GenericUDFIn.class != getUDFClassFromExprDesc(expr)) {
                return expr;
            }

            final List<ExprNodeDesc> children = expr.getChildren();
            if (children == null || children.size() < 2) {
                return expr;
            }

            /*
             * Walk through the elements (children) of the IN <list>
             * expression; where the first child of the expression is the
             * name of the column, and the remaining children are the
             * possible values that column may have. Using that column
             * name, construct an EQUALS expresions for each value in
             * the list.
             *
             * For example, if the expression represents 't IN (a, b, c)',
             * then the column name is 't' and the expressions that are
             * created and placed in the aggreateChildList would be:
             * '(t = a)', '(t = b)', '(t = c)'.
             */
            final List<ExprNodeDesc> aggregateChildList =
                new ArrayList<ExprNodeDesc>();
            final ExprNodeDesc inListColumn = children.get(0);

            for (int i = 1; i < children.size(); i++) {
                final ExprNodeDesc inListValue = children.get(i);
                final List<ExprNodeDesc> curChildList =
                    new ArrayList<ExprNodeDesc>();
                curChildList.add(inListColumn);
                curChildList.add(inListValue);
                final ExprNodeDesc curExpr =
                    new ExprNodeGenericFuncDesc(
                            TypeInfoFactory.booleanTypeInfo,
                            new GenericUDFOPEqual(),
                            curChildList);
                aggregateChildList.add(curExpr);
            }

            /*
             * From the aggregateChildList constructed above, generate the
             * single expression to return; which should consist of a set
             * of "embedded" OR statements. For example, using the scenario
             * presented above, the expression to return would be:
             * OR( (t = a), OR( (t = b), (t = c) ) )
             */
            ExprNodeDesc retExpr =
                aggregateChildList.get(aggregateChildList.size() - 1);
            for (int i = aggregateChildList.size() - 2; i >= 0; i--) {
                final List<ExprNodeDesc> pairWiseExprList =
                    new ArrayList<ExprNodeDesc>();
                pairWiseExprList.add(aggregateChildList.get(i));
                pairWiseExprList.add(retExpr);
                retExpr = new ExprNodeGenericFuncDesc(
                           TypeInfoFactory.booleanTypeInfo,
                           conjunctionMap.get(GenericUDFOPOr.class),
                           pairWiseExprList);
            }
            return retExpr;
        }

        private ExprNodeDesc replaceInClauses(ExprNodeDesc expr) {

            /* If expr does not contain at least 1 IN clause, then NO-OP. */
            if (!containsInClause(expr)) {
                return expr;
            }

            /* If expr contains exactly 1 IN clause. Replace and return. */
            if (isOpIn(expr)) {
                return inClauseToOrClause(expr);
            }

            /*
             * Contains at least 1 IN clause within parent. But if the
             * parent is not a valid conjunction, then NO-OP.
             */
            if (!isValidConjunction(expr)) {
                return expr;
            }

            /*
             * Contains at least 1 IN clause within valid conjunction. Walk
             * the tree, replacing each IN clause and aggregating as new
             * OR clauses.
             */
            final GenericUDF exprParent = getGenericUDFForConjunction(expr);

            final List<ExprNodeDesc> children = expr.getChildren();

            final List<ExprNodeDesc> aggregateChildList =
                                         new ArrayList<ExprNodeDesc>();

            for (ExprNodeDesc curChild : children) {
                aggregateChildList.add(replaceInClauses(curChild));
            }
            return new ExprNodeGenericFuncDesc(
                                  TypeInfoFactory.booleanTypeInfo,
                                  exprParent,
                                  aggregateChildList);
        }

        /**
         * Utility for debugging the various methods of this class.
         */
        public static void displayNodeTree(final ExprNodeDesc nodeTree) {

            final List<ExprNodeDesc> childList = nodeTree.getChildren();
            if (childList == null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("\n  nodeTree " + nodeTree);
                    LOG.trace("  nodeTree has no children");
                }

                if (LOG2.isTraceEnabled()) {
                    LOG2.trace("\n  nodeTree " + nodeTree);
                    LOG2.trace("  nodeTree has no children");
                }
                return;
            }

            LOG.trace("\n  childList (size=" + childList.size() +
                      ") = " + childList);
            LOG2.trace("\n  childList (size=" + childList.size() +
                       ") = " + childList);

            if (childList.size() > 0) {

                for (int i = 0; i < childList.size(); i++) {

                    final ExprNodeDesc child = childList.get(i);
                    LOG.trace("\n    child[" + i + "] = " + child);
                    LOG2.trace("\n    child[" + i + "] = " + child);

                    final List<ExprNodeDesc> grandChildList =
                                                 child.getChildren();
                    if (grandChildList != null && grandChildList.size() > 0) {

                        LOG.trace("    grandChildList[" + i +
                                  "] (size=" + grandChildList.size() +
                                  ")  = " + grandChildList);
                        LOG2.trace("    grandChildList[" + i +
                                  "] (size=" + grandChildList.size() +
                                  ")  = " + grandChildList);

                        for (int j = 0; j < grandChildList.size(); j++) {

                            final ExprNodeDesc grandChild =
                                                   grandChildList.get(j);
                            LOG.trace("      grandChild[" + i + "][" +
                                      j + "] = " + grandChild);
                            LOG2.trace("      grandChild[" + i + "][" +
                                      j + "] = " + grandChild);

                            final List<ExprNodeDesc> greatGrandChildList =
                                                     grandChild.getChildren();
                            if (greatGrandChildList != null &&
                                greatGrandChildList.size() > 0) {

                                LOG.trace(
                                    "      greatGrandChildList[" + i + "][" +
                                    j + "] (size=" +
                                    greatGrandChildList.size() +
                                    ")  = " + greatGrandChildList);
                                LOG2.trace(
                                    "      greatGrandChildList[" + i + "][" +
                                    j + "] (size=" +
                                    greatGrandChildList.size() +
                                    ")  = " + greatGrandChildList);

                                for (int k = 0; k < greatGrandChildList.size();
                                     k++) {

                                    final ExprNodeDesc greatGrandChild =
                                                   greatGrandChildList.get(k);
                                    LOG.trace(
                                        "        greatGrandChild[" + i + "][" +
                                        j + "][" + k + "] = " +
                                        greatGrandChild);
                                    LOG2.trace(
                                        "        greatGrandChild[" + i + "][" +
                                        j + "][" + k + "] = " +
                                        greatGrandChild);

                                    final List<ExprNodeDesc>
                                        greatGreatGrandChildList =
                                            greatGrandChild.getChildren();
                                    if (greatGreatGrandChildList != null &&
                                        greatGreatGrandChildList.size() > 0) {

                                        LOG.trace(
                                        "        greatGreatGrandChildList[" +
                                        i + "][" + j + "][" + k + "] (size=" +
                                        greatGreatGrandChildList.size() +
                                        ") = " + greatGreatGrandChildList);
                                        LOG2.trace(
                                        "        greatGreatGrandChildList[" +
                                        i + "][" + j + "][" + k + "] (size=" +
                                        greatGreatGrandChildList.size() +
                                        ") = " + greatGreatGrandChildList);
                                    }
                                }
                            } else {
                                LOG.trace(
                                    "      grandChild[" + i + "][" + j + "] " +
                                    "HAS NO CHILDREN ==> NO GREAT " +
                                    "GRANDCHILDREN");
                                LOG2.trace(
                                    "      grandChild[" + i + "][" + j + "] " +
                                    "HAS NO CHILDREN ==> NO GREAT " +
                                    "GRANDCHILDREN");
                            }
                        }
                    } else {
                        LOG.trace("    child[" + i + "] HAS NO " +
                                  "CHILDREN ==> NO GRANDCHILDREN");
                        LOG2.trace("    child[" + i + "] HAS NO " +
                                   "CHILDREN ==> NO GRANDCHILDREN");
                    }
                }
            } else {
                LOG.trace("  PREDICATE HAS NO CHILDREN");
                LOG2.trace("  PREDICATE HAS NO CHILDREN");
            }
        }
    }/* END class ExtendedPredicateAnalyzer */
}
