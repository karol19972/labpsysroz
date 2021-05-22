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

package oracle.kv.impl.metadata;

import oracle.kv.impl.metadata.Metadata.MetadataType;
import oracle.kv.impl.util.SerialVersion;

/**
 * Marker interface for classes used as a key into metadata. Objects
 * implementing MetadataKey should also implement <code>Serializable</code>.
 */
public interface MetadataKey {

    /**
     * Gets the type of metadata this key is for.
     */
    MetadataType getType();

    /**
     * Gets the required serial version for the key. The default implementation
     * returns SerialVersion.MINIMUM.
     */
    default short getRequiredSerialVersion() {
        return SerialVersion.MINIMUM;
    }
}
