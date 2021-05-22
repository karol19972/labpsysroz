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

package oracle.kv.impl.xregion.init;

import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_AGENT_ID;
import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_SOURCE_REGION;
import static oracle.kv.impl.systables.MRTableInitCkptDesc.COL_NAME_TABLE_NAME;
import static oracle.kv.impl.xregion.stat.TableInitStat.TableInitState.NOT_START;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.Durability;
import oracle.kv.FaultException;
import oracle.kv.MetadataNotFoundException;
import oracle.kv.Version;
import oracle.kv.impl.systables.MRTableInitCkptDesc;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.impl.xregion.stat.TableInitStat;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.ReadOptions;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.WriteOptions;
import oracle.nosql.common.JsonUtils;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Object represents table initialization checkpoint
 */
public class TableInitCheckpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 1;
    /**
     * Read options
     */
    private static final ReadOptions CKPT_TABLE_READ_OPT =
        new ReadOptions(Consistency.ABSOLUTE, 0, null);
    /**
     * Write options. It ensures persistence on master and acknowledged by
     * majority of replicas, strong enough to tolerate master failure.
     * <p>
     * It is worthwhile to ensure the persistence on master of checkpoint
     * table at slightly more overhead compared with using
     * {@link oracle.kv.Durability.SyncPolicy#NO_SYNC} or
     * {@link oracle.kv.Durability.SyncPolicy#WRITE_NO_SYNC} because in the
     * case of table copy resumption, it is usually more expensive to re-copy
     * a big set of rows from the remote region and persist them locally. The
     * default checkpoint interval is defined in
     * {@link oracle.kv.impl.xregion.service.JsonConfig#DEFAULT_ROWS_REPORT_PROGRESS_INTV}.
     * Unless {@link oracle.kv.Durability.SyncPolicy#SYNC} causes significant
     * performance issue, it is better to have stronger durability.
     */
    public static final WriteOptions CKPT_TABLE_WRITE_OPT =
        new WriteOptions(Durability.COMMIT_SYNC, 0, null);

    /**
     * sleep time in ms before retry on table not found exception
     */
    private static final int MNFE_SLEEP_MS = 5 * 1000;
    /**
     * sleep time in ms before retry on fault exception
     */
    private static final int FE_SLEEP_MS = 100;
    /**
     * Version
     */
    private volatile int version;
    /**
     * Agent id
     */
    private volatile String agentId;
    /**
     * Source region name
     */
    private volatile String sourceRegion;
    /**
     * Target region name
     */
    private volatile String targetRegion;
    /**
     * Timestamp of checkpoint
     */
    private volatile long timestamp;
    /**
     * Table name
     */
    private volatile String table;
    /**
     * Last persisted primary key in JSON format, null if no checkpoint has
     * been made. It is encrypted for any secure store.
     */
    private volatile String primaryKey;
    /**
     * Initialization state
     */
    private volatile TableInitStat.TableInitState state;
    /**
     * Optional message with the state, e.g., the reason of error if the init
     * state is {@link TableInitStat.TableInitState#ERROR}
     */
    private volatile String message = null;

    /**
     * No args constructor for use in serialization,
     * used when constructing instance from JSON.
     */
    TableInitCheckpoint() {
    }

    public TableInitCheckpoint(@NonNull String agentId,
                               @NonNull String sourceRegion,
                               @NonNull String targetRegion,
                               @NonNull String table,
                               @Nullable String primaryKey,
                               long timestamp,
                               TableInitStat.TableInitState state) {
        this(CURRENT_VERSION, agentId, sourceRegion, targetRegion, table,
             primaryKey, timestamp, state);
    }

    private TableInitCheckpoint(int version,
                                String agentId,
                                String sourceRegion,
                                String targetRegion,
                                String table,
                                String primaryKey,
                                long timestamp,
                                TableInitStat.TableInitState state) {
        this.version = version;
        this.agentId = agentId;
        this.sourceRegion = sourceRegion;
        this.targetRegion = targetRegion;
        this.table = table;
        this.primaryKey = primaryKey;
        this.timestamp = timestamp;
        this.state = state;
        if (NOT_START.equals(state) && primaryKey != null) {
            throw new IllegalArgumentException("Non-null primary key with " +
                                               "state=" + NOT_START);
        }
    }

    public int getVersion() {
        return version;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getTargetRegion() {
        return targetRegion;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTable() {
        return table;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public TableInitStat.TableInitState getState() {
        return state;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TableInitCheckpoint)) {
            return false;
        }
        final TableInitCheckpoint other = (TableInitCheckpoint) obj;
        return version == other.version &&
               agentId.equals(other.agentId) &&
               targetRegion.equals(other.targetRegion) &&
               sourceRegion.equals(other.sourceRegion) &&
               table.equals(other.table) &&
               Objects.equals(primaryKey, other.primaryKey) &&
               timestamp == other.timestamp &&
               state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(version) +
               agentId.hashCode() +
               targetRegion.hashCode() +
               sourceRegion.hashCode() +
               table.hashCode() +
               Objects.hashCode(primaryKey) +
               Long.hashCode(timestamp) +
               state.hashCode();
    }

    @Override
    public String toString() {
        return JsonUtils.prettyPrint(this);
    }

    /**
     * Reads a checkpoint from table
     *
     * @param tableAPI     table api
     * @param agentId      agent id
     * @param sourceRegion source region name
     * @param tableName    table name
     * @param logger       logger
     * @return an optional checkpoint
     */
    public static Optional<TableInitCheckpoint> read(TableAPI tableAPI,
                                                     String agentId,
                                                     String sourceRegion,
                                                     String tableName,
                                                     Logger logger) {

        final Table table = ServiceMDMan.getTableRetry(
            tableAPI, MRTableInitCkptDesc.TABLE_NAME, 2, logger);
        if (table == null) {
            return Optional.empty();
        }

        final PrimaryKey pkey = table.createPrimaryKey();
        pkey.put(COL_NAME_AGENT_ID, agentId);
        pkey.put(COL_NAME_SOURCE_REGION, sourceRegion);
        pkey.put(COL_NAME_TABLE_NAME, tableName);
        final Row row = readRetry(tableAPI, pkey, logger);
        if (row == null) {
            return Optional.empty();
        }
        final String json =
            row.get(MRTableInitCkptDesc.COL_NAME_CHECKPOINT).asString().get();
        try {
            final TableInitCheckpoint val = JsonUtils.readValue(
                json, TableInitCheckpoint.class);
            return Optional.of(val);
        } catch (IOException ioe) {
            final String err = "Cannot convert json to checkpoint" + ", " +
                               ioe.getMessage() + ", json=" + json;
            throw new IllegalStateException(err, ioe);
        }
    }

    /**
     * Writes checkpoint to system table
     *
     * @param tableAPI table api
     * @param ckpt     checkpoint to write
     * @param logger   logger
     * @return an optional version
     */
    public static Optional<Version> write(TableAPI tableAPI,
                                          TableInitCheckpoint ckpt,
                                          Logger logger) {
        final Table table = ServiceMDMan.getTableRetry(
            tableAPI, MRTableInitCkptDesc.TABLE_NAME, 2, logger);
        if (table == null) {
            return Optional.empty();
        }

        final Row row = table.createRow();
        row.put(COL_NAME_AGENT_ID, ckpt.getAgentId());
        row.put(COL_NAME_SOURCE_REGION,
                ckpt.getSourceRegion());
        row.put(COL_NAME_TABLE_NAME, ckpt.getTable());
        row.put(MRTableInitCkptDesc.COL_NAME_CHECKPOINT,
                JsonUtils.prettyPrint(ckpt));
        final Version ver = putRetry(tableAPI, row, logger);
        if (ver == null) {
            return Optional.empty();
        }
        return Optional.of(ver);
    }

    /**
     * Deletes checkpoint from system table
     *
     * @param tableAPI table api
     * @param agentId  agent id
     * @param region   source region
     * @param table    table name
     * @param logger   logger
     */
    public static void del(TableAPI tableAPI,
                           String agentId,
                           String region,
                           String table,
                           Logger logger) {
        final Table sysTable = ServiceMDMan.getTableRetry(
            tableAPI, MRTableInitCkptDesc.TABLE_NAME, 2, logger);
        if (sysTable == null) {
            return;
        }

        final PrimaryKey pkey = sysTable.createPrimaryKey();
        pkey.put(COL_NAME_AGENT_ID, agentId);
        pkey.put(COL_NAME_SOURCE_REGION, region);
        pkey.put(COL_NAME_TABLE_NAME, table);
        deleteRetry(tableAPI, pkey, logger);
    }

    public void setPrimaryKey(String val) {
        primaryKey = val;
    }

    public void setMessage(String msg) {
        message = msg;
    }

    public String getMessage() {
        return message;
    }

    private static String lm(String msg) {
        return "[TableInitCkpt] " + msg;
    }

    /**
     * Reads from the system checkpoint table, retry if table is not found or
     * it is unable to read because of {@link FaultException}
     */
    private static Row readRetry(TableAPI tableAPI,
                                 PrimaryKey pkey,
                                 Logger logger) {
        while (true) {
            try {
                return tableAPI.get(pkey, CKPT_TABLE_READ_OPT);
            } catch (FaultException fe) {
                onFaultExp(logger, "Retry read,  " + fe.getMessage());
            } catch (MetadataNotFoundException mnfe) {
                final String msg = "Cannot read table=" +
                                   pkey.getTable().getFullNamespaceName() +
                                   ", " + mnfe.getMessage();
                logger.info(lm(msg));
                onMNFE();
            }
        }
    }

    /**
     * Writes the system checkpoint table, retry if table is not found, or it
     * is unable to write because of {@link FaultException}
     */
    private static Version putRetry(TableAPI tableAPI,
                                    Row row,
                                    Logger logger) {
        while (true) {
            try {
                return tableAPI.put(row, null, CKPT_TABLE_WRITE_OPT);
            } catch (FaultException fe) {
                onFaultExp(logger, "Retry put,  " + fe.getMessage());
            } catch (MetadataNotFoundException mnfe) {
                final String msg = "Cannot write table=" +
                                   row.getTable().getFullNamespaceName() +
                                   ", " + mnfe.getMessage();
                logger.info(lm(msg));
                onMNFE();
            }
        }
    }

    /**
     * Reads from the system checkpoint table, retry if table is not found,
     * or it is unable to delete because of {@link FaultException}
     */
    private static void deleteRetry(TableAPI tableAPI,
                                    PrimaryKey pkey,
                                    Logger logger) {
        while (true) {
            try {
                /* not return del result, it may not be accurate in retry */
                tableAPI.delete(pkey, null, CKPT_TABLE_WRITE_OPT);
                break;
            } catch (FaultException fe) {
                onFaultExp(logger, "Retry delete,  " + fe.getMessage());
            } catch (MetadataNotFoundException mnfe) {
                final String msg = "Cannot delete row in table=" +
                                   pkey.getTable().getFullNamespaceName() +
                                   ", " + mnfe.getMessage();
                logger.info(lm(msg));
                onMNFE();
            }
        }
    }

    /**
     * Wait if table not found. During partial upgrade, the table may not be
     * found when operating on the table, and it will retry until the table
     * is found and operation is successful.
     */
    private static void onMNFE() {
        try {
            Thread.sleep(MNFE_SLEEP_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted in retry on MNFE");
        }
    }

    /**
     * Wait on fault exception
     */
    private static void onFaultExp(Logger logger, String msg) {
        try {
            logger.fine(msg);
            Thread.sleep(FE_SLEEP_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted in retry on FE");
        }
    }
}
