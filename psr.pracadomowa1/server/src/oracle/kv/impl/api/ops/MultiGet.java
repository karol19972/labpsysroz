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

import oracle.kv.Depth;
import oracle.kv.KeyRange;
import oracle.kv.impl.util.SerialVersion;

/**
 * A multi-get operation.
 *
 * @see #writeFastExternal FastExternalizable format
 */
public class MultiGet extends MultiKeyOperation {
    private final boolean excludeTombstones;

    /**
     * Construct a multi-get operation.
     */
    public MultiGet(byte[] parentKey,
                    KeyRange subRange,
                    Depth depth,
                    boolean excludeTombstones) {
        super(OpCode.MULTI_GET, parentKey, subRange, depth);
        this.excludeTombstones = excludeTombstones;
    }

    /**
     * FastExternalizable constructor.  Must call superclass constructor first
     * to read common elements.
     */
    MultiGet(DataInput in, short serialVersion)
        throws IOException {

        super(OpCode.MULTI_GET, in, serialVersion);
        if (serialVersion >= SerialVersion.MULTI_REGION_TABLE_VERSION) {
            this.excludeTombstones = in.readBoolean();
        } else {
            this.excludeTombstones = true;
        }
    }

    /**
     * Writes this object to the output stream. Format:
     * <ol>
     * <li> ({@link MultiKeyOperation}) {@code super}
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
        if (serialVersion >= SerialVersion.MULTI_REGION_TABLE_VERSION) {
            out.writeBoolean(excludeTombstones);
        }
    }

    boolean getExcludeTombstones() {
        return excludeTombstones;
    }
}
