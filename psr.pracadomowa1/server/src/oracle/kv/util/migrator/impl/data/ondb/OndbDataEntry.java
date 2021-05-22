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

import java.util.HashSet;
import java.util.Set;

import oracle.kv.table.FieldValue;
import oracle.nosql.common.migrator.data.DataEntry;
import oracle.nosql.common.migrator.data.DataValue;

/**
 * The DataEntry implementation for Ondb record.
 */
public class OndbDataEntry extends OndbDataValue
    implements DataEntry {

    public OndbDataEntry(FieldValue value) {
        super(value);
    }

    @Override
    public DataType getType() {
        return DataType.MAP;
    }

    @Override
    public Set<String> getFields() {
        if (value.isRecord()) {
            return new HashSet<String>(value.asRecord().getFieldNames());
        } else if (value.isMap()) {
            return value.asMap().getFields().keySet();
        }
        throw new IllegalArgumentException(
            "Unexpected value type for StoreDataEntry: " + value.getType());
    }

    @Override
    public DataValue getValue(String field) {
        if (value.isRecord()) {
            return new OndbDataValue(value.asRecord().get(field));
        } else if (value.isMap()) {
            return new OndbDataValue(value.asMap().get(field));
        }
        throw new IllegalArgumentException(
            "Unexpected value type for StoreDataEntry: " + value.getType());
    }

    @Override
    public int size() {
        if (value.isRecord()) {
            return value.asRecord().size();
        } else if (value.isMap()) {
            return value.asMap().size();
        }
        throw new IllegalArgumentException(
            "Unexpected value type for StoreDataEntry: " + value.getType());
    }

    @Override
    public DataEntry put(String field, DataValue dataVal) {
        throw new UnsupportedOperationException(
            "StoreDataEntry.put(field, value)");
    }

    @Override
    public DataEntry clone() {
        throw new UnsupportedOperationException("StoreDataEntry.clone()");
    }
}
