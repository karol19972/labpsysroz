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

/**
 * The factory that creates ObjectStoreAPI object, it will check if required
 * OCI classic classes can be found in classpath before create the object.
 */
public class ObjectStoreAPIFactory {

    static final String objectStoreAPIClassName =
        "oracle.kv.util.expimp.utils.ObjectStoreAPIImpl";

    static final String[] cloudStorageClassNames = {
        /* class in oracle.cloud.storage.api.jar */
        "oracle.cloud.storage.CloudStorage",
        /* class in jersey-client.jar */
        "com.sun.jersey.api.client.ClientHandlerException",
        /* class in jersey-core.jar */
        "com.sun.jersey.spi.inject.Errors$Closure",
        /* class in jettison.jar */
        "org.codehaus.jettison.json.JSONException"
    };

    /**
     * Creates and returns an ObjectStoreAPI object if the underlying Oracle
     * storage cloud service class is available.
     *
     * @return the ObjectStoreAPI object
     * @throws IllegalArgumentException if the Oracle storage cloud service
     * class is not available.
     */
    public static ObjectStoreAPI getObjectStoreAPI(String serviceName,
                                                   String userName,
                                                   String password,
                                                   String serviceURL,
                                                   String containerName) {

        /*
         * Check if oracle storage cloud service dependent classes are present.
         */
        for (String className : cloudStorageClassNames) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException e) {
                final String errorMsg =
                    "To use Oracle Storage Cloud as the export store, please " +
                    "install the following jars:\n" +
                    "1. oracle.cloud.storage.api\n" +
                    "2. jersey-core\n" +
                    "3. jersey-client\n" +
                    "4. jettison\n" +
                    "Instructions to install the above jars can be found in " +
                    "the documentation.";
                throw new IllegalArgumentException(errorMsg);
            }
        }

        return openObjectStoreAPI(serviceName, userName, password,
                                    serviceURL, containerName);
    }

    /**
     * Creates ObjectStoreAPI and connects to the cloud storage service.
     *
     * Because OCI classic libs may not be included in package, so
     * oracle.kv.util.expimp.utils.ObjectStoreAPIImpl class that
     * encapsulates the OCI operations may need to exclude from compiling, so
     * here dynamically load ObjectStoreAPIImpl class and create instance at
     * run time.
     */
    private static ObjectStoreAPI openObjectStoreAPI(String serviceName,
                                                     String userName,
                                                     String password,
                                                     String serviceURL,
                                                     String containerName) {
        ObjectStoreAPI objectStoreAPI;
        try {
            Class<?> objectStoreAPIClazz = Class.forName(objectStoreAPIClassName);
            objectStoreAPI = (ObjectStoreAPI)objectStoreAPIClazz
                .getDeclaredConstructor(String.class, String.class,
                                        String.class, String.class,
                                        String.class)
                .newInstance(serviceName, userName, password,
                             serviceURL, containerName);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception creating " +
                objectStoreAPIClassName + ": " + e.getMessage(), e);
        }

        /* Connects to cloud storage */
        objectStoreAPI.connect();

        return objectStoreAPI;
    }
}
