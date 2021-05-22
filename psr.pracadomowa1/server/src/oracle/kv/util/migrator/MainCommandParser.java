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

import oracle.nosql.common.migrator.MigratorCommandParser;

/**
 * The migrator command line parser
 */
public class MainCommandParser extends MigratorCommandParser {

    public static final String MIGRATOR_USAGE_PREFIX =
        "Usage: java -jar KVHOME/lib/migrator.jar ";

    public static final String HELPER_HOSTS_FLAG = "-helper-hosts";
    public static final String STORE_FLAG = "-store";
    public static final String USER_FLAG  = "-username";
    public static final String SECURITY_FLAG  = "-security";
    public static final String DEFAULT_NAMESPACE_FLAG = "-default-namespace";
    public static final String NAMESPACES_FLAG = "-namespaces";
    public static final String TABLES_FLAG = "-tables";

    /* OndbSinkConfig flags */
    public final static String OVERWRITE_FLAG = "-overwrite";
    public final static String SCHEMA_FILE_FLAG = "-schema-file";
    public final static String SCHEMA_BINARY_FLAG = "-binary";
    public final static String TTL_RELATIVE_DAY_FLAG = "-ttl-relative-date";

    public MainCommandParser(String[] args) {
        this(args, null);
    }

    public MainCommandParser(String[] args, String usageText) {
        super(args, usageText);
    }

    @Override
    public String getCommandPrefix() {
        return MIGRATOR_USAGE_PREFIX;
    }

    @Override
    public void usage(String usage, String errorMessage) {
        Migrator.usage(usage, errorMessage);
    }

    public static String getHelperHostUsage() {
        return HELPER_HOSTS_FLAG + " <host:port[,host:port]*>";
    }

    public static String getStoreUsage() {
        return STORE_FLAG + " <storeName>";
    }

    public static String getUserUsage() {
        return USER_FLAG + " <user>";
    }

    public static String getSecurityUsage() {
        return SECURITY_FLAG + " <security-file-path>";
    }

    public static String getDefaultNamespaceUsage() {
        return DEFAULT_NAMESPACE_FLAG + " <namespace>";
    }

    public static String getNamespacesUsage() {
        return NAMESPACES_FLAG + " <namespace>[,<namespace>]*";
    }

    public static String getTablesUsage() {
        return TABLES_FLAG + " <table>[,<table>]*";
    }

    public static String getSchemaFileUsage() {
        return  SCHEMA_FILE_FLAG + " <file-or-dir> ";
    }

    public static String getTtlRelativeUsage() {
        return TTL_RELATIVE_DAY_FLAG + " <yyyy-MM-dd HH:mm:ss>";
    }
}
