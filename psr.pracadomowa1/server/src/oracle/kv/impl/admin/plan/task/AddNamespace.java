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

package oracle.kv.impl.admin.plan.task;

import oracle.kv.impl.admin.IllegalCommandException;
import oracle.kv.impl.admin.NamespaceAlreadyExistsException;
import oracle.kv.impl.admin.PlanLocksHeldException;
import oracle.kv.impl.admin.plan.MetadataPlan;
import oracle.kv.impl.admin.plan.Planner;
import oracle.kv.impl.admin.plan.TablePlanGenerator;
import oracle.kv.impl.api.table.TableMetadata;
import oracle.kv.impl.security.util.SecurityUtils;

import com.sleepycat.je.Transaction;

/**
 * Adds a namespace
 */
public class AddNamespace extends UpdateMetadata<TableMetadata> {
    private static final long serialVersionUID = 1L;

    private final String namespace;

    public AddNamespace(MetadataPlan<TableMetadata> plan, String namespace) {
        super(plan);

        this.namespace = namespace;
        /*
         * Caller verifies parameters
         */

        final TableMetadata md = plan.getMetadata();
        if (md == null) {
            throw tableMetadataNotFound();
        }
        if (md.hasNamespace(namespace)) {
            throw namespaceAlreadyExists();
        }
    }


    static IllegalCommandException tableMetadataNotFound() {
        return new IllegalCommandException("Table metadata not found");
    }

    @Override
    public void acquireLocks(Planner planner) throws PlanLocksHeldException {
        LockUtils.lockNamespace(planner, getPlan(), namespace);
    }

    @Override
    protected TableMetadata createMetadata() {
        return new TableMetadata(true);
    }

    @Override
    protected TableMetadata updateMetadata(TableMetadata md, Transaction txn) {

        /* If the namespace does not exist, add it */
        if (!md.hasNamespace(namespace)) {
            md.createNamespace(namespace, SecurityUtils.currentUserAsOwner());
            getPlan().getAdmin().saveMetadata(md, txn);
            return md;
        }

        throw namespaceAlreadyExists();
    }

    /**
     * Throws a "Namespace  already exists" IllegalCommandException.
     */
    private NamespaceAlreadyExistsException namespaceAlreadyExists() {
        return new NamespaceAlreadyExistsException
            ("Namespace '" + namespace + "' already exists");
    }

    @Override
    public boolean continuePastError() {
        return false;
    }

    @Override
    public StringBuilder getName(StringBuilder sb) {
        return TablePlanGenerator.makeName(super.getName(sb), namespace, null,
            null);
    }

    /**
     * Returns true if this AddNamespace will end up creating the same
     * namespace.
     * Checks that namespace is the same. Intentionally excludes r2compat,
     * schemId, and description, since those don't directly affect the table
     * metadata.
     */
    @Override
    public boolean logicalCompare(Task t) {
        if (this == t) {
            return true;
        }

        if (t == null) {
            return false;
        }

        if (getClass() != t.getClass()) {
            return false;
        }

        AddNamespace other = (AddNamespace) t;
        if (namespace == null) {
            if (other.namespace != null) {
                return false;
            }
        } else if (!namespace.equalsIgnoreCase(other.namespace)) {
            return false;
        }

        return true;
    }
}
