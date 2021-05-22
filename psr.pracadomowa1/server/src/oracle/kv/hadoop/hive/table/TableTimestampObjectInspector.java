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

/*
 * NOTE: Use java.sql.Timestamp when integrating with Hive2.
 *       Use org.apache.hadoop.hive.common.type.Timestamp when integrating
 *       with Hive3.
 */
import java.sql.Timestamp; /* Hive2 */
/* import org.apache.hadoop.hive.common.type.Timestamp; Hive3 */

import oracle.kv.impl.api.table.TimestampUtils;
import oracle.kv.table.FieldValue;

import org.apache.hadoop.hive.serde2.io.TimestampWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.JavaTimestampObjectInspector;

/**
 * The Hive ObjectInspector that is used to translate KVStore row fields
 * of type FieldDef.Type.TIMESTAMP to Hive column type TIMESTAMP.
 */
public class TableTimestampObjectInspector
                 extends JavaTimestampObjectInspector {

    /*
     * Implementation note: with respect to the methods defined by
     * JavaTimestampObjectInspector ('get', 'getPrimitiveWritableObject',
     * and 'getPrimitiveJavaObject'), when the Hive infrastructure invokes
     * any of the methods during a given query, the data Object input to
     * the given method can be an instance of FieldValue (specifically,
     * a TimestampValue), an instance of java.sql.Timestamp, or even a
     * String value. As a result, the methods from this class which
     * override the corresponding methods in JavaTimestampObjectInspector
     * are each defined with implementations that handle all cases.
     */

    private static TimestampWritable DEFAULT_WRITABLE =
                       new TimestampWritable();

    private static Timestamp DEFAULT_VALUE = new Timestamp(0L); /* Hive2 */
    /* private static Timestamp DEFAULT_VALUE = new Timestamp();   Hive3 */

    TableTimestampObjectInspector() {
        super();
    }

    @Override
    public TimestampWritable getPrimitiveWritableObject(Object o) {

        if (o == null) {
            return DEFAULT_WRITABLE;
        }

        java.sql.Timestamp sqlTimestamp = null;

        if (o instanceof java.sql.Timestamp) {

            sqlTimestamp = (java.sql.Timestamp) o;

        } else if (o instanceof FieldValue) {

            if (((FieldValue) o).isNull()) {
                return DEFAULT_WRITABLE;
            }
            sqlTimestamp = (((FieldValue) o).asTimestamp()).get();

        } else if (o instanceof String) {

            sqlTimestamp = TimestampUtils.parseString((String) o);

        } else {

            throw new IllegalArgumentException(
                "invalid object type: must be java.sql.Timestamp " +
                "or TimestampValue");
        }

        return new TimestampWritable(sqlTimestamp);
    }

    @Override
    public Timestamp getPrimitiveJavaObject(Object o) {
        return get(o);
    }

    @Override
    public Timestamp get(Object o) {

        if (o == null) {
            return DEFAULT_VALUE;
        }

        java.sql.Timestamp sqlTimestamp = null;

        if (o instanceof java.sql.Timestamp) {

            sqlTimestamp = (java.sql.Timestamp) o;

        } else if (o instanceof FieldValue) {

            if (((FieldValue) o).isNull()) {
                return DEFAULT_VALUE;
            }
            sqlTimestamp = (((FieldValue) o).asTimestamp()).get();

        } else if (o instanceof String) {

            sqlTimestamp = TimestampUtils.parseString((String) o);

        } else {

            throw new IllegalArgumentException(
                "invalid object type: must be java.sql.Timestamp " +
                "or TimestampValue");
        }

        return sqlTimestamp; /* Hive2 */

        /* REMINDER: In the future, if we change from Hive2 to Hive3,
                     then we will need to do something like the following
                     to change this method to use the Hive3-defined Timestamp
                     class instead of java.sql.Timestamp:

        final long epochMilli = sqlTimestamp.getTime();
        final int nanos = sqlTimestamp.getNanos();
        final Timestamp retVal = new Timestamp();
        retVal.setTimeInMillis(epochMilli, nanos);

        return retVal;
        */
    }
}
