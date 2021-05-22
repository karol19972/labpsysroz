/*-
 * Copyright (C) 2011, 2019 Oracle and/or its affiliates. All rights reserved.
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

package oracle.kv.hadoop.hive.table;

import oracle.kv.table.FieldValue;

import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.SettableStringObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.Text;

/**
 * The Hive ObjectInspector that is used to translate KVStore row fields
 * of type FieldDef.Type.JSON to Hive column type STRING.
 */
public class TableJsonObjectInspector
                 extends AbstractPrimitiveJavaObjectInspector
                 implements SettableStringObjectInspector {

    /*
     * Note: with respect to the 'get' methods defined by this ObjectInspector,
     * when the Hive infrastructure invokes those methods during a given
     * query, the data Object input to those methods may be an instance of
     * FieldValue or may be an instance of the corresponding Java class
     * (that is, a String). As a result, each such method must be prepared
     * to handle both cases.
     *
     * With respect to the 'create/set' methods, this class defaults to
     * the same behavior as the corresponding 'create/set' methods of the
     * JavaStringObjectInspector class; which always returns a Java String
     * representing the JSON document.
     */

    TableJsonObjectInspector() {
        super(TypeInfoFactory.stringTypeInfo);
    }

    @Override
    public Text getPrimitiveWritableObject(Object o) {
        return new Text(getPrimitiveJavaObject(o));
    }

    @Override
    public String getPrimitiveJavaObject(Object o) {

        if (o == null) {
            return TableFieldTypeEnum.TABLE_FIELD_UNKNOWN_TYPE.toString();
        }

        if (o instanceof FieldValue) {
            return ((FieldValue) o).toJsonString(false);
        }
        return o.toString();
    }

    @Override
    public Object create(Text value) {
        return value == null ? null : value.toString();
    }

    @Override
    public Object set(Object o, Text value) {
        return value == null ? null : value.toString();
    }

    @Override
    public Object create(String value) {
        return value;
    }

    @Override
    public Object set(Object o, String value) {
        return value;
    }
}
