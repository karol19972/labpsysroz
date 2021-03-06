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
package oracle.kv.impl.security;

import static oracle.kv.impl.security.KVStorePrivilege.PrivilegeType.NAMESPACE;
import static oracle.kv.impl.security.KVStorePrivilege.PrivilegeType.SYSTEM;
import static oracle.kv.impl.security.KVStorePrivilege.PrivilegeType.TABLE;

import oracle.kv.impl.security.KVStorePrivilege.PrivilegeType;

/**
 * A set of labels denoting system-defined privileges within the KVStore
 * security system. The privilege labels are mainly used in annotations of RMI
 * to denote the required privileges.
 */
public enum KVStorePrivilegeLabel {

    /*
     * Privileges for data access
     */

    /**
     * Get/iterate keys and values in entire store including any tables
     */
    READ_ANY() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Put/delete values in entire store including any tables
     */
    WRITE_ANY() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for administrative tasks
     */

    /**
     * Privilege to perform ONDB database management
     */
    SYSDBA() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege to view/show system information, configuration and metadata
     */
    SYSVIEW() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege to query data object information
     */
    DBVIEW() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege to query users' own information
     */
    USRVIEW() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege to perform ONDB administrative tasks
     */
    SYSOPER() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege for KVStore component to access internal services and internal
     * keyspace.
     */
    INTLOPER() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for reading Avro schemas.
     */
    READ_ANY_SCHEMA() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for adding, removing and updating Avro schemas
     */
    WRITE_ANY_SCHEMA() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for creating any table in kvstore
     */
    CREATE_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for dropping any table in kvstore
     */
    DROP_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for evolving any table in kvstore
     */
    EVOLVE_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for creating index on any table in kvstore
     */
    CREATE_ANY_INDEX() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for dropping index on any table in kvstore
     */
    DROP_ANY_INDEX() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for reading data in any table in kvstore
     */
    READ_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for deleting data in any table in kvstore
     */
    DELETE_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for inserting data in any table in kvstore
     */
    INSERT_ANY_TABLE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privilege for getting/iterating key-values from a specific table
     */
    READ_TABLE() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privilege for deleting key-values in a specific tables
     */
    DELETE_TABLE() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privilege for putting key-values in a specific tables
     */
    INSERT_TABLE() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privilege for evolving a specific tables
     */
    EVOLVE_TABLE() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privilege for creating index on a specific tables
     */
    CREATE_INDEX() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privilege for dropping index on a specific tables
     */
    DROP_INDEX() {

        @Override
        public PrivilegeType getType() {
            return TABLE;
        }
    },

    /**
     * Privileges for creating a new namespace in kvstore.
     */
    CREATE_ANY_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for dropping any namespace in kvstore.
     */
    DROP_ANY_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for creating tables inside a specific namespace.
     */
    CREATE_TABLE_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privileges for dropping tables inside a specific namespace.
     */
    DROP_TABLE_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privileges for evolving tables inside a specific namespace.
     */
    EVOLVE_TABLE_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privileges for creating indexes inside a specific namespace.
     */
    CREATE_INDEX_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privileges for dropping indexes inside a specific namespace.
     */
    DROP_INDEX_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Helper label only for CREATE_TABLE_IN_NAMESPACE, DROP_TABLE_IN_NAMESPACE,
     * EVOLVE_TABLE_IN_NAMESPACE, CREATE_INDEX_IN_NAMESPACE and
     * DROP_INDEX_IN_NAMESPACE privileges.
     */
    MODIFY_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privilege for getting/iterating items within tables from a specific
     * namespace.
     */
    READ_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privilege for creating items within tables in a specific namespace.
     */
    INSERT_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privilege for deleting items within tables in a specific namespace.
     */
    DELETE_IN_NAMESPACE() {

        @Override
        public PrivilegeType getType() {
            return NAMESPACE;
        }
    },

    /**
     * Privileges for creating a new region.
     */
    CREATE_ANY_REGION() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for dropping a region.
     */
    DROP_ANY_REGION() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * An additional privilege that is needed in order to write any system
     * table.
     */
    WRITE_SYSTEM_TABLE() {
        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    },

    /**
     * Privileges for setting the local region name.
     */
    SET_LOCAL_REGION() {

        @Override
        public PrivilegeType getType() {
            return SYSTEM;
        }
    };

    abstract public PrivilegeType getType();
}
