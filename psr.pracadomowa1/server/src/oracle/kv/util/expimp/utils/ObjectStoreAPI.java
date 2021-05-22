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

import java.io.InputStream;
import java.util.Map;

/**
 * The ObjectStoreAPI class encapsulates methods to access OCI classic storage
 * service.
 * <p>
 * All the methods in this class throw ObjectStoreException when failed, the
 * ObjectStoreException wraps the actual Exception as cause and include error
 * type information.
 */
public interface ObjectStoreAPI {
    /**
     * Connects to the Oracle storage cloud service.
     */
    void connect();

    /**
     * Creates container
     */
    void createContainer();

    /**
     * Retrieve the object from Oracle Storage Cloud Service referenced by the
     * object key
     *
     * @param key referencing key for the object
     * @return input stream for the object data
     */
    InputStream retrieveObject(String key);

    /**
     * Retrieves the CustomMatadata of an object.
     *
     * @param key the key associated with the object
     * @return the CustomMetadata, null if not exist
     */
    Map<String, String> retrieveObjectMetadata(String key);

    /**
     * Retrieves an object by Manifest key
     *
     * @param key the key associated with the object
     * @return the StoreInputStream, null if not exist
     */
    InputStream retrieveObjectByManifest(String key);

    /**
     * Store an object to Oracle Storage Cloud Service
     *
     * @param key referencing key for the object
     * @param stream holds the object data
     */
    void storeObject(String key, InputStream stream);

    /**
     * Store an object to Oracle Storage Cloud Service
     *
     * @param key referencing key for the object
     * @param customMetadata a map object of metadata key/metadata value pairs
     *                       for the Object being stored
     * @param stream holds the object data
     */
    void storeObject(String key,
                     Map<String, String> customMetadata,
                     InputStream stream);

    /**
     * Create or overwrite an Object manifest file within a Container.
     *
     * @param key referencing key for the object
     * @param segmentPrefix the common prefix for all the segments
     */
    void storeObjectManifest(String key, String segmentPrefix);

    /**
     * Checks the existence of the specified container.
     *
     * @throws ObjectStoreException if the container does not exists
     */
    void checkContainerExists();

    /**
     * Returns the container name.
     */
    String getContainerName();

    /**
     * An exception thrown if any cloud storage access operation failed, the
     * actual exception are wrapped as cause of ObjectStoreException.
     */
    public static class ObjectStoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public enum ErrorType {
            CONNECT_MALFORM_URL,
            CONNECT_NO_PREM,
            CONNECT_ERROR,
            INVALID_CONTAINER_NAME,
            CREATE_CONTAINER_ERROR,
            STORE_OBJECT_ERROR,
            STORE_OBJECT_MANIFEST_ERROR,
            RETRIEVE_OBJECT_ERROR,
            CONTAINER_NOT_EXISTS
        }

        private ErrorType type;

        ObjectStoreException(ErrorType type, Throwable cause, String message) {
            super(message, cause);
            this.type = type;
        }

        public ErrorType getType() {
            return type;
        }
    }
}
