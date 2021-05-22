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

package oracle.kv.impl.systables;

import oracle.kv.impl.api.table.TableBuilder;

/**
 * Descriptor for the stream service response table. This class defines
 * response specific fields in addition to the base fields defined by
 * StreamServiceTableDesc.
 *
 *  Field       Type    Description
 *  -----       ----    -----------
 *  responseType Integer Response type, service specific.
 */
public class StreamResponseDesc extends StreamServiceTableDesc {

    public static final String TABLE_NAME =
            makeSystemTableName("StreamResponse");

    /* Fields specific to this table */

    /* Response type. The types are specific to the service type. */
    public static final String COL_RESPONSE_TYPE = "responseType";

    /* Schema version of the table */
    private static final int TABLE_VERSION = 1;

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    protected int getCurrentSchemaVersion() {
        return TABLE_VERSION;
    }

    @Override
    protected void buildTable(TableBuilder builder) {
        super.buildTable(builder);
        builder.addInteger(COL_RESPONSE_TYPE);
    }
}