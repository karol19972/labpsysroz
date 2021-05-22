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
package oracle.kv.util.migrator.impl.util;

import static oracle.kv.util.migrator.MainCommandParser.HELPER_HOSTS_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.NAMESPACES_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.STORE_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.TABLES_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.USER_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.SECURITY_FLAG;
import static oracle.kv.util.migrator.MainCommandParser.getHelperHostUsage;
import static oracle.kv.util.migrator.MainCommandParser.getNamespacesUsage;
import static oracle.kv.util.migrator.MainCommandParser.getStoreUsage;
import static oracle.kv.util.migrator.MainCommandParser.getTablesUsage;
import static oracle.kv.util.migrator.MainCommandParser.getSecurityUsage;
import static oracle.kv.util.migrator.MainCommandParser.getUserUsage;
import static oracle.kv.util.migrator.MainCommandParser.optional;
import static oracle.nosql.common.migrator.util.JsonUtils.*;

import java.io.File;

import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.impl.ConfigBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The base class for OndbSourceConfig and OndbSinkConfig.
 */
public abstract class OndbConfig extends ConfigBase {

    public static String PROP_HELPER_HOSTS = "helperHosts";
    public static String PROP_STORE_NAME = "storeName";
    public static String PROP_USERNAME = "username";
    public static String PROP_SECURITY = "security";
    public static String PROP_NAMESPACES = "namespaces";
    public static String PROP_TABLES = "tables";
    public static String PROP_REQUEST_TIMEOUT_MS = "requestTimeoutMs";

    protected final static String COMMAND_ARGS_REQ =
        getHelperHostUsage() + "\n\t" +
        getStoreUsage();

    protected final static String COMMAND_ARGS_OPT =
        optional(getUserUsage()) + " " +
        optional(getSecurityUsage(), true) +
        optional(getTablesUsage(), true) +
        optional(getNamespacesUsage(), true);

    protected String[] helperHosts;
    protected String storeName;
    protected String username;
    protected String security;
    private String[] tables;
    private String[] namespaces;
    private int requestTimeoutMs;

    public OndbConfig(String type) {
        super(type);
    }

    public OndbConfig(String type,
                      String[] helperHosts,
                      String storeName,
                      String userName,
                      String security,
                      String[] namespaces,
                      String[] tables,
                      int requestTimeoutMs) {
        this(type);

        this.helperHosts = helperHosts;
        this.storeName = storeName;
        this.username = userName;
        this.security = security;

        this.namespaces = namespaces;
        this.tables = tables;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public void parseJsonNode(JsonNode node, int configVersion) {
        super.parseJsonNode(node, configVersion);
        helperHosts = readStringArray(node, PROP_HELPER_HOSTS);
        storeName = readString(node, PROP_STORE_NAME);
        username = readString(node, PROP_USERNAME);
        security = readString(node, PROP_SECURITY);
        namespaces = readStringArray(node, PROP_NAMESPACES);
        tables = readStringArray(node, PROP_TABLES);
        requestTimeoutMs = readInt(node, PROP_REQUEST_TIMEOUT_MS);
    }

    @Override
    public void writeJsonNode(ObjectNode node, int configVersion) {
        super.writeJsonNode(node, configVersion);
        writeNode(node, PROP_HELPER_HOSTS, helperHosts);
        writeNode(node, PROP_STORE_NAME, storeName);
        writeNode(node, PROP_USERNAME, username);
        writeNode(node, PROP_SECURITY, security);
        writeNode(node, PROP_NAMESPACES, namespaces);
        writeNode(node, PROP_TABLES, tables);
        if (requestTimeoutMs > 0) {
            writeNode(node, PROP_REQUEST_TIMEOUT_MS, requestTimeoutMs);
        }
    }

    @Override
    public void validate(boolean parseArgs) {
        super.validate(parseArgs);

        if (helperHosts == null || helperHosts.length == 0) {
            requireValue((parseArgs ? HELPER_HOSTS_FLAG : "helperHosts"));
        }
        if (storeName == null) {
            requireValue((parseArgs ? STORE_FLAG : "storeName"));
        }
        if (security != null) {
            checkFileExist(new File(security));
        }
        if (requestTimeoutMs < 0) {
            invalidValue("requestTimeoutMs must not be a negative value: " +
                         requestTimeoutMs);
        }
    }

    public String[] getHelperHosts() {
        return helperHosts;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getUsername() {
        return username;
    }

    public String getSecurity() {
        return security;
    }

    public String[] getTables() {
        return tables;
    }

    public String[] getNamespaces() {
        return namespaces;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public boolean migrateAll() {
        return getTables() == null && getNamespaces() == null;
    }

    protected class OndbConfigArgumentHandler extends ConfigCommandHandler {

        public OndbConfigArgumentHandler(MigratorCommandParser parser) {
            super(parser);
        }

        @Override
        public boolean checkArg(String arg) {
            if (arg.equals(HELPER_HOSTS_FLAG)) {
                String sval = parser.nextArg(arg);
                OndbConfig.this.helperHosts = sval.split(",");
                return true;
            }
            if (arg.equals(STORE_FLAG)) {
                OndbConfig.this.storeName = parser.nextArg(arg);
                return true;
            }
            if (arg.equals(USER_FLAG)) {
                OndbConfig.this.username = parser.nextArg(arg);
                return true;
            }
            if (arg.equals(SECURITY_FLAG)) {
                OndbConfig.this.security = parser.nextArg(arg);
                return true;
            }
            if (arg.equals(NAMESPACES_FLAG)) {
                namespaces = parser.nextArgArray(arg);
                return true;
            }
            if (arg.equals(TABLES_FLAG)) {
                tables = parser.nextArgArray(arg);
                return true;
            }
            return false;
        }
    }
}
