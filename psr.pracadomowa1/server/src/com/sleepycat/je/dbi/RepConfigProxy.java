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

import com.sleepycat.je.ReplicaConsistencyPolicy;

/**
 * Used to pass a replication configuration instance through the non-HA code.
 */
public interface RepConfigProxy {
    public ReplicaConsistencyPolicy getConsistencyPolicy();
}