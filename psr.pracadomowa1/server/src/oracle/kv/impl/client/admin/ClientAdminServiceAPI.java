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
package oracle.kv.impl.client.admin;

import static oracle.kv.impl.util.SerialVersion.NAMESPACE_VERSION_2;
import static oracle.kv.impl.util.SerialVersion.TABLE_LIMITS_VERSION;

import java.net.URI;
import java.rmi.RemoteException;

import oracle.kv.impl.admin.AdminFaultException;
import oracle.kv.impl.admin.CommandResult;
import oracle.kv.impl.api.table.TableLimits;
import oracle.kv.impl.security.AuthContext;
import oracle.kv.impl.security.ContextProxy;
import oracle.kv.impl.security.login.LoginHandle;
import oracle.kv.impl.test.ExceptionTestHook;
import oracle.kv.impl.test.ExceptionTestHookExecute;
import oracle.kv.impl.util.contextlogger.LogContext;
import oracle.kv.impl.util.registry.RemoteAPI;
import oracle.kv.util.ErrorMessage;


/**
 * Defines the RMI interface used by the kvclient to asynchronously submit
 * DDL statements, which will be executed by the Admin service.
 */
public class ClientAdminServiceAPI extends RemoteAPI {

    private static final AuthContext NULL_CTX = null;

    private final ClientAdminService proxyRemote;

    /* For testing only, to mimic network problems */
    public static volatile ExceptionTestHook<String, RemoteException>
                                                            REMOTE_FAULT_HOOK;

    private ClientAdminServiceAPI(ClientAdminService remote,
                                  LoginHandle loginHdl)
        throws RemoteException {

        super(remote);
        this.proxyRemote =
            ContextProxy.create(remote, loginHdl, getSerialVersion());
    }

    public static ClientAdminServiceAPI wrap(ClientAdminService remote,
                                             LoginHandle loginHdl)
        throws RemoteException {

        return new ClientAdminServiceAPI(remote, loginHdl);
    }

    /**
     * Submit a DDL statement for asynchronous execution and return status
     * about the corresponding plan.
     *
     * @param statement - a DDL statement
     * @param namespace - an optional namespace string
     * @param limits - optional limits to use on the table
     * @return information about the current execution state of the plan
     * @throws RemoteException
     */
    @SuppressWarnings("deprecation")
    public ExecutionInfo execute(char[] statement,
                                 String namespace,
                                 boolean validateNamespace,
                                 TableLimits limits,
                                 LogContext lc,
                                 AuthContext auth)
        throws RemoteException {

        assert ExceptionTestHookExecute.doHookIfSet(REMOTE_FAULT_HOOK,
                                                    "execute");

        if (getSerialVersion() < NAMESPACE_VERSION_2) {
            return convertInfo(
                proxyRemote.execute(statement, namespace, limits, lc,
                    NULL_CTX, getSerialVersion()));
        }
        return convertInfo(
            proxyRemote.execute(statement, namespace, validateNamespace,
                limits, lc, auth == null ? NULL_CTX : auth,
                getSerialVersion()));
    }

    public ExecutionInfo setTableLimits(String namespace,
                                        String tableName,
                                        TableLimits limits)
        throws RemoteException {

        checkMethodSupported(TABLE_LIMITS_VERSION);

        return proxyRemote.setTableLimits(namespace, tableName, limits,
                                          NULL_CTX, getSerialVersion());
    }

    /**
     * Get current status for the specified plan
     * @param planId
     * @return detailed plan status
     */
    public ExecutionInfo getExecutionStatus(int planId)
        throws RemoteException {

        assert ExceptionTestHookExecute.doHookIfSet(REMOTE_FAULT_HOOK,
                                                    "getExecutionStatus");

        return getExecutionStatus(planId, NULL_CTX);
    }

    /*
     * Public API is used by proxy only.
     */
    public ExecutionInfo getExecutionStatus(int planId, AuthContext authCtx)
        throws RemoteException {
        /*
         * Note that the STATEMENT_RESULT_VERSION conversion does not need to
         * happen for plan statements, and this entry point only applies to
         * plan statements.
         */
        return proxyRemote.getExecutionStatus(planId,
                                              authCtx,
                                              getSerialVersion());
    }

    /**
     * Return true if this Admin can handle DDL operations. That currently
     * equates to whether the Admin is a master or not.
     */
    public boolean canHandleDDL() throws RemoteException {

        return proxyRemote.canHandleDDL(NULL_CTX, getSerialVersion());
    }

    /**
     * Return the address of the master Admin. If this Admin doesn't know that,
     * return null.
     *
     * @throws RemoteException
     */
    public URI getMasterRmiAddress() throws RemoteException {
        return proxyRemote.getMasterRmiAddress(NULL_CTX, getSerialVersion());
    }

    /**
     * Initiate a plan cancellation.
     */
    public ExecutionInfo interruptAndCancel(int planId)
        throws RemoteException {

        assert ExceptionTestHookExecute.doHookIfSet(REMOTE_FAULT_HOOK,
                                                    "interruptAndCancel");

        return interruptAndCancel(planId, NULL_CTX);
    }

    /*
     * Public API is used by proxy only.
     */
    public ExecutionInfo interruptAndCancel(int planId,
                                            AuthContext authCtx)
        throws RemoteException {

        /*
         * Note that the STATEMENT_RESULT_VERSION conversion does not need to
         * happen for plan statements, and this entry point only applies to
         * plan statements.
         */
        return proxyRemote.interruptAndCancel(planId,
                                              authCtx,
                                              getSerialVersion());
    }

    /**
     * Manage any protocol upgrades needed to the ExecutionInfo class
     */
    private ExecutionInfo convertInfo(ExecutionInfo execInfo) {

        /* Nothing to do */
        return execInfo;
    }

    /**
     * Throws UnsupportedOperationException if a method is not supported by the
     * admin service.
     */
    private void checkMethodSupported(short expectVersion)
        throws UnsupportedOperationException {

        if (getSerialVersion() < expectVersion) {
            final String errMsg =
                    "Command not available because service has not yet been" +
                    " upgraded.  (Internal local version=" + expectVersion +
                    ", internal service version=" + getSerialVersion() + ")";
            throw new AdminFaultException(
                new UnsupportedOperationException(errMsg), errMsg,
                ErrorMessage.NOSQL_5200, CommandResult.NO_CLEANUP_JOBS);
        }
    }
}
