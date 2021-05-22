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

package com.sleepycat.je.util;

import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.VerifySummary;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.util.verify.BtreeVerifier;
import com.sleepycat.je.utilint.CmdUtil;

/**
 * Verifies the internal structures of a database.
 *
 * <p>When using this utility as a command line program, and the
 * application uses custom key comparators, be sure to add the jars or
 * classes to the classpath that contain the application's comparator
 * classes.</p>
 *
 * <p>To verify a database and write the errors to stream:</p>
 *
 * <pre>
 *    DbVerify verifier = new DbVerify(env, dbName, quiet);
 *    verifier.verify();
 * </pre>
 */
public class DbVerify {

    private static final String usageString =
        "usage: " + CmdUtil.getJavaCommand(DbVerify.class) + "\n" +
        "      -h <dir>             # environment home directory\n" +
        "      [-c ]                # check cleaner metadata\n" +
        "      [-q ]                # quiet, exit with success or failure\n" +
        "      [-s <databaseName>]  # database to verify\n" +
        "      [-v <interval>]      # progress notification interval\n" +
        "      [-bs <size>]         # how many records to check each batch\n" +
        "      [-d <millis>]        # delay in ms between batches\n" +
        "      [-vdr]               # verify data records (read LNs)\n" +
        "      [-vor]               # verify obsolete records (cleaner metadata)\n" +
        "      [-V]                 # print JE version number";

    File envHome = null;
    Environment env;
    String dbName = null;

    private VerifyConfig verifyConfig = new VerifyConfig();

    /**
     * The main used by the DbVerify utility.
     *
     * @param argv The arguments accepted by the DbVerify utility.
     *
     * <pre>
     * usage: java { com.sleepycat.je.util.DbVerify | -jar
     * je-&lt;version&gt;.jar DbVerify }
     *             [-q] [-V] -s database -h dbEnvHome [-v intervalLNs]
     *             [-bs batchSize] [-d delayMs] [-vdr] [-vor]
     * </pre>
     *
     * <p>
     * -V   - show the version of the JE library.<br>
     * -s   - name of the database to verify; if omitted, verify all DBs<br>
     * -h   - the environment directory path name; required<br>
     * -q   - don't display database info or errors; default: false (display it)<br>
     * -v   - report intermediate statistics every specified LNs (Leaf Nodes);
     *        default: do not report stats<br>
     * -bs  - number of records to check each batch; default 1000<br>
     * -d   - the delay in ms between batches; default: no delay<br>
     * -vdr - verify data records (fetch LNs if not cached); default: do not fetch<br>
     * -vor - verify obsolete records (cleaner metadata); default: do not verify<br>
     * </p>
     *
     * <p>Note that the DbVerify command line cannot be used to verify the
     * integrity of secondary databases, because this feature requires the
     * secondary databases to have been opened by the application. To verify
     * secondary database integrity, use {@link Environment#verify} or
     * {@link com.sleepycat.je.Database#verify} instead, from within the
     * application.</p>
     *
     * <p>When running DbVerify, trace logging of individual problems is not
     * enabled because the JE environment is opened read-only. To cause output
     * to System.err, the java.util.logging.config.file JVM property may be set
     * to the name of a file containing:
     *   <pre>com.sleepycat.je.util.ConsoleHandler.level=ALL</pre>
     * Note that rate-limited logging is not used by the verifier when it is
     * run via DbVerify. This ensures that all problems are logged for
     * debugging purposes, but may produce a large amount of output.
     *
     * @throws EnvironmentFailureException if an unexpected, internal or
     * environment-wide failure occurs.
     */
    public static void main(String argv[])
        throws DatabaseException {

        DbVerify verifier = new DbVerify();
        verifier.parseArgs(argv);

        VerifySummary summary = null;
        try {
            verifier.openEnv();
            summary = verifier.verify(System.err);
            verifier.closeEnv();
            System.err.println(summary);
        } catch (Throwable T) {
            T.printStackTrace(System.err);
        }

        /*
         * Show the status, only omit if the user asked for a quiet run and
         * didn't specify a progress interval, in which case we can assume
         * that they really don't want any status output.
         *
         * If the user runs this from the command line, presumably they'd
         * like to see the status.
         */
        if (verifier.verifyConfig.getPrintInfo() ||
            (verifier.verifyConfig.getShowProgressInterval() > 0)) {
            System.err.println("Exit status = " + summary);
        }

        System.exit(!summary.hasErrors() ? 0 : -1);
    }

    DbVerify() {
    }

    /**
     * Creates a DbVerify object for a specific environment and database.
     *
     * @param env The Environment containing the database to verify.
     *
     * @param dbName The name of the database to verify.
     *
     * @param quiet true if the verification should not produce errors to the
     * output stream
     *
     * @deprecated as of 7.5, use {@link Environment#verify} or
     * {@link com.sleepycat.je.Database#verify} instead. These methods allow
     * specifying all {@link VerifyConfig} properties.
     */
    public DbVerify(Environment env,
                    String dbName,
                    boolean quiet) {
        this.env = env;
        this.dbName = dbName;
        verifyConfig.setPrintInfo(!quiet);
    }

    protected String getUsageString() {
        return usageString;
    }

    void printUsage(String msg) {
        System.err.println(msg);
        System.err.println(getUsageString());
        System.exit(-1);
    }

    void parseArgs(String argv[]) {
        verifyConfig.setPrintInfo(true);
        verifyConfig.setBatchDelay(0, TimeUnit.MILLISECONDS);

        int argc = 0;
        int nArgs = argv.length;
        while (argc < nArgs) {
            String thisArg = argv[argc++];
            if (thisArg.equals("-q")) {
                verifyConfig.setPrintInfo(false);
            } else if (thisArg.equals("-V")) {
                System.out.println(JEVersion.CURRENT_VERSION);
                System.exit(0);
            } else if (thisArg.equals("-h")) {
                if (argc < nArgs) {
                    envHome = new File(argv[argc++]);
                } else {
                    printUsage("-h requires an argument");
                }
            } else if (thisArg.equals("-s")) {
                if (argc < nArgs) {
                    dbName = argv[argc++];
                } else {
                    printUsage("-s requires an argument");
                }
            } else if (thisArg.equals("-v")) {
                if (argc < nArgs) {
                    int progressInterval = Integer.parseInt(argv[argc++]);
                    if (progressInterval <= 0) {
                        printUsage("-v requires a positive argument");
                    }
                    verifyConfig.setShowProgressInterval(progressInterval);
                } else {
                    printUsage("-v requires an argument");
                }
            } else if (thisArg.equals("-bs")) {
                if (argc < nArgs) {
                    int batchSize = Integer.parseInt(argv[argc++]);
                    if (batchSize <= 0) {
                        printUsage("-bs requires a positive argument");
                    }
                    verifyConfig.setBatchSize(batchSize);
                } else {
                    printUsage("-bs requires an argument");
                }
            } else if (thisArg.equals("-d")) {
                if (argc < nArgs) {
                    long delayMs = Long.parseLong(argv[argc++]);
                    if (delayMs < 0) {
                        printUsage("-d requires a positive argument");
                    }
                    verifyConfig.setBatchDelay(delayMs, TimeUnit.MILLISECONDS);
                } else {
                    printUsage("-d requires an argument");
                }
            } else if (thisArg.equals("-vdr")) {
                verifyConfig.setVerifyDataRecords(true);
            } else if (thisArg.equals("-vor")) {
                verifyConfig.setVerifyObsoleteRecords(true);
            }
        }

        if (envHome == null) {
            printUsage("-h is a required argument");
        }
    }

    void openEnv() {
        if (env == null) {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setReadOnly(true);
            env = new Environment(envHome, envConfig);
        }
    }

    void closeEnv() {
        try {
            if (env != null) {
                env.close();
            }
        } finally {
            env = null;
        }
    }

    private VerifySummary verify(PrintStream out) {

        final EnvironmentImpl envImpl = DbInternal.getNonNullEnvImpl(env);
        final BtreeVerifier verifier =
            new BtreeVerifier(envImpl, true /*disableRateLimitedLogging*/);
        verifyConfig.setShowProgressStream(out);
        verifier.setBtreeVerifyConfig(verifyConfig);

        if (dbName == null) {
            return verifier.verifyAll();
        } else {
            /* Get DB ID from name. */
            BasicLocker locker =
                BasicLocker.createBasicLocker(envImpl, false /*noWait*/);
            final DbTree dbTree = envImpl.getDbTree();
            DatabaseImpl dbImpl = null;
            DatabaseId dbId;

            try {
                dbImpl = dbTree.getDb(locker, dbName, null, false);
                if (dbImpl == null) {
                    return null;
                }
                dbId = dbImpl.getId();
            } finally {
                dbTree.releaseDb(dbImpl);
                locker.operationEnd();
            }

            return verifier.verifyDatabase(dbId);
        }
    }
}
