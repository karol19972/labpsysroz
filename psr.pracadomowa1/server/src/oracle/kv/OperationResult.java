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

package oracle.kv;

import oracle.kv.table.FieldValue;

/**
 * The Result associated with the execution of an Operation.
 *
 * @see OperationFactory
 * @see KVStore#execute execute
 */
public interface OperationResult {

    /**
     * Whether the operation succeeded.  A put or delete operation may be
     * unsuccessful if the key or version was not matched.
     */
    boolean getSuccess();

    /**
     * For a put operation, the version of the new key/value pair.
     *
     * <p>Is null if any of the following conditions are true:</p>
     * <ul>
     * <li>The operation is not a put operation.
     * </li>
     * <li>The put operation did not succeed.
     * </li>
     * </ul>
     */
    Version getNewVersion();

    /**
     * For a put or delete operation, the version of the previous value
     * associated with the key.
     *
     * <p>Is null if any of the following conditions are true:</p>
     * <ul>
     * <li>The operation is not a put or delete operation.
     * </li>
     * <li>A previous value did not exist for the given key.
     * </li>
     * <li>The {@code prevReturn} {@link ReturnValueVersion} parameter
     * specified that the version should not be returned.
     * </li>
     * <li>For a {@link OperationFactory#createPutIfVersion putIfVersion} or
     * {@link OperationFactory#createDeleteIfVersion deleteIfVersion}
     * operation, the {@code matchVersion} parameter matched the version of the
     * previous value.
     * </li>
     * </ul>
     */
    Version getPreviousVersion();

    /**
     * For a put or delete operation, the previous value associated with
     * the key.
     *
     * <p>Is null if any of the following conditions are true:</p>
     * <ul>
     * <li>The operation is not a put or delete operation.
     * </li>
     * <li>A previous value did not exist for the given key.
     * </li>
     * <li>The {@code prevReturn} {@link ReturnValueVersion} parameter
     * specified that the value should not be returned.
     * </li>
     * <li>For a {@link OperationFactory#createPutIfVersion putIfVersion} or
     * {@link OperationFactory#createDeleteIfVersion deleteIfVersion}
     * operation, the {@code matchVersion} parameter matched the version of the
     * previous value.
     * </li>
     * </ul>
     */
    Value getPreviousValue();

    /**
     * Internal use only
     * @hidden
     *
     * For a put operation, returns the expiration time of a new record.
     */
    long getNewExpirationTime();

    /**
     * Internal use only
     * @hidden
     *
     * For a put or delete operation, returns the expiration time of the
     * previous record if available, or 0 (no expiration) if it is not
     * available.
     */
    long getPreviousExpirationTime();

    /**
     * Internal use only
     * @hidden
     *
     * For a put or delete operation, returns the storage size of the
     * previous record if available, or -1 if it is not available.
     */
    int getPreviousStorageSize();

    /**
     * Internal use only
     * @hidden
     *
     * For a put operation, the value generated by the system if the table
     * has an identity column. If the operation is a put and does not
     * generate an identity column value null is returned. If the operation
     * is not a put IllegalStateException is thrown.
     */
    FieldValue getGeneratedValue();

    /**
     * Internal use only
     * @hidden
     *
     * For a put or delete operation, returns the modification time of the
     * previous record in milliseconds.
     */
    long getPreviousModificationTime();

    /**
     * Internal use only
     * @hidden
     *
     * For a put or delete operation, returns the modification time of a new
     * record in milliseconds.
     */
    long getNewModificationTime();

    /**
     * Internal use only
     * @hidden
     *
     * For a put or delete operation, returns the storage size of the
     * new record.
     */
    int getNewStorageSize();
}