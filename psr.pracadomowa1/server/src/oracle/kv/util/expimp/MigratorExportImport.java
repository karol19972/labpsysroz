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

import static oracle.kv.util.expimp.ExportImportMain.*;
import static oracle.nosql.common.migrator.util.Constants.DATA_DIR_NAME;
import static oracle.nosql.common.migrator.util.Constants.JSON_TYPE;
import static oracle.nosql.common.migrator.util.Constants.MONGODB_JSON_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;
import static oracle.nosql.common.migrator.util.Constants.SCHEMA_DIR_NAME;
import static oracle.nosql.common.migrator.util.JsonUtils.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import oracle.kv.Consistency;
import oracle.kv.util.expimp.ExitHandler.ExitCode;
import oracle.kv.util.expimp.ExportImportMain.ExportImportParser;
import oracle.kv.util.expimp.ExportImportMain.ExportParser;
import oracle.kv.util.migrator.Migrator;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig.SchemaFileConfig;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceConfig;
import oracle.kv.util.migrator.impl.util.OndbConfig;
import oracle.nosql.common.migrator.MigrateConfig;
import oracle.nosql.common.migrator.impl.FileConfig;
import oracle.nosql.common.migrator.impl.FileConfig.FileStoreType;
import oracle.nosql.common.migrator.impl.source.json.JsonSourceConfig;
import oracle.nosql.common.migrator.util.JsonUtils;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class encapsulates methods to direct export and import to use migrator
 * internally for exporting and importing operations.
 */
public class MigratorExportImport {
    private static boolean abortOnError = true;

    private final ExportImportParams expImpParams;
    private MigrateConfig config;

    MigratorExportImport(ExportImportParser exportParser,
                         Map<String, String> configParams,
                         Map<String, String> specificParams) {
        boolean isExport = (exportParser instanceof ExportParser);
        if (isExport) {
            expImpParams = new ExportParams(exportParser,
                                            configParams,
                                            specificParams);
        } else {
            expImpParams = new ImportParams(exportParser,
                                            configParams,
                                            specificParams);
        }
        expImpParams.validate();

        if (isExport) {
            initExportConfig();
        } else {
            initImportConfig();
        }
    }

    public void run() {
        runMigrator(config, getLogger(), expImpParams.getVerbose());
    }

    void initExportConfig() {
        final ExportParams params = (ExportParams)expImpParams;
        final DataFormat format = params.getDataFormat();

        String json = convertParamsToJson(params);
        String sourceType = ONDB_TYPE;
        String sinkType = formateToType(format);
        Migrator.validateSourceSink(sourceType, sinkType);

        config = new MigrateConfig(sourceType, sinkType, abortOnError,
                                   true /* unwrapSourceSink */);
        config.parseJson(new ByteArrayInputStream(json.getBytes()));
    }

    private String convertParamsToJson(ExportParams params) {
        ObjectNode root;
        if (params.isJsonConfig()) {
            root = readJsonFile(params.getJsonConfigFile());
        } else {
            root = JsonNodeFactory.instance.objectNode();
            writeBinaryFileConfig(root, params);
            writeNode(root, OndbConfig.PROP_REQUEST_TIMEOUT_MS,
                      params.getRequestTimeoutMs());
            writeConsistency(root, params);
        }
        writeOndbConfig(root, params);
        return JsonUtils.print(root, false);
    }

    void initImportConfig() {
        final ImportParams params = (ImportParams)expImpParams;
        final DataFormat format = params.getDataFormat();

        String json = convertParamsToJson(params);

        String sourceType = formateToType(format);
        String sinkType = ONDB_TYPE;
        Migrator.validateSourceSink(sourceType, sinkType);

        config = new MigrateConfig(sourceType, sinkType, abortOnError,
                                   true /* unwrapSourceSink */);
        config.parseJson(new ByteArrayInputStream(json.getBytes()));

        if (params.fromExportPackage()) {
            FileConfig srcCfg = ((FileConfig)config.getSource());
            OndbSinkConfig sinkCfg = (OndbSinkConfig)config.getSink();
            FileStoreType output = srcCfg.getFileStoreType();
            SchemaFileConfig.Type schemaType =
                 (params.getDataFormat() == DataFormat.BINARY ? null :
                  SchemaFileConfig.Type.DDL);
            switch (output) {
            case FILE:
                sinkCfg.setSchemaFile(getSchemaDir(srcCfg.getPath()),
                                      schemaType);
                break;
            case OCIC_OBJECT_STORE:
                sinkCfg.setSchemaFile(SCHEMA_DIR_NAME,
                                      srcCfg.getContainerName(),
                                      srcCfg.getServiceName(),
                                      srcCfg.getUserName(),
                                      srcCfg.getPassword(),
                                      srcCfg.getServiceUrl(),
                                      schemaType);
                break;
            }
        }
    }

    private String convertParamsToJson(ImportParams params) {
        ObjectNode root;
        if (params.isJsonConfig()) {
            root = readJsonFile(params.getJsonConfigFile());
        } else {
            root = JsonNodeFactory.instance.objectNode();
            writeBinaryFileConfig(root, params);
            if (params.isTtlRelativeDate()) {
                writeNode(root, OndbSinkConfig.PROP_TTL_RELATIVE_DATE,
                          params.getTtlRelativeDate());
            }
            writeNode(root, OndbSinkConfig.PROP_STREAM_CONCURRENCY,
                      params.getStreamParallelism());
            writeNode(root, OndbSinkConfig.PROP_BULKPUT_HEAP_PERCENT,
                      params.getBulkHeapPercent());
            writeNode(root, OndbSinkConfig.PROP_PER_SHARD_CONCURRENCY,
                      params.getPerShardParallelism());
            writeNode(root, OndbSinkConfig.PROP_REQUEST_TIMEOUT_MS,
                      params.getRequestTimeoutMs());
            writeNode(root, OndbSinkConfig.PROP_OVERWRITE,
                      params.getOverwrite());
        }
        if (params.getStatus() != null) {
            writeNode(root, MigrateConfig.PROP_CHECKPOINT_OUTPUT,
                      params.getStatus());
        }
        writeOndbConfig(root, params);
        if (params.fromExportPackage()) {
            writeNode(root, JsonSourceConfig.PROP_DATA_SUB_DIR, DATA_DIR_NAME);
        }
        return JsonUtils.print(root, false);
    }

    private void writeBinaryFileConfig(ObjectNode root,
                                       ExportImportParams params) {
        assert(params.getDataFormat() == DataFormat.BINARY);
        if (params.getExportStoreType() == ExportStoreType.FILE) {
            writeNode(root, FileConfig.PROP_PATH, params.getPackagePath());
        } else {
            writeNode(root, FileConfig.PROP_TYPE,
                      FileConfig.FileStoreType.OCIC_OBJECT_STORE.name());
            writeNode(root, FileConfig.PROP_CONTAINER_NAME,
                      params.getCSContainerName());
            writeNode(root, FileConfig.PROP_SERVICE_NAME,
                      params.getCSServiceName());
            writeNode(root, FileConfig.PROP_USER_NAME,
                      params.getCSUserName());
            writeNode(root, FileConfig.PROP_PASSWORD,
                      params.getCSPassword());
            writeNode(root, FileConfig.PROP_SERVICE_URL,
                      params.getCSServiceURL());
        }
    }

    private void writeOndbConfig(ObjectNode root,
                                 ExportImportParams params) {
        writeNode(root, OndbConfig.PROP_HELPER_HOSTS,
                  params.getHelperHosts(), false);
        writeNode(root, OndbConfig.PROP_STORE_NAME,
                  params.getStoreName(), false);
        writeNode(root, OndbConfig.PROP_USERNAME,
                  params.getUserName(), false);
        writeNode(root, OndbConfig.PROP_SECURITY,
                  params.getSecurityFile(), false);
        writeNode(root, OndbConfig.PROP_NAMESPACES,
                  params.getNamespaces(), false);
        writeNode(root, OndbConfig.PROP_TABLES,
                  params.getTableNames(), false);
    }

    private void writeConsistency(ObjectNode root, ExportParams params) {
        Consistency cons = params.getConsistency();
        if (cons == Consistency.NONE_REQUIRED) {
            writeNode(root, OndbSourceConfig.PROP_CONSISTENCY,
                      OndbSourceConfig.ConsistencyType.Type.NONE.name());
        } else if (cons == Consistency.ABSOLUTE) {
            writeNode(root, OndbSourceConfig.PROP_CONSISTENCY,
                      OndbSourceConfig.ConsistencyType.Type.ABSOLUTE.name());
        } else if (cons instanceof Consistency.Time) {
            Map<String, Object> values = new HashMap<String, Object>();
            values.put(OndbSourceConfig.PROP_CONSISTENCY_TYPE,
                       OndbSourceConfig.ConsistencyType.Type.TIME.name());
            values.put(OndbSourceConfig.PROP_CONSISTENCY_PERMISSIBLE_LAG_MS,
                       ((Consistency.Time)cons)
                          .getPermissibleLag(TimeUnit.MILLISECONDS));
            values.put(OndbSourceConfig.PROP_CONSISTENCY_TYPE,
                       ((Consistency.Time)cons)
                          .getTimeout(TimeUnit.MILLISECONDS));
            writeNode(root, OndbSourceConfig.PROP_CONSISTENCY, values);
        }
    }

    private Logger getLogger() {
        return Logger.getLogger(ExportImportMain.class.getName());
    }

    MigrateConfig getMigrateConfig() {
        return config;
    }

    private String formateToType(DataFormat fmt) {
        switch (fmt) {
        case BINARY:
            return ONDB_BINARY_TYPE;
        case JSON:
            return JSON_TYPE;
        case MONGODB_JSON:
            return MONGODB_JSON_TYPE;
        default:
            throw new IllegalArgumentException("Unsupported data format: " +
                                               fmt);
        }
    }

    private static String getSchemaDir(String rootDir) {
        return rootDir + File.separator + SCHEMA_DIR_NAME;
    }

    /**
     * Run migrator
     */
    private static void runMigrator(MigrateConfig config,
                                    Logger logger,
                                    boolean verbose) {

        Migrator mg = new Migrator(config, logger,
                                   (verbose? System.out : null));
        mg.run();
    }

    /**
     * Configuration of export.
     */
    private static class ExportParams extends ExportImportParams {
        /* To be consistent with default consistency used in AbstractExport */
        static final String DEF_CONSISTENCY_TYPE =
            AbstractStoreExport.DEF_CONSISTENCY_TYPE;
        private static final long DEF_CONSISTENCY_TIME_LAG_MS =
            AbstractStoreExport.DEF_CONSISTENCY_TIMELAG_MS;
        private static final long DEF_CONSISTENY_TIMEOUT_SEC =
            AbstractStoreExport.DEF_CONSISTENCY_TIMEOUT_SEC;

        ExportParams(ExportImportParser parser,
                     Map<String, String> configParams,
                     Map<String, String> specificParams) {
            super(parser, configParams, specificParams);
        }

        public Consistency getConsistency() {

            if (isJsonConfig()) {
                return Consistency.NONE_REQUIRED;
            }

            String type = getSpecificParam(CONSISTENCY_PARAM,
                                           DEF_CONSISTENCY_TYPE);
            if (type.equalsIgnoreCase(absoluteConsistencyFlagValue)) {
                return Consistency.ABSOLUTE;
            }
            if (type.equalsIgnoreCase(noConsistencyFlagValue)) {
                return Consistency.NONE_REQUIRED;
            }
            if (type.equalsIgnoreCase(timeConsistencyFlagValue)) {
                long timeLagMs = getSpecificParamLong(TIME_LAG_PARAM,
                         DEF_CONSISTENCY_TIME_LAG_MS);
                return new Consistency.Time(timeLagMs,
                                            TimeUnit.MILLISECONDS,
                                            DEF_CONSISTENY_TIMEOUT_SEC,
                                            TimeUnit.SECONDS);
            }
            throw new IllegalArgumentException("Invalid consistency type: " +
                                               type);
        }

        @Override
        public void validate() {
            super.validate();

            if (isLocalOutput()) {
                File exportPath = new File(getPackagePath());
                if (!exportPath.exists() || !exportPath.isDirectory()) {
                    throw new InvalidConfigException (
                        ExportImportMain.EXPORT_PACKAGE_PATH_PARAM +
                        " does not exist or not a directory: " + getPackagePath(),
                        ExitCode.EXIT_NO_EXPORT_FOLDER);
                }
                if (!exportPath.canWrite()) {
                    throw new InvalidConfigException (
                        ExportImportMain.EXPORT_PACKAGE_PATH_PARAM +
                        " has no write permissions:  " + getPackagePath(),
                        ExitCode.EXIT_NOWRITE);
                }
            }
        }
    }

    /**
     * Configuration of import
     */
    private static class ImportParams extends ExportImportParams {
        ImportParams(ExportImportParser parser,
                     Map<String, String> configParams,
                     Map<String, String> specificParams) {
            super(parser, configParams, specificParams);
        }

        ImportParams(ExportImportParser parser) {
            super(parser, null, null);
        }

        public String getStatus() {
            return ((ImportParser)cmdParser).getStatus();
        }

        public int getStreamParallelism() {
            return getSpecificParamInt(STREAM_PARALLELISM_PARAM);
        }

        public int getPerShardParallelism() {
            return getSpecificParamInt(PER_SHARD_PARALLELISM_PARAM);
        }

        public int getBulkHeapPercent() {
            return getSpecificParamInt(BULK_HEAP_PERCENT_PARAM);
        }

        public boolean isTtlRelativeDate() {
            String val = getSpecificParam(TTL_PARAM);
            if (val != null) {
                return val.equalsIgnoreCase(relativeFlagValue);
            }
            return false;
        }

        public String getTtlRelativeDate() {
            return getSpecificParam(TTL_RELATIVE_DATE_PARAM);
        }

        public boolean getOverwrite() {
            return getSpecificParamBoolean(OVERWRITE_PARAM, false);
        }

        public boolean fromExportPackage() {
            return !((ImportParser)cmdParser).isExternal();
        }

        @Override
        public void validate() {
            super.validate();

            if (isLocalOutput()) {
                File exportPath = new File(getPackagePath());
                if (!exportPath.exists() || !exportPath.isDirectory()) {
                    throw new InvalidConfigException (
                        ExportImportMain.EXPORT_PACKAGE_PATH_PARAM +
                        " does not exist or not a directory: " + getPackagePath(),
                        ExitCode.EXIT_NOEXPPACKAGE);
                }

                if (!exportPath.canRead()) {
                    throw new InvalidConfigException (
                        ExportImportMain.EXPORT_PACKAGE_PATH_PARAM +
                        " has no read permissions:  " + getPackagePath(),
                        ExitCode.EXIT_NOREAD);
                }
            }
        }
    }

    /**
     * Base class of configuration for export and import.
     */
    private static class ExportImportParams {

        ExportImportParser cmdParser;
        private Map<String, String> configParams;
        private Map<String, String> specificParams;

        ExportImportParams(ExportImportParser cmdParser,
                           Map<String, String> configParams,
                           Map<String, String> specificParams) {
            this.cmdParser = cmdParser;
            this.configParams = configParams;
            this.specificParams = specificParams;
        }

        public String getStoreName() {
            return cmdParser.getStoreName();
        }

        public String[] getHelperHosts() {
            String helpHosts = cmdParser.getHelperHosts();
            assert(helpHosts != null);
            return helpHosts.split(",");
        }

        public String getUserName() {
            return cmdParser.getUserName();
        }

        public String getSecurityFile() {
            return cmdParser.getSecurityFile();
        }

        public String getPackagePath() {
            return getConfigParam(EXPORT_PACKAGE_PATH_PARAM);
        }

        public String[] getNamespaces() {
            if (cmdParser.getNamespaces() != null) {
                return cmdParser.getNamespaces().split(",");
            }
            return null;
        }

        public String[] getTableNames() {
            if (cmdParser.getTableNames() != null) {
                return cmdParser.getTableNames().split(",");
            }
            return null;
        }

        public String getExportType() {
            return getConfigParam(EXPORT_TYPE);
        }

        public ExportStoreType getExportStoreType() {
            if (isJsonConfig()) {
                return null;
            }
            String exportType = getExportType();
            assert(exportType != null);
            return ExportStoreType.get(exportType);
        }

        public boolean isLocalOutput() {
            return getExportStoreType() == ExportStoreType.FILE;
        }

        public boolean isObjectStoreOutput() {
            return getExportStoreType() == ExportStoreType.OCI_OBJECT_STORE;
        }

        public String getCSContainerName() {
            return getConfigParam(CONTAINER_NAME_PARAM);
        }

        public String getCSServiceName() {
            return configParams.get(SERVICE_NAME_PARAM);
        }

        public String getCSUserName() {
            return getConfigParam(USER_NAME_PARAM);
        }

        public String getCSPassword() {
            return getConfigParam(PASSWORD_PARAM);
        }

        public String getCSServiceURL() {
            return getConfigParam(SERVICE_URL_PARAM);
        }

        public int getRequestTimeoutMs() {
            return intValue(REQUEST_TIMEOUT_MS,
                            getConfigParam(REQUEST_TIMEOUT_MS));
        }

        public boolean isJsonConfig() {
            return cmdParser.getIsJsonConfig();
        }

        public File getJsonConfigFile() {
            if (isJsonConfig()) {
                return new File(cmdParser.getConfigFile());
            }
            return null;
        }

        public DataFormat getDataFormat() {
            return cmdParser.getDataFormat();
        }

        private String getConfigParam(String key) {
            if (configParams != null) {
                return configParams.get(key);
            }
            return null;
        }

        protected String getSpecificParam(String key) {
            return getSpecificParam(key, null);
        }

        protected String getSpecificParam(String key, String def) {
            String sval = null;
            if (specificParams != null) {
                sval = specificParams.get(key);
            }
            return (sval != null) ? sval : def;
        }

        protected int getSpecificParamInt(String key) {
            return getSpecificParamInt(key, 0);
        }

        protected int getSpecificParamInt(String key, int def) {
            String sval = getSpecificParam(key);
            if (sval != null) {
                return intValue(key, sval);
            }
            return def;
        }

        protected long getSpecificParamLong(String key, long def) {
            String sval = getSpecificParam(key);
            if (sval != null) {
                return longVal(key, sval);
            }
            return def;
        }

        protected boolean getSpecificParamBoolean(String key, boolean def) {
            String sval = getSpecificParam(key);
            if (sval != null) {
                return Boolean.parseBoolean(sval);
            }
            return def;
        }

        boolean getVerbose() {
            return cmdParser.getVerbose();
        }

        public void validate() {
            if (isObjectStoreOutput() &&
                getDataFormat() != DataFormat.BINARY) {
                throw new InvalidConfigException (
                   ExportStoreType.OCI_OBJECT_STORE +
                   " is not supported for data format: " + getDataFormat());
            }

            if (getSecurityFile() != null) {
                File securityFile = new File(getSecurityFile());
                if (!securityFile.exists()) {
                    throw new InvalidConfigException ("Security file " +
                        getSecurityFile() + " not found",
                        ExitCode.EXIT_SECURITY_ERROR);
                }
            }
        }

        private int intValue(String key, String sval) {
            if (sval != null) {
                try {
                    int val = Integer.parseInt(sval);
                    if (val < 0) {
                        throw new InvalidConfigException (
                            key + " may not be negative: " + sval);
                    }
                    return val;
                } catch (NumberFormatException nfe) {
                    throw new InvalidConfigException(
                        "Not a valid int parameter for " + key + ": " + sval);
                }
            }
            return 0;
        }

        private long longVal(String key, String sval) {
            if (sval != null) {
                try {
                    long val = Long.parseLong(sval);
                    if (val < 0) {
                        throw new InvalidConfigException (
                            key + " may not be negative: " + sval);
                    }
                    return val;
                } catch (NumberFormatException nfe) {
                    throw new InvalidConfigException(
                        "Not a valid long parameterfor " + key + ": " + sval);
                }
            }
            return 0;
        }
    }

    public static class InvalidConfigException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private ExitCode exitCode;

        InvalidConfigException(String msg) {
            this(msg, ExitCode.EXIT_USAGE);
        }

        InvalidConfigException(String msg, ExitCode exitCode) {
            super(msg);
            this.exitCode = exitCode;
        }

        public ExitCode getExitCode() {
            return exitCode;
        }
    }
}
