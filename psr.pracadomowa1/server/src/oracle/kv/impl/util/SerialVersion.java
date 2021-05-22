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

package oracle.kv.impl.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import oracle.kv.KVVersion;

/**
 * Defines the previous and current serialization version for services and
 * clients.
 *
 * <p>As features that affect serialized formats are introduced constants
 * representing those features should be added here, associated with the
 * versions in which they are introduced. This creates a centralized location
 * for finding associations of features, serial versions, and release
 * versions. Existing constants (as of release 4.0) are spread throughout the
 * source and can be moved here as time permits.
 *
 * @see oracle.kv.impl.util.registry.VersionedRemote
 */
public class SerialVersion {

    public static final short UNKNOWN = -1;

    private static final Map<Short, KVVersion> kvVersions = new HashMap<>();

    private static final TreeMap<KVVersion, Short> versionMap = new TreeMap<>();

    /* Unsupported versions */

    /* R1 version */
    private static final short V1 = 1;
    static { init(V1, KVVersion.R1_2_123); }

    /* Introduced at R2 (2.0.23) */
    private static final short V2 = 2;
    static { init(V2, KVVersion.R2_0_23); }

    /* Introduced at R2.1 (2.1.8) */
    private static final short V3 = 3;
    static { init(V3, KVVersion.R2_1); }

    /*
     * Introduced at R3.0 (3.0.5)
     *  - secondary datacenters
     *  - table API
     */
    private static final short V4 = 4;
    static { init(V4, KVVersion.R3_0); }

    /* Introduced at R3.1 (3.1.0) for role-based authorization */
    private static final short V5 = 5;
    static { init(V5, KVVersion.R3_1); }

    /*
     * Introduced at R3.2 (3.2.0):
     * - real-time session update
     * - index key iteration
     */
    private static final short V6 = 6;
    static { init(V6, KVVersion.R3_2); }

    /*
     * Introduced at R3.3 (3.3.0) for secondary Admin type and JSON flag to
     * verifyConfiguration, and password expiration.
     */
    private static final short V7 = 7;
    static { init(V7, KVVersion.R3_2); }

    /*
     * Introduced at R3.4 (3.4.0) for the added replica threshold parameter on
     * plan methods, and the CommandService.getAdminStatus,
     * repairAdminQuorum, and createFailoverPlan methods.
     * Also added MetadataNotFoundException.
     *
     * Added bulk get APIs to Key/Value and Table interface.
     */
    private static final short V8 = 8;
    static { init(V8, KVVersion.R3_4); }

    /*
     * Introduced at R3.5 (3.5.0) for Admin automation V1 features, including
     * json format output, error code, and Kerberos authentication.
     *
     * Added bulk put APIs to Key/Value and Table interface.
     */
    private static final short V9 = 9;
    static { init(V9, KVVersion.R3_5); }

    /*
     * The first version that support admin CLI output in JSON string.
     */
    public static final short ADMIN_CLI_JSON_V1_VERSION = V9;

    /* Supported versions */

    /*
     * Introduced at R4.0/V10:
     * - new query protocol operations. These were added in V10, but because
     *   they were "preview" there is no attempt to handle V10 queries in
     *   releases > V10. Because the serialization format of queries has changed
     *   such operations will fail with an appropriate message.
     * - time to live
     * - Arbiters
     * - Full text search
     */
    private static final short V10 = 10;
    static { init(V10, KVVersion.R4_0); }

    /*
     * Introduced at R4.1/V11
     * - SN/topology contraction
     * - query protocol change (not compatible with V10)
     * - new SNA API for mount point sizes
     */
    private static final short V11 = 11;
    static { init(V11, KVVersion.R4_1); }

    /*
     * Introduced at R4.2/V12
     * - query protocol change (compatible with V11)
     * - getStorageNodeInfo added to SNA
     * - indicator bytes in indexes
     */
    private static final short V12 = 12;
    static { init(V12, KVVersion.R4_2); }

    /*
     * Introduced at R4.3/V13
     * - new SNI API for checking parameters
     */
    private static final short V13 = 13;
    static { init(V13, KVVersion.R4_3); }

    /*
     * Introduced at R4.4/V14
     * - Standard UTF-8 encoding
     * - typed indexes for JSON, affecting index plans
     * - SFWIter.theDoNullOnEmpty field
     * - Snapshot command is executed on the server side, using the admin to
     *   coordinate operations and locking.
     * - add TableQuery.mathContext field
     * - changed the value of the NULL indicator in index keys and
     *   added IndexImpl.serialVersion.
     */
    private static final short V14 = 14;
    static { init(V14, KVVersion.R4_4); }


    /* Introduced at R4.5/V15
     * - Switched query and DDL statements to char[] from String.
     * - Added currentIndexRange field in TableQuery op
     * - BaseTableIter may carry more than 1 keys and ranges
     * - Add TABLE_V1 to Value.Format
     * - added theIsUpdate field in BaseTableIter and ReceiveIter
     */
    private static final short V15 = 15;

    static { init(V15, KVVersion.R4_5); }

    /*
     * - Added members theIndexTupleRegs and theIndexResultReg in BaseTableIter
     * - Master affinity zone feature.
     * - Added failed shard removal.
     * - Added verify data feature.
     * - Added table limits
     * - Added LogContext string in Request.
     * - Check running subscription feeder before running elasticity operation
     * - Add maxReadKB to Table/Index iterate operation
     */
    public static final short V16 = 16;
    static { init(V16, KVVersion.R18_1); }

    public static final short QUERY_VERSION_6 = V16;

    /* Add metadataSeqNum to Result */
    public static final short RESULT_WITH_METADATA_SEQNUM = V16;

    /* Add Admin API to set table limits */
    public static final short TABLE_LIMITS_VERSION = V16;

    /* Added methods to support failed shard removal */
    public static final short TOPOLOGY_REMOVESHARD_VERSION = V16;

    /* Add APIs and additional result fields for resource tracking */
    public static final short RESOURCE_TRACKING_VERSION = V16;

    /* Master affinity zone feature */
    public static final short MASTER_AFFINITY_VERSION = V16;

    /*Add Admin API to verify data*/
    public static final short VERIFY_DATA_VERSION = V16;

    /* Support get table by id */
    public static final short GET_TABLE_BY_ID_VERSION = V16;

    /* Support all admin CLI for JSON output v2 */
    public static final short ADMIN_CLI_JSON_V2_VERSION = V16;

    /* Enable request type command */
    public static final short ENABLE_REQUEST_TYPE_VERSION = V16;

    /* Add emptyReadFactor to Table/Index iterate operation */
    public static final short EMPTY_READ_FACTOR_VERSION = V16;

    /* Pass a LogContext in the Request object. */
    public static final short LOGCONTEXT_REQUEST_VERSION = V16;

    /* Add maxReadKB to Table/Index iterate operation */
    public static final short MAXKB_ITERATE_VERSION = V16;

    /* Add tableId to execute operation */
    public static final short EXECUTE_OP_TABLE_ID = V16;

    /* Add maxWriteKB and resumeKey to MultiDeleteTable operation */
    public static final short MULTIDELTBL_WRITEKB_RESUMEKEY = V16;

    /*
     * Introduced at R18.2/V17
     * - Add getTable with optional cost
     */
    public static final short V17 = 17;
    static { init(V17, KVVersion.R18_2); }

    /* Support push charge for table get */
    public static final short PUSH_TABLE_COST = V17;

    /*
     * Introduced at R18.3/V17
     * - Enable extended table namespace support
     */
    public static final short V18 = 18;
    static { init(V18, KVVersion.R18_3); }

    public static final short QUERY_VERSION_7 = V18;

    /* Enable extended table namespace support */
    public static final short NAMESPACE_VERSION_2 = V18;

    /* Enable Identity on a field definition */
    public static final short IDENTITY_VERSION = V17;

    /* Add overwrite flag to PutBatch operation */
    public static final short PUT_BATCH_OVERWRITE = V18;

    /*
     * Introduced at R19.1/V19
     * - New RepNodeAdmin shutdown API
     */
    public static final short V19 = 19;
    static { init(V19, KVVersion.R19_1); }

    public static final short RN_SHUTDOWN_STREAM_VERSION = V19;

    /* Update the verify data API*/
    public static final short UPGRADE_VERIFY_DATA_VERSION = V19;

    /*
     * Introduced at R19.2/V20
     * - Added IndexImpl.skipNulls field
     */
    public static final short V20 = 20;
    static { init(V20, KVVersion.R19_2); }

    public static final short SKIP_NULLS_VERSION = V20;

    /*
     * Introduced at R19.3/V21
     * - Renamed IndexImpl.notIndexNulls to IndexImpl.skipNulls
     */
    public static final short V21 = 21;
    static { init(V21, KVVersion.R19_3); }

    public static final short QUERY_VERSION_8 = V21;

    /*
     * Introduced at R19.5/V22
     * - Changed the plan field of WriteNewAdminParams from
     *   ChangeAdminParamsPlan to AbstractPlan, making it more general
     * - Add support for multi-region tables
     * - Added stacktrace to async exceptions
     */
    public static final short V22 = 22;
    static { init(V22, KVVersion.R19_5); }

    public static final short
        GENERAL_WRITE_NEW_ADMIN_PARAMS_TASK_VERSION = V22;

    public static final short MULTI_REGION_TABLE_VERSION = V22;

    public static final short ASYNC_EXCEPTION_STACKTRACE_VERSION = V22;

    /*
     * Introduced at R20.1/V23
     * - Changed BaseTableIter
     * - Alter multi-region table
     * - Add new table metadata info keys
     */
    public static final short V23 = 23;
    static { init(V23, KVVersion.R20_1); }

    public static final short QUERY_VERSION_9 = V23;

    public static final short ROW_MODIFICATION_TIME_VERSION = V23;

    public static final short ALTER_MULTI_REGION_TABLE = V23;

    /*
     * Introduced at R20.2/V24
     * - PutResolve with expiration time
     */
    public static final short V24 = 24;
    static { init(V24, KVVersion.R20_2); }

    public static final short PUT_RESOLVE_EXPIRATION_TIME = V24;

    public static final short QUERY_VERSION_10 = V24;

    /*
     * Introduced at R20.3/V25
     * - PutResult and ValueVersionResult with row storage size
     * - Add isUUID to StringDefImpl
     */
    public static final short V25 = 25;
    static { init(V25, KVVersion.R20_3); }

    public static final short QUERY_VERSION_11 = V25;

    /* UUID data type */
    public static final short UUID_VERSION = V25;

    /*
     * Store seq # on a per table bases and changed semantics of the
     * sequence number returned in the Result object
     */
    public static final short TABLE_SEQ_NUM_VERSION = V25;

    /**
     * When adding a new version and updating DEFAULT_CURRENT, be sure to make
     * corresponding changes in KVVersion as well as the files referenced from
     * there to add a new release version. See {@link KVVersion#CURRENT_VERSION}
     */
    private static final short DEFAULT_CURRENT = V25;

    /*
     * The default earliest supported serial version.
     */
    private static final short DEFAULT_MINIMUM = V16;

    /*
     * Check that the default minimum version matches the KVVersion
     * prerequisite version, since that is the first supported KV version.
     */
    static {
        assert KVVersion.PREREQUISITE_VERSION == getKVVersion(DEFAULT_MINIMUM);
    }

    /*
     * The earliest supported serial version.  Clients and servers should both
     * reject connections from earlier versions.
     */
    public static final short MINIMUM = Integer.getInteger(
        "oracle.kv.minimum.serial.version", DEFAULT_MINIMUM).shortValue();

    static {
        if (MINIMUM != DEFAULT_MINIMUM) {
            System.err.println("Setting SerialVersion.MINIMUM=" + MINIMUM);
        }
    }

    /**
     * The current serial version, with a system property override for use in
     * testing.
     */
    public static final short CURRENT = Integer.getInteger(
        "oracle.kv.test.currentserialversion", DEFAULT_CURRENT).shortValue();

    static {
        if (CURRENT != DEFAULT_CURRENT) {
            System.err.println("Setting SerialVersion.CURRENT=" + CURRENT);
        }
    }

    private static void init(short version, KVVersion kvVersion) {
        kvVersions.put(version, kvVersion);
        if (version > MINIMUM) {
            versionMap.put(kvVersion, version);
        }
    }

    public static KVVersion getKVVersion(short serialVersion) {
        return kvVersions.get(serialVersion);
    }

    /**
     * Returns the maximum serial version supported by the specified KV version.
     * Returns MINIMUM if the KV version is less than the supported versions.
     */
    public static short getMaxSerialVersion(KVVersion kvVersion) {
        final Entry<KVVersion, Short> e = versionMap.floorEntry(kvVersion);
        return e == null ? MINIMUM : e.getValue();
    }

    /**
     * Creates an appropriate exception for a client that does not meet the
     * minimum required version.
     *
     * @param clientSerialVersion the serial version of the client
     * @param requiredSerialVersion the minimum required version
     * @return an appropriate exception
     */
    public static UnsupportedOperationException clientUnsupportedException(
        short clientSerialVersion, short requiredSerialVersion) {

        return new UnsupportedOperationException(
            "The client is incompatible with this service. " +
            "Client version is " +
            getKVVersion(clientSerialVersion).getNumericVersionString() +
            ", but the minimum required version is " +
            getKVVersion(requiredSerialVersion).getNumericVersionString());
    }

    /**
     * Creates an appropriate exception for a server that does not meet the
     * minimum required version.
     *
     * @param serverSerialVersion the serial version of the server
     * @param requiredSerialVersion the minimum required version
     * @return an appropriate exception
     */
    public static UnsupportedOperationException serverUnsupportedException(
        short serverSerialVersion, short requiredSerialVersion) {

        return new UnsupportedOperationException(
            "The server is incompatible with this client.  " +
            "Server version is " +
            getKVVersion(serverSerialVersion).getNumericVersionString() +
            ", but the minimum required version is " +
            getKVVersion(requiredSerialVersion).getNumericVersionString());
    }
}
