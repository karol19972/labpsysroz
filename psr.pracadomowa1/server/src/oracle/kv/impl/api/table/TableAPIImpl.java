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

import static java.util.Collections.singletonList;
import static oracle.kv.impl.async.FutureUtils.checked;
import static oracle.kv.impl.async.FutureUtils.failedFuture;
import static oracle.kv.impl.async.FutureUtils.thenApply;
import static oracle.kv.impl.util.ObjectUtil.checkNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import oracle.kv.BulkWriteOptions;
import oracle.kv.Consistency;
import oracle.kv.Direction;
import oracle.kv.Durability;
import oracle.kv.DurabilityException;
import oracle.kv.EntryStream;
import oracle.kv.FaultException;
import oracle.kv.Key;
import oracle.kv.KeyRange;
import oracle.kv.KeyValueVersion;
import oracle.kv.Operation;
import oracle.kv.OperationExecutionException;
import oracle.kv.OperationResult;
import oracle.kv.ReturnValueVersion;
import oracle.kv.Value;
import oracle.kv.ValueVersion;
import oracle.kv.Version;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.Request;
import oracle.kv.impl.api.bulk.BulkPut;
import oracle.kv.impl.api.ops.Execute.OperationFactoryImpl;
import oracle.kv.impl.api.ops.Execute.OperationImpl;
import oracle.kv.impl.api.ops.InternalOperation;
import oracle.kv.impl.api.ops.MultiDeleteTable;
import oracle.kv.impl.api.ops.MultiGetTable;
import oracle.kv.impl.api.ops.MultiGetTableKeys;
import oracle.kv.impl.api.ops.Put;
import oracle.kv.impl.api.ops.Result;
import oracle.kv.impl.api.ops.ResultKey;
import oracle.kv.impl.api.ops.ResultKeyValueVersion;
import oracle.kv.impl.api.table.TableMetadata.RegionMapperKey;
import oracle.kv.impl.api.table.TableMetadata.SysTableListKey;
import oracle.kv.impl.api.table.TableMetadata.TableList;
import oracle.kv.impl.api.table.TableMetadata.TableListKey;
import oracle.kv.impl.api.table.ValueSerializer.RowSerializer;
import oracle.kv.impl.async.AsyncIterationHandleImpl;
import oracle.kv.impl.async.AsyncPublisherImpl;
import oracle.kv.impl.async.AsyncTableIterator;
import oracle.kv.impl.client.admin.DdlFuture;
import oracle.kv.impl.metadata.MetadataInfo;
import oracle.kv.impl.metadata.MetadataKey;
import oracle.kv.impl.query.runtime.QueryKeyRange;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.table.FieldRange;
import oracle.kv.table.IndexKey;
import oracle.kv.table.KeyPair;
import oracle.kv.table.MultiGetResult;
import oracle.kv.table.MultiRowOptions;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.ReadOptions;
import oracle.kv.table.ReturnRow;
import oracle.kv.table.Row;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TableIterator;
import oracle.kv.table.TableIteratorOptions;
import oracle.kv.table.TableOpExecutionException;
import oracle.kv.table.TableOperation;
import oracle.kv.table.TableOperation.Type;
import oracle.kv.table.TableOperationFactory;
import oracle.kv.table.TableOperationResult;
import oracle.kv.table.TimeToLive;
import oracle.kv.table.WriteOptions;

import org.reactivestreams.Publisher;

/**
 * Implementation of the TableAPI interface.  It also manages materialization
 * of tables from metadata and caches retrieved tables.
 *
 * TableAPIImpl maintains a cache of TableImpl tables that have been explicitly
 * fetched by TableImpl because of schema evolution.  If TableImpl encounters
 * a table version higher than its own then it will fetch that version so it
 * can deserialize records written from a later version.  It is assumed that
 * this cache will be small and is not used for user calls to getTable().
 */
public class TableAPIImpl implements TableAPI {

    /* Default table cache size */
    private static final int TABLE_CACHE_CAPACITY = 20;
    /* Default lifetime in the cache */
    private static final long ENTRY_LIFETIME_MS = 30*1000;

    private final KVStoreImpl store;
    private final OpFactory opFactory;

    /*
     * Cache of table instances.
     */
    final private TableCache tableCache;

    /*
     * The cached TableMetadata seqNum.
     */
    private volatile int metadataSeqNum;

    /*
     * The callback handler that will be invoked if it is detected that table
     * metadata has been changed.
     */
    private TableMetadataCallback metadataCallback;

    /*
     * An optional TableMetadataHelper instance that allows a user to
     * implement a Table cache. This is used by the cloud proxy, which
     * has a Table cache, to avoid going to servers to fetch a Table
     * in order to prepare queries. It is not used in the normal
     * getTable() path, which is unchanged.
     */
    private TableMetadataHelper metadataHelper;

    /*
     * This must be public for KVStoreImpl to use it.
     */
    public TableAPIImpl(KVStoreImpl store, boolean enableCache) {
        this.store = store;
        opFactory = new OpFactory(store.getOperationFactory(),
                                  store);
        tableCache = new TableCache(TABLE_CACHE_CAPACITY, ENTRY_LIFETIME_MS);
        tableCache.setEnabled(enableCache);
        metadataSeqNum = 0;
    }

    /*
     * Table metadata methods
     */
    @Override
    public Table getTable(String fullNamespaceName)
        throws FaultException {
        return getTable(
            NameUtils.getNamespaceFromQualifiedName(fullNamespaceName),
            NameUtils.getFullNameFromQualifiedName(fullNamespaceName));
    }

    @Override
    public Table getTable(String namespace, String tableFullName)
        throws FaultException {
        return getTable(namespace, tableFullName, false);
    }

    public Table getTable(String namespace,
                          String tableFullName,
                          boolean bypassCache)
        throws FaultException {
        return getTable(namespace, TableImpl.parseFullName(tableFullName),
                        0, 0, bypassCache);
    }

    public Table getTable(String namespace, String tableFullName, int cost)
        throws FaultException {
        return getTable(namespace, TableImpl.parseFullName(tableFullName),
                        cost, 0, false);
    }

    /**
     * Gets the specified table. If tableVersion is 0, the latest table version
     * is returned or null is returned if the table is not found. Otherwise the
     * specified version is returned. If bypassCache is true the cache will not
     * be used to obtain the table. However, the cache may be updated.
     */
    private TableImpl getTable(String namespace,
                               String[] path,
                               int cost,
                               int tableVersion,
                               boolean bypassCache) {
        if (path == null || path.length == 0) {
            return null;
        }

        TableImpl table = bypassCache ? null :
                                 getChildVersion(tableCache.get(namespace, path),
                                                 path, tableVersion);
        if (table != null) {
            /*
             * Return the cached table if there is no cost or the table doesn't
             * have limits. If there is a cost and the table has limits, we
             * force a call to the store so that the cost can be accounted for.
             */
            if ((cost == 0) || !table.hasThroughputLimits()) {
                return table;
            }
        }

        /*
         * Either the table is not in the cache or it is not sufficiently
         * recent.  Go to the server.
         */
        table = (TableImpl)store.getDispatcher().
                            getTable(store,
                                     NameUtils.switchToInternalUse(namespace),
                                     path[0],
                                     cost);
        if (table == null) {
            return null;
        }
        /* Update the cache */
        tableCache.put(table);

        return getChildVersion(table, path, tableVersion);
    }

    /**
     * Returns a table based on the path and version.
     */
    private TableImpl getChildVersion(TableImpl table,
                                      String[] path, int tableVersion) {
        /* Get child if needed */
        for (int i = 1; i < path.length && table != null; i++) {
            table = table.getChildTable(path[i]);
        }

        if ((table == null) || (table.numTableVersions() < tableVersion)) {
            return null;
        }
        return tableVersion == 0 ? table :
                                   (TableImpl)table.getVersion(tableVersion);
    }

    @Override
    public Table getTableById(long tableId)
        throws FaultException {
        TableImpl table = tableCache.get(tableId);
        if (table != null) {
            return table;
        }
        table = (TableImpl)store.getDispatcher().getTableById(store, tableId);
        tableCache.put(table);
        return table;
    }

    /**
     * Sets the TableMetadataCallback handler.
     *
     * @param handler the handler
     */
    public void setTableMetadataCallback(TableMetadataCallback handler) {
        metadataCallback = handler;
    }

    /**
     * Returns the MetadataCallback handler or null if not registered.
     */
    public TableMetadataCallback getTableMetadataCallback() {
        return metadataCallback;
    }

    /**
     * Sets the TableMetadataHelper helper to one provided by an
     * application which, for example, may cache Table handles.
     *
     * @param helper the helper
     */
    public void setCachedMetadataHelper(TableMetadataHelper helper) {
        metadataHelper = helper;
    }

    /**
     * Returns the cached TableMetadataHelper helper or null if
     * not set.
     *
     * @return the helper, or null if it has not been set by the
     * application
     */
    public TableMetadataHelper getCachedMetadataHelper() {
        return metadataHelper;
    }

    /**
     * Notifies the TableMetadataCallback handler if table metadata has been
     * changed. If no TableMetadataCallback handler is registered, this call
     * do nothing.
     *
     * Compares the specified {@code remoteSeqNum} with the local metadata seqNum,
     * if the specified {@code remoteSeqNum} is higher than local seqNum, then
     * invoke {@link TableMetadataCallback#metadataChanged}.
     */
    public void metadataNotification(int remoteSeqNum) {
        if (metadataCallback != null && remoteSeqNum > metadataSeqNum) {
            synchronized(this) {
                if (remoteSeqNum > metadataSeqNum) {
                    if (metadataCallback != null) {
                        metadataCallback.metadataChanged(metadataSeqNum,
                                                         remoteSeqNum);
                    }
                    metadataSeqNum = remoteSeqNum;
                }
            }
        }
    }

    /*
     * Note: the 2 getTables() interfaces are generally discouraged as they
     * pull the entire TableMetadata object from a server into a client.
     */
    @Override
    public Map<String, Table> getTables()
        throws FaultException {

        TableMetadata md = getTableMetadata();

        if (md == null) {
            return Collections.<String, Table>emptyMap();
        }

        return md.getTables();
    }

    @Override
    public Map<String, Table> getTables(String namespace)
        throws FaultException {

        final String internalNamespace =
            NameUtils.switchToInternalUse(namespace);
        final List<Table> tables =
            getTablesInternal(new TableListKey(internalNamespace));
        if (tables != null) {
            return getTableMap(tables);
        }

        /* Use old API */
        TableMetadata md = getTableMetadata();

        if (md == null) {
            return Collections.<String, Table>emptyMap();
        }

        return md.getTables(internalNamespace);
    }

    /**
     * Gets a list of system tables.
     */
    public List<Table> getSystemTables() {
        List<Table> sysTables = getTablesInternal(new SysTableListKey());
        if (sysTables != null) {
            return sysTables;
        }

        /* Use old API */
        sysTables = new ArrayList<>();
        for (Table table : getTables(null).values()) {
            TableImpl impl = (TableImpl)table;
            if (impl.isSystemTable()) {
                sysTables.add(impl);
            }
        }
        return sysTables;
    }

    /*
     * Builds an ordered map of table full name -> table from the specified list
     */
    private Map<String, Table> getTableMap(List<Table> list) {
        assert list != null;
        final Map<String, Table> map = new TreeMap<>(FieldComparator.instance);
        for (Table table : list) {
            map.put(table.getFullName(), table);
        }
        return map;
    }

    /**
     * Gets a list of tables based on the metadata key. If the operation was
     * not successful using the key due to an earlier version store, null is
     * returned.
     */
    private List<Table> getTablesInternal(MetadataKey key) {
        try {
            final TableList list = (TableList)getMetadataInfo(key);
            return list == null ?  Collections.emptyList() : list.get();
        } catch (FaultException fe) {
            /*
             * UOE means a serial version mismatch, in which case continue
             * and use the old scheme.
             */
            if (fe.getCause() instanceof UnsupportedOperationException) {
                /* Old version */
                return null;
            }
            throw fe;
        }
    }

    /*
     * Note: the 2 getTables() interfaces are generally discouraged as they
     * pull the entire TableMetadata object from a server into a client.
     */
    @Override
    public Set<String> listNamespaces()
        throws FaultException {

        TableMetadata md = getTableMetadata();
        return md == null ? Collections.<String>emptySet() :
            md.listNamespaces();
    }

    /**
     * Gets the TableMetadata object from a RepNode.
     * It is also used by the public getTables() interface.
     *
     * This method should never be used by clients directly. Fetching a
     * entire TableMetadata instance from a server node is highly
     * discouraged. Most normal applications will only ever need to get a
     * table at a time.
     *
     * This should not be public but unfortunately it is public so that the
     * Thrift-based proxy can use it to cache table information. That should
     * change but old proxies still need to work, so leave it for now.
     */
    public TableMetadata getTableMetadata()
        throws FaultException {
        return store.getDispatcher().getTableMetadata(store);
    }

    /**
     * Gets a region mapper.
     */
    public RegionMapper getRegionMapper() throws FaultException {
        try {
            /*
             * TODO - Now that the MD helper returned from the store is just
             * the header, it should be cached. It could be invalidated via the
             * DDL cache flush (see TableCache.clearCache(String, String).
             */
            return (RegionMapper)getMetadataInfo(new RegionMapperKey());
        } catch (FaultException fe) {
            /*
             * UOE means a serial version mismatch, in which case
             * use the old scheme.
             */
            if (fe.getCause() instanceof UnsupportedOperationException) {
                /* Old version */
                final TableMetadata md = getTableMetadata();
                return (md == null) ? null : md.getRegionMapper();
            }
            throw fe;
        }
    }

    /**
     * Gets table metadata info, potentially updating the cached sequence number
     * and notifying metadata listeners if data is returned.
     */
    private MetadataInfo getMetadataInfo(MetadataKey key) {
        final MetadataInfo info =
            store.getDispatcher().getMetadataInfo(store,
                                                  key,
                                                  metadataSeqNum);
        if (info != null) {
            metadataNotification(info.getSequenceNumber());
        }
        return info;
    }

    /* -- Table cache control,internal use only -- */

    /**
     * Sets the capacity of the table cache to the specified value. If the new
     * capacity is less than the current capacity the cache is cleared.
     * If the new capacity is 0 the cache is unbounded.
     *
     * @param newCapacity the new cache capacity
     */
    public void setCacheCapacity(int newCapacity) {
        tableCache.setCapacity(newCapacity);
    }

    /**
     * Returns the table cache capacity.
     *
     * @return the table cache capacity
     */
    public int getCacheCapacity() {
        return tableCache.getCapacity();
    }

    /**
     * Enables or disables the table cache. If enable is false the cache is
     * disabled and the cache is cleared.
     *
     * @param enable true to enable the table cache false to disable
     */
    public void setCacheEnabled(boolean enable) {
        tableCache.setEnabled(enable);
    }

    /**
     * Gets the number of non-expired table cache entries that have been
     * removed due to cache capacity limit since the creation of the cache
     * or the last call to this method.
     *
     * @return the eviction count
     */
    public int getAndResetEvictionCount() {
        return tableCache.getAndResetEvictionCount();
    }

    /**
     * Clears all entries from the table cache.
     */
    public void clearCache() {
        tableCache.clear();
    }

    /**
     * Removes the specified table from the cache.
     */
    public void removeFromCache(String namespace, String tableName) {
        tableCache.remove(namespace, tableName);
    }

    /**
     * Removes the specified table from the cache. If tableId == 0 the cache
     * will be cleared.
     */
    public void removeFromCache(long tableId) {
        if (tableId > 0L) {
            tableCache.remove(tableId);
        } else {
            tableCache.clear();
        }
    }

    /**
     * Validates the cache entry for the table, if present, using the seqNum.
     * If the cache entry's seq number is less than seqNum the entry is removed.
     */
    public void validateCache(long tableId, int seqNum) {
        tableCache.validate(tableId, seqNum);
    }

    /*
     * Runtime interfaces
     */

    @Override
    public Row get(PrimaryKey rowKeyArg,
                   ReadOptions readOptions)
        throws FaultException {

        PrimaryKeyImpl rowKey = (PrimaryKeyImpl)rowKeyArg;
        Result result = getInternal(rowKey, readOptions);
        return processGetResult(result, rowKey);
    }

    public Row processGetResult(Result result, PrimaryKeyImpl rowKey) {
        ValueReader<RowImpl> reader =
            rowKey.getTableImpl().initRowReader(null);
        createRowFromGetResult(result, rowKey, reader);
        return reader.getValue();
    }

    /* public for use by cloud driver */
    public void createRowFromGetResult(Result result,
                                       RowSerializer rowKey,
                                       ValueReader<?> reader) {

        final ValueVersion vv = KVStoreImpl.processGetResult(result);
        if (vv == null) {
            reader.reset();
            return;
        }
        ((TableImpl)rowKey.getTable()).readKeyFields(reader, rowKey);
        getRowFromValueVersion(vv, rowKey, result.getPreviousExpirationTime(),
                               result.getPreviousModificationTime(),
                               false, reader);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result getInternal(RowSerializer rowKey,
                              ReadOptions readOptions)
        throws FaultException {

        return store.executeRequest(makeGetRequest(rowKey, readOptions));
    }

    private Request makeGetRequest(RowSerializer rowKey,
                                   ReadOptions readOptions) {
        TableImpl table = (TableImpl) rowKey.getTable();
        Key key = table.createKeyInternal(rowKey, false);
        final Request req = store.makeGetRequest(key,
                                                 table.getId(),
                                                 getConsistency(readOptions),
                                                 getTimeout(readOptions),
                                                 getTimeoutUnit(readOptions),
                                                 true /* excludeTombstones */);
        setContextFromOptions(req, readOptions);
        return req;
    }

    @Override
    public CompletableFuture<Row> getAsync(PrimaryKey key,
                                           ReadOptions readOptions) {
        try {
            final PrimaryKeyImpl rowKey = (PrimaryKeyImpl) key;
            return getAsyncInternal(
                rowKey, readOptions,
                result -> processGetResult(result, rowKey));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> getAsyncInternal(RowSerializer key,
                                                      ReadOptions readOptions)
    {
        return getAsyncInternal(key, readOptions, r -> r);
    }

    private <R> CompletableFuture<R> getAsyncInternal(
        RowSerializer key,
        ReadOptions readOptions,
        Function<Result, R> convertResult)
    {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(makeGetRequest(key, readOptions)),
                convertResult::apply);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public Version put(Row rowArg,
                       ReturnRow prevRowArg,
                       WriteOptions writeOptions)
        throws FaultException {
        RowImpl row = (RowImpl) rowArg;
        Result result = putInternal(row, prevRowArg, writeOptions);
        return processPutResult(result, row, prevRowArg);
    }

    public static void setLocalRegionId(RowSerializer row) {
        /*
         * TODO: More changes will be needed if we want to support an internal
         * cloud API for putResolve.
         */
        ((RowImpl)row).setRegionId(Region.LOCAL_REGION_ID);
    }

    public Version processPutResult(Result result,
                                    RowImpl row,
                                    ReturnRow prevRowArg) {
        if (result.getSuccess()) {
            row.setExpirationTime(result.getNewExpirationTime());
            row.setModificationTime(result.getNewModificationTime());
        }

        initReturnRow(prevRowArg, row, result, null);
        return KVStoreImpl.getPutResult(result);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public void initReturnRowFromResult(ReturnRow rr,
                                        RowSerializer row,
                                        Result result,
                                        ValueReader<?> reader) {
        initReturnRow(rr, row, result, reader);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result putInternal(RowSerializer row,
                              ReturnRow prevRowArg,
                              WriteOptions writeOptions)
        throws FaultException {
        if (row.isFromMRTable()) {
            setLocalRegionId(row);
        }

        GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

        Result result = store.executeRequest(
            makePutRequest(row, prevRowArg, writeOptions, genInfo));

        setGeneratedValue(result, genInfo);

        return result;
    }

    /*
     * The variable Result.generatedValue is overloaded.
     * It is used to store the generated value either in IDENTITY column or
     * STRING AS UUID GENERATED BY DEFAULT column.
     * See variable Result.generatedValue definition for details.
     */
    private void setGeneratedValue(Result result,
                                   GeneratedValueInfo genInfo) {
        if (genInfo != null) {
            result.setGeneratedValue(genInfo.getGeneratedValue());
        }
    }

    /**
     * Returns a GeneratedValueInfo if the table has either an identity
     * column or uuid column.
     */
    private static GeneratedValueInfo makeGenInfo(RowSerializer row,
                                                 WriteOptions writeOptions) {

        TableImpl table = (TableImpl) row.getTable();
        GeneratedValueInfo genInfo = null;
        if (table.hasIdentityColumn()) {
            int cacheSize = (writeOptions == null ? 0 :
                             writeOptions.getIdentityCacheSize());
            genInfo = new GeneratedValueInfo(cacheSize);
        } else if (table.hasUUIDcolumn()) {
            genInfo = new GeneratedValueInfo();
        }
        return genInfo;
    }

    public static ValueSerializer.FieldValueSerializer fillIdentityValue(
        ValueSerializer.RecordValueSerializer rec, int pos, TableImpl table,
        GeneratedValueInfo genInfo, KVStoreImpl store) {

        if ( !table.hasIdentityColumn()) {
            return null;
        }
        if (genInfo == null) {
            throw new IllegalStateException(
                "fillIdentityValue requires IdentityInfo");
        }

        ValueSerializer.FieldValueSerializer userValue = rec.get(pos);
        RecordDefImpl rowDef = table.getRowDef();
        int colPos = table.getIdentityColumn();
        FieldValueImpl identityValue =
            store.getIdentityNextValue(table,
                                       rowDef.getFieldDef(colPos),
                                       genInfo.getCacheSize(),
                                       userValue, colPos);

        if (identityValue != null) {
            /*
             * Set this value in both the IdentityInfo and the row, if
             * available. This minimizes code change, especially as related
             * to handling the multiple put operation (execute).
             */
            genInfo.setGeneratedValue(identityValue);
            if (rec instanceof RowImpl) {
                ((RowImpl)rec).putInternal(pos, identityValue,
                                           false /*fromUser*/);
            }
        }

        if (identityValue == null) {
            return userValue;
        }

        return identityValue;
    }

    private Request makePutRequest(RowSerializer row,
                                   ReturnRow prevRow,
                                   WriteOptions writeOptions,
                                   GeneratedValueInfo genInfo) {
        TableImpl table = (TableImpl)row.getTable();
        Key key = table.createKeyInternal(row, false, store, genInfo);
        Value value = table.createValueInternal(row, store, genInfo);
        final Request req = store.makePutRequest(key, value,
                                                 getReturnChoice(prevRow),
                                                 table.getId(),
                                                 getDurability(writeOptions),
                                                 getTimeout(writeOptions),
                                                 getTimeoutUnit(writeOptions),
                                                 getTTL(row.getTTL(), table),
                                                 getUpdateTTL(writeOptions));
        setContextFromOptions(req, writeOptions);
        return req;
    }

    private ReturnValueVersion.Choice getReturnChoice(ReturnRow prevRow) {
        return ReturnRowImpl.mapChoice(
            (prevRow != null) ? prevRow.getReturnChoice() : null);
    }

    @Override
    public CompletableFuture<Version> putAsync(Row row,
                                               ReturnRow prevRow,
                                               WriteOptions writeOptions) {
        try {
            final RowImpl rowImpl = (RowImpl) row;
            return putAsyncInternal(
                rowImpl, prevRow, writeOptions,
                result -> processPutResult(result, rowImpl, prevRow));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> putAsyncInternal(
        RowSerializer row, ReturnRow prevRow, WriteOptions writeOptions)
    {
        return putAsyncInternal(row, prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        putAsyncInternal(RowSerializer row,
                         ReturnRow prevRow,
                         WriteOptions writeOptions,
                         Function<Result, R> convertResult) {
        try {
            checkNull("row", row);
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

            return thenApply(
                store.executeRequestAsync(makePutRequest(
                                              row, prevRow, writeOptions,
                                              genInfo)),
                result -> {
                    setGeneratedValue(result, genInfo);
                    return convertResult.apply(result);
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public Version putIfAbsent(Row rowArg,
                               ReturnRow prevRowArg,
                               WriteOptions writeOptions)
        throws FaultException {

        RowImpl row = (RowImpl) rowArg;
        Result result = putIfAbsentInternal(row,
                                            prevRowArg,
                                            writeOptions);
        return processPutResult(result, row, prevRowArg);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result putIfAbsentInternal(RowSerializer row,
                                      ReturnRow prevRowArg,
                                      WriteOptions writeOptions)
        throws FaultException {
        if (row.isFromMRTable()) {
            setLocalRegionId(row);
        }
        GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

        Result result = store.executeRequest(
            makePutIfAbsentRequest(row, prevRowArg, writeOptions, genInfo));

        setGeneratedValue(result, genInfo);
        return result;
    }

    private Request makePutIfAbsentRequest(RowSerializer row,
                                           ReturnRow prevRow,
                                           WriteOptions writeOptions,
                                           GeneratedValueInfo genInfo) {
        TableImpl table = (TableImpl)row.getTable();
        final Key key = table.createKeyInternal(row, false, store, genInfo);
        final Value value = table.createValueInternal(row, store, genInfo);

        final Request req =
            store.makePutIfAbsentRequest(key, value,
                                         getReturnChoice(prevRow),
                                         table.getId(),
                                         getDurability(writeOptions),
                                         getTimeout(writeOptions),
                                         getTimeoutUnit(writeOptions),
                                         getTTL(row.getTTL(), table),
                                         getUpdateTTL(writeOptions));
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Version> putIfAbsentAsync(
        Row row, ReturnRow prevRowArg, WriteOptions writeOptions)
    {
        try {
            final RowImpl rowImpl = (RowImpl) row;
            return putIfAbsentAsyncInternal(
                rowImpl, prevRowArg, writeOptions,
                result -> processPutResult(result, rowImpl, prevRowArg));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> putIfAbsentAsyncInternal(
        RowSerializer row, ReturnRow prevRow, WriteOptions writeOptions)
    {
        return putIfAbsentAsyncInternal(row, prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        putIfAbsentAsyncInternal(RowSerializer row,
                                 ReturnRow prevRow,
                                 WriteOptions writeOptions,
                                 Function<Result, R> convertResult) {
        try {
            checkNull("row", row);
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

            return thenApply(
                store.executeRequestAsync(
                    makePutIfAbsentRequest(row, prevRow, writeOptions,
                                           genInfo)),
                result -> {
                    setGeneratedValue(result, genInfo);
                    return convertResult.apply(result);
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public Version putIfPresent(Row rowArg,
                                ReturnRow prevRowArg,
                                WriteOptions writeOptions)
        throws FaultException {

        RowImpl row = (RowImpl) rowArg;
        Result result =
            putIfPresentInternal(row, prevRowArg, writeOptions);
        return processPutResult(result, row, prevRowArg);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result putIfPresentInternal(RowSerializer row,
                                       ReturnRow prevRowArg,
                                       WriteOptions writeOptions)
        throws FaultException {
        if (row.isFromMRTable()) {
            setLocalRegionId(row);
        }

        GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

        Result result = store.executeRequest(
            makePutIfPresentRequest(row, prevRowArg, writeOptions, genInfo));

        setGeneratedValue(result, genInfo);
        return result;
    }

    private Request makePutIfPresentRequest(RowSerializer row,
                                            ReturnRow prevRow,
                                            WriteOptions writeOptions,
                                            GeneratedValueInfo genInfo) {
        TableImpl table = (TableImpl)row.getTable();
        final Key key = table.createKeyInternal(row, false, store, genInfo);
        final Value value = table.createValueInternal(row, store, genInfo);
        final Request req =
            store.makePutIfPresentRequest(key, value,
                                          getReturnChoice(prevRow),
                                          table.getId(),
                                          getDurability(writeOptions),
                                          getTimeout(writeOptions),
                                          getTimeoutUnit(writeOptions),
                                          getTTL(row.getTTL(), table),
                                          getUpdateTTL(writeOptions));
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Version> putIfPresentAsync(
        Row row, ReturnRow prevRow, WriteOptions writeOptions)
    {
        try {
            final RowImpl rowImpl = (RowImpl) row;
            return putIfPresentAsyncInternal(
                rowImpl, prevRow, writeOptions,
                result -> processPutResult(result, rowImpl, prevRow));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> putIfPresentAsyncInternal(
        RowSerializer row, ReturnRow prevRow, WriteOptions writeOptions)
    {
        return putIfPresentAsyncInternal(row, prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        putIfPresentAsyncInternal(RowSerializer row,
                                  ReturnRow prevRow,
                                  WriteOptions writeOptions,
                                  Function<Result, R> convertResult) {
        try {
            checkNull("row", row);
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

            return thenApply(
                store.executeRequestAsync(
                    makePutIfPresentRequest(row, prevRow, writeOptions,
                                            genInfo)),
                result -> {
                    setGeneratedValue(result, genInfo);
                    return convertResult.apply(result);
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public Version putIfVersion(Row rowArg,
                                Version matchVersion,
                                ReturnRow prevRowArg,
                                WriteOptions writeOptions)
        throws FaultException {

        RowImpl row = (RowImpl) rowArg;
        Result result = putIfVersionInternal(row,
                                             matchVersion,
                                             prevRowArg,
                                             writeOptions);
        return processPutResult(result, row, prevRowArg);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result putIfVersionInternal(RowSerializer row,
                                       Version matchVersion,
                                       ReturnRow prevRowArg,
                                       WriteOptions writeOptions)
        throws FaultException {
        if (row.isFromMRTable()) {
            setLocalRegionId(row);
        }

        GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

        Result result = store.executeRequest(
            makePutIfVersionRequest(row, matchVersion, prevRowArg,
                                    writeOptions, genInfo));

        setGeneratedValue(result, genInfo);
        return result;
    }

    private Request makePutIfVersionRequest(RowSerializer row,
                                            Version matchVersion,
                                            ReturnRow prevRow,
                                            WriteOptions writeOptions,
                                            GeneratedValueInfo genInfo) {
        TableImpl table = (TableImpl)row.getTable();
        final Key key = table.createKeyInternal(row, false, store, genInfo);
        final Value value = table.createValueInternal(row, store, genInfo);
        final Request req =
            store.makePutIfVersionRequest(key, value, matchVersion,
                                          getReturnChoice(prevRow),
                                          table.getId(),
                                          getDurability(writeOptions),
                                          getTimeout(writeOptions),
                                          getTimeoutUnit(writeOptions),
                                          getTTL(row.getTTL(), table),
                                          getUpdateTTL(writeOptions));
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Version>
        putIfVersionAsync(Row row,
                          Version matchVersion,
                          ReturnRow prevRow,
                          WriteOptions writeOptions) {
        try {
            final RowImpl rowImpl = (RowImpl) row;
            return putIfVersionAsyncInternal(
                rowImpl, matchVersion, prevRow, writeOptions,
                result -> processPutResult(result, rowImpl, prevRow));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> putIfVersionAsyncInternal(
        RowSerializer row,
        Version matchVersion,
        ReturnRow prevRow,
        WriteOptions writeOptions)
    {
        return putIfVersionAsyncInternal(row, matchVersion, prevRow,
                                         writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        putIfVersionAsyncInternal(RowSerializer row,
                                  Version matchVersion,
                                  ReturnRow prevRow,
                                  WriteOptions writeOptions,
                                  Function<Result, R> convertResult) {
        try {
            checkNull("row", row);
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            GeneratedValueInfo genInfo = makeGenInfo(row, writeOptions);

            return thenApply(
                store.executeRequestAsync(
                    makePutIfVersionRequest(row, matchVersion, prevRow,
                                            writeOptions, genInfo)),
                result -> {
                    setGeneratedValue(result, genInfo);
                    return convertResult.apply(result);
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public void put(List<EntryStream<Row>> rowStreams,
                    BulkWriteOptions writeOptions) {

        if (rowStreams == null || rowStreams.isEmpty()) {
            throw new IllegalArgumentException("The stream list cannot be " +
                "null or empty.");
        }

        if (rowStreams.contains(null)) {
            throw new IllegalArgumentException("Elements of stream list " +
                "must not be null.");
        }

        /*
         * Track all tables in the operation for use later. A map is used
         * to keep the comparison simple, based on full table name.
         * TableImpl.equals() does a lot of work (perhaps overkill).
         */
        final Map<String, TableImpl> tablesUsed =
            new HashMap<String, TableImpl>();

        final Map<String, GeneratedValueInfo> tableIdInfos =
            new HashMap<String, GeneratedValueInfo>();

        final BulkWriteOptions options =
            (writeOptions != null) ?
             writeOptions : new BulkWriteOptions(getDurability(writeOptions),
                                                 getTimeout(writeOptions),
                                                 getTimeoutUnit(writeOptions));

        final BulkPut<Row> bulkPut =
            new BulkPut<Row>(store, options, rowStreams, store.getLogger()) {

                @Override
                public BulkPut<Row>.StreamReader<Row>
                    createReader(int streamId, EntryStream<Row> stream) {
                    return new StreamReader<Row>(streamId, stream) {

                        @Override
                        protected Key getKey(Row row) {
                            TableImpl table = (TableImpl)row.getTable();
                            return table.createKeyInternal((RowImpl)row, false,
                                    store, getIdentityInfo(table));
                        }

                        @Override
                        protected Value getValue(Row row) {
                            if (((RowImpl)row).isFromMRTable()) {
                                setLocalRegionId((RowImpl)row);
                            }
                            TableImpl table = (TableImpl)row.getTable();
                            return table.createValueInternal((RowImpl)row,
                                    store, getIdentityInfo(table));
                        }

                        @Override
                        protected long getTableId(Row row) {
                            /*
                             * Return the table id but also put the table
                             * into the map of tables used in the operation
                             */
                            TableImpl table = (TableImpl)row.getTable();
                            tablesUsed.put(table.getFullNamespaceName(), table);
                            return table.getId();
                        }

                        @Override
                        protected TimeToLive getTTL(Row row) {
                            return TableAPIImpl.getTTL((RowImpl)row,
                                                       row.getTable());
                        }

                        private GeneratedValueInfo getIdentityInfo(TableImpl table) {
                            String tableName = table.getFullNamespaceName();
                            if (tableIdInfos.containsKey(tableName)) {
                                return tableIdInfos.get(tableName);
                            }
                            GeneratedValueInfo genInfo = null;
                            if (table.hasIdentityColumn()) {
                                int idCacheSize = options.getIdentityCacheSize();
                                genInfo = new GeneratedValueInfo(idCacheSize);
                                tableIdInfos.put(tableName, genInfo);
                            }
                            return genInfo;
                        }
                    };
                }

                @Override
                protected Row convertToEntry(Key key, Value value) {
                    final byte[] keyBytes =
                        store.getKeySerializer().toByteArray(key);
                    final TableImpl table = (TableImpl)findTableByKey(keyBytes);
                    if (table == null) {
                        return null;
                    }
                    final RowImpl row =
                        table.createRowFromKeyBytes(keyBytes);
                    assert(row != null);
                    final ValueVersion vv = new ValueVersion(value, null);
                    return row.rowFromValueVersion(vv, false) ? row : null;
                }

                private Table findTableByKey(final byte[] keyBytes) {
                    for (TableImpl table : tablesUsed.values()) {
                        final TableImpl target = table.findTargetTable(keyBytes);
                        if (target != null) {
                            return target;
                        }
                    }
                    return null;
                }
        };

        try {
            bulkPut.execute();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unexpected interrupt during " +
                                            "putBulk()", e);
        }
    }

    /**
     * Deprecated in favor of KVStore.execute. Delegate over to that newer
     * method.
     */
    @Deprecated
    @Override
    public oracle.kv.table.ExecutionFuture execute(String statement)
            throws IllegalArgumentException, FaultException {
        return new DeprecatedResults.ExecutionFutureWrapper(store.execute(statement));
    }

    @Deprecated
    @Override
    public oracle.kv.table.StatementResult executeSync(String statement)
        throws FaultException {
        return new DeprecatedResults.StatementResultWrapper
                (store.executeSync(statement));
    }

    @Deprecated
    @Override
    public oracle.kv.table.ExecutionFuture getFuture(int planId) {
        if (planId < 1) {
            throw new IllegalArgumentException("PlanId " + planId +
                                               " isn't valid, must be > 1");
        }
        byte[] futureBytes = DdlFuture.toByteArray(planId);
        return new DeprecatedResults.ExecutionFutureWrapper
                (store.getFuture(futureBytes));
    }

    /*
     * Multi/iterator ops
     */
    @Override
    public List<Row> multiGet(PrimaryKey rowKeyArg,
                              MultiRowOptions getOptions,
                              ReadOptions readOptions)
        throws FaultException {

        return processMultiResults(
            rowKeyArg, getOptions,
            store.executeRequest(
                makeMultiGetTableRequest(rowKeyArg, getOptions, readOptions)));
    }

    private Request makeMultiGetTableRequest(PrimaryKey rowKey,
                                             MultiRowOptions getOptions,
                                             ReadOptions readOptions) {
        Table table = rowKey.getTable();
        TableKey key = TableKey.createKey(table, rowKey, true);
        if (!key.getMajorKeyComplete()) {
            throw new IllegalArgumentException
                ("Cannot perform multiGet on a primary key without a " +
                 "complete major path");
        }

        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, false);
        }
        final byte[] parentKeyBytes =
            store.getKeySerializer().toByteArray(key.getKey());
        final PartitionId partitionId =
            store.getDispatcher().getPartitionId(parentKeyBytes);
        final MultiGetTable get =
            new MultiGetTable(parentKeyBytes,
                              makeTargetTables(table, getOptions),
                              makeKeyRange(key, getOptions));
        final Request req = store.makeReadRequest(get, partitionId,
                                                  getConsistency(readOptions),
                                                  getTimeout(readOptions),
                                                  getTimeoutUnit(readOptions));
        setContextFromOptions(req, readOptions);
        return req;
    }

    @Override
    public CompletableFuture<List<Row>>
        multiGetAsync(final PrimaryKey key,
                      final MultiRowOptions getOptions,
                      ReadOptions readOptions) {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(
                    makeMultiGetTableRequest(key, getOptions, readOptions)),
                result -> processMultiResults(key, getOptions, result));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public List<PrimaryKey> multiGetKeys(PrimaryKey rowKeyArg,
                                         MultiRowOptions getOptions,
                                         ReadOptions readOptions)
        throws FaultException {

        final Result result = store.executeRequest(
            makeMultiGetTableKeysRequest(rowKeyArg, getOptions, readOptions));
        return processMultiResults(rowKeyArg, getOptions, result.getKeyList());
    }

    private Request makeMultiGetTableKeysRequest(PrimaryKey rowKey,
                                                 MultiRowOptions getOptions,
                                                 ReadOptions readOptions) {
        Table table = rowKey.getTable();
        TableKey key = TableKey.createKey(table, rowKey, true);
        if (!key.getMajorKeyComplete()) {
            throw new IllegalArgumentException
                ("Cannot perform multiGet on a primary key without a " +
                 "complete major path");
        }

        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, false);
        }
        final byte[] parentKeyBytes =
            store.getKeySerializer().toByteArray(key.getKey());
        final PartitionId partitionId =
            store.getDispatcher().getPartitionId(parentKeyBytes);
        final MultiGetTableKeys get =
            new MultiGetTableKeys(parentKeyBytes,
                                  makeTargetTables(table, getOptions),
                                  makeKeyRange(key, getOptions),
                                  1 /* emptyReadFactor */);
        final Request req = store.makeReadRequest(get, partitionId,
                                                  getConsistency(readOptions),
                                                  getTimeout(readOptions),
                                                  getTimeoutUnit(readOptions));
        setContextFromOptions(req, readOptions);
        return req;
    }

    @Override
    public CompletableFuture<List<PrimaryKey>>
        multiGetKeysAsync(final PrimaryKey key,
                          final MultiRowOptions getOptions,
                          ReadOptions readOptions) {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(
                    makeMultiGetTableKeysRequest(key, getOptions,
                                                 readOptions)),
                result -> processMultiResults(key, getOptions,
                                              result.getKeyList()));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public TableIterator<Row> tableIterator(PrimaryKey rowKeyArg,
                                            MultiRowOptions getOptions,
                                            TableIteratorOptions iterateOptions)
        throws FaultException {
        return tableIterator(rowKeyArg, getOptions, iterateOptions, null);
    }

    /**
     * @hidden
     */
    public TableIterator<Row> tableIterator(PrimaryKey rowKeyArg,
                                            MultiRowOptions getOptions,
                                            TableIteratorOptions iterateOptions,
                                            Set<Integer> partitions)
        throws FaultException {

        return tableIterator(rowKeyArg, getOptions, iterateOptions, partitions,
                             null);
    }

    private AsyncTableIterator<Row> tableIterator(
        PrimaryKey rowKey,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions,
        Set<Integer> partitions,
        AsyncIterationHandleImpl<Row> iterationHandle) throws FaultException {

        final Table table = rowKey.getTable();
        final TableKey key = TableKey.createKey(table, rowKey, true);

        if (getOptions != null) {

            validateMultiRowOptions(getOptions, table, false);
        }
        return TableScan.createTableIterator(this, key, getOptions,
                                             iterateOptions, partitions,
                                             iterationHandle);
    }

    @Override
    public Publisher<Row> tableIteratorAsync(
        PrimaryKey key,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions) throws FaultException {

        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                checkNull("key", key);
                final AsyncIterationHandleImpl<Row> iterationHandle =
                    new AsyncIterationHandleImpl<Row>(logger);
                iterationHandle.setIterator(
                    tableIterator(key, getOptions, iterateOptions, null,
                                  iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    /**
     * For HTTP Proxy use only.
     * @hidden
     *
     * Return the rows associated with a partial primary key in pagination
     * manner.
     *
     * The number of rows returned per batch is controlled by batchResultSize
     * and maxReadKB of {@code TableIteratorOptions}.
     *
     * The continuationKey references start position the scan from, it is
     * returned in the result of last execution of this operation.
     *
     * @since 18.1
     */
    public MultiGetResult<Row> multiGet(PrimaryKey rowKey,
                                        byte[] continuationKey,
                                        MultiRowOptions getOptions,
                                        TableIteratorOptions iterateOptions)
        throws FaultException {

        checkNull("rowKey", rowKey);
        final TableKey key =
            getMultiGetKey(rowKey, getOptions, iterateOptions);
        return TableScan.multiGet(this, key, continuationKey, getOptions,
                                  iterateOptions);
    }

    /**
     * For HTTP Proxy use only.
     *
     * @hidden
     * @since 18.1
     */
    public CompletableFuture<MultiGetResult<Row>>
        multiGetAsync(PrimaryKey rowKey,
                      byte[] continuationKey,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions) {
        try {
            checkNull("rowKey", rowKey);
            final TableKey key =
                getMultiGetKey(rowKey, getOptions, iterateOptions);
            return TableScan.multiGetAsync(this, key, continuationKey,
                                           getOptions, iterateOptions);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * For HTTP Proxy use only.
     * @hidden
     *
     * Return the primary keys associated with a partial primary key in
     * pagination manner.
     *
     * The number of keys returned per batch is controlled by batchResultSize
     * and maxReadKB of {@code TableIteratorOptions}.
     *
     * The continuationKey references start position the scan from, it is
     * returned in the result of last execution of this operation.
     *
     * @since 18.1
     */
    public MultiGetResult<PrimaryKey>
        multiGetKeys(PrimaryKey rowKey,
                     byte[] continuationKey,
                     MultiRowOptions getOptions,
                     TableIteratorOptions iterateOptions)
        throws FaultException {

        final TableKey key =
            getMultiGetKey(rowKey, getOptions, iterateOptions);
        return TableScan.multiGetKeys(this, key, continuationKey,
                                      getOptions, iterateOptions);
    }

    /**
     * For HTTP Proxy use only.
     *
     * @hidden
     */
    public CompletableFuture<MultiGetResult<PrimaryKey>>
        multiGetKeysAsync(PrimaryKey rowKey,
                          byte[] continuationKey,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions) {
        try {
            checkNull("rowKey", rowKey);
            final TableKey key =
                getMultiGetKey(rowKey, getOptions, iterateOptions);
            return TableScan.multiGetKeysAsync(this, key, continuationKey,
                                               getOptions, iterateOptions);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private TableKey getMultiGetKey(PrimaryKey rowKey,
                                    MultiRowOptions getOptions,
                                    TableIteratorOptions iterateOptions) {
        final Table table = rowKey.getTable();
        final TableKey key = TableKey.createKey(table, rowKey, true);

        boolean hasAncestorOrChild = false;
        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, false);
            hasAncestorOrChild =
                    (getOptions.getIncludedParentTables() != null ||
                     getOptions.getIncludedChildTables() != null);
        }

        if (iterateOptions != null) {
            if (iterateOptions.getDirection() != Direction.UNORDERED) {
                throw new IllegalArgumentException("Direction must be " +
                        "Direction.UNORDERED for this operation");
            }
            if (hasAncestorOrChild && iterateOptions.getMaxReadKB() != 0) {
                throw new IllegalArgumentException("Ancestor or child table " +
                        "returns are not supported if the size limitation " +
                        "'maxReadKB' of TableIteratorOptions is specified.");
            }
        }
        return key;
    }

    /**
     * For HTTP Proxy use only.
     * @hidden
     *
     * Return the rows associated with a partial index key in pagination manner.
     *
     * The number of rows returned per batch is controlled by batchResultSize
     * and maxReadKB of {@code TableIteratorOptions}.
     *
     * The continuationKey references start position the scan from, it is
     * returned in the result of last execution of this operation.
     *
     * @since 18.1
     */
    public MultiGetResult<Row> multiGet(IndexKey indexKeyArg,
                                        byte[] continuationKey,
                                        MultiRowOptions getOptions,
                                        TableIteratorOptions iterateOptions)
        throws FaultException {

        final IndexKeyImpl indexKey = (IndexKeyImpl)indexKeyArg;
        checkIndexMultiGetKeyOptions(indexKey.getTable(), getOptions,
                                     iterateOptions);
        return IndexScan.multiGet(this, indexKey, continuationKey,
                                  getOptions, iterateOptions);

    }

    /**
     * For HTTP Proxy use only.
     *
     * @hidden
     * @since 18.1
     */
    public CompletableFuture<MultiGetResult<Row>>
        multiGetAsync(IndexKey indexKeyArg,
                      byte[] continuationKey,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions) {
        try {
            final IndexKeyImpl indexKey =
                (IndexKeyImpl) checkNull("indexKeyArg", indexKeyArg);
            checkIndexMultiGetKeyOptions(indexKey.getTable(), getOptions,
                                         iterateOptions);
            return IndexScan.multiGetAsync(this, indexKey, continuationKey,
                                           getOptions, iterateOptions);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * For HTTP Proxy use only.
     * @hidden
     *
     * Return the primary and index keys associated with a partial index key in
     * pagination manner.
     *
     * The number of primary and index keys returned per batch is controlled by
     * batchResultSize and maxReadKB of {@code TableIteratorOptions}.
     *
     * The continuationKey references start position the scan from, it is
     * returned in the result of last execution of this operation.
     *
     * @since 18.1
     */
    public MultiGetResult<KeyPair>
        multiGetKeys(IndexKey indexKeyArg,
                     byte[] continuationKey,
                     MultiRowOptions getOptions,
                     TableIteratorOptions iterateOptions)
        throws FaultException {

        final IndexKeyImpl indexKey = (IndexKeyImpl)indexKeyArg;
        checkIndexMultiGetKeyOptions(indexKey.getTable(), getOptions,
                                     iterateOptions);
        return IndexScan.multiGetKeys(this, indexKey, continuationKey,
                                      getOptions, iterateOptions);
    }

    /**
     * For HTTP Proxy use only.
     *
     * @hidden
     * @since 18.1
     */
    public CompletableFuture<MultiGetResult<KeyPair>>
        multiGetKeysAsync(IndexKey indexKeyArg,
                          byte[] continuationKey,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions) {
        try {
            final IndexKeyImpl indexKey =
                (IndexKeyImpl) checkNull("indexKeyArg", indexKeyArg);
            checkIndexMultiGetKeyOptions(indexKey.getTable(), getOptions,
                                         iterateOptions);
            return IndexScan.multiGetKeysAsync(this, indexKey,
                                               continuationKey, getOptions,
                                               iterateOptions);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    private void checkIndexMultiGetKeyOptions(
        Table table,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions) {

        boolean hasAncestor = false;
        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, true);
            hasAncestor = (getOptions.getIncludedParentTables() != null);
        }

        if (iterateOptions != null) {
            if (iterateOptions.getDirection() != Direction.UNORDERED) {
                throw new IllegalArgumentException("Direction must be " +
                        "Direction.UNORDERED for this operation");
            }
            if (hasAncestor && iterateOptions.getMaxReadKB() != 0) {
                throw new IllegalArgumentException("Ancestor returns are not " +
                        "supported if the size limitation 'maxReadKB' of " +
                        "TableIteratorOptions is specified");
            }
        }
    }

    @Override
    public TableIterator<PrimaryKey> tableKeysIterator(
        PrimaryKey rowKey,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions) throws FaultException {

        return tableKeysIterator(rowKey, getOptions, iterateOptions, null);
    }

    private AsyncTableIterator<PrimaryKey> tableKeysIterator(
        PrimaryKey rowKey,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions,
        AsyncIterationHandleImpl<PrimaryKey> iterationHandle)
        throws FaultException {

        final Table table = rowKey.getTable();
        final TableKey key = TableKey.createKey(table, rowKey, true);

        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, false);
        }
        return TableScan.createTableKeysIterator(
            this, key, getOptions, iterateOptions, iterationHandle);
    }

    @Override
    public Publisher<PrimaryKey>
        tableKeysIteratorAsync(PrimaryKey key,
                               MultiRowOptions getOptions,
                               TableIteratorOptions iterateOptions)
        throws FaultException {

        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                checkNull("key", key);
                final AsyncIterationHandleImpl<PrimaryKey> iterationHandle =
                    new AsyncIterationHandleImpl<PrimaryKey>(logger);
                iterationHandle.setIterator(
                    tableKeysIterator(key, getOptions, iterateOptions,
                                      iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    @Override
    public boolean delete(PrimaryKey rowKeyArg,
                          ReturnRow prevRowArg,
                          WriteOptions writeOptions)
        throws FaultException {

        RowSerializer rowKey = (PrimaryKeyImpl)rowKeyArg;
        Result result = deleteInternal(rowKey,
                                       prevRowArg,
                                       writeOptions);
        initReturnRow(prevRowArg, rowKey, result, null);
        return result.getSuccess();
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result deleteInternal(RowSerializer rowKey,
                                 ReturnRow prevRowArg,
                                 WriteOptions writeOptions)
        throws FaultException {

        checkNull("rowKey", rowKey);
        return store.executeRequest(
            makeDeleteRequest(rowKey, prevRowArg, writeOptions));
    }

    private Request makeDeleteRequest(RowSerializer rowKey,
                                      ReturnRow prevRow,
                                      WriteOptions writeOptions) {
        TableImpl table = (TableImpl)rowKey.getTable();
        Key key = table.createKeyInternal(rowKey, false);
        final Request req =
            store.makeDeleteRequest(key,
                                    getReturnChoice(prevRow),
                                    getDurability(writeOptions),
                                    getTimeout(writeOptions),
                                    getTimeoutUnit(writeOptions),
                                    table.getId());
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Boolean> deleteAsync(PrimaryKey key,
                                                  ReturnRow prevRow,
                                                  WriteOptions writeOptions) {
        try {
            final PrimaryKeyImpl rowKey = (PrimaryKeyImpl) key;
            return deleteAsyncInternal(
                rowKey, prevRow, writeOptions,
                result -> {
                    initReturnRow(prevRow, rowKey, result, null);
                    return result.getSuccess();
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> deleteAsyncInternal(
        RowSerializer key, ReturnRow prevRow, WriteOptions writeOptions)
    {
        return deleteAsyncInternal(key, prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        deleteAsyncInternal(RowSerializer key,
                            ReturnRow prevRow,
                            WriteOptions writeOptions,
                            Function<Result, R> convertResult) {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(makeDeleteRequest(
                                              key, prevRow, writeOptions)),
                convertResult::apply);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public boolean deleteIfVersion(PrimaryKey rowKeyArg,
                                   Version matchVersion,
                                   ReturnRow prevRowArg,
                                   WriteOptions writeOptions)
        throws FaultException {

        RowSerializer rowKey = (PrimaryKeyImpl)rowKeyArg;
        Result result = deleteIfVersionInternal(rowKey,
                                                matchVersion,
                                                prevRowArg,
                                                writeOptions);
        initReturnRow(prevRowArg, rowKey, result, null);
        return result.getSuccess();
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result deleteIfVersionInternal(RowSerializer rowKey,
                                          Version matchVersion,
                                          ReturnRow prevRowArg,
                                          WriteOptions writeOptions)
        throws FaultException {

        return store.executeRequest(
            makeDeleteIfVersionRequest(rowKey, matchVersion, prevRowArg,
                                       writeOptions));
    }

    private Request makeDeleteIfVersionRequest(RowSerializer rowKey,
                                               Version matchVersion,
                                               ReturnRow prevRow,
                                               WriteOptions writeOptions) {
        TableImpl table = (TableImpl) rowKey.getTable();
        Key key = table.createKeyInternal(rowKey, false);
        final Request req = store.makeDeleteIfVersionRequest(
            key, matchVersion, getReturnChoice(prevRow),
            getDurability(writeOptions),
            getTimeout(writeOptions),
            getTimeoutUnit(writeOptions),
            table.getId());
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Boolean>
        deleteIfVersionAsync(PrimaryKey key,
                             Version matchVersion,
                             ReturnRow prevRow,
                             WriteOptions writeOptions)
    {
        try {
            final PrimaryKeyImpl rowKey = (PrimaryKeyImpl) key;
            return deleteIfVersionAsyncInternal(
                rowKey, matchVersion, prevRow, writeOptions,
                result -> {
                    initReturnRow(prevRow, rowKey, result, null);
                    return result.getSuccess();
                });
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result>
        deleteIfVersionAsyncInternal(RowSerializer key,
                                     Version matchVersion,
                                     ReturnRow prevRow,
                                     WriteOptions writeOptions)
    {
        return deleteIfVersionAsyncInternal(key, matchVersion,
                                            prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        deleteIfVersionAsyncInternal(RowSerializer key,
                                     Version matchVersion,
                                     ReturnRow prevRow,
                                     WriteOptions writeOptions,
                                     Function<Result, R> convertResult) {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(
                    makeDeleteIfVersionRequest(key, matchVersion, prevRow,
                                               writeOptions)),
                convertResult::apply);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    @Override
    public int multiDelete(PrimaryKey rowKeyArg,
                           MultiRowOptions getOptions,
                           WriteOptions writeOptions)
        throws FaultException {

        RowSerializer rowKey = (PrimaryKeyImpl)rowKeyArg;
        Result result = multiDeleteInternal(rowKey, null, getOptions,
                                            writeOptions);
        return result.getNDeletions();
    }

    /**
     * Public for HTTP Proxy use only.
     * @hidden
     *
     * Deletes multiple rows from a table in an atomic operation, the con
     */
    public Result multiDeleteInternal(RowSerializer rowKey,
                                      byte[] continuationKey,
                                      MultiRowOptions getOptions,
                                      WriteOptions writeOptions)
        throws FaultException {

        return store.executeRequest(
            makeMultiDeleteTableRequest(rowKey, continuationKey, getOptions,
                                        writeOptions));
    }

    private Request makeMultiDeleteTableRequest(RowSerializer rowKey,
                                                byte[] continuationKey,
                                                MultiRowOptions getOptions,
                                                WriteOptions writeOptions) {
        Table table = rowKey.getTable();
        TableKey key = TableKey.createKeyInternal(table, rowKey, true);
        if (!key.getMajorKeyComplete()) {
            throw new IllegalArgumentException
                ("Cannot perform multiDelete on a primary key without a " +
                 "complete major path.  Key: " + rowKey);
        }

        if (getOptions != null) {
            validateMultiRowOptions(getOptions, table, false);
        }
        final KeyRange keyRange = makeKeyRange(key, getOptions);

        final byte[] parentKeyBytes =
            store.getKeySerializer().toByteArray(key.getKey());
        final PartitionId partitionId =
            store.getDispatcher().getPartitionId(parentKeyBytes);
        final MultiDeleteTable del =
            new MultiDeleteTable(parentKeyBytes,
                                 makeTargetTables(table, getOptions),
                                 keyRange,
                                 continuationKey,
                                 getMaxWriteKB(writeOptions));
        final Request req =
            store.makeWriteRequest(del, partitionId,
                                   getDurability(writeOptions),
                                   getTimeout(writeOptions),
                                   getTimeoutUnit(writeOptions));
        setContextFromOptions(req, writeOptions);
        return req;
    }

    @Override
    public CompletableFuture<Integer>
        multiDeleteAsync(PrimaryKey key,
                         MultiRowOptions getOptions,
                         WriteOptions writeOptions)
    {
        return multiDeleteAsyncInternal((PrimaryKeyImpl) key,
                                        null /* continuationKey */,
                                        getOptions,
                                        writeOptions,
                                        result -> result.getNDeletions());
    }

    /**
     * Public for HTTP Proxy use only.
     * @hidden
     *
     * Deletes multiple rows from a table in an atomic operation, the con
     */
    public CompletableFuture<Result>
        multiDeleteAsyncInternal(RowSerializer key,
                                 byte[] continuationKey,
                                 MultiRowOptions getOptions,
                                 WriteOptions writeOptions) {
        return multiDeleteAsyncInternal(key, continuationKey,
                    getOptions, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        multiDeleteAsyncInternal(RowSerializer key,
                                 byte[] continuationKey,
                                 MultiRowOptions getOptions,
                                 WriteOptions writeOptions,
                                 Function<Result, R> convertResult) {
        try {
            checkNull("key", key);
            return thenApply(
                store.executeRequestAsync(
                    makeMultiDeleteTableRequest(key, continuationKey,
                                                getOptions, writeOptions)),
                convertResult::apply);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /*
     * Index iterator operations
     */
    @Override
    public TableIterator<Row>
        tableIterator(IndexKey indexKeyArg,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions)
        throws FaultException {
        return tableIterator(indexKeyArg, getOptions, iterateOptions, null);
    }

    @Override
    public Publisher<Row>
        tableIteratorAsync(IndexKey key,
                           MultiRowOptions getOptions,
                           TableIteratorOptions iterateOptions)
        throws FaultException {

        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                checkNull("key", key);
                final AsyncIterationHandleImpl<Row> iterationHandle =
                    new AsyncIterationHandleImpl<Row>(logger);
                iterationHandle.setIterator(
                    tableIterator(key, getOptions, iterateOptions, null,
                                  iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    public TableIterator<Row>
        tableIterator(IndexKey indexKeyArg,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions,
                      Set<RepGroupId> shardSet)
        throws FaultException {

        return tableIterator(indexKeyArg, getOptions, iterateOptions,
                             shardSet, null);
    }

    private AsyncTableIterator<Row>
        tableIterator(IndexKey indexKeyArg,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions,
                      Set<RepGroupId> shardSet,
                      AsyncIterationHandleImpl<Row> iterationHandle)
        throws FaultException {

        final IndexKeyImpl indexKey = (IndexKeyImpl) indexKeyArg;
        if (getOptions != null) {
            validateMultiRowOptions(getOptions, indexKey.getTable(), true);
        }
        return IndexScan.createTableIterator(this,
                                             indexKey,
                                             getOptions,
                                             iterateOptions,
                                             shardSet,
                                             iterationHandle);
    }

    @Override
    public TableIterator<KeyPair>
        tableKeysIterator(IndexKey indexKeyArg,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions)
        throws FaultException {

        return tableKeysIterator(indexKeyArg, getOptions, iterateOptions,
                                 null);
    }

    @Override
    public Publisher<KeyPair>
        tableKeysIteratorAsync(IndexKey key,
                               MultiRowOptions getOptions,
                               TableIteratorOptions iterateOptions)
        throws FaultException {

        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                checkNull("key", key);
                final AsyncIterationHandleImpl<KeyPair> iterationHandle =
                    new AsyncIterationHandleImpl<KeyPair>(logger);
                iterationHandle.setIterator(
                    tableKeysIterator(key, getOptions, iterateOptions,
                                      iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    private AsyncTableIterator<KeyPair>
        tableKeysIterator(IndexKey indexKeyArg,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions,
                          AsyncIterationHandleImpl<KeyPair> iterationHandle)
        throws FaultException {

        final IndexKeyImpl indexKey = (IndexKeyImpl) indexKeyArg;
        if (getOptions != null) {
            validateMultiRowOptions(getOptions, indexKey.getTable(), true);
        }
        return IndexScan.createTableKeysIterator(
            this, indexKey, getOptions, iterateOptions, iterationHandle);
    }

    @Override
    public TableOperationFactory getTableOperationFactory() {
        return opFactory;
    }

    @Override
    public TableIterator<Row>
        tableIterator(Iterator<PrimaryKey> primaryKeyIterator,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions) {
        return tableIterator(singletonList(primaryKeyIterator), getOptions,
                             iterateOptions);
    }

    private List<Iterator<PrimaryKey>> getSingletonList(
        Iterator<PrimaryKey> primaryKeyIterator)
    {
        if (primaryKeyIterator == null) {
            throw new IllegalArgumentException("Primary key iterator should " +
                "not be null");
        }
        return singletonList(primaryKeyIterator);
    }

    @Override
    public Publisher<Row>
        tableIteratorAsync(Iterator<PrimaryKey> primaryKeyIterator,
                           MultiRowOptions getOptions,
                           TableIteratorOptions iterateOptions) {
        return tableIteratorAsync(() -> getSingletonList(primaryKeyIterator),
                                  getOptions, iterateOptions);
    }

    @Override
    public TableIterator<PrimaryKey>
        tableKeysIterator(Iterator<PrimaryKey> primaryKeyIterator,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions) {
        return tableKeysIterator(getSingletonList(primaryKeyIterator),
                                 getOptions, iterateOptions);
    }

    @Override
    public Publisher<PrimaryKey> tableKeysIteratorAsync(
        Iterator<PrimaryKey> primaryKeyIterator,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions) {

        return tableKeysIteratorAsync(
            () -> getSingletonList(primaryKeyIterator), getOptions,
            iterateOptions);
    }

    @Override
    public TableIterator<Row>
        tableIterator(List<Iterator<PrimaryKey>> primaryKeyIterators,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions)
        throws FaultException {

        return tableIterator(checkPrimaryKeyIterators(primaryKeyIterators),
                             getOptions, iterateOptions, null);
    }

    private List<Iterator<PrimaryKey>> checkPrimaryKeyIterators(
        List<Iterator<PrimaryKey>> primaryKeyIterators)
    {
        if (primaryKeyIterators == null || primaryKeyIterators.isEmpty()) {
            throw new IllegalArgumentException("The key iterator list cannot " +
                "be null or empty");
        }

        if (primaryKeyIterators.contains(null)) {
            throw new IllegalArgumentException("The element of key iterator " +
                "list cannot be null.");
        }

        return primaryKeyIterators;
    }

    @Override
    public Publisher<Row>
        tableIteratorAsync(List<Iterator<PrimaryKey>> primaryKeyIterators,
                           MultiRowOptions getOptions,
                           TableIteratorOptions iterateOptions) {
        return tableIteratorAsync(
            () -> checkPrimaryKeyIterators(primaryKeyIterators), getOptions,
            iterateOptions);
    }

    private Publisher<Row> tableIteratorAsync(
        Supplier<List<Iterator<PrimaryKey>>> primaryKeyIterators,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions) {

        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                final AsyncIterationHandleImpl<Row> iterationHandle =
                    new AsyncIterationHandleImpl<Row>(logger);
                iterationHandle.setIterator(
                    tableIterator(primaryKeyIterators.get(), getOptions,
                                  iterateOptions, iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    private AsyncTableIterator<Row>
        tableIterator(List<Iterator<PrimaryKey>> primaryKeyIterators,
                      MultiRowOptions getOptions,
                      TableIteratorOptions iterateOptions,
                      AsyncIterationHandleImpl<Row> iterationHandle)
        throws FaultException {

        if (iterateOptions != null &&
            iterateOptions.getDirection() != Direction.UNORDERED) {
            throw new IllegalArgumentException("Direction must be " +
                "Direction.UNORDERED for this operation");
        }

        return new TableMultiGetBatch(this, primaryKeyIterators,
                                      getOptions, iterateOptions,
                                      iterationHandle)
            .createIterator();
    }

    @Override
    public TableIterator<PrimaryKey>
        tableKeysIterator(List<Iterator<PrimaryKey>> primaryKeyIterators,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions)
        throws FaultException {

        return tableKeysIterator(checkPrimaryKeyIterators(primaryKeyIterators),
                                 getOptions, iterateOptions, null);
    }

    @Override
    public Publisher<PrimaryKey>
        tableKeysIteratorAsync(List<Iterator<PrimaryKey>> primaryKeyIterators,
                               MultiRowOptions getOptions,
                               TableIteratorOptions iterateOptions) {
        return tableKeysIteratorAsync(
            () -> checkPrimaryKeyIterators(primaryKeyIterators), getOptions,
            iterateOptions);
    }

    private Publisher<PrimaryKey> tableKeysIteratorAsync(
        Supplier<List<Iterator<PrimaryKey>>> primaryKeyIterators,
        MultiRowOptions getOptions,
        TableIteratorOptions iterateOptions)
    {
        final Logger logger = store.getLogger();
        return AsyncPublisherImpl.newInstance(
            () -> {
                final AsyncIterationHandleImpl<PrimaryKey> iterationHandle =
                    new AsyncIterationHandleImpl<PrimaryKey>(logger);
                iterationHandle.setIterator(
                    tableKeysIterator(primaryKeyIterators.get(), getOptions,
                                      iterateOptions, iterationHandle));
                return iterationHandle;
            },
            logger);
    }

    private AsyncTableIterator<PrimaryKey>
        tableKeysIterator(List<Iterator<PrimaryKey>> primaryKeyIterators,
                          MultiRowOptions getOptions,
                          TableIteratorOptions iterateOptions,
                          AsyncIterationHandleImpl<PrimaryKey> iterationHandle)
        throws FaultException {

        if (iterateOptions != null &&
            iterateOptions.getDirection() != Direction.UNORDERED) {
            throw new IllegalArgumentException("Direction must be " +
                "Direction.UNORDERED for this operation");
        }

        return new TableMultiGetBatch(this, primaryKeyIterators, getOptions,
                                      iterateOptions, iterationHandle)
            .createKeysIterator();
    }

    /**
     * @hidden
     */
    public TableIterator<KeyValueVersion>
        tableKVIterator(PrimaryKey rowKeyArg,
                        MultiRowOptions getOptions,
                        TableIteratorOptions iterateOptions)
        throws FaultException {

        return tableKVIterator(rowKeyArg, getOptions, iterateOptions, null);
    }

    /**
     * @hidden
     */
    public TableIterator<KeyValueVersion>
        tableKVIterator(PrimaryKey rowKeyArg,
                        MultiRowOptions getOptions,
                        TableIteratorOptions iterateOptions,
                        Set<Integer> partitions)
        throws FaultException {

        final PrimaryKeyImpl rowKey = (PrimaryKeyImpl) rowKeyArg;
        final Table table = rowKey.getTable();
        final TableKey key = TableKey.createKey(table, rowKey, true);

        if (getOptions != null) {
            throw new IllegalArgumentException("MultiRowOption currently " +
                "not supported by tableKVIterator");
        }

        return TableScan.createTableKVIterator(
            this, key, getOptions, iterateOptions, partitions);
    }

    /**
     * Returns an instance of Put (including PutIf*) if the internal operation
     * is a put.
     *
     * @return null if the operation is not a variant of Put.
     */
    private Put unwrapPut(Operation op) {
        InternalOperation iop = ((OperationImpl)op).getInternalOp();
        return (iop instanceof Put ? (Put) iop : null);
    }

    /**
     * All of the TableOperations can be directly mapped to simple KV operations
     * so do that.
     */
    @Override
    public List<TableOperationResult> execute(List<TableOperation> operations,
                                              WriteOptions writeOptions)
        throws TableOpExecutionException,
               DurabilityException,
               FaultException {

        Result result = executeInternal(operations, writeOptions);
        return createResultsFromExecuteResult(result, operations);
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public Result executeInternal(List<TableOperation> operations,
                                  WriteOptions writeOptions)
        throws TableOpExecutionException,
               DurabilityException,
               FaultException {

        final Table table = ((OpWrapper)operations.get(0)).getTable();
        final List<Operation> kvOperations =
            makeExecuteOps(operations, writeOptions, table);
        final Request req = store.makeExecuteRequest(
            kvOperations,
            ((TableImpl)table).getId(),
            getDurability(writeOptions),
            getTimeout(writeOptions),
            getTimeoutUnit(writeOptions));
        setContextFromOptions(req, writeOptions);
        return processExecuteResult(
            store.executeRequest(req), operations, kvOperations);
    }

    private Result processExecuteResult(Result result,
                                        List<TableOperation> operations,
                                        List<Operation> kvOperations)
        throws TableOpExecutionException {

        try {
            return KVStoreImpl.processExecuteResult(result, kvOperations);
        } catch (OperationExecutionException e) {
            /* Convert this to a TableOpExecutionException */
            int failedOpIndex = e.getFailedOperationIndex();
            PrimaryKey pkey = operations.get(failedOpIndex).getPrimaryKey();
            OperationResult opResult = e.getFailedOperationResult();
            TableOperationResult failedResult =
                    new OpResultWrapper(this, opResult, pkey);

            throw new TableOpExecutionException(operations.get(failedOpIndex),
                                                failedOpIndex,
                                                failedResult,
                                                result.getReadKB(),
                                                result.getWriteKB());
        }
    }

    public List<TableOperationResult>
        createResultsFromExecuteResult(Result result,
                                       List<TableOperation> operations) {

        List<OperationResult> results = result.getExecuteResult();
        List<TableOperationResult> tableResults =
                new ArrayList<TableOperationResult>(results.size());
        int index = 0;
        for (OperationResult opRes : results) {
            PrimaryKey pkey = operations.get(index).getPrimaryKey();
            tableResults.add(new OpResultWrapper(this, opRes, pkey));
            ++index;
        }
        return tableResults;
    }

    private List<Operation> makeExecuteOps(List<TableOperation> operations,
                                           WriteOptions writeOptions,
                                           Table table) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException
                ("operations must be non-null and non-empty");
        }

        ArrayList<Operation> opList =
                new ArrayList<Operation>(operations.size());
        List<String> majorPath = null;
        int index = 0;
        for (TableOperation op : operations) {
            Operation operation = ((OpWrapper)op).getOperation(writeOptions);
            final List<String> mpath =
                ((OperationImpl)operation).getKey().getMajorPath();
            if (majorPath == null) {
                majorPath = mpath;
            } else {
                if (!mpath.equals(majorPath)) {
                    TableOperation lastOp = operations.get(index);
                    String origKey =
                        ((PrimaryKeyImpl) lastOp.getPrimaryKey()).toShardKey();
                    String newKey =
                        ((PrimaryKeyImpl) op.getPrimaryKey()).toShardKey();
                    throw new IllegalArgumentException(
                        "Shard key: " + newKey +
                        " does not match the shard key of " +
                        "previous rows in the list: " + origKey);
                }
                index++; // only used for errors
            }
            opList.add(operation);

            Put putOp = unwrapPut(operation) ;
            if (putOp != null) {
                boolean updateTTL =
                    getUpdateTTL(writeOptions) || op.getUpdateTTL();
                putOp.setTTLOptions(getTTL(((OpWrapper)op).getTTL(), table),
                                    updateTTL);
            }
        }
        return opList;
    }

    private void setContextFromOptions(Request req,
                                       ReadOptions readOptions) {
        if (readOptions != null) {
            req.setLogContext(readOptions.getLogContext());
            req.setAuthContext(readOptions.getAuthContext());
        }
    }

    private void setContextFromOptions(Request req,
                                       WriteOptions writeOptions) {
        if (writeOptions != null) {
            req.setLogContext(writeOptions.getLogContext());
            req.setAuthContext(writeOptions.getAuthContext());
        }
    }

    @Override
    public CompletableFuture<List<TableOperationResult>>
        executeAsync(List<TableOperation> operations,
                     WriteOptions writeOptions)
    {
        return executeAsyncInternal(
            operations, writeOptions,
            result -> createResultsFromExecuteResult(result, operations));
    }

    /**
     * Internal use only
     *
     * Public for use by cloud proxy
     */
    public CompletableFuture<Result> executeAsyncInternal(
        List<TableOperation> operations, WriteOptions writeOptions)
    {
        return executeAsyncInternal(operations, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        executeAsyncInternal(List<TableOperation> operations,
                             WriteOptions writeOptions,
                             Function<Result, R> convertResult) {
        try {
            checkNull("operations", operations);
            final Table table = ((OpWrapper) operations.get(0)).getTable();
            final List<Operation> kvOperations =
                makeExecuteOps(operations, writeOptions, table);
            final Request req = store.makeExecuteRequest(
                kvOperations,
                ((TableImpl)table).getId(),
                getDurability(writeOptions),
                getTimeout(writeOptions),
                getTimeoutUnit(writeOptions));
            setContextFromOptions(req, writeOptions);
            return thenApply(
                store.executeRequestAsync(req),
                checked(result ->
                        convertResult.apply(
                            processExecuteResult(result, operations,
                                                 kvOperations))));
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Creates a Row from the Value with a retry in the case of a
     * TableVersionException.
     *
     * The object passed in is used in-place and returned if all goes well.
     * If there is a TableVersionException a new object is created and
     * returned.
     */
    RowImpl getRowFromValueVersion(ValueVersion vv,
                                   RowImpl row,
                                   long expirationTime,
                                   long modificationTime,
                                   boolean keyOnly) {
        ValueReader<RowImpl> reader = row.initRowReader();
        getRowFromValueVersion(vv, row, expirationTime, modificationTime,
                               keyOnly, reader);
        return reader.getValue();
    }

    void getRowFromValueVersion(ValueVersion vv,
                                RowSerializer row,
                                long expirationTime,
                                long modificationTime,
                                boolean keyOnly,
                                ValueReader<?> reader) {

        final TableImpl table = (TableImpl) row.getTable();
        int requiredVersion = 0;
        assert(reader != null);

        try {
            if (keyOnly) {
                if (row instanceof RowImpl) {
                    ((RowImpl) row).removeValueFields();
                }
            }
            reader.setExpirationTime(expirationTime);
            reader.setModificationTime(modificationTime);
            if (!table.readRowFromValueVersion(reader, vv)) {
                reader.reset();
            }
            return;
        } catch (TableVersionException tve) {
            requiredVersion = tve.getRequiredVersion();
            assert requiredVersion > table.getTableVersion();
            reader.reset();
        }

        /*
         * Gets the required table, create a new row from the existing
         * row and try again.  The fetch will throw if the table and version
         * can't be found.
         */
        final String fullName = table.getFullName();
        TableImpl newTable = getTable(table.getNamespace(),
                                      TableImpl.parseFullName(fullName),
                                      0, requiredVersion, false);
        if (newTable == null) {
                throw new IllegalArgumentException
                            ("Table or version does not exist.  It may have" +
                             " been removed: " + fullName +
                             ", version " + requiredVersion);
        }

        /*
         * Set the version of the table to the original version to ensure that
         * deserialization does the right thing with added and removed fields.
         */
        newTable = (TableImpl)newTable.getVersion(table.getTableVersion());
        RowImpl newRow = newTable.createRow();
        if (reader instanceof RowReaderImpl) {
            RowReaderImpl rr = (RowReaderImpl)reader;
            rr.setValue(newRow);
        }
        newTable.readKeyFields(reader, row);
        reader.setExpirationTime(expirationTime);
        reader.setModificationTime(modificationTime);
        if (!newTable.readRowFromValueVersion(reader, vv)) {
            reader.reset();
        }
    }

    public KVStoreImpl getStore() {
        return store;
    }

    /**
     * The next classes implement mapping of TableOperation and
     * TableOperationFactory to the KVStore Operation and OperationFactory.
     */
    private static class OpWrapper implements TableOperation {
        private Operation op;
        private final TableOperation.Type type;
        private final RowSerializer record;
        private boolean updateTTL;
        private ReturnRow.Choice prevReturn;
        private boolean abortIfUnsuccessful;
        private Version versionMatch;
        private final OperationFactoryImpl factory;
        private final KVStoreImpl store;
        /*
         * The pkey field will be non-null when an OpWrapper is created by a
         * "createPut*Internal" method. The pkey value is needed in those cases
         * because we need a fully populated primary key in order to do the
         * put, and the internal methods don't provide a Row object that can
         * hold a value for a generated identity field. It it not needed in
         * paths where the primary key is fully specified (e.g. delete) or
         * a not-internal put, where the key is populated as a side effect
         * of the operation.
         */
        private final PrimaryKey pkey;

        private OpWrapper(TableOperation.Type type,
                          final RowSerializer record,
                          ReturnRow.Choice prevReturn,
                          boolean abortIfUnsuccessful,
                          Version versionMatch,
                          final OperationFactoryImpl factory,
                          final KVStoreImpl store) {
            this.op = null;
            this.type = type;
            this.record = record;
            this.prevReturn = prevReturn;
            this.abortIfUnsuccessful = abortIfUnsuccessful;
            this.versionMatch = versionMatch;
            this.factory = factory;
            this.store = store;
            this.pkey = null;
        }

        private OpWrapper(TableOperation.Type type,
                          final RowSerializer record,
                          ReturnRow.Choice prevReturn,
                          boolean abortIfUnsuccessful,
                          final OperationFactoryImpl factory,
                          final KVStoreImpl store) {
            this(type, record, prevReturn, abortIfUnsuccessful,
                 null, factory, store);
        }

        private OpWrapper(Operation op,
                          TableOperation.Type type,
                          final RowSerializer record,
                          final PrimaryKey pkey) {
            this.op = op;
            this.type = type;
            this.record = record;
            this.factory = null;
            this.store = null;
            this.pkey = pkey;
        }

        @Override
        public Row getRow() {
            if (record instanceof Row) {
                return (Row)record;
            }
            /* Return null if row is not RowImpl instance */
            return null;
        }

        @Override
        public PrimaryKey getPrimaryKey() {
            if (pkey != null) {
                return pkey;
            }

            if (record instanceof PrimaryKey) {
                return (PrimaryKey) record;
            }

            TableImpl table = (TableImpl)record.getTable();
            PrimaryKeyImpl key = table.createPrimaryKey();
            table.readKeyFields(key.initRowReader(), record);
            return key;
        }

        @Override
        public TableOperation.Type getType() {
            return type;
        }

        @Override
        public boolean getAbortIfUnsuccessful() {
            return op.getAbortIfUnsuccessful();
        }

        /*
         * This method is called when the *Internal methods are not used.
         * It serializes the row and stores any generated identity value
         * in the OpWrapper for later use.
         */
        private Operation getOperation(WriteOptions writeOptions) {
            if (op == null) {
                ReturnValueVersion.Choice choice =
                    ReturnRowImpl.mapChoice(prevReturn);
                TableImpl table = (TableImpl)record.getTable();
                Key key;
                Value value;
                GeneratedValueInfo genInfo =
                    makeGenInfo(record, writeOptions);

                switch(type) {
                case PUT:
                    key = table.createKeyInternal(record, false,
                                                  store, genInfo);
                    value = table.createValueInternal(record, store, genInfo);
                    op = factory.createPut(key, value, choice,
                                           abortIfUnsuccessful,
                                           table.getId());
                    break;
                case PUT_IF_ABSENT:
                    key = table.createKeyInternal(record, false,
                                                  store, genInfo);
                    value = table.createValueInternal(record, store, genInfo);
                    op = factory.createPutIfAbsent(key, value, choice,
                                                   abortIfUnsuccessful,
                                                   table.getId());
                    break;
                case PUT_IF_PRESENT:
                    key = table.createKeyInternal(record, false,
                                                  store, genInfo);
                    value = table.createValueInternal(record, store, genInfo);
                    op = factory.createPutIfPresent(key, value, choice,
                                                    abortIfUnsuccessful,
                                                    table.getId());
                    break;
                case PUT_IF_VERSION:
                    key = table.createKeyInternal(record, false,
                                                  store, genInfo);
                    value = table.createValueInternal(record, store, genInfo);
                    op = factory.createPutIfVersion(key, value,
                                                    versionMatch, choice,
                                                    abortIfUnsuccessful,
                                                    table.getId());
                    break;
                case DELETE:
                    key = table.createKeyInternal(record, false);
                    op = factory.createDelete(key, choice,
                                              abortIfUnsuccessful,
                                              table.getId());
                    break;
                case DELETE_IF_VERSION:
                    key = table.createKeyInternal(record, false);
                    op = factory.createDeleteIfVersion(key, versionMatch,
                                                       choice,
                                                       abortIfUnsuccessful,
                                                       table.getId());
                    break;
                }

            }
            return op;

        }

        @Override
        public void setUpdateTTL(boolean flag) {
            updateTTL = flag;
        }

        @Override
        public boolean getUpdateTTL() {
            return updateTTL;
        }

        TimeToLive getTTL() {
            return record.getTTL();
        }

        Table getTable() {
            return record.getTable();
        }
    }

    /**
     * Public for use by cloud proxy
     */
    public static class OpResultWrapper implements TableOperationResult {
        private final TableAPIImpl impl;
        private final OperationResult opRes;
        private final PrimaryKey key;

        private OpResultWrapper(TableAPIImpl impl,
                                OperationResult opRes, PrimaryKey key) {
            this.impl = impl;
            this.opRes = opRes;
            this.key = key;
        }

        @Override
        public Version getNewVersion() {
            return opRes.getNewVersion();
        }

        @Override
        public Row getPreviousRow() {
            ValueReader<RowImpl> reader =
                ((TableImpl)key.getTable()).createRow().initRowReader();
            return getPreviousRow(reader) ? reader.getValue() : null;
        }

        @Override
        public Version getPreviousVersion() {
            return opRes.getPreviousVersion();
        }

        @Override
        public boolean getSuccess() {
            return opRes.getSuccess();
        }

        @Override
        public long getPreviousExpirationTime() {
            return opRes.getPreviousExpirationTime();
        }

        public boolean getPreviousRow(ValueReader<?> reader) {
            Value value = opRes.getPreviousValue();
            /*
             * Put Version in the Row if it's available.
             */
            Version version = opRes.getPreviousVersion();
            if (value != null && key != null) {
                PrimaryKeyImpl rowKey = (PrimaryKeyImpl)key;
                ((TableImpl)key.getTable()).readKeyFields(reader, rowKey);
                impl.getRowFromValueVersion
                    (new ValueVersion(value, version),
                     rowKey,
                     opRes.getPreviousExpirationTime(),
                     opRes.getPreviousModificationTime(),
                     false,
                     reader);
                return true;
            }
            return false;
        }
    }

    /**
     * Public for use by cloud proxy
     */
    public static class OpFactory implements TableOperationFactory {
        private final OperationFactoryImpl factory;
        private final KVStoreImpl store;

        private OpFactory(final OperationFactoryImpl factory,
                          final KVStoreImpl store) {
            this.factory = factory;
            this.store = store;
        }

        @Override
        public TableOperation createPut(Row rowArg,
                                        ReturnRow.Choice prevReturn,
                                        boolean abortIfUnsuccessful) {

            final RowImpl row = (RowImpl) rowArg;
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            return new OpWrapper(Type.PUT,
                                 row,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 factory,
                                 store);
        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createPutInternal
            (RowSerializer row,
             ReturnRow.Choice prevReturn,
             GeneratedValueInfo genInfo,
             boolean abortIfUnsuccessful) {

            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)row.getTable();

            Key key = table.createKeyInternal(row, false, store, genInfo);
            Value value = table.createValueInternal(row, store, genInfo);
            Operation op = factory.createPut(key, value, choice,
                                             abortIfUnsuccessful,
                                             table.getId());
            PrimaryKey pkey =
                table.createPrimaryKeyFromKeyBytes(key.toByteArray());
            return new OpWrapper(op, TableOperation.Type.PUT, row, pkey);
        }

        @Override
        public TableOperation createPutIfAbsent(Row rowArg,
                                                ReturnRow.Choice prevReturn,
                                                boolean abortIfUnsuccessful) {
            final RowImpl row = (RowImpl) rowArg;
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            return new OpWrapper(Type.PUT_IF_ABSENT,
                                 row,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 factory,
                                 store);
        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createPutIfAbsentInternal
            (RowSerializer row,
             ReturnRow.Choice prevReturn,
             GeneratedValueInfo genInfo,
             boolean abortIfUnsuccessful) {

            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)row.getTable();
            Key key = table.createKeyInternal(row, false, store, genInfo);
            Value value = table.createValueInternal(row, store, genInfo);
            Operation op = factory.createPutIfAbsent(key, value, choice,
                                                     abortIfUnsuccessful,
                                                     table.getId());
            PrimaryKey pkey =
                table.createPrimaryKeyFromKeyBytes(key.toByteArray());
            return new OpWrapper(op, TableOperation.Type.PUT_IF_ABSENT,
                                 row, pkey);
        }

        @Override
        public TableOperation createPutIfPresent(Row rowArg,
                                                 ReturnRow.Choice prevReturn,
                                                 boolean abortIfUnsuccessful) {

            final RowImpl row = (RowImpl) rowArg;
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            return new OpWrapper(Type.PUT_IF_PRESENT,
                                 row,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 factory,
                                 store);
        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createPutIfPresentInternal
            (RowSerializer row,
             ReturnRow.Choice prevReturn,
             GeneratedValueInfo genInfo,
             boolean abortIfUnsuccessful) {

            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)row.getTable();
            Key key = table.createKeyInternal(row, false, store, genInfo);
            Value value = table.createValueInternal(row, store, genInfo);
            Operation op = factory.createPutIfPresent(key, value, choice,
                                                     abortIfUnsuccessful,
                                                     table.getId());
            PrimaryKey pkey =
                table.createPrimaryKeyFromKeyBytes(key.toByteArray());
            return new OpWrapper(op, TableOperation.Type.PUT_IF_PRESENT,
                                 row, pkey);
        }

        @Override
        public TableOperation createPutIfVersion(Row rowArg,
                                                 Version versionMatch,
                                                 ReturnRow.Choice prevReturn,
                                                 boolean abortIfUnsuccessful) {

            final RowImpl row = (RowImpl) rowArg;
            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            return new OpWrapper(Type.PUT_IF_VERSION,
                                 row,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 versionMatch,
                                 factory,
                                 store);

        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createPutIfVersionInternal
            (RowSerializer row,
             Version versionMatch,
             ReturnRow.Choice prevReturn,
             GeneratedValueInfo genInfo,
             boolean abortIfUnsuccessful) {

            if (row.isFromMRTable()) {
                setLocalRegionId(row);
            }
            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)row.getTable();
            Key key = table.createKeyInternal(row, false, store, genInfo);
            Value value = table.createValueInternal(row, store, genInfo);
            Operation op = factory.createPutIfVersion(key, value,
                                                      versionMatch, choice,
                                                      abortIfUnsuccessful,
                                                      table.getId());
            PrimaryKey pkey =
                table.createPrimaryKeyFromKeyBytes(key.toByteArray());
            return new OpWrapper(op, TableOperation.Type.PUT_IF_VERSION,
                                 row, pkey);
        }

        @Override
        public TableOperation createDelete
            (PrimaryKey keyArg,
             ReturnRow.Choice prevReturn,
             boolean abortIfUnsuccessful) {

            return new OpWrapper(Type.DELETE,
                                 (PrimaryKeyImpl) keyArg,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 factory,
                                 store);
        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createDeleteInternal(RowSerializer rowKey,
                                                   ReturnRow.Choice prevReturn,
                                                   boolean abortIfUnsuccessful) {
            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)rowKey.getTable();
            Key key = table.createKeyInternal(rowKey, false);
            Operation op = factory.createDelete(key, choice,
                                                abortIfUnsuccessful,
                                                table.getId());
            return new OpWrapper(op, TableOperation.Type.DELETE, rowKey, null);
        }

        @Override
        public TableOperation createDeleteIfVersion
            (PrimaryKey keyArg,
             Version versionMatch,
             ReturnRow.Choice prevReturn,
             boolean abortIfUnsuccessful) {

            return new OpWrapper(Type.DELETE_IF_VERSION,
                                 (PrimaryKeyImpl) keyArg,
                                 prevReturn,
                                 abortIfUnsuccessful,
                                 versionMatch,
                                 factory,
                                 store);

        }

        /**
         * Public for use by cloud proxy
         */
        public TableOperation createDeleteIfVersionInternal
            (RowSerializer rowKey,
             Version versionMatch,
             ReturnRow.Choice prevReturn,
             boolean abortIfUnsuccessful) {

            ReturnValueVersion.Choice choice =
                ReturnRowImpl.mapChoice(prevReturn);
            TableImpl table = (TableImpl)rowKey.getTable();
            Key key = table.createKeyInternal(rowKey, false);
            Operation op = factory.createDeleteIfVersion(key, versionMatch,
                                                         choice,
                                                         abortIfUnsuccessful,
                                                         table.getId());
            return new OpWrapper
                (op, TableOperation.Type.DELETE_IF_VERSION, rowKey, null);
        }
    }

    /**
     * Puts the row if it wins the conflict resolution in
     * {@code ConflictResolver}. Now only {@code LastWriteWinResolver} is
     * available.
     *
     * @param rowArg            row to put
     * @param prevRowArg        previous row value and version
     * @param writeOptions      write options
     *
     * @return true if the row is persisted after winning the conflict
     * resolution, false otherwise
     */
    public boolean putResolve(Row rowArg,
                              ReturnRow prevRowArg,
                              WriteOptions writeOptions)
        throws FaultException {
        return putDelResolveInternal((RowImpl)rowArg, prevRowArg, writeOptions)
            != null;
    }

    public Version putDelResolveInternal(RowImpl row,
                                         ReturnRow prevRowArg,
                                         WriteOptions writeOptions) {
        final Result result =
            putResolveInternal(row, prevRowArg, writeOptions);
        return processPutResult(result, row, prevRowArg);
    }

    /** For internal use only. Public for use by the cloud proxy. */
    public Result putResolveInternal(RowSerializer row,
                                     ReturnRow prevRowArg,
                                     WriteOptions writeOptions)
        throws FaultException {

        /*
         * Identity columns are not currently supported for multi-region
         * tables.
         */
        return store.executeRequest(
            makePutDelResolveRequest(row, prevRowArg, writeOptions));
    }

    /**
     * Puts a row into a table if it wins the conflict resolution, returning a
     * future to manage the asynchronous operation.
     *
     * @param row the row to put
     * @param prevRow a {@code ReturnRow} object to contain the previous row
     * value and version associated with the given row, or {@code null} if they
     * should not be returned
     * @param writeOptions non-default arguments controlling the durability of
     * the operation, or {@code null} to get default behavior
     * @return a future for managing the asynchronous operation
     * @since 19.5
     */
    public CompletableFuture<Boolean>
        putResolveAsync(Row row,
                        ReturnRow prevRow,
                        WriteOptions writeOptions) {
        try {
            final RowImpl rowImpl = (RowImpl) row;
            return putResolveAsyncInternal(
                rowImpl, prevRow, writeOptions,
                result -> processPutResult(result, rowImpl, prevRow) != null);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /** For internal use only. Public for use by the cloud proxy. */
    public CompletableFuture<Result> putResolveAsyncInternal(
        RowSerializer row, ReturnRow prevRow, WriteOptions writeOptions) {

        return putResolveAsyncInternal(row, prevRow, writeOptions, r -> r);
    }

    private <R> CompletableFuture<R>
        putResolveAsyncInternal(RowSerializer row,
                                ReturnRow prevRow,
                                WriteOptions writeOptions,
                                Function<Result, R> convertResult) {
        try {
            checkNull("row", row);
            /*
             * Identity columns are not currently supported for multi-region
             * tables.
             */
            return thenApply(
                store.executeRequestAsync(makePutDelResolveRequest(
                                              row, prevRow, writeOptions)),
                convertResult::apply);
        } catch (Throwable e) {
            return failedFuture(e);
        }
    }

    /**
     * Deletes the primary key if it wins the default conflict resolution. If
     * the delete wins the default conflict resolution, regardless the key
     * exists in the store or not, a tombstone is created or updated for the
     * deletion. If the deletion fails the default conflict resolution, it
     * returns false and there is no side-effect.
     *
     * @param keyArg        primary key to delete
     * @param prevRowArg        previous row value and version
     * @param writeOptions  write options
     *
     * @return true if the delete wins the default conflict, false otherwise.
     */
    public boolean deleteResolve(PrimaryKey keyArg,
                                 ReturnRow prevRowArg,
                                 WriteOptions writeOptions)
        throws FaultException {
        return putDelResolveInternal((RowImpl)keyArg, prevRowArg, writeOptions)
            != null;
    }

    /** For internal use only. Public for use by the cloud proxy. */
    public Result deleteResolveInternal(RowSerializer rowKey,
                                        ReturnRow prevRowArg,
                                        WriteOptions writeOptions) {
        return putResolveInternal(rowKey, prevRowArg, writeOptions);
    }

    /**
     * Deletes a row from a table if it wins the conflict resolution, returning
     * a future to manage the asynchronous operation.
     *
     * @param key the primary key for the row to delete
     * @param prevRow a {@code ReturnRow} object to contain the previous row
     * value and version associated with the given row, or {@code null} if they
     * should not be returned
     * @param writeOptions non-default arguments controlling the durability of
     * the operation, or {@code null} to get default behavior
     * @return a future for managing the asynchronous operation
     * @since 19.5
     */
    public CompletableFuture<Boolean> deleteResolveAsync(
        PrimaryKey key, ReturnRow prevRow, WriteOptions writeOptions) {

        return putResolveAsync(key, prevRow, writeOptions);
    }

    private Request makePutDelResolveRequest(RowSerializer row,
                                             ReturnRow prevRow,
                                             WriteOptions writeOptions) {
        final TableImpl table = (TableImpl) row.getTable();
        final Key key = table.createKeyInternal(row, false, store, null);
        final Value value;
        if (row.isPrimaryKey()) {
            value = Value.createTombstoneValue(row.getRegionId());
        } else {
            value = table.createValueInternal(row, store, null);
        }
        final Request req =
        store.makePutResolveRequest(key, value, table.getId(),
                                        getReturnChoice(prevRow),
                                        getDurability(writeOptions),
                                        getTimeout(writeOptions),
                                        getTimeoutUnit(writeOptions),
                                        row.isPrimaryKey() ?
                                        0 /* unused in tombstone */ :
                                        ((RowImpl) row).getExpirationTime(),
                                        getUpdateTTL(writeOptions),
                                        /* delete if row is primary key */
                                        row.isPrimaryKey(),
                                        /* pass key metadata */
                                        row.getLastModificationTime());
        setContextFromOptions(req, writeOptions);
        return req;
    }

    /************* end runtime methods **************/

    /*
     * Internal utilities
     */

    private ReturnValueVersion makeRVV(ReturnRow rr) {
        if (rr != null) {
            return ((ReturnRowImpl)rr).makeReturnValueVersion();
        }
        return null;
    }

    /**
     * Add expiration time to current and prior row
     * @param rr prior row
     * @param row current row
     * @param result the result of put or delete
     * @param reader the specified ValueReader used in deserialization.
     */
    private void initReturnRow(ReturnRow rr,
                               RowSerializer row,
                               Result result,
                               ValueReader<?> reader) {
        if (rr != null) {
            ReturnValueVersion rvv = makeRVV(rr);
            rvv.setValue(result.getPreviousValue());
            rvv.setVersion(result.getPreviousVersion());

            ValueReader<?> rowReader =
                (reader != null) ? reader : ((RowImpl)rr).initRowReader();
            ((ReturnRowImpl)rr).init(this, rvv, row,
                result.getPreviousExpirationTime(),
                result.getPreviousModificationTime(),
                rowReader);
        }
    }

    static KeyRange makeKeyRange(TableKey key, MultiRowOptions getOptions) {
        if (getOptions != null) {
            FieldRange range = getOptions.getFieldRange();
            if (range != null) {
                if (key.getKeyComplete()) {
                    throw new IllegalArgumentException
                        ("Cannot specify a FieldRange with a complete " +
                         "primary key");
                }
                key.validateFieldOrder(range);
                return createKeyRange(range);
            }
        } else {
            key.validateFields();
        }
        return null;
    }

    public static KeyRange createKeyRange(FieldRange range) {
        return createKeyRange(range, false);
    }

    public static KeyRange createKeyRange(FieldRange range, boolean forQuery) {

        if (range == null) {
            return null;
        }

        String start = null;
        String end = null;
        boolean startInclusive = true;
        boolean endInclusive = true;
        FieldDefImpl def = (FieldDefImpl)range.getDefinition();

        if (range.getStart() != null) {
            start = ((FieldValueImpl)range.getStart()).
                    formatForKey(def, range.getStorageSize());
            startInclusive = range.getStartInclusive();
        }

        if (range.getEnd() != null) {
            end = ((FieldValueImpl)range.getEnd()).
                  formatForKey(def, range.getStorageSize());
            endInclusive = range.getEndInclusive();
        }

        if (forQuery) {
            return new QueryKeyRange(start, startInclusive, end, endInclusive);
        }

        return new KeyRange(start, startInclusive, end, endInclusive);
    }

    /**
     * Turn a List<ResultKey> of keys into List<PrimaryKey>
     */
    private List<PrimaryKey>
        processMultiResults(PrimaryKey rowKey,
                            MultiRowOptions getOptions,
                            List<ResultKey> keys) {
        final List<PrimaryKey> list = new ArrayList<PrimaryKey>(keys.size());
        final boolean hasAncestorTables = (getOptions != null) &&
            (getOptions.getIncludedParentTables() != null);
        TableImpl t = (TableImpl) rowKey.getTable();
        if (hasAncestorTables) {
            t = t.getTopLevelTable();
        }
        for (ResultKey key : keys) {
            PrimaryKeyImpl pk = t.createPrimaryKeyFromResultKey(key);
            if (pk != null) {
                list.add(pk);
            }
        }
        return list;
    }

    /**
     * Turn a List<ResultKeyValueVersion> of results into List<Row>
     */
    private List<Row>
        processMultiResults(PrimaryKey rowKey,
                            MultiRowOptions getOptions,
                            Result result) {
        final List<ResultKeyValueVersion> resultList =
            result.getKeyValueVersionList();
        final List<Row> list = new ArrayList<Row>(resultList.size());
        final boolean hasAncestorTables = (getOptions != null) &&
            (getOptions.getIncludedParentTables() != null);
        TableImpl t = (TableImpl) rowKey.getTable();
        if (hasAncestorTables) {
            t = t.getTopLevelTable();
        }

        for (ResultKeyValueVersion rkvv : result.getKeyValueVersionList()) {
            RowImpl row = t.createRowFromKeyBytes(rkvv.getKeyBytes());
            if (row != null) {
                ValueVersion vv = new ValueVersion(rkvv.getValue(),
                                                   rkvv.getVersion());
                list.add(getRowFromValueVersion(vv,
                                                row,
                                                rkvv.getExpirationTime(),
                                                rkvv.getModificationTime(),
                                                false));
            }
        }
        return list;
    }

    /**
     * Validate the ancestor and child tables, if set against the target table.
     */
    static void validateMultiRowOptions(MultiRowOptions mro,
                                        Table targetTable,
                                        boolean isIndex) {
        if (mro.getIncludedParentTables() != null) {
            for (Table t : mro.getIncludedParentTables()) {
                if (!((TableImpl)targetTable).isAncestor(t)) {
                    throw new IllegalArgumentException
                        ("Ancestor table \"" + t.getFullName() + "\" is not " +
                         "an ancestor of target table \"" +
                         targetTable.getFullName() + "\"");
                }
            }
        }
        if (mro.getIncludedChildTables() != null) {
            if (isIndex) {
                throw new UnsupportedOperationException
                    ("Child table returns are not supported for index " +
                     "scan operations");
            }
            for (Table t : mro.getIncludedChildTables()) {
                if (!((TableImpl)t).isAncestor(targetTable)) {
                    throw new IllegalArgumentException
                        ("Child table \"" + t.getFullName() + "\" is not a " +
                         "descendant of target table \"" +
                         targetTable.getFullName() + "\"");
                }

            }
        }
    }

    public static Consistency getConsistency(ReadOptions opts) {
        return (opts != null ? opts.getConsistency() : null);
    }

    public static long getTimeout(ReadOptions opts) {
        return (opts != null ? opts.getTimeout() : 0);
    }

    public static TimeUnit getTimeoutUnit(ReadOptions opts) {
        return (opts != null ? opts.getTimeoutUnit() : null);
    }

    static Direction getDirection(TableIteratorOptions opts,
                                  TableKey key) {
        if (opts == null) {
           return key.getMajorKeyComplete() ? Direction.FORWARD :
                                              Direction.UNORDERED;
        }
        return opts.getDirection();
    }

    public static int getBatchSize(TableIteratorOptions opts) {
        return ((opts != null && opts.getResultsBatchSize() != 0) ?
                opts.getResultsBatchSize():
                (opts != null && opts.getMaxReadKB() == 0 ?
                 KVStoreImpl.DEFAULT_ITERATOR_BATCH_SIZE : 0));
    }

    public static int getMaxReadKB(TableIteratorOptions opts) {
        return ((opts != null && opts.getMaxReadKB() != 0) ?
                opts.getMaxReadKB(): 0);
    }

    static Durability getDurability(WriteOptions opts) {
        return (opts != null ? opts.getDurability() : null);
    }

    static long getTimeout(WriteOptions opts) {
        return (opts != null ? opts.getTimeout() : 0);
    }

    static TimeUnit getTimeoutUnit(WriteOptions opts) {
        return (opts != null ? opts.getTimeoutUnit() : null);
    }

    static public TimeToLive getTTL(RowImpl row, Table table) {
        TimeToLive ttl = row.getTTLAndClearExpiration();
        return getTTL(ttl, table);
    }

    /**
     * Gets the TTL to use. If the specified ttl is not-null then that is
     * returned otherwise the table default TTL is returned.
     */
    private static TimeToLive getTTL(TimeToLive ttl, Table table) {
        return ttl != null ? ttl : table.getDefaultTTL();
    }

    static boolean getUpdateTTL(WriteOptions opts) {
        return opts != null ? opts.getUpdateTTL() : false;
    }

    static int getMaxWriteKB(WriteOptions opts) {
        return ((opts != null && opts.getMaxWriteKB() != 0) ?
               opts.getMaxWriteKB(): 0);
    }

    static TargetTables makeTargetTables(Table target,
                                         MultiRowOptions getOptions) {
        List<Table> childTables =
            getOptions != null ? getOptions.getIncludedChildTables() : null;
        List<Table> ancestorTables =
            getOptions != null ? getOptions.getIncludedParentTables() : null;

        return new TargetTables(target, childTables, ancestorTables);
    }

    public TableMetadataHelper getTableMetadataHelper() {
        if (metadataHelper != null) {
            return metadataHelper;
        }
        return new MetadataHelper(this);
    }

    /*
     * Implementation of TableMetadataHelper for use in the client. Clients
     * should only call the single getTable() interface. This keeps
     * TableMetadata as a monolithic object out of the client.
     */
    private static class MetadataHelper implements TableMetadataHelper {

        private final TableAPIImpl tableAPI;

        MetadataHelper(TableAPIImpl tableAPI) {
            this.tableAPI = tableAPI;
        }

        @Override
        public TableImpl getTable(String namespace, String tableName) {
            return (TableImpl) tableAPI.getTable(namespace, tableName, 0);
        }

        /*
         * This algorithm assumes that the top-level table has all of its
         * hierarchy in one piece to allow traversal into child tables.
         */
        @Override
        public TableImpl getTable(String namespace,
                                  String[] tablePath,
                                  int cost) {
            return tableAPI.getTable(namespace, tablePath, cost, 0, false);
        }

        @Override
        public RegionMapper getRegionMapper() {
            return tableAPI.getRegionMapper();
        }
    }

    /**
     * The MetadataCallback handler, it can be registered using
     * {@link #setTableMetadataCallback} method.
     *
     * The {@link TableMetadataCallback#metadataChanged} will be invoked when
     * it is detected that the table metadata has been changed.
     */
    public interface TableMetadataCallback {
        /**
         * The method is invoked after detected that the table metadata has
         * been changed, it should not block and do minimal processing,
         * delegating any blocking or time-consuming operations to a separate
         * thread and return back to the caller.
         *
         * @param oldSeqNum the old table metadata sequence number.
         * @param newSeqNum the new table metadata sequence number.
         */
        void metadataChanged(int oldSeqNum, int newSeqNum);
    }

    /**
     * Holds information about values generated by the system, which include:
     * 1) Identity columns
     * 2) Generated UUIDs
     * A given table can only have one of the above.
     */
    public static class GeneratedValueInfo {
        /* may need to be made volatile when used in async api */
        int cacheSize; // only applies to Identity columns
        FieldValueImpl generatedValue;

        public GeneratedValueInfo() {}

        public GeneratedValueInfo(int cacheSize) {
            this.cacheSize = cacheSize;
            generatedValue = null;
        }

        public void setCacheSize(int size) {
            cacheSize = size;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public FieldValueImpl getGeneratedValue() {
            return generatedValue;
        }

        public void setGeneratedValue(FieldValueImpl value) {
            generatedValue = value;
        }
    }
}
