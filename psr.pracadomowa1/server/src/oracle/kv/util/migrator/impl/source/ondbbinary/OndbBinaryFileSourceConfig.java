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

package oracle.kv.util.migrator.impl.source.ondbbinary;

import static oracle.kv.util.migrator.MainCommandParser.getNamespacesUsage;
import static oracle.kv.util.migrator.MainCommandParser.getTablesUsage;
import static oracle.kv.util.migrator.MainCommandParser.optional;
import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.Constants.EXPORT_VERSION_KEY;
import static oracle.nosql.common.migrator.util.Constants.EXPORT_FORMAT_KEY;
import static oracle.nosql.common.migrator.util.JsonUtils.*;
import static oracle.nosql.common.migrator.util.MigratorUtils.readExportInfo;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import oracle.kv.util.expimp.utils.DataSerializer;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.FileConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Configuration of nosqldb-binary source:
 * <ul>
 * <li>Local file system
 * <pre>{@code
 * "source": {
 *     "type":"file",
 *     "format":"binary",
 *     "path":<dir>                            --required
 *     "exportVersion":<export-version>        --optional, hidden
 * }}</pre>
 * <li>Object store OCI classic, all the configuration are hidden for now,
 *     because it will be deprecated.
 * <pre>{@code
 * "source": {
 *     "type":"oci_binary_store",
 *     "format":"binary",
 *     "containerName":<container-name>,
 *     "serviceName:<service-name>,
 *     "userName":<user-name>,
 *     "password":<password>,
 *     "serviceUrl":<url>
 * }}</pre></li>
 * <ul>
 */
public class OndbBinaryFileSourceConfig extends FileConfig
    implements DataSourceConfig {

    public final static String PROP_EXPORT_VERSION = "exportVersion";

    final static String DATA_FOLDER = "Data";
    final static String TABLE_SUB_FOLDER = "Table";
    final static String LOB_SUB_FOLDER = "LOB";
    final static String OTHER_SUB_FOLDER = "Other";
    final static String OTHER_DATA_FILE_PREFIX = "OtherData";
    final static String CHUNKS_KEY = "chunksKey";

    final static String COMMAND_ARGS =
        FileConfig.FILE_CONFIG_ARGS + "\n\t" +
        optional(getTablesUsage(), true) +
        optional(getNamespacesUsage());

    private int exportVersion;

    OndbBinaryFileSourceConfig() {
        super(ONDB_BINARY_TYPE);
    }

    public OndbBinaryFileSourceConfig(InputStream in, int configVersion) {
        this();
        parseJson(in, configVersion);
    }

    public OndbBinaryFileSourceConfig(MigratorCommandParser parser) {
        this();
        parseArgs(parser);
    }

    /**
     * Used by Export/Import
     */
    public OndbBinaryFileSourceConfig(String path) {
        super(ONDB_BINARY_TYPE, path);
    }

    /**
     * Used by Export/Import
     */
    public OndbBinaryFileSourceConfig(String containerName,
                                      String serviceName,
                                      String userName,
                                      String password,
                                      String serviceUrl) {
        super(ONDB_BINARY_TYPE, containerName, serviceName, userName,
              password, serviceUrl);
    }

    @Override
    public void parseJsonNode(JsonNode node, int configVersion) {
        super.parseJsonNode(node, configVersion);
        exportVersion = readInt(node, PROP_EXPORT_VERSION);
    }

    @Override
    public void writeJsonNode(ObjectNode node, int configVersion) {
        super.writeJsonNode(node, configVersion);
        /* Don't display the exportVersion */
    }

    @Override
    public void validate(boolean parseArgs) {
        super.validate(parseArgs);
        if (getFileStoreType() == FileStoreType.FILE) {
            checkDirExist(new File(getPath()));

            /*
             * Read "exportFormat" from export.info file
             *
             * Start from 19.1 release, the export.info file is created in
             * BINARY and JSON export package, it records the
             * date format("exportFormat"), and additional
             * data serialization version("exportVersion") for BINARY package.
             *
             * BINARY package before 19.1 release doesn't contain export.info
             * file, the JSON export is new in 19.1 and its package always
             * has export.info, so here we just need to check the "exportFormat"
             * information if export.info exists.
             */
            Properties props = readExportInfo(getPath());
            String value = props.getProperty(EXPORT_FORMAT_KEY);
            if (value != null && !value.equals(ONDB_BINARY_TYPE)) {
                throw new IllegalArgumentException(
                   "Invalid import package, expect " +
                   ONDB_BINARY_TYPE.toUpperCase() + " format but get " +
                   value.toUpperCase() + " format: " + getPath());
            }

            if (exportVersion == 0) {
                value = props.getProperty(EXPORT_VERSION_KEY);
                if (value != null) {
                    try {
                        exportVersion = Integer.valueOf(value);
                    } catch (NumberFormatException nfe) {
                        /* do nothing */
                    }
                }
            }
        }
    }

    public int getExportVersion() {
        return (exportVersion != 0) ?
                exportVersion : DataSerializer.CURRENT_VERSION;
    }

    private File getDataFolder() {
        if (getPath() == null) {
            return null;
        }
        return new File(getPath(), DATA_FOLDER);
    }

    File getTableFolder() {
        if (getDataFolder() == null) {
            return null;
        }
        return new File(getDataFolder(), TABLE_SUB_FOLDER);
    }

    File getOtherFolder() {
        if (getDataFolder() == null) {
            return null;
        }
        return new File(getDataFolder(), OTHER_SUB_FOLDER);
    }

    File getLobFolder() {
        if (getDataFolder() == null) {
            return null;
        }
        return new File(getDataFolder(), LOB_SUB_FOLDER);
    }
}
