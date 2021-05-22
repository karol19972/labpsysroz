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

package oracle.kv.util.expimp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.utils.exp.AbstractStoreOutput;
import oracle.kv.util.expimp.utils.exp.ObjectStoreOutput;

/**
 * An implementation class for AbstractStoreExport used to export the data from
 * Oracle NoSql store to Oracle Storage Cloud Service.
 */
public class ObjectStoreExport extends AbstractStoreExport {

    /*
     * Stream for logging data
     */
    private final ByteArrayOutputStream loggerOutput;

    /*
     * The export handler
     */
    private ObjectStoreOutput outputHandler;

    public static final String exportLogKey = "exportLogKey";
    private static final String exportStatsKey = "exportStats";

    /**
     * Constructor that connects to the Oracle storage cloud service and
     * creates the container if it does not exists.
     *
     * @param storeName kvstore name
     * @param helperHosts kvstore helper hosts
     * @param kvUsername the user name to connect to the store
     * @param kvSecurityFile the security file of the specified user
     * @param requestTimeoutMs the request timeout in milliseconds
     * @param containerName name of container in Oracle storage cloud service
     * @param serviceName Oracle storage cloud service instance name
     * @param userName Oracle storage cloud service username
     * @param password Oracle storage cloud service password
     * @param serviceURL Oracle storage cloud service URL
     * @param json the flag indicates to return JSON format information
     */
    public ObjectStoreExport(String storeName,
                             String[] helperHosts,
                             String kvUsername,
                             String kvSecurityFile,
                             int requestTimeoutMs,
                             String containerName,
                             String serviceName,
                             String userName,
                             String password,
                             String serviceURL,
                             boolean json) {

        super(storeName, helperHosts, kvUsername, kvSecurityFile,
              requestTimeoutMs, json);

        loggerOutput = new ByteArrayOutputStream();

        /*
         * Set handle to the logger
         */
        setLoggerHandler();

        /*
         * Initialize export out handler
         */
        try {
            outputHandler = new ObjectStoreOutput(serviceName,
                                                  userName,
                                                  password,
                                                  serviceURL,
                                                  containerName,
                                                  logger);
        } catch (RuntimeException re) {
            exit(true, Utilities.getExitCode(re, ExitCode.EXIT_UNEXPECTED),
                 System.err, re.getMessage());
        }
    }

    @Override
    public AbstractStoreOutput getOutputHandler() {
        return outputHandler;
    }

    /**
     * Sets the log handler
     */
    @Override
    protected void setLoggerHandler(Logger logger) {
       Utilities.setStreamHandler(logger, loggerOutput);
    }

    /**
     * Flushes all the logging data
     */
    @Override
    protected void flushLogs() {
        if (outputHandler == null) {
            return;
        }

        InputStream stream =
            new ByteArrayInputStream(loggerOutput.toByteArray());
        try {
            outputHandler.storeObject(exportLogKey, stream, false);
        } catch (RuntimeException re) {
            /* do nothing */
        }
    }

    @Override
    protected void generateExportStats(String exportStats) {
        InputStream stream = new ByteArrayInputStream(exportStats
            .getBytes(StandardCharsets.UTF_8));
        try {
            /* Store the export stats into Oracle Storage Cloud Service */
            outputHandler.storeObject(exportStatsKey, stream, false);
        } catch (RuntimeException re) {
            logger.warning("Flush export status failed: " + re.getMessage());
        }
    }
}
