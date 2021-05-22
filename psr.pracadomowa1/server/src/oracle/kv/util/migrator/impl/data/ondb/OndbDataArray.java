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

import java.util.Iterator;

import oracle.kv.impl.api.table.ArrayValueImpl;
import oracle.kv.table.FieldValue;
import oracle.nosql.common.migrator.data.DataArray;
import oracle.nosql.common.migrator.data.DataValue;

/**
 * The implementation of DataArray for Ondb record
 */
public class OndbDataArray extends OndbDataValue
    implements DataArray {

    OndbDataArray(FieldValue value) {
        super(value);
    }

    @Override
    public Iterator<DataValue> getElements() {
        final ArrayValueImpl av = (ArrayValueImpl)value.asArray();
        return new Iterator<DataValue>()  {

            private Iterator<FieldValue> iter = av.toList().iterator();
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public DataValue next() {
                if (!iter.hasNext()) {
                    return null;
                }
                return new OndbDataValue(iter.next());
            }
        };
    }

    @Override
    public int size() {
        return value.asArray().size();
    }

    @Override
    public DataArray add(DataValue val) {
        throw new UnsupportedOperationException(
            "add(DataValue) is not implemetned");
    }

    @Override
    public DataArray add(int index, DataValue val) {
        throw new UnsupportedOperationException(
            "add(int, DataValue) is not implemetned");
    }
}
