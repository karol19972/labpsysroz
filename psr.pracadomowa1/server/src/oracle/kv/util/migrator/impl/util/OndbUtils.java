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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import oracle.kv.FaultException;
import oracle.kv.KVSecurityException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.LoginCredentials;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadataHelper;
import oracle.kv.impl.security.util.KVStoreLogin;
import oracle.kv.impl.security.util.KVStoreLogin.CredentialsProvider;
import oracle.kv.query.PrepareCallback;
import oracle.kv.table.Table;

/**
 * A class encapsulates utility methods used by Ondb source and sink.
 */
public class OndbUtils {

    public static KVStore connectToStore(final String storeName,
                                         final String[] helperHosts,
                                         final String userName,
                                         final String securityFile) {
        return connectToStore(storeName, helperHosts, userName,
                              securityFile, 0);
    }

    public static KVStore connectToStore(final String storeName,
                                         final String[] helperHosts,
                                         final String userName,
                                         final String securityFile,
                                         final int requestTimeoutMs) {

        KVStoreLogin storeLogin = new KVStoreLogin(userName, securityFile);
        storeLogin.loadSecurityProperties();

        final LoginCredentials loginCreds;
        if (storeLogin.foundTransportSettings()) {
            try {
                loginCreds = storeLogin.makeShellLoginCredentials();
            } catch (IOException e) {
                throw new IllegalArgumentException();
            }
        } else {
            loginCreds = null;
        }

        KVStoreConfig kconfig = new KVStoreConfig(storeName, helperHosts);
        if (requestTimeoutMs > 0 &&
            (requestTimeoutMs >
                kconfig.getSocketReadTimeout(TimeUnit.MILLISECONDS))) {
            kconfig.setSocketReadTimeout(requestTimeoutMs,
                                         TimeUnit.MILLISECONDS);
        }
        kconfig.setSecurityProperties(storeLogin.getSecurityProperties());

        try {
            return KVStoreFactory.getStore(kconfig, loginCreds,
                        KVStoreLogin.makeReauthenticateHandler(
                            new CredentialsProvider() {
                                @Override
                                public LoginCredentials getCredentials() {
                                    return loginCreds;
                                }
                    }));
        } catch (IllegalArgumentException iae) {
            throw new ConnectStoreException(
                "Failed to connect to store: " + iae.getMessage(), iae);
        } catch (KVSecurityException kse) {
            throw new ConnectStoreException(
                "Failed to connect to store: " + kse.getMessage(), kse);
        } catch (FaultException fe) {
            throw new ConnectStoreException(
                "Failed to connect to store: " + fe.getMessage(), fe);
        }
    }

    /**
     * Validates the Namespace
     */
    public static void validateNamespace(String namespace) {
        if (namespace != null) {
            TableImpl.validateNamespace(namespace);
        }
    }

    /**
     * Validates the TableName.
     */
    public static void validateTableName(String tableName) {
        if (tableName != null) {
            int pos = tableName.lastIndexOf(NameUtils.CHILD_SEPARATOR);
            String name = (pos < 0) ? tableName :
                                      tableName.substring(pos + 1);
            TableImpl.validateTableName(name, false);
        }
    }

    /**
     * Given the child table full name, return the parent table name.
     *
     * Example: If child table full name is ABC.DEF.GHI, the method returns
     * ABC.DEF
     */
    public static String getParentTableName(String tableName) {

        int index = tableName.lastIndexOf(".");

        /*
         * If the table has no parent table, return null
         */
        if (index == -1) {
            return null;
        }

        return tableName.substring(0, index);
    }

    /**
     * The comparator for table names:
     *    o Ignore case
     *    o Table with no namespace equals to Table with "sysdefault"
     *      namespace.
     */
    public static class TableNameComparator implements Comparator<String> {

        public static TableNameComparator newInstance =
            new TableNameComparator();

        @Override
        public int compare(String name1, String name2) {
            return toIternalQualifiedname(name1)
                .compareToIgnoreCase(toIternalQualifiedname(name2));
        }

        private String toIternalQualifiedname(String tableName) {
            String ns = NameUtils.switchToInternalUse(
                NameUtils.getNamespaceFromQualifiedName(tableName));

            String tname =
                NameUtils.getFullNameFromQualifiedName(tableName);
            return NameUtils.makeQualifiedName(ns, tname);
        }
    }

    /**
     * The TableFilter is to filter the table name that matches the specified
     * namespaces and table names.
     */
    public static class TableFilter {

        private final List<TableNameMatcher> matchers;

        public TableFilter(String[] namespaces, String[] tableNames) {
            if (namespaces == null && tableNames == null) {
                matchers = null;
                return;
            }

            matchers = new ArrayList<TableNameMatcher>();
            if (namespaces != null) {
                final Set<String> nsSet =
                   new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                nsSet.addAll(Arrays.asList(namespaces));
                matchers.add(new TableNameMatcher() {
                    @Override
                    public boolean matches(String tableName) {
                        String ns;
                        ns = NameUtils.getNamespaceFromQualifiedName(tableName);
                        ns = NameUtils.switchToExternalUse(ns);
                        return nsSet.contains(ns);
                    }
                });
            }

            if (tableNames != null) {
                final Set<String> tableSet =
                    new TreeSet<String>(TableNameComparator.newInstance);
                tableSet.addAll(Arrays.asList(tableNames));
                matchers.add(new TableNameMatcher() {
                    @Override
                    public boolean matches(String tableName) {
                        return tableSet.contains(tableName);
                    }
                });
            }
        }

        public boolean matches(String tableName) {
            if (matchers != null) {
                for (TableNameMatcher matcher : matchers) {
                    if (matcher.matches(tableName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static interface TableNameMatcher {
            boolean matches(String tableName);
        }
    }

    /**
     * An implementation of PrepareCallback used to do ddl validation check.
     */
    public static class ValidateDDLPrepareCallback implements PrepareCallback {
        private QueryOperation qop;
        private String tableName;
        private String namespace;
        private TableMetadataHelper tmdHelper;
        private Table newTable;

        public ValidateDDLPrepareCallback() {
            this(null);
        }

        public ValidateDDLPrepareCallback(TableMetadataHelper tmdHelper) {
            this.tmdHelper = tmdHelper;
        }

        @Override
        public void queryOperation(QueryOperation op) {
            qop = op;
        }

        public QueryOperation getOperation() {
            return qop;
        }

        @Override
        public void tableName(String name) {
            this.tableName = name;
        }

        public String getTableName() {
            return tableName;
        }

        @Override
        public void indexName(String indexName) {
        }

        @Override
        public void namespaceName(String namespaceName) {
            namespace = namespaceName;
        }

        public String getNamespace() {
            return namespace;
        }

        @Override
        public void ifNotExistsFound() {
        }

        @Override
        public void ifExistsFound() {
        }

        @Override
        public boolean prepareNeeded() {
            return false;
        }

        @Override
        public void isTextIndex() {
        }

        @Override
        public void newTable(Table t) {
            this.newTable = t;
        }

        public Table getNewTable() {
            return newTable;
        }

        @Override
        public TableMetadataHelper getMetadataHelper() {
            return tmdHelper;
        }

        public void reset() {
            qop = null;
            tableName = null;
            namespace = null;
        }

        @Override
        public void regionName(String regionName) {
        }
    }

    /**
     * The exception thrown when connect to the store failed.
     */
    public static class ConnectStoreException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ConnectStoreException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
