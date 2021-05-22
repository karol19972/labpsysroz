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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.SerializationUtil;
import oracle.kv.table.Table;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * Helper methods for persisting table metadata. This class assumes that the
 * table metadata container (TableMetadata) is persisted by the caller.
 * These methods handle the table instances, specifically the table
 * hierarchies (a top-level table and all of its child tables and indexes).
 *
 * Two types of objects are stored in the table metadata DB:
 *
 * 1. A single table TableMetadata instance (handled elsewhere).
 *
 * 2. Table hierarchies (one record per top-level table). The key is the table
 * ID as a long. The IDs are short and unique. The RN does not need to access
 * these records by table name (or ID for that matter).
 *
 * The table ID does not conflict with the metadata key which is the string
 * returned from MetadataType.getType(). The iterations over the table records
 * in this class assume that the metadata object appears BEFORE the table
 * records by key order.
 */
public class TableMetadataPersistence {

    private TableMetadataPersistence() {}

    /**
     * Writes the changed table instances, or all of the tables in the
     * metadata. If changed is null, all of the top level tables in the
     * metadata are persisted, and tables no longer in the metadata are
     * deleted. Otherwise only the tables in the changed list are added,
     * updated, or removed from the database as necessary.
     * The input metadata is returned.
     */
    public static void writeTables(TableMetadata md,
                                   Map<Long, TableImpl> changed,
                                   Database db,
                                   Transaction txn,
                                   TableKeyGenerator keyGen,
                                   short serialVersion,
                                   Logger logger) {
        assert txn != null;
        assert serialVersion > 0;

        if (changed == null) {
            writeAll(md, db, txn, keyGen, serialVersion, logger);
            return;
        }
        logger.log(Level.FINE, () -> "Partial update of " + md + " " +
                                     changed.size() + " changes");

        /* Reusable database entries */
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();

        for (TableImpl table : changed.values()) {
            assert table.isTop();

            /*
             * If the top table was dropped (no longer in the metadata), remove
             * it from the DB
             */
            if (md.getTable(table.getFullNamespaceName()) == null) {
                deleteTable(table, key, db, txn, keyGen);
                continue;
            }
            putTable(table, key, value, db, txn, keyGen, serialVersion);
        }
    }

    /**
     * Writes the specified table to the database.
     */
    private static void putTable(TableImpl table,
                                 DatabaseEntry key,
                                 DatabaseEntry value,
                                 Database db, Transaction txn,
                                 TableKeyGenerator keyGen,
                                 short serialVersion) {
        assert serialVersion > 0;
        keyGen.setKey(key, table);
        value.setData(SerializationUtil.getBytes(table, serialVersion));
        db.put(txn, key, value);
    }

    /**
     * Deletes the specified table from the database.
     */
    private static void deleteTable(TableImpl table,
                                    DatabaseEntry key,
                                    Database db, Transaction txn,
                                    TableKeyGenerator keyGen) {
        keyGen.setKey(key, table);
        db.delete(txn, key);
    }

    /**
     * Persists all of the tables in the metadata. Tables no longer in the
     * metadata are deleted. The input metadata is returned.
     */
    private static void writeAll(TableMetadata md,
                                 Database db,
                                 Transaction txn,
                                 TableKeyGenerator keyGen,
                                 short serialVersion,
                                 Logger logger) {
        logger.log(Level.FINE, () -> "Full update of " + md);

        /* Reusable database entries */
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();

        /*
         * This is a map of the tables to write. They are removed from the map
         * as they are checked against the existing tables.
         */
        final Map<String, Table> tableMap = new HashMap<>(md.getTables());

        /*
         * Read each of the existing top level table records to see if the table
         * still exists and if it has changed.
         */
        try (Cursor cursor = db.openCursor(txn, CursorConfig.DEFAULT)) {
            final DatabaseEntry newValue = new DatabaseEntry();

            /* Move the cursor to the first table record */
            keyGen.setStartKey(key);
            OperationStatus status = cursor.getSearchKeyRange(key, value,
                                                              LockMode.RMW);
            while (status == OperationStatus.SUCCESS) {
                final TableImpl oldTable = getTable(value);

                /* The name of the table we are currently checking */
                final String tableName = oldTable.getFullNamespaceName();

                /*
                 * If the old table is gone (not in the new MD) or the old
                 * table has a different ID delete the old table.
                 */
                final TableImpl newTable = (TableImpl)tableMap.get(tableName);
                if ((newTable == null) ||
                    (newTable.getId() != oldTable.getId())) {
                    logger.log(Level.FINE, () -> "Removing old table " +
                                                 tableName);
                    cursor.delete();
                } else {
                    /**
                     * If here, the old table and new table are the same tables,
                     * i.e. they have the same ID. Check whether the old table
                     * has changed and if so update it here.
                     */
                    newValue.setData(SerializationUtil.getBytes(newTable,
                                                                serialVersion));
                    if (!Arrays.equals(value.getData(), newValue.getData())) {
                        logger.log(Level.FINE, () -> "Detected change in " +
                                                     tableName + " updating");
                        cursor.put(key, newValue);
                    }
                    /* Remove the entry since it has been checked */
                    tableMap.remove(tableName);
                }
                status = cursor.getNext(key, value, LockMode.RMW);
            }
        }

        /*
         * At this point all changed tables have been updated and have been
         * removed from the idMap. Any remaining are new tables.
         */
        for (Table t : tableMap.values()) {
            logger.log(Level.FINE, () -> "Found new table: " +
                                         t.getFullNamespaceName());
            putTable((TableImpl)t, key, value, db, txn, keyGen, serialVersion);
        }
    }

    /**
     * Reads all table records and populates the specified table metadata
     * instance.
     */
    public static TableMetadata readTables(TableMetadata fetchedMD,
                                           Database db,
                                           Transaction txn,
                                           TableKeyGenerator keyGen,
                                           Logger logger) {
        fetchedMD.initializeTables(null);

        /* Reusable database entries */
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();

        try (Cursor cursor = db.openCursor(txn, CursorConfig.DEFAULT)) {
            keyGen.setStartKey(key);

            OperationStatus status = cursor.getSearchKeyRange(key, value, null);

            while (status == OperationStatus.SUCCESS) {
                final TableImpl table = getTable(value);
                fetchedMD.addTableHierarchy(table);
                logger.log(Level.FINE, () -> "Inserted " + table +
                                             " to " + fetchedMD);
                status = cursor.getNext(key, value, null);
            }
            return fetchedMD;
        }
    }

    /* Dumps the table record keys from the DB. For debug. */
    @SuppressWarnings("unused")
    private static void dump(Database db, Transaction txn, Logger logger) {
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry value = new DatabaseEntry();

        key.setData(new byte[0]);
        try (Cursor cursor = db.openCursor(txn, CursorConfig.DEFAULT)) {
            OperationStatus status = cursor.getSearchKeyRange(key, value, null);

            while (status == OperationStatus.SUCCESS) {
                logger.warning("Found table key: " + key);
                status = cursor.getNext(key, value, null);
            }
        }
    }

    /**
     * Returns a table instance from the input database entry.
     */
    public static TableImpl getTable(DatabaseEntry entry) {
        try (final DataInputStream dis = new DataInputStream(
                 new ByteArrayInputStream(entry.getData()))) {
            final short serialVersion = dis.readShort();
            if (serialVersion > SerialVersion.CURRENT) {
                throw new IllegalStateException("Error deserializing table," +
                                                " unknown serial version: " +
                                                serialVersion);
            }
            final TableImpl table = new TableImpl(dis, serialVersion, null);
            // Convert to runtime exception?
            assert table.isTop();
            return table;
        } catch (IOException ioe) {
            throw new IllegalStateException ("Exception deserializing table",
                                             ioe);
        }
    }

    public interface TableKeyGenerator {
        void setStartKey(DatabaseEntry key);
        void setKey(DatabaseEntry key, TableImpl table);
    }
}

