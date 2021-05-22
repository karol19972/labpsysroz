/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je;

/**
 * The result of an operation that successfully reads or writes a record.
 * <p>
 * An OperationResult does not contain any failure information. Methods that
 * perform unsuccessful reads or writes return null or throw an exception. Null
 * is returned if the operation failed for commonly expected reasons, such as a
 * read that fails because the key does not exist, or an insertion that fails
 * because the key does exist.
 * <p>
 * Methods that return OperationResult can be compared to methods that return
 * {@link OperationStatus} as follows: If {@link OperationStatus#SUCCESS} is
 * returned by the latter methods, this is equivalent to returning a non-null
 * OperationResult by the former methods.
 *
 * @since 7.0
 */
public class OperationResult {

    private final long expirationTime;
    private final long modificationTime;
    private final boolean tombstone;
    private final boolean update;

    OperationResult(final long expirationTime,
                    final boolean update,
                    final long modificationTime,
                    final boolean tombstone) {
        this.expirationTime = expirationTime;
        this.modificationTime = modificationTime;
        this.tombstone = tombstone;
        this.update = update;
    }

    /**
     * Returns whether the operation was an update, for distinguishing inserts
     * and updates performed by a {@link Put#OVERWRITE} operation.
     *
     * @return whether an existing record was updated by this operation.
     */
    public boolean isUpdate() {
        return update;
    }

    /**
     * Returns the expiration time of the record, in milliseconds, or zero
     * if the record has no TTL and does not expire.
     * <p>
     * For 'get' operations, this is the expiration time of the current record.
     * For 'put operations, this is the expiration time of the newly written
     * record. For 'delete' operation, this is the expiration time of the
     * record that was deleted.
     * <p>
     * The return value will always be evenly divisible by the number of
     * milliseconds in one hour. If {@code TimeUnit.Days} was specified
     * when the record was written, the return value will also be evenly
     * divisible by the number of milliseconds in one day.
     *
     * @return the expiration time in milliseconds, or zero.
     *
     * @see <a href="WriteOptions.html#ttl">Time-To-Live</a>
     */
    public long getExpirationTime() {
        return expirationTime;
    }

    /**
     * Returns the last modification time of the record, or zero.
     *
     * <p>For write operations, non-zero is returned for records belonging to
     * primary databases and zero is returned when the record belongs to a
     * secondary (duplicates) database.</p>
     *
     * <p>For read operations, zero is returned in the following cases and
     * non-zero is returned in all other cases.
     * <ul>
     *     <li>When the record belongs to a secondary (duplicates) database,
     *     zero is always returned. Modification times are not maintained in
     *     secondary databases.</li>
     *
     *     <li>When the record data is not requested, i.e., the {@code data}
     *     param is null or {@link DatabaseEntry#setPartial} was called, then
     *     zero may be returned. This is because the modification time is
     *     stored with the record's data, so to obtain the modification time
     *     the record's LN may need to be fetched from disk. If the LN happens
     *     to be cached or is embedded in the parent BIN (see {@link
     *     EnvironmentConfig#TREE_MAX_EMBEDDED_LN}), then non-zero is
     *     returned; but to guarantee that it is returned, the data should be
     *     requested.</li>
     *
     *     <li>When the record was last written using JE 19.3 or earlier,
     *     zero is always returned. Storage of modification times was added in
     *     JE 19.5.</li>
     * </ul>
     *
     * @see WriteOptions#setModificationTime
     * @since 19.5
     */
    public long getModificationTime() {
        return modificationTime;
    }

    /**
     * Returns the tombstone property of the record.
     *
     * @see <a href="WriteOptions.html#tombstones">Tombstones</a>
     * @since 19.5
     */
    public boolean isTombstone() {
        return tombstone;
    }
}
