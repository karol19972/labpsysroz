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

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import oracle.kv.table.Table;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordBytes;
import oracle.kv.util.expimp.utils.exp.StoreExportHandler.RecordIterator;
import oracle.kv.util.migrator.impl.data.ondbbinary.RecordBytesEntry;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory.BinarySourceManager;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory.SourceManager;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.data.Entry;

/**
 * The OndbSource supplies binary record entry.
 */
public abstract class OndbBinarySource extends OndbSource<RecordBytes> {

    private boolean isDone;

    public OndbBinarySource(SourceManager manager,
                            String name,
                            Logger logger) {
        super(manager, name, logger);
        isDone = false;
    }

    static DataSource getDataSource(SourceManager manager,
                                    String name,
                                    Logger logger) {
        return new BinaryDataSource(manager, name, logger);
    }

    static DataSource getSchemaSource(SourceManager manager,
                                      String name,
                                      Collection<Table> tables,
                                      Logger logger) {
        return new BinarySchemaSource(manager, name, tables, logger);
    }

    @Override
    protected Entry nextEntry() {
        RecordBytes value;
        if (hasNextEntry()) {
            value = getNextValue();
            if (value != null) {
                return createEntry(value);
            }
        }
        isDone = true;
        return null;
    }

    boolean isDone() {
        return isDone;
    }

    private RecordBytes getNextValue() {
        RecordBytes value;
        while (hasNextEntry()) {
            value = iterator.next();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    protected Entry createEntry(RecordBytes value) {
        return new RecordBytesEntry(value);
    }

    @Override
    public void close() {
        if (iterator != null && (iterator instanceof RecordIterator)) {
            ((RecordIterator) iterator).close();
        }
        super.close();
    }

    private static class BinaryDataSource extends OndbBinarySource {
        public BinaryDataSource(SourceManager manager,
                                String tableFullNamespaceName,
                                Logger logger) {
            super(manager, tableFullNamespaceName, logger);
        }

        @Override
        Iterator<RecordBytes> createIterator() {
            BinarySourceManager bsManger = ((BinarySourceManager)manager);
            return bsManger.createDataIterator(getTargetTable());
        }
    }

    private static class BinarySchemaSource extends OndbBinarySource {
        private Collection<Table> tables;
        public BinarySchemaSource(SourceManager manager,
                                  String name,
                                  Collection<Table> tables,
                                  Logger logger) {
            super(manager, name,  logger);
            this.tables = tables;
        }

        @Override
        Iterator<RecordBytes> createIterator() {
            BinarySourceManager bsManger = ((BinarySourceManager)manager);
            return bsManger.createSchemaIterator(tables);
        }
    }
}
