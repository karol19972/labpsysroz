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

package oracle.kv.impl.api.ops;

import static oracle.kv.impl.util.SerialVersion.PUT_RESOLVE_EXPIRATION_TIME;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import oracle.kv.ReturnValueVersion.Choice;
import oracle.kv.Value;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.util.SerialVersion;

import com.sleepycat.util.PackedInteger;

public class PutResolve extends Put {
    private final boolean isTombstone;
    private final long timestamp;

    /* expiration time in system time */
    private final long expirationTimeMs;

    /**
     * Constructs a put-resolve operation with a table id.
     */
    public PutResolve(byte[] keyBytes,
                      Value value,
                      long tableId,
                      Choice prevValChoice,
                      long expirationTimeMs,
                      boolean updateTTL,
                      boolean isTombstone,
                      long timestamp) {
        super(OpCode.PUT_RESOLVE, keyBytes, value,
              prevValChoice, tableId,
              null, /* wont use that TTL, use expiration time instead */
              updateTTL);
        this.isTombstone = isTombstone;
        this.timestamp = timestamp;
        this.expirationTimeMs = expirationTimeMs;
    }

    /**
     * FastExternalizable constructor.  Must call superclass constructor first
     * to read common elements.
     */
    PutResolve(DataInput in, short serialVersion)
        throws IOException {

        super(OpCode.PUT_RESOLVE, in, serialVersion);
        isTombstone = in.readBoolean();
        timestamp = in.readLong();
        if (includeExpirationTime(serialVersion)) {
            expirationTimeMs = in.readLong();
        } else {
            expirationTimeMs = 0;
        }
    }

    @Override
    public boolean performsRead() {
        /* Override the conditional return in Put */
        return true;
    }

    @Override
    public byte[] getValueBytes() {
        int regionId = requestValue.getRegionId();
        Region.checkRemoteId(regionId);
        return requestValue.getBytes();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRegionId() {
        return requestValue.getRegionId();
    }

    public long getExpirationTimeMs() {
        return expirationTimeMs;
    }

    /**
     * Returns the table version from the value byte[] if put, or 0 if delete
     *
     * @return table version for put or 0 for delete
     */
    int getTableVer() {
        final byte[] valueBytes = getValueBytes();
        /* byte[0] is format */
        final Value.Format format = Value.Format.fromFirstByte(valueBytes[0]);
        /* should always pass, cheap check for safety */
        if (format != Value.Format.MULTI_REGION_TABLE) {
            throw new IllegalArgumentException("Invalid format=" + format);
        }

        /* skip bytes of region id */
        final int regionIdLen = PackedInteger.getReadIntLength(valueBytes, 1);
        final int offset = regionIdLen + 1;

        if (valueBytes.length == offset) {
            /* at the end of byte[], there is no table version for a delete */
            return 0;
        }

        /* for a put, next byte is table version */
        return valueBytes[offset];
    }

    public boolean isTombstone() {
        return isTombstone;
    }

    /**
     * Writes this object to the output stream. Format for {@code
     * serialVersion} {@link SerialVersion#MULTI_REGION_TABLE_VERSION} or
     * greater:
     * <ol>
     * <li> ({@link Put}) {@code super}
     * <li> ({@code boolean}) {@code isTombstone}
     * <li> ({@code long}) {@code timestamp}
     * <li> ({@code long}) {@code expirationTimeMs} for {@code serialVersion}
     * {@link SerialVersion#PUT_RESOLVE_EXPIRATION_TIME} or greater
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {
        super.writeFastExternal(out, serialVersion);
        out.writeBoolean(isTombstone);
        out.writeLong(timestamp);
        if (includeExpirationTime(serialVersion)) {
            out.writeLong(expirationTimeMs);
        }
    }

    private static boolean includeExpirationTime(short serialVersion) {
        return serialVersion >= PUT_RESOLVE_EXPIRATION_TIME;
    }
}
