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

package oracle.kv.xregion;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.util.CommandParser;
import oracle.kv.impl.util.FormatUtils;
import oracle.kv.impl.util.RateLimitingLogger;
import oracle.kv.impl.util.client.ClientLoggerUtils;
import oracle.kv.impl.xregion.service.JsonConfig;
import oracle.kv.impl.xregion.service.ServiceMDMan;
import oracle.kv.impl.xregion.service.XRegionService;
import oracle.nosql.common.JsonUtils;
import oracle.nosql.common.contextlogger.LogFormatter;

import com.sleepycat.je.log.FileManager;

/**
 * Object represents the entry point of cross-region service agent.
 */
public class XRService {

    /** max number of logging files */
    private final static int LOG_FILE_LIMIT_COUNTS =
        Integer.getInteger("oracle.kv.xregion.logfile.count", 20);
    /** size limit of each logging file in bytes */
    private final static int LOG_FILE_LIMIT_BYTES =
        Integer.getInteger("oracle.kv.xregion.logfile.limit",
                           100 * 1024 * 1024);

    /* retry interval in ms if host region is not reachable */
    private final static int UNREACHABLE_HOST_REGION_RETRY_MS = 6 * 1000;

    private final static String DEFAULT_LOG_DIR = "log";
    private final static String LOG_FILE_SUFFIX = ".log";
    private final static String LOG_FILE_NAME_SPLITTER = ".";
    private final static String LOCK_FILE_SUFFIX = ".lck";
    private final static String PID_FILE_SUFFIX = ".pid";

    /* External commands, for "java -jar" usage. */
    public static final String START_COMMAND_NAME = "xrstart";
    public static final String START_COMMAND_DESC = "start cross-region " +
                                                    "(xregion) service";
    public static final String STOP_COMMAND_NAME = "xrstop";
    public static final String STOP_COMMAND_DESC = "stop cross-region " +
                                                   "(xregion) service";

    private static final String STOP_FORCE_FLAG = "-force";
    public static final String CONFIG_FLAG = "-config";
    public static final String START_COMMAND_ARGS =
        CommandParser.optional(CONFIG_FLAG + " <JSON config file>");
    public static final String STOP_COMMAND_ARGS =
        CommandParser.optional(CONFIG_FLAG + " <JSON config file>") + " " +
        CommandParser.optional(STOP_FORCE_FLAG);

    /* rate limiting log period in ms */
    private static final int RL_LOG_PERIOD_MS = 10 * 1000;

    /* list of arguments */
    private final String[] args;

    /* JSON configuration file for bootstrap */
    private String json;

    /*
     * false if stop the agent after pending requests are done and
     * checkpoint of each stream is done, true if immediately stop the agent,
     * the default is false
     */
    private boolean force = false;

    /* lock file manager */
    private LockFileManager lockMan;

    /* json config */
    private JsonConfig conf;

    /* command */
    private String command;

    /* logger */
    private Logger logger;

    /* rate limiting logger */
    private RateLimitingLogger<String> rlLogger;

    private XRService(final String[] args) throws IOException {
        this.args = args;
        parseArgs();

        try {
            conf = JsonConfig.readJsonFile(json);
        } catch (Exception exp) {
            final String err = "cannot parse the configuration file " + json +
                               ", " + exp.getMessage();
            throw new IllegalArgumentException(err, exp);
        }

        try {
            logger = getServiceLogger(conf);
            rlLogger = new RateLimitingLogger<>(RL_LOG_PERIOD_MS, 8, logger);
        } catch (IOException ioe) {
            final String err = "cannot create logger for region=" +
                               conf.getRegion() + ", " + ioe.getMessage();
            throw new IllegalStateException(err, ioe);
        }
        lockMan = new LockFileManager(conf);

        /* dump the json config with parameters in log file */
        logger.info(lm("JSON Configuration:\n" +
                       JsonUtils.print(conf, true)));
    }

    public static void main(String[] args) {
        try {
            final XRService xrs = new XRService(args);
            xrs.run();
        } catch (Exception exp) {
            System.err.println("Error in executing command=" +
                               args[args.length - 1] +
                               " for cross-region service, " +
                               exp.getMessage());
        }
    }

    /**
     * Builds the agent id
     *
     * @param conf json config
     * @return agent id
     */
    public static String buildAgentId(JsonConfig conf) {
        return conf.getRegion() + LOG_FILE_NAME_SPLITTER +
               conf.getAgentGroupSize() + LOG_FILE_NAME_SPLITTER +
               conf.getAgentId();
    }

    @Override
    public String toString() {
        return "command=" + command +
               ", json=" + json +
               (command.equals(STOP_COMMAND_ARGS) ? ", force=" + force : "");
    }

    /*---------------------*
     * Private Functions   *
     *---------------------*/
    private static void usage(final String message) {
        if (message != null) {
            System.err.println("\n" + message + "\n");
        }
        System.err.println("Usage: + XRService");
        System.err.println("\t[ start | stop ] " +
                           "-config <JSON config file> [ -force ]");
        System.exit(1);
    }

    /**
     * Runs one of the commands
     */
    private void run() {

        switch (command) {
            case START_COMMAND_NAME:
                runStart();
                break;
            case STOP_COMMAND_NAME:
                runStop();
                break;
            default:
                throw new IllegalStateException("Unsupported command " +
                                                command);
        }
    }

    private void runStart() {
        try {
            if (!lockMan.lock()) {
                System.err.println(
                    "Duplicate cross-region service is not allowed, id=" +
                    buildAgentId(conf));
                return;
            }
            /* get the lock */
            XRegionService service;
            int attempts = 0;
            while (true) {
                try {
                    service = new XRegionService(conf, logger);
                    break;
                } catch (ServiceMDMan.UnreachableHostRegionException exp) {
                    attempts++;
                    final String msg = "Please check if the local region=" +
                                       conf.getRegion() +
                                       " is online, will retry after " +
                                       UNREACHABLE_HOST_REGION_RETRY_MS +
                                       " ms, # attempts=" + attempts +
                                       ", error " +
                                       exp.getCause().getMessage();
                    rlLogger.log(conf.getRegion(), Level.WARNING, msg);
                    Thread.sleep(UNREACHABLE_HOST_REGION_RETRY_MS);
                }
            }

            /* add shutdown hook */
            addShutdownHook(service);
            /* start service */
            service.start();
            final String ts =
                FormatUtils.formatDateAndTime(System.currentTimeMillis());
            System.err.println("Cross-region agent (region=" +
                               conf.getRegion() +
                               ", store=" + conf.getStore() +
                               ", helpers=" +
                               Arrays.toString(conf.getHelpers()) +
                               ") starts up from config file=" +
                               json + " at " + ts);
            /* wait for service to exit */
            service.join();
        } catch (Exception exp) {
            System.err.println("Cannot start cross-region service " +
                               "agent: " + exp.getMessage());

        } finally {
            lockMan.release();
        }
    }

    private void runStop() {
        try {
            final long pid = lockMan.readPid();
            String cmd = "kill ";
            if (force) {
                cmd += "-9 ";
            }
            cmd += pid;
            final String error = runKill(cmd);
            final String ts =
                FormatUtils.formatDateAndTime(System.currentTimeMillis());
            if (error == null) {
                System.err.println("Cross-region service (pid=" + pid +
                                   ", region=" + conf.getRegion() +
                                   ", store=" + conf.getStore() +
                                   ") shuts down at " + ts);
                lockMan.deletePid();
            } else {
                System.err.println("Cannot shut down cross-region service " +
                                   "(pid=" + pid +
                                   ", region=" + conf.getRegion() +
                                   ", store=" + conf.getStore() +
                                   "), time=" + ts + ", " + error);
            }
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
        }
    }

    /**
     * Parses the argument list
     */
    private void parseArgs() {

        int nArgs = args.length;
        /* get the command */
        command = args[nArgs - 1];
        if (!command.equals(START_COMMAND_NAME) &&
            !command.equals(STOP_COMMAND_NAME)) {
            usage("invalid command: " + command);
        }

        int argc = 0;
        while (argc < nArgs - 1) {
            final String thisArg = args[argc++];
            if ("-config".equals(thisArg)) {
                if (argc < nArgs) {
                    json = args[argc++];
                } else if (args[0].equals("start")) {
                    usage("-config requires an argument to start");
                }
            } else if ("-force".equals(thisArg)) {
                force = true;
            } else {
                usage("Unknown argument: " + thisArg);
            }
        }
    }

    /**
     * Gets client side logger for agent thread
     *
     * @return client side logger
     */
    private static Logger getServiceLogger(JsonConfig conf)
        throws IOException {
        final Logger logger = ClientLoggerUtils.getLogger(
            XRService.class, XRService.class.getSimpleName());
        final String dir = createLogDirIfNotExist(conf.getAgentRoot());
        final String fileName = buildLogFileName(conf);
        final File lf = new File(dir, fileName);
        /* TODO: Provide a way for users to customize the logging config */
        final FileHandler fh = new FileHandler(lf.getAbsolutePath(),
                                               LOG_FILE_LIMIT_BYTES,
                                               LOG_FILE_LIMIT_COUNTS, true);
        fh.setFormatter(new LogFormatter(null));
        logger.addHandler(fh);
        return logger;
    }

    /* create log directory if not exist */
    private static String createLogDirIfNotExist(String path)
        throws IOException {
        final Path dir = Paths.get(path, DEFAULT_LOG_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException exp) {
                System.err.println("Cannot create log directory " +
                                   dir.getFileName() +
                                   ", error " + exp.getMessage());
                throw exp;
            }
        }
        return dir.toString();
    }

    /* build the log file name from agent id */
    private static String buildLogFileName(JsonConfig conf) {
        return buildAgentId(conf) + LOG_FILE_SUFFIX;
    }

    private static String buildLockFileName(JsonConfig conf) {
        return buildAgentId(conf) + LOCK_FILE_SUFFIX;
    }

    private static String buildPidFileName(JsonConfig conf) {
        return buildAgentId(conf) + PID_FILE_SUFFIX;
    }

    /**
     * Runs shell command
     *
     * @param cmd shell command
     * @return null if command exits normally, or error output otherwise
     * @throws IOException if fail to read the output
     * @throws InterruptedException if interrupted in waiting for result
     */
    private String runKill(String cmd) throws IOException,
        InterruptedException {

        final ProcessBuilder processBuilder = new ProcessBuilder();
        if (isWindows()) {
            /* windows not supported, but various users use windows */
            processBuilder.command("cmd.exe", "/c", cmd);
        } else {
            processBuilder.command("bash", "-c", cmd);
        }

        /* merged error with the standard output */
        processBuilder.redirectErrorStream(true);
        /* Run a shell command */
        final Process process = processBuilder.start();
        if (process.waitFor() == 0) {
           return null;
        }

        /* get error output */
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        final StringBuilder output = new StringBuilder("command output:");
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(System.getProperty("line.separator")).append(line);
        }
        return output.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private void addShutdownHook(XRegionService service) {
        logger.fine(() -> lm("Adding shutdown hook"));
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(service));
    }

    private class LockFileManager {

        private final String lockFileDir;
        private final String pidFileName;

        /* The channel and lock for the lock file. */
        private final RandomAccessFile lockFile;
        private FileLock exclLock = null;

        LockFileManager(JsonConfig conf) throws IOException {
            pidFileName = buildPidFileName(conf);
            lockFileDir = conf.getAgentRoot();
            /* lock file name and dir */
            final String lockFileName = buildLockFileName(conf);
            lockFile = new RandomAccessFile(
                new File(lockFileDir, lockFileName),
                FileManager.FileMode.READWRITE_MODE.getModeValue());
        }

        public boolean lock() throws IOException {
            final FileChannel channel = lockFile.getChannel();
            try {
                /* lock exclusive */
                exclLock = channel.tryLock(0, 1, false);
                final boolean succ = (exclLock != null);
                if (succ) {
                    /* delete previous pid file if existent */
                    deletePid();
                    /* persist process id for stop */
                    writePid();
                }
                return succ;
            } catch (OverlappingFileLockException e) {
                return false;
            }
        }

        public void release() {
            try {
                if (exclLock != null) {
                    exclLock.release();
                }
            } catch (Exception e) {
                /* ignore? */
            }
        }

        /* read pid from pid file */
        long readPid() throws IOException {
            final File file = new File(lockFileDir, pidFileName);
            if (!file.exists()) {
                throw new IOException("Cannot find PID file=" +
                                      file.getAbsolutePath() +
                                      ", check the file and if the " +
                                      "service has already shut down.");
            }
            if (file.length() == 0) {
                throw new IOException("Empty PID file: " +
                                      file.getAbsolutePath());
            }
            /* only read the first line */
            final String line = Files.lines(file.toPath()).iterator().next();
            return Long.parseLong(line);
        }

        /* delete pid file */
        void deletePid() {
            final File file = new File(lockFileDir, pidFileName);
            if (!file.exists()) {
                return;
            }
            file.delete();
        }

        /*  write pid to pid file */
        private void writePid() throws IOException {
            final long pid = getPid();
            if (pid == 0) {
                final String err = "Cannot determine process id";
                throw new IOException(err);
            }
            final List<String> lines =
                Collections.singletonList(String.valueOf(pid));
            final Path file = Paths.get(lockFileDir, pidFileName);
            try {
                Files.write(file, lines);
            } catch (IOException ioe) {
                logger.warning(lm("Cannot write process id=" + pid +
                                  " to pid file=" + file.toAbsolutePath()));
                throw ioe;
            }
        }

        /* get pid from OS */
        private long getPid() {
            //TODO: simplify when upgrade to Java 9+
            // return ProcessHandle.current().pid();

            /* Java 8 */
            final String processName =
                ManagementFactory.getRuntimeMXBean().getName();
            if (processName != null && processName.length() > 0) {
                try {
                    return Long.parseLong(processName.split("@")[0]);
                }
                catch (Exception e) {
                    return 0;
                }
            }
            return 0;
        }
    }

    /* Provide a shutdown hook so that if the service is killed externally */
    private class ShutdownThread extends Thread {

        private final XRegionService service;

        ShutdownThread(XRegionService service) {
            this.service = service;
        }

        @Override
        public void run() {
            logger.info(lm("Shutdown thread running, stopping services"));
            try {
                service.shutdown();
                /* wait for shutdown complete */
                service.join();
            } catch (Exception exp) {
                /* ignored in shut down */
            } finally {
                logger.info(lm("Shutdown complete"));
            }
        }
    }

    private String lm(String msg) {
        return "[XRegionService] " + msg;
    }
}
