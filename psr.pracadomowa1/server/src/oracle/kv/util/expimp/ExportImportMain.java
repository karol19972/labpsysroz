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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TimeZone;

import oracle.kv.impl.util.CommandParser;
import oracle.kv.util.expimp.ExitHandler.ExitCode;

/**
 * This class contains the implementaion for export and import commands.
 * KVToolMain class delegates the execution of the export/import commands
 * to this class.
 */
public class ExportImportMain {

    public static final String REGEX = "\\s*,\\s*";
    public static final String EXPORT_TYPE = "export-type";
    public static final String CONFIG_FILE = "-config";
    public static boolean expimpJson = false;

    public static final String REQUEST_TIMEOUT_MS = "request-timeout-ms";
    public static final String[] expImpOptParams = {REQUEST_TIMEOUT_MS};

    /*
     * Parameters in config file specific to local file system
     */
    public static final String
        EXPORT_PACKAGE_PATH_PARAM = "export-package-path";

    public static String[] localStoreParams = {EXPORT_PACKAGE_PATH_PARAM};

    /*
     * Parameters in config file specific to oracle storage cloud service
     */
    public static final String CONTAINER_NAME_PARAM = "container-name";
    public static final String SERVICE_NAME_PARAM = "service-name";
    public static final String USER_NAME_PARAM = "user-name";
    public static final String PASSWORD_PARAM = "password";
    public static final String SERVICE_URL_PARAM = "service-url";

    public static String[] objectStoreParams =
        {CONTAINER_NAME_PARAM, SERVICE_NAME_PARAM,
         USER_NAME_PARAM, PASSWORD_PARAM, SERVICE_URL_PARAM};

    /*
     * Parameters in config file specific to import
     */

    /*
     * TTL parameters
     */
    public static final String TTL_PARAM = "ttl";
    public static final String TTL_RELATIVE_DATE_PARAM = "ttl-relative-date";

    public static final String JSON_EXPORT_PARAM = "-json-export";

    public static final String USE_EXPIMP_FLAG = "-use-old-expimp";
    public static final String FORMAT_FLAG = "-format";

    /*
     * Bulk put tuning parameters
     */
    public static final String STREAM_PARALLELISM_PARAM ="stream-parallelism";
    public static final String
        PER_SHARD_PARALLELISM_PARAM = "per-shard-parallelism";
    public static final String BULK_HEAP_PERCENT_PARAM = "bulk-heap-percent";

    public static String[] importSpecificParams =
        {TTL_PARAM, TTL_RELATIVE_DATE_PARAM, STREAM_PARALLELISM_PARAM,
         PER_SHARD_PARALLELISM_PARAM, BULK_HEAP_PERCENT_PARAM};

    public static Map<String, String> importSpecificParamMap =
        new HashMap<String, String>();

    public static final String relativeFlagValue = "RELATIVE";
    public static final String absoluteFlagValue = "ABSOLUTE";

    /*
     * Parameters in config file specific to export
     */
    public static final String CONSISTENCY_PARAM = "consistency";
    public static final String TIME_LAG_PARAM = "permissible-lag";

    public static String[] exportSpecificParams =
        {CONSISTENCY_PARAM, TIME_LAG_PARAM};

    public static Map<String, String> exportSpecificParamMap =
            new HashMap<String, String>();

    public static final String timeConsistencyFlagValue = "TIME";
    public static final String absoluteConsistencyFlagValue = "ABSOLUTE";
    public static final String noConsistencyFlagValue = "NONE";

    /*
     * Parameters in config file specific to export JSON
     */
    public static final String PRETTY_PARAM = "pretty";
    public static final String CONCURRENCY_PARAM = "concurrency";
    public static final String FILE_SIZE_MB_PARAM = "file-size-mb";

    public static String[] exportJsonSpecificParams =
        {PRETTY_PARAM, CONCURRENCY_PARAM, FILE_SIZE_MB_PARAM};

    /*
     * Parameters in config file specific to import JSON
     */
    public static final String OVERWRITE_PARAM = "overwrite";
    public static final String DDL_SCHEMA_FILE_PARAM = "ddl-schema-file";
    public static String[] importJsonSpecificParams =
        {OVERWRITE_PARAM, DDL_SCHEMA_FILE_PARAM};

    /*
     * Parameters in config file specific to import MongoDB JSON
     */
    public static final String DATETIME_TO_LONG_PARAM = "datetime-to-long";
    public static String[] importMongoDBJsonSpecificParams =
        {DATETIME_TO_LONG_PARAM};

    /*
     * Supported export stores
     */
    public static enum ExportStoreType {
        FILE("LOCAL"),
        OCI_OBJECT_STORE("OBJECT_STORE");

        private String alias;

        private ExportStoreType(String alias) {
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }

        public String toString(boolean includeAlias) {
            if (includeAlias && getAlias() != null) {
                return toString() + "(" + getAlias() + ")";
            }
            return toString();
        }

        public static ExportStoreType get(String name) {
            for (final ExportStoreType est :
                 EnumSet.allOf(ExportStoreType.class)) {
                if (est.getAlias().equalsIgnoreCase(name) ||
                    est.name().equalsIgnoreCase(name)) {
                    return est;
                }
            }
            throw new IllegalArgumentException(
                "No enum constant " + ExportStoreType.class.getCanonicalName() +
                "." + name);
        }
    }

    /*
     * Supported data format
     */
    public enum DataFormat {
        BINARY,
        JSON,
        MONGODB_JSON
    }

    /**
     * Argument parser for export/import commands
     */
    public static abstract class ExportImportParser extends CommandParser {

        private DataFormat DEF_FORMAT = DataFormat.BINARY;
        private String tableNames = null;
        private String helperHosts = null;
        private String namespaces = null;
        private boolean useOldExpImp = false;
        private DataFormat dataFormat;
        private String configFile = null;
        private boolean isJsonConfig = false;

        ExportImportParser(String[] args) {
            super(args);
        }

        @Override
        protected boolean checkArg(String arg) {

            if (arg.equals(HELPER_HOSTS_FLAG)) {
                helperHosts = nextArg(arg);
                return true;
            }

            if (arg.equals(TABLE_FLAG)) {
                tableNames = nextArg(arg);
                return true;
            }

            if (arg.equals(JSON_EXPORT_PARAM)) {
                return true;
            }

            if (arg.equals(NAMESPACE_FLAG)) {
                namespaces = nextArg(arg);
                return true;
            }

            if (arg.equals(CONFIG_FILE)) {
                configFile = nextArg(arg);
                return true;
            }

            if (arg.equals(FORMAT_FLAG)) {
                dataFormat = nextEnumArg(arg, DataFormat.class);
                return true;
            }

            if (arg.equals(USE_EXPIMP_FLAG)) {
                useOldExpImp = true;
                return true;
            }
            return false;
        }

        @Override
        protected void verifyArgs() {

            expimpJson = getJson();

            if (getStoreName() == null) {
                missingArg(STORE_FLAG);
            }

            if (helperHosts == null) {
                missingArg(HELPER_HOSTS_FLAG);
            }

            if (configFile == null) {
                missingArg(CONFIG_FILE);
            } else {
                File cfgFile = new File(configFile);
                if (!cfgFile.exists() || !cfgFile.isFile()) {
                    usage("Invalid config file, it doesn't exists or not a " +
                          "valid file: " + configFile);
                }
                isJsonConfig = checkIsJsonConfig(cfgFile);
            }

            if (useOldExpImp) {
                if (getDataFormat() != DataFormat.BINARY) {
                    usage(USE_EXPIMP_FLAG + " can only be used for " +
                          DataFormat.BINARY + " format");
                }
                if (isJsonConfig) {
                    usage(USE_EXPIMP_FLAG + " can not be used with JSON " +
                          "format config file");
                }
            }

            if (getDataFormat() != DataFormat.BINARY && !isJsonConfig) {
                usage("Invalid config file, it must be in JSON format: " +
                      configFile);
            }
        }

        public String getHelperHosts() {
            return helperHosts;
        }

        public String getTableNames() {
            return tableNames;
        }

        public String getNamespaces() {
            return namespaces;
        }

        public boolean getUseMigrator() {
            return !useOldExpImp;
        }

        public String getConfigFile() {
            return configFile;
        }

        public boolean getIsJsonConfig() {
            return isJsonConfig;
        }

        public DataFormat getDataFormat() {
            return (dataFormat != null) ? dataFormat : DEF_FORMAT;
        }

        public static String getHelperHostsUsage() {
            return HELPER_HOSTS_FLAG + " <helper_hosts>";
        }

        public static String getTableUsage() {
            return TABLE_FLAG + " <table_names>";
        }

        public static String getConfigFileUsage() {
            return CONFIG_FILE + " <config_file_name>";
        }

        public static String getNamespaceUsage() {
            return NAMESPACE_FLAG + " <namespaces>";
        }

        public static String getDataFormatUsage(DataFormat[] formats) {
            StringBuilder sb = new StringBuilder(FORMAT_FLAG);
            sb.append(" <");
            boolean first = true;
            for (DataFormat fmt : formats) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" | ");
                }
                sb.append(fmt.name());
            }
            sb.append(">");
            return sb.toString();
        }
    }

    /**
     * Argument parser specific to import
     */
    public static class ImportParser extends ExportImportParser {

        public static final String IMPORT_ALL_FLAG = "-import-all";
        public static final String EXTERNAL_FLAG = "-external";
        public static final String STATUS_FLAG = "-status";

        enum ImportType {
            ALL(IMPORT_ALL_FLAG, IMPORT_ALL_FLAG),
            TABLE(TABLE_FLAG, getTableUsage()),
            NAMESPACE(NAMESPACE_FLAG, getNamespaceUsage()),
            EXTERNAL(EXTERNAL_FLAG, EXTERNAL_FLAG);

            private String flag;
            private String usage;
            ImportType(String flag, String usage) {
                this.flag = flag;
                this.usage = usage;
            }

            String getFlag() {
                return flag;
            }

            String getUsage() {
                return usage;
            }
        }

        private ImportType impType = null;
        private String status = null;

        @Override
        protected boolean checkArg(String arg) {

            if (arg.equals(IMPORT_ALL_FLAG)) {
                setImportType(ImportType.ALL);
                return true;
            }

            if (arg.equals(EXTERNAL_FLAG)) {
                setImportType(ImportType.EXTERNAL);
                return true;
            }

            if (arg.equals(STATUS_FLAG)) {
                status = nextArg(arg);
                return true;
            }

            if (arg.equals(TABLE_FLAG)) {
                setImportType(ImportType.TABLE);
                /*
                 * Don't return but continue to super.checkArg() to parse the
                 * value of argument.
                 */
            }

            if (arg.equals(NAMESPACE_FLAG)) {
                setImportType(ImportType.NAMESPACE);
                /*
                 * Don't return but continue to super.checkArg() to parse the
                 * value of argument.
                 */
            }
            return super.checkArg(arg);
        }

        private void setImportType(ImportType type) {
            if (impType != null) {
                String msg = "Found flags: " + impType.getFlag() +
                    " | " + type.getFlag() + ". Please use either one of " +
                    "the flags to perform the import";
                usage(msg);
            }
            impType = type;
        }

        public ImportParser(String[] args) {
            super(args);
        }

        @Override
        public void usage(String errorMsg) {
            exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
        }

        @Override
        protected void verifyArgs() {

            super.verifyArgs();

            if (impType == null) {
                String errMsg = "Missing flags: " + getImportTypeUsage() +
                  ". Please use either one of the flags to perform the import";
                usage(errMsg);
            }

            if (impType == ImportType.EXTERNAL) {
                if (getDataFormat() == DataFormat.BINARY) {
                    String errMsg = "Import " + DataFormat.BINARY +
                        " can not be used with " + ImportType.EXTERNAL.getFlag();
                    usage(errMsg);
                }
            } else {
                if (getDataFormat() == DataFormat.MONGODB_JSON) {
                    String errMsg = "Import " + DataFormat.MONGODB_JSON +
                        " must be used with " + ImportType.EXTERNAL.getFlag();
                    usage(errMsg);
                }
            }
        }

        public boolean importAll() {
            return impType == ImportType.ALL;
        }

        public boolean isExternal() {
            return impType == ImportType.EXTERNAL;
        }

        public String getStatus() {
            return status;
        }

        public static String getImportTypeUsage() {
            StringBuilder sb = new StringBuilder();
            for (ImportType t : ImportType.values()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(t.getUsage());
            }
            return sb.toString();
        }

        public static String getStatusFlagUsage() {
            return STATUS_FLAG + " <status_file>";
        }
    }

    /**
     * Argument parser specific to export
     */
    public static class ExportParser extends ExportImportParser {

        public static final String EXPORT_ALL_FLAG = "-export-all";

        enum ExportType {
            ALL(EXPORT_ALL_FLAG, EXPORT_ALL_FLAG),
            TABLE(TABLE_FLAG, getTableUsage()),
            NAMESPACE(NAMESPACE_FLAG, getNamespaceUsage());

            private String flag;
            private String usage;
            ExportType(String flag, String usage) {
                this.flag = flag;
                this.usage = usage;
            }

            String getFlag() {
                return flag;
            }

            String getUsage() {
                return usage;
            }
        }

        private ExportType expType;
        /*
         * Use to enable using original Export to do export JSON, this is used
         * by OCI export utility in cloud
         */
        private boolean jsonExport = false;

        public ExportParser(String[] args) {
            super(args);
        }

        @Override
        protected boolean checkArg(String arg) {

            if (arg.equals(EXPORT_ALL_FLAG)) {
                setExportType(ExportType.ALL);
                return true;
            }

            if (arg.equals(JSON_EXPORT_PARAM)) {
                jsonExport = true;
                return true;
            }

            if (arg.equals(TABLE_FLAG)) {
                setExportType(ExportType.TABLE);
                /*
                 * Don't return but continue to super.checkArg() to parse the
                 * value of argument.
                 */
            }

            if (arg.equals(NAMESPACE_FLAG)) {
                setExportType(ExportType.NAMESPACE);
                /*
                 * Don't return but continue to super.checkArg() to parse the
                 * value of argument.
                 */
            }
            return super.checkArg(arg);
        }

        private void setExportType(ExportType type) {
            if (expType != null) {
                String msg = "Found flags: " + expType.getFlag() +
                    " | " + type.getFlag() + ". Please use either one of " +
                    "the flags to perform the export";
                usage(msg);
            }
            expType = type;
        }

        @Override
        public void usage(String errorMsg) {

            exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
        }

        @Override
        protected void verifyArgs() {

            super.verifyArgs();

            if (expType == null) {
                String errMsg = "Missing flags: " + getExportTypeUsage() +
                    ". Please use either one of the flags to perform the export";
                usage(errMsg);
            }
        }

        public boolean exportAll() {
            return expType == ExportType.ALL;
        }

        public boolean getJsonExport() {
            return jsonExport;
        }

        @Override
        public boolean getUseMigrator() {
            if (jsonExport) {
                /*
                 * If -json-export is specified, use original Export to do
                 * JSON export.
                 */
                return false;
            }
            return super.getUseMigrator();
        }

        public static String getExportTypeUsage() {
            StringBuilder sb = new StringBuilder();
            for (ExportType t : ExportType.values()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(t.getUsage());
            }
            return sb.toString();
        }
    }

    /**
     * Returns value for a given parameter from the list of parameters in the
     * command line arguments
     */
    public String getParam(String args[],
                           String param,
                           String operation) {

        int argC;
        for (argC = 0; argC < args.length; argC++) {
            if (args[argC].equals(param)) {
                break;
            }
        }

        if (argC == args.length) {

            String errorMsg = "Flag " + param + " is required";
            printToConsole(ExitCode.EXIT_USAGE, errorMsg, operation);
            return null;
        }

        if (argC == args.length - 1) {

            String errorMsg = "Flag " + param + " requires an argument";
            printToConsole(ExitCode.EXIT_USAGE, errorMsg, operation);
            return null;
        }

        return args[++argC];
    }

    /**
     * Parses config file containing parameters for a given export store
     */
    public Map<String, String> parseConfigFile(File file, String operation) {

        /*
         * Map that holds all the config file parameters
         */
        Map<String, String> configParameters = new HashMap<String, String>();

        try {
            Properties prop = new Properties();
            InputStream inputStream = new FileInputStream(file);

            prop.load(inputStream);

            for (Entry<Object, Object> entry : prop.entrySet()) {
                String name = (String)entry.getKey();
                String value = (String)entry.getValue();

                configParameters.put(name, value);
            }

            /*
             * Add parameter in expImpOptionalParams and its value to
             * configParameters if exits.
             */
            for (String param : expImpOptParams) {
                String value = configParameters.get(param);
                if (value != null) {
                    configParameters.put(param, value);
                }
            }

            /*
             * Move the import specific params from configParameters to
             * a separate map. Import specific params will be validated
             * separately later.
             */
            for (String impSpecificParam : importSpecificParams) {
                String value = configParameters.get(impSpecificParam);

                if (value != null) {
                    importSpecificParamMap.put(impSpecificParam, value);
                    configParameters.remove(impSpecificParam);
                }
            }

            /*
             * Move the export specific params from configParameters to
             * a separate map. Export specific params will be validated
             * separately later.
             */
            for (String expSpecifigParam : exportSpecificParams) {
                String value = configParameters.get(expSpecifigParam);

                if (value != null) {
                    exportSpecificParamMap.put(expSpecifigParam, value);
                    configParameters.remove(expSpecifigParam);
                }
            }

            /*
             * Parses specific params for json export
             */
            for (String param : exportJsonSpecificParams) {
                String value = configParameters.get(param);
                if (value != null) {
                    exportSpecificParamMap.put(param, value);
                    configParameters.remove(param);
                }
            }

            /*
             * Parses specific params for json import
             */
            for (String param : importJsonSpecificParams) {
                String value = configParameters.get(param);
                if (value != null) {
                    importSpecificParamMap.put(param, value);
                    configParameters.remove(param);
                }
            }

            /*
             * Parses specific params for mongo-json import
             */
            for (String param : importMongoDBJsonSpecificParams) {
                String value = configParameters.get(param);
                if (value != null) {
                    importSpecificParamMap.put(param, value);
                    configParameters.remove(param);
                }
            }
        } catch (FileNotFoundException e) {
            exit(ExitCode.EXIT_USAGE, "Config file: " + file.getName() +
                 " not found in the path provided.", operation);
        } catch (IOException ioe) {
            exit(ExitCode.EXIT_UNEXPECTED, "Exception reading config file: " +
                 file.getName(), operation);
        }

        String exportType = configParameters.get(EXPORT_TYPE);
        if (exportType == null) {
            String errorMsg = EXPORT_TYPE + " must be specified in the " +
                              "config file specified by -config";
            exit(ExitCode.EXIT_USAGE, errorMsg, operation);
        } else {
            try {
                ExportStoreType type = ExportStoreType.get(exportType);
                switch (type) {
                case FILE:
                    validateConfigParams(configParameters,
                                         localStoreParams,
                                         operation);
                    break;
                case OCI_OBJECT_STORE :
                    validateConfigParams(configParameters,
                                         objectStoreParams,
                                         operation);
                    break;
                default:
                    boolean isValidStore =
                        isValidStore(exportType.toUpperCase());
                    if (isValidStore && getStoreParams() != null) {
                        validateConfigParams(configParameters,
                                             getStoreParams(),
                                             operation);
                        break;
                    }
                    throw new IllegalArgumentException(
                        "Unsupported export type: " + exportType);
                }
            } catch (IllegalArgumentException iae) {
                String errorMsg = getErrorMessage();
                exit(ExitCode.EXIT_INVALID_EXPORT_STORE, errorMsg, operation);
            }
        }

        return configParameters;
    }

    @SuppressWarnings("unused")
    protected boolean isValidStore(String storeName) {

        return false;
    }

    protected String[] getStoreParams() {
        return null;
    }

    protected String getErrorMessage() {
        String errorMsg = EXPORT_TYPE + " parameter " +
            "specified in the config file currently only " +
            "supports " + ExportStoreType.FILE.toString(true) + " and " +
            ExportStoreType.OCI_OBJECT_STORE.toString(true) +
            " as valid values";

        return errorMsg;
    }

    /**
     * Validate the parameters defined in the config file
     *
     * @param configParams parameters in the config file
     * @param validParams valid parameters for the given export type
     * @param operation export or import operation
     */
    public void validateConfigParams(Map<String, String> configParams,
                                     String[] validParams,
                                     String operation) {

        /*
         * All the validParams must be present in the config params
         */
        for (String param : validParams) {
            if (!configParams.containsKey(param)) {
                String errorMsg = param + " must be specified in the " +
                    "config file specified by -config";
                exit(ExitCode.EXIT_USAGE, errorMsg, operation);
            } else if (configParams.get(param).trim().equals("")) {
                String errorMsg = param + " specified in the config " +
                    "cannot have an empty value";
                exit(ExitCode.EXIT_USAGE, errorMsg, operation);
            }
        }

        /*
         * Config params must have only those parameters that are defined in
         * valid params or expImpOptParams. Additional unknown parameters must
         * not be present in the config file
         */
        for (String param : configParams.keySet()) {
            if (param.equals(EXPORT_TYPE) ||
                parameterInArray(validParams,param) ||
                parameterInArray(expImpOptParams, param)) {
                continue;
            }

            String errorMsg = "Invalid parameter " + param + " specified " +
                "in the config file";
            exit(ExitCode.EXIT_USAGE, errorMsg, operation);
        }

        /* Validate the value of requestTimeout */
        String requestTimeout = configParams.get(REQUEST_TIMEOUT_MS);
        if (requestTimeout != null) {
            String errorMsg = "Invalid value " + requestTimeout +
                " specified for param " + REQUEST_TIMEOUT_MS +
                " in the config file.";
            try {
                Integer.parseInt(requestTimeout);
            } catch (NumberFormatException e) {
                exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
            }
        }
    }

    private boolean parameterInArray(String[] array, String param) {
        for (String str : array) {
            if (str.equals(param)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Print the message to console
     */
    private static void printToConsole(ExitCode exitCode,
                                       String errorMsg,
                                       String operation) {
        if (expimpJson) {
            /*
             * Display the message in json format
             */
            ExitHandler
                .displayExitJson(System.err, exitCode, errorMsg, operation);
        } else {
            /*
             * Display the error message
             */
            System.err.println(errorMsg);

            String commandArgs = "";

            if (operation.equals(Export.COMMAND_NAME)) {
                commandArgs = Export.COMMAND_ARGS;
            } else {
                commandArgs = Import.COMMAND_ARGS;
            }

            /*
             * Display the command usage
             */
            System.err.println(CommandParser.KVTOOL_USAGE_PREFIX + operation +
                               "\n\t" + commandArgs);
        }
    }

    protected static void exit(ExitCode exitCode,
                               String errorMsg,
                               String operation) {

        printToConsole(exitCode, errorMsg, operation);
        System.exit(exitCode.value());
    }

    /**
     * Removes the arg from the args arrays. Returns a new array without arg.
     */
    public String[] removeArg(String[] args, String arg) {

        String[] allArgs = new String[args.length - 2];
        int index = 0;

        for (int i = 0; i < args.length; i++) {

            /*
             * Ignore arg
             */
            if (args[i].equals(arg)) {
                i++;
                continue;
            }

            allArgs[index++] = args[i];
        }

        return allArgs;
    }

    /**
     * Return true if the named argument is in the command array.
     * The arg parameter is expected to be in lower case.
     */
    public boolean checkArg(String[] args, String arg) {
        for (String s : args) {
            String sl = s.toLowerCase();
            if (sl.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Implementation class for Export command
     */
    public static class Export {
        public static final String COMMAND_NAME = "export";
        public static final String COMMAND_DESC = "Exports the entire " +
            "kvstore or a given table to a export store. Use config file " +
            "to specify export store parameters.";

        private static DataFormat[] suppFormats = new DataFormat[] {
            DataFormat.BINARY,
            DataFormat.JSON
        };

        public static final String COMMAND_ARGS =
            ExportParser.getExportTypeUsage() + "\n\t" +
            CommandParser.getStoreUsage() + " " +
            ExportImportParser.getHelperHostsUsage() + "\n\t" +
            ExportImportParser.getConfigFileUsage() + "\n\t" +
            CommandParser.optional(
                ExportImportParser.getDataFormatUsage(suppFormats)) + "\n\t" +
            CommandParser.optional(ExportImportParser.getUserUsage()) + " " +
            CommandParser.optional(ExportImportParser.getSecurityUsage()) +
            "\n\n";

        protected static void validateExportSpecificParams() {
            String consistency = exportSpecificParamMap.get(CONSISTENCY_PARAM);
            String timeLag =
                exportSpecificParamMap.get(TIME_LAG_PARAM);

            if (consistency != null &&
                !(consistency.toUpperCase()
                    .equals(absoluteConsistencyFlagValue) ||
                   consistency.toUpperCase().equals(timeConsistencyFlagValue) ||
                   consistency.toUpperCase()
                    .equals(noConsistencyFlagValue))) {

                String errorMsg = CONSISTENCY_PARAM + " parameter specified " +
                    "in the config file currently only supports " +
                    "ABSOLUTE, TIME and NONE as valid values";
                exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
            }

            if ((consistency == null ||
                 !consistency.toUpperCase()
                     .equals(timeConsistencyFlagValue)) && (timeLag != null)) {

                String errorMsg = TIME_LAG_PARAM + " parameter can be " +
                    "specified in the config file only if " +
                    CONSISTENCY_PARAM + " is " + timeConsistencyFlagValue +
                    ". Please add " + TIME_LAG_PARAM + " = " +
                    timeConsistencyFlagValue + " in the config file.";
                exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
            }

            if (consistency != null && consistency
                    .toUpperCase().equals(timeConsistencyFlagValue) &&
                    timeLag == null) {

                String errorMsg = TIME_LAG_PARAM + " parameter " +
                    "not specified for TIME consistency.";
                exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
            }

            if (timeLag != null) {

                String errorMsg = "Invalid value " + timeLag + " specified for"
                    + " param " + TIME_LAG_PARAM + " in the config file.";

                try {
                    Integer.parseInt(timeLag);
                } catch (NumberFormatException e) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
                }
            }
        }

        public static void main(String[] args) {

            ExportImportMain expimpMain = new ExportImportMain();

            ExportParser exportParser = new ExportParser(args);
            exportParser.parseArgs();

            /*
             * Get the configFile from the command line arguments
             */
            Map<String, String> configParams = null;
            File cfgFile = new File(exportParser.getConfigFile());
            if (!exportParser.getIsJsonConfig()) {
                /*
                 * Parse config file and retrieve all the parameters
                 */
                configParams =
                    expimpMain.parseConfigFile(cfgFile, Export.COMMAND_NAME);

                /*
                 * Validate the export specific parameters separately
                 */
                validateExportSpecificParams();

                /*
                 * Export config file should not contain import specific config
                 * parameters
                 */
                for (String param : importSpecificParams) {
                    if (importSpecificParamMap.containsKey(param)) {
                        String errorMsg = "Invalid parameter " + param +
                            " specified in the config file";
                        exit(ExitCode.EXIT_USAGE, errorMsg, Export.COMMAND_NAME);
                    }
                }
            }

            if (exportParser.getUseMigrator()) {
                runMigrator(exportParser, configParams, exportSpecificParamMap);
            } else {
                runExport(exportParser, configParams);
            }
        }

        private static void runMigrator(ExportParser exportParser,
                                        Map<String, String> configParams,
                                        Map<String, String> sepcificParams) {

            MigratorExportImport migExpImp = null;
            ExitCode exitCode = null;
            String errMsg = null;
            try {
                migExpImp = new MigratorExportImport(exportParser,
                                                     configParams,
                                                     sepcificParams);
                migExpImp.run();
            } catch (RuntimeException ex) {
                exitCode = Utilities.getExitCode(ex, ExitCode.EXIT_UNEXPECTED);
                errMsg = ex.getMessage();
            }

            if (exitCode == null) {
                exitCode = ExitCode.EXIT_OK;
                errMsg = "Completed export of store " +
                         exportParser.getStoreName() + ", helperHosts=" +
                         exportParser.getHelperHosts();
            }
            AbstractStoreExport.exit(exitCode,
                (exitCode == ExitCode.EXIT_OK ? System.out : System.err),
                errMsg, exportParser.getJson());
        }

        private static void runExport(ExportParser exportParser,
                                      Map<String, String> configParams) {
            String storeName = exportParser.getStoreName();
            String[] helperHosts = exportParser.getHelperHosts().split(REGEX);
            String userName = exportParser.getUserName();
            String securityFile = exportParser.getSecurityFile();
            boolean jsonExport = exportParser.getJsonExport();

            if (securityFile != null) {
                File file = new File(securityFile);

                if (!file.exists()) {
                    String errorMsg = "Security file " +
                        securityFile + " not found";
                    exit(ExitCode.EXIT_SECURITY_ERROR, errorMsg,
                         Export.COMMAND_NAME);
                }
            }

            String exportType = configParams.get(EXPORT_TYPE).toUpperCase();
            String consistencyType =
                exportSpecificParamMap.get(CONSISTENCY_PARAM);
            String timeLag = exportSpecificParamMap.get(TIME_LAG_PARAM);

            int requestTimeoutMs = 0;
            String sval = configParams.get(REQUEST_TIMEOUT_MS);
            if (sval != null) {
                requestTimeoutMs = Integer.parseInt(sval);
            }

            if (ExportStoreType.get(exportType) == ExportStoreType.FILE) {

                String exportPackagePath =
                    configParams.get(EXPORT_PACKAGE_PATH_PARAM);

                /*
                 * Instance of LocalStoreExport used to export contents from
                 * Oracle NoSql store to local file system
                 */
                AbstractStoreExport storeExport =
                    new LocalStoreExport(storeName,
                                         helperHosts,
                                         userName,
                                         securityFile,
                                         requestTimeoutMs,
                                         exportPackagePath,
                                         !exportParser.exportAll(),
                                         exportParser.getJson());
                if (consistencyType != null) {
                    storeExport.setConsistencyType(consistencyType);
                }

                if (timeLag != null) {
                    storeExport.setTimeLag(Integer.parseInt(timeLag));
                }

                if (exportParser.exportAll()) {
                    /*
                     * Exports all contents in kvstore to local file system
                     */
                    storeExport.export();
                } else if (exportParser.getTableNames() != null) {
                    /*
                     * Exports specified tables in kvstore to local file
                     * system
                     */
                    storeExport
                        .exportTable(exportParser.getTableNames()
                        .split(REGEX), jsonExport);
                } else {
                    /*
                     * Exports the tables in specified namespaces in kvstore to
                     * local file system
                     */
                    storeExport
                        .exportNamespace(exportParser.getNamespaces()
                        .split(REGEX), jsonExport);
                }
            } else if (ExportStoreType.get(exportType) ==
                       ExportStoreType.OCI_OBJECT_STORE) {

                String containerName = configParams.get(CONTAINER_NAME_PARAM);
                String serviceName = configParams.get(SERVICE_NAME_PARAM);
                String objectStoreUserName = configParams.get(USER_NAME_PARAM);
                String objectStorePassword = configParams.get(PASSWORD_PARAM);
                String serviceUrl = configParams.get(SERVICE_URL_PARAM);

                final ObjectStoreFactory objectStoreFactory =
                    createObjectStoreFactory(Export.COMMAND_NAME);

                /*
                 * Instance of ObjectStoreExport used to export contents from
                 * Oracle NoSql store to Oracle Storage Cloud Service
                 */
                AbstractStoreExport storeExport =
                    objectStoreFactory.createObjectStoreExport(
                        storeName,
                        helperHosts,
                        userName,
                        securityFile,
                        requestTimeoutMs,
                        containerName,
                        serviceName,
                        objectStoreUserName,
                        objectStorePassword,
                        serviceUrl,
                        exportParser.getJson());

                if (consistencyType != null) {
                    storeExport.setConsistencyType(consistencyType);
                }

                if (timeLag != null) {
                    storeExport.setTimeLag(Integer.parseInt(timeLag));
                }

                if (exportParser.exportAll()) {
                    /*
                     * Exports all contents in kvstore to Oracle Storage
                     * Cloud Service
                     */
                    storeExport.export();
                } else if (exportParser.getTableNames() != null) {
                    /*
                     * Exports specified tables in kvstore to Oracle Storage
                     * Cloud Service
                     */
                    storeExport
                        .exportTable(exportParser.getTableNames()
                                     .split(REGEX), jsonExport);
                } else {
                    /*
                     * Exports the tables in specified namespaces in kvstore to
                     * Oracle Storage Cloud Service
                     */
                    storeExport
                        .exportNamespace(exportParser.getNamespaces()
                                         .split(REGEX), jsonExport);
                }
            }
        }
    }

    private static final ObjectStoreFactory createObjectStoreFactory(
        String commandName) {

        ObjectStoreFactory factory = null;
        try {
            factory = ObjectStoreFactory.createFactory();
        } catch (RuntimeException e) {
            exit(ExitCode.EXIT_UNEXPECTED, e.getMessage(), commandName);
        }

        if (factory == null) {
            final String errorMsg =
                "To use Oracle Storage Cloud as the export store, please " +
                "install the following jars:\n" +
                "1. oracle.cloud.storage.api\n" +
                "2. jersey-core\n" +
                "3. jersey-client\n" +
                "4. jettison\n" +
                "Instructions to install the above jars can be found in the " +
                "documentation.";
            exit(ExitCode.EXIT_USAGE, errorMsg, commandName);
        }

        return factory;
    }

    /**
     * Implementation class for Import command
     */
    public static class Import {

        public static final String COMMAND_NAME = "import";
        public static final String COMMAND_DESC = "Imports the exported " +
            "store contents to a new nosql store. Also used to import " +
            "individual tables. Use config file to specify export store " +
            "parameters.";

        private static DataFormat[] suppFormats = new DataFormat[] {
            DataFormat.BINARY,
            DataFormat.JSON,
            DataFormat.MONGODB_JSON
        };

        public static final String COMMAND_ARGS =
            ImportParser.getImportTypeUsage() + "\n\t" +
            CommandParser.getStoreUsage() + " " +
            ExportImportParser.getHelperHostsUsage() + "\n\t" +
            ExportImportParser.getConfigFileUsage() + "\n\t" +
            CommandParser.optional(
                ExportImportParser.getDataFormatUsage(suppFormats)) + "\n\t" +
            CommandParser.optional(ImportParser.getStatusFlagUsage()) + "\n\t" +
            CommandParser.optional(ExportImportParser.getUserUsage()) + " " +
            CommandParser.optional(ExportImportParser.getSecurityUsage()) +
            "\n\n";

        private static void validateImportSpecificParams() {
            String ttl = importSpecificParamMap.get(TTL_PARAM);
            String ttlRelativeDate =
                importSpecificParamMap.get(TTL_RELATIVE_DATE_PARAM);

            String streamParallelism =
                importSpecificParamMap.get(STREAM_PARALLELISM_PARAM);
            String perShardParallelism =
                importSpecificParamMap.get(PER_SHARD_PARALLELISM_PARAM);
            String bulkHeapPercent =
                importSpecificParamMap.get(BULK_HEAP_PERCENT_PARAM);

            if (ttl != null &&
                !(ttl.toUpperCase().equals(relativeFlagValue) ||
                  ttl.toUpperCase().equals(absoluteFlagValue))) {

                String errorMsg = TTL_PARAM + " parameter specified in " +
                    "the config file currently only supports " +
                    "RELATIVE and ABSOLUTE as valid values";
                exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
            }

            if ((ttl == null || !ttl.toUpperCase().equals(relativeFlagValue))
                   && (ttlRelativeDate != null)) {

                String errorMsg = TTL_RELATIVE_DATE_PARAM + " parameter " +
                    "can be specified in the config file only if " +
                    TTL_PARAM + " is " + relativeFlagValue + ". Please " +
                    "add " + TTL_PARAM + " = " + relativeFlagValue + " in " +
                    "the config file.";
                exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
            }

            if (ttl != null && ttl.toUpperCase().equals(relativeFlagValue)
                    && ttlRelativeDate == null) {

                String errorMsg = TTL_RELATIVE_DATE_PARAM + " parameter " +
                    "not specified for RELATIVE ttl.";
                exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
            }

            if (ttlRelativeDate != null) {

                String errorMsg = "Invalid date " + ttlRelativeDate
                    + " specified for param " + TTL_RELATIVE_DATE_PARAM
                    + " in the config file.";

                SimpleDateFormat formatter =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                formatter.setLenient(false);

                try {
                    formatter.parse(ttlRelativeDate);
                } catch (ParseException e) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                }

                String year = ttlRelativeDate.split(" ")[0].split("-")[0];

                if (year.length() > 4) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                }
            }

            if (streamParallelism != null) {

                String errorMsg = "Invalid value " + streamParallelism +
                    " specified for param " + STREAM_PARALLELISM_PARAM +
                    " in the config file.";

                try {
                    int num = Integer.parseInt(streamParallelism);

                    if (num < 1) {
                        exit(ExitCode.EXIT_USAGE,
                             errorMsg, Import.COMMAND_NAME);
                    }

                } catch (NumberFormatException e) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                }
            }

            if (perShardParallelism != null) {

                String errorMsg = "Invalid value " + perShardParallelism +
                    " specified for param " + PER_SHARD_PARALLELISM_PARAM +
                    " in the config file.";

                try {
                    int num = Integer.parseInt(perShardParallelism);

                    if (num < 1) {
                        exit(ExitCode.EXIT_USAGE,
                             errorMsg, Import.COMMAND_NAME);
                    }

                } catch (NumberFormatException e) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                }
            }

            if (bulkHeapPercent != null) {

                String errorMsg = "Invalid value " + bulkHeapPercent +
                    " specified for param " + BULK_HEAP_PERCENT_PARAM +
                    " in the config file.";

                try {
                    int num = Integer.parseInt(bulkHeapPercent);

                    if (num < 1 || num > 100) {
                        exit(ExitCode.EXIT_USAGE,
                             errorMsg, Import.COMMAND_NAME);
                    }

                } catch (NumberFormatException e) {
                    exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                }
            }
        }

        public static void main(String[] args) {

            ExportImportMain expimpMain = new ExportImportMain();

            ImportParser importParser = new ImportParser(args);
            importParser.parseArgs();

            Map<String, String> configParams = null;
            if (!importParser.getIsJsonConfig()) {
                File cfgFile = new File (importParser.getConfigFile());
                /*
                 * Parse config file. Validate and retrieve the parameters
                 */
                configParams =
                    expimpMain.parseConfigFile(cfgFile, Import.COMMAND_NAME);

                /*
                 * Validate the import specific parameters separately
                 */
                validateImportSpecificParams();

                /*
                 * Import config file should not contain export specific config
                 * parameters
                 */
                for (String param : exportSpecificParams) {
                    if (exportSpecificParamMap.containsKey(param)) {
                        String errorMsg = "Invalid parameter " + param +
                            " specified in the config file";
                        exit(ExitCode.EXIT_USAGE, errorMsg, Import.COMMAND_NAME);
                    }
                }
            }

            if (importParser.getUseMigrator()) {
                runMigrator(importParser, configParams, importSpecificParamMap);
            } else {
                runImport(importParser, configParams);
            }
        }

        private static void runMigrator(ImportParser importParser,
                                        Map<String, String> configParams,
                                        Map<String, String> sepcificParams) {

            MigratorExportImport migExpImp = null;
            ExitCode exitCode = null;
            String errMsg = null;
            try {
                migExpImp = new MigratorExportImport(importParser,
                                                     configParams,
                                                     sepcificParams);
                migExpImp.run();
            } catch (RuntimeException ex) {
                exitCode = Utilities.getExitCode(ex, ExitCode.EXIT_UNEXPECTED);
                errMsg = ex.getMessage();
            }

            if (exitCode == null) {
                exitCode = ExitCode.EXIT_OK;
                errMsg = "Completed import of store " +
                         importParser.getStoreName() + ", helperHosts=" +
                         importParser.getHelperHosts();
            }
            AbstractStoreImport.exit(exitCode,
                (exitCode == ExitCode.EXIT_OK ? System.out : System.err),
                errMsg, importParser.getJson());
        }

        private static void runImport(ImportParser importParser,
                                      Map<String, String> configParams) {

            String storeName = importParser.getStoreName();
            String[] helperHosts = importParser.getHelperHosts().split(REGEX);
            String userName = importParser.getUserName();
            String securityFile = importParser.getSecurityFile();

            if (securityFile != null) {
                File file = new File(securityFile);

                if (!file.exists()) {
                    String errorMsg = "Security file " +
                        securityFile + " not found";
                    exit(ExitCode.EXIT_SECURITY_ERROR, errorMsg,
                         Import.COMMAND_NAME);
                }
            }

            String status = importParser.getStatus();
            String exportType = configParams.get(EXPORT_TYPE).toUpperCase();
            String ttlRelativeDate =
                importSpecificParamMap.get(TTL_RELATIVE_DATE_PARAM);
            String streamParallelism =
                importSpecificParamMap.get(STREAM_PARALLELISM_PARAM);
            String perShardParallelism =
                importSpecificParamMap.get(PER_SHARD_PARALLELISM_PARAM);
            String bulkHeapPercent =
                importSpecificParamMap.get(BULK_HEAP_PERCENT_PARAM);

            int requestTimeoutMs = 0;
            String sval = configParams.get(REQUEST_TIMEOUT_MS);
            if (sval != null) {
                requestTimeoutMs = Integer.parseInt(sval);
            }

            if (ExportStoreType.get(exportType) == ExportStoreType.FILE) {

                String exportPackagePath =
                    configParams.get(EXPORT_PACKAGE_PATH_PARAM);

                /*
                 * Instance of LocalStoreImport used to import contents from
                 * local file system to Oracle NoSql store
                 */
                AbstractStoreImport storeImport =
                    new LocalStoreImport(storeName,
                                         helperHosts,
                                         userName,
                                         securityFile,
                                         requestTimeoutMs,
                                         exportPackagePath,
                                         status,
                                         importParser.getJson());

                storeImport.setImportTtlRelativeDate(ttlRelativeDate);

                if (streamParallelism != null) {
                    storeImport.setStreamParallelism(
                        Integer.parseInt(streamParallelism));
                }

                if (perShardParallelism != null) {
                    storeImport.setPerShardParallelism(
                        Integer.parseInt(perShardParallelism));
                }

                if (bulkHeapPercent != null) {
                    storeImport.setBulkHeapPercent(
                        Integer.parseInt(bulkHeapPercent));
                }

                if (importParser.importAll()) {
                    /*
                     * Imports all contents from the export package in local
                     * file system to kvstore
                     */
                    storeImport.doImport();
                } else if (importParser.getTableNames() != null) {
                    /*
                     * Imports the specified tables from the export package
                     * in local file system to kvstore
                     */
                    storeImport
                        .doTableImport(importParser.getTableNames()
                                       .split(REGEX));
                } else {
                    /*
                     * Imports the tables in the specified namespaces from the
                     * export package in local file system to kvstore
                     */
                    storeImport
                        .doNamespaceImport(importParser.getNamespaces()
                                           .split(REGEX));
                }
            } else if (ExportStoreType.get(exportType) ==
                       ExportStoreType.OCI_OBJECT_STORE) {

                String containerName = configParams.get(CONTAINER_NAME_PARAM);
                String serviceName = configParams.get(SERVICE_NAME_PARAM);
                String objectStoreUserName = configParams.get(USER_NAME_PARAM);
                String objectStorePassword = configParams.get(PASSWORD_PARAM);
                String serviceUrl = configParams.get(SERVICE_URL_PARAM);

                final ObjectStoreFactory objectStoreFactory =
                    createObjectStoreFactory(Import.COMMAND_NAME);

                /*
                 * Instance of ObjectStoreImport used to import contents
                 * from Oracle Storage Cloud Service to Oracle NoSql store
                 */
                AbstractStoreImport storeImport =
                    objectStoreFactory.createObjectStoreImport(
                        storeName,
                        helperHosts,
                        userName,
                        securityFile,
                        requestTimeoutMs,
                        containerName,
                        serviceName,
                        objectStoreUserName,
                        objectStorePassword,
                        serviceUrl,
                        status,
                        importParser.getJson());

                storeImport.setImportTtlRelativeDate(ttlRelativeDate);

                if (streamParallelism != null) {
                    storeImport.setStreamParallelism(
                        Integer.parseInt(streamParallelism));
                }

                if (perShardParallelism != null) {
                    storeImport.setPerShardParallelism(
                        Integer.parseInt(perShardParallelism));
                }

                if (bulkHeapPercent != null) {
                    storeImport.setBulkHeapPercent(
                        Integer.parseInt(bulkHeapPercent));
                }

                if (importParser.importAll()) {
                    /*
                     * Imports all contents from the container in Oracle
                     * Storage Cloud Service to kvstore
                     */
                    storeImport.doImport();
                } else if (importParser.getTableNames() != null) {
                    /*
                     * Imports the specified table from the container in
                     * Oracle Storage Cloud Service to kvstore
                     */
                    storeImport
                        .doTableImport(importParser.getTableNames()
                                       .split(REGEX));
                } else {
                    /*
                     * Imports the tables in the specified namespaces from the
                     * container in Oracle Storage Cloud Service to kvstore
                     */
                    storeImport
                        .doNamespaceImport(importParser.getNamespaces()
                                           .split(REGEX));
                }
            }
        }
    }

    /**
     * Returns true if the given config file in JSON format.
     */
    private static boolean checkIsJsonConfig(File configFile) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(configFile));
            String firstLine;
            while ((firstLine = reader.readLine().trim()).length() == 0) {
            }
            return (firstLine.charAt(0) == '{');
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Failed to read configFile: " +
                configFile);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
