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

package oracle.kv.impl.api.query;

import java.util.Set;

import oracle.kv.StatementResult;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.async.AsyncIterationHandleImpl;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.table.RecordValue;

public interface InternalStatement {

    StatementResult executeSync(KVStoreImpl store,
                                ExecuteOptions options);

    StatementResult executeSyncShards(
        KVStoreImpl store,
        ExecuteOptions options,
        Set<RepGroupId> shards);

    /**
     * Execute the statement, returning results asynchronously through the
     * execution handle.
     *
     * @param store the store
     * @param options options that override the defaults
     * @param shards A set of shards where the query will be executed at
     * @return a handle for controlling the statement execution
     */
    AsyncIterationHandleImpl<RecordValue> executeAsync(
        KVStoreImpl store,
        ExecuteOptions options,
        Set<RepGroupId> shards);
}
