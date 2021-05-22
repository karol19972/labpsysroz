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

import com.sleepycat.je.Transaction;

import oracle.kv.impl.admin.Admin;
import oracle.kv.impl.admin.PlanLocksHeldException;
import oracle.kv.impl.admin.RegionAlreadyExistsException;
import oracle.kv.impl.admin.RegionNotFoundException;
import oracle.kv.impl.admin.plan.MetadataPlan;
import oracle.kv.impl.admin.plan.Planner;
import oracle.kv.impl.api.table.Region;
import oracle.kv.impl.api.table.TableMetadata;

/**
 * Tasks for region related operations.
 */
public abstract class RegionTask extends UpdateMetadata<TableMetadata> {
    private static final long serialVersionUID = 1L;

    protected final String regionName;

    protected RegionTask(MetadataPlan<TableMetadata> plan, String regionName) {
        super(plan);
        if (regionName == null) {
            throw new IllegalArgumentException("Region name cannot be null");
        }
        /* Throws FaultException if the multi-region manager is not ready. */
        plan.getAdmin().getMRTServiceManager().checkForReady();
        this.regionName = regionName;
    }

    @Override
    public void acquireLocks(Planner planner) throws PlanLocksHeldException {
        LockUtils.lockRegion(planner, getPlan(), regionName);
    }

    @Override
    protected TableMetadata createMetadata() {
        return new TableMetadata(true);
    }

    @Override
    public StringBuilder getName(StringBuilder sb) {
        return super.getName(sb).append(" ").append(regionName);
    }

    /**
     * Creates a region.
     */
    public static class CreateRegion extends RegionTask {
        private static final long serialVersionUID = 1L;

        public CreateRegion(MetadataPlan<TableMetadata> plan,
                            String regionName) {
            super(plan, regionName);

            final TableMetadata md = plan.getMetadata();
            if ((md != null) && (md.getActiveRegion(regionName) != null)) {
                throw new RegionAlreadyExistsException("Region " + regionName +
                                                      " already exists");
            }
        }

        @Override
        protected TableMetadata updateMetadata(TableMetadata md,
                                               Transaction txn) {
            if (md.createRegion(regionName)) {
                final Admin admin = getPlan().getAdmin();
                admin.saveMetadata(md, txn);
                admin.getMRTServiceManager().
                      postCreateRegion(getPlan().getId(),
                                       md.getActiveRegion(regionName),
                                       md.getSequenceNumber());
                return md;
            }
            return null;
        }
    }

    /**
     * Drops a region.
     */
    public static class DropRegion extends RegionTask {
        private static final long serialVersionUID = 1L;

        public DropRegion(MetadataPlan<TableMetadata> plan,
                          String regionName) {
            super(plan, regionName);

            final TableMetadata md = plan.getMetadata();
            if ((md != null) && (md.getActiveRegion(regionName) == null)) {
                throw new RegionNotFoundException("Region " + regionName +
                                                   " does not exist");
            }
        }

        @Override
        protected TableMetadata updateMetadata(TableMetadata md,
                                               Transaction txn) {
            final Region region = md.dropRegion(regionName);
            if (region != null) {
                final Admin admin = getPlan().getAdmin();
                admin.saveMetadata(md, txn);
                admin.getMRTServiceManager().
                      postDropRegion(getPlan().getId(),
                                     region.getId(),
                                     md.getSequenceNumber());
                return md;
            }
            return null;
        }
    }

    /**
     * Sets the local region name.
     */
    public static class SetLocalRegionName extends RegionTask {
        private static final long serialVersionUID = 1L;

        public SetLocalRegionName(MetadataPlan<TableMetadata> plan,
                                  String regionName) {
            super(plan, regionName);
        }

        @Override
        protected TableMetadata updateMetadata(TableMetadata md,
                                               Transaction txn) {
            if (md.setLocalRegionName(regionName)) {
                final Admin admin = getPlan().getAdmin();
                admin.saveMetadata(md, txn);
                admin.getMRTServiceManager().
                      postCreateRegion(getPlan().getId(),
                                       md.getLocalRegion(),
                                       md.getSequenceNumber());
                return md;
            }
            return null;
        }
    }
}
