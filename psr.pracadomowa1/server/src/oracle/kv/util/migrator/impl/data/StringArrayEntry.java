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

package oracle.kv.util.migrator.impl.data;

import oracle.nosql.common.migrator.data.GenericEntry;

/**
 * An entry that holds a string array.
 */
public class StringArrayEntry implements GenericEntry<String[]>{

    private String[] values;

    public StringArrayEntry(String[] values) {
        this.values = values;
    }

    @Override
    public String[] getData() {
        return values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(value);
        }
        return sb.toString();
    }
}
