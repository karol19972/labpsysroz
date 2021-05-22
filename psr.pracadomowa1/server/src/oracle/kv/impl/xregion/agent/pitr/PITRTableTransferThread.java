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

package oracle.kv.impl.xregion.agent.pitr;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.xregion.agent.BaseTableTransferThread;
import oracle.kv.impl.xregion.agent.RegionAgentThread;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.stat.TableInitStat;
import oracle.kv.table.Row;
import oracle.kv.table.TableUtils;

/**
 * Table transfer thread of region agent for Point-In-Time-Recovery (PITR)
 * TODO:
 */
public class PITRTableTransferThread extends BaseTableTransferThread {

    /* transferred rows */
    private final List<Row> rows;

    public PITRTableTransferThread(RegionAgentThread parent,
                                   String tableName,
                                   RegionInfo srcRegion,
                                   TableAPIImpl srcAPI,
                                   Logger logger) {
        super("PITRTableTrans" + tableName,
              parent, tableName, srcRegion, srcAPI, logger);
        rows = new ArrayList<>();
    }

    @Override
    protected void pushRow(Row srcRow) {
        /* size of source row *///TODO: inefficient, need serialize the row
        final long sz = TableUtils.getDataSize(srcRow) +
                        TableUtils.getDataSize(srcRow);
        final TableInitStat ts = getMetrics().getTableMetrics(tableName)
            .getRegionInitStat(srcRegion.getName());
        ts.incrTransferred(1);
        ts.incrTransBytes(sz);
        rows.add(srcRow);
    }

    @Override
    protected String dumpStat() {
        return null;
    }
}
