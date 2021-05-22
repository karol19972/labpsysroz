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

package oracle.kv.impl.streamservice.MRT;

import static oracle.kv.impl.systables.StreamRequestDesc.COL_REQUEST_TYPE;
import static oracle.kv.impl.util.SerialVersion.CURRENT;
import static oracle.kv.impl.util.SerializationUtil.readNonNullString;
import static oracle.kv.impl.util.SerializationUtil.readPackedInt;
import static oracle.kv.impl.util.SerializationUtil.readPackedLong;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullString;
import static oracle.kv.impl.util.SerializationUtil.writePackedInt;
import static oracle.kv.impl.util.SerializationUtil.writePackedLong;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.streamservice.ServiceMessage;
import oracle.kv.table.Row;
import oracle.kv.table.Table;

/**
 * Multi-region service request messages.
 */
public abstract class Request extends ServiceMessage {

    /**
     * Multi-region service request types. New types must be added to the end of
     * this enum to maintain compatibility.
     */
    public enum Type {
        CREATE_TABLE,       /* Create a table */
        UPDATE_TABLE,       /* Update a table */
        DROP_TABLE,         /* Drop a table */
        CREATE_REGION,      /* Create a region */
        DROP_REGION;        /* Drop a region */

        private static final Type[] VALUES = values();

        private static Type getType(Row row) {
            final int ord = row.get(COL_REQUEST_TYPE).asInteger().get();
            return VALUES[ord];
        }
    }

    /**
     * Returns a request object from the specified row. If row is null then
     * null is returned.
     */
    static Request getFromRow(Row row) throws IOException {
        if (row == null) {
            return null;
        }
        switch (Type.getType(row)) {
            case CREATE_TABLE : return new CreateTable(row);
            case UPDATE_TABLE : return new UpdateTable(row);
            case DROP_TABLE : return new DropTable(row);
            case CREATE_REGION : return new CreateRegion(row);
            case DROP_REGION : return new DropRegion(row);
            default : throw new IllegalStateException("unreachable");
        }
    }

    private final Type type;

    protected Request(int requestId, Type type) {
        super(ServiceType.MRT, requestId);
        assert type != null;
        this.type = type;
    }

    protected Request(Row row) {
        super(row);
        if (!getServiceType().equals(ServiceType.MRT)) {
            throw new IllegalStateException("Row is not a MRT request");
        }
        type = Type.getType(row);
    }

    /**
     * Gets the type of this message.
     */
    public Type getType() {
        return type;
    }

    @Override
    protected Row toRow(Table requestTable) throws IOException {
        final Row row = super.toRow(requestTable);
        row.put(COL_REQUEST_TYPE, type.ordinal());
        return row;
    }

    /**
     * Create table request. This message indicates that a multi-region table
     * has been created.
     * Response semantics:
     *      SUCCESS     - update successful, request terminated
     *      ERROR       - update failed, request terminated
     */
    public static class CreateTable extends Request {

        private final int seqNum;
        private final TableImpl table;

        public CreateTable(int requestId, TableImpl table, int seqNum) {
            super(requestId, Type.CREATE_TABLE);
            assert table.isTop();
            this.table = table;
            this.seqNum = seqNum;
        }

        private CreateTable(Row row) throws IOException {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            final DataInput in = new DataInputStream(bais);
            final short serialVersion = in.readShort();
            seqNum = readPackedInt(in);
            table = new TableImpl(in, serialVersion, null);
        }

        /**
         * Gets the metadata sequence number associated with the table create.
         */
        public int getMetadataSeqNum() {
            return seqNum;
        }

        /**
         * Gets the new table instance.
         */
        public TableImpl getTable() {
            return table;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutput out = new DataOutputStream(baos);
            out.writeShort(CURRENT);
            writePackedInt(out, seqNum);
            table.writeFastExternal(out, CURRENT);
            return baos.toByteArray();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", seqNum=").append(seqNum);
            sb.append(", tableId=").append(table.getId());
            return sb;
        }
    }

    /**
     * Update table request. This message indicates that a multi-region table
     * has been modified.
     * Response semantics:
     *      SUCCESS     - update successful, request terminated
     *      ERROR       - update failed, request terminated
     */
    public static class UpdateTable extends Request {

        private final int seqNum;
        private final TableImpl table;

        /**
         * Constructs a table update request.
         */
        public UpdateTable(int requestId, TableImpl table, int seqNum) {
            super(requestId, Type.UPDATE_TABLE);
            assert table.isTop();
            this.table = table;
            this.seqNum = seqNum;
        }

        private UpdateTable(Row row) throws IOException {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            final DataInput in = new DataInputStream(bais);
            final short serialVersion = in.readShort();
            seqNum = readPackedInt(in);
            table = new TableImpl(in, serialVersion, null);
        }

        /**
         * Gets the metadata sequence number associated with the table update.
         */
        public int getMetadataSeqNum() {
            return seqNum;
        }

        /**
         * Gets the updated table instance.
         */
        public TableImpl getTable() {
            return table;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutput out = new DataOutputStream(baos);
            out.writeShort(CURRENT);
            writePackedInt(out, seqNum);
            table.writeFastExternal(out, CURRENT);
            return baos.toByteArray();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", seqNum=").append(seqNum);
            sb.append(", tableId=").append(table.getId());
            return sb;
        }
    }

    /**
     * Drop table request. This message indicates that a multi-region table
     * has been dropped.
     * Response semantics:
     *      Any response terminates request
     */
    public static class DropTable extends Request {

        private final int seqNum;
        private final long tableId;
        private final String tableName;

        /**
         * Constructs a drop table request.
         */
        public DropTable(int requestId, long tableId,
                         String tableName, int seqNum) {
            super(requestId, Type.DROP_TABLE);
            assert tableName != null;
            this.seqNum = seqNum;
            this.tableId = tableId;
            this.tableName = tableName;
        }

        private DropTable(Row row) throws IOException {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            final DataInput in = new DataInputStream(bais);
            final short serialVersion = in.readShort();
            seqNum = readPackedInt(in);
            tableId = readPackedLong(in);
            tableName = readNonNullString(in, serialVersion);
        }

        /**
         * Gets the metadata sequence number associated with the table drop.
         */
        public int getMetadataSeqNum() {
            return seqNum;
        }

        /**
         * Gets the table name of dropped table
         */
        public String getTableName() {
            return tableName;
        }

        /**
         * Gets the ID of the dropped table.
         */
        public long getTableId() {
            return tableId;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutput out = new DataOutputStream(baos);
            out.writeShort(CURRENT);
            writePackedInt(out, seqNum);
            writePackedLong(out, tableId);
            writeNonNullString(out, CURRENT, tableName);
            return baos.toByteArray();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", seqNum=").append(seqNum);
            sb.append(", tableId=").append(tableId);
            return sb;
        }
    }

    /**
     * Create a region. This message indicates that a region has been created.
     *
     * Response semantics:
     *      Any response terminates request
     */
    public static class CreateRegion extends Request {

        private final int seqNum;
        private final Region region;

        /**
         * Constructs a drop table request.
         */
        public CreateRegion(int requestId, Region region, int seqNum) {
            super(requestId, Type.CREATE_REGION);
            this.seqNum = seqNum;
            this.region = region;
        }

        private CreateRegion(Row row) throws IOException {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            final DataInput in = new DataInputStream(bais);
            final short serialVersion = in.readShort();
            seqNum = readPackedInt(in);
            region = new Region(in, serialVersion);
        }

        /**
         * Gets the metadata sequence number associated with the table drop.
         */
        public int getMetadataSeqNum() {
            return seqNum;
        }

        /**
         * Gets the region.
         */
        public Region  getRegion() {
            return region;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutput out = new DataOutputStream(baos);
            out.writeShort(CURRENT);
            writePackedInt(out, seqNum);
            region.writeFastExternal(out, CURRENT);
            return baos.toByteArray();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", seqNum=").append(seqNum);
            sb.append(", region=").append(region.toString());
            return sb;
        }
    }

    /**
     * Drop a region. This message indicates that a region has been dropped.
     *
     * Response semantics:
     *      Any response terminates request
     */
    public static class DropRegion extends Request {

        private final int seqNum;
        private final int regionId;

        /**
         * Constructs a drop table request.
         */
        public DropRegion(int requestId, int regionId, int seqNum) {
            super(requestId, Type.DROP_REGION);
            Region.checkId(regionId);
            this.seqNum = seqNum;
            this.regionId = regionId;
        }

        private DropRegion(Row row) throws IOException {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            final DataInput in = new DataInputStream(bais);
            in.readShort(); /*serialVersion*/
            seqNum = readPackedInt(in);
            regionId = readPackedInt(in);
        }

        /**
         * Gets the metadata sequence number associated with the table drop.
         */
        public int getMetadataSeqNum() {
            return seqNum;
        }

        /**
         * Gets the region.
         */
        public int getRegionId() {
            return regionId;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutput out = new DataOutputStream(baos);
            out.writeShort(CURRENT);
            writePackedInt(out, seqNum);
            writePackedInt(out, regionId);
            return baos.toByteArray();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", seqNum=").append(seqNum);
            sb.append(", regionId=").append(regionId);
            return sb;
        }
    }
}
