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

/**
 * Add (create) region. Used for both remote regions (add) or the local
 * region (set or reset).
 */
class AddRegion extends TableChange {
    private static final long serialVersionUID = 1L;

    private final Region region;

    AddRegion(Region region, int seqNum) {
        super(seqNum);
        this.region = region;
    }

    @Override
    TableImpl apply(TableMetadata md) {
        md.addRegion(region);
        return null;
    }

    @Override
    public String toString() {
        return "AddRegion[" + region + "]";
    }
}
