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
package oracle.kv.util.migrator.impl.sink.ondbbinary;

import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.JsonUtils.*;

import java.io.File;
import java.io.InputStream;

import oracle.nosql.common.migrator.DataSinkConfig;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.FileConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *The Configuration of nosqldb-binary sink:
 * <ul>
 * <li>Local file system
 * <pre>{@code
 * "source": {
 *     "type":"file",
 *     "format":"binary",
 *     "path":<dir>,                        --required
 *     "fileSizeMB":<mb>                    --optional, default:1024
 * }}</pre>
 * <li>Object store OCI classic, all the configuration are hidden for now,
 *     because it will be deprecated.
 * <pre>{@code
 * "source": {
 *     "type":"ocic_object_store",
 *     "format":"binary",
 *     "containerName":<container-name>,
 *     "serviceName:<service-name>,
 *     "userName":<user-name>,
 *     "password":<password>,
 *     "serviceUrl":<url>,
 *     "fileSizeMB":<mb>
 * }}</pre></li>
 * <ul>
 */
public class OndbBinaryFileSinkConfig extends FileConfig
    implements DataSinkConfig {

    final static String COMMAND_ARGS = FILE_CONFIG_ARGS;

    private int fileSizeMB;

    OndbBinaryFileSinkConfig() {
        super(ONDB_BINARY_TYPE);
    }

    public OndbBinaryFileSinkConfig(InputStream in, int configVersion) {
        this();
        parseJson(in, configVersion);
    }

    public OndbBinaryFileSinkConfig(MigratorCommandParser parser) {
        this();
        parseArgs(parser);
    }

    /**
     * Used by Export/Import
     */
    public OndbBinaryFileSinkConfig(String outputPath) {
        super(ONDB_BINARY_TYPE, outputPath);
    }

    /**
     * Used by Export/Import
     */
    public OndbBinaryFileSinkConfig(String containerName,
                                    String serviceName,
                                    String userName,
                                    String password,
                                    String serviceUrl) {
        super(ONDB_BINARY_TYPE, containerName, serviceName,
              userName, password, serviceUrl);
    }

    @Override
    public void parseJsonNode(JsonNode node, int configVersion) {
        super.parseJsonNode(node, configVersion);
        fileSizeMB = readInt(node, PROP_FILE_SIZE_MB);
    }

    @Override
    public void writeJsonNode(ObjectNode node, int configVersion) {
        super.writeJsonNode(node, configVersion);
        if (fileSizeMB > 0) {
            writeNode(node, PROP_FILE_SIZE_MB, fileSizeMB);
        }
    }

    @Override
    public void validate(boolean parseArgs) {
        super.validate(parseArgs);
        if (fileSizeMB < 0) {
            invalidValue(PROP_FILE_SIZE_MB + " must not be a negative value: " +
                         fileSizeMB);
        }

        if (getFileStoreType() == FileStoreType.FILE) {
            checkDirExist(new File(getPath()));
        }
    }

    public int getFileSizeMB() {
        return fileSizeMB;
    }

    @Override
    public void setDefaults(DataSourceConfig sourceConfig) {
    }
}
