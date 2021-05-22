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

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import oracle.kv.impl.admin.CommandServiceAPI;
import oracle.kv.impl.admin.param.AdminParams;
import oracle.kv.impl.admin.param.StorageNodeParams;
import oracle.kv.impl.admin.plan.Plan;
import oracle.kv.impl.admin.plan.VerifyDataPlanV2;
import oracle.kv.impl.admin.plan.task.JobWrapper.TaskRunner;
import oracle.kv.impl.topo.AdminId;
import oracle.kv.impl.topo.StorageNodeId;
import oracle.kv.impl.util.DatabaseUtils;
import oracle.kv.impl.util.DatabaseUtils.VerificationInfo;
import oracle.kv.impl.util.DatabaseUtils.VerificationOptions;
import oracle.kv.impl.util.registry.RegistryUtils;

/**
 * A task that verifies the btree and log files of databases
 * for admins.
 *
 */
public class VerifyAdminDataV2 extends VerifyData<AdminId> {
    private static final long serialVersionUID = 1L;


    public VerifyAdminDataV2(VerifyDataPlanV2 plan,
                             AdminId targetAdminId,
                             VerificationOptions verifyOptions) {
        super(plan, targetAdminId, verifyOptions);

    }

    @Override
    public boolean continuePastError() {
        return false;
    }

    @Override
    protected Plan getPlan() {
        return plan;
    }

    @Override
    protected NextJob startAndPollVerify(int taskId,
                                         TaskRunner runner) {
        final AdminParams ap = plan.getAdmin().getParams().getAdminParams();
        StorageNodeId curSNId = plan.getPlanner().getAdmin().
            getCurrentParameters().get(targetId).getStorageNodeId();
        final StorageNodeParams snParam = plan.getAdmin().
            getStorageNodeParams(curSNId);
        try {
            CommandServiceAPI cs = RegistryUtils.
                getAdmin(snParam.getHostname(), snParam.getRegistryPort(),
                         plan.getLoginManager());
            if (cs != null) {
                retryTimes = 0;
                VerificationInfo info = null;
                if (started) {
                    /*poll for status*/
                    info = cs.startAndPollVerify(null, getPlan().getId());
                }

                if (!started || info == null) {
                    /* try starting verification */
                    info = cs.startAndPollVerify(verifyOptions,
                                                 getPlan().getId());
                    started = true;
                }
                if (checkForCompletion(info)) {
                    return NextJob.END_WITH_SUCCESS;
                }

            } else {
                if (!checkForRetry()) {
                    return new NextJob(Task.State.ERROR,
                                       "Lost Connection to " + targetId);
                }
                /* Retry connecting the node. */
            }

        } catch (RemoteException | NotBoundException e) {
            if (!checkForRetry()) {
                /* Retry connecting the node. */
                return new NextJob(Task.State.ERROR,
                                   "Lost Connection to " + targetId);
            }
            /* Retry connecting the node. */
        }
        return new NextJob(Task.State.RUNNING,
                           makeStartPollVerifyJob(taskId, runner),
                           ap.getServiceUnreachablePeriod());
    }

    @Override
    public StringBuilder getName(StringBuilder sb) {
        String name = "VerifyAdminData for " + targetId;
        return sb.append(name);
    }

    @Override
    public Runnable getCleanupJob() {
        return new Runnable() {
            @Override
            public void run() {
                StorageNodeId curSNId = plan.getPlanner().getAdmin().
                    getCurrentParameters().get(targetId).getStorageNodeId();
                final StorageNodeParams snParam = plan.getAdmin().
                                                getStorageNodeParams(curSNId);
                int cleanupRetryTime = 0;
                while (!plan.cleanupInterrupted() &&
                    cleanupRetryTime < LOST_CONNECTION_RETRY) {
                    try {
                        CommandServiceAPI cs = RegistryUtils.
                                            getAdmin(snParam.getHostname(),
                                                     snParam.getRegistryPort(),
                                                     plan.getLoginManager());
                        if (cs != null) {
                            cs.interruptVerifyData();
                            return;
                        }
                    } catch (Exception e) {
                        /* Failed to interrupt verification, retry*/
                        plan.getLogger().info("Failed to interrupt " +
                            "verification. Exception: " + e.getMessage() +
                            " Retry.");
                    }
                    if (DatabaseUtils.VERIFY_ERROR_HOOK != null) {
                        /* for test only*/
                        break;
                    }

                    try {
                        Thread.sleep(CLEANUP_RETRY_MILLIS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    cleanupRetryTime++;

                }

            }

        };
    }

}
