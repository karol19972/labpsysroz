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

package oracle.kv.util.migrator.impl.sink.ondb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.BulkWriteOptions;
import oracle.kv.EntryStream;
import oracle.kv.Key;
import oracle.kv.KeyValue;
import oracle.kv.Value;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableAPIImpl.GeneratedValueInfo;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig.TableInfo;
import oracle.kv.util.migrator.impl.sink.ondb.ValueSerializer.RowSerializerImpl;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.DataTransform;
import oracle.nosql.common.migrator.StateHandler;
import oracle.nosql.common.migrator.data.DataEntry;
import oracle.nosql.common.migrator.data.Entry;
import oracle.nosql.common.migrator.impl.transform.AddJsonFieldTransform;

/**
 * The OndbRowSink that writes row data entry represented by DataEntry to
 * NoSQL DB.
 */
public class OndbRowSink extends OndbSink<KeyValue> {

    public OndbRowSink(OndbSinkConfig config,
                       StateHandler stateHandler,
                       Logger logger) {
        super(config, stateHandler, logger);
    }

    @Override
    List<EntryStream<KeyValue>> createBulkPutStreams(DataSource[] sources) {

        final List<EntryStream<KeyValue>> kvstreams =
             new ArrayList<EntryStream<KeyValue>>(sources.length);

        for (DataSource source : sources) {
            String qualifiedName = source.getTargetTable();
            TableInfo ti = config.getTableInfo(qualifiedName);

            /* Execute tableStatement and indexStatements if specified */
            Table table = createTable(ti);
            if (table == null) {
                String msg = "[Skipped] Table not present in store, " +
                    "skipping the records of the table: " + qualifiedName;
                log(Level.WARNING, msg);
                continue;
            }

            /* Add transformer if needed */
            DataTransform[] transforms = createTransforms(table);
            if (transforms != null) {
                for (DataTransform handler : transforms) {
                    source.addTransform(handler);
                }
            }

            /* Populates EntryStream */
            kvstreams.add(new DataKVStream(source, table));
        }

        return kvstreams;
    }

    private DataTransform[] createTransforms(Table table) {
        String jsonFieldName = null;
        Set<String> nonJsonFields = new HashSet<String>();
        /*
         * Add AddJsonFieldTransform if table contains single JSON field.
         *
         * A special case is as below, e.g.
         *   Table foo(id integer, name string, doc JSON, primary key(id)).
         *    Json record: {"id":1, "name":"jack", "doc":{...}}.
         *
         * In this case, Json record has exact same fields of that of table,
         * there will be no JSON field added finally, it is handled by
         * AddJsonFieldTransform.
         */
        for (String field : table.getFields()) {
            if (table.getField(field).isJson()) {
                if (jsonFieldName == null) {
                    jsonFieldName = field;
                } else {
                    /*
                     * Skip to add JSON field if table contains multiple
                     * JSON field
                     */
                    jsonFieldName = null;
                    break;
                }
            } else {
                nonJsonFields.add(field);
            }
        }

        if (jsonFieldName != null) {
            DataTransform transform =
                new AddJsonFieldTransform(nonJsonFields, jsonFieldName, logger);
            return new DataTransform[]{transform};
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    void performBulkPut(List<EntryStream<KeyValue>> kvstreams,
                        BulkWriteOptions bwo) {
        List<EntryStream<KeyValue>> streams =
                new ArrayList<EntryStream<KeyValue>>();
        for (EntryStream<?> stream : kvstreams) {
            streams.add((EntryStream<KeyValue>)stream);
        }
        store.put(streams, bwo);
    }

    private class DataKVStream extends DataStream<KeyValue> {
        private final RowSerializerImpl rowSerializer;
        private final TableImpl table;
        private final GeneratedValueInfo idInfo;

        public DataKVStream(DataSource source, Table table) {
            super(source);
            this.table = (TableImpl)table;
            rowSerializer = (RowSerializerImpl)
                ValueSerializer.createRowSerializer(table);
            if (((TableImpl)table).hasIdentityColumn()) {
                idInfo = new GeneratedValueInfo(0);
            } else {
                idInfo = null;
            }
        }

        @Override
        KeyValue convertData(Entry entry) {
            return serializeToKeyValue((DataEntry)entry);
        }

        @Override
        String keyStringOfData(KeyValue kv) {
            return getPrimaryKey(kv).toJsonString(false);
        }

        private KeyValue serializeToKeyValue(DataEntry dataEntry) {
            rowSerializer.setRowValue(dataEntry);
            try {
                Key key = table.createKeyInternal(rowSerializer, false,
                        (KVStoreImpl)store, idInfo);
                Value value = table.createValueInternal(rowSerializer,
                        (KVStoreImpl)store, idInfo);
                return new KeyValue(key, value);
            } catch (RuntimeException re) {
                log(Level.SEVERE, "Serialize data entry failed:" + re);
                throw re;
            }
        }

        private Row getPrimaryKey(KeyValue kv) {
            byte[] keyBytes = kv.getKey().toByteArray();
            return table.createRowFromKeyBytes(keyBytes);
        }

        @Override
        public void close() {
            rowSerializer.clear();
        }
    }
}
