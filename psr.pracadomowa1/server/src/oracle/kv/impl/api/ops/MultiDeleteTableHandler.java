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

import static com.sleepycat.je.Put.CURRENT;
import static oracle.kv.impl.api.ops.OperationHandler.CURSOR_DEFAULT;

import java.util.Collections;
import java.util.List;

import oracle.kv.Direction;
import oracle.kv.Value;
import oracle.kv.Version;
import oracle.kv.impl.api.ops.InternalOperation.OpCode;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.rep.migration.MigrationStreamHandle;
import oracle.kv.impl.security.KVStorePrivilege;
import oracle.kv.impl.security.NamespacePrivilege;
import oracle.kv.impl.security.TablePrivilege;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.table.TimeToLive;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationResult;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.WriteOptions;

/**
 * Server handler for {@link MultiDeleteTable}.
 *
 * Throughput calculation
 * +---------------------------------------------------------------------------+
 * |    Op         | Choice | # |          Read        |       Write           |
 * |---------------+--------+---+----------------------+-----------------------|
 * | MultiDelete   |   N/A  |N/A|   MIN_READ * number  | sum of deleted record |
 * |               |        |   |  of records scanned  |        sizes          |
 * +---------------------------------------------------------------------------+
 */
public class MultiDeleteTableHandler
        extends MultiTableOperationHandler<MultiDeleteTable> {

    public MultiDeleteTableHandler(OperationHandler handler) {
        super(handler, OpCode.MULTI_DELETE_TABLE, MultiDeleteTable.class);
    }

    @Override
    public Result execute(MultiDeleteTable op,
                          Transaction txn,
                          PartitionId partitionId) {

        verifyTableAccess(op);

        int nDeletions = 0;
        final OperationTableInfo tableInfo = new OperationTableInfo();
        Scanner scanner = getScanner(op,
                                     tableInfo,
                                     txn,
                                     partitionId,
                                     op.getMajorPathComplete(),
                                     Direction.FORWARD,
                                     op.getResumeKey(),
                                     true, /*moveAfterResumeKey*/
                                     CURSOR_DEFAULT,
                                     LockMode.READ_UNCOMMITTED_ALL,
                                     true); // set keyOnly. Handle fetch here

        DatabaseEntry keyEntry = scanner.getKey();
        DatabaseEntry dataEntry = scanner.getData();
        Cursor cursor = scanner.getCursor();
        boolean moreElements;
        try {
            /*
             * Don't charge the cost of reading key in scanner, the cost of
             * reading key will be charged after key check by keyInTargetTable()
             *   - match > 0, valid key, charge min. read.
             *   - match < 0, no key found, charge empty read.
             *   - match = 0, invalid key for the target table and continue
             *                to next key, no charge.
             */
            scanner.setChargeKeyRead(false);
            while ((moreElements = scanner.next()) == true) {
                if (scanner.getResult().isTombstone()) {
                    continue;
                }

                int match = keyInTargetTable(op,
                                             tableInfo,
                                             keyEntry,
                                             dataEntry,
                                             cursor,
                                             false /* chargeReadCost */);

                if (match > 0) {
                    op.setLastDeleted(keyEntry.getData());

                    final int oldRecordSize = getStorageSize(cursor);

                    if (tableInfo.currentTable.isMultiRegion()) {
                        /* Insert tombstone for multi-region table.
                         * Since the record is not locked, it's possible
                         * that other threads have already inserted tombstones
                         * and here a duplicate tombstone will be inserted.
                         * This is harmless and it's ok to overcount the
                         * deletions.*/
                        insertTombstone(cursor, operationHandler,
                                        keyEntry,
                                        getTombstoneTTL(), op,
                                        oldRecordSize);
                        nDeletions++;

                    } else {

                        /*
                         * There is no need to get the record to lock it
                         * in the delete path.  If the record is gone the
                         * delete below will fail.
                         */
                        if (cursor.delete(null) != null) {
                            nDeletions++;
                            /*
                             * Gets the migration stream to forward the
                             * operation. It is OK if a migration starts
                             * between the get() call and addDelete() call,
                             * since the record is already deleted.
                             */
                            MigrationStreamHandle.get().
                                addDelete(keyEntry, cursor);

                            nDeletions +=
                                    tableInfo.deleteAncestorKeys(cursor,
                                                                 keyEntry);
                            op.addWriteBytes(oldRecordSize,
                                             getNIndexWrites(cursor));

                            /* Charge min. read for reading the matched key */
                            op.addMinReadCharge();
                        }
                    }
                } else if (match < 0) {
                    moreElements = false;
                    /* No matched key found, charge empty read */
                    op.addEmptyReadCharge();
                    break;
                }
                if (op.getBatchSize() > 0 && nDeletions >= op.getBatchSize()) {
                    break;
                }
                if (op.getMaxWriteKB() > 0 &&
                    op.getWriteKB() >= op.getMaxWriteKB()) {
                    break;
                }
            }
        } finally {
            scanner.close();
        }
        assert (!moreElements || op.getBatchSize() > 0 ||
                op.getMaxWriteKB() > 0 );
        byte[] resumeKey = moreElements ? op.getLastDeleted() : null;
        return new Result.MultiDeleteResult(getOpCode(),
                                            op.getReadKB(), op.getWriteKB(),
                                            nDeletions, resumeKey);
    }

    @Override
    public List<? extends KVStorePrivilege>
        tableAccessPrivileges(long tableId) {
        return Collections.singletonList(
            new TablePrivilege.DeleteTable(tableId));
    }

    @Override
    public List<? extends KVStorePrivilege>
    namespaceAccessPrivileges(String namespace) {
        return Collections.singletonList(
            new NamespacePrivilege.DeleteInNamespace(namespace));
    }

    /**
     * Put a tombstone at current position of the cursor.
     */
    protected static OperationResult
        insertTombstone(Cursor cursor,
                        OperationHandler operationHandler,
                        DatabaseEntry keyEntry,
                        TimeToLive tombstoneTTL,
                        InternalOperation op,
                        int oldRecordSize) {
        WriteOptions jeOptions = makeOption(tombstoneTTL, true);
        jeOptions.setTombstone(true);
        Value value = Value.createTombstoneValue(Region.LOCAL_REGION_ID);

        byte[] valueBytes = value.toByteArray();
        final DatabaseEntry dataEntry = valueDatabaseEntry(valueBytes);

        OperationResult result = BasicPutHandler.
            putEntry(cursor, null, dataEntry, CURRENT, jeOptions);

        long expTime = result.getExpirationTime();
        Version version = operationHandler.getVersion(cursor);
        MigrationStreamHandle.get().addPut(keyEntry, dataEntry,
                                           version.getVLSN(), expTime,
                                           true /*isTombstone*/);
        op.addReadBytes(MIN_READ);
        /* Charge for the deletion of the old record */
        op.addWriteBytes(oldRecordSize, 0);
        /* Charge for inserting tombstone */
        op.addWriteBytes(getStorageSize(cursor), 0);

        return result;
    }
}
