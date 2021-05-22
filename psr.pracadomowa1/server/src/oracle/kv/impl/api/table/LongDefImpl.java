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

import static oracle.kv.impl.util.SerializationUtil.readPackedInt;
import static oracle.kv.impl.util.SerializationUtil.readPackedLong;
import static oracle.kv.impl.util.SerializationUtil.writePackedInt;
import static oracle.kv.impl.util.SerializationUtil.writePackedLong;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import oracle.kv.impl.util.SerializationUtil;   /* for Javadoc */
import oracle.kv.impl.util.SortableString;
import oracle.kv.table.LongDef;

/**
 * LongDefImpl implements the LongDef interface.
 */
public class LongDefImpl extends FieldDefImpl implements LongDef {

    private static final long serialVersionUID = 1L;

    /*
     * min and max are inclusive
     */
    private Long min;
    private Long max;
    private int encodingLength;

    LongDefImpl(String description, Long min, Long max) {
        super(Type.LONG, description);
        this.min = min;
        this.max = max;
        validate();
    }

    LongDefImpl(String description) {
        this(description, null, null);
    }

    LongDefImpl() {
        super(Type.LONG);
        min = null;
        max = null;
        encodingLength = 0;
    }

    private LongDefImpl(LongDefImpl impl) {
        super(impl);
        min = impl.min;
        max = impl.max;
        encodingLength = impl.encodingLength;
    }

    /**
     * Constructor for FastExternalizable
     */
    LongDefImpl(DataInput in, short serialVersion) throws IOException {
        super(in, serialVersion, Type.LONG);
        min = readLongOrNull(in);
        max = readLongOrNull(in);
        encodingLength = readPackedInt(in);
    }

     private Long readLongOrNull(DataInput in) throws IOException {
        return in.readBoolean() ? readPackedLong(in) : null;
    }

    /**
     * Writes this object to the output stream. Format:
     *
     * <ol>
     * <li> ({@link FieldDefImpl}) {@code super}
     * <li> ({@code boolean}) <i> min != null </i>
     * <li> <i>[Optional]</i>
     *                  ({@link SerializationUtil#writePackedLong packed long})
     *                  {@code min} // if min != null
     * <li> ({@code boolean}) <i> max != null </i>
     * <li> <i>[Optional]</i>
     *                  ({@link SerializationUtil#writePackedLong packed long})
     *                  {@code max} // if max != null
     * <li> ({@link SerializationUtil#writePackedInt packed int})
     *      {@code encodingLength}
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {
        super.writeFastExternal(out, serialVersion);
        writeLongOrNull(out, min);
        writeLongOrNull(out, max);
        writePackedInt(out, encodingLength);
    }

    private void writeLongOrNull(DataOutput out, Long value)
            throws IOException {
        if (value == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            writePackedLong(out, value);
        }
    }

    /*
     * Public api methods from Object and FieldDef
     */

    @Override
    public LongDefImpl clone() {

        if (this == FieldDefImpl.longDef) {
            return this;
        }

        return new LongDefImpl(this);
    }

    @Override
    public int hashCode() {
        return super.hashCode() +
            (min != null ? min.hashCode() : 0) +
            (max != null ? max.hashCode() : 0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LongDefImpl;
    }

    @Override
    public boolean isValidKeyField() {
        return true;
    }

    @Override
    public boolean isValidIndexField() {
        return true;
    }

    @Override
    public LongDef asLong() {
        return this;
    }

    /*
     * Public api methods from LongDef
     */

    @Override
    public Long getMin() {
        return min;
    }

    @Override
    public Long getMax() {
        return max;
    }

    /*
     * FieldDefImpl internal api methods
     */

    @Override
    int getEncodingLength() {
        return encodingLength;
    }

    @Override
    public boolean hasMin() {
        return min != null;
    }

    @Override
    public boolean hasMax() {
        return max != null;
    }

    @Override
    public boolean isSubtype(FieldDefImpl superType) {

        if (superType.isLong() ||
            superType.isNumber() ||
            superType.isAny() ||
            superType.isAnyJsonAtomic() ||
            superType.isAnyAtomic() ||
            superType.isJson()) {
            return true;
        }

        return false;
    }

    @Override
    public LongValueImpl createLong(long value) {

        return (hasMin() || hasMax() ?
                new LongRangeValue(value, this) :
                new LongValueImpl(value));
    }

    @Override
    LongValueImpl createLong(String value) {

        return (hasMin() || hasMax() ?
                new LongRangeValue(value, this) :
                new LongValueImpl(value));
    }

    /*
     * Local methods
     */

    private void validate() {

        if (min != null && max != null) {
            if (min > max) {
                throw new IllegalArgumentException
                    ("Invalid minimum or maximum value");
            }
        }
        encodingLength = SortableString.encodingLength(min, max);
    }

    /**
     * Validates the value against the range if one exists.
     * min/max are inclusive.
     */
    void validateValue(long val) {

        if ((min != null && val < min) || (max != null && val > max)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Value, ");
            sb.append(val);
            sb.append(", is outside of the allowed range");
            throw new IllegalArgumentException(sb.toString());
        }
    }
}
