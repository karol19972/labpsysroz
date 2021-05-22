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
import java.net.MalformedURLException;
import java.util.Map;

import oracle.cloud.storage.CloudStorage;
import oracle.cloud.storage.CloudStorageConfig;
import oracle.cloud.storage.CloudStorageFactory;
import oracle.cloud.storage.exception.AuthenticationException;
import oracle.cloud.storage.exception.InvalidContainerNameException;
import oracle.cloud.storage.exception.NoSuchContainerException;
import oracle.cloud.storage.exception.SystemException;
import oracle.cloud.storage.model.StorageInputStream;
import oracle.kv.util.expimp.utils.ObjectStoreAPI.ObjectStoreException.ErrorType;

import com.sun.jersey.api.client.ClientHandlerException;

/**
 * The ObjectStoreAPI class encapsulates methods to access OCI classic storage
 * service.
 * <p>
 * All the methods in this class throw ObjectStoreException when failed, the
 * ObjectStoreException wraps the actual Exception as cause and include error
 * type information.
 */
public class ObjectStoreAPIImpl implements ObjectStoreAPI {

    private CloudStorage conn;

    private final String serviceName;
    private final String userName;
    private final String password;
    private final String serviceURL;
    private final String containerName;

    public ObjectStoreAPIImpl(String serviceName,
                              String userName,
                              String password,
                              String serviceURL,
                              String containerName) {

        this.serviceName = serviceName;
        this.userName = userName;
        this.password = password;
        this.serviceURL = serviceURL;
        this.containerName = containerName;
    }

    /**
     * Connects to the Oracle storage cloud service.
     */
    @Override
    public void connect() {

        /*
         * Oracle Storage Cloud Service client configuration builder
         */
        CloudStorageConfig myConfig = new CloudStorageConfig();

        try {
            /*
             * Use CloudStorageConfig interface to specify options for
             * connecting to a Service instance.
             */
            try {
                myConfig.setServiceName(serviceName)
                        .setUsername(userName)
                        .setPassword(password.toCharArray())
                        .setServiceUrl(serviceURL);
            } catch (MalformedURLException mue) {
                String msg = "Invalid URL " + serviceURL + ": " +
                             mue.getMessage();
                raiseException(ErrorType.CONNECT_MALFORM_URL, mue, msg);
            }
            /*
             * Get a handle to a Service Instance of the Oracle Storage
             * Cloud Service
             */
            conn = CloudStorageFactory.getStorage(myConfig);
        } catch (AuthenticationException ae) {
            String msg = "Authentiation failed: " + ae.getMessage();
            raiseException(ErrorType.CONNECT_NO_PREM, ae, msg);
        } catch (ClientHandlerException che) {
            String msg = "Connect to object store failed: " + che.getMessage();
            raiseException(ErrorType.CONNECT_ERROR, che, msg);
        }
    }

    /**
     * Creates container
     */
    @Override
    public void createContainer() {
        try {
            /*
             * Create a new storage Container to store the exported data/
             * metadata
             */
            conn.createContainer(containerName);
        } catch (InvalidContainerNameException ncne) {
            String msg = "Invalid container name: " + containerName;
            raiseException(ErrorType.INVALID_CONTAINER_NAME, ncne, msg);
        } catch (ClientHandlerException | SystemException e) {
            String msg = "Create container " + containerName +
                         " failed: " + e.getMessage();
            raiseException(ErrorType.CREATE_CONTAINER_ERROR, e, msg);
        }
    }

    /**
     * Retrieve the object from Oracle Storage Cloud Service referenced by the
     * object key
     *
     * @param key referencing key for the object
     * @return input stream for the object data
     */
    @Override
    public StorageInputStream retrieveObject(String key) {

        try {
            return conn.retrieveObject(containerName, key);
        } catch (ClientHandlerException | SystemException se) {
            String msg = "Exception retrieving object " + key +
                    " from Oracle Storage Cloud Service: " + se.getMessage();
            raiseException(ErrorType.RETRIEVE_OBJECT_ERROR, se, msg);
        }
        return null;
    }

    /**
     * Retrieves the CustomMatadata of an object.
     *
     * @param key the key associated with the object
     * @return the CustomMetadata, null if not exist
     */
    @Override
    public Map<String, String> retrieveObjectMetadata(String key) {
        StorageInputStream in = retrieveObject(key);
        if (in == null) {
            return null;
        }
        return in.getCustomMetadata();
    }

    /**
     * Retrieves an object by Manifest key
     *
     * @param key the key associated with the object
     * @return the StoreInputStream, null if not exist
     */
    @Override
    public InputStream retrieveObjectByManifest(String key) {
        return retrieveObject(getManifestKey(key));
    }

    /**
     * Store an object to Oracle Storage Cloud Service
     *
     * @param key referencing key for the object
     * @param stream holds the object data
     */
    @Override
    public void storeObject(String key, InputStream stream) {
        storeObject(key, null, stream);
    }

    /**
     * Store an object to Oracle Storage Cloud Service
     *
     * @param key referencing key for the object
     * @param customMetadata a map object of metadata key/metadata value pairs
     *                       for the Object being stored
     * @param stream holds the object data
     */
    @Override
    public void storeObject(String key,
                            Map<String, String> customMetadata,
                            InputStream stream) {
        try {
            conn.storeObject(containerName, key, "text/plain",
                             customMetadata, stream);
        } catch (ClientHandlerException | SystemException e) {
            String msg = "Exception storing object " + key +
                " from Oracle Storage Cloud Service: " + e.getMessage();
            raiseException(ErrorType.STORE_OBJECT_ERROR, e, msg);
        }
    }

    /**
     * Create or overwrite an Object manifest file within a Container.
     *
     * @param key referencing key for the object
     * @param segmentPrefix the common prefix for all the segments
     */
    @Override
    public void storeObjectManifest(String key, String segmentPrefix) {
        try {
            /*
             * Store the manifest object for this file. The manifest object
             * is used to retrieve all the file segments belonging to a
             * given file.
             */
            conn.storeObjectManifest(containerName,
                                     key,
                                     "text/plain",
                                     containerName,
                                     segmentPrefix);
        } catch (ClientHandlerException | SystemException e) {
            String msg = "Exception storing manifest object " + key +
                         " in Oracle Storage Cloud Service: " + e.getMessage();
            raiseException(ErrorType.STORE_OBJECT_MANIFEST_ERROR, e, msg);
        }
    }

    @Override
    public void checkContainerExists() {
        try {
            if (conn.describeContainer(containerName) != null) {
                return;
            }
        } catch (NoSuchContainerException ose) {
            /* do nothing */
        }
        String msg = "Container " + containerName +
            " not present in Oracle Storage Cloud Service.";
        raiseException(ErrorType.CONTAINER_NOT_EXISTS, null, msg);
    }

    public CloudStorage getCloudStorage() {
        return conn;
    }

    @Override
    public String getContainerName() {
        return containerName;
    }

    private String getManifestKey(String fileName) {
        return fileName + "-" + fileName.length() + "-ManifestKey";
    }

    private void raiseException(ErrorType type, Throwable cause, String msg) {
        throw new ObjectStoreException(type, cause, msg);
    }
}
