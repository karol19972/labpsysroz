/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.sleepycat.je.rep.stream;

import java.net.InetSocketAddress;

import com.sleepycat.je.rep.elections.Proposer.Proposal;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.utilint.NotSerializable;

/**
 * Class used by a node to track changes in Master Status. It's updated by
 * the Listener. It represents the abstract notion that the notion of the
 * current Replica Group is definitive and is always in advance of the notion
 * of a master at each node. A node is typically playing catch up as it tries
 * to bring its view in line with that of the group.
 */
public class MasterStatus implements Cloneable {

    /* This node's identity */
    private final NameIdPair nameIdPair;

    /* The current master resulting from election notifications */
    private String groupMasterHostName = null;
    private int groupMasterPort = 0;
    /* The node ID used to identify the master. */
    private NameIdPair groupMasterNameId = NameIdPair.NULL;
    /* The proposal that resulted in this group master. */
    private Proposal groupMasterProposal = null;

    /*
     * The Master as implemented by the Node. It can lag the groupMaster
     * as the node tries to catch up.
     */
    private String nodeMasterHostName = null;
    private int nodeMasterPort = 0;
    private NameIdPair nodeMasterNameId = NameIdPair.NULL;
    /* The proposal that resulted in this node becoming master. */
    private Proposal nodeMasterProposal = null;

    /*
     * True, if the node knows of a master (could be out of date) and the node
     * and group's view of the master is in sync. The two are out of sync when
     * a node is informed about the result of an election, but has not had a
     * chance to react to that election result. Make sure to update this field
     * by calling updateInSync if nodeMasterNameId or groupMasterNameId change.
     */
    private volatile boolean inSync = false;

    public MasterStatus(NameIdPair nameIdPair) {
        this.nameIdPair = nameIdPair;
    }

    /**
     * Returns a read-only snapshot of the object.
     */
    @Override
    public synchronized Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            assert(false);
        }
        return null;
    }

    /**
     * Returns true if it's the master from the Group's perspective
     */
    public synchronized boolean isGroupMaster() {
        final int id = nameIdPair.getId();
        return (id != NameIdPair.NULL_NODE_ID) &&
            (id == groupMasterNameId.getId());
    }

    /**
     * Returns true if it's the master from the node's localized perspective
     */
    public synchronized boolean isNodeMaster() {
        final int id = nameIdPair.getId();
        return (id != NameIdPair.NULL_NODE_ID) &&
            (id == nodeMasterNameId.getId());
    }

    public synchronized void setGroupMaster(String hostname,
                                            int port,
                                            NameIdPair newGroupMasterNameId,
                                            Proposal proposal) {
        groupMasterHostName = hostname;
        groupMasterPort = port;
        groupMasterNameId = newGroupMasterNameId;
        groupMasterProposal = proposal;
        updateInSync();
    }

    /**
     * Predicate to determine whether the group and node have a consistent
     * notion of the Master. Note that it's not synchronized to minimize
     * contention.
     *
     * @return false if the node does not know of a Master, or the group Master
     * is different from the node's notion the master.
     */

    public boolean inSync() {
        return inSync;
    }

    private void updateInSync() {
        assert Thread.holdsLock(this);
        inSync = !nodeMasterNameId.hasNullId() &&
            (isNodeMaster() ?
                /*
                 * A master, avoid unnecessary transitions through the
                 * UNKNOWN state, due to new re-affirming proposals
                 */
             (groupMasterNameId.getId() == nodeMasterNameId.getId()) :
                 /*
                  * A replica, need to reconnect, if there is a newer proposal
                  */
             (groupMasterProposal == nodeMasterProposal));
    }

    public synchronized void unSync() {
        nodeMasterHostName = null;
        nodeMasterPort = 0;
        nodeMasterNameId = NameIdPair.NULL;
        nodeMasterProposal = null;
        updateInSync();
    }

    /**
     * An assertion form of the above. By combining the check and exception
     * generation in an atomic operation, it provides for an accurate exception
     * message.
     *
     * @throws MasterSyncException
     */
    public void assertSync()
        throws MasterSyncException {

        if (!inSync()) {
            synchronized (this) {
                /* Check again in synchronized block. */
                if (!inSync()) {
                    throw new MasterSyncException();
                }
            }
        }
    }

    /**
     * Syncs to the group master
     */
    public synchronized void sync() {
        nodeMasterHostName = groupMasterHostName;
        nodeMasterPort = groupMasterPort;
        nodeMasterNameId = groupMasterNameId;
        nodeMasterProposal = groupMasterProposal;
        updateInSync();
    }

    /**
     * Returns the Node's current idea of the Master. It may be "out of sync"
     * with the Group's notion of the Master
     */
    public synchronized InetSocketAddress getNodeMaster() {
        if (nodeMasterHostName == null) {
            return null;
        }
        return new InetSocketAddress(nodeMasterHostName, nodeMasterPort);
    }

    public synchronized NameIdPair getNodeMasterNameId() {
        return nodeMasterNameId;
    }

    /**
     * Returns a socket that can be used to communicate with the group master.
     * It can return null, if there is no current group master, that is,
     * groupMasterNameId is NULL.
     */
    public synchronized InetSocketAddress getGroupMaster() {
        if (groupMasterHostName == null) {
             return null;
        }
        return new InetSocketAddress(groupMasterHostName, groupMasterPort);
    }

    public synchronized NameIdPair getGroupMasterNameId() {
        return groupMasterNameId;
    }

    @SuppressWarnings("serial")
    public class MasterSyncException extends Exception
        implements NotSerializable {

        private final NameIdPair savedGroupMasterId;
        private final NameIdPair savedNodeMasterId;

        MasterSyncException () {
            savedGroupMasterId = MasterStatus.this.getGroupMasterNameId();
            savedNodeMasterId = MasterStatus.this.getNodeMasterNameId();
        }

        @Override
        public String getMessage() {
            return "Master change. Node master id: " + savedNodeMasterId +
            " Group master id: " + savedGroupMasterId;
        }
    }
}
