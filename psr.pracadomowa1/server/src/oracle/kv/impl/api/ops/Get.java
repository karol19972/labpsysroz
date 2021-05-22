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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import oracle.kv.impl.util.SerialVersion;

/**
 * A get operation gets a value from the KV Store.
 *
 * @see #writeFastExternal FastExternalizable format
 */
public class Get extends SingleKeyOperation {

    /**
     * Table operations include the table id.  0 means no table.
     */
    private final long tableId;

    private final boolean excludeTombstones;

    /**
     * Construct a get operation.
     */
    public Get(byte[] keyBytes) {
        this(keyBytes, 0, true);
    }

    /**
     * Construct a get operation with a table id and a boolean for whether to
     * exclude tombstones.
     */
    public Get(byte[] keyBytes,
               long tableId,
               boolean excludeTombstones) {
        super(OpCode.GET, keyBytes);
        this.tableId = tableId;
        this.excludeTombstones = excludeTombstones;
    }

    /**
     * Returns the tableId, which is 0 if this is not a table operation.
     */
    @Override
    public long getTableId() {
        return tableId;
    }

    /**
     * FastExternalizable constructor.  Must call superclass constructor first
     * to read common elements.
     */
    Get(DataInput in, short serialVersion)
        throws IOException {

        super(OpCode.GET, in, serialVersion);

        /*
         * Read table id.
         */
        tableId = in.readLong();
        if (serialVersion >= SerialVersion.MULTI_REGION_TABLE_VERSION) {
            excludeTombstones = in.readBoolean();
        } else {
            excludeTombstones = true;
        }
    }

    /**
     * Writes this object to the output stream.  Format:
     * <ol>
     * <li> ({@link SingleKeyOperation}) {@code super}
     * <li> ({@link DataOutput#writeLong long}) {@link #getTableId tableId}
     * <li> ({@link DataOutput#writeBoolean boolean})
     *      {@link #getExcludeTombstones excludeTombstones} // for {@code
     *      serialVersion} {@link SerialVersion#MULTI_REGION_TABLE_VERSION}
     *      or greater
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {

        super.writeFastExternal(out, serialVersion);

        /*
         * Write the table id.  If this is not a table operation the
         * id will be 0.
         */
        out.writeLong(tableId);
        if (serialVersion >= SerialVersion.MULTI_REGION_TABLE_VERSION) {
            out.writeBoolean(excludeTombstones);
        }

    }

    boolean getExcludeTombstones() {
        return excludeTombstones;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        if (tableId != 0) {
            sb.append(" Table Id ");
            sb.append(tableId);
        }
        return sb.toString();
    }
}
