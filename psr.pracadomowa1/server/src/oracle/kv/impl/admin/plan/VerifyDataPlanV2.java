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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oracle.kv.KVVersion;
import oracle.kv.impl.admin.Admin;
import oracle.kv.impl.admin.IllegalCommandException;
import oracle.kv.impl.admin.plan.task.ParallelBundle;
import oracle.kv.impl.admin.plan.task.SerialBundle;
import oracle.kv.impl.admin.plan.task.Task;
import oracle.kv.impl.admin.plan.task.TaskList;
import oracle.kv.impl.admin.plan.task.VerifyAdminDataV2;
import oracle.kv.impl.admin.plan.task.VerifyData;
import oracle.kv.impl.admin.plan.task.VerifyRNDataV2;
import oracle.kv.impl.security.KVStorePrivilege;
import oracle.kv.impl.security.SystemPrivilege;
import oracle.kv.impl.topo.AdminId;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.util.DatabaseUtils.VerificationOptions;
import oracle.kv.impl.util.JsonUtils;
import oracle.kv.impl.util.SerialVersion;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A plan that verifies either the btree, or log files, or both of databases.
 * For btree verification, it can verify primary tables and indexes. Also, it
 * can verify data records in disk. The verification can be ran on selected
 * admins or/and rns.
 */
public class VerifyDataPlanV2 extends AbstractPlan {
    private static final long serialVersionUID = 1L;
    private final boolean showMissingFiles;

    public VerifyDataPlanV2(String name,
                          Planner planner,
                          Set<AdminId> allAdmins,
                          Set<RepNodeId> allRns,
                          VerificationOptions verifyOptions) {
        super(name, planner);
        Admin admin = planner.getAdmin();
        KVVersion storeVersion = admin.getStoreVersion();
        KVVersion expectVersion = SerialVersion.
            getKVVersion(SerialVersion.UPGRADE_VERIFY_DATA_VERSION);
        if (storeVersion.compareTo(expectVersion) < 0) {
            throw new IllegalCommandException(
                "Store version is not capable " +
                "of executing plan. Required version is " +
                expectVersion.getNumericVersionString() +
                ", store version is " +
                storeVersion.getNumericVersionString());
        }
        this.showMissingFiles = verifyOptions.showMissingFiles;
        ParallelBundle parallelTasks = new ParallelBundle();

        if (allAdmins != null) {
            /* run verifications on all admins serially. */
            SerialBundle adminBundle = new SerialBundle();

            for (AdminId aid : allAdmins) {
                adminBundle.addTask(new VerifyAdminDataV2(this, aid, verifyOptions));
            }
            parallelTasks.addTask(adminBundle);
        }

        if (allRns != null) {
            /* Group rns by shard */
            Map<Integer, List<RepNodeId>> idsPerShard =
                new HashMap<Integer, List<RepNodeId>>();
            for (RepNodeId id : allRns) {
                int shardNum = id.getGroupId();
                if (idsPerShard.get(shardNum) == null) {
                    idsPerShard.put(shardNum, new ArrayList<RepNodeId>());
                }
                idsPerShard.get(shardNum).add(id);
            }

            for (List<RepNodeId> rnids : idsPerShard.values()) {
                if (rnids != null) {
                    /*
                     * run verifications on RNs within the same shard serially
                     */
                    SerialBundle rnBundle = new SerialBundle();
                    for (RepNodeId rnid : rnids) {
                        rnBundle.addTask(new VerifyRNDataV2(this, rnid, verifyOptions));
                    }
                    parallelTasks.addTask(rnBundle);

                }
            }
        }
        addTask(parallelTasks);
    }

    @Override
    public String getDefaultName() {
        return "Verify Data";
    }

    @Override
    public List<? extends KVStorePrivilege> getRequiredPrivileges() {
        /* Requires SYSOPER */
        return SystemPrivilege.sysoperPrivList;
    }

    @Override
    public ObjectNode getPlanJson() {
        ObjectNode jsonTop = JsonUtils.createObjectNode();
        ObjectNode jsonReport = jsonTop.putObject("Verify Report");
        List<TaskRun> taskRuns =
            getExecutionState().getLatestPlanRun().getTaskRuns();
        for (TaskRun t : taskRuns) {
            Task task = t.getTask();
            if (t.getState() != Task.State.SUCCEEDED) {
                jsonReport.put("" + ((VerifyData<?>)task).getNodeId(),
                               t.getState().toString());
            } else {
                addJson(task, jsonReport);
            }
        }

        return jsonTop;
    }

    private void addJson(Task task, ObjectNode jsonReport) {
        TaskList nestedTaskList = task.getNestedTasks();
        if (nestedTaskList == null) {
            jsonReport.setAll(((VerifyData<?>)task).getVerifyResult().
                              getJson(showMissingFiles));
        } else {
            for (Task t : nestedTaskList.getTasks()) {
                addJson(t, jsonReport);
            }
        }
    }
}
