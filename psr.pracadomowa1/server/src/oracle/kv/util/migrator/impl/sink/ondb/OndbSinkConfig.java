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

package oracle.kv.util.migrator.impl.sink.ondb;

import static oracle.kv.util.migrator.MainCommandParser.OVERWRITE_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.SCHEMA_FILE_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.TTL_RELATIVE_DAY_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.getNamespacesUsage;
import static oracle.kv.util.migrator.MainCommandParser.getSchemaFileUsage;
import static oracle.kv.util.migrator.MainCommandParser.getTablesUsage;
import static oracle.kv.util.migrator.MainCommandParser.getTtlRelativeUsage;
import static oracle.kv.util.migrator.MainCommandParser.optional;
import static oracle.nosql.common.migrator.util.Constants.MONGODB_JSON_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;
import static oracle.nosql.common.migrator.util.JsonUtils.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import oracle.kv.KVStore;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.RegionMapper;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.table.RecordDef;
import oracle.kv.util.expimp.utils.ObjectStoreAPI;
import oracle.kv.util.expimp.utils.ObjectStoreAPIFactory;
import oracle.kv.util.migrator.impl.sink.ondb.SchemaFileParser.SchemaParserCallback;
import oracle.kv.util.migrator.impl.util.OndbConfig;
import oracle.kv.util.migrator.impl.util.OndbUtils;
import oracle.kv.util.migrator.impl.util.OndbUtils.TableNameComparator;
import oracle.nosql.common.migrator.DataSinkConfig;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.MigratorCommandParser.CommandParserHandler;
import oracle.nosql.common.migrator.impl.FileConfig;
import oracle.nosql.common.migrator.impl.FileConfig.FileStoreType;
import oracle.nosql.common.migrator.util.MigratorUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The SinkConfig implementation for Ondb Sink, it includes below configuration:
 * <pre>{@code
 *  "sink": {
 *      "type": "nosqldb",
 *      "helperHosts": [<host:port>, <host:port> ...],  --required
 *      "storeName": <store-name>,                      --required
 *      "username": <user-name>,                        --optional
 *      "security": <security-file-path>,               --optional
 *
 *      "namespaces": [<namespace>,<namespace>,...],    --optional
 *      "tables": [<table>,<table>,...],
 *
 *      "ddlSchemaFile": <ddl-schema-file>,             --optional,valid if source is json, mongo json
 *      "continueOnDdlError": <false | true>,           --optional,default:false
 *
 *      "overwrite": <false | true>,                    --optional,default:false
 *
 *      "streamConcurrency": <num>,                     --optional,default:see below
 *      "perShardConcurrency": <num>,                   --optional,hidden
 *      "bulkPutHeapPercent": <num>,                    --optional,hidden
 *      "requestTimeoutMs": <ms>                        --optional,hidden
 *      "ttlRelativeDate": <yyyy-MM-dd HH:mm:ss>        --optional,valid if source is binary.
 *  }
 *
 *  default of streamConcurrency: MIN(number of streams,
 *                                    number of shards * perShardParallelism)
 *  }</pre> *
 */
public class OndbSinkConfig extends OndbConfig implements DataSinkConfig {

    /* Json configuration properties */
    public static String PROP_TTL_RELATIVE_DATE = "ttlRelativeDate";
    public static String PROP_DDL_SCHEMA_FILE = "ddlSchemaFile";
    public static String PROP_CONTINUE_ON_DDL_ERROR = "continueOnDdlError";
    public static String PROP_STREAM_CONCURRENCY = "streamConcurrency";
    public static String PROP_BULKPUT_HEAP_PERCENT = "bulkPutHeapPercent";
    public static String PROP_PER_SHARD_CONCURRENCY = "perShardConcurrency";
    public static String PROP_OVERWRITE = "overwrite";

    private final static String DEF_JSON_FIELD_NAME = "document";
    private final static String DEF_PRIMARY_KEY_FIELD_NAME = "id";

    /* The cache size of identity column */
    private final static int PKEY_INDENTITY_CACHE_SIZE = 5000;

    /* The primary field type for JSON source */
    private final static String PKEY_INDENTITY_TYPE =
        "LONG GENERATED ALWAYS AS IDENTITY (CACHE " +
        PKEY_INDENTITY_CACHE_SIZE + ")";

    /* The default primary field type for MONGO JSON source */
    private final static String PKEY_STRING_TYPE = "STRING";

    final static String COMMAND_ARGS_BINARY =
        COMMAND_ARGS_REQ + "\n\t" +
        COMMAND_ARGS_OPT + "\n\t" +
        optional(getTtlRelativeUsage(), true) +
        optional(OVERWRITE_FLAG);

    final static String COMMAND_ARGS =
        COMMAND_ARGS_REQ + "\n\t" +
        COMMAND_ARGS_OPT + "\n\t" +
        optional(getSchemaFileUsage(), true) +
        optional(getTablesUsage(), true) +
        optional(getNamespacesUsage(), true) +
        optional(OVERWRITE_FLAG);

    private String ddlSchemaFile;
    private boolean continueOnDdlError;
    private boolean overwrite;

    private int streamConcurrency;
    /* Configurable but does not display */
    private int perShardConcurrency;
    private int bulkPutHeapPercent;

    private String ttlRelativeDate;     /* for nosqldb-binary souce only */

    /* Internal used members */
    protected SchemaFileConfig schemaFile;
    private Map<String, TableInfo> tableInfos =
        new TreeMap<String, TableInfo>(TableNameComparator.newInstance);
    private Map<String, String> defPrimaryKeyFields;
    private String defJsonFieldName;

    private Map<String, TableImpl> tableMap;
    private Map<String, RecordDef> tableWriterSchemas;
    private long referenceTimeMs = 0;

    OndbSinkConfig() {
        super(ONDB_TYPE);
    }

    public OndbSinkConfig(InputStream in, int configVersion) {
        this();
        parseJson(in, configVersion);
    }

    public OndbSinkConfig(MigratorCommandParser parser) {
        this();
        parseArgs(parser);
    }

    /**
     * Used by unit test
     */
    public OndbSinkConfig(String[] helperHosts,
                          String storeName,
                          String userName,
                          String securityFile,
                          boolean overwrite) {
        super(ONDB_TYPE, helperHosts, storeName, userName, securityFile,
              null, null, 0);
        this.overwrite = overwrite;
        validate(false);
    }

    @Override
    public void parseJsonNode(JsonNode node, int configVersion) {
        super.parseJsonNode(node, configVersion);

        ttlRelativeDate = readString(node, PROP_TTL_RELATIVE_DATE);

        ddlSchemaFile = readString(node, PROP_DDL_SCHEMA_FILE);
        if (ddlSchemaFile != null) {
            continueOnDdlError = readBoolean(node, PROP_CONTINUE_ON_DDL_ERROR);
        }

        streamConcurrency = readInt(node, PROP_STREAM_CONCURRENCY);
        perShardConcurrency = readInt(node, PROP_PER_SHARD_CONCURRENCY);
        bulkPutHeapPercent = readInt(node, PROP_BULKPUT_HEAP_PERCENT);
        overwrite = readBoolean(node, PROP_OVERWRITE);
    }

    @Override
    public void writeJsonNode(ObjectNode node, int configVersion) {
        super.writeJsonNode(node, configVersion);

        /*
         * TODO:
         *  o parse TTL relatitve date for ondb binary file source only
         *  o parse ddlSchemaFile and continueOnDdlError for non ondb binary
         *    file.
         */
        writeNode(node, PROP_TTL_RELATIVE_DATE, ttlRelativeDate);
        writeNode(node, PROP_DDL_SCHEMA_FILE, ddlSchemaFile);
        if (ddlSchemaFile != null) {
            writeNode(node, PROP_CONTINUE_ON_DDL_ERROR, continueOnDdlError);
        }

        if (streamConcurrency > 0) {
            writeNode(node, PROP_STREAM_CONCURRENCY, streamConcurrency);
        }
        writeNode(node, PROP_OVERWRITE, overwrite);
    }

    @Override
    public void validate(boolean parseArg) {
        super.validate(parseArg);
        if (ddlSchemaFile != null) {
            checkFileExist(new File(ddlSchemaFile));
            setSchemaFile(ddlSchemaFile, SchemaFileConfig.Type.DDL);
        }
        if (streamConcurrency < 0) {
            invalidValue("\"streamConcurrency\" must not be a negative value: " +
                         streamConcurrency);
        }
    }

    protected void parseSchemaFiles() {

        final SchemaFileConfig.Type schemaType = schemaFile.getSchemaType();

        KVStore store = null;
        InputStream in = null;
        try {
            tableInfos.clear();
            if (schemaType == null ||
                schemaType == SchemaFileConfig.Type.BINARY) {

                if (tableMap == null) {
                    tableMap = new HashMap<String, TableImpl>();
                }
                if (tableWriterSchemas == null) {
                    tableWriterSchemas = new HashMap<String, RecordDef>();
                }

                store = OndbUtils.connectToStore(getStoreName(),
                                                 getHelperHosts(),
                                                 getUsername(),
                                                 getSecurity());
            }

            SchemaParserCallback callback = getSchemaParserCallback(store);
            SchemaFileParser parser = getSchemaFileParsr(callback);
            parser.parse();
        } finally {
            if (store != null) {
                store.close();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private SchemaFileParser getSchemaFileParsr(SchemaParserCallback callback) {
        if (schemaFile.getFileStoreType() == FileStoreType.FILE) {
            File file = new File(schemaFile.getPath());
            return new SchemaFileParser(file,
                                        schemaFile.getSchemaType(),
                                        callback);
        }

        ObjectStoreAPI objectStoreAPI =
            ObjectStoreAPIFactory.getObjectStoreAPI(
                schemaFile.getServiceName(),
                schemaFile.getUserName(),
                schemaFile.getPassword(),
                schemaFile.getServiceUrl(),
                schemaFile.getContainerName());
        /*
         * Check if the specified exists in Cloud Storage
         */
        objectStoreAPI.checkContainerExists();

        InputStream in = objectStoreAPI.retrieveObjectByManifest(
                             schemaFile.getFileName());
        return new SchemaFileParser(in,
                                    schemaFile.getSchemaType(),
                                    callback);
    }

    @Override
    public CommandParserHandler getCommandHandler(MigratorCommandParser parser) {
        /*
         * Supported arguments:
         *  -helper-hosts <host:port[,host:port]*>
         *  -store <storeName>
         *  [-username <user>] [-security <security-file-path>]
         *  [-default-namespace <namespace>]
         *  [-schema-file <dir>]
         *  [-namespaces <namespace>,<namespace>]
         *  [-tables <tablename>,<tablename>]
         *  [-ttl-relative-date <date-string>]
         */
        return new OndbConfigArgumentHandler(parser) {

            private SchemaFileConfig.Type schemaType = null;

            @Override
            public boolean checkArg(String arg) {
                if (super.checkArg(arg)) {
                    return true;
                }
                if (arg.equals(OVERWRITE_FLAG)) {
                    overwrite = true;
                    return true;
                }
                if (arg.equals(SCHEMA_FILE_FLAG)) {
                    String file = parser.nextArg(arg);
                    schemaFile = new SchemaFileConfig(file, schemaType);
                    return true;
                }
                if (arg.equals(TTL_RELATIVE_DAY_FLAG)) {
                    ttlRelativeDate = parser.nextArg(arg);
                    return true;
                }
                return false;
            }
        };
    }

    public Map<String, String> getDefaultPrimaryKeyFields() {
        return defPrimaryKeyFields;
    }

    public String getDefaultJsonFieldName() {
        return defJsonFieldName;
    }

    public int getStreamConcurrency() {
        return streamConcurrency;
    }

    public int getPerShardConcurrency() {
        return perShardConcurrency;
    }

    public int getBulkPutHeapPercent() {
        return bulkPutHeapPercent;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    public String getDdlSchemaFile() {
        return ddlSchemaFile;
    }

    public boolean importAllSchema() {
        return (ddlSchemaFile == null && schemaFile != null) &&
               (getTables() == null  && getNamespaces() == null);
    }

    public boolean getContinueOnDdlError() {
        return (ddlSchemaFile != null) ? continueOnDdlError : true;
    }

    public void setSchemaFile(String filePath, SchemaFileConfig.Type type) {
        this.schemaFile = new SchemaFileConfig(filePath, type);
        parseSchemaFiles();
    }

    public void setSchemaFile(String fileName,
                              String containerName,
                              String serviceName,
                              String userName,
                              String password,
                              String serviceUrl,
                              SchemaFileConfig.Type type) {
        this.schemaFile = new SchemaFileConfig(fileName, containerName,
                            serviceName, userName, password, serviceUrl, type);
        parseSchemaFiles();
    }

    public SchemaFileConfig getSchemaFile() {
        return schemaFile;
    }

    Set<String> getTableNames() {
        return tableInfos.keySet();
    }

    public void setTtlRelativeDate(String ttlRelativeDate) {
        this.ttlRelativeDate = ttlRelativeDate;
    }

    public String getTtlRelativeDate() {
        return ttlRelativeDate;
    }

    boolean hasTableWriterSchema(String tableFullName) {
        if (tableWriterSchemas != null) {
            return tableWriterSchemas.containsKey(tableFullName);
        }
        return false;
    }

    RecordDef getTableWriterSchema(String tableFullName) {
        if (tableWriterSchemas != null) {
            return tableWriterSchemas.get(tableFullName);
        }
        return null;
    }

    TableImpl getParsedTable(String tableFullName) {
        if (tableMap != null) {
            return tableMap.get(tableFullName);
        }
        return null;
    }

    /**
     * Get the reference time for ttl that will be used during import
     */
    long getReferenceTime() {

        if (referenceTimeMs != 0) {
            return referenceTimeMs;
        }

        referenceTimeMs = System.currentTimeMillis();

        if (ttlRelativeDate != null) {
            SimpleDateFormat formatter =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            formatter.setLenient(false);

            try {
                Date referenceDate = formatter.parse(ttlRelativeDate);
                referenceTimeMs = referenceDate.getTime();
            } catch (ParseException e) {
                /*
                 * The check for valid format of the relative date should be
                 * done when the config.xml file is parsed in
                 * ExportImportMain.java, so a format error here is unexpected.
                 */
                throw new IllegalStateException("Invalid date format: " +
                                                ttlRelativeDate +
                                                ": " + e);
            }
        }

        return referenceTimeMs;
    }

    public TableInfo getTableInfo(String qualifiedName) {
        String ns =
            MigratorUtils.getNamespaceFromQualifiedName(qualifiedName);
        String tableName =
            MigratorUtils.getTableNameFromQualifiedName(qualifiedName);
        String fullTableName = NameUtils.makeQualifiedName(ns, tableName);

        TableInfo ti = tableInfos.get(fullTableName);
        if (ti != null) {
            return ti;
        }
        return new TableInfo(ns, tableName);
    }

    public String getNamespace(TableInfo ti) {
        return ti.getNamespace();
    }

    public String getQualifiedName(TableInfo ti) {
        return getQualifiedName(ti.getNamespace(), ti.getTableName());
    }

    private String getQualifiedName(String namespace, String tableName) {
        String ns = NameUtils.switchToInternalUse(namespace);
        return NameUtils.makeQualifiedName(ns, tableName);
    }

    public Map<String, String> getPrimaryKeyFields(TableInfo ti) {
        if (ti.getCreateJsonTable()) {
            return getDefaultPrimaryKeyFields();
        }
        return null;
    }

    public String getJsonFieldName(TableInfo ti) {
        if (ti.getCreateJsonTable()) {
             return getDefaultJsonFieldName();
        }
        return null;
    }

    @Override
    public void setDefaults(DataSourceConfig srcCfg) {
        String sourceType = srcCfg.getType();
        defPrimaryKeyFields = getDefaultPrimaryKeyFields(sourceType);
        defJsonFieldName = getDefaultJsonFieldName(sourceType);
    }

    public static
    Map<String, String> getDefaultPrimaryKeyFields(String sourceType) {
        Map<String, String> pkeyFields = new HashMap<String, String>();
        if (sourceType != null) {
            if (sourceType.equals(MONGODB_JSON_TYPE)) {
                pkeyFields.put(DEF_PRIMARY_KEY_FIELD_NAME, PKEY_STRING_TYPE);
                return pkeyFields;
            }
        }
        pkeyFields.put(DEF_PRIMARY_KEY_FIELD_NAME, PKEY_INDENTITY_TYPE);
        return pkeyFields;
    }

    public static String getDefaultJsonFieldName(@SuppressWarnings("unused")
                                                 String sourceType) {
        return DEF_JSON_FIELD_NAME;
    }

    /**
     * Add TableInfo contains createStatement and indexStatements
     */
    public void setTableDdls(String namespace,
                             String tableName,
                             String tableDdl,
                             String[] indexDdls) {

        String qualifiedName = getQualifiedName(namespace, tableName);
        TableInfo ti = tableInfos.get(qualifiedName);
        if (ti != null) {
            if (tableDdl != null) {
                if (ti.getTableStatement() != null) {
                    throw new IllegalArgumentException("Fail to add " +
                        "ddl statment \'" + tableDdl + "' for table \'" +
                        qualifiedName +
                        "\', because its table statement has been existed: " +
                        ti.getTableStatement());
                }
                ti.tableStatement = tableDdl;
            }
            if (indexDdls != null && indexDdls.length > 0) {
                for (String indexDdl : indexDdls) {
                    ti.addIndexStatement(indexDdl);
                }
            }
        } else {
            ti = new TableInfo(namespace, tableName, tableDdl, indexDdls);
            tableInfos.put(getQualifiedName(ti), ti);
        }
    }

    private SchemaParserCallback getSchemaParserCallback(KVStore store) {

        return new SchemaParserCallback() {
            @Override
            public void tableDdl(String namespace,
                                 String tableName,
                                 String tableDdl) {
                setTableDdls(namespace, tableName, tableDdl, null);
            }

            @Override
            public void indexDdls(String namespace,
                                  String tableName,
                                  String... indexDdls) {
                setTableDdls(namespace, tableName, null, indexDdls);
            }

            @Override
            public void newTable(TableImpl table) {
                if (tableMap != null) {
                    tableMap.put(table.getFullNamespaceName(), table);
                }
                if (tableWriterSchemas != null) {
                    tableWriterSchemas.put(table.getFullNamespaceName(),
                                           table.getValueRecordDef());
                }
            }

            @Override
            public TableMetadataHelper getTableMetadataHelper() {
                if (tableMap != null) {
                    return new TableMetadataHelperImpl(store);
                }
                return null;
            }
        };
    }

    public static class TableInfo {

        private String namespace;
        private String tableName;
        private String tableStatement;
        private List<String> indexStatements;

        TableInfo(String namespace, String tableName) {
            this.namespace = namespace;
            this.tableName = tableName;
            this.indexStatements = new ArrayList<String>();
        }

        TableInfo(String namespace,
                  String tableName,
                  String tableDdl,
                  String[] indexDdls) {
            this.namespace = namespace;
            this.tableName = tableName;
            tableStatement = tableDdl;
            indexStatements = new ArrayList<String>();
            if (indexDdls != null) {
                indexStatements.addAll(Arrays.asList(indexDdls));
            }
        }

        public String getNamespace() {
            return namespace;
        }

        public String getTableName() {
            return tableName;
        }

        public boolean getCreateJsonTable() {
            return (tableStatement == null);
        }

        public String getTableStatement() {
            return tableStatement;
        }

        public String[] getIndexStatements() {
            return indexStatements.toArray(new String[indexStatements.size()]);
        }

        void addIndexStatement(String indexDdl) {
            indexStatements.add(indexDdl);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TableInfo)) {
                return false;
            }

            final TableInfo ti1 = (TableInfo)obj;

            return (Objects.equals(namespace, ti1.namespace) &&
                    Objects.equals(tableName, ti1.tableName) &&
                    Objects.equals(tableStatement, ti1.tableStatement) &&
                    Objects.equals(indexStatements, ti1.indexStatements));
        }

        @Override
        public int hashCode() {
            int code = 0;
            if (namespace != null) {
                code += namespace.hashCode();
            }
            if (tableName != null) {
                code += tableName.hashCode();
            }
            if (tableStatement != null) {
                code += tableStatement.hashCode();
            }
            if (indexStatements != null) {
                for (String statment : indexStatements) {
                    code += statment.hashCode();
                }
            }
            return code;
        }
    }

    /**
     * Schema file configuration
     */
    public static class SchemaFileConfig extends FileConfig {
        private final static String SchemaFileName = "schemaFile";

        public enum Type {
            DDL,
            BINARY
        }

        private String fileName;
        private Type schemaType;

        SchemaFileConfig() {
            super(SchemaFileName);
        }

        public SchemaFileConfig(String localFile, Type schemaType) {
            super(SchemaFileName, localFile);
            this.schemaType = schemaType;
        }

        public SchemaFileConfig(String fileName,
                                String containerName,
                                String serviceName,
                                String userName,
                                String password,
                                String serviceUrl,
                                Type schemaType) {
            super(SchemaFileName, containerName, serviceName,
                  userName, password, serviceUrl);
            this.fileName = fileName;
            this.schemaType = schemaType;
        }

        public String getFileName() {
            return fileName;
        }

        void setSchemaType(Type type) {
            this.schemaType = type;
        }

        public Type getSchemaType() {
            return schemaType;
        }
    }

    /**
     * An implementation of TableMetadataHelper, it finds table from local
     * <String, TableImpl> tableMap or store.
     */
    private class TableMetadataHelperImpl implements TableMetadataHelper {

        private KVStore store;

        TableMetadataHelperImpl(KVStore store) {
            this.store = store;
        }

        @Override
        public TableImpl getTable(String namespace, String tableName) {
            String qualifiedName =
                NameUtils.makeQualifiedName(namespace, tableName);
            if (tableMap != null) {
                TableImpl t = tableMap.get(qualifiedName);
                if (t != null) {
                    return t;
                }
            }
            if (store != null) {
                return (TableImpl)store.getTableAPI().getTable(qualifiedName);
            }
            return null;
        }

        @Override
        public TableImpl getTable(String namespace,
                                  String[] tablePath,
                                  int cost) {

            StringBuilder sb = new StringBuilder();
            for (String path : tablePath) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(path);
            }
            String tableName = sb.toString();
            return getTable(namespace, tableName);
        }

        @Override
        public RegionMapper getRegionMapper() {
            return ((TableAPIImpl)store.getTableAPI()).getRegionMapper();
        }
    }
}
