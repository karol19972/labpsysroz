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

import java.sql.Timestamp;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import oracle.kv.Value;
import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.NumberUtils;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableKey;
import oracle.kv.impl.api.table.TimestampDefImpl;
import oracle.kv.impl.api.table.ValueSerializer.ArrayValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.FieldValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.MapValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.RecordValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.RowSerializer;
import oracle.kv.table.ArrayDef;
import oracle.kv.table.FieldDef;
import oracle.kv.table.FieldDef.Type;
import oracle.kv.table.MapDef;
import oracle.kv.table.RecordDef;
import oracle.kv.table.Table;
import oracle.kv.table.TimeToLive;
import oracle.nosql.common.migrator.data.DataArray;
import oracle.nosql.common.migrator.data.DataEntry;
import oracle.nosql.common.migrator.data.DataValue;
import oracle.nosql.common.migrator.data.DataValue.DataType;

/**
 * Implements the ValueSerializer interface to serialize DataEntry object to
 * Key/Value.
 */
public class ValueSerializer {

    private enum SerType {
        FIELD_VALUE,
        MAP_VALUE,
        ARRAY_VALUE,
        RECORD_VALUE
    }

    private final Map<SerType, SerializerFactory<?>> factories;

    ValueSerializer() {
        factories = new HashMap<SerType, SerializerFactory<?>>();
    }

    public static RowSerializer createRowSerializer(Table table) {
        final ValueSerializer mgr = new ValueSerializer();
        return new RowSerializerImpl(mgr, table);
    }

    /*
     * Returns a FieldValueSerializer instance.
     */
    FieldValueSerializer getValueSerializer(FieldDef def, DataValue val) {
        final SerType type = SerType.FIELD_VALUE;

        @SuppressWarnings("unchecked")
        SerializerFactory<FieldValueSerializer> fac =
            (SerializerFactory<FieldValueSerializer>)factories.get(type);
        if (fac == null) {
            fac = new SerializerFactory<FieldValueSerializer>(this) {
                @Override
                FieldValueSerializer createSerializer(ValueSerializer mgr) {
                    return new FieldValueSerializerImpl(mgr);
                }
            };
            factories.put(type, fac);
        }
        FieldValueSerializerImpl fvs = (FieldValueSerializerImpl)fac.get();
        fvs.setFieldValue(def, val);
        return fvs;
    }

    /*
     * Returns an ArrayValueSerializer instance.
     */
    ArrayValueSerializer getArraySerializer(ArrayDef def, DataArray val) {
        final SerType type = SerType.ARRAY_VALUE;

        @SuppressWarnings("unchecked")
        SerializerFactory<ArrayValueSerializer> fac =
            (SerializerFactory<ArrayValueSerializer>)factories.get(type);

        if (fac == null) {
            fac = new SerializerFactory<ArrayValueSerializer>(this) {
                @Override
                ArrayValueSerializer createSerializer(ValueSerializer mgr) {
                    return new ArrayValueSerializerImpl(mgr);
                }
            };
            factories.put(type, fac);
        }

        ArrayValueSerializerImpl fvs = (ArrayValueSerializerImpl)fac.get();
        fvs.setArrayValue(def, val);
        return fvs;
    }

    /*
     * Returns a MapValueSerializer instance.
     */
    MapValueSerializer getMapSerializer(MapDef def, DataEntry val) {
        final SerType type = SerType.MAP_VALUE;

        @SuppressWarnings("unchecked")
        SerializerFactory<MapValueSerializer> fvFac =
            (SerializerFactory<MapValueSerializer>) factories.get(type);

        if (fvFac == null) {
            fvFac = new SerializerFactory<MapValueSerializer>(this) {
                @Override
                MapValueSerializer createSerializer(ValueSerializer mgr) {
                    return new MapValueSerializerImpl(mgr);
                }
            };
            factories.put(type, fvFac);
        }

        MapValueSerializerImpl mvs = (MapValueSerializerImpl)fvFac.get();
        mvs.setMapValue(def, val);
        return mvs;
    }

    /*
     * Returns a RecordValueSerializer instance.
     */
    RecordValueSerializer getRecordSerializer(RecordDef def, DataEntry val) {
        final SerType type = SerType.RECORD_VALUE;

        @SuppressWarnings("unchecked")
        SerializerFactory<RecordValueSerializer> fac =
            (SerializerFactory<RecordValueSerializer>)factories.get(type);

        if (fac == null) {
            fac = new SerializerFactory<RecordValueSerializer>(this) {
                @Override
                RecordValueSerializer createSerializer(ValueSerializer mgr) {
                    return new RecordValueSerializerImpl(mgr);
                }
            };
            factories.put(type, fac);
        }

        RecordValueSerializerImpl rvs = (RecordValueSerializerImpl)fac.get();
        rvs.setRecordValue(def, val);
        return rvs;
    }

    void reset() {
        for (SerializerFactory<?> f : factories.values()) {
            f.reset();
        }
    }

    void clear() {
        for (SerializerFactory<?> f : factories.values()) {
            f.clear();
        }
    }

    /**
     * Implementation of RowSerializer
     */
    public static class RowSerializerImpl extends RecordValueSerializerImpl
        implements RowSerializer {

        private final Table table;
        private TimeToLive ttl;
        private int regionId;
        private long lastUpdateTimeMs;

        public RowSerializerImpl(ValueSerializer mgr, Table table) {
            super(mgr);
            this.table = table;
            setRecordValue(((TableImpl)table).getRowDef(), null);
        }

        public void setRowValue(DataEntry newValue) {
            if (value != null) {
                reset();
            }
            setRecordValue(newValue);
        }

        @Override
        public String getClassNameForError() {
            return "Row";
        }

        public void setTTL(TimeToLive ttl) {
            this.ttl = ttl;
        }

        @Override
        public TimeToLive getTTL() {
            return ttl;
        }

        @Override
        public Table getTable() {
            return table;
        }

        @Override
        public boolean isPrimaryKey() {
            return false;
        }

        @Override
        public FieldValueSerializer get(int pos) {
            FieldValueSerializer fval = super.get(pos);
            if (fval != null) {
                TableImpl tableImpl = (TableImpl)table;
                if (tableImpl != null &&
                    tableImpl.hasIdentityColumn() &&
                    tableImpl.getIdentityColumn() == pos &&
                    tableImpl.isIdentityGeneratedAlways() ) {
                    throw new IllegalArgumentException("Value should not be " +
                        "set for a generated always identity column: " +
                        tableImpl.getFields().get(pos));
                }
            }
            return fval;
        }

        @Override
        public void validateKey(TableKey key) {
        }

        @Override
        public void validateValue(Value val) {
        }

        @Override
        public int getRegionId() {
            return regionId;
        }

        @Override
        public boolean isFromMRTable() {
            return ((TableImpl)table).isMultiRegion();
        }

        @Override
        public long getLastModificationTime() {
            if (lastUpdateTimeMs <= 0) {
                throw new UnsupportedOperationException("Modification " +
                    "time is not available.");
            }
            return lastUpdateTimeMs;
        }

        public void reset() {
            mgr.reset();
        }

        public void clear() {
            mgr.clear();
        }
    }

    /**
     * Implementation of RecordValueSerializer
     */
    static class RecordValueSerializerImpl implements RecordValueSerializer {

        final ValueSerializer mgr;
        RecordDef def;
        DataEntry value;

        RecordValueSerializerImpl(ValueSerializer mgr) {
            this.mgr = mgr;
        }

        RecordValueSerializerImpl setRecordValue(RecordDef def,
                                                 DataEntry value) {
            this.def = def;
            this.value = value;
            return this;
        }

        RecordValueSerializerImpl setRecordValue(DataEntry value) {
            this.value = value;
            return this;
        }

        @Override
        public FieldValueSerializer get(int pos) {
            String name = def.getFieldName(pos);
            DataValue fval = value.getValue(name);
            if (fval == null) {
                return null;
            }
            FieldDef fdef = def.getFieldDef(pos);
            return mgr.getValueSerializer(fdef, fval);
        }

        @Override
        public RecordDef getDefinition() {
            return def;
        }

        @Override
        public int size() {
            return (value != null) ? value.size() : 0;
        }
    }

    /**
     * Implementation of MapValueSerializerImpl
     */
    static class MapValueSerializerImpl implements MapValueSerializer {

        private final ValueSerializer mgr;
        private MapDef def;
        private DataEntry dataEntry;

        MapValueSerializerImpl(ValueSerializer mgr) {
            this.mgr = mgr;
        }

        MapValueSerializerImpl setMapValue(MapDef def, DataEntry dataEntry) {
            this.def = def;
            this.dataEntry = dataEntry;
            return this;
        }

        @Override
        public MapDef getDefinition() {
            return def;
        }

        @Override
        public Iterator<Entry<String, FieldValueSerializer>> iterator() {
            final Iterator<String> iter = dataEntry.getFields().iterator();

            return new Iterator<Entry<String, FieldValueSerializer>>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Map.Entry<String, FieldValueSerializer> next() {
                    if (!hasNext()) {
                        return null;
                    }
                    final String key = iter.next();
                    FieldValueSerializer value =
                        mgr.getValueSerializer(def.getElement(),
                                               dataEntry.getValue(key));
                    return new SimpleEntry<String, FieldValueSerializer>
                        (key, value);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public int size() {
            return dataEntry.size();
        }
    }

    /**
     * Implementation of ArrayValueSerializer
     */
    static class ArrayValueSerializerImpl implements ArrayValueSerializer {

        private final ValueSerializer mgr;
        private ArrayDef def;
        private DataArray value;

        ArrayValueSerializerImpl(ValueSerializer mgr) {
            this.mgr = mgr;
        }

        ArrayValueSerializerImpl setArrayValue(ArrayDef def, DataArray value) {
            this.def = def;
            this.value = value;
            return this;
        }

        @Override
        public ArrayDef getDefinition() {
            return def;
        }

        @Override
        public Iterator<FieldValueSerializer> iterator() {
            Iterator<DataValue> iter = value.getElements();
            return new Iterator<FieldValueSerializer> () {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }
                @Override
                public FieldValueSerializer next() {
                    if (!hasNext()) {
                        return null;
                    }
                    return mgr.getValueSerializer(def.getElement(),
                                                  iter.next());
                }
            };
        }

        @Override
        public int size() {
            return value.size();
        }
    }

    /**
     * Implementation of FieldValueSerializer
     */
    static class FieldValueSerializerImpl implements FieldValueSerializer {

        private final ValueSerializer mgr;
        private FieldDef def;
        private DataValue value;

        FieldValueSerializerImpl(ValueSerializer mgr) {
            this.mgr = mgr;
        }

        FieldValueSerializerImpl setFieldValue(FieldDef def, DataValue value) {
            this.def = def.isJson() ? mapToKVType(value) : def;
            this.value = value;
            return this;
        }

        @Override
        public FieldDef getDefinition() {
            return def;
        }

        @Override
        public Type getType() {
            return def.getType();
        }

        @Override
        public ArrayValueSerializer asArrayValueSerializer() {
            return mgr.getArraySerializer(def.asArray(), value.asArray());
        }

        @Override
        public MapValueSerializer asMapValueSerializer() {
            return mgr.getMapSerializer(def.asMap(), value.asMap());
        }

        @Override
        public RecordValueSerializer asRecordValueSerializer() {
            return mgr.getRecordSerializer(def.asRecord(), value.asMap());
        }

        @Override
        public boolean getBoolean() {
            return value.booleanValue();
        }

        @Override
        public byte[] getBytes() {
            return value.binaryValue();
        }

        @Override
        public double getDouble() {
            return value.doubleValue();
        }

        @Override
        public String getEnumString() {
            return value.stringValue();
        }

        @Override
        public byte[] getFixedBytes() {
            return getBytes();
        }

        @Override
        public float getFloat() {
            return value.floatValue();
        }

        @Override
        public int getInt() {
            return value.intValue();
        }

        @Override
        public long getLong() {
            return value.longValue();
        }

        @Override
        public byte[] getNumberBytes() {
            return NumberUtils.serialize(value.decimalValue());
        }

        @Override
        public String getString() {
            return value.stringValue();
        }

        @Override
        public byte[] getTimestampBytes() {
            if (value.getType() == DataType.STRING) {
                String str = value.stringValue();
                return ((TimestampDefImpl)def).fromString(str).getBytes();
            } else if (value.getType() == DataType.LONG) {
                long lval = value.longValue();
                return ((TimestampDefImpl)def)
                    .createTimestamp(new Timestamp(lval)).getBytes();
            }
            throw new IllegalArgumentException(
                "Invalid type for Timestamp: " + value.getType());
        }

        @Override
        public boolean isEMPTY() {
            return false;
        }

        @Override
        public boolean isJsonNull() {
            /* In JSON field type, convert NULL to JSON_NULL. */
            return (def.isJson() && value.isNull());
        }

        @Override
        public boolean isNull() {
            return (!def.isJson() && value.isNull());
        }

        private FieldDef mapToKVType(DataValue dval) {
            switch (dval.getType()) {
            case NULL:
                return FieldDefImpl.jsonDef;
            case INTEGER:
                return FieldDefImpl.integerDef;
            case LONG:
                return FieldDefImpl.longDef;
            case DOUBLE:
                return FieldDefImpl.doubleDef;
            case DECIMAL:
                return FieldDefImpl.numberDef;
            case BOOLEAN:
                return FieldDefImpl.booleanDef;
            case BINARY:
            case STRING:
                return FieldDefImpl.stringDef;
            case ARRAY:
                return FieldDefImpl.arrayJsonDef;
            case MAP:
                return FieldDefImpl.mapJsonDef;
            default:
                throw new IllegalArgumentException(
                    "Unsupported type:" + dval.getType());
            }
        }
    }

    private abstract class SerializerFactory<T> {

        private List<T> objects;
        private int index;
        private final ValueSerializer mgr;

        SerializerFactory(ValueSerializer mgr) {
            this.mgr = mgr;
            objects = new ArrayList<T>();
            index = 0;
        }

        abstract T createSerializer(ValueSerializer vsMgr);

        T get() {
            if (index < objects.size()) {
                return objects.get(index++);
            }
            T obj = createSerializer(mgr);
            objects.add(obj);
            index++;
            return obj;
        }

        public void reset() {
            index = 0;
        }

        public void clear() {
            objects.clear();
        }

        @Override
        public String toString() {
            return "Index: " + index + ", Size: " + objects.size();
        }
    }
}
