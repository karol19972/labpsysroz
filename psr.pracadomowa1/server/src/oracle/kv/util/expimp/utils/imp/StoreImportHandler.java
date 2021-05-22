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

package oracle.kv.util.expimp.utils.imp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.Durability;
import oracle.kv.DurabilityException;
import oracle.kv.FaultException;
import oracle.kv.KVSecurityException;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.RequestTimeoutException;
import oracle.kv.Value;
import oracle.kv.Key.BinaryKeyIterator;
import oracle.kv.Value.Format;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.util.KVThreadFactory;
import oracle.kv.table.RecordDef;
import oracle.kv.table.Table;
import oracle.kv.util.expimp.utils.DataSerializer.KeyValueBytes;

/**
 * The class manages the import lob data operations, and alos includes 2
 * Transformer classes that transform Table data.
 */
public class StoreImportHandler {

    private static final int NUM_THREADS = 20;

    /*
     * Each LOB in the export package is imported into the target kvstore in a
     * separate thread. This list holds the status (future instances) of all
     * the worker threads importing the lob bytes into the kvstore.
     */
    private final List<FutureHolder> lobFutureList;

    /*
     * Asynchronous thread pool
     */
    private final ExecutorService threadPool;

    private Logger logger;

    private KVStore store;

    public StoreImportHandler(KVStore store, Logger logger) {
        this.store = store;
        lobFutureList = new ArrayList<FutureHolder>();
        threadPool = Executors.newFixedThreadPool(NUM_THREADS,
                                                  new KVThreadFactory("Import",
                                                                       null));
        this.logger = logger;
    }

    public void importLob(InputStream in, Key lobKey) {
        /*
         * LOB data is not imported using Bulk Put. A separate
         * thread is spawned which imports the LOB stream into the
         * target KVStore using KVStore.putLOB() API
         */
        Future<Boolean> lobImportTask =
            threadPool.submit(new PutLobTask(in, lobKey));

        FutureHolder holder = new FutureHolder(lobImportTask);
        lobFutureList.add(holder);
    }

    /**
     * LOB data is imported in a separate thread using KVStore.putLOB().
     * Wait for all these threads to complete their execution.
     */
    public void waitForLobTasks() {

        logger.info("Waiting for PutLobTask threads to complete execution.");

        try {
            for (FutureHolder holder : lobFutureList) {
                holder.getFuture().get();
            }
        } catch (ExecutionException e) {
            logger.log(Level.SEVERE, "Exception importing LOB.", e);
        } catch (InterruptedException ie) {
            logger.log(Level.SEVERE, "Exception importing LOB.", ie);
        }
    }

    public void close() {
        if (threadPool != null) {
            threadPool.shutdown();
        }
    }

    /**
     * This class holds a Future<Integer> and exists so that a
     * BlockingQueue<FutureHolder> can be used to indicate which Worker thread
     * tasks need to be waited upon.  A FutureHolder with a null future
     * indicated the end of input and the waiting thread can exit.
     */
    class FutureHolder {
        Future<Boolean> future;

        public FutureHolder(Future<Boolean> future) {
            this.future = future;
        }

        public Future<Boolean> getFuture() {
            return future;
        }
    }

    /**
     * A callable responsible for importing the LOB data into KVStore
     */
    public class PutLobTask implements Callable<Boolean> {

        private final InputStream in;
        private final Key lobKey;

        public PutLobTask(InputStream in, Key lobKey) {
            this.in = in;
            this.lobKey = lobKey;
        }

        @Override
        public Boolean call() {

            logger.info("PutLobTask thread spawned.");

            try {
                store.putLOB(lobKey, in,
                             Durability.COMMIT_WRITE_NO_SYNC,
                             5, TimeUnit.SECONDS);
            } catch (DurabilityException e) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), e);

                return false;
            } catch (RequestTimeoutException e) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), e);

                return false;
            } catch (ConcurrentModificationException e) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), e);

                return false;
            } catch (KVSecurityException kvse) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), kvse);

                return false;
            } catch (FaultException e) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), e);

                return false;
            } catch (IOException e) {

                logger.log(Level.SEVERE, "Error importing LOB with key " +
                           lobKey.toString(), e);

                return false;
            }

            return true;
        }
    }

    /**
     * Transform table data:
     *  1. If present table schema is same as writer schema associated with
     *     the table data, then refill tableIds and table version if needed.
     *  2. Otherwise, reserialize the table data to Key/Value with the present
     *     table in store.
     */
    public static class TableDataTransformer {
        private TableImpl tableImpl;
        private RecordDef writeValueDef;
        private boolean sameTableSchema;
        private Map<String, Integer> tableIds;
        private boolean needReserialize;

        public TableDataTransformer(Table table, RecordDef writeValueDef, boolean needReserialize) {
            tableImpl = (TableImpl)table;
            this.writeValueDef = writeValueDef;
            sameTableSchema = (writeValueDef != null &&
                tableImpl.getValueRecordDef() != null &&
                writeValueDef.equals(tableImpl.getValueRecordDef()));
            this.needReserialize = needReserialize;
            tableIds = null;
        }

        public KeyValueBytes transform(KeyValueBytes kvBytes) {
            if ((sameTableSchema && !needReserialize) ||
                    kvBytes.getValueBytes().length == 0) {
                if (tableIds == null) {
                    tableIds = getTableIds(tableImpl);
                }
                Key key = Key.fromByteArray(kvBytes.getKeyBytes());
                key = refillTableId(key);
                kvBytes.setKeyBytes(key.toByteArray());
                refillTableVersion(kvBytes.getValueBytes(),
                                   ((kvBytes.getValueFormat() != null) ? 0 : 1));
            } else {
                kvBytes = convert(kvBytes);
            }
            return kvBytes;
        }

        /**
         * Get the tableId strings and their location in the key full path
         * for the table
         */
        private Map<String, Integer> getTableIds(TableImpl table) {

            Map<Integer, String> tempMap = new HashMap<Integer, String>();
            Map<String, Integer> tabIds = new HashMap<String, Integer>();
            TableImpl tempTable = table;

            int tableNumber = 0;
            while (tempTable != null) {
                String idString = tempTable.getIdString();
                tempTable = (TableImpl)tempTable.getParent();
                int numKeys = 0;
                if (tempTable != null) {
                    numKeys = tempTable.getPrimaryKeySize();
                }
                tempMap.put(tableNumber, numKeys + "," + idString);
                tableNumber++;
            }

            for (Map.Entry<Integer, String> entry : tempMap.entrySet()) {
                int idx = entry.getKey();
                int numParentTables = tableNumber - 1 - idx;
                String[] keysAndTableId = entry.getValue().split(",");
                int numKeys = Integer.parseInt(keysAndTableId[0]);
                String tableId = keysAndTableId[1];
                tabIds.put(tableId, numKeys + numParentTables);
            }
            return tabIds;
        }

        private Key refillTableId(Key key) {
            List<String> fullPath = key.getFullPath();

            /*
             * Set the tableIds in the correct position in the full
             * path for the key
             */
            setTableIds(tableIds, fullPath);

            List<String> majorPath = new ArrayList<String>();
            List<String> minorPath = new ArrayList<String>();
            int majorPathLength = key.getMajorPath().size();

            /*
             * Extract major path of key from the full path
             */
            for (int i = 0; i < majorPathLength; i++) {
                majorPath.add(fullPath.get(i));
            }

            /*
             * Extract minor path of key from the full path
             */
            for (int i = majorPathLength; i < fullPath.size(); i++) {
                minorPath.add(fullPath.get(i));
            }

            return Key.createKey(majorPath, minorPath);
        }

        /**
         * Set the tableIds (obtained using getTableIds) in the fullKey
         */
        private void setTableIds(Map<String, Integer> tableIds,
                                 List<String> fullKey) {

            for (Map.Entry<String, Integer> entry : tableIds.entrySet()) {
                String tableId = entry.getKey();
                int index = entry.getValue();
                fullKey.set(index, tableId);
            }
        }

        private void refillTableVersion(byte[] valueBytes, int offset) {
            if (valueBytes.length > 0) {
                int tableVersion = tableImpl.getTableVersion();
                if (valueBytes[offset] != tableVersion) {
                    valueBytes[offset] = (byte)tableVersion;
                }
            }
        }

        private KeyValueBytes convert(KeyValueBytes kvBytes) {
            byte[] keyBytes = kvBytes.getKeyBytes();
            byte[] valueBytes = kvBytes.getValueBytes();

            BinaryKeyIterator keyIter = new BinaryKeyIterator(keyBytes);
            RowImpl importedRow = tableImpl.createRow();

            Iterator<String> pkIter = tableImpl.getPrimaryKey().iterator();

            boolean isKeyFilled =
                tableImpl.createImportRowFromKeyBytes(importedRow,
                                                      keyIter,
                                                      pkIter);

            if (!isKeyFilled || !keyIter.atEndOfKey()) {
                String msg = "Mismatch between the primary key of the " +
                    "table schema in the target nosql store and the " +
                    "table data being imported. Skipping the record.";

                throw new RuntimeException(msg);
            }

            if (valueBytes.length == 0) {

                Key key = importedRow.getPrimaryKey(false);
                Value value = importedRow.createValue();

                return KeyValueBytes.createNonFormatData(
                        key.toByteArray(), value.toByteArray());
            }

            Format valueFormat = kvBytes.getValueFormat();
            /*
             * Deserialize a record value.
             */
            tableImpl.createExportRowFromValueSchema((RecordDefImpl)writeValueDef,
                                                     null,
                                                     importedRow,
                                                     valueBytes,
                                                     0,
                                                     tableImpl.getTableVersion(),
                                                     valueFormat);

            Key key = importedRow.getPrimaryKey(false);
            Value value = importedRow.createValue();
            long expiryTimeMs = kvBytes.getExpirationTime();
            return KeyValueBytes.createTableData(key.toByteArray(), valueFormat,
                                                 value.getValue(), expiryTimeMs);
        }
    }
}
