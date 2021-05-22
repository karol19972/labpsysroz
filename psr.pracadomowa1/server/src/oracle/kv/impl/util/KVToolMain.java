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

package oracle.kv.impl.util;

import oracle.kv.util.expimp.ExportImportMain.Export;
import oracle.kv.util.expimp.ExportImportMain.Import;

/**
 * Used as main class in Jar manifest for kvtool.jar. Implements the help
 * command here and delegates the execution of other commands like export/
 * import to the main() method of other classes. The first param, the command,
 * is always removed before delegating.
 */
public class KVToolMain {

    private static final String HELP_COMMAND_NAME = "help";
    private static final String HELP_COMMAND_DESC = "prints usage info";
    private static final String HELP_COMMANDS_COMMAND = "commands";

    /**
     * Abstract Command.  A Command is identified by its name, which is the
     * first arg to main().
     */
    private static abstract class Command {
        final String name;
        final String description;

        Command(String name, String description) {
            this.name = name;
            this.description = description;
        }

        abstract void run(String[] args)
            throws Exception;

        abstract String getUsageArgs();

        boolean isHelpCommand() {
            return false;
        }
    }

    /**
     * The order commands appear in the array is the order they appear in the
     * 'help' and 'help commands' output.
     */
    private static Command[] ALL_COMMANDS = {

        new Command(Export.COMMAND_NAME, Export.COMMAND_DESC) {

            @Override
            void run(String[] args)
                throws Exception {

                Export.main(makeArgs(args));
            }

            @Override
            String getUsageArgs() {
                return Export.COMMAND_ARGS;
            }
        },

        new Command(Import.COMMAND_NAME, Import.COMMAND_DESC) {

            @Override
            void run(String[] args)
                throws Exception {

                Import.main(makeArgs(args));
            }

            @Override
            String getUsageArgs() {
                return Import.COMMAND_ARGS;
            }
        },

        new Command(HELP_COMMAND_NAME, HELP_COMMAND_DESC) {

            @Override
            void run(String[] args) {
                doHelpCommand(args);
            }

            @Override
            String getUsageArgs() {
                final StringBuilder builder = new StringBuilder();
                builder.append('[');
                builder.append(HELP_COMMANDS_COMMAND);
                for (final Command cmd : ALL_COMMANDS) {
                    builder.append(" |\n\t ");
                    builder.append(cmd.name);
                }
                builder.append(']');
                return builder.toString();
            }

            @Override
            boolean isHelpCommand() {
                return true;
            }
        },
    };

    /**
     * For transforming args when delegating this main() to the specific
     * command class main().  Delete first arg (the command) and add any
     * additional args specified.
     */
    private static String[] makeArgs(String[] origArgs, String... addArgs) {
        final int useOrigArgs = origArgs.length - 1;
        final String[] newArgs = new String[useOrigArgs + addArgs.length];
        System.arraycopy(origArgs, 1, newArgs, 0, useOrigArgs);
        System.arraycopy(addArgs, 0, newArgs, useOrigArgs, addArgs.length);
        return newArgs;
    }

    /**
     * Returns the Command with the given name, or null if name is not found.
     */
    private static Command findCommand(String name) {
        for (final Command cmd : ALL_COMMANDS) {
            if (cmd.name.equals(name)) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * Delegates to Command object named by first arg.  If no args, delegates
     * to 'help' Command.
     */
    public static void main(String args[])
        throws Exception {

        final String cmdName =
            (args.length == 0) ? HELP_COMMAND_NAME : args[0];

        final Command cmd = findCommand(cmdName);
        if (cmd == null) {
            usage("Unknown command: " + cmdName);
            return;
        }

        /* Note that cmd will not be null, because usage will do an exit. */
        if (findVerbose(args) && !cmd.isHelpCommand()) {
            System.err.println("Enter command: " + cmdName);
            cmd.run(args);
            System.err.println("Leave command: " + cmdName);
        } else {
            cmd.run(args);
        }
    }

    /**
     * Returns whether -verbose appears in the arg array.  This is the only
     * arg parsing necessary in this class, prior to delegating the command.
     */
    private static boolean findVerbose(String[] args) {
        for (final String arg : args) {
            if (arg.equals(CommandParser.VERBOSE_FLAG)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Top-level usage command.
     */
    private static void usage(String errorMsg) {
        if (errorMsg != null) {
            System.err.println(errorMsg);
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(CommandParser.KVTOOL_USAGE_PREFIX);
        builder.append("\n  <");
        builder.append(ALL_COMMANDS[0].name);
        for (int i = 1; i < ALL_COMMANDS.length; i += 1) {
            builder.append(" |\n   ");
            builder.append(ALL_COMMANDS[i].name);
        }
        builder.append("> [-verbose] [args]");
        builder.append("\nUse \"help <commandName>\" to get usage for a ");
        builder.append("specific command");
        builder.append("\nUse \"help commands\" to get detailed usage ");
        builder.append("information");
        builder.append("\nUse the -verbose flag to get debugging output");
        System.err.println(builder);
        usageExit();
    }

    /**
     * Does System.exit on behalf of all usage commands.
     */
    private static void usageExit() {
        System.exit(2);
    }

    /**
     * Implements 'help', 'help commands' and 'help COMMAND'.
     */
    private static void doHelpCommand(String[] args) {

        /* Just 'help', also used for no args. */
        if (args.length <= 1) {
            usage(null);
        }

        /* 'help <something>' */
        final String cmdName = args[1];

        /* 'help commands' */
        if (HELP_COMMANDS_COMMAND.equals(cmdName)) {
            System.err.println("Commands are:");
            for (final Command cmd : ALL_COMMANDS) {
                System.err.println("  " + cmd.name + "\n\t# " +
                                   cmd.description);
            }

            usageExit();
        }

        /* 'help <command>' */
        final Command cmd = findCommand(cmdName);
        if (cmd == null) {
            usage("Unknown 'help' topic: " + args[1]);
        }

        /* Note that cmd will not be null, because usage will do an exit. */
        @SuppressWarnings("null")
        final String usageArgs = cmd.getUsageArgs();
        System.err.println
            (CommandParser.KVTOOL_USAGE_PREFIX + cmdName + " " +
             (cmd.isHelpCommand() ? "" :
              CommandParser.optional(CommandParser.VERBOSE_FLAG)) +
             ((usageArgs == null) ?  "" : ("\n\t" + usageArgs)));

        usageExit();
    }
}
