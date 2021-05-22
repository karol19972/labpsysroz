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

import static oracle.kv.impl.util.ObjectUtil.checkNull;
import static oracle.kv.impl.util.SerialVersion.PUT_BATCH_OVERWRITE;
import static oracle.kv.impl.util.SerializationUtil.readNonNullSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.readSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.writeArrayLength;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullCollection;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oracle.kv.impl.api.bulk.BulkPut.KVPair;
import oracle.kv.impl.util.SerializationUtil;

/**
 * A put-batch operation.
 *
 * @see #writeFastExternal FastExternalizable format
 */
public class PutBatch extends MultiKeyOperation {

    private final List<KVPair> kvPairs;
    private final long[] tableIds;
    private final boolean overwrite;

    public PutBatch(List<KVPair> le, long[] tableIds, boolean overwrite) {
        super(OpCode.PUT_BATCH, null, null, null);
        checkNull("le", le);
        for (final KVPair element : le) {
            checkNull("le element", element);
        }
        this.kvPairs = le;
        this.tableIds = tableIds;
        this.overwrite = overwrite;
    }

    PutBatch(DataInput in, short serialVersion) throws IOException {

        super(OpCode.PUT_BATCH, in, serialVersion);
        final int kvPairCount = readNonNullSequenceLength(in);
        kvPairs = new ArrayList<>(kvPairCount);

        for (int i = 0; i < kvPairCount; i++) {
            kvPairs.add(new KVPair(in, serialVersion));
        }

        final int tableIdCount = readSequenceLength(in);
        if (tableIdCount == -1) {
            tableIds = null;
        } else {
            tableIds = new long[tableIdCount];
            for (int i = 0; i < tableIdCount; i++) {
                tableIds[i] = in.readLong();
            }
        }

        if (serialVersion >= PUT_BATCH_OVERWRITE) {
            overwrite = in.readBoolean();
        } else {
            overwrite = false;
        }
    }

    /**
     * Writes this object to the output stream.  Format:
     * <ol>
     * <li> ({@link MultiKeyOperation} {@code super}
     * <li> ({@link SerializationUtil#writeNonNullCollection non-null
     *      collection}) {@link #getKvPairs kvPairs}
     * <li> ({@link SerializationUtil#writeArrayLength sequence length})
     *      <i>number of table IDs</i>
     * <li> <i>[Optional]</i> ({@link DataOutput#writeLong long}{@code []})
     *      <i>table IDs</i>
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {

        super.writeFastExternal(out, serialVersion);
        writeNonNullCollection(out, serialVersion, kvPairs);
        writeArrayLength(out, tableIds);

        if (tableIds != null) {
            for (final long tableId : tableIds) {
                out.writeLong(tableId);
            }
        }

        if (serialVersion >= PUT_BATCH_OVERWRITE) {
            out.writeBoolean(overwrite);
        }
    }

    List<KVPair> getKvPairs() {
        return kvPairs;
    }

    @Override
    public long[] getTableIds() {
        return tableIds;
    }

    @Override
    public boolean performsWrite() {
        return true;
    }

    boolean getOverwrite() {
        return overwrite;
    }
}
