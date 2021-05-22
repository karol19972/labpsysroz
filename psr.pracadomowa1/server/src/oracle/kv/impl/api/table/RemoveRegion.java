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
 * Remove (drop) region.
 */
public class RemoveRegion  extends TableChange {
    private static final long serialVersionUID = 1L;

    private final String regionName;

    RemoveRegion(String regionName, int seqNum) {
        super(seqNum);
        this.regionName = regionName;
    }

    @Override
    TableImpl apply(TableMetadata md) {
        md.removeRegion(regionName);
        return null;
    }

    @Override
    public String toString() {
        return "RemoveRegion[" + regionName + "]";
    }
}
