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

package oracle.kv.impl.api.ops;

import static com.sleepycat.je.Put.NO_OVERWRITE;
import static oracle.kv.impl.api.ops.OperationHandler.CURSOR_DEFAULT;

import oracle.kv.FaultException;
import oracle.kv.ReturnValueVersion.Choice;
import oracle.kv.UnauthorizedException;
import oracle.kv.Value;
import oracle.kv.Value.Format;
import oracle.kv.Version;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.rep.migration.MigrationStreamHandle;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.util.TxnUtil;
import oracle.kv.impl.xregion.agent.TargetTableEvolveException;
import oracle.kv.impl.xregion.resolver.ConflictResolver;
import oracle.kv.impl.xregion.resolver.PrimaryKeyMetadata;
import oracle.kv.table.Table;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationResult;
import com.sleepycat.je.Put;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.WriteOptions;
import com.sleepycat.util.PackedInteger;

/**
 * Server handler for {@link PutResolve}.
 *
 * Throughput calculation
 * +------------------------------------------------------------------------------+
 * |    Op         | Choice | # |          Read        | * |     Write            |
 * |---------------+--------+---+----------------------+--------------------------|
 * |               |        | P |    old record size   | L | remote record size + |
 * |               |        |   |                      |   | local record size    |
 * |               |        |   |                      |--------------------------|
 * |               |        |   |                      | W |         0            |
 * |               |  NONE  |---+----------------------+--------------------------|
 * |               |        | A |           0          |    remote record size    |
 * |               +--------+---+----------------------+--------------------------|
 * |               |        | P |                      | L | remote record size + |
 * |               |        |   |    old record size   |   | local record size    |
 * |               |        |   |                      |--------------------------|
 * |               |        |   |                      | W |         0            |
 * | Put           | VERSION|---+----------------------+--------------------------|
 * |               |        | A |           0          |   remote record size     |
 * |               +--------+---+----------------------+--------------------------|
 * |               |        | P |                      | L | remote record size + |
 * |               |        |   |    old record size   |   | local record size    |
 * |               |        |   |                      |--------------------------|
 * |               |        |   |                      | W |         0            |
 * |               |  VALUE |---+----------------------+--------------------------|
 * |               |        | A |           0          |   remote record size     |
 * +------------------------------------------------------------------------------+
 *      # = Target record is present (P) or absent (A) locally
 *      * = Local record wins (W) or losts (L) the conflict resolution.
 */
class PutResolveHandler extends BasicPutHandler<PutResolve> {

    PutResolveHandler(OperationHandler handler) {
        super(handler, OpCode.PUT_RESOLVE, PutResolve.class);
    }

    @Override
    Result execute(PutResolve op,
                   Transaction txn,
                   PartitionId partitionId)
        throws UnauthorizedException {
        verifyDataAccess(op);

        byte[] keyBytes = op.getKeyBytes();
        byte[] valueBytes = op.getValueBytes();
        PutHandler.checkTombstoneLength(op.isTombstone(), valueBytes.length);

        assert (keyBytes != null) && (valueBytes != null);

        final DatabaseEntry keyEntry = new DatabaseEntry(keyBytes);
        final DatabaseEntry dataEntry = valueDatabaseEntry(valueBytes);

        final WriteOptions writeOptions = getWriteOptions(op);
        writeOptions.setModificationTime(op.getTimestamp());

        final Table table = getAndCheckTable(op.getTableId());
        if (table == null) {
            throw new FaultException(
                "Key/value request is not expected", true);
        }

        final int serverVersion = table.getTableVersion();
        final int clientVersion = op.getTableVer();
        /* check if table has evolved for put */
        if (clientVersion != 0 && serverVersion > clientVersion) {
            /* table has evolved on the server */
            final String err = "table=" + table.getFullNamespaceName() +
                               " evolved to version=" + serverVersion +
                               ", client version=" + clientVersion;
            throw new FaultException(
                err, new TargetTableEvolveException(), true);
        }

        final Database db = getRepNode().getPartitionDB(partitionId);

        final Cursor cursor = db.openCursor(txn, CURSOR_DEFAULT);
        try {
            return put(cursor, keyEntry, dataEntry, writeOptions, op);
        } finally {
            TxnUtil.close(cursor);
        }
    }

    private WriteOptions getWriteOptions(PutResolve op) {
        WriteOptions writeOptions;
        if (op.isTombstone()) {
            writeOptions =
                makeOption(getRepNode().getRepNodeParams().getTombstoneTTL(),
                           true);
            writeOptions.setTombstone(true);
        } else {
            writeOptions = makeExpirationTimeOption(op.getExpirationTimeMs(),
                                                    op.getUpdateTTL());
        }
        return writeOptions;
    }

    private Result.PutResult put(Cursor cursor,
                                 DatabaseEntry keyEntry,
                                 DatabaseEntry dataEntry,
                                 WriteOptions writeOptions,
                                 PutResolve op) {
        ResultValueVersion prevVal = null;
        Version version = null;
        long expTime = 0L;
        long modificationTime = 0L;
        int storageSize = -1;
        boolean wasUpdate = false;

        while (true) {
            OperationResult opres = putEntry(cursor, keyEntry, dataEntry,
                             NO_OVERWRITE, writeOptions);
            if (opres != null) {
                version = getVersion(cursor);
                expTime = opres.getExpirationTime();
                modificationTime = opres.getModificationTime();
                storageSize = getStorageSize(cursor);

                op.addReadBytes(MIN_READ);
                op.addWriteBytes(storageSize, getNIndexWrites(cursor));
            } else {
                final Choice choice = op.getReturnValueVersionChoice();
                final DatabaseEntry localDataEntry = new DatabaseEntry();
                opres = cursor.get(keyEntry, localDataEntry,
                    Get.SEARCH, LockMode.RMW.toReadOptions());
                if (opres == null) {
                    /* Another thread deleted the record. Continue. */
                    continue;
                }

                /* set prevVal if needed*/
                prevVal = getBeforeUpdateInfo(choice, cursor,
                                              operationHandler,
                                              localDataEntry,
                                              opres);

                op.addReadBytes(getStorageSize(cursor));

                byte[] localValue = localDataEntry.getData();
                PrimaryKeyMetadata localKeyMeta =
                    getMRMeta(opres.getModificationTime(), localValue);
                PrimaryKeyMetadata remoteKeyMeta =
                    new PrimaryKeyMetadata(op.getTimestamp(), op.getRegionId());

                /* call resolver in table manager to resolve the conflict */
                final ConflictResolver resolver =
                    getRepNode().getTableManager().getResolver();
                final PrimaryKeyMetadata winner = (PrimaryKeyMetadata)
                    resolver.resolve(localKeyMeta, remoteKeyMeta);

                if (winner.equals(remoteKeyMeta)) {
                    /* The remote row wins, overwrite the row*/
                    /* Charge for deletion of old value. */
                    op.addWriteBytes(getStorageSize(cursor), 0);

                    opres = putEntry(cursor, null, dataEntry, Put.CURRENT,
                                     writeOptions);

                    version = getVersion(cursor);
                    expTime = opres.getExpirationTime();
                    wasUpdate = true;
                    modificationTime = opres.getModificationTime();
                    storageSize = getStorageSize(cursor);

                    op.addWriteBytes(storageSize, getNIndexWrites(cursor));
                }
                reserializeResultValue(op, prevVal);

            }

            if (version != null) {
                MigrationStreamHandle.get().addPut(keyEntry,
                                                   dataEntry,
                                                   version.getVLSN(),
                                                   expTime,
                                                   op.isTombstone());
            }
            return new Result.PutResult(getOpCode(),
                                        op.getReadKB(),
                                        op.getWriteKB(),
                                        prevVal,
                                        version,
                                        expTime,
                                        wasUpdate,
                                        modificationTime,
                                        storageSize,
                                        getRepNode().getRepNodeId().
                                        getGroupId());
        }
    }

    private PrimaryKeyMetadata getMRMeta(long updateTime, byte[] valueBytes) {
        Value.Format format = Value.Format.fromFirstByte(valueBytes[0]);
        if (format != Format.MULTI_REGION_TABLE) {
            throw new IllegalArgumentException("This is not a record of " +
                "multiregion tables.");
        }
        int regionId = PackedInteger.readInt(valueBytes, 1);
        return new PrimaryKeyMetadata(updateTime, regionId);
    }
}
