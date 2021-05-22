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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.utils.exp.AbstractStoreOutput;
import oracle.kv.util.expimp.utils.exp.LocalStoreOutput;

/**
 * An implementation class for AbstractStoreExport used to export the data from
 * Oracle NoSql store to Local file system.
 */
public class LocalStoreExport extends AbstractStoreExport {

    /*
     * The folder on local file system
     */
    private final File exportFolder;

    /*
     * Export log file location inside the export package
     */
    private final File exportLogFile;

    /*
     * The export output handler
     */
    private final LocalStoreOutput outputHandler;

    public static final String logFileName = "Export.log";
    private static final String exportStatsFileName = "Export.stat";

    /**
     * Constructor that creates the export package directory structure
     *
     * @param storeName kvstore name
     * @param helperHosts kvstore helper hosts
     * @param userName the user name to connect to the store
     * @param securityFile the security file of the specified user
     * @param requestTimeoutMs the request timeout in millisecond
     * @param exportPackagePath path in local file system for export package
     * @param exportTable true if exporting subset of tables in the kvsotre.
     *                    false if exporting the entire kvstore
     * @param json the flag indicates to return JSON format information
     */
    public LocalStoreExport(String storeName,
                            String[] helperHosts,
                            String userName,
                            String securityFile,
                            int requestTimeoutMs,
                            String exportPackagePath,
                            boolean exportTable,
                            boolean json) {

        super(storeName, helperHosts, userName, securityFile,
              requestTimeoutMs, json);

        exportFolder = new File(exportPackagePath);

        if (!exportFolder.exists() || !exportFolder.isDirectory()) {
            exit(false, ExitCode.EXIT_NO_EXPORT_FOLDER, System.err, null);
        }

        if (!Files.isWritable(exportFolder.toPath())) {
            exit(false, ExitCode.EXIT_NOWRITE, System.err, null);
        }

        exportLogFile = new File(exportFolder, logFileName);

        outputHandler = new LocalStoreOutput(exportFolder, exportTable, logger);
        /*
         * Set handle to the logger
         */
        setLoggerHandler();
    }

    @Override
    protected AbstractStoreOutput getOutputHandler() {
        return outputHandler;
    }

    /**
     * Sets the log handler
     */
    @Override
    protected void setLoggerHandler(Logger logger) {
        try {
            Utilities.setLocalFileHandler(logger, exportLogFile);
        } catch (IllegalArgumentException iae) {
            exit(false, ExitCode.EXIT_UNEXPECTED, System.err, null);
        }
    }

    /**
     * Nothing to do here for local file system
     */
    @Override
    protected void flushLogs() {

    }

    /**
     * Write the export stats - store name, helper hosts, export start time
     * and export end time to the export store - local file system
     */
    @Override
    protected void generateExportStats(String exportStats) {

        File exportStatsFile = new File(exportFolder, exportStatsFileName);
        PrintWriter out = null;
        try {
            out = new PrintWriter(exportStatsFile);
            out.write(exportStats);
        } catch (FileNotFoundException e) {
            logger.log(Level.WARNING, "Export stats file not found", e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
