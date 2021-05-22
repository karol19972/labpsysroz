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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;

import oracle.kv.table.NumberDef;

/**
 * NumberDefImpl implements the NumberDef interface.
 */
public class NumberDefImpl extends FieldDefImpl implements NumberDef {

    private static final long serialVersionUID = 1L;

    NumberDefImpl(String description) {
        super(Type.NUMBER, description);
    }

    NumberDefImpl() {
        super(Type.NUMBER);
    }

    private NumberDefImpl(NumberDefImpl impl) {
        super(impl);
    }

    /**
     * Constructor for FastExternalizable
     */
    NumberDefImpl(DataInput in, short serialVersion) throws IOException {
        super(in, serialVersion, Type.NUMBER);
    }

    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {
        super.writeFastExternal(out, serialVersion);
    }

    /*
     * Public api methods from Object and FieldDef
     */

    @Override
    public NumberDefImpl clone() {
        return new NumberDefImpl(this);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NumberDefImpl;
    }

    @Override
    public boolean isValidKeyField() {
        return true;
    }

    /*
     * BigDecimal can be an indexed field. A binary format exists that sorts
     * properly.
     */
    @Override
    public boolean isValidIndexField() {
        return true;
    }

    @Override
    public NumberDef asNumber() {
        return this;
    }

    @Override
    public NumberValueImpl createNumber(int value) {
        return new NumberValueImpl(value);
    }

    @Override
    public NumberValueImpl createNumber(long value) {
        return new NumberValueImpl(value);
    }

    @Override
    public NumberValueImpl createNumber(float value) {
        return new NumberValueImpl(BigDecimal.valueOf(value));
    }

    @Override
    public NumberValueImpl createNumber(double value) {
        return new NumberValueImpl(BigDecimal.valueOf(value));
    }

    @Override
    public NumberValueImpl createNumber(BigDecimal value) {
        return new NumberValueImpl(value);
    }

    @Override
    public NumberValueImpl createNumberFromIndexField(String value) {
        return new NumberValueImpl(value);
    }

    @Override
    public NumberValueImpl createNumber(String value) {
        return new NumberValueImpl(new BigDecimal(value));
    }

    @Override
    NumberValueImpl createNumber(byte[] value) {
        return new NumberValueImpl(value);
    }

    public void validateValue(byte[] value) {
        NumberUtils.validateValue(value);
    }

    /*
     * FieldDefImpl internal api methods
     */

    @Override
    public boolean isSubtype(FieldDefImpl superType) {

        if (superType.isNumber() ||
            superType.isAny() ||
            superType.isAnyJsonAtomic() ||
            superType.isAnyAtomic() ||
            superType.isJson()) {
            return true;
        }

        return false;
    }
}
