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

package oracle.kv.query;

import static oracle.kv.impl.api.table.TableImpl.validateNamespace;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.TimeUnit;

import oracle.kv.Consistency;
import oracle.kv.Durability;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.table.GeometryUtils;
import oracle.kv.impl.security.AuthContext;
import oracle.kv.impl.util.contextlogger.LogContext;
import oracle.kv.table.TableIteratorOptions;

/**
 * Class contains several options for the execution of an SQL statement
 * (DDL or DML). SQL statements are executed via one of the
 * KVStore.execute() methods.
 *
 * @see KVStore#execute(String, ExecuteOptions)
 * @see KVStore#executeSync(String, ExecuteOptions)
 * @see KVStore#executeSync(Statement, ExecuteOptions)
 *
 * @since 4.0
 */
public class ExecuteOptions {

    public static short DRIVER_QUERY_V2 = 2;

    public static short DRIVER_QUERY_V3 = 3;

    private Consistency consistency;

    private Durability durability;

    private long timeout;

    private TimeUnit timeoutUnit;

    private int maxConcurrentRequests;

    private int resultsBatchSize;

    private byte traceLevel;

    private MathContext mathContext = MathContext.DECIMAL32;

    /* added in 4.4 */
    private String namespace;

    private PrepareCallback prepareCallback;

    /* added in 18.1 */
    private int maxReadKB;

    /* added in 19.1 */
    private int maxWriteKB;

    private byte[] continuationKey;
    /*
     * The flag indicates if use resultsBatchSize as the limit to the number of
     * results returned, the resultsBatchSize can be configured with
     * {@link #setResultsBatchSize}
     */
    private boolean useBatchSizeAsLimit;

    private AuthContext authContext = null;

    private LogContext logContext = null;

    private boolean doPrefetching = true;

    private long maxMemoryConsumption = 100 * 1024 * 1024;

    private int geoMaxCoveringCells = GeometryUtils.theMaxCoveringCellsForSearch;

    private int geoMinCoveringCells = GeometryUtils.theMinCoveringCellsForSearch;

    private int geoMaxRanges = GeometryUtils.theMaxRanges;

    private double geoSplitRatio = GeometryUtils.theSplitRatio;

    private int geoMaxSplits = GeometryUtils.theMaxSplits;

    /* For SQL DELETE, this is the max number of rows to delete per RN batch */
    private int deleteLimit = 1000;

    /* added in 18.3 */
    private boolean validateNamespace = true;

    /*
     * Should be set to true for proxy-based queries and false for queries
     * submitted via the direct driver (fat java client).
     */
    private boolean isProxyQuery;

    /*
     * If proxy-based query, this is the version of the driver-proxy protocol for
     * query
     */
    private int driverQueryVersion;

    public ExecuteOptions() {}

    /**
     * @hidden
     */
    public ExecuteOptions(TableIteratorOptions options) {

        if (options != null) {
            maxConcurrentRequests = options.getMaxConcurrentRequests();
            resultsBatchSize = options.getResultsBatchSize();
            maxReadKB = options.getMaxReadKB();
            consistency = options.getConsistency();
            timeout = options.getTimeout();
            timeoutUnit = options.getTimeoutUnit();
            logContext = options.getLogContext();
            authContext = options.getAuthContext();
        }
    }


    /**
     * Sets the execution consistency.
     */
    public ExecuteOptions setConsistency(Consistency consistency) {
        this.consistency = consistency;
        return this;
    }

    /**
     * Gets the last set execution consistency.
     */
    public Consistency getConsistency() {
        return consistency;
    }

    /**
     * Sets the execution durability.
     */
    public ExecuteOptions setDurability(Durability durability) {
        this.durability = durability;
        return this;
    }

    /**
     * Gets the last set execution durability.
     */
    public Durability getDurability() {
        return durability;
    }

    /**
     * The {@code timeout} parameter is an upper bound on the time interval for
     * processing the operation.  A best effort is made not to exceed the
     * specified limit. If zero, the {@link KVStoreConfig#getRequestTimeout
     * default request timeout} is used.
     * <p>
     * If {@code timeout} is not 0, the {@code timeoutUnit} parameter must not
     * be {@code null}.
     *
     * @param timeout the timeout value to use
     * @param timeoutUnit the {@link TimeUnit} used by the
     * <code>timeout</code> parameter or null
     */
    public ExecuteOptions setTimeout(long timeout,
                                     TimeUnit timeoutUnit) {

        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        if ((timeout != 0) && (timeoutUnit == null)) {
            throw new IllegalArgumentException("A non-zero timeout requires " +
                "a non-null timeout unit");
        }

        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
        return this;
    }

    /**
     * Gets the timeout, which is an upper bound on the time interval for
     * processing the read or write operations.  A best effort is made not to
     * exceed the specified limit. If zero, the
     * {@link KVStoreConfig#getRequestTimeout default request timeout} is used.
     *
     * @return the timeout
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Gets the unit of the timeout parameter, and may
     * be {@code null} only if {@link #getTimeout} returns zero.
     *
     * @return the timeout unit or null
     */
    public TimeUnit getTimeoutUnit() {
        return timeoutUnit;
    }

    /**
     * Returns the maximum number of concurrent requests.
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    /**
     * Sets the maximum number of concurrent requests.
     */
    public ExecuteOptions setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
        return this;
    }

    /**
     * Returns the number of results per request.
     */
    public int getResultsBatchSize() {
        return (resultsBatchSize > 0 ?
                resultsBatchSize :
                getMaxReadKB() == 0 ?
                KVStoreImpl.DEFAULT_ITERATOR_BATCH_SIZE :
                KVStoreImpl.DEFAULT_BATCH_SIZE_FOR_PROXY_QUERIES);
    }

    /**
     * Sets the number of results per request.
     */
    public ExecuteOptions setResultsBatchSize(int resultsBatchSize) {

        if (resultsBatchSize < 0) {
            throw new IllegalArgumentException(
                "The batch size can not be a negative value: " +
                resultsBatchSize);
        }
        this.resultsBatchSize = resultsBatchSize;
        return this;
    }

    /**
     * Returns the {@link MathContext} used for {@link BigDecimal} and
     * {@link BigInteger} operations. {@link MathContext#DECIMAL32} is used by
     * default.
     */
    public MathContext getMathContext() {
        return mathContext;
    }

    /**
     * Sets the {@link MathContext} used for {@link BigDecimal} and
     * {@link BigInteger} operations. {@link MathContext#DECIMAL32} is used by
     * default.
     */
    public ExecuteOptions setMathContext(MathContext mathContext) {

        if (mathContext != null) {
            this.mathContext = mathContext;
        }
        return this;
    }

    /**
     * Returns the trace level for a query
     * @hidden
     */
    public byte getTraceLevel() {
        return traceLevel;
    }

    /**
     * Sets the trace level for a query
     * @hidden
     */
    public ExecuteOptions setTraceLevel(byte level) {
        this.traceLevel = level;
        return this;
    }

    /**
     * Sets the namespace to use for the query. Query specified namespace
     * takes precedence, else this namespace value is used for unqualified
     * table names.
     *
     * @since 18.3
     */
    public ExecuteOptions setNamespace(String namespace) {
        return setNamespace(namespace, true);
    }

    /**
     * For internal use only.
     * @hidden
     *
     * Sets the namespace to use for the query. This method is semantically
     * equivalent to {@link #setNamespace(String)} when validate flag is true.
     * The validate = false option should be used in the cloud implementation.
     *
     * @since 18.3
     */
    public ExecuteOptions setNamespace(String namespace, boolean validate) {
        if (namespace != null && validate) {
            validateNamespace(namespace);
        }

        this.namespace = namespace;
        this.validateNamespace = validate;

        return this;
    }

    /**
     * Returns the namespace to use for the query, null if not set.
     *
     * @since 18.3
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @hidden
     * Sets the PrepareCallback to use for the query.
     *
     * @param prepareCallback an instance of PrepareCallback
     *
     * @since 18.1
     */
    public ExecuteOptions setPrepareCallback(PrepareCallback prepareCallback) {
        this.prepareCallback = prepareCallback;
        return this;
    }

    /**
     * @hidden
     * Returns the PrepareCallback set, or null if not set.
     *
     * @since 18.1
     */
    public PrepareCallback getPrepareCallback() {
        return prepareCallback;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Sets the max number of KBytes read during a query.
     *
     * @since 18.1
     */
    public ExecuteOptions setMaxReadKB(int maxReadKB) {
        if (maxReadKB < 0) {
            throw new IllegalArgumentException("The max read KB can not " +
                "be a negative value: " + maxReadKB);
        }
        this.maxReadKB = maxReadKB;
        return this;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Returns the max number of KBytes that may be read during a query.
     *
     * @since 19.2
     */
    public int getMaxReadKB() {
        return maxReadKB;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Sets the max number of KBytes that may be written during a query.
     *
     * @since 19.2
     */
    public ExecuteOptions setMaxWriteKB(int maxWriteKB) {
        if (maxWriteKB < 0) {
            throw new IllegalArgumentException("The max read KB can not " +
                "be a negative value: " + maxWriteKB);
        }
        this.maxWriteKB = maxWriteKB;
        return this;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Returns the max number of KBytes that may be written during a query.
     *
     * @since 18.1
     */
    public int getMaxWriteKB() {
        return maxWriteKB;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Sets if use resultsBatchSize as the limit to the number of results
     * returned, the resultsBatchSize can be configured with
     * {@link #setResultsBatchSize}
     *
     * @since 18.1
     */
    public ExecuteOptions setUseBatchSizeAsLimit(boolean value) {
        useBatchSizeAsLimit = value;
        return this;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Returns true if use resultsBatchSize as the limit to the number of
     * results returned.
     *
     * @since 18.1
     */
    public boolean getUseBatchSizeAsLimit() {
        return useBatchSizeAsLimit;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Sets the continuation Key from which the query resumed.
     *
     * @since 18.1
     */
    public ExecuteOptions setContinuationKey(byte[] continuationKey) {
        this.continuationKey = continuationKey;
        return this;
    }

    /**
     * Used for http proxy only.
     * @hidden
     *
     * Returns the continuation Key specified.
     *
     * @since 18.1
     */
    public byte[] getContinuationKey() {
        return continuationKey;
    }

    /**
     * For internal use only.
     * @hidden
     *
     * Sets the LogContext to use for the query.
     *
     * @since 18.1
     */
    public ExecuteOptions setLogContext(LogContext lc) {
        this.logContext = lc;
        return this;
    }

    /**
     * For internal use only.
     * @hidden
     *
     * Returns the LogContext to use for the query, null if not set.
     *
     * @since 18.1
     */
    public LogContext getLogContext() {
        return logContext;
    }

    /**
     * For internal use only.
     * @hidden
     */
    public void setDoPrefetching(boolean v) {
        doPrefetching = v;
    }

    /**
     * For internal use only.
     * @hidden
     */
    public boolean getDoPrefetching() {
        return doPrefetching;
    }

    /**
     * Set the maximum number of memory bytes that may be consumed by the
     * statement at the client for blocking operations, such as duplicate
     * elimination (which may be required due to the use of an index on an
     * array or map) and sorting (sorting by distance when a query contains
     * a geo_near() function). Such operations may consume a lot of memory
     * as they need to cache the full result set at the client memory.
     * The default value is 100MB.
     *
     * @since 18.3
     */
    public void setMaxMemoryConsumption(long v) {
        maxMemoryConsumption = v;
    }

    /**
     * Get the maximum number of memory bytes that may be consumed by the
     * statement at the client for blocking operations, such as duplicate
     * elimination (which may be required due to the use of an index on an
     * array or map) and sorting (sorting by distance when a query contains
     * a geo_near() function). Such operations may consume a lot of memory
     * as they need to cache the full result set at the client memory.
     * The default value is 100MB.
     */
    public long getMaxMemoryConsumption() {
        return maxMemoryConsumption;
    }

    /**
     * @hidden
     */
    public int getGeoMaxCoveringCells() {
        return geoMaxCoveringCells;
    }

    /**
     * @hidden
     */
    public void setGeoMaxCoveringCells(int v) {
        geoMaxCoveringCells = v;
    }

    /**
     * @hidden
     */
    public int getGeoMinCoveringCells() {
        return geoMinCoveringCells;
    }

    /**
     * @hidden
     */
    public void setGeoMinCoveringCells(int v) {
        geoMinCoveringCells = v;
    }

    /**
     * @hidden
     */
    public int getGeoMaxRanges() {
        return geoMaxRanges;
    }

    /**
     * @hidden
     */
    public void setGeoMaxRanges(int v) {
        geoMaxRanges = v;
    }

    /**
     * @hidden
     */
    public int getGeoMaxSplits() {
        return geoMaxSplits;
    }

    /**
     * @hidden
     */
    public void setGeoMaxSplits(int v) {
        geoMaxSplits = v;
    }

    /**
     * @hidden
     */
    public double getGeoSplitRatio() {
        return geoSplitRatio;
    }

    /**
     * @hidden
     */
    public void setGeoSplitRatio(double v) {
        geoSplitRatio = v;
    }

    /**
     * @hidden
     */
    public void setDeleteLimit(int v) {
        deleteLimit = v;
    }

    /**
     * @hidden
     */
    public int getDeleteLimit() {
        return deleteLimit;
    }

    /**
     * @hidden
     */
    public boolean isValidateNamespace() {
        return validateNamespace;
    }

    /**
     * @hidden
     */
    public ExecuteOptions setIsCloudQuery(boolean v) {
        isProxyQuery = v;
        return this;
    }

    /**
     * @hidden
     */
    public boolean isProxyQuery() {
        return isProxyQuery;
    }

    /**
     * @hidden
     */
    public ExecuteOptions setDriverQueryVersion(int v) {
        driverQueryVersion = v;
        return this;
    }

    /**
     * @hidden
     */
    public int getDriverQueryVersion() {
        return driverQueryVersion;
    }

    /**
     * @hidden
     */
    public AuthContext getAuthContext() {
        return authContext;
    }

    /**
     * @hidden
     */
    public ExecuteOptions setAuthContext(AuthContext authContext) {
        this.authContext = authContext;
        return this;
    }
}
