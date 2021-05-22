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

package oracle.kv.impl.api.table;

import java.util.Set;

import oracle.kv.table.TimeToLive;


/**
 * A TableChange that evolves an existing table.
 */
class EvolveTable extends TableChange {
    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final String namespace;
    private final FieldMap fields;
    private final TimeToLive ttl;
    private final String description;
    private final IdentityColumnInfo identityColumnInfo;
    private final Set<Integer> regions;

    EvolveTable(TableImpl table, int seqNum) {
        super(seqNum);
        tableName = table.getFullName();
        namespace = table.getInternalNamespace();
        fields = table.getFieldMap();
        ttl = table.getDefaultTTL();
        description = table.getDescription();
        identityColumnInfo = table.getIdentityColumnInfo();
        regions = table.getRemoteRegions();
    }

    @Override
    TableImpl apply(TableMetadata md) {
        return md.evolveTable(namespace, tableName, fields, ttl, description,
                              identityColumnInfo, regions);
    }
}
