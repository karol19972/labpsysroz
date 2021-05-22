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

package oracle.kv.util.migrator.impl.source.ondb;

import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;

import java.util.Iterator;
import java.util.logging.Logger;

import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory.SourceManager;
import oracle.nosql.common.migrator.data.Entry;
import oracle.nosql.common.migrator.impl.source.DataSourceBaseImpl;

/**
 * The base class of NoSQL database source.
 */
public abstract class OndbSource<T> extends DataSourceBaseImpl {

    final SourceManager manager;
    Iterator<T> iterator;

    public OndbSource(SourceManager manager, String name, Logger logger) {
        super(ONDB_TYPE, name, null, logger);
        this.manager = manager;
    }

    abstract Iterator<T> createIterator();
    abstract Entry createEntry(T entry);

    @Override
    public String getTargetTable() {
        return getName();
    }

    @Override
    protected boolean hasNextEntry() {
        if (iterator == null) {
            iterator = createIterator();
        }
        return iterator != null && iterator.hasNext();
    }

    @Override
    protected Entry nextEntry() {
        if (!hasNextEntry()) {
            return null;
        }
        return createEntry(iterator.next());
    }

    @Override
    public void close() {
        manager.release(this);
    }
}
