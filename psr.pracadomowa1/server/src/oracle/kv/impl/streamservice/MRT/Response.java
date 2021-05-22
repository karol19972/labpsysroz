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

import static oracle.kv.impl.systables.StreamResponseDesc.COL_RESPONSE_TYPE;

import java.io.IOException;

import oracle.kv.impl.streamservice.ServiceMessage;
import oracle.kv.table.Row;
import oracle.kv.table.Table;

/**
 * Messages for the multi-region-table service.
 */
public abstract class Response extends ServiceMessage {

    /**
     * Multi-region service response types. New types must be added to the end of
     * this enum to maintain compatibility.
     */
    public enum Type {
        SUCCESS,        /* Request succefully handled */
        ERROR;          /* There was an error handling the request */

        private static final Type[] VALUES = values();

        private static Type getType(Row row) {
            final int ord = row.get(COL_RESPONSE_TYPE).asInteger().get();
            return VALUES[ord];
        }
    }

    /**
     * Returns a response object from the specified row. If row is null then
     * null is returned.
     */
    public static Response getFromRow(Row row) {
        if (row == null) {
            return null;
        }
        switch (Type.getType(row)) {
            case SUCCESS : return new Success(row);
            case ERROR : return new Error(row);
            default : throw new IllegalStateException("unreachable");
        }
    }

    private final Type type;

    protected Response(int requestId, Type type) {
        super(ServiceType.MRT, requestId);
        this.type = type;
    }

    protected Response(Row row) {
        super(row);
        if (!getServiceType().equals(ServiceType.MRT)) {
            throw new IllegalStateException("Row is not a MRT response");
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
        row.put(COL_RESPONSE_TYPE, type.ordinal());
        return row;
    }

    /**
     * Success response. Indicates that the service was successful in handling
     * the request.
     */
    public static class Success extends Response {

        public Success(int requestId) {
            super(requestId, Type.SUCCESS);
        }

        private Success(Row row) {
            super(row);
        }
    }

    /**
     * Error response. Indicates that the service failed to handle the
     * request.
     */
    public static class Error extends Response {

        private final String message;

        public Error(int requestId, String message) {
            super(requestId, Type.ERROR);
            this.message = message;
        }

        private Error(Row row) {
            super(row);
            final byte[] payload = getPayloadFromRow(row);
            message = new String(payload);
        }

        /**
         * Gets the error message.
         */
        public String getErrorMessage() {
            return message;
        }

        @Override
        protected byte[] getPayload() throws IOException {
            return message.getBytes();
        }

        @Override
        protected StringBuilder getToString(StringBuilder sb) {
            sb.append(", ").append(message);
            return sb;
        }
    }
}
