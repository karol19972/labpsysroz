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

package oracle.kv.impl.xregion.agent.mrt;

import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.FaultException;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.util.RateLimitingLogger;
import oracle.kv.impl.xregion.agent.BaseTableTransferThread;
import oracle.kv.impl.xregion.agent.RegionAgentThread;
import oracle.kv.impl.xregion.service.MRTableMetrics;
import oracle.kv.impl.xregion.service.RegionInfo;
import oracle.kv.impl.xregion.stat.TableInitStat;
import oracle.kv.pubsub.StreamOperation;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableUtils;
import oracle.kv.table.WriteOptions;

import com.sleepycat.je.dbi.TTL;
import com.sleepycat.je.utilint.LoggerUtils;

/**
 * Object represents the table transfer of region agent for multi-region table.
 */
public class MRTTableTransferThread extends BaseTableTransferThread {

    /* target region */
    private final RegionInfo tgtRegion;

    /* target Table API */
    private final TableAPIImpl tgtAPI;

    /* subscriber associated with the MRT, null in unit test */
    private final MRTSubscriber subscriber;

    /* write options to local store */
    private final WriteOptions wo;

    /* Rate limiting rl */
    private final RateLimitingLogger<String> rlLogger;

    /**
     * Constructs an instance of table transfer thread
     *
     * @param parent     parent region agent
     * @param subscriber subscriber of the mrt
     * @param tableName  name of table to transfer
     * @param srcRegion  source region to transfer table from
     * @param tgtRegion  target region to write table to
     * @param srcAPI     source region table api
     * @param tgtAPI     target region table api
     * @param logger     private logger
     */
    public MRTTableTransferThread(RegionAgentThread parent,
                                  MRTSubscriber subscriber,
                                  String tableName,
                                  RegionInfo srcRegion,
                                  RegionInfo tgtRegion,
                                  TableAPIImpl srcAPI,
                                  TableAPIImpl tgtAPI,
                                  Logger logger)
        throws MRTableNotFoundException {

        super("MRTTrans-from-" + srcRegion.getName() + "-to-" +
              tgtRegion.getName() + "-" + tableName,
              parent, tableName, srcRegion, srcAPI, logger);

        this.subscriber = subscriber;
        this.tgtRegion = tgtRegion;
        this.tgtAPI = tgtAPI;
        rlLogger = subscriber != null ? subscriber.getRlLogger() :
            /* no subscriber, unit test only */
            new RateLimitingLogger<>(10 * 1000, 32, logger);
        final Table tgtTable = tgtAPI.getTable(tableName);
        if (tgtTable == null) {
            final String err = "Table=" + tableName + " not found at region=" +
                               tgtRegion.getName();
            logger.warning(lm(err));
            throw new MRTableNotFoundException(tableName, tgtRegion.getName());
        }
        wo = (subscriber == null) ? null/* unit test only */ :
            subscriber.getWriteOptions();
    }

    @Override
    protected void pushRow(Row srcRow) {

        /* put or delete with resolve */
        int attempts = 0;
        Row tgtRow;
        while (true) {
            try {
                attempts++;
                tgtRow = transform(srcRow);
                if (tgtRow == null) {
                    return;
                }
                final boolean succ = tgtAPI.putResolve(tgtRow, null, wo);
                /* stats update */
                final MRTableMetrics tm =
                    getMetrics().getTableMetrics(tableName);
                final String src = srcRegion.getName();
                final TableInitStat st = tm.getRegionInitStat(src);
                st.incrTransferred(1);
                if (TTL.isExpired(srcRow.getExpirationTime())) {
                    st.incrExpired(1);
                }
                /* size of source row */
                st.incrTransBytes(getRowSize(srcRow));
                if (succ) {
                    st.incrPersisted(1);
                    st.incrPersistBytes(getRowSize(tgtRow));
                }
                logger.finest(() -> lm("Transfer row from table=" + tableName +
                                       ", persisted?=" + succ));
                break;
            } catch (MetadataNotFoundException mnfe) {
                final TableImpl target =
                    (TableImpl) parent.getMdMan().getMRT(tableName);
                logger.warning(lm("Target table=" +
                                  target.getFullNamespaceName() +
                                  " (id=" + target.getId() + ")" +
                                  " not found, error: " + mnfe.getMessage() +
                                  (logger.isLoggable(Level.FINE) ?
                                  LoggerUtils.getStackTrace(mnfe) : "")));
                /* refresh the target table and retry */
                parent.getMdMan().refreshMRT(tableName);
            } catch (FaultException fe) {
                final String tbName = srcRow.getTable().getFullNamespaceName();
                final TableImpl target =
                    (TableImpl) parent.getMdMan().getMRT(tbName);
                final String msg =
                    "Cannot push row after attempts=" + attempts +
                    ", will retry, " +
                    "target table=" + target.getFullNamespaceName() +
                    " (id=" + target.getId() + ")" +
                    ", source=" + srcRegion.getName() +
                    ", target=" + tgtRegion.getName() +
                    ", error: " + fe;
                rlLogger.log(target.getFullNamespaceName() +
                             fe.getFaultClassName(),
                             Level.WARNING, lm(msg));
            }
        }
    }

    @Override
    protected String dumpStat() {
        final MRTAgentMetrics m = (MRTAgentMetrics) getMetrics();
        return "transfer stat for table " + tableName +
               "\nsource region: " + srcRegion.getName() +
               "\ntarget region: " + tgtRegion.getName() +
               "\n" + m.getTableMetrics(tableName);
    }

    /**
     * Returns the size of row in bytes
     */
    private static long getRowSize(Row row) {
        //TODO: inefficient, need serialize the row
        return TableUtils.getDataSize(row) + TableUtils.getDataSize(row);
    }

    /**
     * Transforms the source table row to target table row, skip if in unit
     * test without parent
     */
    private Row transform(Row srcRow) {
        final Row tgtRow;
        if (subscriber == null) {
            /* unit test only */
            tgtRow = srcRow;
            /* set a new region id > 1, unit test only  */
            ((RowImpl) tgtRow).setRegionId(2);
            ((RowImpl) tgtRow).setExpirationTime(srcRow.getExpirationTime());
        } else {
            tgtRow = subscriber.transformRow(srcRow, StreamOperation.Type.PUT,
                                             false);
        }
        return tgtRow;
    }

    /**
     * Thrown if the table is not found at the given region. Because a
     * multi-region table at a region can be dropped at any time, the exception
     * should be handled and consumed by the region agent or the polling
     * thread, and should not surface to bring down the agent.
     */
    public static class MRTableNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String table;
        private final String region;

        MRTableNotFoundException(String table, String region) {
            this.table = table;
            this.region = region;
        }

        public String getTable() {
            return table;
        }

        public String getRegion() {
            return region;
        }
    }
}
