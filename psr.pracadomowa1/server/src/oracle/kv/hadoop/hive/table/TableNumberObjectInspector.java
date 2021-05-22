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

import java.math.BigDecimal;
import java.math.BigInteger;

import oracle.kv.table.FieldValue;

import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.JavaHiveDecimalObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.HiveDecimalUtils;

/**
 * The Hive ObjectInspector that is used to translate KVStore row fields
 * of type FieldDef.Type.NUMBER to Hive column type DECIMAL.
 */
public class TableNumberObjectInspector
                 extends JavaHiveDecimalObjectInspector {

    private static final int KV_HIVE_NUMBER_PRECISION =
                                 HiveDecimal.MAX_PRECISION;

    private static final int KV_HIVE_NUMBER_SCALE = HiveDecimal.MAX_SCALE;

    /*
     * Implementation note: with respect to the 'getPrimitiveJavaObject'
     * method defined by this ObjectInspector, when the Hive infrastructure
     * invokes that method during a given query, the data Object input to
     * that method may be an instance of FieldValue (specifically, a
     * NumberValue) or may be an instance of the corresponding Java class,
     * org.apache.hadoop.hive.common.type.HiveDecimal. As a result, the
     * 'getPrimitiveJavaObject' method is overridden with an implementation
     * that handles both cases.
     */

    private static HiveDecimal DEFAULT_VALUE =
        HiveDecimal.create(new BigDecimal(0.0D));

    TableNumberObjectInspector() {
        super(new DecimalTypeInfo(
                  KV_HIVE_NUMBER_PRECISION, KV_HIVE_NUMBER_SCALE));
    }

    @Override
    public HiveDecimal getPrimitiveJavaObject(Object o) {

        if (o == null) {
            return DEFAULT_VALUE;
        }

        if (o instanceof HiveDecimal) {

            return HiveDecimalUtils.enforcePrecisionScale(
                (HiveDecimal) o, (DecimalTypeInfo) typeInfo);

        } else if (o instanceof FieldValue) {

            if (((FieldValue) o).isNull()) {
                return DEFAULT_VALUE;
            }

            final BigDecimal kvNumber = (((FieldValue) o).asNumber()).get();

            /*
             * Note: the NoSQL RecordValue.putNumber method allows int and long
             *       to be stored as well as float, double, and BigDecimal.
             *       But when the Number value retrieved above corresponds
             *       to an int or long, HiveDecimal will store that value
             *       as NULL (because the scale of such a Number is 0).
             *
             *       To deal with this, determine whether the Number
             *       value stored in NoSQL was created from an int or long
             *       (contains no decimal point) and, if yes, convert
             *       the value to a BigDecimal with the same non-zero scale
             *       as that used by the constructor of this class. This
             *       will cause queries to use a uniform format for each
             *       Number field in a given table.
             */
            final String kvNmbrStr = kvNumber.toPlainString();

            boolean nmbrIsIntLong = false;
            if (kvNmbrStr != null) {
                nmbrIsIntLong = !kvNmbrStr.contains(".");
            }

            if (nmbrIsIntLong) {

                /* Convert to BigDecimal with same scale used as float, etc. */
                return  HiveDecimal.create(
                    new BigDecimal(
                        new BigInteger(kvNmbrStr), KV_HIVE_NUMBER_SCALE));
            }

            /* Not int or long. Do what JavaHiveDecimalObjectInspector does. */
            return HiveDecimalUtils.enforcePrecisionScale(
                     HiveDecimal.create(kvNumber), (DecimalTypeInfo) typeInfo);
        }
        throw new IllegalArgumentException(
            "invalid object type: must be HiveDecimal or NumberValue");
    }
}
