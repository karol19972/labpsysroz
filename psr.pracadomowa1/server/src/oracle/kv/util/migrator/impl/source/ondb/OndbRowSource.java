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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import oracle.kv.impl.api.table.DDLGenerator;
import oracle.kv.impl.api.table.RegionMapper;
import oracle.kv.impl.api.table.TableAPIImpl;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableIterator;
import oracle.kv.util.migrator.impl.data.StringArrayEntry;
import oracle.kv.util.migrator.impl.data.ondb.OndbDataEntry;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory.RowSourceManager;
import oracle.kv.util.migrator.impl.source.ondb.OndbSourceFactory.SourceManager;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.data.DataEntry;
import oracle.nosql.common.migrator.data.Entry;

/**
 * This class encapsulates 2 sources that supplies DataEntry for table data
 * and StringArrayEntry for table schema.
 */
public abstract class OndbRowSource<T> extends OndbSource<T> {

    public OndbRowSource(SourceManager factory,
                          String name,
                          Logger logger) {
        super(factory, name, logger);
    }

    static DataSource getDataSource(SourceManager manager,
                                    String name,
                                    Logger logger) {
        return new TableDataSource(manager, name, logger);
    }

    static DataSource getSchemaSource(SourceManager manager,
                                      String name,
                                      Collection<Table> tables,
                                      Logger logger) {
        return new TableSchemaSource(manager, name, tables, logger);
    }

    /**
     * The source that supplies the data entries of a table.
     */
    private static class TableDataSource extends OndbRowSource<Row> {

        public TableDataSource(SourceManager manager,
                               String tableFullNamespaceName,
                               Logger logger) {
            super(manager, tableFullNamespaceName, logger);
        }

        @Override
        Iterator<Row> createIterator() {
            RowSourceManager mgr = (RowSourceManager)manager;
            return mgr.createDataIterator(getTargetTable());
        }

        @Override
        protected DataEntry createEntry(Row row) {
            return new OndbDataEntry(row);
        }

        @Override
        public void close() {
            if (iterator instanceof TableIterator) {
                ((TableIterator<?>)iterator).close();
            }
            super.close();
        }
    }

    /**
     * The source that supplies the entries of table schema.
     */
    private static class TableSchemaSource extends OndbRowSource<Table> {
        private final static String FILE_SUFFIX = ".ddl";
        private final Collection<Table> tables;

        private TableSchemaSource(SourceManager manager,
                                  String name,
                                  Collection<Table> tables,
                                  Logger logger) {
            super(manager, name, logger);
            this.tables = tables;
        }

        @Override
        public String getTargetTable() {
            return getName() + FILE_SUFFIX;
        }

        @Override
        Iterator<Table> createIterator() {
            return tables.iterator();
        }

        @Override
        Entry createEntry(Table table) {
            return new TableSchemaEntry(table, manager);
        }

        @Override
        public boolean isSchemaData() {
            return true;
        }
    }

    /**
     * The Table schema entry
     */
    private static class TableSchemaEntry extends StringArrayEntry {

        TableSchemaEntry(Table table, SourceManager manager) {
            super(genDdls(table, manager));
        }

        private static String[] genDdls(Table table, SourceManager manager) {
            final RegionMapper regionMapper =
                    ((TableAPIImpl)manager.store.getTableAPI()).getRegionMapper();
            final DDLGenerator gen = new DDLGenerator(table,
                                                      true /* withIfNotExist */,
                                                      regionMapper);
            List<String> ddls = new ArrayList<>();
            ddls.add(gen.getDDL());
            if (!table.getIndexes().isEmpty()) {
                ddls.addAll(gen.getAllIndexDDL());
            }
            return ddls.toArray(new String[ddls.size()]);
        }
    }
}
