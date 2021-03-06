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

package com.sleepycat.je.dbi;

import com.sleepycat.je.utilint.StatDefinition;

/**
 * Per-stat Metadata for JE transaction statistics.
 */
public class TxnStatDefinition {

    public static final StatDefinition TXN_ACTIVE =
        new StatDefinition("nActive", 
                           "Number of transactions that are currently " + 
                           "active.");

    public static final StatDefinition TXN_BEGINS =
        new StatDefinition("nBegins", 
                           "Number of transactions that have begun.");

    public static final StatDefinition TXN_ABORTS =
        new StatDefinition("nAborts",
                           "Number of transactions that have aborted.");

    public static final StatDefinition TXN_COMMITS =
        new StatDefinition("nCommits",
                           "Number of transactions that have committed.");

    public static final StatDefinition TXN_ACTIVE_TXNS =
        new StatDefinition("activeTxns", 
                           "Array of active transactions. Each element of " +
                           "the array is an object of type " +
                           "Transaction.Active.");
}
