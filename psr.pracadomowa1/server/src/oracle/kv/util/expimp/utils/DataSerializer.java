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

package oracle.kv.util.expimp.utils;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import oracle.kv.Key;
import oracle.kv.KeyValueVersion;
import oracle.kv.Value;
import oracle.kv.Value.Format;
import oracle.kv.ValueVersion;
import oracle.kv.Version;
import oracle.kv.impl.api.KeySerializer;
import oracle.kv.impl.api.KeyValueVersionInternal;
import oracle.kv.impl.api.table.DDLGenerator;
import oracle.kv.impl.api.table.RecordDefImpl;
import oracle.kv.impl.api.table.RegionMapper;
import oracle.kv.impl.api.table.RowImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.table.RecordDef;
import oracle.kv.table.Table;

import com.sleepycat.util.PackedInteger;

/**
 * This class encapsulates methods to serialize/deserialize table schema
 * entries and table, non-format data.
 */
public class DataSerializer {

    /**
     * V1: the value format used prior to v4.5
     */
    @SuppressWarnings("unused")
    private static final int V1 = 1;

    /**
     * V2: Add value format to table data.
     */
    private static final int V2 = 2;
    public static final int CURRENT_VERSION = V2;

    /*
     * Identifiers for the data retrieved from the store iterator.
     */
    public static final String tableDataIdentifier = "T";
    public static final String avroDataIdentifier = "A";
    public static final String noneDataIdentifier = "N";
    public static final String lobDataReferenceIdentifier = "L";

    public static final Charset UTF8 = StandardCharsets.UTF_8;

    private static final String SPACE = " ";
    private static final byte SPACE_BYTE = SPACE.getBytes(UTF8)[0];

    /* Use by test only */
    private static int testVersion = CURRENT_VERSION;

    /**
     * Exports the table schema record with below format:
     *
     * [table ddl statement \n[index ddl statement \n]*]
     */
    public static void writeTableSchemaData(OutputStream out,
                                            Table table,
                                            RegionMapper regionMapper) {

        DDLGenerator gen = new DDLGenerator(table,
                                            true /* withIfNotExist */,
                                            regionMapper);
        write(out, gen.getDDL().getBytes(UTF8));
        write(out, new byte[] {'\n'});
        for (String ddl : gen.getAllIndexDDL()) {
            write(out, ddl.getBytes(UTF8));
            write(out, new byte[] {'\n'});
        }
    }

    /**
     * Exports the table schema record with below format:
     *
     * [T, TableFullNamespaceName: TableSchemaJsonString \n]
     */
    public static void writeTableSchemaData(OutputStream out,
                                            String identifier,
                                            Table table,
                                            RegionMapper regionMapper) {
        String tableJson = ((TableImpl)table).toJsonString(false, regionMapper);
        String tableFullName = table.getFullNamespaceName();

        String tableSchema = identifier + ", " +
                             tableFullName + ": " +
                             tableJson + "\n";
        write(out, tableSchema.getBytes(UTF8));
    }

    public static void skipAvroFormatData(DataInputStream dis, String fileChunk)
        throws IOException {

        readNumber(dis, "schemaId", fileChunk);
        readBytesWithLength(dis, "key", fileChunk);
        readBytesWithLength(dis, "value", fileChunk);
        readBytesWithLength(dis, "checkSum", fileChunk);
    }

    /**
     * Exports the None format record and LOB file reference.
     * Key and value are exported as bytes.
     *
     * Format of the exported None format record bytes:
     * [N RecordKeyLength RecordKeyBytes RecordValueLength RecordValueBytes
     * ChecksumBytesLength ChecksumBytes] where 'N' denotes this is a
     * None format record.
     *
     * Format of the exported LOB file reference:
     * [L LOBKeyLength LOBKeyBytes LOBFileNameLength LOBFileName
     * ChecksumBytesLength ChecksumBytes] where 'L' signifies this is a LOB
     * file reference.
     */
    public static void writeNoneFormatData(OutputStream out,
                                           String identifier,
                                           Key key,
                                           byte[] valueBytes) {

        /* identifier */
        writeIndentifier(out, identifier);

        /* keyLength, keyBytes */
        byte[] keyBytes = key.toByteArray();
        writeBytesWithLength(out, keyBytes);

        /* valueLength, valueBytes */
        writeBytesWithLength(out, valueBytes);

        /*
         * Calculate the checksum of the record bytes.
         */
        long checkSum = getChecksum(keyBytes, valueBytes);

        /*
         * Convert the checksum in long format to binary using PackedInteger
         */
        byte[] checkSumPacked =
            new byte[PackedInteger.getWriteLongLength(checkSum)];
        PackedInteger.writeLong(checkSumPacked, 0, checkSum);
        /* checkSumLength, checkSumPacked */
        writeBytesWithLength(out, checkSumPacked);
    }

    public static KeyValueBytes readNonFormatData(DataInputStream dis,
                                                  String fileChunk)
        throws IOException {

        byte[] keyBytes = readBytesWithLength(dis, "key", fileChunk);
        byte[] valueBytes = readBytesWithLength(dis, "value", fileChunk);
        byte[] checksumBytes = readBytesWithLength(dis, "checkSum", fileChunk);

        validateCheckSum(checksumBytes, keyBytes, valueBytes);

        return KeyValueBytes.createNonFormatData(keyBytes, valueBytes);
    }

    public static KeyValueBytes readLobData(DataInputStream dis,
                                            String fileChunk)
        throws IOException {

        byte[] keyBytes = readBytesWithLength(dis, "key", fileChunk);
        byte[] valueBytes = readBytesWithLength(dis, "lobFileName", fileChunk);
        byte[] checksumBytes = readBytesWithLength(dis, "checkSum", fileChunk);

        validateCheckSum(checksumBytes, keyBytes, valueBytes);
        return KeyValueBytes.createLobData(keyBytes, valueBytes);
    }

    public static KeyValueBytes readKVData(DataInputStream dis,
                                           String fileChunk) {
        while(true) {
            try {
                String id;
                try {
                    id = readIdentifier(dis);
                } catch (EOFException eof) {
                    /* End of InputStream, return null;*/
                    return null;
                }
                if (id.equals(avroDataIdentifier)) {
                    skipAvroFormatData(dis, fileChunk);
                    continue;
                } else if (id.equals(noneDataIdentifier)) {
                    return readNonFormatData(dis, fileChunk);
                } else if (id.equals(lobDataReferenceIdentifier)) {
                    return readLobData(dis, fileChunk);
                }
            } catch (IOException ioe) {
                raiseCurruptedDataError(ioe.getMessage(), fileChunk, ioe);
            }
        }
    }


    /**
     * Exports the table record. Key and value are exported as bytes.
     *
     * Format of the exported Table record bytes:
     * [RecordKeyLength RecordKeyBytes RecordValueLength RecordValueBytes
     * ExpirationTimeBytesLength ExpirationTimeInBytes ChecksumBytesLength
     * ChecksumBytes]
     */
    public static void writeTableData(OutputStream out,
                                      KeySerializer keySerializer,
                                      TableImpl table,
                                      int exportTableVersion,
                                      Key key,
                                      Value value,
                                      Version version,
                                      long expirationTime) {

        byte[] keyBytes = keySerializer.toByteArray(key);
        byte[] valueBytes = value.getValue();
        Value.Format valueFormat = value.getFormat();

        /*
         * Empty values still need to be used because the current schema
         * may have added fields which need to be defaulted.
         */
        if (valueBytes.length == 0 ||
            (valueBytes[0] != exportTableVersion)) {
            RowImpl row = table.createRowFromKeyBytes(keyBytes);
            ValueVersion vv = new ValueVersion(value, version);
            createExportRowFromValueVersion(table, row, vv, exportTableVersion);

            Key rKey = row.getPrimaryKey(false);
            Value rValue = row.createValue();

            keyBytes = keySerializer.toByteArray(rKey);
            valueBytes = rValue.getValue();
            valueFormat = rValue.getFormat();
        }

        /* keyLength, keyBytes*/
        writeBytesWithLength(out, keyBytes);

        /* valueLength, valueBytes*/
        writeBytesWithLength(out, valueBytes);

        if (testVersion >= V2) {
            /* Value format */
            write(out, new byte[] {(byte)valueFormat.ordinal()});
        }

        /*
         * Convert row expiration time in milliseconds to hours
         */
        int expiryTimeInHours = (int)(expirationTime/(60 * 60 * 1000));
        byte[] expiryTimePacked = null;

        if (expiryTimeInHours > 0) {
            /*
             * Convert row expiration time in hours to binary using
             * PackedInteger. This allows the expiration time of record to
             * be stored more compactly in the export package.
             */
            expiryTimePacked =
                new byte[PackedInteger.getWriteIntLength(expiryTimeInHours)];
            PackedInteger.writeInt(expiryTimePacked, 0, expiryTimeInHours);
            /* expiryTimePacked length, expiryTimePacked */
            writeBytesWithLength(out, expiryTimePacked);
        } else {
            String zeroExpirationEntry = expiryTimeInHours + " ";
            /* zeroExpirationEntry */
            write(out, zeroExpirationEntry.getBytes(UTF8));
        }

        /*
         * Calculate the checksum of the record bytes
         */
        long checkSum = getChecksum(keyBytes, valueBytes);

        /*
         * Convert the checksum in long format to binary using PackedInteger
         */
        byte[] checkSumPacked =
            new byte[PackedInteger.getWriteLongLength(checkSum)];
        PackedInteger.writeLong(checkSumPacked, 0, checkSum);
        /* checkSumLength, checkSumPacked */
        writeBytesWithLength(out, checkSumPacked);
    }

    /**
     * Deserializes table data entry.
     */
    public static KeyValueBytes readTableData(DataInputStream dis,
                                              String fileChunk) {
        return readTableData(dis, fileChunk, CURRENT_VERSION);
    }

    public static KeyValueBytes readTableData(DataInputStream dis,
                                              String fileChunk,
                                              int serialVersion) {
        try {
            byte[] keyBytes = null;
            try {
                /* key bytes */
                keyBytes = readBytesWithLength(dis, "key", fileChunk);
            } catch (EOFException eof) {
                return null;
            }

            /* value bytes */
            byte[] valueBytes = readBytesWithLength(dis, "value", fileChunk);

            /* value format */
            Format valueFormat;
            if (serialVersion >= V2) {
                int ordinal = dis.read();
                valueFormat = Value.Format.values()[ordinal];
            } else {
                valueFormat = Value.Format.TABLE;
            }

            /* expiry time bytes */
            byte[] expiryTimeBytes = readBytesWithLength(dis,
                                                         "expiryTimeBytes",
                                                         fileChunk);
            long expiryTimeMs = -1;
            if (expiryTimeBytes.length > 0) {
                /*
                 * Convert the expiryTime in bytes to expiryTime in hours
                 * using PackedInteger utility method
                 */
                long expiryTimeHours =
                    PackedInteger.readInt(expiryTimeBytes, 0);

                /*
                 * Convert expiryTime in hours to expiryTime in ms
                 */
                expiryTimeMs = expiryTimeHours * 60L * 60L * 1000L;
            }

            /* expiry time bytes */
            byte[] checksumBytes = readBytesWithLength(dis,
                                                       "checksumBytes",
                                                       fileChunk);

            validateCheckSum(checksumBytes, keyBytes, valueBytes);
            return KeyValueBytes.createTableData(keyBytes, valueFormat,
                                                 valueBytes, expiryTimeMs);
        } catch (IOException ioe) {
            raiseCurruptedDataError(ioe.getMessage(), fileChunk, ioe);
        }
        return null;
    }

    /**
     * Deserialize a record encoded with different version of definition.
     * This API is used by the export utility to deserialize a record using the
     * version of the table that was exported and NOT the latest evolved version
     * of the table.
     *
     * @param vv record that needs to be deserialized
     * @param exportTableVersion export table version used to deserialize the
     *        record
     * @param row row containing only the key portion
     */
    private static void
        createExportRowFromValueVersion(TableImpl table,
                                        RowImpl row,
                                        ValueVersion vv,
                                        int exportTableVersion) {

        byte[] data = vv.getValue().getValue();
        if (data.length == 0) {
            return;
        }

        int offset = 0;
        int tableVersion = data[offset];

        /*
         * Get the writer schema for the record
         */
        RecordDef writeDef = ((TableImpl)table.getVersion(tableVersion))
                                .getValueRecordDef();

        /*
         * Get the reader schema for the record using exportTableVersion
         */
        RecordDef readDef = ((TableImpl)table.getVersion(exportTableVersion))
                              .getValueRecordDef();
        if (readDef == null) {
            /*
             * no record value RecordDef, table is key-only, all non-key
             * fields are null
             */
            return;
        }

        table.createExportRowFromValueSchema((RecordDefImpl)writeDef,
                                             (RecordDefImpl)readDef,
                                             row,
                                             data,
                                             offset,
                                             exportTableVersion,
                                             vv.getValue().getFormat());
    }

    private static void writeBytesWithLength(OutputStream out, byte[] bytes) {
        String lengthStr = bytes.length + " ";
        write(out, lengthStr.getBytes(UTF8));
        write(out, bytes);
    }

    private static byte[] readBytesWithLength(DataInputStream dis,
                                              String name,
                                              String fileChunk)
        throws IOException {

        int length = (int)readLength(dis, name, fileChunk);
        byte[] bytes = new byte[length];
        if (length > 0) {
            dis.readFully(bytes);
            return bytes;
        }
        return bytes;
    }

    public static long readNumber(DataInputStream dis,
                                  String name,
                                  String fileChunk) throws IOException {
        return readLength(dis, name, fileChunk);
    }

    /**
     * Return a number present in the FileEntryStream. If a number is not
     * found, NumberFormatException is thrown.
     */
    private static long readLength(DataInputStream dis,
                                   String name,
                                   String fileChunk)
        throws IOException {

        /* Length value is long type which has up to 19 bytes */
        byte[] bytes = new byte[20];
        int i = 0;
        for (; i < bytes.length; i++) {
            byte b = dis.readByte();
            if (b == SPACE_BYTE) {
                break;
            }
            bytes[i] = b;
        }
        if (i == bytes.length) {
            raiseCurruptedDataError("invalid length of " + name, fileChunk);
        }

        int length = 0;
        String numberString = new String(bytes, 0, i, UTF8);
        try {
            length = Integer.parseInt(numberString);
        } catch (NumberFormatException nfe) {
            raiseCurruptedDataError("invalid bytes for length of " + name +
                                    ": " + numberString, fileChunk, nfe);
        }
        return length;
    }

    private static void writeIndentifier(OutputStream out, String identifier) {
        write(out, identifier.getBytes(UTF8));
    }

    private static void write(OutputStream out, byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException ioe) {
            throw new RuntimeException(
                "Failed to write bytes: " + bytes.length);
        }
    }

    private static String readIdentifier(DataInputStream dis)
        throws IOException {

        int val = dis.readByte();
        return new String(new byte[] {(byte)val}, UTF8);
    }

    private static void raiseCurruptedDataError(String msg, String fileChunk) {
        throw new DataCorruptedException("Data currupted in file segment: " +
                fileChunk + ": "  + msg);
    }

    private static void raiseCurruptedDataError(String msg,
                                                String fileChunk,
                                                Throwable cause) {

        throw new DataCorruptedException("Data currupted in file segment: " +
                  fileChunk + ": "  + msg, cause);
    }

    private static void validateCheckSum(byte[] checksumBytes,
                                         byte[] keyBytes,
                                         byte[] valueBytes) {
        long storedCheckSum = PackedInteger.readLong(checksumBytes, 0);
        /*
         * Calculate the checksum of the record
         */
        long checkSum = getChecksum(keyBytes, valueBytes);

        /*
         * If the checksum of the record during import does not match
         * the checksum of the record calculated during export, skip
         * the record and get the next table record.
         */
        if (storedCheckSum != checkSum) {
            throw new DataCorruptedException("Mismatched checksum", true);
        }
    }

    /**
     * Generate a CRC checksum given the record key and value bytes
     */
    private static long getChecksum(byte[] keyBytes, byte[] valueBytes) {

        byte[] recordBytes =
            new byte[keyBytes.length + valueBytes.length];

        System.arraycopy(keyBytes, 0, recordBytes,
                         0, keyBytes.length);

        System.arraycopy(valueBytes, 0, recordBytes,
                         keyBytes.length, valueBytes.length);

        Checksum checksum = new CRC32();
        checksum.update(recordBytes, 0, recordBytes.length);

        return checksum.getValue();
    }

    public static void setTestVersion(int version) {
        testVersion = version;
    }

    /**
     * This class used to store deserialized data entry
     */
    public static class KeyValueBytes {
        public enum Type {
            TABLE,
            NONFORMAT,
            LOB
        }
        private Type type;
        private byte[] keyBytes;
        private Value.Format valueFormat;
        private byte[] valueBytes;
        private long expiryTimeMs;
        private int schemaId;
        private InputStream stream;

        KeyValueBytes(Type type,
                      byte[] keyBytes,
                      Value.Format valueFormat,
                      byte[] valueBytes,
                      int schemaId,
                      long expirationTimeMs) {
            this.type = type;
            this.keyBytes = keyBytes;
            this.valueFormat = valueFormat;
            this.valueBytes = valueBytes;
            this.schemaId = schemaId;
            this.expiryTimeMs = expirationTimeMs;
        }

        public static KeyValueBytes createTableData(byte[] keyBytes,
                                                    Value.Format valueFormat,
                                                    byte[] valueBytes,
                                                    long expirationTimeMs) {
            return new KeyValueBytes(Type.TABLE, keyBytes, valueFormat,
                                     valueBytes, 0, expirationTimeMs);
        }

        public static KeyValueBytes createNonFormatData(byte[] keyBytes,
                                                        byte[] valueBytes) {
            return new KeyValueBytes(Type.NONFORMAT, keyBytes, null,
                                     valueBytes, 0, 0);
        }

        public static KeyValueBytes createLobData(byte[] keyBytes,
                                                  byte[] valueBytes) {
            return new KeyValueBytes(Type.LOB, keyBytes, null,
                                     valueBytes, 0, 0);
        }

        public byte[] getKeyBytes() {
            return keyBytes;
        }

        public Value.Format getValueFormat() {
            return valueFormat;
        }

        public byte[] getValueBytes() {
            return valueBytes;
        }

        public long getExpirationTime() {
            return expiryTimeMs;
        }

        public int getSchemaId() {
            return schemaId;
        }

        public String getLobFileName() {
            if (type == Type.LOB) {
                return new String(valueBytes, StandardCharsets.UTF_8);
            }
            return null;
        }

        public InputStream getInputStream() {
            return stream;
        }

        public Type getType() {
            return type;
        }

        public void setKeyBytes(byte[] bytes) {
            keyBytes = bytes;
        }

        public void setValueBytes(byte[] bytes) {
            valueBytes = bytes;
        }

        public void setLobStream(InputStream stream) {
            this.stream = stream;
        }

        public KeyValueVersion toKeyValueVersion() {
            Key key = Key.fromByteArray(keyBytes);
            Value value;
            if (type == Type.TABLE) {
                if (valueFormat != null) {
                    value = Value.internalCreateValue(valueBytes, valueFormat);
                } else {
                    value = Value.fromByteArray(valueBytes);
                }
            } else {
                value = Value.createValue(valueBytes);
            }
            if (expiryTimeMs > 0) {
                // TODO: Handle the modification time for row import after
                // multi-region tables support export/import. We may want
                // to give users the option to maintain the original
                // modification time.
                return new KeyValueVersionInternal(key, value, expiryTimeMs);
            }
            return new KeyValueVersion(key, value);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("keyBytes: ");
            sb.append(keyBytes.length);
            sb.append(", valueBytes: ");
            sb.append(valueBytes.length);
            if (expiryTimeMs > 0) {
                sb.append(", expiryTimeMs: ");
                sb.append(expiryTimeMs);
            }
            if (schemaId > 0) {
                sb.append(", schemaId: ");
                sb.append(schemaId);
            }
            return sb.toString();
        }
    }

    /**
     * The exception
     */
    public static class DataCorruptedException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private boolean checksumMismatched;

        DataCorruptedException(String msg) {
            this(msg, false);
        }

        DataCorruptedException(String msg, boolean checksumMismatched) {
            super(msg);
            this.checksumMismatched = checksumMismatched;
        }

        DataCorruptedException(String msg, Throwable cause) {
            super(msg, cause);
        }

        public boolean isChecksumMismatched() {
            return checksumMismatched;
        }
    }
}
