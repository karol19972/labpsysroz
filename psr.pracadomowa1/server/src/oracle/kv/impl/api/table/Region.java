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

package oracle.kv.impl.api.table;

import static oracle.kv.impl.util.SerializationUtil.readNonNullString;
import static oracle.kv.impl.util.SerializationUtil.readPackedInt;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullString;
import static oracle.kv.impl.util.SerializationUtil.writePackedInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import oracle.kv.impl.util.FastExternalizable;
import oracle.kv.impl.util.SerializationUtil;   /* for Javadoc */

/**
 * A region.
 */
public class Region implements FastExternalizable, Serializable {

    private static final long serialVersionUID = 1L;

    /* Local region ID cannot change */
    public static final int LOCAL_REGION_ID = 1;
    public static final int REGION_ID_START = 2;
    static final int REGION_ID_MAX = 1000000;
    public static final int UNKNOWN_REGION_ID = -1;
    /** Reserved region ID for non-multi-region entries */
    public static final int NULL_REGION_ID = 0;

    /**
     * Region name.
     */
    private final String name;

    /**
     * Region ID.
     */
    private final int id;

    Region(String name, int id) {
        checkId(id);
        if (name == null) {
            throw new IllegalArgumentException("Region name cannot be null");
        }
        this.name = name;
        this.id = id;
    }

    /* Constructor for FastExternalizable */
    public Region(DataInput in, short serialVersion) throws IOException {
        name = readNonNullString(in, serialVersion);
        id = readPackedInt(in);
    }

    /**
     * Writes this object to the output stream. Format:
     *
     * <ol>
     * <li> ({@link SerializationUtil#writeNonNullString
     *      non-null String}) {@code name}
     * <li> ({@link SerializationUtil#writePackedInt int}) <i>region ID</i>
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {
        writeNonNullString(out, serialVersion, name);
        writePackedInt(out, id);
    }

    /**
     * Gets the region name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the region ID.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns true if this region object represents the local region.
     */
    public boolean isLocal() {
        return id == LOCAL_REGION_ID;
    }

    /**
     * Throws IllegalArgumentException if the specified ID is not a valid
     * region ID.
     */
    public static void checkId(int id) {
        if ((id < LOCAL_REGION_ID) || (id > REGION_ID_MAX)) {
            throw new IllegalArgumentException("Invalid region ID: " + id);
        }
    }

    /**
     * Throws IllegalArgumentException if the specified ID is not a valid
     * remote region ID.
     */
    public static void checkRemoteId(int id) {
        if ((id < REGION_ID_START) || (id > REGION_ID_MAX)) {
            throw new IllegalArgumentException("Invalid region ID: " + id);
        }
    }

    @Override
    public String toString() {
        return "Region[" + name + " " + id + "]";
    }
}
