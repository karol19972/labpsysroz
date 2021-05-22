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

package oracle.kv.impl.util.registry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static oracle.kv.KVStoreConfig.DEFAULT_REGISTRY_READ_TIMEOUT;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import oracle.kv.AuthenticationFailureException;
import oracle.kv.KVStoreConfig;
import oracle.kv.impl.admin.CommandService;
import oracle.kv.impl.admin.CommandServiceAPI;
import oracle.kv.impl.admin.param.GlobalParams;
import oracle.kv.impl.admin.param.Parameters;
import oracle.kv.impl.admin.param.StorageNodeParams;
import oracle.kv.impl.api.ClientId;
import oracle.kv.impl.api.RequestHandler;
import oracle.kv.impl.api.RequestHandlerAPI;
import oracle.kv.impl.arb.admin.ArbNodeAdmin;
import oracle.kv.impl.arb.admin.ArbNodeAdminAPI;
import oracle.kv.impl.client.admin.ClientAdminService;
import oracle.kv.impl.client.admin.ClientAdminServiceAPI;
import oracle.kv.impl.fault.OperationFaultException;
import oracle.kv.impl.monitor.MonitorAgent;
import oracle.kv.impl.monitor.MonitorAgentAPI;
import oracle.kv.impl.rep.admin.RepNodeAdmin;
import oracle.kv.impl.rep.admin.RepNodeAdminAPI;
import oracle.kv.impl.security.login.LoginHandle;
import oracle.kv.impl.security.login.LoginManager;
import oracle.kv.impl.security.login.TrustedLogin;
import oracle.kv.impl.security.login.TrustedLoginAPI;
import oracle.kv.impl.security.login.UserLogin;
import oracle.kv.impl.security.login.UserLoginAPI;
import oracle.kv.impl.sna.StorageNodeAgentAPI;
import oracle.kv.impl.sna.StorageNodeAgentInterface;
import oracle.kv.impl.test.RemoteTestAPI;
import oracle.kv.impl.test.RemoteTestInterface;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.impl.topo.ArbNode;
import oracle.kv.impl.topo.ArbNodeId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.RepNode;
import oracle.kv.impl.topo.RepNodeId;
import oracle.kv.impl.topo.ResourceId;
import oracle.kv.impl.topo.ResourceId.ResourceType;
import oracle.kv.impl.topo.StorageNode;
import oracle.kv.impl.topo.StorageNodeId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.HostPort;
import oracle.kv.impl.util.client.ClientLoggerUtils;
import oracle.kv.impl.util.registry.RMISocketPolicy.SocketFactoryArgs;
import oracle.kv.impl.util.registry.ssl.SSLClientSocketFactory;

/**
 * RegistryUtils encapsulates the operations around registry lookups and
 * registry bindings. It's responsible for producing RMI binding names for the
 * various remote interfaces used by the store, and wrapping them in an
 * appropriate API class.
 * <p>
 * All KV store registry binding names are of the form:
 * <p>
 * { kvstore name } : { component full name } :
 *                         [ main | monitor | admin | login | trusted_login ]
 * <p>
 * This class is responsible for the construction of these names. Examples:
 * FooStore:sn1:monitor names the monitor interface of the SNA.
 * Foostore:rg5-rn12:admin names the admin interface of the RN rg5-rn2.
 *
 * In future we may make provisions for making a version be a part of the name,
 * e.g. FooStore:sn1:monitor:5, for version 5 of the SNA monitor interface.
 * <p>
 * Note that all exception handling is done by the invokers of the get/rebind
 * methods.
 * <p>
 * While server processes only access a single store, applications may access
 * multiple stores. To support applications with multiple stores, this class
 * uses storeToRegistryCSFMap to look up right registry for a given store. Look
 * up by client ID is best because client IDs are unique, and that is now the
 * intended way for registries to be found. Although we recommend that stores
 * have unique names, we do not enforce uniqueness for store names, so we can't
 * depend on them being unique.
 * <p>
 * But there are complications when testing because test environments often
 * include both client and server facilities in the same process. To handle
 * this situation in cases where tests use multiple stores, the tests were
 * written to depend on store-name-based lookups to access the proper
 * registries, and used unique store names for each store. To continue to
 * support these tests, registry lookup continues to support lookup by store
 * name. It also permits a single registry supplied for server use to be found
 * by clients, and a single client-supplied registry to be found be servers,
 * since that behavior was already present and depended upon by existing tests.
 * When tests are written that use multiple stores with the same name, they
 * need to isolate server components by store in their own processes, as seen
 * in MultiStoreTest.
 *
 * @see VersionedRemote
 */
public class RegistryUtils {

    /**
     * The exception message used in a ConnectIOException that is thrown when
     * there may be a security mismatch between the client and the server.
     */
    public static final String POSSIBLE_SECURITY_MISMATCH_MESSAGE =
        "Problem connecting to the storage node agent, which may be caused" +
        " by a security mismatch between the client and server, or by" +
        " another network connectivity issue";

    /**
     * The exception message used in a ConnectIOException that is thrown when
     * there may be an Async vs. RMI mismatch between the client and the
     * server.
     */
    public static final String POSSIBLE_ASYNC_MISMATCH_MESSAGE =
        "Problem connecting to the storage node agent, which may be caused" +
        " by a client using the async network protocol with a server that" +
        " does not support that protocol, or by another network" +
        " connectivity issue";

    /** Test hook called on registry lookup with the name of the entry */
    public static volatile TestHook<String> registryLookupHook;

    /**
     * The global client socket factory, used in server contexts where only a
     * single store is being accessed, or null if not yet set. Synchronize on
     * the class when accessing this field.
     */
    private static ClientSocketFactory globalRegistryCSF = null;

    /**
     * Map from store client ID or store name to registry ClientSocketFactory.
     * Synchronize on the class when accessing this field.
     */
    private static final Map<Object, ClientSocketFactory>
        storeToRegistryCSFMap = new HashMap<>();

    /**
     * A regular expression used to parse the port number from the string
     * representation of remote object, which may contain the endpoint info:
     * endpoint:[host:port<,RMIServerSocketFactory><,RMIClientSocketFactory>]
     * where the info enclosed with <> are optional
     */
    private static final Pattern PARSE_PORT_REGEX =
        Pattern.compile("endpoint:\\[.*:(\\d{1,5})[,\\]]");

    /**
     * A logger to use for debugging, even though we don't have access to the
     * real logger.
     */
    private static final Logger debugLogger =
        ClientLoggerUtils.getLogger(RegistryUtils.class, "debugging");

    /** Logging level for debug logging, to be adjusted during debugging. */
    private static final Level debugLoggerLevel = Level.FINEST;

    /** Cache of remote service objects. */
    private static final ServiceCache serviceCache = new ServiceCache();

    /* The topology that is the basis for generation of binding names. */
    private final Topology topology;

    /* The login manager to be used for initializing APIs */
    private final LoginManager loginMgr;

    /** The client ID of the associated client, or null. */
    private final ClientId clientId;

    /* Used to separate the different name components */
    private static final String BINDING_NAME_SEPARATOR = ":";

    /*
     * The different interface types associated with a remotely accessed
     * topology component. The component, depending upon its function, may only
     * implement a subset of the interfaces listed below.
     */
    public enum InterfaceType {

        MAIN,     /* The main interface. */
        MONITOR,  /* The monitoring interface. */
        ADMIN,    /* The administration interface. */
        TEST,     /* The test interface. */
        LOGIN,    /* The standard login interface */
        TRUSTED_LOGIN; /* The trusted login interface */

        public String interfaceName() {
            return name().toLowerCase();
        }
    }

    /**
     * Create an instance of this class for use in a server context.
     *
     * @param topology the topology used as the basis for operations
     * @param loginMgr a null-allowable LoginManager to be used for
     *  authenticating RMI access.  When accessing a non-secure instance
     *  or when executing only operations that do not require authentication,
     *  this may be null.
     */
    public RegistryUtils(Topology topology, LoginManager loginMgr) {
        this(topology, loginMgr, null /* clientId */);
    }

    /**
     * Create an instance of this class, passing a client ID if needed for a
     * client context.
     *
     * @param topology the topology used as the basis for operations
     * @param loginMgr a null-allowable LoginManager to be used for
     *  authenticating RMI access.  When accessing a non-secure instance
     *  or when executing only operations that do not require authentication,
     *  this may be null.
     * @param clientId the resource ID of the client, may be null in a server
     * context
     */
    public RegistryUtils(Topology topology,
                         LoginManager loginMgr,
                         ClientId clientId) {
        this.topology = topology;
        this.loginMgr = loginMgr;
        this.clientId = clientId;
    }

    /** Returns the topology supplied to the constructor. */
    public Topology getTopology() {
        return topology;
    }

    /** Returns the login manager supplied to the constructor. */
    public LoginManager getLoginManager() {
        return loginMgr;
    }

    /** Returns the client ID supplied to the constructor. */
    public ClientId getClientId() {
        return clientId;
    }

    /**
     * Returns the API wrapper for the RMI stub associated with request handler
     * for the RN identified by <code>repNodeId</code>. If the RN is not
     * present in the topology <code>null</code> is returned.
     */
    public RequestHandlerAPI getRequestHandler(RepNodeId repNodeId)
        throws RemoteException, NotBoundException {

        final RepNode repNode = topology.get(repNodeId);

        return (repNode == null) ?
                null :
                RequestHandlerAPI.wrap
                       ((RequestHandler) lookup(repNodeId.getFullName(),
                                                InterfaceType.MAIN,
                                                repNode.getStorageNodeId()));
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the
     * administration of the RN identified by <code>repNodeId</code>.
     */
    public RepNodeAdminAPI getRepNodeAdmin(RepNodeId repNodeId)
        throws RemoteException, NotBoundException {

        final RepNode repNode = topology.get(repNodeId);

        return RepNodeAdminAPI.wrap
            ((RepNodeAdmin) lookup(repNodeId.getFullName(),
                                   InterfaceType.ADMIN,
                                   repNode.getStorageNodeId()),
             getLogin(repNodeId));
    }

    /**
     * A RepNodeAdmin interface will look like this:
     * storename:rg1-rn1:ADMIN
     */
    public static boolean isRepNodeAdmin(String serviceName) {
        if (serviceName.toLowerCase().indexOf("rg") > 0) {
            return (serviceName.endsWith
                    (InterfaceType.ADMIN.toString()));
        }
        return false;
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the
     * login of the RN identified by <code>repNodeId</code>.
     */
    public UserLoginAPI getRepNodeLogin(RepNodeId repNodeId)
        throws RemoteException, NotBoundException {

        final RepNode repNode = topology.get(repNodeId);

        final LoginHandle loginHdl = getLogin(repNodeId);

        return UserLoginAPI.wrap
            ((UserLogin) lookup(repNodeId.getFullName(),
                                InterfaceType.LOGIN,
                                repNode.getStorageNodeId()),
             loginHdl);
    }

    /**
     * Check whether the service name appears to be a valid RepNode UserLogin
     * interface for some kvstore.
     * @return the store name from the RN login entry if this appears
     * to be a valid rep node login and null otherwise.
     */
    public static String isRepNodeLogin(String serviceName) {
        /*
         * A RepNode UserLogin interface will look like this:
         * storename:rg1-rn1:LOGIN
         */
        final int firstSep = serviceName.indexOf(BINDING_NAME_SEPARATOR);
        if (firstSep < 0) {
            return null;
        }
        if (serviceName.toLowerCase().indexOf(
                RepGroupId.getPrefix(), firstSep) > 0 &&
                serviceName.endsWith(
                    BINDING_NAME_SEPARATOR + InterfaceType.LOGIN.toString())) {
            return serviceName.substring(0, firstSep);
        }
        return null;
    }

    /**
     * Check whether the service name appears to be a valid RepNode UserLogin
     * interface for the named kvstore.
     */
    public static boolean isRepNodeLogin(String serviceName, String storeName) {
        return storeName.equals(isRepNodeLogin(serviceName));
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the test
     * interface of the RN identified by <code>repNodeId</code>.
     */
    public RemoteTestAPI getRepNodeTest(RepNodeId repNodeId)
        throws RemoteException, NotBoundException {

        final RepNode repNode = topology.get(repNodeId);

        return RemoteTestAPI.wrap
            ((RemoteTestInterface) lookup(repNodeId.getFullName(),
                                          InterfaceType.TEST,
                                          repNode.getStorageNodeId()));
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the monitor for
     * the service identified by <code>resourceId</code>. This should only be
     * called for components that support monitoring. An attempt to obtain an
     * agent for a non-monitorable component will result in an {@link
     * IllegalStateException}.
     */
    public static MonitorAgentAPI getMonitor(String storeName,
                                             String snHostname,
                                             int snRegistryPort,
                                             ResourceId resourceId,
                                             LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return MonitorAgentAPI.wrap
            ((MonitorAgent) getInterface(
                storeName, snHostname, snRegistryPort,
                resourceId.getFullName(), InterfaceType.MONITOR,
                /* MonitorAgentAPI used in server context only */
                null /* clientId */),
             getLogin(loginMgr, snHostname, snRegistryPort,
                      resourceId.getType()));
    }

    /**
     * Looks up the Admin (CommandService) in the registry without benefit of
     * the Topology. Used to bootstrap storage node registration.
     * @throws RemoteException
     * @throws NotBoundException
     */
    public static CommandServiceAPI getAdmin(String hostname,
                                             int registryPort,
                                             LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return CommandServiceAPI.wrap
            ((CommandService)
             registryLookup(hostname, registryPort, null /* storeName */,
                            GlobalParams.COMMAND_SERVICE_NAME,
                            null /* clientId */),
             getLogin(loginMgr, hostname, registryPort, ResourceType.ADMIN));
    }

    /**
     * Look up an Admin (CommandService) when a Topology is available.
     * @throws RemoteException
     * @throws NotBoundException
     */
    public CommandServiceAPI getAdmin(StorageNodeId snid)
        throws RemoteException, NotBoundException {

        return CommandServiceAPI.wrap
            ((CommandService)
             registryLookup(snid, GlobalParams.COMMAND_SERVICE_NAME),
             getLogin(snid));
    }

    public RemoteTestAPI getAdminTest(StorageNodeId snid)
        throws RemoteException, NotBoundException {

        return RemoteTestAPI.wrap
            ((RemoteTestInterface)
             registryLookup(snid, GlobalParams.COMMAND_SERVICE_TEST_NAME));
    }

    public static RemoteTestAPI getAdminTest(String hostname,
                                             int registryPort)
        throws RemoteException, NotBoundException {

        return RemoteTestAPI.wrap
            ((RemoteTestInterface)
             registryLookup(hostname, registryPort, null /* storeName */,
                            GlobalParams.COMMAND_SERVICE_TEST_NAME,
                            null /* clientId */));
    }

    public UserLoginAPI getAdminLogin(StorageNodeId snid)
        throws RemoteException, NotBoundException {

        return UserLoginAPI.wrap
            ((UserLogin)
             registryLookup(snid, GlobalParams.ADMIN_LOGIN_SERVICE_NAME),
             getLogin(snid));
    }

    public static UserLoginAPI getAdminLogin(String hostname,
                                             int registryPort,
                                             LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return UserLoginAPI.wrap
            ((UserLogin)
             registryLookup(hostname, registryPort, null /* storeName */,
                            GlobalParams.ADMIN_LOGIN_SERVICE_NAME,
                            null /* clientId */),
             getLogin(loginMgr, hostname, registryPort, ResourceType.ADMIN));
    }

    /**
     * Look up an ClientAdminService on the master Admin without benefit of
     * the Topology. Used to execute DDL operations.
     * @throws RemoteException
     * @throws NotBoundException
     */
    public static ClientAdminServiceAPI getAdminDDL(String storeName,
                                                    String hostname,
                                                    int registryPort,
                                                    LoginManager loginMgr,
                                                    ClientId clientId)
        throws RemoteException, NotBoundException {

        return ClientAdminServiceAPI.wrap
            ((ClientAdminService)
             registryLookup(hostname, registryPort, storeName,
                            GlobalParams.CLIENT_ADMIN_SERVICE_NAME,
                            clientId),
             getLogin(loginMgr, hostname, registryPort, ResourceType.ADMIN));
    }

    /**
     * Look up an ClientAdminService on the master Admin when a Topology is
     * available. Used to execute DDL operations.
     * @throws RemoteException
     * @throws NotBoundException
     */
    public ClientAdminServiceAPI getAdminDDL(StorageNodeId snid)
        throws RemoteException, NotBoundException {

        return ClientAdminServiceAPI.wrap(
            (ClientAdminService) registryLookup(
                snid, GlobalParams.CLIENT_ADMIN_SERVICE_NAME),
             getLogin(snid));
    }

    /**
     * Returns an API wrapper for the RMI handle to the RepNodeAdmin.
     */
    public static RepNodeAdminAPI getRepNodeAdmin(String storeName,
                                                  String snHostname,
                                                  int snRegistryPort,
                                                  RepNodeId rnId,
                                                  LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return getRepNodeAdmin(storeName, snHostname, snRegistryPort,
                               rnId, loginMgr, null /* clientId */);
    }

    /**
     * Returns an API wrapper for the RMI handle to the RepNodeAdmin,
     * specifying an optional client ID.
     */
    public static RepNodeAdminAPI getRepNodeAdmin(String storeName,
                                                  String snHostname,
                                                  int snRegistryPort,
                                                  RepNodeId rnId,
                                                  LoginManager loginMgr,
                                                  ClientId clientId)
        throws RemoteException, NotBoundException {

        return RepNodeAdminAPI.wrap
                ((RepNodeAdmin) getInterface(storeName,
                                             snHostname,
                                             snRegistryPort,
                                             rnId.getFullName(),
                                             InterfaceType.ADMIN,
                                             clientId),
                 getLogin(loginMgr, snHostname, snRegistryPort,
                          ResourceType.REP_NODE));
    }

    /**
     * Returns an API wrapper for the RMI handle to the ArbNodeAdmin.
     */
    public static ArbNodeAdminAPI getArbNodeAdmin(String storeName,
                                                  String snHostname,
                                                  int snRegistryPort,
                                                  ArbNodeId arbId,
                                                  LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return ArbNodeAdminAPI.wrap(
            (ArbNodeAdmin) getInterface(
                storeName,
                snHostname,
                snRegistryPort,
                arbId.getFullName(),
                InterfaceType.ADMIN,
                /* ArbNodeAdminAPI used in server context only */
                null /* clientId */),
            getLogin(loginMgr, snHostname, snRegistryPort,
                     ResourceType.ARB_NODE));
    }

    /**
     * Returns an API wrapper for the RMI handle to the RepNode UserLogin.
     */
    public static UserLoginAPI getRepNodeLogin(String storeName,
                                               String snHostname,
                                               int snRegistryPort,
                                               RepNodeId rnId,
                                               LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return getRepNodeLogin(storeName, snHostname, snRegistryPort,
                               rnId.getFullName(), loginMgr);
    }

    /**
     * Returns an API wrapper for the RMI handle to the RepNode UserLogin.
     */
    public static UserLoginAPI getRepNodeLogin(String storeName,
                                               String snHostname,
                                               int snRegistryPort,
                                               String rnFullName,
                                               LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return UserLoginAPI.wrap
                ((UserLogin) getInterface(storeName,
                                          snHostname,
                                          snRegistryPort,
                                          rnFullName,
                                          InterfaceType.LOGIN,
                                          /* Server side only */
                                          null /* clientId */),
                 getLogin(loginMgr, snHostname, snRegistryPort,
                          ResourceType.REP_NODE));
    }

    /**
     * If a startup problem is found for this resource, throw an exception with
     * the information, because that's the primary problem.
     */
    public static void checkForStartupProblem(String storeName,
                                              String snHostname,
                                              int snRegistryPort,
                                              ResourceId rId,
                                              StorageNodeId snId,
                                              LoginManager loginMgr) {

        StringBuilder startupProblem = null;

        try {
            final StorageNodeAgentAPI sna = getStorageNodeAgent(storeName,
                                                                snHostname,
                                                                snRegistryPort,
                                                                snId,
                                                                loginMgr);
            startupProblem =  sna.getStartupBuffer(rId);
        } catch (Exception secondaryProblem) {

            /*
             * We couldn't get to the SNA to check. Eat this secondary problem
             * so the first problem isn't hidden.
             */
        }

        if (startupProblem != null) {
            throw new OperationFaultException("Problem starting process for " +
                                              rId + ":" +
                                              startupProblem.toString());
        }
    }

    private static Remote getInterface(String storeName,
                                       String snHostname,
                                       int snRegistryPort,
                                       String rnName,
                                       InterfaceType interfaceType,
                                       ClientId clientId)
        throws RemoteException, NotBoundException {

        return registryLookup(snHostname, snRegistryPort, storeName,
                              bindingName(storeName, rnName, interfaceType),
                              clientId);
    }

    /**
     * Looks up a StorageNode in the registry without benefit of the
     * Topology. Used to bootstrap storage node registration.
     * @throws RemoteException
     * @throws NotBoundException
     */
    public static StorageNodeAgentAPI getStorageNodeAgent(String hostname,
                                                          int registryPort,
                                                          String serviceName,
                                                          LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        final StorageNodeAgentInterface snai =
            (StorageNodeAgentInterface) registryLookup(
                hostname, registryPort, null /* storeName */,
                serviceName, null /* clientId */);

        final LoginHandle loginHdl = getLogin(loginMgr, hostname, registryPort,
                                              ResourceType.STORAGE_NODE);

        return StorageNodeAgentAPI.wrap(snai, loginHdl);
    }

    public StorageNodeAgentAPI getStorageNodeAgent(StorageNodeId storageNodeId)
        throws RemoteException, NotBoundException {

        final StorageNodeAgentInterface snai =
            (StorageNodeAgentInterface) lookup(storageNodeId.getFullName(),
                                               InterfaceType.MAIN,
                                               storageNodeId);

        final LoginHandle loginHdl = getLogin(storageNodeId);

        return StorageNodeAgentAPI.wrap(snai, loginHdl);
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the
     * administration of the ARB identified by <code>arbNodeId</code>.
     */
    public ArbNodeAdminAPI getArbNodeAdmin(ArbNodeId arbNodeId)
        throws RemoteException, NotBoundException {

        final ArbNode arbNode = topology.get(arbNodeId);

        return ArbNodeAdminAPI.wrap
            ((ArbNodeAdmin) lookup(arbNodeId.getFullName(),
                                   InterfaceType.ADMIN,
                                   arbNode.getStorageNodeId()),
             getLogin(arbNodeId));
    }

    /**
     * Returns the API wrapper for the RMI stub associated with the test
     * interface of the AN identified by <code>arbNodeId</code>.
     */
    public RemoteTestAPI getArbNodeTest(ArbNodeId arbNodeId)
        throws RemoteException, NotBoundException {

        final ArbNode arbNode = topology.get(arbNodeId);

        return RemoteTestAPI.wrap
            ((RemoteTestInterface) lookup(arbNodeId.getFullName(),
                                          InterfaceType.TEST,
                                          arbNode.getStorageNodeId()));
    }

    public static RemoteTestAPI getStorageNodeAgentTest
        (String storeName, String hostname,
         int registryPort, StorageNodeId snid)
        throws RemoteException, NotBoundException {

        final String serviceName = bindingName(storeName,
                                               snid.getFullName(),
                                               InterfaceType.TEST);
        return RemoteTestAPI.wrap(
            (RemoteTestInterface) registryLookup(hostname, registryPort,
                                                 storeName, serviceName,
                                                 null /* clientId */));
    }

    public RemoteTestAPI getStorageNodeAgentTest(StorageNodeId storageNodeId)
        throws RemoteException, NotBoundException {

        return RemoteTestAPI.wrap
            ((RemoteTestInterface) lookup(storageNodeId.getFullName(),
                                          InterfaceType.TEST,
                                          storageNodeId));
    }

    /**
     * Get the SNA trusted login
     */
    public static TrustedLoginAPI getStorageNodeAgentLogin(String hostname,
                                                           int registryPort)
        throws RemoteException, NotBoundException {

        return TrustedLoginAPI.wrap(
            (TrustedLogin) registryLookup(
                hostname, registryPort, null /* storeName */,
                GlobalParams.SNA_LOGIN_SERVICE_NAME,
                null /* clientId */));
    }

    /**
     * Checks whether the service name appears to be a valid StorageNodeAgent
     * TrustedLogin interface.
     */
    public static boolean isStorageNodeAgentLogin(String serviceName) {
        /*
         * A StorageAgentNode TrustedLogin interface will look like this:
         * SNA:TRUSTED_LOGIN
         */
        return serviceName.equals(GlobalParams.SNA_LOGIN_SERVICE_NAME);
    }

    /**
     * Get the non-bootstrap SNA interface without using Topology.
     */
    public static StorageNodeAgentAPI getStorageNodeAgent(String storeName,
                                                          String snHostname,
                                                          int snRegistryPort,
                                                          StorageNodeId snId,
                                                          LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        final StorageNodeAgentInterface snai =
            (StorageNodeAgentInterface) getInterface(
                storeName,
                snHostname,
                snRegistryPort,
                snId.getFullName(),
                InterfaceType.MAIN,
                /* StorageNodeAgentAPI used in server context only */
                null /* clientId */);
        final LoginHandle loginHdl =
            getLogin(loginMgr, snHostname, snRegistryPort,
                     ResourceType.STORAGE_NODE);

        return StorageNodeAgentAPI.wrap(snai, loginHdl);
    }

    /**
     * Get the non-bootstrap SNA interface without using Topology.
     */
    public static StorageNodeAgentAPI getStorageNodeAgent(String storename,
                                                          StorageNode sn,
                                                          LoginManager loginMgr)
        throws RemoteException,
        NotBoundException {

        return getStorageNodeAgent(storename,
                                   sn.getHostname(),
                                   sn.getRegistryPort(),
                                   sn.getResourceId(),
                                   loginMgr);
    }

    /**
     * Get the non-bootstrap SNA interface using the given Parameters object.
     */
    public static StorageNodeAgentAPI getStorageNodeAgent(Parameters parameters,
                                                          StorageNodeId snId,
                                                          LoginManager loginMgr)
        throws RemoteException, NotBoundException {
        final String storename = parameters.getGlobalParams().getKVStoreName();
        return getStorageNodeAgent(storename, parameters.get(snId),
                                   snId, loginMgr);
    }

    public static StorageNodeAgentAPI getStorageNodeAgent(String storename,
                                                          StorageNodeParams snp,
                                                          StorageNodeId snId,
                                                          LoginManager loginMgr)
        throws RemoteException, NotBoundException {

        return getStorageNodeAgent(storename, snp.getHostname(),
                                   snp.getRegistryPort(), snId, loginMgr);
    }

    /**
     * Rebinds the RMI stub associated with request handler for the RN
     * identified by <code>repNodeId</code>.
     */
    public void rebind(RepNodeId repNodeId, RequestHandler requestHandler)
        throws RemoteException {

        final RepNode repNode = topology.get(repNodeId);
        rebind(repNodeId.getFullName(), InterfaceType.MAIN,
               repNode.getStorageNodeId(), requestHandler);
    }

    /**
     * Rebinds the RMI stub associated with monitor agent for the RN identified
     * by <code>repNodeId</code>.
     */
    public void rebind(RepNodeId repNodeId, MonitorAgent monitorAgent)
        throws RemoteException {

        final RepNode repNode = topology.get(repNodeId);
        rebind(repNodeId.getFullName(), InterfaceType.MONITOR,
               repNode.getStorageNodeId(), monitorAgent);
    }

    /**
     * Rebinds the RMI stub associated with administration of the RN
     * identified by <code>repNodeId</code>.
     */
    public void rebind(RepNodeId repNodeId, RepNodeAdmin repNodeAdmin)
        throws RemoteException {

        final RepNode repNode = topology.get(repNodeId);
        rebind(repNodeId.getFullName(), InterfaceType.ADMIN,
               repNode.getStorageNodeId(), repNodeAdmin);
    }

    /**
     * Rebinds the object in the registry after assembling the binding name
     * from its base name and suffix if any.
     *
     * @param baseName the base name
     * @param interfaceType the suffix if any to be appended to the baseName
     * @param storageNodeId the storage node used to locate the registry
     * @param object the remote object to be bound
     *
     * @throws RemoteException
     */
    private void rebind(String baseName,
                        InterfaceType interfaceType,
                        StorageNodeId storageNodeId,
                        Remote object)
        throws RemoteException {

        exportAndRebind(bindingName(baseName, interfaceType), object,
                        getRegistry(storageNodeId), null, null);
    }

    /**
     * Rebinds the object in the registry after assembling the binding name.
     * This form of rebind is used when a Topology object is not yet available.
     *
     * @param hostname the hostname associated with the registry
     * @param registryPort the registry port
     * @param storeName the name of the KV store
     * @param baseName the base name
     * @param interfaceType the suffix if any to be appended to the baseName
     * @param object the remote object to be bound
     * @param clientSocketFactory the client socket factory; a null value
     *                             results in use of RMI default timeout values
     * @param serverSocketFactory the server socket factory; a null value
     *                             results in use of RMI default bind values
     *
     * @throws RemoteException
     */
    public static void rebind(String hostname,
                              int registryPort,
                              String storeName,
                              String baseName,
                              InterfaceType interfaceType,
                              Remote object,
                              ClientSocketFactory clientSocketFactory,
                              ServerSocketFactory serverSocketFactory)
        throws RemoteException {

        rebind(hostname, registryPort,
               bindingName(storeName, baseName, interfaceType), object,
               clientSocketFactory,
               serverSocketFactory);
    }

    /**
     * Overloading only for rebinds where the default socket factories are
     * adequate. This may be in unit tests, or with KVS services that don't
     * need fine control over server connection backlogs or client timeouts.
     */
    public static void rebind(String hostname,
                              int registryPort,
                              String storeName,
                              String baseName,
                              InterfaceType interfaceType,
                              Remote object)
        throws RemoteException {

        rebind(hostname, registryPort,
               storeName, baseName, interfaceType, object,
               null, null); /* Use default socket factories. */
    }

    /**
     * Rebinds the object in the registry using the specified service name.
     * This form of rebind is used by the StorageNodeAgent before it is
     * registered with a Topology.
     *
     * @param hostname the hostname associated with the registry
     * @param registryPort the registry port
     * @param serviceName
     * @param object the remote object to be bound
     *
     * @throws RemoteException
     */
    public static void rebind(String hostname,
                              int registryPort,
                              String serviceName,
                              Remote object,
                              ClientSocketFactory clientSocketFactory,
                              ServerSocketFactory serverSocketFactory)
        throws RemoteException {

        try {
            exportAndRebind(serviceName, object,
                            getRegistry(hostname, registryPort),
                            clientSocketFactory, serverSocketFactory);
        } catch (RemoteException re) {
            throw new RemoteException("Can't rebind " + serviceName + " at " +
                                      hostname + ":" + registryPort +
                                      " csf: " + clientSocketFactory +
                                      " ssf: " + serverSocketFactory, re);
        }
    }

    /**
     * Unbinds the object in the registry after assembling the binding name. It
     * also unexports the object after letting all active operations finish.
     *
     * @param hostname the hostname associated with the registry
     * @param registryPort the registry port
     * @param storeName the name of the KV store
     * @param baseName the base name
     * @param interfaceType the suffix if any to be appended to the baseName
     * @param object the object being unbound
     *
     * @return true if the registry had a binding and it was unbound,false if
     * the binding was not present in the registry
     *
     * @throws RemoteException
     */
    public static boolean unbind(String hostname,
                                 int registryPort,
                                 String storeName,
                                 String baseName,
                                 InterfaceType interfaceType,
                                 Remote object)
        throws RemoteException {

        return unbind(hostname,
                      registryPort,
                      bindingName(storeName, baseName, interfaceType),
                      object);
    }

    /**
     * Static unbind method for non-topology object, returning whether the
     * binding was found.
     */
    public static boolean unbind(String hostname,
                                 int registryPort,
                                 String serviceName,
                                 Remote object)
        throws RemoteException {

        final Registry registry = getRegistry(hostname, registryPort);

        boolean result = true;
        try {
            registry.unbind(serviceName);
        } catch (NotBoundException e) {
            result = false;
        }

        /*
         * Even if the object was not in the registry, make sure it is
         * unexported. Unexport is needed in cases where an attempt to create a
         * new service failed, and the failure cleanup caused the existing
         * service to be unbound in the registry, even though it is still
         * exported. It's not clear if this situation can occur in production
         * or only happens in tests, but seems safest to account for it, and
         * the unexport should be harmless.
         */
        try {
            UnicastRemoteObject.unexportObject(object, true);
        } catch (NoSuchObjectException e) {
            /* The object must not have been exported after all */
        }

        return result;
    }

    /**
     * Returns the binding name used in the registry.
     */
    public static String bindingName(String storeName,
                                     String baseName,
                                     InterfaceType interfaceType) {
        return storeName + BINDING_NAME_SEPARATOR +
            baseName  + BINDING_NAME_SEPARATOR + interfaceType;
    }

    /**
     * Initialize socket policies with default client socket factory timeout
     * settings for client utility use (e.g. admin client, ping, etc.).
     *
     * Whether to use SSL is determined from system properties.
     * TBD: the reference to system property usage could be invalidated by
     * pending proposed change to the security configuration API.
     */
    public static void initRegistryCSF() {
        initRegistryCSF(KVStoreConfig.DEFAULT_REGISTRY_OPEN_TIMEOUT,
                        KVStoreConfig.DEFAULT_REGISTRY_READ_TIMEOUT);
    }

    /**
     * Initialize socket policies with given client socket factory timeout
     * settings for client utility use (e.g. admin client, ping, etc.).
     *
     * Whether to use SSL is determined from system properties.
     * TBD: the reference to system property usage could be invalidated by
     * pending proposed change to the security configuration API.
     *
     * @param openTimeoutMs client socket factory open timeout
     * @param readTimeoutMs client socket factory read timeout
     */
    public static void initRegistryCSF(int openTimeoutMs, int readTimeoutMs) {
        final RMISocketPolicy rmiPolicy =
            ClientSocketFactory.ensureRMISocketPolicy();
        final String registryCsfName =
            ClientSocketFactory.registryFactoryName();

        final SocketFactoryArgs args = new SocketFactoryArgs().
            setCsfName(registryCsfName).
            setCsfConnectTimeout(openTimeoutMs).
            setCsfReadTimeout(readTimeoutMs);

        setRegistryCSF(rmiPolicy.getRegistryCSF(args), null, null);
    }

    /**
     * Set socket timeouts for ClientSocketFactory.
     * Only for KVStore client usage.
     * Whether to use SSL is determined from system properties, if not yet
     * configured.
     */
    public static void setRegistrySocketTimeouts(
        int connectMs, int readMs, String storeName, ClientId clientId) {

        final RMISocketPolicy rmiPolicy =
            ClientSocketFactory.ensureRMISocketPolicy();
        final String registryCsfName =
            ClientSocketFactory.registryFactoryName();
        final SocketFactoryArgs args = new SocketFactoryArgs().
            setKvStoreName(storeName).
            setCsfName(registryCsfName).
            setCsfConnectTimeout(connectMs).
            setCsfReadTimeout(readMs).
            setClientId(clientId);

        setRegistryCSF(rmiPolicy.getRegistryCSF(args), storeName, clientId);
    }

    /**
     * Set the supplied CSF for use during registry lookups within the
     * KVStore server.
     */
    public static void setServerRegistryCSF(
        ClientSocketFactory clientSocketFactory) {

        setRegistryCSF(clientSocketFactory, null, null);
    }

    /**
     * Sets the client socket factory to use for registry lookups for the
     * specified store name or client ID, if non-null, and also the global
     * client socket factory.
     */
    public static synchronized void setRegistryCSF(ClientSocketFactory csf,
                                                   String storeName,
                                                   ClientId clientId) {
        checkNull("csf", csf);
        globalRegistryCSF = csf;
        if (storeName != null) {
            storeToRegistryCSFMap.put(storeName, csf);
        }
        if (clientId != null) {
            storeToRegistryCSFMap.put(clientId, csf);
        }
        debugLogger.log(debugLoggerLevel,
                        () -> String.format("RegistryUtils.setRegistryCSF" +
                                            " storeName:%s clientId:%s csf:%s",
                                            storeName, clientId, csf));
    }

    /**
     * Reset all client socket factory settings. For use by tests.
     */
    public static synchronized void clearRegistryCSF() {
        globalRegistryCSF = null;
        SSLClientSocketFactory.clearUserSSLControlMap();
        storeToRegistryCSFMap.clear();
    }

    /**
     * Reset the client socket factory settings for the specified client ID
     * when a KVStoreImpl is closed.
     *
     * @param clientId the client ID of the KVStoreImpl
     */
    public static synchronized void clearRegistryCSF(ClientId clientId) {

        checkNull("clientId", clientId);
        storeToRegistryCSFMap.remove(clientId);
    }

    /**
     * Reset registry client socket factory after resetting the RMI socket
     * policy.  These resets will allow future connections to take advantage of
     * updated truststore certificates, if any.
     */
    public static synchronized void resetRegistryCSF(String storeName,
                                                     ClientId clientId) {
        checkNull("storeName", storeName);
        final RMISocketPolicy rmiPolicy =
            ClientSocketFactory.resetRMISocketPolicy(storeName, clientId);
        ClientSocketFactory csf =
            (clientId != null) ? storeToRegistryCSFMap.get(clientId) : null;
        if (csf == null) {
            csf = storeToRegistryCSFMap.get(storeName);
        }
        if (debugLogger.isLoggable(debugLoggerLevel)) {
            debugLogger.log(debugLoggerLevel,
                            String.format("RegistryUtils.resetRegistryCSF" +
                                          " storeName:%s clientId:%s csf:%s",
                                          storeName, clientId, csf));
        }
        if (csf == null) {
            return;
        }

        /*
         * Using the same socket factory argument to reset client socket
         * factory
         */
        final SocketFactoryArgs args = new SocketFactoryArgs().
            setKvStoreName(storeName).
            setCsfName(csf.name).
            setCsfConnectTimeout(csf.getConnectTimeoutMs()).
            setCsfReadTimeout(csf.getReadTimeoutMs()).
            setClientId(csf.getClientId());
        setRegistryCSF(rmiPolicy.getRegistryCSF(args), storeName, clientId);
    }

    /**
     * Common function to get a Registry reference using a pre-configured CSF
     * with an optional storeName or client ID as a CSF lookup key, for cases
     * where multiple stores are accessed concurrently.
     */
    public static Registry getRegistry(String hostname,
                                       int port,
                                       String storeName,
                                       ClientId clientId)
        throws RemoteException {

        return new ExceptionWrappingRegistry(
            hostname, port, getRegistryCSF(storeName, clientId));
    }

    /**
     * Returns the client socket factory for the specified store name or client
     * ID, or the global client socket factory if the parameters are null or
     * the factory is not found.
     */
    static synchronized ClientSocketFactory getRegistryCSF(String storeName,
                                                           ClientId clientId)
    {
        ClientSocketFactory csf =
            (clientId != null) ? storeToRegistryCSFMap.get(clientId) : null;
        if ((csf == null) && (storeName != null)) {
            csf = storeToRegistryCSFMap.get(storeName);
        }
        if (csf == null) {
            csf = globalRegistryCSF;
        }
        if (debugLogger.isLoggable(debugLoggerLevel)) {
            debugLogger.log(debugLoggerLevel,
                            String.format("RegistryUtils.getRegistryCSF" +
                                          " storeName:%s clientId:%s csf:%s",
                                          storeName, clientId, csf));
        }
        return csf;
    }

    /**
     * Convenience version of the method above, where the storeName is not
     * specified.  This is suitable for all calls within a KVStore
     * component and for calls by applications that have no ability to access
     * multiple stores concurrently.
     */
    public static Registry getRegistry(String hostname, int port)
        throws RemoteException {

        return getRegistry(hostname, port, null, null);
    }

    public static int findFreePort(int start, int end, String hostname) {
        ServerSocket serverSocket = null;
        for (int current = start; current <= end; current++) {
            try {
                /*
                 * Try a couple different methods to be sure that the port is
                 * truly available.
                 */
                serverSocket = new ServerSocket(current);
                serverSocket.close();

                /**
                 * Now using the hostname.
                 */
                serverSocket = new ServerSocket();
                final InetSocketAddress sa =
                    new InetSocketAddress(hostname, current);
                serverSocket.bind(sa);
                serverSocket.close();
                return current;
            } catch (IOException e) /* CHECKSTYLE:OFF */ {
                /* Try the next port number. */
            } /* CHECKSTYLE:ON */
        }
        return 0;
    }

    /**
     * Get the service port on which the specified remote object is exported.
     * This method attempts to use a known format specified in PARSE_PORT_REGEX
     * to parse port number from the string that represents the remote object.
     * If the attempt fails, return 0.
     *
     * @param remote remote object
     * @return a positive port number on which the specified remote object is
     * exported, or 0 if the specified remote object is null or port number
     * cannot be retrieved
     */
    public static int getServicePort(Remote remote) {
        if (remote == null) {
            return 0;
        }

        String str = remote.toString();
        if (remote instanceof RemoteObject) {
            final RemoteRef remoteRef = ((RemoteObject) remote).getRef();
            if (remoteRef != null) {
                str = remoteRef.remoteToString();
            }
        }

        if (str != null) {
            final Matcher m = PARSE_PORT_REGEX.matcher(str);
            if (m.find()) {
                final String port = m.group(1);
                try {
                    return Integer.parseInt(port);
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Log service name and port associated with the specified remote object.
     * This method attempts to use a known format to parse port number from the
     * string that represents the remote object.  If the attempt fails, log the
     * string as it may contain useful info to figure out the port number.
     *
     * @param name the name to which the specified remote object is bound
     * @param remote remote object
     * @param logger logger
     */
    public static void logServiceNameAndPort(String name,
                                             Remote remote,
                                             Logger logger) {
        if (logger == null || !logger.isLoggable(Level.INFO) ||
            remote == null || name == null) {
            return;
        }

        final int port = getServicePort(remote);
        if (port > 0) {
            logger.info(name + " service port: " + port);
        } else {
            String str = remote.toString();
            if (remote instanceof RemoteObject) {
                final RemoteRef remoteRef = ((RemoteObject) remote).getRef();
                if (remoteRef != null) {
                    str = remoteRef.remoteToString();
                }
            }
            logger.info("Remote object: " + str + " is bound to " + name);
        }
    }

    /* Internal Utility methods. */

    /**
     * Export the specified object and bind the resulting stub to the name in
     * the registry.  This method takes special steps to leave out any
     * specified client socket factories when exporting the object so that
     * objects with unequal client socket factories can still be exported on
     * the same port.  The client socket factories are then added back when
     * binding the client-side stub in the registry.
     *
     * @param name the name to bind
     * @param object the object to export
     * @param registry the registry
     * @param csf the client socket factory or null
     * @param ssf the server socket factory or null
     */
    private static void exportAndRebind(String name,
                                        Remote object,
                                        Registry registry,
                                        ClientSocketFactory csf,
                                        ServerSocketFactory ssf)
        throws RemoteException {

        int port = 0;
        ServerSocket ss = null;
        if (ssf != null) {
            try {
                /*
                 * Allow the ssf to determine in advance what server socket
                 * should be used.  This allows us to specify the port number
                 * when exporting the object, which is necessary for RMI to
                 * close the server socket when all objects exported on this
                 * port are unexported.
                 */
                ss = ssf.preallocateServerSocket();
                if (ss != null) {
                    port = ss.getLocalPort();
                }
            } catch (IOException ioe) {
                throw new ExportException(
                    "Unable to create ServerSocket for export", ioe);
            }
        }

        final Remote remote;
        try {

            /*
             * Export the object using a ReplaceableRMIClientSocketFactory so
             * that the actual client socket factory does not prevent port
             * sharing.
             */
            remote = UnicastRemoteObject.exportObject(
                object, port, ReplaceableRMIClientSocketFactory.INSTANCE, ssf);

            /*
             * The prepared server socket, if any, should have been consumed,
             * so null out ss to suppress the call to discardServerSocket
             * below.
             */
            ss = null;
        } finally {
            /* If the export did not succeed, we may need to clean up */
            if (ss != null && ssf != null) {
                ssf.discardServerSocket(ss);
            }
        }

        /*
         * Specify a replacement client socket factory when binding the stub in
         * the registry.  The call will serialize the stub, which will cause
         * the desired replacement client socket factory to be substituted for
         * the temporary one.
         */
        try {
            if (csf != null) {
                ReplaceableRMIClientSocketFactory.setReplacement(csf);
            }
            registry.rebind(name, remote);
        } catch (RemoteException re) {
            UnicastRemoteObject.unexportObject(object, true);
            throw re;
        } finally {
            if (csf != null) {
                ReplaceableRMIClientSocketFactory.setReplacement(null);
            }
        }

        if (ssf != null) {
            logServiceNameAndPort(name, remote, ssf.getConnectionLogger());
        }
    }

    /**
     * Returns the binding name used in the registry.
     */
    private String bindingName(String baseName, InterfaceType interfaceType) {
        return bindingName(topology.getKVStoreName(), baseName, interfaceType);
    }

    /**
     * Returns the registry associated with the storage node.
     */
    public Registry getRegistry(StorageNodeId storageNodeId)
        throws RemoteException {

        final StorageNode storageNode = topology.get(storageNodeId);

        return getRegistry(storageNode.getHostname(),
                           storageNode.getRegistryPort(),
                           topology.getKVStoreName(),
                           clientId);
    }

    /**
     * Looks up an object in the registry after assembling the binding name
     * from its base name and suffix if any.
     *
     * @param baseName the base name
     * @param interfaceType the suffix if any to be appended to the baseName
     * @param storageNodeId the storage node used to locate the registry
     *
     * @return the stub associated with the remote object
     *
     * @throws RemoteException
     * @throws NotBoundException
     */
    private Remote lookup(String baseName,
                          InterfaceType interfaceType,
                          StorageNodeId storageNodeId)
        throws RemoteException, NotBoundException {

        final String bindingName = bindingName(baseName, interfaceType);
        debugLogger.log(debugLoggerLevel,
                        () -> String.format("RegistryUtils.lookup" +
                                            " bindingName=%s clientId=%s",
                                            bindingName, clientId));
        return registryLookup(storageNodeId, bindingName);
    }

    /**
     * Look up an entry in the RMI registry associated with a storage node ID.
     */
    private Remote registryLookup(StorageNodeId storageNodeId,
                                  String bindingName)
        throws RemoteException, NotBoundException {

        final StorageNode storageNode = topology.get(storageNodeId);

        return registryLookup(storageNode.getHostname(),
                              storageNode.getRegistryPort(),
                              topology.getKVStoreName(),
                              bindingName, clientId);
    }

    /** A key associated with a service in the service cache. */
    private static class ServiceKey {
        final String hostname;
        final int port;
        final String storeName;
        final String bindingName;
        final ClientId clientId;
        ServiceKey(String hostname,
                   int port,
                   String storeName,
                   String bindingName,
                   ClientId clientId) {
            this.hostname = hostname;
            this.port = port;
            this.storeName = storeName;
            this.bindingName = bindingName;
            this.clientId = clientId;
        }
        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof ServiceKey)) {
                return false;
            }
            final ServiceKey otherKey = (ServiceKey) other;
            return Objects.equals(hostname, otherKey.hostname) &&
                port == otherKey.port &&
                Objects.equals(storeName, otherKey.storeName) &&
                Objects.equals(bindingName, otherKey.bindingName) &&
                Objects.equals(clientId, otherKey.clientId);
        }
        @Override
        public int hashCode() {
            int result = 79;
            result = (result * 31) + Objects.hashCode(hostname);
            result = (result * 31) + port;
            result = (result * 31) + Objects.hashCode(storeName);
            result = (result * 31) + Objects.hashCode(bindingName);
            result = (result * 31) + Objects.hashCode(clientId);
            return result;
        }
        @Override
        public String toString() {
            return "ServiceKey[" +
                "hostname=" + hostname +
                " port=" + port +
                " storeName=" + storeName +
                " bindingName=" + bindingName +
                " clientId=" + clientId +
                "]";
        }
    }

    /**
     * A cache for remote services. The cache is needed to avoid concurrent
     * creation of RMI remote stub instances, which can result in duplicate RMI
     * RenewClean threads, presumably due to a concurrency bug in the RMI DGC
     * code. [KVSTORE-493]
     *
     * Before returning a cached entry, the cache calls isValid, which probes
     * the remote object by calling getSerialVersion. If the probe fails, which
     * it should if the old object has been unexported, the entry is removed
     * from the cache before a new entry is fetched. Code, including tests,
     * that binds a new object in the registry should make sure to unexport the
     * old one so that the cache will know to remove its stale entry.
     */
    private static class ServiceCache {

        /**
         * Map from key to a future that holds or will hold the associated
         * value. Synchronize on the cache when accessing this field.
         */
        private final Map<ServiceKey, CompletableFuture<VersionedRemote>> map
            = new HashMap<>();

        /**
         * Gets a future that returns the remote RMI stub for the service
         * associated with the specified parameters.
         */
        CompletableFuture<VersionedRemote> get(String hostname,
                                               int port,
                                               String storeName,
                                               String bindingName,
                                               ClientId clientId) {
            final ServiceKey key = new ServiceKey(hostname, port, storeName,
                                                  bindingName, clientId);
            CompletableFuture<VersionedRemote> future;
            synchronized (this) {
                future = map.get(key);
                if (future == null) {

                    /* Add a future for a new try */
                    future = new CompletableFuture<>();
                    map.put(key, future);
                } else if (!future.isDone()) {

                    /*
                     * This value is being refreshed, which makes sure it is
                     * valid or else arranges to throw an exception.
                     */
                    return future;
                } else if (future.isCompletedExceptionally()) {

                    /* The previous refresh failed -- try again */
                    future = new CompletableFuture<>();
                    map.put(key, future);
                }
            }

            /*
             * We already checked for an exceptional result, so this call
             * should not throw
             */
            VersionedRemote value = future.getNow(null);

            if (value != null) {

                /* Return if the value is valid */
                if (isValid(value)) {
                    return future;
                }

                synchronized (this) {
                    final CompletableFuture<VersionedRemote> currentFuture =
                        map.get(key);

                    /*
                     * If a new future has been created since the one we
                     * checked, it is a newly refreshed future, so return it
                     */
                    if (currentFuture != future) {
                        return currentFuture;
                    }

                    future = new CompletableFuture<>();
                    map.put(key, future);
                }
            }
            refresh(key, future);
            return future;
        }

        /**
         * Check to be sure that the cached remote RMI stub is still valid. We
         * are depending on services that export RMI servers to unexport them
         * when binding a new object in the registry. We can make the trivial
         * getSerialVersion remote call to the remote object to confirm that it
         * has not be unexported.
         */
        private boolean isValid(VersionedRemote remote) {
            try {
                remote.getSerialVersion();
                return true;
            } catch (RemoteException e) {
                return false;
            }
        }

        /**
         * Obtain a new value for the associated key, updating the future
         * either with the new value or with an exception that represents a
         * failure to obtain the value.
         */
        private void refresh(ServiceKey key,
                             CompletableFuture<VersionedRemote> future) {

            /*
             * TODO: Log entries for refreshes at INFO to help debug any
             * problems. Use the logger provided when KVSTORE-531 is fixed.
             */
            try {
                final VersionedRemote remote = (VersionedRemote)
                    registryLookupInternal(key.hostname, key.port,
                                           key.storeName, key.bindingName,
                                           key.clientId);
                future.complete(remote);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }
    }

    /**
     * Look up an entry in the RMI registry with the specified client ID bound
     * during the lookup.
     */
    private static Remote registryLookup(String hostname,
                                         int port,
                                         String storeName,
                                         String bindingName,
                                         ClientId clientId)
        throws RemoteException, NotBoundException {

        final CompletableFuture<VersionedRemote> future =
            serviceCache.get(hostname, port, storeName, bindingName, clientId);
        final ClientSocketFactory csf = getRegistryCSF(storeName, clientId);
        final long timeoutMs = (csf != null) ?
            csf.getReadTimeoutMs() :
            DEFAULT_REGISTRY_READ_TIMEOUT;
        try {
            return future.get(timeoutMs, MILLISECONDS);
        } catch (CancellationException e) {
            /*
             * TODO: This would happen if we canceled pending requests on
             * shutdown
             */
            throw new RemoteException("Canceled by shutdown", e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            if (cause instanceof NotBoundException) {
                throw (NotBoundException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unexpected exception: " + e, e);
        } catch (InterruptedException e) {
            /*
             * This happens if a request is interrupted during a shutdown
             */
            throw new RemoteException("Interrupted", e);
        } catch (TimeoutException e) {
            throw new RemoteException("Request timed out", e);
        }
    }

    /**
     * Do a registry lookup after no cached value was found.
     */
    private static Remote registryLookupInternal(String hostname,
                                                 int port,
                                                 String storeName,
                                                 String bindingName,
                                                 ClientId clientId)
        throws RemoteException, NotBoundException {

        final Registry registry =
            getRegistry(hostname, port, storeName, clientId);

        /*
         * Bind the client ID while performing the registry lookup, which makes
         * the client ID available while deserializing the underlying client
         * socket factory
         */
        ClientSocketFactory.setCurrentClientId(clientId);
        try {
            return registry.lookup(bindingName);
        } finally {
            ClientSocketFactory.setCurrentClientId(null);
        }
    }

    private LoginHandle getLogin(ResourceId resourceId) {

        if (loginMgr == null) {
            return null;
        }

        /*
         * Because not all LoginManagers can resolve topology, attempt to
         * resolve it here if possible.
         */
        if (topology != null) {
            final Topology.Component<?> comp = topology.get(resourceId);
            if (comp != null) {
                final StorageNodeId snId = comp.getStorageNodeId();
                final StorageNode sn = topology.get(snId);
                if (sn != null) {
                    return loginMgr.getHandle(
                        new HostPort(sn.getHostname(),
                                     sn.getRegistryPort()),
                        resourceId.getType());
                }
            }
        }

        return loginMgr.getHandle(resourceId);
    }

    private static LoginHandle getLogin(LoginManager loginMgr,
                                        String hostname,
                                        int registryPort,
                                        ResourceType rtype) {

        if (loginMgr == null) {
            return null;
        }

        return loginMgr.getHandle(new HostPort(hostname, registryPort),
                                  rtype);
    }

    /**
     * A decorator class for Registry. When messages of some exception in
     * Registry need to be improved to show a clearer instruction, this class
     * gives a chance to do that.
     * <p>
     * Currently we wrap the {@link ConnectIOException} to tell users to check
     * the security configurations on client and server ends when this
     * exception is seen.
     */
    private static class ExceptionWrappingRegistry implements Registry {
        private final String hostname;
        private final int registryPort;
        private final Registry registry;

        private ExceptionWrappingRegistry(String hostname,
                                          int registryPort,
                                          ClientSocketFactory csf)
            throws RemoteException {

            this.hostname = hostname;
            this.registryPort = registryPort;
            registry = LocateRegistry.getRegistry(hostname, registryPort, csf);
        }

        @Override
        public Remote lookup(String name)
            throws RemoteException,
                   NotBoundException,
                   AccessException,
                   AuthenticationFailureException {

            assert TestHookExecute.doHookIfSet(registryLookupHook, name);

            try {
                return registry.lookup(name);
            } catch (RemoteException re) {
                rethrow(re);
            }
            /* Unreachable code */
            return null;
        }

        @Override
        public void bind(String name, Remote obj)
            throws RemoteException,
                   AlreadyBoundException,
                   AccessException,
                   AuthenticationFailureException {
            try {
                registry.bind(name, obj);
            } catch (RemoteException re) {
                rethrow(re);
            }
        }

        @Override
        public void unbind(String name)
            throws RemoteException,
                   NotBoundException,
                   AccessException,
                   AuthenticationFailureException {
            try {
                registry.unbind(name);
            } catch (RemoteException re) {
                rethrow(re);
            }
        }

        @Override
        public void rebind(final String name, final Remote obj)
            throws RemoteException,
                   AccessException,
                   AuthenticationFailureException {
            try {
                registry.rebind(name, obj);
            } catch (RemoteException re) {
                rethrow(re);
            }
        }

        @Override
        public String[] list()
            throws RemoteException,
                   AccessException,
                   AuthenticationFailureException{

            try {
                return registry.list();
            } catch (RemoteException re) {
                rethrow(re);
            }
            /* Unreachable code */
            return null;
        }

        /*
         * Note: Keep this method in sync with
         * AsyncRegistryUtils.TranslateExceptions.onResult and
         * AsyncRequestHandler.handleDialogException
         */
        private void rethrow(RemoteException re)
            throws RemoteException, AuthenticationFailureException {

            /* Wraps the CIOE to give a clearer message to users */
            if (re instanceof ConnectIOException) {

                /*
                 * If CIOE caused by the SSL handshake error, wraps as an
                 * AuthenticationFailureException
                 */
                final Throwable cause = re.getCause();
                if (cause instanceof SSLHandshakeException) {
                    throw new AuthenticationFailureException(cause);
                }
                throw new ConnectIOException(
                    POSSIBLE_SECURITY_MISMATCH_MESSAGE, re);
            }
            if (re instanceof ConnectException) {
                throw new ConnectException(
                    "Unable to connect to the storage node agent at" +
                    " host " + hostname + ", port " + registryPort +
                    ", which may not be running",
                    re);
            }
            throw re;
        }
    }

    /**
     * Define an RMI client socket factory that will replace itself with
     * another client factory during serialization, if one is specified, or
     * else with null, to use the default.  All instances of this class are
     * equal to each other.  Use this factory when exporting an RMI server so
     * that different client socket factories can be used on the client side
     * without requiring the use of a different server socket for each export.
     *
     * <p>If you want to use a non-standard client socket factory, call the
     * setReplacement method around an operation that serializes the
     * client-side stub, which will cause the instance of this class to be
     * replaced by the desired client-side client socket factory.  [#24708]
     */
    public static class ReplaceableRMIClientSocketFactory
            implements Serializable, RMIClientSocketFactory {

        /** Use this default instance since all instances are equal. */
        public static final ReplaceableRMIClientSocketFactory INSTANCE =
            new ReplaceableRMIClientSocketFactory();

        /**
         * Use this ClientSocketFactory instance as the replacement if none is
         * specified. Because of a regression in Java 1.8.0_241, 11.0.6, and
         * 13.0.2, specifying a null replacement is producing a
         * NullPointerException in those Java versions. See:
         * https://bugs.openjdk.java.net/browse/JDK-8237368
         */
        private static final ClientSocketFactory NULL_REPLACEMENT =
            new ClientSocketFactory("nullReplacement", 0, 0, null);

        private static final long serialVersionUID = 1;

        /**
         * Stores the replacement client socket factory for the current thread.
         */
        private static final ThreadLocal<RMIClientSocketFactory>
            replacement = new ThreadLocal<RMIClientSocketFactory>();

        @Override
        public boolean equals(Object object) {
            return object instanceof ReplaceableRMIClientSocketFactory;
        }

        @Override
        public int hashCode() {
            return 424242;
        }

        /**
         * Call the default RMI socket factory if this instance has not been
         * replaced.
         */
        @Override
        public Socket createSocket(String host, int port)
            throws IOException {

            final RMIClientSocketFactory defaultFactory =
                RMISocketFactory.getDefaultSocketFactory();
            return defaultFactory.createSocket(host, port);
        }

        /**
         * Specify the client socket factory that should replace this instance
         * during serialization.  Make sure to clear the setting to null after
         * serialization is complete, to avoid holding a reference to the
         * replacement.
         */
        static void setReplacement(RMIClientSocketFactory csf) {
            replacement.set(csf);
        }

        /**
         * Replace this instance with the replacement, if not null.  Otherwise,
         * replace this instance with null, which will in turn be replaced by
         * the default RMI socket factory when it is deserialized.
         */
        private Object writeReplace() {
            final RMIClientSocketFactory csf = replacement.get();
            if (csf != null) {
                return csf;
            }
            return NULL_REPLACEMENT;
        }

        /**
         * Instances of this class should never be deserialized because they
         * are always replaced during serialization.
         */
        @SuppressWarnings("unused")
        private void readObject(ObjectInputStream in) {
            throw new AssertionError();
        }
    }
}
