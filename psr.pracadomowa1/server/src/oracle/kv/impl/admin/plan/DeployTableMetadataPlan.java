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

package oracle.kv.impl.admin.plan;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import oracle.kv.impl.admin.IllegalCommandException;
import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.metadata.Metadata.MetadataType;
import oracle.kv.impl.security.KVStorePrivilege;
import oracle.kv.impl.security.NamespacePrivilege;
import oracle.kv.impl.security.ResourceOwner;
import oracle.kv.impl.security.RoleInstance;
import oracle.kv.impl.security.SystemPrivilege;
import oracle.kv.impl.security.TablePrivilege;
import oracle.kv.impl.security.metadata.SecurityMetadata;
import oracle.kv.impl.security.util.SecurityUtils;
import oracle.kv.impl.util.JsonUtils;
import oracle.kv.impl.util.SerialVersion;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A general purpose plan for executing changes to table metadata. By default
 * this plan will lock out other table metadata plans and elasticity plans.
 */
public class DeployTableMetadataPlan extends MetadataPlan<TableMetadata> {
    private static final long serialVersionUID = 1L;

    DeployTableMetadataPlan(String planName,
                            Planner planner) {
        this(planName, planner, false);
    }

    /* Public for unit test */
    public DeployTableMetadataPlan(String planName,
                                   Planner planner,
                                   boolean systemPlan) {
        super(planName, planner, systemPlan);
    }

    @Override
    protected Class<TableMetadata> getMetadataClass() {
        return TableMetadata.class;
    }

    @Override
    public MetadataType getMetadataType() {
        return MetadataType.TABLE;
    }

    @Override
    public void preExecuteCheck(boolean force, Logger executeLogger) {
        /*
         * Override the default preExecuteCheck since we do not want the
         * basis sequence number checked to allow concurrent table
         * metadata plan execution.
         */
    }

    @Override
    public String getDefaultName() {
        return "Deploy Table Metadata";
    }

    @Override
    public List<? extends KVStorePrivilege> getRequiredPrivileges() {
        /* Requires SYSDBA for table operations */
        return SystemPrivilege.sysdbaPrivList;
    }

    /**
     * Checks if admin version is at least SerialVersion.NAMESPACE_VERSION_2.
     * If not IllegalCommandException exception is thrown.
     */
    private static void checkNamespacesVersion(Planner planner,
        String namespace, String msg) throws IllegalCommandException {
        if (namespace != null) {
            checkVersion(planner.getAdmin(), SerialVersion.getKVVersion(
                SerialVersion.NAMESPACE_VERSION_2),
                msg + " until all nodes in the store have been upgraded to " +
                SerialVersion.getKVVersion(SerialVersion.NAMESPACE_VERSION_2));
        }
    }

    /*
     * A series of new version table plans since R3.3 with table authorization,
     * since they are set with finer-grain table-level privileges now rather
     * than the blanket SYSDBA privilege.
     */

    /**
     * AddTablePlan requires CREATE_ANY_TABLE privilege.
     */
    static class AddTablePlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final String namespace;

        AddTablePlan(String planName,
                     Planner planner,
                     String namespace,
                     boolean systemPlan) {
            super(planName, planner, systemPlan);
            checkNamespacesVersion(planner, namespace,
                "Cannot add tables with namespaces");
            this.namespace = namespace;
        }
        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            return Collections.singletonList(
                new NamespacePrivilege.CreateTableInNamespace(namespace));
        }
    }

    /**
     * RemoveTablePlan requires DROP_ANY_TABLE privilege.
     */
    static class RemoveTablePlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final ResourceOwner tableOwner;
        private final long tableId;
        private final String namespace;
        private final String tableName;
        private final boolean toRemoveIndex;

        RemoveTablePlan(String planName,
                        String namespace,
                        String tableName,
                        Planner planner) {
            super(planName, planner);

            checkNamespacesVersion(planner, namespace, "Cannot remove tables " +
                "with namespaces");

            final TableImpl table = TablePlanGenerator.
                getAndCheckTable(namespace, tableName, getMetadata());

            tableOwner = table.getOwner();
            tableId = table.getId();
            toRemoveIndex = !table.getIndexes().isEmpty();
            this.namespace = namespace;
            this.tableName = tableName;
        }

        @Override
        public void preExecuteCheck(boolean force, Logger plannerlogger) {

            /*
             * Checks before each execution to ensure no new privilege defined
             * on this table has been granted, before the drop operation ends
             * successfully.
             */
            checkPrivilegesOnTable();
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            return TablePlanGenerator.
                getRemoveTableRequiredPrivs(tableOwner, namespace,
                    toRemoveIndex, tableId);
        }

        /**
         * Checks whether any privileges defined on the dropping table exists
         * in any role. If yes, the drop will fail. This check prevents any
         * privilege defined on a removed table seen by users.
         */
        void checkPrivilegesOnTable() {
            final SecurityMetadata secMd =
                planner.getAdmin().getMetadata(SecurityMetadata.class,
                                               MetadataType.SECURITY);
            final Set<String> involvedRoles =
                TablePlanGenerator.getInvolvedRoles(tableId, secMd);

            if (!involvedRoles.isEmpty()) {
                throw new IllegalCommandException(
                    "Cannot drop table " + tableName + " since there are " +
                    "privileges defined on it in roles " + involvedRoles +
                    ". Please revoke the privileges explicitly and then try" +
                    " dropping the table again.");
            }
        }
    }

    /**
     * EvolveTablePlan requires EVOLVE_TABLE(tableId) privilege.
     */
    static class EvolveTablePlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final ResourceOwner tableOwner;
        private final String namespace;
        private final long tableId;

        EvolveTablePlan(String planName,
                        String namespace,
                        String tableName,
                        Planner planner,
                        boolean systemPlan) {
            super(planName, planner, systemPlan);

            checkNamespacesVersion(planner, namespace,
                "Cannot alter tables with namespaces");

            final TableImpl table = TablePlanGenerator.
                getAndCheckTable(namespace, tableName, getMetadata());
            this.tableOwner = table.getOwner();
            this.namespace = namespace;
            this.tableId = table.getId();
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            final ResourceOwner currentUser =
                SecurityUtils.currentUserAsOwner();
            if ((currentUser != null) && (currentUser.equals(tableOwner))) {
                return SystemPrivilege.usrviewPrivList;
            }
            return Collections.singletonList(
                new TablePrivilege.EvolveTable(tableId, namespace));
        }
    }

    /**
     * AddIndexPlan requires CREATE_INDEX(tableId) privilege.
     */
    static class AddIndexPlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final ResourceOwner tableOwner;
        private final String namespace;
        private final long tableId;

        AddIndexPlan(String planName,
                     String namespace,
                     String tableName,
                     Planner planner) {
            super(planName, planner);

            checkNamespacesVersion(planner, namespace,
                "Cannot add indexes on tables with namespaces");

            final TableImpl table = TablePlanGenerator.
                getAndCheckTable(namespace, tableName, getMetadata());
            this.tableOwner = table.getOwner();
            this.namespace = namespace;
            this.tableId = table.getId();
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            final ResourceOwner currentUser =
                SecurityUtils.currentUserAsOwner();
            if ((currentUser != null) && (currentUser.equals(tableOwner))) {
                return SystemPrivilege.usrviewPrivList;
            }
            return Collections.singletonList(
                new TablePrivilege.CreateIndex(tableId, namespace));
        }
    }

    /**
     * RemoveAddIndexPlan requires DROP_INDEX(tableId) privilege.
     */
    static class RemoveIndexPlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final ResourceOwner tableOwner;
        private final String namespace;
        private final long tableId;

        RemoveIndexPlan(String planName,
                        String namespace,
                        String tableName,
                        Planner planner) {
            super(planName, planner);

            checkNamespacesVersion(planner, namespace,
                "Cannot remove indexes on tables with namespaces");

            final TableImpl table = TablePlanGenerator.
                getAndCheckTable(namespace, tableName, getMetadata());
            this.tableOwner = table.getOwner();
            this.namespace = namespace;
            this.tableId = table.getId();
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            final ResourceOwner currentUser =
                SecurityUtils.currentUserAsOwner();
            if ((currentUser != null) && (currentUser.equals(tableOwner))) {
                return SystemPrivilege.usrviewPrivList;
            }
            return Collections.singletonList(
                new TablePrivilege.DropIndex(tableId, namespace));
        }
    }

    static class SetTableLimitPlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final String namespace;
        private final String tableName;
        private final ResourceOwner tableOwner;
        private final long tableId;

        SetTableLimitPlan(String planName,
                          String namespace,
                          String tableName,
                          Planner planner) {
            super(planName, planner);
            this.namespace = namespace;
            this.tableName = tableName;
            final TableImpl table = TablePlanGenerator.
                    getAndCheckTable(namespace, tableName, getMetadata());
            tableOwner = table.getOwner();
            tableId = table.getId();
        }

        // BIG TODO - what are the privs for setting the limits?
        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            final ResourceOwner currentUser =
                SecurityUtils.currentUserAsOwner();
            if ((currentUser != null) && (currentUser.equals(tableOwner))) {
                return SystemPrivilege.sysoperPrivList;
            }
            return Collections.singletonList(
                new TablePrivilege.EvolveTable(tableId, namespace));
        }

        @Override
        public String getOperation() {
            return "plan set-table-limits -name " + tableName + " ...";
        }

        @Override
        public ObjectNode getPlanJson() {
            final ObjectNode jsonTop = JsonUtils.createObjectNode();
            jsonTop.put("plan_id", getId());
            jsonTop.put("namespace", namespace);
            jsonTop.put("tableName", tableName);
            return jsonTop;
        }
    }

    static class AddNamespacePlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        AddNamespacePlan(String planName, Planner planner) {
            super(planName, planner);
            checkVersion(planner.getAdmin(),
                SerialVersion.getKVVersion(SerialVersion.NAMESPACE_VERSION_2),
                "Cannot add new namespaces until all nodes in the store" +
                " have been upgraded to " +
                SerialVersion.getKVVersion(SerialVersion.NAMESPACE_VERSION_2));
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            return SystemPrivilege.namespaceCreatePrivList;
        }
    }

    static class RemoveNamespacePlan extends MultiMetadataPlan {
        private static final long serialVersionUID = 1L;
        private final String theNamespace;
        RemoveNamespacePlan(String planName, Planner
            planner, String namespace) {
            super(planName, planner);
            this.theNamespace = namespace;
            checkVersion(planner.getAdmin(),
                SerialVersion.getKVVersion(SerialVersion.NAMESPACE_VERSION_2),
                "Cannot remove namespaces until all nodes in the store" +
                " have been upgraded to " +
                SerialVersion.getKVVersion(SerialVersion.NAMESPACE_VERSION_2));
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            return SystemPrivilege.namespaceDropPrivList;
        }

        @Override
        protected Set<MetadataType> getMetadataTypes() {
            return TABLE_SECURITY_TYPES;
        }

        @Override
        public void preExecuteCheck(boolean force, Logger plannerlogger) {
            /*
             * Checks before each execution to ensure no new privilege defined
             * on this namespace has been granted, before the drop operation
             * ends successfully.
             */
            checkPrivilegesOnNamespace();
        }

        /**
         * Checks whether any privileges defined on the dropping ns exists
         * in any role. If yes, the drop will fail. This check prevents any
         * privilege defined on a removed namespace seen by users.
         */
        void checkPrivilegesOnNamespace() {
            final SecurityMetadata secMd =
                planner.getAdmin().getMetadata(SecurityMetadata.class,
                    MetadataType.SECURITY);

            if (secMd == null) {
                return ;
            }

            for (RoleInstance role : secMd.getAllRoles()) {
                for (KVStorePrivilege priv : role.getPrivileges()) {
                    if (priv.getType().equals(
                        KVStorePrivilege.PrivilegeType.NAMESPACE) &&
                        NameUtils.namespaceEquals(
                            ((NamespacePrivilege) priv).getNamespace(),
                            theNamespace)
                        ) {

                        throw new IllegalCommandException(
                            "Cannot drop namespace '" + theNamespace +
                            "' since there are roles that have been granted " +
                            "privileges for it: " + role.name() + ". Please " +
                            "revoke the privileges explicitly and then try " +
                            "dropping the namespace again.");
                    }
                }
            }
        }
    }

    static class RegionPlan extends DeployTableMetadataPlan {
        private static final long serialVersionUID = 1L;
        RegionPlan(String planName, Planner planner) {
            super(planName, planner);
            checkVersion(planner.getAdmin(),
                         SerialVersion.getKVVersion(
                                       SerialVersion.MULTI_REGION_TABLE_VERSION),
                         "Cannot preform region operations until all nodes in" +
                         " the store have been upgraded to " +
                         SerialVersion.getKVVersion(
                                       SerialVersion.MULTI_REGION_TABLE_VERSION));
        }

        @Override
        public List<? extends KVStorePrivilege> getRequiredPrivileges() {
            return SystemPrivilege.regionCreatePrivList;
        }
    }
}
