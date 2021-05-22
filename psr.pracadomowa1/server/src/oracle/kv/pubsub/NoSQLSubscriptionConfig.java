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

package oracle.kv.pubsub;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import oracle.kv.impl.api.table.NameUtils;
import oracle.kv.impl.security.login.LoginToken;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.table.TableAPI;

/**
 * Configuration used by the NoSQL Publisher to create a subscription.
 */
public class NoSQLSubscriptionConfig {

    /**
     * Default empty stream lifetime in seconds is
     * {@value #DEFAULT_EMPTY_STREAM_LIFETIME_SECS}
     */
    public final static int DEFAULT_EMPTY_STREAM_LIFETIME_SECS = 60;

    /**
     * Default timeout in ms to change the stream, e.g, add or remove tables
     * from stream
     */
    public final static int DEFAULT_STREAM_CHANGE_TIMEOUT_MS = 30 * 1000;

    /* default max reconnect */
    private final static long DEFAULT_MAX_RETRY = 10;

    /* default stream mode */
    private final static NoSQLStreamMode DEFAULT_STREAM_MODE =
        NoSQLStreamMode.FROM_CHECKPOINT;

    /* subscriber subscriberId */
    private final NoSQLSubscriberId subscriberId;

    /* the position at which to resume the stream */
    private final StreamPosition initialPosition;

    /*
     * The tables associated with this subscription
     *
     * Note each table in the set is prefixed with its name space, e.g. a
     * table "MyTable" in namespace "MyNameSpace" can be represented as
     * "MyNameSpace:MyTable". If no namespace is specified, the table will be
     * found in the system default namespace.
     */
    private final Set<String> tables;

    /*
     * checkpoint table name; The user must specify the checkpoint table to
     * use in the subscription, and she must have permission to read and write
     * the table, otherwise the subscription will fail at the beginning and
     * a SubscriptionFailureException will be signalled to subscriber.
     *
     * Checkpoint table name will be prefixed with its namespace unless it is
     * in the system default namespace -- see TableAPI.INITIAL_NAMESPACE_NAME}
     */
    private final String ckptTableName;

    /*
     * True to create a new checkpoint table. Possible cases:
     *
     * When set true, that means user want to create a new checkpoint table
     * if specified checkpoint table does not exist
     * - If the table does not exist, subscription will create a new one if
     * user is eligible to create it; If the user does not have the create
     * table privilege, subscription will fail.
     * - If the table already exists, subscription will succeed since no new
     * table need be created.
     *
     * When set false, it means user want to use an existing checkpoint table:
     * - If the table does not exist, subscription will fail;
     * - If the table exists and the user is eligible to read/write it, the
     * subscription will succeed, otherwise, it will fail.
     *
     * The default is true to allow publisher create checkpoint table for user
     */
    private final boolean newCkptTable;

    /* subscription stream mode */
    private final NoSQLStreamMode streamMode;

    /* maximum time in seconds an empty subscription should live */
    private final int emptyStreamSecs;

    /* time out in ms when changing the subscription */
    private final long changeTimeoutMs;

    /*
     * For writes to multi-region table, true if the only write
     * from local region are allowed to pass, false otherwise;
     *
     * No impact to writes to other tables
     *
     * Default is false;
     */
    private final boolean localWritesOnly;

    /* max # of reconnect if the shard is unreachable */
    private final long maxReconnect;

    /* unit test only */
    private TestHook<NoSQLStreamMode> testHook = null;

    /* unit test only, always true unless test disable it */
    private boolean useFeederFilter = true;

    /* unit test only, always true unless test disable it */
    private boolean enableCkptTable = true;

    /**
     * True to enable the start up checkpoint, false to skip it. The start up
     * checkpoint is the checkpoint the streams API made at the stream start
     * position after the stream is created successfully. The start up
     * checkpoint may not necessarily be equal to the requested stream start
     * position. The purpose of the start up checkpoint is to prevent the
     * stream resuming from a position earlier than the start position.
     */
    private boolean enableStartupCkpt = true;

    /* unit test only, only set internally */
    private LoginToken token = null;

    /**
     * unit test only
     *
     * Reauthentication interval in ms. If it is not set, we compute
     * re-authentication time from toke lifetime. If set, we would compute
     * from this reAuthIntervalMs
     */
    private final long reAuthIntervalMs;

    private NoSQLSubscriptionConfig(Builder builder) {

        ckptTableName = builder.ckptTableName;
        subscriberId = builder.subscriberId;
        tables = builder.subscribedTables;
        reAuthIntervalMs = builder.reAuthIntervalMs;
        newCkptTable = builder.newCkptTable;
        streamMode = builder.streamMode;
        emptyStreamSecs = builder.emptyStreamSecs;
        changeTimeoutMs = builder.changeTimeoutMs;
        localWritesOnly = builder.localWritesOnly;
        maxReconnect = builder.maxReconnect;

        /*
         * If FROM_(EXACT_)STREAM_POSITION, user must specify a stream
         * position in builder
         */
        if (builder.initialPosition == null &&
            (streamMode.equals(NoSQLStreamMode
                                   .FROM_STREAM_POSITION) ||
             streamMode.equals(NoSQLStreamMode
                                   .FROM_EXACT_STREAM_POSITION))){

            throw new IllegalArgumentException(
                "Unspecified start stream position with stream mode " +
                streamMode);
        }


        if (streamMode.equals(NoSQLStreamMode.FROM_NOW) ||
            streamMode.equals(NoSQLStreamMode.FROM_CHECKPOINT) ||
            streamMode.equals(NoSQLStreamMode.FROM_EXACT_CHECKPOINT)) {
            initialPosition = null;
        } else {
            initialPosition = builder.initialPosition;
        }
    }

    /* getters */

    /**
     * Gets the name of checkpoint table associated with subscription
     *
     * @return  the full namespace name of checkpoint table
     */
    public String getCkptTableName() {
        return ckptTableName;
    }

    /**
     * Returns the initial stream position to be used when creating a
     * Subscription. The first call to <code>onNext()</code> by the Publisher
     * will <code>signal</code> the element following this position in the
     * stream.
     * <p>
     * If stream mode is
     * {@link NoSQLStreamMode#FROM_NOW},
     * {@link NoSQLStreamMode#FROM_CHECKPOINT}, or
     * {@link NoSQLStreamMode#FROM_EXACT_CHECKPOINT}, it always
     * returns null since these modes do not use the initial position
     * specified in config.
     */
    public StreamPosition getInitialPosition() {
        return initialPosition;
    }

    /**
     * Gets the subscriber ID of the configuration.
     *
     * @return subscriber ID
     */
    public NoSQLSubscriberId getSubscriberId() {
        return subscriberId;
    }

    /**
     * Returns the tables to be associated with a subscription. If null or an
     * empty set, it means all tables be streamed.
     */
    public Set<String> getTables() {
        return tables;
    }

    /**
     * Returns true if the subscription should attempt to create the checkpoint
     * table if it doesn't already exist.
     *
     * @return true if new checkpoint table should be created if needed
     */
    public boolean useNewCheckpointTable() {
        return newCkptTable;
    }

    /**
     * Returns true if when streaming multi-region tables, only local writes
     * are streamed, false otherwise. No impact when subscribing other
     * types of table.
     *
     * @return true when streaming multi-region tables, only local writes are
     * streamed, false otherwise.
     * @hidden for internal use only
     */
    public boolean isLocalWritesOnly() {
        return localWritesOnly;
    }

    /**
     * Returns the lifetime in seconds of empty subscription.
     *
     * @return  the lifetime of empty subscription
     */
    public int getEmptyStreamSecs() {
        return emptyStreamSecs;
    }

    /**
     * Returns the maximum number of times to attempt to reconnect to the store
     * on failure.
     *
     * @return the max number of reconnect attempts
     * @hidden for internal use only
     */
    public long getMaxReconnect() {
        return maxReconnect;
    }

    @Override
    public String toString() {
        final String tableNames = (tables == null || tables.isEmpty()) ?
            "all tables in NoSQL store" : Arrays.toString(tables.toArray());

        if (streamMode.equals(NoSQLStreamMode.FROM_CHECKPOINT) ||
            streamMode.equals(NoSQLStreamMode.FROM_EXACT_CHECKPOINT)) {
            return "Subscription " + subscriberId.toString() +
                   " configured to stream from checkpoint with stream mode:" +
                   streamMode + ", subscribed tables: " + tableNames;
        }

        if (streamMode.equals(NoSQLStreamMode.FROM_STREAM_POSITION) ||
            streamMode.equals(NoSQLStreamMode.FROM_EXACT_STREAM_POSITION)) {
            return "Subscription " + subscriberId.toString() +
                   " configured to stream from position " + initialPosition +
                   " with stream mode: " + streamMode +  ", subscribed " +
                   "tables: " + tableNames;
        }

        return "Subscription " + subscriberId.toString() +
               " configured to stream with stream mode: " + streamMode +
               ", subscribed tables: " + tableNames;
    }

    /**
     * @hidden
     *
     * Returns true if enable subscription feeder filter, false to disable
     * the filter.
     *
     * @return true if enable filter, false if not
     */
    public boolean isFeederFilterEnabled() {
        return useFeederFilter;
    }

    /**
     * @hidden
     *
     * Returns true if enable subscription feeder filter, false to disable
     * the filter.
     *
     * @return true if enable filter, false if not
     */
    public boolean isCkptEnabled() {
        return enableCkptTable;
    }

    /**
     * @hidden
     *
     * Returns true if startup checkpoint is enabled
     */
    public boolean isStartupCkptEnabled() {
        return enableStartupCkpt;
    }

    /**
     * @hidden
     *
     * Unit test only
     *
     * Disables feeder filter, can be a knob for performance tuning or
     * currently used in test only.
     */
    public void disableFeederFilter() {
        useFeederFilter = false;
    }

    /**
     * @hidden
     *
     * Unit test only
     *
     * Disables checkpoint table
     */
    public void disableCheckpoint() {
        enableCkptTable = false;
    }

    /**
     * @hidden
     *
     * Disable stream start up checkpoint
     */
    public void disableStartupCkpt() {
        enableStartupCkpt = false;
    }

    /**
     * @hidden
     *
     * Used in unit test only
     *
     * Gets the reauthentication interval in ms
     *
     * @return reauthentication interval in ms
     */
    public long getReAuthIntervalMs() {
        return reAuthIntervalMs;
    }

    /**
     * @hidden
     *
     * Gets the change timeout in ms
     *
     * @return the change timeout in ms
     */
    public long getChangeTimeoutMs() {
        return changeTimeoutMs;
    }


    /**
     * @hidden
     *
     * Computes the mapping from subscriber id to group of shards that the
     * subscriber streams from.
     *
     * @param si       subscriber id
     * @param topology topology of source kv store
     *
     * @return group of rep groups that the subscriber streams from
     *
     * @throws SubscriptionFailureException if number of subscribers is more
     * than number of shards in store
     */
    public static Set<RepGroupId> computeShards(NoSQLSubscriberId si,
                                                Topology topology)
        throws SubscriptionFailureException {

        final Set<RepGroupId> all = topology.getRepGroupIds();

        /* short cut for single member group */
        if (si.getTotal() == 1) {
            return all;
        }

        if (si.getTotal() > all.size()) {
            throw new SubscriptionFailureException(
                si, "number of subscribers in group (" + si.getTotal() +
                    ") exceeds number of shards in store (" + all.size() + ")");
        }

        return all.stream()
                  .filter(gid -> subscriberIncludesShard(si, gid))
                  .collect(Collectors.toSet());
    }

    /**
     * Returns true if the given shard should be included in the given
     * subscriber.
     *
     * @param si      subscriber id
     * @param shardId shard id
     *
     * @return true if the given shard should be included in the given
     * subscriber, false otherwise.
     */
    public static boolean subscriberIncludesShard(NoSQLSubscriberId si,
                                                  RepGroupId shardId) {
        /*
         * mapping algorithm by example:
         *
         * suppose 10 shards, 3 subscribers
         * rg1, rg4, rg7, rg10 -> subscriber 1
         * rg2, rg5, rg8 -> subscriber 2
         * rg3, rg6, rg9 -> subscriber 3
         */
        return si.getIndex() == (shardId.getGroupId()- 1) % si.getTotal();
    }

    /**
     * @hidden
     *
     * Set test hook
     */
    public void setILETestHook(TestHook<NoSQLStreamMode> hook) {
        testHook = hook;
    }

    /**
     * @hidden
     *
     * @return ILE test hooker
     */
    public TestHook<NoSQLStreamMode> getILETestHook() {
        return testHook;
    }

    /**
     * @hidden
     *
     * Constructs a singleton subscriber id with single member in group.
     *
     * @return a subscriber id with machine generated uuid as the group id.
     */
    private static NoSQLSubscriberId getSingletonSubscriberId() {
        return new NoSQLSubscriberId( 1, 0);
    }

    /**
     * @hidden
     * Unit test only
     */
    public LoginToken getTestToken() {
        return token;
    }

    /**
     * @hidden
     * Unite test only
     *
     * @param t  token to use
     */
    public void setTestToken(LoginToken t) {
        token = t;
    }

    /**
     * Returns the start stream mode.
     *
     * @return the start stream mode
     */
    public NoSQLStreamMode getStreamMode() {
        return streamMode;
    }

    /* Returns default mode */
    static NoSQLStreamMode getDefaultStreamMode() {
        return DEFAULT_STREAM_MODE;
    }

    /**
     * @hidden
     *
     * Returns true if the subscription currently streams all user tables
     * from the kvstore, without specifying a particular set of subscribed
     * tables.
     *
     * @return Returns true if the subscription currently streams all user
     * tables, false otherwise.
     */
    public boolean isWildCardStream() {
        return  tables == null || tables.isEmpty();
    }

    /**
     * Builder to construct a NoSQLSubscriptionConfig instance
     */
    public static class Builder {

        /* required parameter */
        private final String ckptTableName;

        /*
         * Optional parameters
         *
         * Default: a singleton group subscription with default stream mode
         * and feeder filter enabled, create new ckpt table if not exist,
         * subscribe all user tables.
         */
        private NoSQLSubscriberId subscriberId = getSingletonSubscriberId();
        private StreamPosition initialPosition = null;
        private Set<String> subscribedTables = null;
        private long reAuthIntervalMs = 0;
        private boolean newCkptTable = true;
        private NoSQLStreamMode streamMode = DEFAULT_STREAM_MODE;
        private int emptyStreamSecs = DEFAULT_EMPTY_STREAM_LIFETIME_SECS;
        private long changeTimeoutMs = DEFAULT_STREAM_CHANGE_TIMEOUT_MS;
        private boolean localWritesOnly = false;
        private long maxReconnect = DEFAULT_MAX_RETRY;


        /**
         * Makes a builder for NoSQLSubscriptionConfig with required parameter.
         *
         * <p>The checkpoint table can be specified in one of following
         * formats:
         *
         * <ol>
         * <li> <i>tablename</i>, e.g., "MyCkptTable". The given table should
         * be existent or will be created in system default table space.
         *
         * <li> <i>namespace</i>:<i>tablename</i>, e.g.,
         * "MyNameSpace:MyTable". The given table should be existent or will be
         * created in in the given name space.
         * </ol>
         *
         * @param ckptTableName the full namespace name of checkpoint table.
         *                      The checkpoint table cannot be a child table.
         *
         * @throws IllegalArgumentException if the checkpoint table name is
         * null or empty
         */
        public Builder(String ckptTableName) {
            this.ckptTableName = normalize(ckptTableName);
        }

        /**
         * Builds a NoSQLSubscriptionConfig instance from builder
         *
         * @return a NoSQLSubscriptionConfig instance
         */
        public NoSQLSubscriptionConfig build() {
            return new NoSQLSubscriptionConfig(this);
        }

        /**
         * Sets the subscribed tables under given namespace. Each table
         * should be specified in one of following formats:
         * <ol>
         * <li><i>tablename</i>, e.g., "MyTable". The given table should be
         * existent in system default table space. See {@link
         * TableAPI#SYSDEFAULT_NAMESPACE_NAME}.
         *
         * <li><i>namespace</i>:<i>tablename</i>, e.g.,
         * "MyNameSpace:MyTable". The given table should be existent in the
         * given table space.
         * </ol>
         *
         * If not set or set to null or empty set, the stream will subscribe
         * all user tables from all name spaces.
         *
         * @param tables set of table names to subscribe
         *
         * @return this instance
         */
        public Builder setSubscribedTables(Set<String> tables) {
            subscribedTables = tables.stream()
                                     .map(Builder::normalize)
                                     .collect(Collectors.toSet());
            return this;
        }

        /**
         * Sets the subscribed tables under given namespace. Each table
         * should be specified in one of following formats:
         *
         * <ol>
         * <li> <i>tablename</i>, e.g., "MyTable". The given table should be
         * existent in initial table space.
         *
         * <li> <i>namespace</i>:<i>tablename</i>, e.g.,
         * "MyNameSpace:MyTable". The given table should be existent in given
         * table space.
         * </ol>
         *
         * If not set or set to null or empty set, the stream will subscribe
         * all user tables.
         *
         * @param tables set of table names to subscribe
         * @return this instance
         */
        public Builder setSubscribedTables(String... tables) {
            subscribedTables = Arrays.stream(tables)
                                     .map(Builder::normalize)
                                     .collect(Collectors.toSet());
            return this;
        }

        /**
         * Sets the subscriber id which owns the subscription. If it is not set,
         * the subscription is considered a singleton subscription with single
         * subscriber streaming from all shards.
         *
         * @param id subscriber id
         * @return this instance
         */
        public Builder setSubscriberId(NoSQLSubscriberId id) {
            subscriberId = id;
            return this;
        }

        /**
         * Sets the start stream position. Depending on stream mode, the
         * start stream position has different semantics as follows.
         * <p> If stream mode is set to
         * {@link NoSQLStreamMode#FROM_CHECKPOINT}, which is the default if
         * stream mode is not set, the stream will start from the checkpoint
         * persisted in the specified checkpoint table, and any specified
         * stream position will be ignored. If the checkpoint table does not
         * exist, the stream will create a new checkpoint table for user if
         * allowed. If the checkpoint table is empty, or if the position
         * specified by the checkpoint table is not available, subscription
         * will stream from the next available position.
         *
         * <p> If stream mode is set to
         * {@link NoSQLStreamMode#FROM_EXACT_CHECKPOINT}, the stream will
         * start from the checkpoint persisted in the specified checkpoint
         * table, and any specified stream position will be ignored. If the
         * table does not exist, the stream will create a new checkpoint
         * table for user if allowed. If the checkpoint table is empty,
         * subscription will to stream from the first available entry. If
         * a checkpoint has been saved but that stream position is not
         * available, the subscription will fail and the publisher will signal
         * {@link SubscriptionInsufficientLogException} to the subscriber via
         * {@link NoSQLSubscriber#onError}.
         *
         * <p> If stream mode is set to
         * {@link NoSQLStreamMode#FROM_STREAM_POSITION} the stream will
         * start from specified stream position if set. If the start stream
         * position is not available, the subscription will stream from the
         * next available position. If the start stream position is not
         * set or is set to null, {@link IllegalArgumentException} will be
         * raised when the configuration is created.
         *
         * <p> If stream mode is set to
         * {@link NoSQLStreamMode#FROM_EXACT_STREAM_POSITION}, the stream will
         * start from specified stream position if set. If the specified
         * position is not available, the subscription will fail and the
         * publisher will signal {@link SubscriptionInsufficientLogException}
         * to the subscriber via {@link NoSQLSubscriber#onError}. If the
         * start stream position is not set or set to null,
         * {@link IllegalArgumentException} will be raised when the
         * configuration is created.
         *
         * <p> If stream mode is set to
         * {@link NoSQLStreamMode#FROM_NOW}, the stream will start from
         * the latest available stream position and any specified start stream
         * position will be ignored.
         *
         * @param position               stream position to start stream
         *
         * @return this instance
         */
        public Builder setStartStreamPosition(StreamPosition position) {
            initialPosition = position;
            return this;
        }

        /**
         * Sets if publisher is allowed to create a new checkpoint table if
         * it does not exist. If not set, the default is that publisher would
         * create a new checkpoint table if it does not exist.
         *
         * @param allow   true if publisher is allowed to create a new
         *                checkpoint table for user if it does not exist;
         *                false otherwise, and subscription will fail if
         *                checkpoint table does not exist.
         *
         * @return this instance
         */
        public Builder setCreateNewCheckpointTable(boolean allow) {
            newCkptTable = allow;
            return this;
        }

        /**
         * Sets the stream mode.  If not set, the stream mode defaults to
         * {@link NoSQLStreamMode#FROM_CHECKPOINT}.
         *
         * @param mode the stream mode
         *
         * @return this instance
         *
         * @throws IllegalArgumentException if specified stream mode is null.
         */
        public Builder setStreamMode(NoSQLStreamMode mode)
            throws IllegalArgumentException {

            if (mode == null) {
                throw new IllegalArgumentException(
                    "Stream mode cannot be null");
            }
            streamMode = mode;
            return this;
        }

        /**
         * Sets the empty stream duration. A stream is considered
         * empty if all subscribed tables have been removed from the
         * subscription. An empty stream is considered to be no longer empty
         * when at least one subscribed table is added successfully to the
         * empty stream. If a stream is empty, the publisher would keep the
         * empty stream alive for a period of time specified, and the
         * publisher would shut down the empty stream when it times out.
         * <p>
         * If not set, the empty stream stream duration defaults to
         * {@value #DEFAULT_EMPTY_STREAM_LIFETIME_SECS} seconds.
         *
         * @param emptyStreamSecs  empty stream lifetime in seconds
         *
         * @return this instance
         *
         * @throws IllegalArgumentException if specified empty stream
         * lifetime is non-positive.
         */
        public Builder setEmptyStreamDuration(int emptyStreamSecs)
            throws IllegalArgumentException {
            if (emptyStreamSecs <= 0) {
                throw new IllegalArgumentException("Empty stream duration " +
                                                   "must be positive.");
            }

            this.emptyStreamSecs = emptyStreamSecs;
            return this;
        }

        /**
         * Sets the subscription change timeout in milliseconds. When the
         * user changes the subscription by adding or removing table, the
         * request would be sent to kvstore and it would timeout if the
         * kvstore does not respond the request within the time period.
         * <p>
         * If not set, change timeout defaults to
         * {@value #DEFAULT_STREAM_CHANGE_TIMEOUT_MS} milliseconds.
         *
         * @param changeTimeoutMs  change timeout in milliseconds
         *
         * @return this instance
         *
         * @throws IllegalArgumentException if specified change timeout
         * is non-positive.
         */
        public Builder setChangeTimeoutMs(long changeTimeoutMs)
            throws IllegalArgumentException {
            if (changeTimeoutMs <= 0) {
                throw new IllegalArgumentException("Change timeout must be " +
                                                   "positive.");
            }

            this.changeTimeoutMs = changeTimeoutMs;
            return this;
        }

        /**
         * @hidden
         *
         * Used in test only
         *
         * Set the reauthentication interval in ms. During subscription,
         * subscriber will reauthenticate itself periodically by renew the
         * token from KVStore. Subscriber can specify a time to
         * reauthenticate via this parameter. If the parameter is not set or
         * set to 0, subscriber will compute the time to reauthenticate from
         * the token expiration time. The default is 0.
         *
         * @param intervalMs reauthentication interval in ms
         *
         * @return this instance
         *
         * @throws IllegalArgumentException if interval is not positive.
         */
        public Builder setReAuthIntervalMs(long intervalMs)
            throws IllegalArgumentException {
            if (intervalMs <= 0) {
                throw new IllegalArgumentException(
                    "Reauthentication interval must be positive");
            }

            reAuthIntervalMs = intervalMs;
            return this;
        }

        /**
         * Sets that when subscribing multi-region tables, whether only local
         * writes are streamed.
         *
         * @return this instance
         */
        public Builder setLocalWritesOnly(boolean localWritesOnly) {
            this.localWritesOnly = localWritesOnly;
            return this;
        }

        /**
         * Sets the max number of reconnect if stream is down
         *
         * @return this instance
         */
        public Builder setMaxReconnect(long maxReconnect) {
            this.maxReconnect = maxReconnect;
            return this;
        }

        /**
         * Normalizes the table name from user by making it a qualified name.
         * If the name space is not set by user, the system default namespace
         * will be added as prefix.
         *
         * @param in given table name
         *
         * @return  a qualified table name with non-empty name space
         *
         * @throws IllegalArgumentException if given table name is null or
         * empty.
         */
        private static String normalize(String in) {
            final String tableName = NameUtils.getFullNameFromQualifiedName(in);
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException
                    ("Table name cannot be null or empty " + tableName);
            }
            final String ns = NameUtils.getNamespaceFromQualifiedName(in);
            return (ns == null || ns.isEmpty()) ?
                tableName : NameUtils.makeQualifiedName(ns, tableName);
        }
    }
}
