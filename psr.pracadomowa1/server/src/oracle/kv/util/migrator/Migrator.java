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

package oracle.kv.util.migrator;

import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;

import java.io.File;
import java.io.PrintStream;
import java.util.Set;
import java.util.logging.Logger;

import oracle.kv.impl.util.CommandParser;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkFactory;
import oracle.kv.util.migrator.impl.sink.ondbbinary.OndbBinaryFileSinkFactory;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory;
import oracle.kv.util.migrator.impl.source.ondbbinary.OndbBinaryFileSourceFactory;
import oracle.nosql.common.migrator.MigrateConfig;
import oracle.nosql.common.migrator.MigratorBase;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.sink.json.JsonSinkFactory;
import oracle.nosql.common.migrator.impl.source.json.JsonSourceFactory;
import oracle.nosql.common.migrator.impl.source.mongodbjson.MongoJsonSourceFactory;

public class Migrator extends MigratorBase {

    private final static String COMMAND_USAGE =
        CommandParser.MIGRATOR_USAGE_PREFIX + "<options>\n" +
        "<options> must be one of:\n\t" +
        MainCommandParser.SOURCES_FLAG +
            " -- lists valid migration source type\n\t" +
        MainCommandParser.SINKS_FLAG +
            " -- lists valid migration sink type\n\t" +
        MainCommandParser.CONFIG_FLAG +
            " <config-file> -- migrate based on the file configuration\n\t" +
        MainCommandParser.SOURCE_FLAG +
            " <source> -- migrate from the specified source.\n\t" +
        "For additional usage of this option use:\n\t" +
        CommandParser.MIGRATOR_USAGE_PREFIX +
            MainCommandParser.SOURCE_FLAG + " <source> -help" ;

    /* Used for unit test only */
    static boolean dontExit = false;

    static {
        /* data sources */
        addSource(new MongoJsonSourceFactory());
        addSource(new JsonSourceFactory());
        addSource(new OndbSourceFactory());
        addSource(new OndbBinaryFileSourceFactory());

        /* data sinks */
        addSink(new OndbSinkFactory());
        addSink(new OndbBinaryFileSinkFactory());
        addSink(new JsonSinkFactory());
    }

    public Migrator(MigrateConfig config,
                    Logger logger,
                    PrintStream verboseOut) {
        super(config, logger, verboseOut);
    }

    private Migrator(File configFile, PrintStream verboseOut) {
        super(configFile, verboseOut);
    }

    private Migrator(String sourceType,
                     String sinkType,
                     MigratorCommandParser parser,
                     PrintStream verboseOut) {
        super(sourceType, sinkType, parser, verboseOut);
    }

    /**
     * Don't public the main() method, because we don't want to expose the
     * command line of Migrator. Keep this for test only.
     */
    static void main(String args[]) {

        MainCommandParser parser = new MainCommandParser(args);
        parser.parseArgs();

        if (parser.getListSources() || parser.getListSinks()) {
            Set<String> types;
            String info;
            if (parser.getListSources()) {
                types = MigratorBase.getSourceTypes(parser.getShowHidden());
                info = formatTypes("sources", types);
            } else {
                types = MigratorBase.getSinkTypes(parser.getShowHidden());
                info = formatTypes("sinks", types);
            }
            exit(ExitCode.EXIT_OK, info);
        }

        try {
            Migrator migrator;
            PrintStream out = (parser.getVerbose() ? System.out : null);
            if (parser.getConfigFile() != null) {
                migrator = new Migrator(new File(parser.getConfigFile()), out);
            } else {
                migrator = new Migrator(parser.getSourceType(),
                                        parser.getSinkType(),
                                        parser,
                                        out);
            }
            migrator.run();
        } catch (Exception ex) {
            exit(ExitCode.EXIT_FAILED, "Migrate failed. " + ex.getMessage());
        }
        exit(ExitCode.EXIT_OK, "Migrate complete.");
    }

    public static String getCommandUsage() {
        return COMMAND_USAGE;
    }

    public static void usage(String usageText, String errorMsg) {
        exit(ExitCode.EXIT_USAGE,
             ((errorMsg != null)? errorMsg + "\n\n" : "") +
             ((usageText == null)? getCommandUsage() : usageText));
    }

    private static String formatTypes(String name, Set<String> types) {
        StringBuilder sb = new StringBuilder("Valid ");
        sb.append(name).append(": ");
        boolean first = true;
        for (String type : types) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(type);
        }
        return sb.toString();
    }

    public static void validateSourceSink(String sourceType, String sinkType) {

        /* nosqldb-binary source can only be with nosqldb sink */
        if (sourceType.equals(ONDB_BINARY_TYPE) &&
            !sinkType.equals(ONDB_TYPE)) {
            throw new IllegalArgumentException(
                "Invalid Source/Sink combination, the source " +
                ONDB_BINARY_TYPE + " can only be with sink " + ONDB_TYPE);
        }

        /* nosqldb-binary sink can only be with nosqldb source */
        if (sinkType.equals(ONDB_BINARY_TYPE) &&
            !sourceType.equals(ONDB_TYPE)) {
            throw new IllegalArgumentException(
                "Invalid Source/Sink combination, the sink " +
                ONDB_BINARY_TYPE + " can only be with source " + ONDB_TYPE);
        }
    }

    private static void exit(ExitCode exitCode, String errorMsg) {
        if (dontExit) {
            if (exitCode != ExitCode.EXIT_OK) {
                System.err.println(errorMsg);
            }
            return;
        }
        System.err.println(errorMsg);
        System.exit(exitCode.value());
    }

    /*
     * The possible return codes for the migrator utility, obeying the Unix
     * convention that a non-zero value is an abnormal termination.
     */
    public static enum ExitCode {

        EXIT_OK(0, "Operation completed"),
        EXIT_USAGE(100, "Usage error"),
        EXIT_FAILED(101, "Operation failed");

        /* A value that is a valid Unix return code, in the range of 0-127 */
        private final int returnCode;
        private final String description;

        ExitCode(int returnCode, String description) {
            this.returnCode = returnCode;
            this.description = description;
        }

        public int value(){
            return this.returnCode;
        }

        public String getDescription() {
            return description;
        }
    }
}
