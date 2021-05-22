/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je.rep.stream;

import com.sleepycat.je.utilint.StatDefinition;
import com.sleepycat.je.utilint.StatDefinition.StatType;

/**
 * Per-stat Metadata for HA Feeder Transaction statistics.
 */
public class FeederTxnStatDefinition {

    public static final String GROUP_NAME = "FeederTxns";
    public static final String GROUP_DESC = "FeederTxns statistics";

    public static final String TXNS_ACKED_NAME =
        "txnsAcked";
    public static final String TXNS_ACKED_DESC =
        "Number of Transaction ack'd.";
    public static final StatDefinition TXNS_ACKED =
        new StatDefinition(
            TXNS_ACKED_NAME,
            TXNS_ACKED_DESC);

    public static final String TXNS_NOT_ACKED_NAME =
        "txnsNotAcked";
    public static final String TXNS_NOT_ACKED_DESC =
        "Number of Transactions not Ack'd.";
    public static final StatDefinition TXNS_NOT_ACKED =
        new StatDefinition(
            TXNS_NOT_ACKED_NAME,
            TXNS_NOT_ACKED_DESC);

    public static final String LAST_COMMIT_VLSN_NAME =
        "lastCommitVLSN";
    public static final String LAST_COMMIT_VLSN_DESC =
        "The VLSN of the last committed transaction on the master, or 0 if " +
            "not known or this node is not the master.";
    public static final StatDefinition LAST_COMMIT_VLSN =
        new StatDefinition(
            LAST_COMMIT_VLSN_NAME,
            LAST_COMMIT_VLSN_DESC,
            StatType.CUMULATIVE);

    public static final String LAST_COMMIT_TIMESTAMP_NAME =
        "lastCommitTimestamp";
    public static final String LAST_COMMIT_TIMESTAMP_DESC =
        "The commit timestamp of the last committed transaction on the " +
            "master, or 0 if not known or this node is not the master.";
    public static final StatDefinition LAST_COMMIT_TIMESTAMP =
        new StatDefinition(
            LAST_COMMIT_TIMESTAMP_NAME,
            LAST_COMMIT_TIMESTAMP_DESC,
            StatType.CUMULATIVE);

    public static final String VLSN_RATE_NAME =
        "vlsnRate";
    public static final String VLSN_RATE_DESC =
        "A moving average of the rate replication data is being generated by " +
            "the master, in VLSNs per minute, or 0 if not known or this node " +
            "is not the master.";
    public static final StatDefinition VLSN_RATE =
        new StatDefinition(
            VLSN_RATE_NAME,
            VLSN_RATE_DESC);

    public static final String ACK_TXN_AVG_NS_NAME = "ackTxnAvgNs";
    public static final String ACK_TXN_AVG_NS_DESC =
        "The average write txn time (from txn start to HA commit) " +
        "in nanoseconds at the master; " +
        "the txn time includes the time it took to obtain an ack quorum. " ;
    public static final StatDefinition ACK_TXN_AVG_NS =
        new StatDefinition(ACK_TXN_AVG_NS_NAME, ACK_TXN_AVG_NS_DESC);

    public static final String LOCAL_TXN_AVG_NS_NAME = "localTxnAvgNs";
    public static final String LOCAL_TXN_AVG_NS_DESC =
        "The average write txn time (from txn start to local commit) " +
        "in nanoseconds at the master.";
    public static final StatDefinition LOCAL_TXN_AVG_NS =
        new StatDefinition(LOCAL_TXN_AVG_NS_NAME, LOCAL_TXN_AVG_NS_DESC);
}
