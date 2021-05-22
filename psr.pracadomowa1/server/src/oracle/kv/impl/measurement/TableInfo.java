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

package oracle.kv.impl.measurement;

import java.io.Serializable;

import com.google.gson.JsonObject;

/**
 * Container for table information for a period of time.
 */
public class TableInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final long tableId;
    private final long startTime;
    private final long duration;
    private final int readKB;
    private final int writeKB;
    private final long size;
    private final int readThroughputExceptions;
    private final int writeThroughputExceptions;
    private final int sizeExceptions;
    private final int accessExceptions;

    public TableInfo(String tableName,
                     long tableId,
                     long startTime, long duration,
                     int readKB, int writeKB, long size,
                     int readThroughputExceptions,
                     int writeThroughputExceptions,
                     int sizeExceptions,
                     int accessExceptions) {
        assert duration > 0;
        this.tableName = tableName;
        this.tableId = tableId;
        this.startTime = startTime;
        this.duration = duration;
        this.readKB = readKB;
        this.writeKB = writeKB;
        this.size = size;
        this.readThroughputExceptions = readThroughputExceptions;
        this.writeThroughputExceptions = writeThroughputExceptions;
        this.sizeExceptions = sizeExceptions;
        this.accessExceptions = accessExceptions;
    }

    /**
     * Gets the table name.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Gets the table id.
     *
     * @return the table id
     */
    public long getTableId() {
        return tableId;
    }

    /**
     * Gets the time (in millis) when the collection period started.
     *
     * @return the time when this object was created
     */
    public long getStartTimeMillis() {
        return startTime;
    }

    /**
     * Gets the duration (in millis) of the collection period. DUration will
     * be &gt; 0;
     *
     * @return the duration of the collection period
     */
    public long getDurationMillis() {
        return duration;
    }

    /**
     * Gets the total read KB for the table.
     *
     * @return the total read KB
     */
    public int getReadKB() {
        return readKB;
    }

    /**
     * Gets the total write KB for the table.
     *
     * @return the total write KB
     */
    public int getWriteKB() {
        return writeKB;
    }

    public long getSize() {
        return size;
    }

    /**
     * Gets the read throughput exceptions during the collection period.
     */
    public int getReadThroughputExceptions() {
        return readThroughputExceptions;
    }

    /**
     * Gets the write throughput exceptions during the collection period.
     */
    public int getWriteThroughputExceptions() {
        return writeThroughputExceptions;
    }

    public int getSizeExceptions() {
        return sizeExceptions;
    }

    public int getAccessExceptions() {
        return accessExceptions;
    }

    @Override
    public String toString() {
        return "TableInfo[" + tableName + ", " + tableId + ", " +
               readKB + ", " + writeKB + ", " + size + ", " +
               readThroughputExceptions + ", " +
               writeThroughputExceptions + ", " + sizeExceptions + ", " +
               accessExceptions + ", " +
               startTime + ", " + duration + "]";
    }

    /* TODO: use Gson#toJsonTree? */
    public JsonObject toJson() {
        final JsonObject result = new JsonObject();
        result.addProperty("Table_Name", tableName);
        result.addProperty("Table_Id", tableId);
        result.addProperty("Read_KB", readKB);
        result.addProperty("Write_KB", writeKB);
        result.addProperty("Table_Size", size);
        result.addProperty("Start_Time", startTime);
        result.addProperty("Duration_Millis", duration);
        result.addProperty("Throughtput_Exceptions",
                           readThroughputExceptions +
                           writeThroughputExceptions);
        result.addProperty("Read_Throughput_Exceptions",
                           readThroughputExceptions);
        result.addProperty("Write_Throughput_Exceptions",
                           writeThroughputExceptions);
        result.addProperty("Access_Exceptions", accessExceptions);
        result.addProperty("Size_Exceptions", sizeExceptions);
        return result;
    }
}
