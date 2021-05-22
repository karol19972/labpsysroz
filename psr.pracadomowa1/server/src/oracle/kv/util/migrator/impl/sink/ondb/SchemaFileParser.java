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

import static oracle.nosql.common.migrator.util.MigratorUtils.toFilePath;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import oracle.kv.impl.api.table.DDLGenerator;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.RegionMapper;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableJsonUtils;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.compiler.CompilerAPI;
import oracle.kv.query.ExecuteOptions;
import oracle.kv.query.PrepareCallback.QueryOperation;
import oracle.kv.util.migrator.impl.sink.ondb.OndbSinkConfig.SchemaFileConfig;
import oracle.kv.util.migrator.impl.util.OndbUtils;
import oracle.kv.util.migrator.impl.util.OndbUtils.ValidateDDLPrepareCallback;

/**
 * The parser of schema files
 */
public class SchemaFileParser {

    private File fileOrDir;
    private InputStream stream;
    private SchemaFileConfig.Type type;
    private SchemaParserCallback spbc;

    public SchemaFileParser(InputStream stream,
                            SchemaFileConfig.Type type,
                            SchemaParserCallback callback) {
        this.stream = stream;
        this.type = type;
        this.spbc = callback;
    }

    public SchemaFileParser(File schemaFileOrDir,
                            SchemaFileConfig.Type type,
                            SchemaParserCallback callback) {
        this.fileOrDir = schemaFileOrDir;
        this.type = type;
        this.spbc = callback;
    }

    public interface SchemaParserCallback {
        void tableDdl(String namespace, String tableName, String ddl);
        void indexDdls(String namespace, String tableName, String... ddl);
        void newTable(TableImpl table);
        TableMetadataHelper getTableMetadataHelper();
    }

    public void parse() {
        if (fileOrDir != null) {
            if (fileOrDir.isDirectory()) {
                for (File file : fileOrDir.listFiles()) {
                    parse(file);
                }
            } else {
                parse(fileOrDir);
            }
        } else {
            parse(stream);
        }
    }

    private void parse(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            parse(in);
        } catch (FileNotFoundException fnfe) {
            throw new IllegalArgumentException("Schema file not found: " +
                                               toFilePath(file));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void parse(InputStream in) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String firstLine = null;
            if (type == null) {
                firstLine = reader.readLine();
                type = checkSchemaFileType(firstLine);
            }
            getSchemaParser(reader, firstLine).parse();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(
                "Failed to read schema file: " + ioe.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private SchemaFileConfig.Type checkSchemaFileType(String line) {
        return line.contains("{") ? SchemaFileConfig.Type.BINARY :
                                    SchemaFileConfig.Type.DDL;
    }

    private SchemaParser getSchemaParser(BufferedReader reader,
                                         String firstLine) {
        switch(type) {
        case DDL:
            return new DDLSchemaParser(reader, firstLine);
        case BINARY:
            return new BinarySchemaParser(reader, firstLine);
        default:
            throw new IllegalArgumentException("Invalid schema type: " + type);
        }
    }

    /**
     * Schema parser interface
     */
    private interface SchemaParser  {
        void parse() throws IOException;
    }

    /**
     * The parser for schema file that contains DDL statements, the statements
     * can include:
     *   o table statements: create table or alter table
     *   o index statements: create index or drop index
     */
    public class DDLSchemaParser implements SchemaParser {

        private BufferedReader reader;
        private String firstLine;

        DDLSchemaParser(BufferedReader reader,
                        String firstLine) {
            this.reader = reader;
            this.firstLine = firstLine;
        }

        @Override
        public void parse() throws IOException {

            TableMetadataHelper mdHelper =
                (spbc != null) ? spbc.getTableMetadataHelper() : null;
            ValidateDDLPrepareCallback pcb =
                new ValidateDDLPrepareCallback(mdHelper);
            ExecuteOptions opt = new ExecuteOptions().setPrepareCallback(pcb);
            String ddl = (firstLine != null) ? firstLine : reader.readLine();
            String namespace;
            String tableName;
            while (ddl != null) {
                try {
                    CompilerAPI.prepare(null, ddl.toCharArray(), opt);
                    QueryOperation op = pcb.getOperation();
                    if (op != null) {
                        switch (op) {
                        case CREATE_TABLE:
                            namespace = pcb.getNamespace();
                            tableName = pcb.getTableName();
                            if (spbc != null) {
                                spbc.tableDdl(namespace, tableName, ddl);
                                spbc.newTable((TableImpl)pcb.getNewTable());
                            }
                            break;
                        case CREATE_INDEX:
                        case DROP_INDEX:
                            namespace = pcb.getNamespace();
                            tableName = pcb.getTableName();
                            if (spbc != null) {
                                spbc.indexDdls(namespace, tableName, ddl);
                            }
                            break;
                        case ALTER_TABLE:
                            namespace = pcb.getNamespace();
                            tableName = pcb.getTableName();
                            if (spbc != null) {
                                spbc.tableDdl(namespace, tableName, ddl);
                            }
                            break;
                        default:
                            throw new IllegalArgumentException(
                                "Invalid ddl statement: " + op);
                        }
                    }
                    pcb.reset();
                } catch (QueryException re) {
                    /* ignore invalid statement */
                    throw new IllegalArgumentException(
                        "Invalid ddl statement \"" + ddl + "\":" +
                        re.getMessage());
                }
                ddl = readNextLine(reader);
            }
        }
    }

    /**
     * Ignore empty line, comments line that starts with '#', remove tailing
     * spaces and the ';' at the end if exists
     */
    private String readNextLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = line.trim();
            if (line.endsWith(";")) {
                return line.substring(0, line.length() - 1);
            }
            return line;
        }
        return null;
    }

    /**
     * The parser for schema file that contains native format that dumped
     * with nosqldb-binary sink:
     *
     *  T, TableFullNamespaceName: TableSchemaJsonString
     */
    private class BinarySchemaParser implements SchemaParser {

        private BufferedReader reader;
        private String firstLine;

        BinarySchemaParser(BufferedReader reader, String firstLine) {
            this.reader = reader;
            this.firstLine = firstLine;
        }

        @Override
        public void parse() throws IOException {
            TableMetadataHelper mdHelper =
                (spbc != null) ? spbc.getTableMetadataHelper() : null;

            Map<String, String> tableJsonMap = new TreeMap<String, String>();
            String line = (firstLine != null) ? firstLine : reader.readLine();
            while (line != null) {
                parseLine(line, tableJsonMap);
                line = reader.readLine();
            }
            /*
             * Sorting the table schemas by table full name to make sure the
             * parent table will be parsed before child table
             */
            for (Entry<String, String> e : tableJsonMap.entrySet()) {
                addTableSchema(e.getKey(), e.getValue(),
                               mdHelper == null ? null :
                                                  mdHelper.getRegionMapper());
            }
        }

        private void parseLine(String schemaJsonString,
                               Map<String, String> tableJsonMap) {
            /*
             * Get the schema type of the schema definition. This can be
             * either 'T' representing table definition.
             */
            String identity = schemaJsonString.substring(0,
                                  schemaJsonString.indexOf(",")).trim();
            if (identity != null && identity.equals("T")) {
                String jsonString = schemaJsonString.substring(
                                        schemaJsonString.indexOf(",") + 1,
                                        schemaJsonString.length()).trim();
                addTableJsonSchema(jsonString, tableJsonMap);
            }
        }

        private void addTableJsonSchema(String jsonString,
                                        Map<String, String> tableJsonMap) {

            int pos = jsonString.indexOf("{");
            String tableFullName =
                jsonString.substring(0, jsonString.lastIndexOf(":", pos)).trim();
            String jsonSchema =
                jsonString.substring(pos, jsonString.length()).trim();
            tableJsonMap.put(tableFullName, jsonSchema);
        }

        private void addTableSchema(String tableFullName,
                                    String jsonSchema,
                                    RegionMapper regionMapper) {

            String ns = NameUtils.getNamespaceFromQualifiedName(tableFullName);
            String tableName =
                NameUtils.getFullNameFromQualifiedName(tableFullName);

            TableImpl parentTable = getParentTable(tableFullName);
            TableImpl table =
                TableJsonUtils.fromJsonString(jsonSchema, parentTable);

            if (spbc != null) {
                spbc.newTable(table);
            }

            DDLGenerator ddlGenerator =
                new DDLGenerator(table, true /* withIfNotExists */, regionMapper);
            String tableDdl = ddlGenerator.getDDL();
            List<String> indexDdls = ddlGenerator.getAllIndexDDL();

            if (spbc != null) {
                spbc.tableDdl(ns, tableName, tableDdl);
                spbc.indexDdls(ns, tableName,
                    indexDdls.toArray(new String[indexDdls.size()]));
            }
        }

        private TableImpl getParentTable(String qualifiedName) {
            String parentTableName = OndbUtils.getParentTableName(qualifiedName);
            if (parentTableName == null) {
                return null;
            }

            if (spbc != null) {
                TableMetadataHelper mdHelper = spbc.getTableMetadataHelper();
                if (mdHelper != null) {
                    String namespace = NameUtils.getNamespaceFromQualifiedName(
                                           parentTableName);
                    String tableName = NameUtils.getFullNameFromQualifiedName(
                                           parentTableName);
                    TableImpl ptable = mdHelper.getTable(namespace, tableName);
                    if (ptable != null) {
                        return ptable;
                    }
                }
            }
            throw new IllegalArgumentException("Cannot load child table " +
                qualifiedName + " before loading the parent tables");
        }
    }
}
