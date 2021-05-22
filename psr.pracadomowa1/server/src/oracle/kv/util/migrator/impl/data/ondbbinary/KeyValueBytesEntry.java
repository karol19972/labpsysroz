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

package oracle.kv.util.migrator.impl.data.ondbbinary;

import oracle.kv.util.expimp.utils.DataSerializer.KeyValueBytes;
import oracle.nosql.common.migrator.data.GenericEntry;

/**
 * The entry that wraps KeyValueBytes value, it is the entry supplied by
 * OndbBinaryFileSource.
 */
public class KeyValueBytesEntry implements GenericEntry<KeyValueBytes> {

    private KeyValueBytes kvBytes;

    public KeyValueBytesEntry(KeyValueBytes kvBytes) {
        this.kvBytes = kvBytes;
    }

    @Override
    public KeyValueBytes getData() {
        return kvBytes;
    }

    @Override
    public String toString() {
        return kvBytes.toString();
    }

    public void setKeyValueBytes(KeyValueBytes kvBytes) {
        this.kvBytes = kvBytes;
    }
}

