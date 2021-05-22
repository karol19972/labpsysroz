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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.MigratorExportImport.InvalidConfigException;
import oracle.kv.util.expimp.utils.ObjectStoreAPI.ObjectStoreException;
import oracle.kv.util.migrator.impl.util.OndbUtils.ConnectStoreException;

/**
 * General Export/Import utility class
 */
public class Utilities {

    /**
     * Format the LogRecord needed for export and import
     *
     * @param record LogRecord
     */
    public static String format(LogRecord record) {

        Throwable throwable = record.getThrown();
        String throwableException = "";

        if (throwable != null) {
            throwableException = "\n" + throwable.toString();
        }

        Date date=new Date(record.getMillis());
        SimpleDateFormat simpleDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String dateFormat = simpleDateFormat.format(date);

        return dateFormat + " " + record.getLevel().getName() + ": " +
               record.getMessage() + throwableException + "\n";
    }

    /**
     * The comparator for table names:
     *    o Ignore case
     *    o sysdefault:<tableName> equals to <tableName>
     */
    public static class TableNameComparator implements Comparator<String> {

        static TableNameComparator newInstance = new TableNameComparator();

        @Override
        public int compare(String name1, String name2) {
            return toIternalFullNamespacename(name1)
                .compareToIgnoreCase(toIternalFullNamespacename(name2));
        }

        /**
         * Removes the default "sysdefault" namespace from table full namespace
         * name. e.g. sysdefault:t1 => t1
         */
        private String toIternalFullNamespacename(String fullNamespaceName) {
            String ns = NameUtils.switchToInternalUse(
                NameUtils.getNamespaceFromQualifiedName(fullNamespaceName));
            String tname =
                NameUtils.getFullNameFromQualifiedName(fullNamespaceName);
            return NameUtils.makeQualifiedName(ns, tname);
        }
    }

    /**
     * Adds log file handler to logger
     */
    public static void setLocalFileHandler(Logger logger, File logFile) {
        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler(logFile.getAbsolutePath(), false);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    return Utilities.format(record);
                }
            });
        } catch (SecurityException se) {
            throw new IllegalArgumentException("Intiailize log file failed: " +
                                               se.getMessage());
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Intiailize log file failed: " +
                                               ioe);
        }

        logger.addHandler(fileHandler);
        logger.setLevel(Level.INFO);
    }

    /**
     * Adds stream handler to logger
     */
    public static void setStreamHandler(Logger logger,
                                        ByteArrayOutputStream outputStream) {
        StreamHandler streamHandler =
            new StreamHandler(outputStream,
                new SimpleFormatter() {
                    @Override
                    public synchronized String format(LogRecord record) {
                        return Utilities.format(record);
                    }
                 }) {
                    @Override
                    public synchronized void publish(final LogRecord record) {
                        super.publish(record);
                        flush();
                    }
            };

        logger.addHandler(streamHandler);
        logger.setLevel(Level.INFO);
    }

    /**
     * Handles the exit, display message and exit the process with the specified
     * code accordingly.
     */
    public static void exit(ExitCode exitCode,
                            PrintStream ps,
                            String message,
                            boolean outputAsJson,
                            String command,
                            boolean printToConsole) {
        if (outputAsJson) {
            /*
             * Display export output in json format
             */
            ExitHandler.displayExitJson(ps, exitCode, message, command);
        } else {

            String exitMsg = exitCode.getDescription();

            if (message != null) {
                exitMsg += " - " + message;
            }

            if ((exitCode.equals(ExitCode.EXIT_OK) && printToConsole) ||
                    !exitCode.equals(ExitCode.EXIT_OK)) {
                ps.println(exitMsg);
            }
        }

        if (!exitCode.equals(ExitCode.EXIT_OK)) {
            System.exit(exitCode.value());
        }
    }

    public static ExitCode getExitCode(RuntimeException ex, ExitCode exitCode) {
        try {
            throw ex;
        } catch (InvalidConfigException ice) {
            return ice.getExitCode();
        } catch (ConnectStoreException cse) {
            return ExitCode.EXIT_NOCONNECT;
        } catch (ObjectStoreException ose) {
            switch (ose.getType()) {
            case CONNECT_MALFORM_URL:
            case CONNECT_ERROR:
                return ExitCode.EXIT_EXPSTR_NOCONNECT;
            case CONNECT_NO_PREM:
                return ExitCode.EXIT_NOPERM;
            case CREATE_CONTAINER_ERROR:
                return ExitCode.EXIT_CONTAINER_EXISTS;
            case CONTAINER_NOT_EXISTS:
                return ExitCode.EXIT_NOEXPPACKAGE;
            default:
                break;
            }
        } catch (RuntimeException re) {
        }
        return exitCode;
    }
}
