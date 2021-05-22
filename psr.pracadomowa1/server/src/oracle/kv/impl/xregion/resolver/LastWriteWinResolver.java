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

package oracle.kv.impl.xregion.resolver;

import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.rep.table.TableManager;

/**
 * Object that solves the conflict by the rule last-write-win.
 */
public class LastWriteWinResolver implements ConflictResolver {

    /** parent table manager */
    private final TableManager tableMan;

    public LastWriteWinResolver(TableManager tableMan) {
        if (tableMan == null) {
            throw new IllegalArgumentException("Null table manager");
        }
        this.tableMan = tableMan;
    }

    @Override
    public KeyMetadata resolve(KeyMetadata r1, KeyMetadata r2) {
        return resolve(tableMan.getTableMetadata(), r1, r2);
    }

    /**
     * Resolves the conflict from key metadata
     * @param tm  table metadata
     * @param r1  row 1
     * @param r2  row 2
     * @return the winning key metadata
     */
    static KeyMetadata resolve(TableMetadata tm, KeyMetadata r1,
                               KeyMetadata r2) {
        if (r1.getTimestamp() > r2.getTimestamp()) {
            return r1;
        }
        if (r1.getTimestamp() < r2.getTimestamp()) {
            return r2;
        }
        return breakTie(tm, r1, r2);
    }

    /**
     * Breaks the tie between writes r1 and r2
     *
     * @param tm  table metadata
     * @param r1  row 1
     * @param r2  row 2
     *
     * @return the winner of the two conflicting write rows
     */
    private static KeyMetadata breakTie(TableMetadata tm,
                                        KeyMetadata r1,
                                        KeyMetadata r2) {
        final String rname1 = tm.getRegionName(r1.getRegionId());
        if (rname1 == null) {
            throw new IllegalArgumentException("Invalid first region id=" +
                                               r1.getRegionId());
        }
        final String rname2 = tm.getRegionName(r2.getRegionId());
        if (rname2 == null) {
            throw new IllegalArgumentException("Invalid second region id=" +
                                               r2.getRegionId());
        }
        if (rname1.compareToIgnoreCase(rname2) >= 0) {
            return r1;
        }

        return r2;
    }
}

