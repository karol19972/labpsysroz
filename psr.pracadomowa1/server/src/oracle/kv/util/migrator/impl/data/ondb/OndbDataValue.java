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

package oracle.kv.util.migrator.impl.data.ondb;

import static oracle.nosql.common.migrator.impl.data.DataUtils.*;

import java.math.BigDecimal;

import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.table.FieldDef.Type;
import oracle.kv.table.FieldValue;
import oracle.nosql.common.migrator.data.DataArray;
import oracle.nosql.common.migrator.data.DataEntry;
import oracle.nosql.common.migrator.data.DataValue;
import oracle.nosql.common.migrator.util.JsonUtils;

/**
 * The implementation of DataValue to represents Ondb record
 */
public class OndbDataValue implements DataValue {

    FieldValue value;
    private DataType type;

    OndbDataValue(FieldValue value) {
        this.value = value;
        this.type = getDataType(value);
    }

    private static DataType getDataType(FieldValue fval) {
        if (fval.isNull()) {
            return DataType.NULL;
        }
        Type type = fval.getType();
        switch(type) {
        case ARRAY:
            return DataType.ARRAY;
        case BINARY:
        case FIXED_BINARY:
        case ENUM:
        case STRING:
        case TIMESTAMP:
            return DataType.STRING;
        case BOOLEAN:
            return DataType.BOOLEAN;
        case FLOAT:
        case DOUBLE:
            return DataType.DOUBLE;
        case INTEGER:
            return DataType.INTEGER;
        case LONG:
            return DataType.LONG;
        case MAP:
        case RECORD:
            return DataType.MAP;
        case JSON:
            return DataType.NULL;
        case NUMBER:
            return DataType.DECIMAL;
        default:
            throw new IllegalStateException("Unexpected data type: " + type);
        }
    }
    @Override
    public DataType getType() {
        return type;
    }

    @Override
    public boolean isNull() {
        return (type == DataType.NULL);
    }

    @Override
    public int intValue() {
        switch (value.getType()) {
        case INTEGER:
            return value.asInteger().get();
        case LONG:
            return longToInt(value.asLong().get());
        case FLOAT:
            return doubleToInt(value.asFloat().get());
        case DOUBLE:
            return doubleToInt(value.asDouble().get());
        case NUMBER:
            return ((FieldValueImpl)value.asNumber()).castAsInt();
        case STRING:
            return stringToInt(value.asString().get());
        default:
            throw new IllegalArgumentException("Not valid int value");
        }
    }

    @Override
    public long longValue() {
        switch (value.getType()) {
        case INTEGER:
            return value.asInteger().get();
        case LONG:
            return value.asLong().get();
        case FLOAT:
            return doubleToLong(value.asFloat().get());
        case DOUBLE:
            return doubleToLong(value.asDouble().get());
        case NUMBER:
            return ((FieldValueImpl)value.asNumber()).castAsLong();
        case STRING:
            return stringToLong(value.asString().get());
        default:
            throw new IllegalArgumentException("Not valid long value");
        }
    }

    @Override
    public float floatValue() {
        switch (value.getType()) {
        case INTEGER:
            return value.asInteger().get();
        case LONG:
            return longToFloat(value.asLong().get());
        case FLOAT:
            return value.asFloat().get();
        case DOUBLE:
            return doubleToFloat(value.asDouble().get());
        case NUMBER:
            return ((FieldValueImpl)value.asNumber()).castAsFloat();
        case STRING:
            return stringToFloat(value.asString().get());
        default:
            throw new IllegalArgumentException("Not valid double value");
        }
    }

    @Override
    public double doubleValue() {
        switch (value.getType()) {
        case INTEGER:
            return value.asInteger().get();
        case LONG:
            return longToDouble(value.asLong().get());
        case FLOAT:
            return value.asFloat().get();
        case DOUBLE:
            return value.asDouble().get();
        case NUMBER:
            return ((FieldValueImpl)value.asNumber()).castAsDouble();
        case STRING:
            return stringToDouble(value.asString().get());
        default:
            throw new IllegalArgumentException("Not valid double value");
        }
    }

    @Override
    public BigDecimal decimalValue() {
        switch (value.getType()) {
        case INTEGER:
            return BigDecimal.valueOf(value.asInteger().get());
        case LONG:
            return BigDecimal.valueOf(value.asLong().get());
        case FLOAT:
            return BigDecimal.valueOf(value.asFloat().get());
        case DOUBLE:
            return BigDecimal.valueOf(value.asDouble().get());
        case NUMBER:
            return value.asNumber().get();
        case STRING:
            return stringToDecimal(value.asString().get());
        default:
            throw new IllegalArgumentException("Not valid decimal value");
        }
    }

    @Override
    public boolean booleanValue() {
        switch (value.getType()) {
        case STRING:
            return Boolean.valueOf(value.asString().get());
        case BOOLEAN:
            return value.asBoolean().get();
        default:
            throw new IllegalArgumentException("Not valid boolean value");
        }
    }

    @Override
    public String stringValue() {
        switch (value.getType()) {
        case INTEGER:
            return String.valueOf(value.asInteger().get());
        case LONG:
            return String.valueOf(value.asLong().get());
        case FLOAT:
            return String.valueOf(value.asFloat().get());
        case DOUBLE:
            return String.valueOf(value.asDouble().get());
        case NUMBER:
            return value.asNumber().get().toString();
        case STRING:
            return value.asString().get();
        case BOOLEAN:
            return String.valueOf(value.asBoolean().get());
        default:
            throw new IllegalArgumentException("Not valid boolean value");
        }
    }

    @Override
    public byte[] binaryValue() {
        switch (value.getType()) {
        case STRING:
            return JsonUtils.decodeBase64(value.asString().get());
        case BINARY:
            return value.asBinary().get();
        case FIXED_BINARY:
            return value.asFixedBinary().get();
        default:
            throw new IllegalArgumentException("Not valid binary value");
        }
    }

    @Override
    public DataEntry asMap() {
        if (type != DataType.MAP) {
            throw new IllegalArgumentException("Not valid object value");
        }
        return new OndbDataEntry(value);
    }

    @Override
    public DataArray asArray() {
        if (type != DataType.ARRAY) {
            throw new IllegalArgumentException("Not valid array value");
        }
        return new OndbDataArray(value);
    }

    @Override
    public DataValue clone() {
        return new OndbDataValue(value.clone());
    }

    @Override
    public String toJsonString(boolean pretty) {
        return value.toJsonString(pretty);
    }

    @Override
    public String toString() {
        return toJsonString(false);
    }
}
