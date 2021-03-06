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

package oracle.kv.impl.rep.admin;

import static oracle.kv.impl.util.SerialVersion.GET_TABLE_BY_ID_VERSION;
import static oracle.kv.impl.util.SerialVersion.PUSH_TABLE_COST;
import static oracle.kv.impl.util.SerialVersion.RESOURCE_TRACKING_VERSION;
import static oracle.kv.impl.util.SerialVersion.RN_SHUTDOWN_STREAM_VERSION;
import static oracle.kv.impl.util.SerialVersion.UPGRADE_VERIFY_DATA_VERSION;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import oracle.kv.impl.metadata.Metadata;
import oracle.kv.impl.metadata.MetadataInfo;
import oracle.kv.impl.metadata.MetadataKey;
import oracle.kv.impl.mgmt.RepNodeStatusReceiver;
import oracle.kv.impl.param.LoadParameters;
import oracle.kv.impl.rep.NetworkRestoreStatus;
import oracle.kv.impl.rep.RepNodeStatus;
import oracle.kv.impl.rep.admin.RepNodeAdmin.MigrationState;
import oracle.kv.impl.rep.admin.RepNodeAdmin.PartitionMigrationState;
import oracle.kv.impl.rep.admin.ResourceInfo.UsageRecord;
import oracle.kv.impl.rep.migration.PartitionMigrationStatus;
import oracle.kv.impl.security.AuthContext;
import oracle.kv.impl.security.ContextProxy;
import oracle.kv.impl.security.login.LoginHandle;
import oracle.kv.impl.security.util.KerberosPrincipals;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.DatabaseUtils.VerificationInfo;
import oracle.kv.impl.util.DatabaseUtils.VerificationOptions;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.registry.RemoteAPI;

/**
 * The administrative interface to a RepNode process.
 */
public class RepNodeAdminAPI extends RemoteAPI {

    /* Null value that will be filled in by proxyRemote */
    private final static AuthContext NULL_CTX = null;

    private static final short NEW_MIGRATION_STATE_VERSION = SerialVersion.V16;


    private final RepNodeAdmin proxyRemote;

    private RepNodeAdminAPI(RepNodeAdmin remote, LoginHandle loginHdl)
        throws RemoteException {

        super(remote);
        this.proxyRemote = ContextProxy.create(remote, loginHdl,
                                               getSerialVersion());
    }

    public static RepNodeAdminAPI wrap(RepNodeAdmin remote,
                                       LoginHandle loginHdl)
        throws RemoteException {

        return new RepNodeAdminAPI(remote, loginHdl);
    }

    /**
     * Provides the configuration details of this RepNode.  This method may
     * be called once and only once.  It is expected that it will be called
     * by a {@link oracle.kv.impl.sna.StorageNodeAgentInterface}
     * after the SNA has started the RepNode process for the first time.
     * No methods other than shutdown may be called on the RepNode until
     * it has been configured.
     */
    public void configure(Set<Metadata<? extends MetadataInfo>> metadataSet)
        throws RemoteException {

        proxyRemote.configure(metadataSet, NULL_CTX, getSerialVersion());
    }

    /**
     * Notifies the RN that new parameters are available in the storage node
     * configuration file and should be reread.
     */
    public void newParameters()
        throws RemoteException {

        proxyRemote.newParameters(NULL_CTX, getSerialVersion());
    }

    /**
     * Notifies the RN that new global parameters are available in the storage
     * node configuration file and should be reread.
     */
    public void newGlobalParameters()
        throws RemoteException {

        proxyRemote.newGlobalParameters(NULL_CTX, getSerialVersion());
    }

    /**
     * Returns this RN's view of the Topology. In a distributed system like
     * KVS, it may be temporarily different from the Topology at other nodes,
     * but will eventually become eventually consistent.
     */
    public Topology getTopology()
        throws RemoteException {

        return proxyRemote.getTopology(NULL_CTX, getSerialVersion());
    }

    /**
     * Returns the sequence number associated with the Topology at the RN.
     */
    public int getTopoSeqNum()
        throws RemoteException {

        return proxyRemote.getTopoSeqNum(NULL_CTX, getSerialVersion());
    }

    /**
     * Returns the in-memory values of the parameters for the RN. Used for
     * configuration verification.
     */
    public LoadParameters getParams()
        throws RemoteException {
        return proxyRemote.getParams(NULL_CTX, getSerialVersion());
    }

    /**
     * Shuts down this RepNode process cleanly. Since 19.1, please use
     * {@link #shutdown(boolean, boolean)}. This is method is deprecated
     * and left as a convenience for existing code calling the old method.
     *
     * @param force force the shutdown
     */
    public void shutdown(boolean force)
        throws RemoteException {

        shutdown(force, false /*checkStream*/);
    }

    /**
     * Shuts down this RepNode process, and check stream client if necessary
     *
     * @param force force the shutdown
     * @param checkStream check stream client before shutdown if true
     */
    @SuppressWarnings("deprecation")
    public void shutdown(boolean force, boolean checkStream)
        throws RemoteException {

        if (getSerialVersion() < RN_SHUTDOWN_STREAM_VERSION) {
            /*
             * if remote is old, call the old version once when the check
             * stream if false, otherwise throw UOE since it is not supported
             * at remote.
             */
            if (checkStream) {
                throw new UnsupportedOperationException
                    ("There was an attempt to shutdown an RepNode that " +
                     "is running an earlier, incompatible release (" +
                     "target ver " + RN_SHUTDOWN_STREAM_VERSION + ", " +
                     "actual ver " + getSerialVersion() + ") Please " +
                     "upgrade all components of the store before attempting " +
                     "to shut down an RN with checking stream client.");
            }
            proxyRemote.shutdown(force, NULL_CTX, getSerialVersion());
            return;
        }

        proxyRemote.shutdown(force, checkStream,
                             NULL_CTX, getSerialVersion());
    }

    /**
     * Returns the <code>RepNodeStatus</code> associated with the rep node.
     *
     * @return the service status
     */
    public RepNodeStatus ping()
        throws RemoteException {

        return proxyRemote.ping(NULL_CTX, getSerialVersion());
    }

    /**
     * Returns administrative and configuration information from the
     * repNode. Meant for diagnostic and debugging support.
     */
    public RepNodeInfo getInfo()
        throws RemoteException {

        return proxyRemote.getInfo(NULL_CTX, getSerialVersion());
    }

    public String [] startBackup()
        throws RemoteException {

        return proxyRemote.startBackup(NULL_CTX, getSerialVersion());
    }

    public long stopBackup()
        throws RemoteException {

        return proxyRemote.stopBackup(NULL_CTX, getSerialVersion());
    }

    /**
     * @param groupName
     * @param targetNodeName
     * @param targetHelperHosts
     * @param newNodeHostPort
     * @return true if this node's address can be updated in the JE
     * group database, false if there is no current master, and we need to
     * retry.
     * @throws RemoteException
     */
    public boolean updateMemberHAAddress(String groupName,
                                         String targetNodeName,
                                         String targetHelperHosts,
                                         String newNodeHostPort)
        throws RemoteException{

        /*
         * The return type of this method has changed since R1.  We do
         * not expect cross-version interoperation using this method,
         * so we simply prohibit it.
         */
        if (getSerialVersion() < 2) {
            throw new UnsupportedOperationException
                ("There was an attempt update the HA address on a RepNode " +
                 "that is running an earlier, incompatible release.  Please " +
                 "upgrade all components of the store before attempting " +
                 "to change the store's configuration.");
        }

        return proxyRemote.updateMemberHAAddress(groupName,
                                                 targetNodeName,
                                                 targetHelperHosts,
                                                 newNodeHostPort,
                                                 NULL_CTX,
                                                 getSerialVersion());
    }


    /**
     * @param groupName
     * @param targetNodeName
     * @param targetHelperHosts
     * @return true if the node was deleted from the JE group database, false
     * if there is no current master, and we need to retry.
     * @throws RemoteException
     */
    public boolean deleteMember(String groupName,
                                String targetNodeName,
                                String targetHelperHosts)
        throws RemoteException{

        return proxyRemote.deleteMember(groupName,
                                        targetNodeName,
                                        targetHelperHosts,
                                        NULL_CTX,
                                        getSerialVersion());
    }

    /**
     * Initiates a partition migration from the source node. This call must be
     * made on the destination (target) node. The admin must take the following
     * actions for each of the possible returns:
     *
     * SUCCEEDED - Update the topology to reflect the partition's new
     *             location and broadcast to the store.
     * ERROR - Invoke canceled(PartitionId, RepGroupId) on the master source
     *         repNode.
     * UNKNOWN - Retry the migration
     * All others - Enter a loop to monitor migration progress by calling
     *              getMigrationState(PartitionId)
     *
     * @param partitionId the ID of the partition to migrate
     * @param sourceRGId the ID of the partitions current location
     * @return the migration state
     * @throws RemoteException
     */
    @Deprecated
    public PartitionMigrationState migratePartition(PartitionId partitionId,
                                                    RepGroupId sourceRGId)
            throws RemoteException {
        return migratePartitionV2(partitionId,
                                  sourceRGId).getPartitionMigrationState();
    }

    /**
     * Initiates a partition migration from the source node. This call must be
     * made on the destination (target) node. The admin must take the following
     * actions for each of the possible returns:
     *
     * SUCCEEDED - Update the topology to reflect the partition's new
     *             location and broadcast to the store.
     * ERROR - Invoke canceled(PartitionId, RepGroupId) on the master source
     *         repNode.
     * UNKNOWN - Retry the migration
     * All others - Enter a loop to monitor migration progress by calling
     *              getMigrationState(PartitionId)
     *
     * @param partitionId the ID of the partition to migrate
     * @param sourceRGId the ID of the partitions current location
     * @return the migration state
     * @throws RemoteException
     */
    @SuppressWarnings("deprecation")
    public MigrationState migratePartitionV2(PartitionId partitionId,
                                             RepGroupId sourceRGId)
            throws RemoteException {

        if (getSerialVersion() < NEW_MIGRATION_STATE_VERSION) {
            PartitionMigrationState mstate =
                proxyRemote.migratePartition(partitionId, sourceRGId,
                                             NULL_CTX, getSerialVersion());
            return new MigrationState(mstate);
        }

        return proxyRemote.migratePartitionV2(partitionId, sourceRGId, NULL_CTX,
                                              getSerialVersion());
    }

    /**
     * Returns the state of a partition migration. The admin must take the
     * following actions for each of the possible returns:
     *
     * SUCCEEDED - Update the topology to reflect the partition's new
     *             location an broadcast to the store.
     * ERROR - Invoke canceled(PartitionId, RepGroupId) on the master source
     *         repNode.
     * PENDING - No action, retry after delay
     * RUNNING - No action, retry after delay
     * UNKNOWN - Retry the migration
     *
     * For each call to getMigrationState, verify that the target mastership
     * has not changed.
     *
     * @param partitionId a partition ID
     * @return the migration state
     * @throws RemoteException
     */
    @Deprecated
    public PartitionMigrationState getMigrationState(PartitionId partitionId)
            throws RemoteException {
        return getMigrationStateV2(partitionId).getPartitionMigrationState();
    }

    /**
     * Returns the state of a partition migration. The admin must take the
     * following actions for each of the possible returns:
     *
     * SUCCEEDED - Update the topology to reflect the partition's new
     *             location an broadcast to the store.
     * ERROR - Invoke canceled(PartitionId, RepGroupId) on the master source
     *         repNode.
     * PENDING - No action, retry after delay
     * RUNNING - No action, retry after delay
     * UNKNOWN - Retry the migration
     *
     * For each call to getMigrationState, verify that the target mastership
     * has not changed.
     *
     * @param partitionId a partition ID
     * @return the migration state
     * @throws RemoteException
     */
    @SuppressWarnings("deprecation")
    public MigrationState getMigrationStateV2(PartitionId partitionId)
            throws RemoteException {

        if (getSerialVersion() < NEW_MIGRATION_STATE_VERSION) {
            PartitionMigrationState mstate =
                proxyRemote.getMigrationState(partitionId, NULL_CTX,
                                              getSerialVersion());
            return new MigrationState(mstate);
        }

        return
            proxyRemote.getMigrationStateV2(partitionId, NULL_CTX,
                                            getSerialVersion());
    }

    /**
     * Requests that a partition migration for the specified partition
     * be canceled. Returns the migration state if there was a migration in
     * progress, otherwise null is returned.
     * If the migration can be canceled it will be stopped and
     * PartitionMigrationState.ERROR is returned. If the migration has passed
     * the "point of no return" in the Transfer of Control protocol or is
     * already completed PartitionMigrationState.SUCCEEDED is returned.
     * All other states indicate that the cancel should be retried.
     *
     * As with getMigrationState(PartitionId) if the return value is
     * PartitionMigrationState.ERROR, canceled(PartitionId, RepGroupId) must be
     * invoked on the migration source repNode.
     *
     * @param partitionId a partition ID
     * @return a migration state or null
     * @throws RemoteException
     */
    @Deprecated
    public PartitionMigrationState canCancel(PartitionId partitionId)
        throws RemoteException {
        return canCancelV2(partitionId).getPartitionMigrationState();
    }

    /**
     * Requests that a partition migration for the specified partition
     * be canceled. Returns the migration state if there was a migration in
     * progress, otherwise null is returned.
     * If the migration can be canceled it will be stopped and
     * PartitionMigrationState.ERROR is returned. If the migration has passed
     * the "point of no return" in the Transfer of Control protocol or is
     * already completed PartitionMigrationState.SUCCEEDED is returned.
     * All other states indicate that the cancel should be retried.
     *
     * As with getMigrationState(PartitionId) if the return value is
     * PartitionMigrationState.ERROR, canceled(PartitionId, RepGroupId) must be
     * invoked on the migration source repNode.
     *
     * @param partitionId a partition ID
     * @return a migration state or null
     * @throws RemoteException
     */
    @SuppressWarnings("deprecation")
    public MigrationState canCancelV2(PartitionId partitionId)
        throws RemoteException {
        if (getSerialVersion() < NEW_MIGRATION_STATE_VERSION) {
            PartitionMigrationState mstate =
                proxyRemote.canCancel(partitionId, NULL_CTX,
                                      getSerialVersion());
            return new MigrationState(mstate);
        }

        return proxyRemote.canCancelV2(partitionId, NULL_CTX,
                                       getSerialVersion());
    }


    /**
     * Cleans up a source migration stream after a cancel or error. If the
     * cleanup was successful true is returned. If false is returned, the
     * call should be retried. This method must be invoked on the master
     * if the source rep group.
     *
     * This method  must be invoked on the migration source repNode whenever
     * PartitionMigrationState.ERROR is returned from a call to
     * getMigrationState(PartitionId) or cancelMigration(PartitionId).
     *
     * @param partitionId a partition ID
     * @param targetRGId the target rep group ID
     * @return true if the cleanup was successful
     * @throws RemoteException
     */
    public boolean canceled(PartitionId partitionId, RepGroupId targetRGId)
        throws RemoteException {

        return proxyRemote.canceled(partitionId, targetRGId, NULL_CTX,
                                    getSerialVersion());
    }

    /**
     * Gets the status of partition migrations for the specified partition. If
     * no status is available, null is returned.
     *
     * @param partitionId a partition ID
     * @return the partition migration status or null
     */
    public PartitionMigrationStatus getMigrationStatus(PartitionId partitionId)
        throws RemoteException {

        return proxyRemote.getMigrationStatus(partitionId, NULL_CTX,
                                              getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#initiateMasterTransfer
     */
    public boolean initiateMasterTransfer(RepNodeId replicaId,
                                          int timeout,
                                          TimeUnit timeUnit)
        throws RemoteException {

        return proxyRemote.initiateMasterTransfer(replicaId, timeout, timeUnit,
                                                  NULL_CTX, getSerialVersion());
    }

    /**
     * Install a receiver for RepNode status updates, for delivering metrics
     * and service change information to the standardized monitoring/management
     * agent.
     */
    public void installStatusReceiver(RepNodeStatusReceiver receiver)
        throws RemoteException {

        proxyRemote.installStatusReceiver(receiver, NULL_CTX,
                                          getSerialVersion());
    }

    public boolean awaitConsistency(long stopTime, int timeout, TimeUnit unit)
        throws RemoteException {
        return proxyRemote.awaitConsistency(stopTime, timeout, unit,
                                       NULL_CTX, getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#getMetadataSeqNum(Metadata.MetadataType, AuthContext,
     * short)
     */
    public int getMetadataSeqNum(Metadata.MetadataType type)
        throws RemoteException {
        return proxyRemote.getMetadataSeqNum(type, NULL_CTX,
                                             getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#getMetadata(Metadata.MetadataType, AuthContext, short)
     */
    public Metadata<?> getMetadata(Metadata.MetadataType type)
        throws RemoteException {
        return proxyRemote.getMetadata(type, NULL_CTX, getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#getMetadata(Metadata.MetadataType, int, AuthContext,
     * short)
     */
    public MetadataInfo getMetadata(Metadata.MetadataType type, int seqNum)
        throws RemoteException {
        return proxyRemote.getMetadata(type, seqNum, NULL_CTX,
                                       getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#getMetadata(Metadata.MetadataType, MetadataKey, int,
     * AuthContext, short)
     */
    public MetadataInfo getMetadata(Metadata.MetadataType type,
                                    MetadataKey key,
                                    int seqNum)
        throws RemoteException {
        checkVersion(key.getRequiredSerialVersion());
        return proxyRemote.getMetadata(type, key, seqNum, NULL_CTX,
                                  getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#updateMetadata(MetadataInfo, AuthContext, short)
     */
    public int updateMetadata(MetadataInfo metadataInfo)
        throws RemoteException {
        return proxyRemote.updateMetadata(metadataInfo, NULL_CTX,
                                     getSerialVersion());
    }

    /**
     * @see RepNodeAdmin#updateMetadata(Metadata, AuthContext, short)
     */
    public void updateMetadata(Metadata<?> newMetadata)
        throws RemoteException {
        proxyRemote.updateMetadata(newMetadata, NULL_CTX, getSerialVersion());
    }

    public boolean addIndexComplete(String namespace,
                                    String indexId,
                                    String tableName)
        throws RemoteException {
        return proxyRemote.addIndexComplete(namespace, indexId, tableName,
                                            NULL_CTX, getSerialVersion());
    }

    public boolean removeTableDataComplete(String namespace,
                                           String tableName)
        throws RemoteException {
        return proxyRemote.removeTableDataComplete(namespace, tableName,
                                                   NULL_CTX, getSerialVersion());
    }

    public KerberosPrincipals getKerberosPrincipals()
        throws RemoteException {
        return proxyRemote.getKerberosPrincipals(NULL_CTX, getSerialVersion());
    }

    public boolean startNetworkRestore(RepNodeId sourceNode,
                                       boolean retainOriginalLogFile,
                                       long minVLSN)
        throws RemoteException {
        return proxyRemote.startNetworkRestore(sourceNode,
                                               retainOriginalLogFile, minVLSN,
                                               NULL_CTX, getSerialVersion());
    }

    public NetworkRestoreStatus getNetworkRestoreStatus()
       throws RemoteException {
        return proxyRemote.getNetworkRestoreStatus(NULL_CTX,
                                                   getSerialVersion());
    }

    /**
     * Verify data.
     **/
    public VerificationInfo startAndPollVerifyData(VerificationOptions options,
                                                   int planId)
        throws RemoteException {
        checkVersion(UPGRADE_VERIFY_DATA_VERSION);
        return proxyRemote.startAndPollVerifyData(options, planId, NULL_CTX,
                                           getSerialVersion());
    }

    public boolean interruptVerifyData() throws RemoteException {
        checkVersion(UPGRADE_VERIFY_DATA_VERSION);
        return proxyRemote.interruptVerifyData(NULL_CTX,
                                               getSerialVersion());
    }

    /* Resource tracking */
    public ResourceInfo exchangeResourceInfo(long sinceMillis,
                                           Collection<UsageRecord> usageRecords)
            throws RemoteException {
        checkVersion(RESOURCE_TRACKING_VERSION);
        return proxyRemote.exchangeResourceInfo(sinceMillis, usageRecords,
                                                NULL_CTX, getSerialVersion());
    }

    /**
     * Retrieve table metadata information by specific table id.
     *
     * @param tableId number of table id
     * @return metadata information which is a table instance
     * @throws RemoteException
     */
    public MetadataInfo getTableById(final long tableId)
        throws RemoteException {
        checkVersion(GET_TABLE_BY_ID_VERSION);
        return proxyRemote.getTableById(tableId,
                                        NULL_CTX,
                                        getSerialVersion());
    }

    /**
     * Gets the specified table with an optional resource cost. If the table
     * is not found, null is returned. The specified cost will be charged
     * against the table's resource limits. If the cost is greater than 0
     * and the table has resource limits and those limits have been exceeded,
     * either by this call, or by other table activity a ResourceLimitException
     * will be thrown.
     *
     * @param namespace the table namespace
     * @param tableName the table name
     * @param cost the cost
     * @return a table instance or null
     * @throws RemoteException
     */
    public MetadataInfo getTable(String namespace,
                                 String tableName,
                                 int cost)
        throws RemoteException {
        checkVersion(PUSH_TABLE_COST);
        return proxyRemote.getTable(namespace, tableName, cost,
                                    NULL_CTX, getSerialVersion());
    }

    private void checkVersion(short requiredVersion) {
        if (getSerialVersion() < requiredVersion) {
            throw new UnsupportedOperationException(
                "Command not available because service has not yet been" +
                " upgraded.  (Internal local version=" +
                requiredVersion +
                ", internal service version=" + getSerialVersion() + ")");
        }
    }
}
