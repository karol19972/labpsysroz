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

import static oracle.kv.impl.api.table.TableJsonUtils.ALWAYS;
import static oracle.kv.impl.api.table.TableJsonUtils.ANNOTATIONS;
import static oracle.kv.impl.api.table.TableJsonUtils.CACHE;
import static oracle.kv.impl.api.table.TableJsonUtils.CHILDREN;
import static oracle.kv.impl.api.table.TableJsonUtils.CHILDTABLES;
import static oracle.kv.impl.api.table.TableJsonUtils.CHILD_TABLE_LIMIT;
import static oracle.kv.impl.api.table.TableJsonUtils.COLLECTION;
import static oracle.kv.impl.api.table.TableJsonUtils.CYCLE;
import static oracle.kv.impl.api.table.TableJsonUtils.DEFAULT;
import static oracle.kv.impl.api.table.TableJsonUtils.DESC;
import static oracle.kv.impl.api.table.TableJsonUtils.ENUM_NAME;
import static oracle.kv.impl.api.table.TableJsonUtils.FIELDS;
import static oracle.kv.impl.api.table.TableJsonUtils.IDENTITY;
import static oracle.kv.impl.api.table.TableJsonUtils.INCREMENT;
import static oracle.kv.impl.api.table.TableJsonUtils.INDEXES;
import static oracle.kv.impl.api.table.TableJsonUtils.INDEX_KEY_SIZE_LIMIT;
import static oracle.kv.impl.api.table.TableJsonUtils.INDEX_LIMIT;
import static oracle.kv.impl.api.table.TableJsonUtils.JSON_VERSION;
import static oracle.kv.impl.api.table.TableJsonUtils.LIMITS;
import static oracle.kv.impl.api.table.TableJsonUtils.MAX;
import static oracle.kv.impl.api.table.TableJsonUtils.MIN;
import static oracle.kv.impl.api.table.TableJsonUtils.NAME;
import static oracle.kv.impl.api.table.TableJsonUtils.NULL;
import static oracle.kv.impl.api.table.TableJsonUtils.NULLABLE;
import static oracle.kv.impl.api.table.TableJsonUtils.OWNER;
import static oracle.kv.impl.api.table.TableJsonUtils.PARENT;
import static oracle.kv.impl.api.table.TableJsonUtils.PKEY_SIZES;
import static oracle.kv.impl.api.table.TableJsonUtils.PRIMARYKEY;
import static oracle.kv.impl.api.table.TableJsonUtils.PROPERTIES;
import static oracle.kv.impl.api.table.TableJsonUtils.READ_LIMIT;
import static oracle.kv.impl.api.table.TableJsonUtils.REGIONS;
import static oracle.kv.impl.api.table.TableJsonUtils.SEQUENCE;
import static oracle.kv.impl.api.table.TableJsonUtils.SHARDKEY;
import static oracle.kv.impl.api.table.TableJsonUtils.SIZE_LIMIT;
import static oracle.kv.impl.api.table.TableJsonUtils.START;
import static oracle.kv.impl.api.table.TableJsonUtils.SYSTABLE;
import static oracle.kv.impl.api.table.TableJsonUtils.TIMESTAMP_PRECISION;
import static oracle.kv.impl.api.table.TableJsonUtils.TYPE;
import static oracle.kv.impl.api.table.TableJsonUtils.TYPES;
import static oracle.kv.impl.api.table.TableJsonUtils.WRITE_LIMIT;
import static oracle.kv.impl.util.SerialVersion.IDENTITY_VERSION;
import static oracle.kv.impl.util.SerialVersion.MULTI_REGION_TABLE_VERSION;
import static oracle.kv.impl.util.SerialVersion.TABLE_SEQ_NUM_VERSION;
import static oracle.kv.impl.util.SerialVersion.UUID_VERSION;
import static oracle.kv.impl.util.SerializationUtil.LOCAL_BUFFER_SIZE;
import static oracle.kv.impl.util.SerializationUtil.readNonNullSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.readNonNullString;
import static oracle.kv.impl.util.SerializationUtil.readPackedInt;
import static oracle.kv.impl.util.SerializationUtil.readPackedLong;
import static oracle.kv.impl.util.SerializationUtil.readSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.readString;
import static oracle.kv.impl.util.SerializationUtil.writeCollectionLength;
import static oracle.kv.impl.util.SerializationUtil.writeFastExternalOrNull;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullString;
import static oracle.kv.impl.util.SerializationUtil.writePackedInt;
import static oracle.kv.impl.util.SerializationUtil.writePackedLong;
import static oracle.kv.impl.util.SerializationUtil.writeString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import oracle.kv.ChildTableLimitException;
import oracle.kv.IndexLimitException;
import oracle.kv.Key;
import oracle.kv.Key.BinaryKeyIterator;
import oracle.kv.Value;
import oracle.kv.Value.Format;
import oracle.kv.ValueVersion;
import oracle.kv.Version;
import oracle.kv.impl.admin.IllegalCommandException;
import oracle.kv.impl.api.KVStoreImpl;
import oracle.kv.impl.api.ops.ResultKey;
import oracle.kv.impl.api.table.IndexImpl.IndexField;
import oracle.kv.impl.api.table.TableAPIImpl.GeneratedValueInfo;
import oracle.kv.impl.api.table.ValueSerializer.ArrayValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.FieldValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.MapValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.RecordValueSerializer;
import oracle.kv.impl.api.table.ValueSerializer.RowSerializer;
import oracle.kv.impl.api.table.serialize.BinaryEncoder;
import oracle.kv.impl.api.table.serialize.Decoder;
import oracle.kv.impl.api.table.serialize.DecoderFactory;
import oracle.kv.impl.api.table.serialize.Encoder;
import oracle.kv.impl.api.table.serialize.ResolvingDecoder;
import oracle.kv.impl.metadata.Metadata.MetadataType;
import oracle.kv.impl.metadata.MetadataInfo;
import oracle.kv.impl.security.Ownable;
import oracle.kv.impl.security.ResourceOwner;
import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.util.ArrayPosition;
import oracle.kv.impl.util.FastExternalizable;
import oracle.kv.impl.util.SerialVersion;
import oracle.kv.impl.util.SerializationUtil;
import oracle.kv.impl.util.SortableString;
import oracle.kv.table.ArrayDef;
import oracle.kv.table.FieldDef;
import oracle.kv.table.FieldRange;
import oracle.kv.table.FieldValue;
import oracle.kv.table.Index;
import oracle.kv.table.IndexKey;
import oracle.kv.table.MapDef;
import oracle.kv.table.MultiRowOptions;
import oracle.kv.table.RecordDef;
import oracle.kv.table.RecordValue;
import oracle.kv.table.ReturnRow;
import oracle.kv.table.Row;
import oracle.kv.table.SequenceDef;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.TimeToLive;

import com.sleepycat.util.PackedInteger;
import com.sleepycat.util.UtfOps;

/**
 * TableImpl implements Table, which represents a table in Oracle NoSQL
 * Database.  It is an immutable object created from system metadata.
 *
 * Tables are defined in terms of several properties:
 * 1.  a map of {@link FieldDef} instances keyed by a String field name.  This
 * defines the fields (or "columns") of a table.
 * 2.  a list of fields that define the fields that participate in the
 * primary key for the table.  These fields turn into KV Key path
 * components in the store.
 * 3.  a list of fields that is a proper subset of the primary key fields
 * that defines the "shard key" for the table.  The shard key defines the
 * primary key fields that become part of the Key's major path.  The remaining
 * primary key fields become the Key's minor path.
 * 4.  optional indexes, defined in terms of fields in the table.
 * 5.  optional child tables, keyed by table name.  Child tables inherit the
 * table's primary key and shard key.
 * 6.  optional Time-to-Live (TTL) duration. A record may use this TTL as its
 *  expiration when no explicit TTL value is set for the same record.
 *
 * If a table is a child table it also references its parent table.  When a
 * table is created the system generates a unique long to serve as an id for
 * the table.  The serialized form of this id serves a part of the table's
 * primary key to locate it in the store.  An id is used instead of the table
 * name to keep keys small.
 *
 * Tables can be created in {@code r2compat} mode which means that the table
 * name is used for keys instead of the id because the table overlays R2 data.
 * Such tables also write new records in a manner that is compatible with R2 by
 * avoiding adding the table version to the record data.
 *
 * Because a table can evolve the map of fields is maintained as a list of
 * maps of fields, indexed by table "version."  The initial table version
 * is 1 (but index 0).
 *
 * Tables can evolve in limited ways with schema evolution.  The only thing
 * that can be done is to add or remove non-key fields or change fields in
 * a way that does not affect their serialization or change the default TTL.
 * Once r2compat tables have been evolved they are no longer readable by R2
 * key/value code.
 * <p>
 *
 * Tables can carry an optional default expiry duration. Expiry duration can be
 * null or a positive value (including 0) in a particular unit of time. A 0
 * value is semantically equivalent to no expiry being defined at all.
 * <br>
 * For non-zero positive expiry value, the unit of time must be equal or longer
 * than a minimum time unit supported by the system. <br>
 * Currently, minimum unit of time is an hour.
 *
 * @see #writeFastExternal FastExternalizable format
 */
public class TableImpl implements Table, MetadataInfo, Ownable,
                                  FastExternalizable, Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    /**
     * A thread-local byte array used as the initial buffer in the
     * ByteArrayOutputStreams used by createValueInternal.
     */
    private static final ThreadLocal<byte[]> createValueBuffer =
        ThreadLocal.withInitial(() -> new byte[LOCAL_BUFFER_SIZE]);

    /**
     * A thread-local byte array used as the buffer when creating binary
     * encoders.
     */
    private static final ThreadLocal<byte[]> encoderBuffer =
        ThreadLocal.withInitial(
            () -> {
                final int size =
                    TableJsonUtils.getEncoderFactory().getBinaryBufferSize();
                return new byte[size];
            });

    private final String name;

    /* The dynamically cached namespace-qualified table name. */
    private volatile transient String fullNameSpaceName;

    private long id;

    private final TableImpl parent;

    private final TreeMap<String, Index> indexes;

    /*
     * The names of the fields that comprise the primary key of this table.
     * The list includes the pk fields of the ancestor tables, if any. It
     * does not include the id of this table or its ancestors.
     */
    private final List<String> primaryKey;

    /*
     * If non-null, a list of size constraints on the corresponding Primary Key
     * fields.
     */
    private final List<Integer> primaryKeySizes;

    private final List<String> shardKey;

    private String description;

    private final Map<String, Table> children;

    private final ArrayList<FieldMap> versions;

    private TimeToLive ttl;

    private TableStatus status;

    /*
     * These next two are true, and non-zero, respectively, if this is
     * an overlay on R2 data with an Avro schema.  r2compat can be true
     * without a schemaId for a key-only table.  It affects the string used
     * as the table's key component (idString, below).
     */
    private final boolean r2compat;
    private final int schemaId;

    private final ResourceOwner owner;

    /* Whether this table is system table */
    private final boolean sysTable;

    private String namespace;

    /*
     * Table limits. If this field is non-null, then the limits defined in
     * the TableLimits will be enforced on this table.
     */
    private TableLimits limits = null;

    /*
     * Information about the identity column if it exists, null otherwise.
     */
    private IdentityColumnInfo identityColumnInfo;

    /*
     * The set of remote region IDs that the table is subscribed to. This is
     * non-null if the table is a multi-region table. The set may be empty
     * if the table is not currently subscribed to any remote regions.
     */
    private final Set<Integer> regionIds;

    /*
     * Sequence number of the last change affecting this table. Only set on the
     * top level table. May be zero due to upgrade from older store.
     */
    private int sequenceNumber = 0;

    /*
     * transient, cached values
     */

    /*
     * The current version of this table instance. It must only be set using
     * its accessor to ensure that associated caches are maintained.
     */
    private transient volatile int version;

    /*
     * The number of components in a key for this table. It includes all the
     * user-declared pk fields of this tables and its ancestors, as well as the
     * internal table ids of this tables and its ancestors.
     */
    private transient int numKeyComponents;

    /* The string representation of the table key. */
    private transient String idString;

    /* The byte array representation of the table key. */
    private transient byte[] idBytes;

    /* A RecordDef defining the schema of the primary key for this table */
    private transient RecordDefImpl primaryKeyDef;

    /*
     * An array of TableVersionInfo to cache per-version information used to
     * handle schema-evolved tables. This is initialized on construction, in
     * initializeVersionInfo.
     */
    private transient ArrayList<TableVersionInfo> tableVersionInfo;

    /* sequence definition of the identity column */
    private transient SequenceDef identitySequenceDef = null;

    /*
     * The value format used for this table.
     */
    private transient Value.Format valueFormat;

    /*
     * The column position of the "STRING AS UUID GENERATED BY DEFAULT" field.
     * The default value -2 means the table has no UUID column.
     * The value -1 means the table has UUID column that is not generated.
     * The value between 0 and Integer.MAX_VALUE is the column position
     * of the table that has STRING AS UUID GENERATED BY DEFAULT field.
     */
    private transient int generatedUUIDPosition = -2;

    /**
     * Constants used to designate "special" steps in a DDL path. They are here
     * to be more shareable across external classes that need them.
     */
    public static final String BRACKETS = "[]";
    public static final String KEYS = "keys()";
    public static final String VALUES = "values()";

    public static final String FN_KEYS = "keys(";
    public static final String FN_KEYOF = "keyof(";
    public static final String FN_ELEMENTOF = "elementof(";

    /**
     * For testing
     * It is called in rowFromValueVersion() to validate the value format if
     * configured.
     */
    private transient TestHook<Format> checkDeserializeValueFormatHook;

    /**
     * For testing
     * It is used when serialize a row to a value in createValue(Row). If it is
     * configured, then use the corresponding value format of the specified
     * serial version to do the serialization.
     */
    private static short testCurrentSerialVersion = 0;

    /**
     * Table status.
     *
     * @see #writeFastExternal FastExternalizable format
     */
    public enum TableStatus implements FastExternalizable  {
        /** Table and its data is being deleted */
        DELETING() {
            @Override
            public boolean isDeleting() {
                return true;
            }
        },

        /** Table is ready for use */
        READY() {
            @Override
            public boolean isReady() {
                return true;
            }
        };

        private static final TableStatus[] ENUM_VALUES = values();

        /**
         * Returns true if this is the {@link #DELETING} type.
         * @return true if this is the {@link #DELETING} type
         */
        public boolean isDeleting() {
            return false;
        }

        /**
         * Returns true if this is the {@link #READY} type.
         * @return true if this is the {@link #READY} type
         */
        public boolean isReady() {
            return false;
        }

        public static TableStatus readFastExternal(
            DataInput in,
            @SuppressWarnings("unused") short serialVersion)
            throws IOException {

            final int ordinal = in.readByte();
            try {
                return ENUM_VALUES[ordinal];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IOException(
                    "Wrong ordinal for TableStatus: " + ordinal, e);
            }
        }

        /**
         * Writes this object to the output stream.  Format:
         * <ol>
         * <li> ({@code byte}) <i>ordinal</i>
         * </ol>
         */
        @Override
        public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {

            out.writeByte(ordinal());
        }
    }

    private static final int MAX_ID_LENGTH = 256;
    static final int MAX_NAME_LENGTH = 64;
    private static final String SEPARATOR_REGEX = "\\.";
    private static final int INITIAL_TABLE_VERSION = 1;

    /* The prefix of system table names, case insensitive */
    public static final String SYSTEM_TABLE_PREFIX = "SYS$";
    private static final String SYSTEM_TABLE_PREFIX_STRING = "SYS";

    /*
     * Names (field names, enum symbols) must start with an alphabetic
     * character [A-Za-z] followed by alphabetic characters, numeric
     * characters or underscore [A-Za-z0-9_].
     */
    static final String VALID_NAME_CHAR_REGEX = "^[A-Za-z][A-Za-z0-9_]*$";

    /*
     * Namespaces. Allow some additional characters required for the cloud.
     */
    static final String VALID_NAMESPACE_CHAR_REGEX =
        "^[A-Za-z][A-Za-z0-9_.\\-@]*$";
    private static final int MAX_NAMESPACE_LENGTH = 128;
    private static final int MAX_REGION_NAME_LENGTH = 128;

    /**
     * Creates a TableImpl.
     * @param name the table name (required)
     * @param parent the parent table, or null
     * @param primaryKey the primary key fields (required)
     * @param shardKey the shard key (required)
     * @param fields the field definitions for the table (required)
     * @param r2compat if true create a release 2 compatible table which
     * means using the table name instead of its id in getIdString()
     * @param schemaId if a release 2 schema was used to construct the
     * fields this must be its schema id. It is only meaningful if r2compat
     * is true.
     * @param description a user-provided description of the table, or null
     * @param validate if true validate the fields and state of the table
     * upon construction
     * @param owner the owner of this table
     * @param sysTable if true the table is a system table
     */
    public TableImpl(final String namespace,
                      final String name,
                      final TableImpl parent,
                      final List<String> primaryKey,
                      final List<Integer> primaryKeySizes,
                      final List<String> shardKey,
                      final FieldMap fields,
                      final TimeToLive ttl,
                      TableLimits limits,
                      boolean r2compat,
                      int schemaId,
                      final String description,
                      boolean validate,
                      ResourceOwner owner,
                      boolean sysTable,
                      IdentityColumnInfo identityColumnInfo,
                      Set<Integer> regions) {
        if (name == null) {
            throw new IllegalArgumentException("Table names cannot be null");
        }
        if (regions != null) {
            if (parent != null) {
                throw new IllegalArgumentException("A child table cannot be" +
                                                   " multi-region");
            }
            if (identityColumnInfo != null) {
                throw new IllegalArgumentException("A multi-region table cannot" +
                                                   " contain an identity column");
            }
        }
        this.name = name;
        this.namespace = namespace;
        this.parent = parent;
        this.description = description;
        this.primaryKey = primaryKey;
        this.primaryKeySizes = primaryKeySizes;
        this.shardKey = shardKey;
        this.status = TableStatus.READY;
        this.r2compat = r2compat;
        this.schemaId = schemaId;
        this.sysTable = sysTable;
        children = new TreeMap<>(FieldComparator.instance);
        indexes = new TreeMap<>(FieldComparator.instance);
        versions = new ArrayList<>();
        versions.add(fields);
        this.ttl = ttl;
        setVersion(INITIAL_TABLE_VERSION);

        validateTableName(name, sysTable);

        if (identityColumnInfo != null) {
            assert identityColumnInfo.getIdentityColumn() <= fields.size() - 1 :
                "Identity column out of range.";

            this.identityColumnInfo = identityColumnInfo;
        }

        if (validate) {
            validate();
        }
        setIdString();
        /* regionIds should be set before initialize value format. */
        if (regions != null) {
            regionIds = new HashSet<>();
            setRegions(regions);
        } else {
            regionIds = null;
        }
        initializeVersionInfo(validate);
        initGeneratedUUIDPos();
        this.owner = owner == null ? null : new ResourceOwner(owner);
        if (limits != null) {
            setTableLimits(limits);
        }
    }

    /* Updates the region set with the specified set */
    private void setRegions(Set<Integer> regions) {
        assert regionIds != null;
        regionIds.clear();
        regionIds.addAll(regions);
        regionIds.remove(Region.LOCAL_REGION_ID);
    }

    /*
     * This constructor is used by clone().  Some fields are copied by
     * reference:
     *  parent
     *  primaryKey, shardKey
     *  limits
     *  indexes (they are immutable)
     */
    private TableImpl(TableImpl t) {
        name = t.name;
        namespace = t.namespace;
        id = t.id;
        sequenceNumber = t.sequenceNumber;
        version = t.version;
        description = t.description;
        parent = t.parent;
        primaryKey = t.primaryKey;
        primaryKeySizes = t.primaryKeySizes;
        shardKey = t.shardKey;
        status = t.status;
        r2compat = t.r2compat;
        schemaId = t.schemaId;
        owner = t.owner;
        sysTable = t.sysTable;
        limits = t.limits;

        children = new TreeMap<>(FieldComparator.instance);
        for (Table table : t.children.values()) {
            children.put(table.getName(), ((TableImpl)table).clone());
        }

        versions = new ArrayList<>(t.versions);
        ttl = t.ttl;
        setVersion(t.version);
        /* this constructor uses the same Comparator as t.indexes */
        indexes = new TreeMap<>(t.indexes);
        setIdString();
        /* regionIds should be set before initialize value format. */
        regionIds = t.regionIds;
        initializeVersionInfo(true);
        initGeneratedUUIDPos();
        identityColumnInfo = t.identityColumnInfo;
    }

    /* Constructor for FastExternalizable */
    public TableImpl(DataInput in, short serialVersion, TableImpl parent)
        throws IOException {
        this.parent = parent;
        name = readNonNullString(in, serialVersion);
        id = readPackedLong(in);
        if (serialVersion >= TABLE_SEQ_NUM_VERSION) {
            sequenceNumber = readPackedInt(in);
        }
        final int nPK = readNonNullSequenceLength(in);
        primaryKey = new ArrayList<>(nPK);
        for (int i = 0; i < nPK; i++) {
            primaryKey.add(i, readNonNullString(in, serialVersion));
        }
        final int nPKSize = readSequenceLength(in);
        if (nPKSize < 0) {
            primaryKeySizes = null;
        } else {
            primaryKeySizes = new ArrayList<>(nPKSize);
            for (int i = 0; i < nPKSize; i++) {
                primaryKeySizes.add(i, readPackedInt(in));
            }
        }
        final int nSK = readNonNullSequenceLength(in);
        shardKey = new ArrayList<>(nSK);
        for (int i = 0; i < nSK; i++) {
            shardKey.add(i, readNonNullString(in, serialVersion));
        }
        description = readString(in, serialVersion);
        final int nVersions = readNonNullSequenceLength(in);
        versions = new ArrayList<>(nVersions);
        for (int i = 0; i < nVersions; i++) {
            versions.add(i, new FieldMap(in, serialVersion));
        }
        ttl = in.readBoolean() ?
                        TimeToLive.readFastExternal(in, serialVersion) : null;
        status = TableStatus.readFastExternal(in, serialVersion);
        r2compat = in.readBoolean();
        schemaId = readPackedInt(in);
        owner = in.readBoolean() ? new ResourceOwner(in, serialVersion) : null;
        sysTable = in.readBoolean();
        namespace = readString(in, serialVersion);
        limits = in.readBoolean() ? new TableLimits(in) : null;
        identityColumnInfo = in.readBoolean() ? new IdentityColumnInfo(in) :
                                                null;
        final int nRegions = readSequenceLength(in);
        if (nRegions < 0) {
            regionIds = null;
        } else {
            regionIds = new HashSet<>(nRegions);
            for (int i = 0; i < nRegions; i++) {
                regionIds.add(readPackedInt(in));
            }
        }

        /* Initialize transient state before adding children and indexes */
        getTableVersion();
        setIdString();
        initializeVersionInfo(true);
        initGeneratedUUIDPos();

        final int nChildren = readNonNullSequenceLength(in);
        children = new TreeMap<>(FieldComparator.instance);
        for (int i = 0; i < nChildren; i++) {
            final TableImpl child = new TableImpl(in, serialVersion, this);
            children.put(child.getName(), child);
        }
        final int nIndexes = readNonNullSequenceLength(in);
        indexes = new TreeMap<>(FieldComparator.instance);
        for (int i = 0; i < nIndexes; i++) {
            addIndex(new IndexImpl(in, serialVersion, this));
        }
    }

    /**
     * Writes this object to the output stream. Format:
     *
     * <ol>
     * <li> ({@link SerializationUtil#writeNonNullString
     *      non-null String}) {@code name}
     * <li> ({@link SerializationUtil#writePackedLong long}) {@code id}
     * <li> ({@link SerializationUtil#writePackedInt int}) {@code sequenceNumber}
     * <li> ({@link SerializationUtil#writeNonNullSequenceLength non-null
     *      sequence length}) {@code primaryKey} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link SerializationUtil#writeNonNullString
     *         non-null String}) <i>key</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeCollectionLength
     *      sequence length}) {@code primaryKeySizes} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link SerializationUtil#writePackedInt int}) <i>key length</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeNonNullSequenceLength non-null
     *      sequence length}) {@code shardKey} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link SerializationUtil#writeNonNullString
     *         non-null String}) <i>shard key</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeString String})
     *      {@code description}
     * <li> ({@link SerializationUtil#writeNonNullSequenceLength non-null
     *      sequence length}) {@code versions} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link FieldMap}) <i>field map</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeFastExternalOrNull TimeToLive
     *          or null}) {@code ttl}
     * <li> ({@link TableStatus}) {@code status}
     * <li> ({@code boolean}) {@code r2compat}
     * <li> ({@link SerializationUtil#writePackedInt int}) {@code schemaId}
     * <li> ({@link SerializationUtil#writeFastExternalOrNull ResourceOwner
     *          or null}) {@code owner}
     * <li> ({@code boolean}) {@code sysTable}
     * <li> ({@link SerializationUtil#writeString String}) {@code namespace}
     * <li> ({@link SerializationUtil#writeFastExternalOrNull TableLimits
     *          or null}) {@code limits}
     * <li> ({@link SerializationUtil#writeFastExternalOrNull IdentityColumnInfo
     *          or null}) {@code identityColumnInfo}
     * <li> ({@link SerializationUtil#writeCollectionLength
     *      sequence length}) {@code regionIds} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link SerializationUtil#writePackedInt int}) <i>region ID</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeNonNullSequenceLength non-null
     *      sequence length}) {@code children} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link TableImpl}) <i>child table</i>
     *    </ol>
     * <li> ({@link SerializationUtil#writeNonNullSequenceLength non-null
     *      sequence length}) {@code indexes} <i>length</i>
     * <li> For each element:
     *    <ol type="a">
     *    <li> ({@link IndexImpl}) <i>index</i>
     *    </ol>
     * </ol>
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
            throws IOException {
        writeNonNullString(out, serialVersion, name);
        writePackedLong(out,id);
        if (serialVersion >= TABLE_SEQ_NUM_VERSION) {
            writePackedInt(out, sequenceNumber);
        }
        writeNonNullSequenceLength(out, primaryKey.size());
        for (String pk : primaryKey) {
            writeNonNullString(out, serialVersion, pk);
        }
        writeCollectionLength(out, primaryKeySizes);
        if (primaryKeySizes != null) {
            for (int pks : primaryKeySizes) {
                writePackedInt(out, pks);
            }
        }
        writeNonNullSequenceLength(out, shardKey.size());
        for (String sk : shardKey) {
            writeNonNullString(out, serialVersion, sk);
        }
        writeString(out, serialVersion, description);
        writeNonNullSequenceLength(out, versions.size());
        for (FieldMap fm : versions) {
            fm.writeFastExternal(out, serialVersion);
        }
        writeFastExternalOrNull(out, serialVersion, ttl);
        status.writeFastExternal(out, serialVersion);
        out.writeBoolean(r2compat);
        writePackedInt(out, schemaId);
        writeFastExternalOrNull(out, serialVersion, owner);
        out.writeBoolean(sysTable);
        writeString(out, serialVersion, namespace);
        writeFastExternalOrNull(out, serialVersion, limits);
        writeFastExternalOrNull(out, serialVersion, identityColumnInfo);
        writeCollectionLength(out, regionIds);
        if (regionIds != null) {
            for (int regionId : regionIds) {
                writePackedInt(out, regionId);
            }
        }

        /*
         * Write children and indexes last because the table instance is passed
         * to their constructors during deserialization.
         */
        writeNonNullSequenceLength(out, children.size());
        for (Table child : children.values()) {
            ((TableImpl)child).writeFastExternal(out, serialVersion);
        }
        writeNonNullSequenceLength(out, indexes.size());
        for (Index index : indexes.values()) {
            ((IndexImpl)index).writeFastExternal(out, serialVersion);
        }
    }

    /*
     * Needed to deserialize an instance of TableImpl via java deserialization.
     * Specifically, it is needed to initialize transient fields not sent in
     * the serialized object.
     */
    private void readObject(java.io.ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        getTableVersion();
        setIdString();
        initializeVersionInfo(true);
        initGeneratedUUIDPos();
    }

    @Override
    public TableImpl clone() {
        return new TableImpl(this);
    }

    /**
     * Sets the sequence number. Note that the sequence number is only set
     * in the top level table instance.
     *
     * @param newSeqNum the new sequence number
     */
    void setSequenceNumber(int newSeqNum) {
        assert newSeqNum >= sequenceNumber;
        getTopLevelTable().sequenceNumber = newSeqNum;
    }

    @Override
    public TableImpl getChildTable(String tableName) {
        return (TableImpl) children.get(tableName);
    }

    @Override
    public boolean childTableExists(String tableName) {
        return children.containsKey(tableName);
    }

    @Override
    public Table getVersion(int version1) {
        if (versions.size() < version1 || version1 < 0) {
            throw new IllegalArgumentException
                ("Table version " + version1 + " does not exist for table " +
                 getFullName());
        }
        TableImpl newTable = clone();
        newTable.setVersion(version1);
        newTable.initializeVersionInfo(true);
        return newTable;
    }

    @Override
    public Map<String, Table> getChildTables() {
        return Collections.unmodifiableMap(children);
    }

    @Override
    public Table getParent() {
        return parent;
    }

    public boolean isTop() {
        return parent == null;
    }

    public RecordDefImpl getValueRecordDef() {
        return getVersionInfo().getValueRecordDef();
    }
    /**
     * Return the current version of this table.  Each time a table
     * is evolved its version number will increment.  A table starts out at
     * version 1.  Check for 0 because the field is transient and will not be
     * set from a deserialized instance.
     */
    @Override
    public int getTableVersion() {
        if (version == 0) {
            setVersion(versions.size());
        }
        return version;
    }

    @Override
    public Index getIndex(String indexName) {
        return indexes.get(indexName);
    }

    /**
     * Get the secondary Index with the given name.  If no such index exists,
     * return null.  If an index with the given name exists, but it is a Text
     * type index, then the exception is thrown.
     */
    public Index getSecondaryIndex(String indexName) {
        Index i = indexes.get(indexName);
        if (i == null || i.getType() == Index.IndexType.SECONDARY) {
            return i;
        }
        throw new IllegalArgumentException("The index named " + indexName +
                                           " is not a secondary index.");
    }

    /**
     * Get the Text Index with the given name.  If no such index exists, return
     * null.  If an index with the given name exists, but it is not a Text type
     * index, then the exception is thrown.
     */
    public Index getTextIndex(String indexName) {
        Index i = indexes.get(indexName);
        if (i == null || i.getType() == Index.IndexType.TEXT) {
            return i;
        }
        throw new IllegalArgumentException("The index named " + indexName +
                                           " is not a text index.");
    }

    @Override
    public Map<String, Index> getIndexes() {
        return Collections.unmodifiableMap(indexes);
    }

    @Override
    public Map<String, Index> getIndexes(Index.IndexType type) {
        Map<String, Index> r = new TreeMap<>();
        for (Entry<String, Index> entry : indexes.entrySet()) {
            if (entry.getValue().getType() == type) {
                r.put(entry.getKey(), entry.getValue());
            }
        }
        return r;
    }

    @Override
    public String getName()  {
        return name;
    }

    /**
     * Get a unique string that identifies the table.  This
     * includes the name(s) of any parent tables.
     */
    @Override
    public String getFullName()  {
        StringBuilder sb = new StringBuilder();
        getTableNameInternal(sb);
        return sb.toString();
    }

    public long getId()  {
        return id;
    }

    public String getIdString()  {
        return idString;
    }

    public byte[] getIDBytes() {
        return idBytes;
    }

    @Override
    public String getDescription()  {
        return description;
    }

    /**
     * Sets the table's description.
     */
    void setDescription(String description) {
        this.description = description;
    }

    @Override
    public List<String> getFields() {
        return Collections.unmodifiableList(getFieldMap().getFieldNames());
    }

    /**
     * Method used to set the current version associated with the table.
     */
    private void setVersion(int currentVersion) {
        assert(currentVersion > 0);
        version = currentVersion;
    }

    @Override
    public FieldDef getField(String fieldName) {
        FieldMapEntry fme = getFieldMapEntry(fieldName, false);
        if (fme != null) {
            return fme.getFieldDef();
        }
        return null;
    }

    @Override
    public boolean isNullable(String fieldName) {

        /* true means throw if the field doesn't exist */
        FieldMapEntry fme = getFieldMapEntry(fieldName, true);
        return fme.isNullable();
    }

    @Override
    public FieldValue getDefaultValue(String fieldName) {

        /* true means throw if the field doesn't exist */
        FieldMapEntry fme = getFieldMapEntry(fieldName, true);
        return fme.getDefaultValue();
    }

    @Override
    public List<String> getPrimaryKey() {
        return Collections.unmodifiableList(primaryKey);
    }

    @Override
    public List<String> getShardKey() {
        return Collections.unmodifiableList(shardKey);
    }

    public List<String> getPrimaryKeyInternal() {
        return primaryKey;
    }

    public int getPrimaryKeySize() {
        return primaryKey.size();
    }

    public List<Integer> getPrimaryKeySizes() {
        return primaryKeySizes;
    }

    public String getPrimaryKeyColumnName(int i) {
        return primaryKey.get(i);
    }

    List<String> getShardKeyInternal() {
        return shardKey;
    }

    public int getShardKeySize() {
        return shardKey.size();
    }

    public boolean isKeyOnly() {
        return getRowDef().getNumFields() == getPrimKeyDef().getNumFields();
    }

    @Override
    public RowImpl createRow() {
        return new RowImpl(getVersionInfo().getRecordDef(), this);
    }

    @Override
    public RowImpl createRow(RecordValue value) {
        if (value instanceof IndexKey) {
            throw new IllegalArgumentException(
                "Index keys cannot be passed to createRow");
        }
        RowImpl row = createRow();
        populateRecord(row, value);
        return row;
    }

    @Override
    public RowImpl createRowWithDefaults() {
        TableVersionInfo info = getVersionInfo();
        RowImpl row = createRow();
        FieldMap fieldMap = getFieldMap();
        for (int pos = 0; pos < fieldMap.size(); ++pos) {
            if (!info.isPrimKeyAtPos(pos)) {
                row.put(pos, fieldMap.getDefaultValue(pos));
            }
        }
        return row;
    }

    @Override
    public PrimaryKeyImpl createPrimaryKey() {
        return new PrimaryKeyImpl(primaryKeyDef, this);
    }

    @Override
    public PrimaryKeyImpl createPrimaryKey(RecordValue value) {
        if (value instanceof IndexKey) {
            throw new IllegalArgumentException(
                "Index keys cannot be passed to createPrimaryKey");
        }

        PrimaryKeyImpl key = new PrimaryKeyImpl(primaryKeyDef, this);
        populateRecord(key, value);
        return key;
    }

    @Override
    public ReturnRowImpl createReturnRow(ReturnRow.Choice returnChoice) {
        return new ReturnRowImpl(
            getVersionInfo().getRecordDef(), this, returnChoice);
    }

    @Override
    public Row createRowFromJson(String jsonInput, boolean exact) {
        return createRowFromJson
            (new ByteArrayInputStream(jsonInput.getBytes()), exact);
    }

    @Override
    public Row createRowFromJson(InputStream jsonInput, boolean exact) {

        RowImpl row = createRow();

        /*
         * Using addMissingFields false to not add missing fields, if Json
         * contains a subset of fields, then build partial row.
         */
        ComplexValueImpl.createFromJson(row, jsonInput, exact,
                                        false /*addMissingFields*/);
        return row;
    }

    @Override
    public PrimaryKeyImpl createPrimaryKeyFromJson(String jsonInput,
                                                   boolean exact) {
        return createPrimaryKeyFromJson
            (new ByteArrayInputStream(jsonInput.getBytes()), exact);
    }

    @Override
    public PrimaryKeyImpl createPrimaryKeyFromJson(InputStream jsonInput,
                                                   boolean exact) {
        PrimaryKeyImpl key = createPrimaryKey();

        /*
         * Using addMissingFields false to not add missing fields, if Json
         * contains a subset of primary key fields, then build partial primary
         * key.
         */
        ComplexValueImpl.createFromJson(key, jsonInput, exact,
                                        false /*addMissingFields*/);
        return key;
    }

    @Override
    public FieldRange createFieldRange(String fieldName) {
        FieldDef def = getField(fieldName);
        if (def == null) {
            throw new IllegalArgumentException
                ("Field does not exist in table definition: " + fieldName);
        }
        if (!primaryKey.contains(fieldName)) {
            throw new IllegalArgumentException
                ("Field does not exist in primary key: " + fieldName);
        }
        return new FieldRange(fieldName, def, getPrimaryKeySize(fieldName));
    }

    @Override
    public MultiRowOptions createMultiRowOptions
        (List<String> tableNames, FieldRange fieldRange) {

        if ((tableNames == null || tableNames.isEmpty()) &&
            fieldRange == null) {
            throw new IllegalArgumentException
                ("createMultiRowOptions must have at least one non-null " +
                 "parameter");
        }

        MultiRowOptions mro = null;
        if (fieldRange != null) {
            mro = new MultiRowOptions(fieldRange);
        }

        if (tableNames != null) {
            List<Table> ancestorTables = new ArrayList<>();
            List<Table> childTables =  new ArrayList<>();
            TableImpl topLevelTable = getTopLevelTable();
            for (String tableName : tableNames) {
                TableImpl t = topLevelTable.findTable(tableName);
                if (t == this) {
                    throw new IllegalArgumentException
                        ("Target table must not appear in included tables list");
                }
                if (isAncestorOf(this, t)) {
                    ancestorTables.add(t);
                } else {
                    assert isAncestorOf(t, this);
                    childTables.add(t);
                }
            }
            if (mro == null) {
                mro = new MultiRowOptions(null, ancestorTables, childTables);
            } else {
                mro.setIncludedParentTables(ancestorTables);
                mro.setIncludedChildTables(childTables);
            }
        }
        return mro;
    }

    /**
     * Returns the size contstraint for the named primary key field, or 0
     * if there is none. This assumes that the field name has already been
     * validated as a primary key field.
     */
    public int getPrimaryKeySize(String keyName) {
        if (primaryKeySizes != null) {
            return primaryKeySizes.get(primaryKey.indexOf(keyName));
        }
        return 0;
    }

    public int getPrimaryKeySize(int pos) {
        if (primaryKeySizes != null) {
            return primaryKeySizes.get(pos);
        }
        return 0;
    }

    /**
     * Return true if ancestor is an ancestor of this table.   Match on
     * full name only.  Equality isn't needed here.
     */
    public boolean isAncestor(Table ancestor) {
        Table parentTable = getParent();
        String fullName = ancestor.getFullName();
        while (parentTable != null) {
            if (fullName.equals(parentTable.getFullName())) {
                return true;
            }
            parentTable = parentTable.getParent();
        }
        return false;
    }

    /**
     * Return the top-level for this table.
     */
    public TableImpl getTopLevelTable() {
        if (parent != null) {
            return parent.getTopLevelTable();
        }
        return this;
    }

    /**
     * Returns true if this is a multi-region table.
     */
    public boolean isMultiRegion() {
        return regionIds != null;
    }

    /**
     * Returns true if table has an UUID column.
     */
    public boolean hasUUIDcolumn() {
        return (generatedUUIDPosition != -2);
    }

    /**
     * Returns true if the column of specified position is defined as
     * "STRING AS UUID GENERATED BY DEFAULT"
     */
    public boolean isGeneratedByDefault(int pos){
        return (generatedUUIDPosition == pos);
    }

    public int getGeneratedColumn(){
        return generatedUUIDPosition;
    }

    /**
     * Initialize the generated UUID column position.
     */
    private void initGeneratedUUIDPos() {
        generatedUUIDPosition = -2;
        FieldMap fieldMap = getFieldMap();
        for (int pos = 0; pos < fieldMap.size(); ++pos) {
            FieldMapEntry fme = fieldMap.getFieldMapEntry(pos);
            if (fme.getFieldDef().isUUIDString()) {
                generatedUUIDPosition = -1;
                if (((StringDefImpl)fme.getFieldDef()).isGenerated()) {
                    generatedUUIDPosition = pos;
                    break;
                }
            }
        }
    }

    /**
     *  Generates a UUID string and put it into the field
     *  defined as 'string as uuid generated by default'.
     *
     *  @param row
     */
    public void setUUIDDefaultValue(RowImpl row) {
        if (generatedUUIDPosition >= 0 ) {
            FieldValueImpl res = null;
            if (row.get(generatedUUIDPosition) == null ||
                row.get(generatedUUIDPosition).isEMPTY()) {
                String uuidStr = UUID.randomUUID().toString();
                res = new StringValueImpl(uuidStr);
                row.put(generatedUUIDPosition, res);
            }
        }
    }

    static FieldValueImpl getGeneratedUUID(GeneratedValueInfo genInfo,
                                           RecordValueSerializer row,
                                           int pos) {
        String uuidStr = UUID.randomUUID().toString();
        FieldValueImpl value = new StringValueImpl(uuidStr);
        if (row instanceof RecordValueImpl) {
            /* update the row with the generated value */
            ((RecordValueImpl) row).putInternal(pos, value, false);
        }
        if (genInfo != null) {
            genInfo.setGeneratedValue(value);
        }
        return value;
    }

    /**
     * Unit test only. A work-around to check if a table is PITR table.
     * @return true if the table is a PITR table
     */
    //TODO: remove after table impl supports PITR
    public boolean isPITR() {
        return getFullName().startsWith("PITR");
    }

    /**
     * Returns the set of remote regionIds this table subscribes to or null if
     * this table is not multi-region. Note that the set may be empty if the
     * multi-region table is not currently subscribed to any remote regions.
     */
    public Set<Integer> getRemoteRegions() {
        return regionIds;
    }

    /**
     * Returns true if the table is subscribed to the specified region.
     */
    boolean inRegion(int regionId) {
        return regionIds == null ? false : regionIds.contains(regionId);
    }

    /**
     * Determine equality.  Use name, parentage, version, field definitions
     * and default TTL.
     */
    @Override
    public boolean equals(Object other) {
        if (other != null && other instanceof Table) {
            TableImpl otherDef = (TableImpl) other;

            if (NameUtils.namespaceEquals(getInternalNamespace(),
                    otherDef.getInternalNamespace()) &&
                getName().equalsIgnoreCase(otherDef.getName()) &&
                idsEqual(otherDef)) {
                if (getParent() != null) {
                    if (!getParent().equals(otherDef.getParent())) {
                        return false;
                    }
                } else if (otherDef.getParent() != null) {
                    return false;
                }
                if (!equalsTTL(ttl, otherDef.ttl)) {
                    return false;
                }
                if (!equalsPKSizes(primaryKeySizes,
                                   otherDef.primaryKeySizes)) {
                    return false;
                }
                return (versionsEqual(otherDef) &&
                        getFieldMap().equals(otherDef.getFieldMap()));
            }

        }
        return false;
    }

    /*
     * Compares ids, matching an id of 0 as ok against any actual versioned id.
     * This allows transient tables to compare correctly to persistent onces
     * when everything but the id matches.
     */
    private boolean idsEqual(TableImpl other) {
        if ((getId() == other.getId()) ||
            (getId() == 0 || other.getId() == 0)) {
            return true;
        }
        return false;
    }

    private static boolean equalsTTL(TimeToLive ttl, TimeToLive ottl) {
        if (ttl != null) {
            return ttl.equals(ottl);
        }
        return (ottl == null);
    }

    private static boolean equalsPKSizes(final List<Integer> pks,
                                         final List<Integer> opks) {
        if (pks != null) {
            if (opks != null && (pks.size() == opks.size())) {
                for (int i = 0; i < pks.size(); i++) {
                    if (pks.get(i) != opks.get(i)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        return (opks == null);
    }

    /**
     * Determine equality using only name, fields and keys, ignoring version and
     * other persistent-only state.
     */
    public boolean fieldsEqual(Object other) {
        if (other != null && other instanceof Table) {
            TableImpl otherTable = (TableImpl) other;
            if (getName().equalsIgnoreCase(otherTable.getName())) {
                if (parent != null) {
                    if (!parent.fieldsEqual(otherTable.parent)) {
                        return false;
                    }
                } else if (otherTable.parent != null) {
                    return false;
                }
                /*
                 * Consider the fields equal if these match:
                 *  fields, primary key, shard key
                 */
                return (getFieldMap().equals(otherTable.getFieldMap()) &&
                        primaryKey.equals(otherTable.primaryKey) &&
                        shardKey.equals(otherTable.shardKey));
            }
        }
        return false;
    }

    /**
     * More could be added, but this is enough to uniquely identify tables
     * users have obtained.
     */
    @Override
    public int hashCode() {
        return getFullName().hashCode() + versions.size() +
            getFieldMap().hashCode()
          + (getDefaultTTL() != null ? getDefaultTTL().hashCode() : 0);
    }

    boolean nameEquals(TableImpl other) {
        return getFullNamespaceName().equals(other.getFullNamespaceName());
    }

    private boolean versionsEqual(TableImpl other) {
        int thisVersion = (version == 0 ? versions.size() : version);
        int otherVersion = (other.version == 0 ? other.versions.size() :
                            other.version);
        return (thisVersion == otherVersion);
    }

    @Override
    public int numTableVersions() {
        return versions.size();
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Return true if the table is an overlay over Avro key/value records.
     */
    public boolean isR2compatible() {
        return r2compat;
    }

    /**
     * Return the Avro schema ID if this table overlays an R2 table, 0
     * otherwise.
     */
    public int getSchemaId() {
        return schemaId;
    }

    /*
     * This is the only call that sets the table id.  It is called when a table
     * object is created in TableMetadata.
     */
    void setId(long id)  {
        this.id = id;
        setIdString();
    }

    private void setIdString() {
        if (id == 0 || r2compat) {
            idString = name;
        } else {
            idString = createIdString(id);
        }
        idBytes = UtfOps.stringToBytes(idString);
    }

    /**
     * Creates the string used for table keys.  This is separate so it
     * can be used by test code.
     */
    public static String createIdString(long id) {
        int encodingLength = SortableString.encodingLength(id);
        return SortableString.toSortable(id, encodingLength);
    }

    /**
     * Creates the numeric table id from the table id string, reverse of
     * TableImpl.createIdString()
     *
     * @param idStr the table ID String
     * @return the numerical table ID converted from given id string
     */
    public static long createIdFromIdStr(String idStr) {
        return SortableString.longFromSortable(idStr);
    }

    public FieldMap getFieldMap() {
        return getFieldMap(version);
    }

    /**
     * The number of key components for a unique primary key for this table.
     * This number is used to perform relatively efficient filtering of
     * keys on both client and server side as necessary.
     * NOTE: this could be made persistent but it's easily calculated and
     * cached.
     */
    public int getNumKeyComponents() {
        if (numKeyComponents == 0) {
            calculateNumKeys();
        }
        return numKeyComponents;
    }

    /*
     * This is separate from above so that setting the value is synchronized.
     * The number is:
     * 1.  The size of the primary key plus
     * 2.  One for each table in its hierarchy (including itself) plus
     */
    private synchronized void calculateNumKeys() {
        if (numKeyComponents == 0) {
            int num = primaryKey.size() + 1;
            TableImpl t = this;
            while (t.parent != null) {
                ++num;
                t = t.parent;
            }
            numKeyComponents = num;
        }
    }

    public TableStatus getStatus() {
        return status;
    }

    /**
     * Returns true if this table is being deleted
     */
    public boolean isDeleting() {
        return status.isDeleting();
    }

    public synchronized void setStatus(TableStatus newStatus) {
        if ((status != newStatus) && status.isDeleting()) {
            throw new IllegalStateException("Table is being deleted, cannot " +
                                            "change status to " + newStatus);
        }
        status = newStatus;
    }

    /**
     * Adds the specified child table. If a child table exist with the same name
     * it is replaced.
     */
    public void addChild(TableImpl child) {
        if (regionIds != null) {
            throw new IllegalArgumentException("A multi-region table cannot" +
                                               " have child tables");
        }
        children.put(child.getName(), child);
    }

    void removeChild(String childName) {
        children.remove(childName);
    }

    /*
     * See below.  This is used internally and by TableBuilder.
     * TODO: should the accessor methods in this class default to allowing
     * nested paths?  Perhaps so...
     */
    FieldMapEntry getFieldMapEntry(String fieldName,
                                   boolean mustExist) {

        FieldMap fieldMap = getFieldMap();
        FieldMapEntry fme = fieldMap.getFieldMapEntry(fieldName);
        if (fme != null) {
            return fme;
        }
        if (mustExist) {
            throw new IllegalArgumentException
                ("Field does not exist in table definition: " + fieldName);
        }
        return null;
    }

    Map<String, Index> getMutableIndexes() {
        return indexes;
    }

    /**
     * If this table has a parent return its fully-qualified name, otherwise
     * null.
     */
    public String getParentName() {
        if (parent != null) {
            return parent.getFullName();
        }
        return null;
    }

    public Key createKey(Row row, boolean allowPartial) {
        return createKeyInternal((RowSerializer)row, allowPartial);
    }

    public Key createKeyInternal(RowSerializer row, boolean allowPartial) {
        return createKeyInternal(row, allowPartial, null, null);
    }

    public Key createKeyInternal(RowSerializer row, boolean allowPartial,
                                 KVStoreImpl store,
                                 GeneratedValueInfo genInfo) {
        if (row instanceof RowImpl) {
            setTableVersion((RowImpl)row);
        }
        TableKey key = TableKey.createKeyInternal(this, row, allowPartial,
            store, genInfo);
        row.validateKey(key);
        return key.getKey();
    }


    /**
     * Create a Row object with all values for the primary key,
     * extracted from the byte[] array that is the store key.
     *
     * This method, and createPrimaryKeyFromBytes are lenient with
     * respect to failures and return null if they fail to match
     * a table.  This is necessary for mixed access between tables and
     * potentially matching key/value records.
     *
     * This is public so that code in api/ops can use it.
     */
    public RowImpl createRowFromKeyBytes(byte[] keyBytes) {
        return createFromKeyBytes(keyBytes, false);
    }

    /**
     * PrimaryKey version of createRowFromKeyBytes.
     */
    public PrimaryKeyImpl createPrimaryKeyFromKeyBytes(byte[] keyBytes) {
        return (PrimaryKeyImpl) createFromKeyBytes(keyBytes, true);
    }

    PrimaryKeyImpl createPrimaryKeyFromResultKey(ResultKey rkey) {
        PrimaryKeyImpl pkey =
            (PrimaryKeyImpl) createFromKeyBytes(rkey.getKeyBytes(), true);
        if (pkey != null) {
            pkey.setExpirationTime(rkey.getExpirationTime());
        }
        return pkey;
    }

    /**
     * Creates a RowImpl or PrimaryKeyImpl (which is a RowImpl) from keyBytes,
     * which is the serialized format of a Primary Key.
     *
     * @param keyBytes the serialized key
     *
     * @param createPrimaryKey if true, a PrimaryKeyImpl is created; otherwise,
     * a RowImpl is created.
     *
     * @return RowImpl, which may be a PrimaryKeyImpl.
     */
    private RowImpl createFromKeyBytes(byte[] keyBytes,
                                       boolean createPrimaryKey) {

        BinaryKeyIterator keyIter = createBinaryKeyIterator(keyBytes);

        if (keyIter == null) {
            return null;
        }

        TableImpl targetTable = findTargetTable(keyIter);

        if (targetTable == null) {
            return null;
        }

        keyIter.reset();

        ArrayPosition currentPrimKeyPos =
            new ArrayPosition(targetTable.primaryKey.size());

        RowImpl row = (createPrimaryKey ?
                       targetTable.createPrimaryKey() :
                       targetTable.createRow());

        ValueReader<RowImpl> reader = targetTable.initRowReader(row);
        reader.setTableVersion(targetTable.getTableVersion());

        if (targetTable.initRowFromKeyBytes(targetTable,
                                            currentPrimKeyPos,
                                            keyIter,
                                            -1 /*initPos*/,
                                            row.getDefinition(),
                                            createPrimaryKey,
                                            reader)) {
            return reader.getValue();
        }
        return null;
    }

    /**
     * Turn the server-side byte arrays into a Row for index
     * key extraction.
     *
     * If there is a failure of any sort return null.  This method
     * needs to be flexible to work with mixed KV and table access.
     * It also cannot throw an exception or the server would die.
     *
     * One caller of this method is IndexImpl.extractIndexKey(s).
     *
     * Another caller is OpenTransactionBuffer.deserializeRow() in package
     * kv.impl.pubsub, by the subscriber to convert raw bytes received from
     * source KV store to a row of a subscribed table.
     */
    @SuppressWarnings("deprecation")
    public RowImpl createRowFromBytes(byte[] keyBytes,
                                      byte[] valueBytes,
                                      boolean keyOnly,
                                      boolean addMissingCol) {

        RowImpl fullKey = createRowFromKeyBytes(keyBytes);
        /*
         * If createRowFromKeyBytes returns null, then the serialized key
         * doesn't match the table's key.  It may, however, return a false
         * positive if the key belongs to a descendent in the parent-child
         * table hierarchy.  Hence the extra test for matching table Ids.
         */
        if (fullKey != null && getId() == fullKey.getTableImpl().getId()) {
            /*
             * valueBytes.length == 0 implies that when the row was created
             * the table was a key-only table.  However, the table may have
             * evolved since then, so we must add any missing fields using
             * their default values.
             */
            if (keyOnly || valueBytes.length == 0) {
                if (addMissingCol && !keyOnly &&
                    getPrimaryKeySize() != getRowDef().getNumFields()) {
                    fullKey.addMissingFields();
                }

                return fullKey;
            }

            Value.Format format = Value.Format.fromFirstByte(valueBytes[0]);
            if (Format.isTableFormat(format) ||
                (format == Value.Format.AVRO && r2compat)) {
                ValueReader<RowImpl> reader = initRowReader(fullKey);
                int offset = 1;
                if (format == Value.Format.AVRO && r2compat) {
                    offset =
                        PackedInteger.getReadSortedIntLength(valueBytes, 0);
                } else if (format == Value.Format.MULTI_REGION_TABLE) {
                    final int regionIdLen =
                        PackedInteger.getReadIntLength(valueBytes, 1);
                    final int regionId = PackedInteger.readInt(valueBytes, 1);
                    offset = regionIdLen + 1;
                    reader.setRegionId(regionId);
                }

                if (initRowFromByteValue(reader, valueBytes,
                                         format, offset)){
                    return reader.getValue();
                }
            }
        }
        return null;
    }

    public RowImpl createRowFromBytes(byte[] keyBytes,
                                      byte[] valueBytes,
                                      boolean keyOnly) {
        return createRowFromBytes(keyBytes, valueBytes, keyOnly, true);
    }

    /**
     * This method is used by the query runtime code. It is used by the
     * initRowFromBytes() method above, in which case the row param is a
     * RowImpl, and from IndexImpl.rowFromIndexEntry(), in which case the
     * row param is a RecordValueImpl that is supposed to store a deserialized
     * index entry. In the later case, the initPos param is the position of
     * the 1st primary-key column within the index entry.
     */
    public boolean initRowFromKeyBytes(byte[] keyBytes,
                                       int initPos,
                                       RecordValueImpl row) {
        final ValueReader<?> reader = new FieldValueReaderImpl<>(row);
        return initRowFromKeyBytes(keyBytes, initPos, row.getDefinition(),
            reader);
    }

    private boolean initRowFromKeyBytes(byte[] keyBytes,
                                        int initPos,
                                        RecordDefImpl recordDef,
                                        ValueReader<?> reader) {

        final ArrayPosition currentPrimKeyPos =
            new ArrayPosition(getPrimaryKeySize());

        final BinaryKeyIterator keyIter =
            new BinaryKeyIterator(keyBytes);

        return initRowFromKeyBytes(this,
                                   currentPrimKeyPos,
                                   keyIter,
                                   initPos,
                                   recordDef,
                                   false,
                                   reader);
    }

    /**
     * Deserialize a binary primary key, and use the extracted values to fill-in
     * the corresponding fields of given a RowImpl or PrimaryKeyImpl or a
     * RecordValueImpl. The binary prim key is given as a BinaryKeyIterator.
     * The given "row" is associated with a given "targetTable".
     *
     * When the target is a RecordValueImpl, the method is called from the
     * initRowFromKeyBytes() above. In this case, the RecordValueImpl is
     * supposed to store a deserialized index entry, and the initPos param is
     * the position of the 1st primary-key column within the index entry.
     * If the prim key consists of N columns, the values of these columns are
     * stored at positions initPos to initPos + N - 1, withing the target
     * RecordValueImpl.
     *
     * When the target is a RowImpl or PrimaryKeyImpl, initPos is not used.
     *
     * Notice that the binary primary key contains the internal ids of the
     * targetTable and its ancestors (if any). As a result, this method calls
     * itself recursively on the ancestor tables in order to deserialize and
     * skip their table ids. Each ancestor table deserializes its portion of
     * the prim key as well and fills-in the target RowImpl/PrimaryKeyImpl.
     *
     * This method should only be called for Key objects from the store so they
     * are well-formed in terms of the expected layout.  It does have to be
     * defensive in the face of keys that match a table in structure but
     * have values that can't be deserialized correctly.  This can happen
     * if there is mixed access between KV and table applications.  An example
     * is a too-long string that can't be turned into an integer.
     *
     * Unfortunately if the key really isn't supposed to be in the table AND
     * it deserializes without an exception this will succeed.  For this,
     * and other reasons, mixing keyspace for tables and non-tables is
     * not supported.
     *
     * @return true if the key was deserialized in full, false otherwise.
     *
     * This method must not throw exceptions.
     */
    private boolean initRowFromKeyBytes(TableImpl targetTable,
                                        ArrayPosition currentPrimKeyColumn,
                                        BinaryKeyIterator keyIter,
                                        int initPos,
                                        RecordDefImpl recordDef,
                                        boolean createPrimaryKey,
                                        ValueReader<?> reader) {
        if (parent != null) {
            if (!parent.initRowFromKeyBytes(targetTable,
                                            currentPrimKeyColumn,
                                            keyIter,
                                            initPos,
                                            recordDef,
                                            createPrimaryKey,
                                            reader)) {
                return false;
            }
        }
        assert !keyIter.atEndOfKey();

        String keyComponent = keyIter.next();

        if (!keyComponent.equals(getIdString())) {
            return false;
        }

        int lastPrimKeyCol = primaryKey.size() - 1;

        /*
         * Fill in values for primary key components that belong to this
         * table only.
         */
        while (currentPrimKeyColumn.hasNext()) {

            int pos = currentPrimKeyColumn.next();

            assert !keyIter.atEndOfKey();

            /* The position within "row" where to insert the next field */
            int pkFieldPos;

            if (initPos >= 0) {
                pkFieldPos = initPos + pos;
            } else if (createPrimaryKey) {
                pkFieldPos = pos;
            } else {
                pkFieldPos = targetTable.getPrimKeyPos(pos);
            }

            String val = keyIter.next();
            FieldDefImpl def = recordDef.getFieldDef(pkFieldPos);
            String fname = recordDef.getFieldName(pkFieldPos);
            try {
                readFieldValue(reader, fname,
                    FieldDefImpl.createValueFromKeyString(val, def));
            } catch (Exception e) {
                return false;
            }

            if (pos == lastPrimKeyCol) {
                break;
            }
        }

        return true;
    }

    /**
     * Size of the value is the length of the serialized value plus
     * a format byte and a region id byte for multi-region tables.
     *
     * TODO: if zero-length empty values are supported, don't add one.
     */
    int getDataSize(Row row) {
        if (isMultiRegion()) {
            TableAPIImpl.setLocalRegionId((RowImpl)row);
        }
        Value value = createValue(row);
        return value.getValue().length + 1;
    }

    int getKeySize(Row row) {
        return createKey(row, true).toByteArray().length;
    }

    /**
     * Serialize the non-key fields into an Avro record.
     * Special cases:
     * 1. NullValue in a nullable field.  Avro wants these to be null entries
     * in the record.  Similarly, on reconstruction (rowFromValue) null Avro
     * record entries turn into NullValue instances in the Row.
     * 2. Default values.  If a field is both optional AND not set in the Row,
     * put its default value into the Avro record.  Required fields are just
     * that -- required.
     */
    public Value createValue(Row row) {
        return createValueInternal((RowSerializer) row);
    }

    public Value createValueInternal(RowSerializer row) {
        return createValueInternal(row, null, null);
    }

    public Value createValueInternal(RowSerializer row,
                                     KVStoreImpl store,
                                     GeneratedValueInfo genInfo) {
        final short opSerialVersion = (testCurrentSerialVersion != 0) ?
            testCurrentSerialVersion : SerialVersion.CURRENT;
        Value value = createValueInternal(row, opSerialVersion, store,
                                          genInfo);
        row.validateValue(value);
        return value;
    }

    @SuppressWarnings("deprecation")
    private Value createValueInternal(RowSerializer row,
                                      short opSerialVersion,
                                      KVStoreImpl store,
                                      GeneratedValueInfo genInfo) {

        Format valFormat = getValueFormat(opSerialVersion);

        if (getValueRecordDef() == null) {
            return Value.internalCreateValue(new byte[0], valFormat,
                row.isFromMRTable() ? row.getRegionId() : Region.NULL_REGION_ID);
        }

        boolean isAvro = (schemaId != 0 && getTableVersion() == 1);
        final ByteArrayOutputStream outputStream =
            new ByteArrayOutputStreamWithInitialBuffer(
                createValueBuffer.get());

        /*
         * If this is a normal table, write the table/schema version to the
         * stream.
         *
         * If this is a table that overlays R2 (Avro) data and it has not been
         * evolved (which excludes direct KV access) then it must be
         * written using the AVRO Value.Format in order to be readable by
         * a pure key/value application doing mixed access.
         * Evolved R2 table overlays will have a table version > 1.
         */
        if (!isAvro) {
            int writeVersion = getTableVersion();
            outputStream.write(writeVersion);
            if (row instanceof RowImpl) {
                setTableVersion((RowImpl) row);
            }
        } else {
            final int size =
                PackedInteger.getWriteSortedIntLength(schemaId);
            final byte[] buf = new byte[size];
            /* Copy in the schema ID. */
            PackedInteger.writeSortedInt(buf, 0, schemaId);
            outputStream.write(buf, 0, size);
            if (row instanceof RowImpl) {
                ((RowImpl) row).setTableVersion(1);
            }
        }

        final BinaryEncoder e = TableJsonUtils.getEncoderFactory().
            binaryEncoder(outputStream, encoderBuffer.get());

        try {
            writeAvroRecord(e, row, true, valFormat, store, genInfo);
            e.flush();
            return Value.internalCreateValue
                (outputStream.toByteArray(),
                 isAvro ? Value.Format.AVRO : valFormat,
                 (valFormat == Format.MULTI_REGION_TABLE ?
                     row.getRegionId() : Region.NULL_REGION_ID));
        } catch (IOException ioe) {
            throw new IllegalCommandException("Failed to serialize Avro: " +
                                              ioe);
        }
    }

    /**
     * A ByteArrayOutputStream that uses the specified byte array as its
     * initial buffer.
     */
    private static class ByteArrayOutputStreamWithInitialBuffer
            extends ByteArrayOutputStream
    {
        ByteArrayOutputStreamWithInitialBuffer(final byte[] buffer) {

            /*
             * The only superclass constructors create a byte buffer, so create
             * the smallest possible one and then override with the shared
             * buffer
             */
            super(0);
            buf = buffer;
        }
    }

    /**
     * Deserialize the record value that is encoded in Avro.
     *
     * Offset is requires because on the client side the byte offset is 0 but
     * on the server side a "raw" database record is used which includes an
     * empty first byte added by the system.
     *
     * There is a special case where the table version cannot be acquired.
     * When a key-only table has a non-key field added (the only evolution
     * that can happen for key-only tables, really), there may be empty
     * records in which case the data array is empty.  In this case
     * there may be schema-evolved fields that need to be defaulted so
     * this method must be called regardless of data length.
     *
     * R2/KV compatibility NOTE:
     * If the table overlays R2 (KV) data, treat it specially because it
     * may not have a table version in the data.  Unevolved R2 overlays
     * will have table version 1 and the data will start with the encoded
     * schema id.  Evolved R2 overlays will have table version &gt; 1 and
     * values may either (1) have the encoded schema id (first byte &lt; 0) or
     * be newly-written values, which will have the table format (1) as
     * the first byte and table version used for write as the second byte.
     *
     * This is public to allow access from the query processor.
     */
    public boolean initRowFromByteValue(RowImpl row,
                                        byte[] data,
                                        Value.Format format,
                                        int offset) {
        ValueReader<RowImpl> reader = initRowReader(row);
        return initRowFromByteValue(reader, data, format, offset);
    }

    /**
     * This method is used by the query runtime code (ServerTableIter) to
     * fillin a table row from the binary key and value of the row. In this
     * case we know that the binary key belongs to "this" table, so there is
     * no need to call findTargetTable().
     */
    public boolean initRowFromKeyValueBytes(
        byte[] keyBytes,
        byte[] valueBytes,
        long expTime,
        long modTime,
        Version vers,
        int partition,
        int storageSize,
        RowImpl row) {

        ValueReader<RowImpl> reader = initRowReader(row);

        if (!initRowFromKeyBytes(keyBytes,
                                 -1, /*initPos*/
                                 row.getDefinition(),
                                 reader)) {
            return false;
        }

        row = initRowFromValueBytes(row, valueBytes, expTime, modTime,
                                    vers, partition, -1, storageSize);
        return (row != null ? true : false);
    }

    /**
     * Used by query.
     */
    public RowImpl initRowFromValueBytes(
        RowImpl row,
        byte[] data,
        long expTime,
        long modTime,
        Version vers,
        int partition,
        int shard,
        int storageSize) {

        if (!isTableData(data, this)) {
            return null;
        }

        if (data == null || data.length == 0) {

            /*
             * A key-only row, no data to fetch. However, the table may
             * have evolved and it now contains non-prim-key columns as
             * well. So, we must fill the missing columns with their
             * default values.
             */
            if (row.getNumFields() > getPrimaryKeySize()) {
                row.removeValueFields();
                row.addMissingFields();
            }

            row.setExpirationTime(expTime);
            row.setModificationTime(modTime);
            row.setVersion(vers);
            row.setPartition(partition);
            row.setShard(shard);
            row.setStorageSize(storageSize);
            return row;
        }

        Value.Format format = Value.Format.fromFirstByte(data[0]);
        int offset = 1;

        if (!Value.Format.isTableFormat(format)) {
            return null;
        }

        /* multi-region table */
        if (Format.MULTI_REGION_TABLE.equals(format)) {
            final int regionIdLen = PackedInteger.getReadIntLength(data, 1);
            int regionId = PackedInteger.readInt(data, 1);
            offset = regionIdLen + 1;
            row.setRegionId(regionId);
        }

        if (initRowFromByteValue(row, data, format, offset)) {
            row.setExpirationTime(expTime);
            row.setModificationTime(modTime);
            row.setPartition(partition);
            row.setShard(shard);
            row.setStorageSize(storageSize);
            row.setVersion(vers);
            return row;
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    private boolean initRowFromByteValue(ValueReader<?> rowReader,
                                         byte[] data,
                                         Value.Format format,
                                         int offset) {

        /*
         * Decode the serialized data if the record is not key-only
         */
        if (data.length >= (offset + 1)) {
            RecordDefImpl recordDef = getValueRecordDef();

            if (recordDef == null) {
                /*
                 * this is a table that evolved from not-key-only to key-only.
                 */
                return true;
            }
            int tableVersion = (format == Value.Format.AVRO ? 1
                                : data[offset]);
            rowReader.setTableVersion(tableVersion);
            /*
             * If table versions don't match get the writer schema unless
             * this table overlays KV records in which case there won't be
             * a valid table version in the first byte.
             */
            if (tableVersion != getTableVersion() &&
                tableVersion > numTableVersions()) {
                /*
                 * Throw TableVersionException so the caller can
                 * get the appropriate metadata and retry or take
                 * other appropriate action.
                 */
                throw new TableVersionException(tableVersion);
            }

            try {
                if (tableVersion != getTableVersion()) {
                    TableVersionInfo info = getVersionInfo(tableVersion);
                    recordDef = info.getValueRecordDef();
                }

                /*
                 * If a "normal" table, or operating on the client side (offset
                 * 0), move the offset past table version byte.
                 */
                if (!(format == Value.Format.AVRO) || offset == 0) {
                    offset += 1;
                }
                Decoder decoder =
                    TableJsonUtils.getDecoderFactory().binaryDecoder
                    (data, offset, (data.length - offset), null);

                SimpleAvroReader reader = new SimpleAvroReader(recordDef,
                        getValueRecordDef(), rowReader, format);
                reader.read(decoder);
                return true;
            } catch (Exception e) {
                /*
                 * Exception is a big catch-all. It's possible to enumerate
                 * the possibilities, but they all end up returning false.
                 * The reason to do this might be client-side logging but
                 * there's no easy way to get the logger from here.
                 *
                 * This is used on the server side in queries. That path might
                 * pass a logger (TBD).
                 */
                return false;
            }
        }

        fillInDefaultValues(rowReader);
        return true;
    }

    /**
     * Return true if this data value is, or could be, from a table.
     * Could be means that if it's null or an Avro value it may, or
     * may not be from a table.
     */
    public static boolean isTableData(byte[] data, TableImpl table) {

        if (data == null ||      // not known
            data.length == 0 ||  // not known
            Value.Format.isTableFormat(data[0]) ||  // TABLE format
            /* accept NONE format if length is 1 */
            (data.length == 1 && data[0] == 0)  ||
            (data[0] < 0 && (table == null || (table.isR2compatible())))) {
            return true;
        }
        return false;
    }

    /*
     * The stored record was key-only. In the face of possible schema
     * evolution this does not mean that the returned row should not
     * contain any fields. It's possible that the current (expected)
     * table version has added non-key fields to a previously key-only
     * table. Such fields need to either be initialized with null or
     * default values.
     *
     * Default values for new fields added to non-key-only tables are
     * handled in the decoding code called above (SimpleAvroReader)
     * because default values are returned by the ResolvingDecoder.
     */
    private void fillInDefaultValues(ValueReader<?> reader) {
        TableVersionInfo info = getVersionInfo();
        int numFields = reader.getTable().getFields().size();
        for (int pos = 0; pos < numFields; ++pos) {

            FieldMapEntry fme = getFieldMap().getFieldMapEntry(pos);
            String fname = fme.getFieldName();
            if (!info.isPrimKeyAtPos(pos)) {
                if (fme.hasDefaultValue()) {
                    readFieldValue(reader, fname, fme.getDefaultValue());
                } else if (fme.isNullable()) {
                    reader.readNull(fname);
                }
            }
        }
    }

    /**
     * Initializes a Row from the Value.
     */
    public boolean rowFromValueVersion(ValueVersion vv, RowImpl row) {

        assert row != null;
        ValueReader<RowImpl> reader = initRowReader(row);
        return readRowFromValueVersion(reader, vv);
    }

    @SuppressWarnings("deprecation")
    public boolean readRowFromValueVersion(ValueReader<?> reader,
                                           ValueVersion vv) {

        assert reader != null;

        reader.setVersion(vv.getVersion());
        if (vv.getValue() == null) {
            /* key-only, set table version */
            reader.setTableVersion(getTableVersion());
            return true;
        }

        byte[] data = vv.getValue().getValue();

        /*
         * If the value is not the correct format this is a non-table
         * record, skip it silently.  Empty table records will have the
         * TABLE format as well as data.length == 0.  Empty table records
         * (and empty KV records) are not distinguishable so let them pass.
         */
        Value.Format format = vv.getValue().getFormat();

        /* Invoke the test hook to check value format if it is set */
        if (checkDeserializeValueFormatHook != null) {
            checkDeserializeValueFormatHook.doHook(format);
        }

        if (!Format.isTableFormat(format) &&
            (format != Value.Format.AVRO || !r2compat) &&
            (data.length > 1)) {

            return false;
        }

        if (format == Value.Format.MULTI_REGION_TABLE) {
            reader.setRegionId(vv.getValue().getRegionId());
        }

        /*
         * Do the check for schema after the check for the correct format
         * to filter out non-table rows in the case where the table is key-only
         * and there is a KV key in the key space that doesn't belong to the
         * table. If there is no schema the table is currently key-only, which
         * means that all non-key fields should be null, and there are no
         * default values, so just return.
         */
        if (getValueRecordDef() == null) {
            return true;
        }

        return initRowFromByteValue(reader, data, format, 0);
    }

    /**
     * Gets the limits governing this table. If there are no limits set null
     * is returned.
     */
    public TableLimits getTableLimits() {
        final TableImpl top = getTopLevelTable();
        return top.limits;
    }

    /**
     * Sets the limits for this table. Throws an IllegalCommandException if
     * this table is not a top level table or is a system table.
     */
    final public void setTableLimits(TableLimits newLimits) {
        if(!isTop()) {
            throw new IllegalCommandException("Cannot set limits on child " +
                                              "table " + name);
        }
        if (isSystemTable()) {
            throw new IllegalCommandException("Cannot set limits on system " +
                                              "table " + name);
        }
        if (newLimits == null) {
            limits = null;
            return;
        }

        /*
         * Ensure that all of the limits have been initialized. This will copy
         * values from the previous limits, if any, for any values in the
         * new limits which have not been set.
         */
        newLimits.init(limits);

        /* Make sure the new limits are valid */
        validateNewLimits(newLimits);

        /* As an optimization, if there are no limits, simply store null */
        limits = newLimits.hasLimits() ? newLimits : null;
    }

    /**
     * Validates the new limits. The rules are:
     *  1) cannot set child table limits below the number of existing child
     *     tables
     *  2) cannot set index limit below the number of existing indexes
     *  3) cannot decrease the index key size limit if there are indexes
     */
    private void validateNewLimits(TableLimits newLimits) {

        /* Can't set child table limits below existing count */
        if (newLimits.hasChildTableLimit()) {
            final int numChildTables = countChildren(this);
            if (numChildTables > newLimits.getChildTableLimit()) {
                throw new IllegalCommandException("Cannot set child table" +
                                                  " limit below number of" +
                                                  " existing child tables: " +
                                                  numChildTables);
            }
        }

        final int numIndexes = countIndexes(this);

        /* Can't set index limits below existing count */
        if (newLimits.hasIndexLimit()) {
            if (numIndexes > newLimits.getIndexLimit()) {
                throw new IllegalCommandException("Cannot set index limit" +
                                                  " below number of existing" +
                                                  " indexes: " +
                                                  numIndexes);
            }
        }

        /*
         * Can only increase (or not change) index key size limit if there
         * are existing indexes
         */
        if (newLimits.hasIndexKeySizeLimit() && (numIndexes > 0)) {
            if ((limits == null) ||
                !limits.hasIndexKeySizeLimit() ||
                (limits.getIndexKeySizeLimit() >
                                            newLimits.getIndexKeySizeLimit())) {
                throw new IllegalCommandException("Cannot decrease index key" +
                                                  " size limit");
            }
        }
    }

    /**
     * Evolve a table by adding a new version associated with a new set of
     * fields, a new TTL, or description.  Evolutionary changes are limited to
     * adding/removing non-key fields.  Evolution is always relative to the
     * latest version.
     *
     * If newDescription is null the description of the table will not
     * be changed.
     */
    void evolve(FieldMap newFields,
                TimeToLive newTTL,
                String newDescription,
                IdentityColumnInfo identityInfo,
                SequenceDef sequenceDef,
                Set<Integer> newRegions) {

        if ((identityInfo != null) && isMultiRegion()) {
            throw new IllegalCommandException
                ("Cannot add an identity column to a multi-region table");
        }

        /*
         * Only bump the version if the schema has changed (or is new). Changes
         * to TTL, description, and identity column do not change the schema and
         * do not count as a version change.
         */
        if (versions.isEmpty() ||
            !newFields.equals(versions.get(versions.size()-1))) {

            if (version == 255) {
                throw new IllegalCommandException
                    ("Can't evolve the table any further; too many versions");
            }

            validateEvolution(newFields);

            /*
             * it's not legal to evolve a version other than the latest one
             */
            if (version != 0 && (version != versions.size())) {
                throw new IllegalCommandException
                    ("Table evolution must be performed on the latest version");
            }

            versions.add(newFields);
            setVersion(getTableVersion() + 1);

            /* Recalculate primKeyPositions and isPrimKeyAtPos arrays. */
            initializeVersionInfo(true);

            initGeneratedUUIDPos();
        }

        ttl = newTTL;
        if (newDescription != null) {
            setDescription(newDescription);
        }
        identityColumnInfo = identityInfo;
        identitySequenceDef = sequenceDef;

        if (regionIds == null) {
            if (newRegions != null) {
                throw new IllegalCommandException("Cannot add regions to a" +
                                                  " non-multi-region table");
            }
        } else {
            /*
             * newRegions may be null due to upgrade, but we will fail it here
             * so that it does not mask a coding error that results in a null
             * region set for a MR table.
             */
            if (newRegions == null) {
                throw new IllegalStateException("Regions missing from" +
                                                " evolve request");
            }
            setRegions(newRegions);
        }
    }

    /**
     * Validates a specific field for schema evolution.  It needs to do a few
     * things:
     *  1) validate that the name doesn't exist in the current version of the
     *     table.  See (3) for future exceptions.
     *  2) validate that if the field is being resurrected from an earlier
     *     version of the table that the type and constraints match.
     *  3) future -- allow constraints or other things to change even if the
     *     field exists in the current version.
     */
    void validateFieldAddition(final String fieldPath,
                               final FieldMapEntry fme) {

        if (findTableField(fieldPath) != null) {
            throw new IllegalArgumentException
                ("Cannot add field, " + fieldPath + ", it already exists");
        }

        /*
         * Try to find the named field in older table versions and if found,
         * do more validation.  This loop checks the current version as well.
         * This is harmless and the code is simpler this way.
         */
        for (FieldMap map : versions) {
            FieldDef def = findTableField(new TablePath(map, fieldPath));
            if (def != null) {

                /*
                 * Insist that the FieldDef instances match.  In the
                 * future this may be more flexible and allow some differences
                 * that are compatible with schema evolution -- e.g. min, max,
                 * default.  Description changes will not be flagged as it's
                 * not used in the equals comparison.
                 */
                if (!def.equals(fme.getFieldDef())) {
                    throw new IllegalArgumentException
                        ("Cannot add field, " + fieldPath +
                         ". A version " +
                         "of the table contains this name and the types do " +
                         "not match, is: " + fme.getFieldDef().getType() +
                         ", was: " + def.getType());
                }
            }
        }
    }

    /**
     * Does the table have a value or is it key-only?  Key-only tables
     * can avoid some unnecessary work.
     */
    boolean hasValueFields() {
        return getValueRecordDef() != null;
    }

    /**
     * Validation of individual evolution steps is performed on the front end
     * when modifying fields. A few additional checks are done here.
     *
     * These operations are not allowed:
     * 1.  change fields in primary key
     * 2.  remove fields that participate in an index
     */
    private void validateEvolution(FieldMap newFields) {

        /*
         * Make sure primary key is intact.  Do this in a loop on primary
         * key fields vs above because it's more efficient.
         */
        for (String fieldName : primaryKey) {
            FieldDef oldDef = getField(fieldName);
            FieldDef newDef = newFields.getFieldDef(fieldName);
            if (!oldDef.equals(newDef)) {
                throw new IllegalCommandException
                    ("Evolution cannot modify the primary key");
            }
        }

        /*
         * Keys need not be validated because they cannot be modified
         * at this time, but if minor modifications to primary key fields
         * are allowed (description, default value), this should be called
         * for extra safety:
         * validate();
         */

        /*
         * Make sure indexed fields are intact.
         */
        for (Index index : indexes.values()) {

            for (IndexField ifield : ((IndexImpl)index).getIndexFields()) {

                TablePath fieldPath = (ifield.isJson()) ?
                        ifield.getJsonFieldPath() : ifield;
                /*
                 * Use findTableField in order to descend into nested fields.
                 */
                FieldDefImpl def = findTableField(newFields,
                                                  fieldPath.getPathName());
                if (def == null) {
                    throw new IllegalCommandException
                        ("Evolution cannot remove indexed fields");
                }
                FieldDefImpl origDef = findTableField(fieldPath);

                if (!def.equals(origDef)) {
                    throw new IllegalCommandException
                        ("Evolution cannot modify indexed fields");
                }
            }
        }
    }

    /**
     * Create a JSON representation of the table and format. Child tables
     * are not included.
     */
    public String toJsonString(boolean pretty) {
        return toJsonString(pretty, false, null);
    }

    /**
     * Create a JSON representation of the table and format. Child tables
     * are not included. If regionMapper is not null, region names will be
     * included in the output, otherwise only the region ID will be output.
     */
    public String toJsonString(boolean pretty, RegionMapper regionMapper) {
        return toJsonString(pretty, false, regionMapper);
    }

    /**
     * Create a JSON representation of the table and format.
     */
    public String toJsonString(boolean pretty,
                               boolean includeChildren,
                               RegionMapper regionMapper) {
        JsonFormatter formatter = createJsonFormatter(pretty);
        walkTableInfo(formatter, includeChildren, regionMapper);
        return formatter.toString();
    }

    /**
     * Formats the table.  If fields is null format the entire
     * table, otherwise, just use the specified fields.  The field names
     * may be nested (i.e. multi-component dot notation).
     *
     * @param asJson true if output should be JSON, otherwise tabular.
     * @param fieldPaths list of paths to describe, where each path is a
     *        list of its steps.
     */
    public String formatTable(boolean asJson,
                              List<List<String>> fieldPaths,
                              RegionMapper regionMapper) {

        Map<String, Object> fields = null;

        if (fieldPaths != null) {
            fields = new LinkedHashMap<>();

            for (List<String> fieldPath : fieldPaths) {
                TablePath tablePath = new TablePath(getFieldMap(), fieldPath);
                /*
                 * If the path finishes with [], it references an anonymous
                 * field (map and array elements). If so, try getting the
                 * field definition directly.
                 */
                if (tablePath.getLastStep() == TableImpl.BRACKETS ||
                    tablePath.getLastStep().equalsIgnoreCase(
                        TableImpl.VALUES)) {

                    FieldDefImpl def = findTableField(tablePath);
                    if (def != null) {
                        fields.put(tablePath.getPathName(), def);
                        continue;
                    }

                    throw new IllegalArgumentException(
                        "No such field in table " + getFullName() + ": " +
                        tablePath.getPathName());
                }

                /*
                 * The path references a record field.
                 */
                fields.put(tablePath.getPathName(),
                           getFieldMap().getFieldMapEntry(tablePath));
            }
        }

        if (asJson) {
            if (fields == null) {
                return toJsonString(true, regionMapper);
            }

            JsonFormatter handler = createJsonFormatter(true);
            handler.startObject();
            handler.appendString(FIELDS);
            handler.sep();
            handler.startArray();

            for (Entry<String, Object> e : fields.entrySet()) {
                handler.startField(true);
                Object obj = e.getValue();
                if (obj instanceof FieldDefImpl) {
                    handler.fieldInfo(e.getKey(),
                                      (FieldDefImpl) obj,
                                      null, null);
                } else {
                    assert(obj instanceof FieldMapEntry);
                    FieldMapEntry fme = (FieldMapEntry) obj;
                    handler.fieldInfo(fme.getFieldName(),
                                      fme.getFieldDef(),
                                      fme.isNullable(),
                                      fme.hasDefaultValue() ?
                                      fme.getDefaultValue().toString() : null);
                }
                handler.endField();
            }

            handler.endArray();
            handler.endObject();
            return handler.toString();
        }

        return TabularFormatter.formatTable(this, fields);
    }

    /**
     * Add Index objects during construction.  Check for the same indexed
     * fields in a different index name.  Do not allow this.
     */
    public void addIndex(Index index) {
        checkForDuplicateIndex(index);
        checkIndexLimit(index.getName());
        indexes.put(index.getName(), index);
    }

    /**
     * Remove an Index.
     */
    public Index removeIndex(String indexName) {
        return indexes.remove(indexName);
    }

    /**
     * Create and return a BinaryKeyIterator based on this table.  If this is
     * a top-level table the first component of the key must match the table
     * id.  If this is a child table it is assumed that the key is well-formed
     * and the parent's primary key is skipped and this child's id must match.
     *
     * If a match is not found null is returned.
     */
    BinaryKeyIterator createBinaryKeyIterator(byte[] key) {
        final BinaryKeyIterator keyIter =
            new BinaryKeyIterator(key);
        if (parent != null) {
            for (int i = 0; i < parent.getNumKeyComponents(); i++) {
                if (keyIter.atEndOfKey()) {
                    return null;
                }
                keyIter.skip();
            }
        }
        if (keyIter.atEndOfKey()) {
            return null;
        }
        final String tableId = keyIter.next();
        if (getIdString().equals(tableId)) {
            return keyIter;
        }
        return null;
    }

    /**
     * Returns a TableImpl for a given key as a byte array, or null if the key
     * is not a table key (and therefore it must be an old format KV API key).
     *
     * The algorithm is the same as if {@link #createBinaryKeyIterator} is
     * called followed by findTargetTable(BinaryKeyIterator), but it
     * uses {@link Key#findNextComponent} to avoid creating objects.
     *
     * Assumes key is for an existing table or is a non-table key, in
     * which case null is returned. Does not throw DroppedTableException.
     */
    public TableImpl findTargetTable(byte[] key) {

        int prevOff = 0;

        /* Skip all components in ancestor tables. */
        if (parent != null) {
            for (int i = 0; i < parent.getNumKeyComponents(); i++) {
                prevOff = Key.findNextComponent(key, prevOff);
                if (prevOff < 0) {
                    return null;
                }
                prevOff += 1;
            }
        }

        /* Get table ID component for this table. */
        final int nextOff = Key.findNextComponent(key, prevOff);
        if (nextOff < 0) {
            return null;
        }

        final int len = nextOff - prevOff;

        if (!equalsKeyBytes(key, prevOff, len)) {
            return null;
        }

        /* Search remaining key components and child tables. */
        return findTargetTable(key, nextOff + 1, 0 /*maxTableId*/);
    }

    /**
     * Given the position of the first key component for this table, validate
     * the number of key components and recurse to find child tables as needed.
     *
     * The algorithm is the same as findTargetTable(BinaryKeyIterator)
     * but it uses {@link Key#findNextComponent} to avoid creating objects.
     *
     * @param prevOff is the offset of the first key component for this table.
     *
     * @param maxTableId is non-zero to check for dropped tables.
     *
     * @throws DroppedTableException if maxTableId is non-zero, the key is not
     * for an existing table, and the key does not appear to be a non-table
     * (KV API) key. Never thrown when maxTableId is zero.
     *
     * @return the table for the given key, or null if the key appears to be a
     * non-table (KV API) key.
     */
    public TableImpl findTargetTable(final byte[] key,
                                     int prevOff,
                                     final long maxTableId) {

        /* Skip key components for this table only. */
        int numPrimaryKeyComponentsToSkip = primaryKey.size();
        if (parent != null) {
            numPrimaryKeyComponentsToSkip -= parent.primaryKey.size();
        }

        for (int i = 0; i < numPrimaryKeyComponentsToSkip; i++) {
            prevOff = Key.findNextComponent(key, prevOff);
            if (prevOff < 0) {
                return (i == numPrimaryKeyComponentsToSkip - 1) ? this : null;
            }
            prevOff += 1;
        }

        /* Get table ID component of child table. */
        final int nextOff = Key.findNextComponent(key, prevOff);

        /* If no more components, this table matches. */
        if (nextOff < 0) {
            return this;
        }

        final int len = nextOff - prevOff;

        /* Match ID with child table IDs. */
        for (final Table table : children.values()) {
            final TableImpl tableImpl = (TableImpl) table;

            if (!tableImpl.equalsKeyBytes(key, prevOff, len)) {
                continue;
            }

            /* Keep searching in child table. */
            return tableImpl.findTargetTable(key, nextOff + 1, maxTableId);
        }

        /* Check for a dropped child table. */
        if (maxTableId != 0) {
            checkForDroppedTable(key, prevOff, nextOff, maxTableId);
        }

        return null;
    }

    private boolean equalsKeyBytes(byte[] key, int off, int len) {

        final int idLen = idBytes.length;
        if (idLen != len) {
            return false;
        }

        for (int i = 0, j = off; i < idLen; i += 1, j += 1) {
            if (idBytes[i] != key[j]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Find the target table for this key in this table's hierarchy.
     * The caller has set the BinaryKeyIterator on this table's id
     * in the key and it matches.  At this point, consume key entries
     * until this table's primary key count is done.  The primary key
     * contribution from parent tables must be skipped.
     *
     * Assumes key is for an existing table or is a non-table key, in
     * which case null is returned. Does not throw DroppedTableException.
     */
    TableImpl findTargetTable(BinaryKeyIterator keyIter) {
        int numPrimaryKeyComponentsToSkip = primaryKey.size();
        if (parent != null) {
            numPrimaryKeyComponentsToSkip -= parent.primaryKey.size();
        }

        /* Match up the primary keys with the input keys, in number only */
        for (int i = 0; i < numPrimaryKeyComponentsToSkip; i++) {
            /* If the key is short, no match */
            if (keyIter.atEndOfKey()) {
                return null;
            }
            keyIter.skip();
        }

        /* If both are done we have a match */
        if (keyIter.atEndOfKey()) {
            return this;
        }

        /* There is another component, check for a child table */
        final String childId = keyIter.next();
        for (Table table : children.values()) {
            if (((TableImpl)table).getIdString().equals(childId)) {
                return ((TableImpl)table).findTargetTable(keyIter);
            }
        }
        return null;
    }

    /**
     * Checks the given key component to see if it is a valid and previously
     * assigned table ID. If so, throws {@link DroppedTableException}. If not,
     * the key must be a KV API key.
     *
     * Called when a key component does not currently exist as a table ID to
     * determine whether it is the ID of a dropped table or the key is not a
     * table key.
     */
    public static void checkForDroppedTable(final byte[] key,
                                            final int thisOff,
                                            final int nextOff,
                                            final long maxTableId) {

        /* A valid table ID is always followed by a primary key component. */
        if (Key.findNextComponent(key, nextOff + 1) < 0) {
            return;
        }

        final long checkId;
        try {
            final String idString =
                UtfOps.bytesToString(key, thisOff, nextOff - thisOff);

            checkId = TableImpl.createIdFromIdStr(idString);

        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            /* Invalid UTF bytes or invalid table ID. */
            return;
        }

        if (checkId < TableMetadata.INITIAL_KEY_ID ||
            checkId > maxTableId) {
            /* Not in the range of assigned table IDs. */
            return;
        }
        throw new DroppedTableException();
    }

    /*
     * Internal methods, some for the class, some for the package.
     */

    /**
     * Is the field part of the primary key? This is public for test access.
     */
    public boolean isKeyComponent(String fieldName) {
        for (String component : primaryKey) {
            if (fieldName.equalsIgnoreCase(component)) {
                return true;
            }
        }
        return false;
    }

    public int findKeyComponent(String fieldName) {
        for (int i = 0; i < primaryKey.size(); ++i) {
            String pkname = primaryKey.get(i);
            if (fieldName.equalsIgnoreCase(pkname)) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Is the field in an index on this table?
     */
    boolean isIndexKeyComponent(TablePath tablePath) {
        for (Index index : indexes.values()) {
            if (((IndexImpl)index).isIndexPath(tablePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * List of versions is 0 indexed, actual versions start at 1, so
     * subtract when indexing.  0 means get the default (latest) version.
     */
    private FieldMap getFieldMap(final int version1) {
        if (versions.size() < version1 || version1 < 0) {
            throw new IllegalCommandException
                ("Table version " + version1 + " does not exist for table " +
                 name);
        }
        int versionToGet = (version1 == 0) ? versions.size() : version1;
        return versions.get(versionToGet - 1);
    }

    /**
     * List of versions is 0 indexed, actual versions start at 1, so
     * subtract when indexing.  0 means get the default (latest) version.
     */
    private TableVersionInfo getVersionInfo(final int version1) {
        if (versions.size() < version1 || version1 < 0) {
            throw new IllegalCommandException
                ("Table version " + version1 + " does not exist for table " +
                 name);
        }
        int versionToGet = (version1 == 0) ? versions.size() : version1;
        return tableVersionInfo.get(versionToGet - 1);
    }

    private TableVersionInfo getVersionInfo() {
        return getVersionInfo(version);
    }

    /* public for access by query compiler */
    public int[] getPrimKeyPositions() {
        return getVersionInfo().getPrimKeyPositions();
    }

    public int getPrimKeyPos(int i) {
        return getVersionInfo().getPrimKeyPositions()[i];
    }

    /* public for access by query compiler */
    public RecordDefImpl getRowDef() {
        return getVersionInfo().getRecordDef();
    }

    public boolean isPrimKeyAtPos(int i) {
        return getVersionInfo().isPrimKeyAtPos(i);
    }

    private void throwMissingState(String state) {
        throw new IllegalCommandException
            ("Table is missing state required for construction: " + state);
    }

    /**
     * Validate the parameters, primary key, and shard key.
     * IllegalCommandException is thrown vs IllegalArgumentException because
     * this could be run on the server side and IAE will cause the server to
     * crash.
     */
    private void validate() {

        if (primaryKey.isEmpty()) {
            throwMissingState("primary key");
        }

        if (name == null) {
            throwMissingState("table name");
        }

        FieldMap fields = getFieldMap(0);
        if (fields == null || fields.isEmpty()) {
            throwMissingState("no fields defined");
        } else {
            boolean generated = false;

            for (int pos = 0; pos < fields.size(); ++pos) {
                FieldMapEntry fme = fields.getFieldMapEntry(pos);

                if (fme.getFieldDef().isUUIDString() &&
                    ((StringDefImpl)fme.getFieldDef()).isGenerated()) {
                    if (generated || identityColumnInfo != null) {
                        throw new IllegalCommandException(
                            "Only one IDENTITY field or one STRING " +
                            "AS UUID GENERATED BY DEFAULT field is allowed " +
                            "in a table.");
                    }
                    generated = true;
                }
            }
        }
        /**
         * Primary key for child tables has to have at least one
         * component in addition to parent's key.
         */
        if (parent != null) {
            if (!(primaryKey.size() > parent.primaryKey.size())) {
                throw new IllegalCommandException
                    ("Child table needs a primary key component");
            }
        }

        /**
         * Make sure that the shardKey is a strict subset of primary key
         */
        if (shardKey.size() > primaryKey.size()) {
            throw new IllegalCommandException
                ("Shard key must be a subset of the primary key");
        }
        for (int i = 0; i < shardKey.size(); i++) {
            String pkField = primaryKey.get(i);
            if (pkField == null || !pkField.equals(shardKey.get(i))) {
                throw new IllegalCommandException
                    ("Shard key must be a subset of the primary key");
            }
        }

        /*
         * Validate the primary key fields.  The properties of nullable and
         * default values are not relevant to primary keys, so they are
         * ignored.
         */
        for (int i = 0; i < primaryKey.size(); i++) {
            String pkField = primaryKey.get(i);

            FieldMapEntry fme = getFieldMapEntry(pkField, false);
            if (fme == null) {
                throw new IllegalCommandException
                    ("Primary key field is not a valid field: " +
                     pkField);
            }

            fme.setAsPrimaryKey();

            FieldDef field = fme.getFieldDef();
            if (!field.isValidKeyField()) {
                throw new IllegalCommandException
                    ("Field type cannot be part of a primary key: " +
                     field.getType() + ", field name: " + pkField);
            }
            if (primaryKeySizes != null) {
                validateKeyFieldSize(field, primaryKeySizes.get(i));
            }
        }
    }

    private void validateKeyFieldSize(FieldDef field, int size) {
        if (size != 0 && !(field.isInteger())) {
                throw new IllegalCommandException
                    ("Only Integer sizes can be constrained. Invalid type: " +
                     field.getType());
        }

        /* 0 means no restriction */
        if (size != 0) {
            if (size < 1 || size > 5) {
                throw new IllegalCommandException
                    ("Size constraint value on primary key must be between " +
                     "1 and 5. Invalid value: " + size);
            }
        }
    }

    /**
     * Deserialize a record. This API is used by the export
     * utility to deserialize a record using the version of the table that was
     * exported and NOT the latest evolved version of the table.
     *
     * @param writerDef the RecordDef used to write the record value
     * @param readerDef the RecordDef used to read the record value
     * @param row
     * @param data record in bytes
     * @param offset
     * @param tableVersion version of the table used for export
     */
    public void createExportRowFromValueSchema(RecordDefImpl writerDef,
                                               RecordDefImpl readerDef,
                                               RowImpl row,
                                               byte[] data,
                                               int offset,
                                               int tableVersion,
                                               Format valFormat) {

        ValueReader<RowImpl> rowReader = initRowReader(row);
        if (data.length >= (offset + 1)) {
            /*
             * Move the offset past table version byte.
             */
            offset++;

            if (readerDef == null) {
                readerDef = getValueRecordDef();
            }

            Decoder decoder = TableJsonUtils.getDecoderFactory().binaryDecoder
                (data, offset, (data.length - offset), null);
            SimpleAvroReader reader = new SimpleAvroReader(writerDef, readerDef,
                    rowReader, valFormat);
            try {
                reader.read(decoder);
            } catch (Exception e) {
                /*
                 * Return row without the value portion
                 * Fall through
                 */
            }
            return;
        }
        fillInDefaultValues(rowReader);
    }

    /**
     * Use table schema (primary key) to create a Row record with values from
     * the key parameter (derived from Key). This is used by the import utility
     * to create a Row record using the key field from an external record. The
     * external record might have been created from a different kvstore and
     * hence may have a different table idString than the table in this store.
     * Table idString mismatch will be ignored since the objective is to
     * populate the table with the key values from an external record. If there
     * is a key mismatch (by field type and number of fields), false is returned
     */
    public boolean createImportRowFromKeyBytes(Row keyRecord,
                                               BinaryKeyIterator keyIter,
                                               Iterator<String> pkIter) {
        if (parent != null) {
            if (!(parent).
                    createImportRowFromKeyBytes(keyRecord, keyIter, pkIter)) {
                return false;
            }
        }

        assert !keyIter.atEndOfKey();

        setTableVersion(keyRecord);
        keyIter.next();

        /*
         * Fill in values for primary key components that belong to this
         * table.
         */
        String lastKeyField = primaryKey.get(primaryKey.size() - 1);

        while (pkIter.hasNext()) {

            /*
             * If the table in the kvstore has more key components than the
             * key components in the record being imported return false. The
             * import utility will reject this record
             */
            if (keyIter.atEndOfKey()) {
                return false;
            }

            String field = pkIter.next();
            String val = keyIter.next();
            FieldDefImpl type = (FieldDefImpl)getField(field);

            try {
                keyRecord.put(field,
                    FieldDefImpl.createValueFromKeyString(val, type));
            } catch (Exception e) {
                return false;
            }

            if (field.equals(lastKeyField)) {
                break;
            }
        }

        return true;
    }

    /*
     * Constructs the fully-qualified name for this table, including parent
     * tables.  It is a dot-separated format:
     *      parentName.childName.grandChildName
     *
     * Top-level tables have a single component.
     */
    private void getTableNameInternal(StringBuilder sb) {
        if (parent != null) {
            parent.getTableNameInternal(sb);
            sb.append(NameUtils.CHILD_SEPARATOR);
        }
        sb.append(name);
    }

    /**
     * Get a fieldMap containing all non-primary key fields.
     * @param versionToUse
     * @return return a FieldMap containing all non-primary key fields. return
     * null if it's key only.
     */
    private FieldMap generateValueFieldMap(final int versionToUse) {

        boolean hasSchema = false;
        FieldMap valueFmap = new FieldMap();
        TableVersionInfo versionInfo = getVersionInfo(versionToUse);
        FieldMap fmap = versionInfo.getFieldMap();

        for (int pos = 0; pos < fmap.size(); ++pos) {
            FieldMapEntry fme = fmap.getFieldMapEntry(pos);
            if (!versionInfo.isPrimKeyAtPos(pos)) {
                hasSchema = true;
                valueFmap.put(fme);
            }
        }

        if (!hasSchema) {
            return null;
        }
        return valueFmap;
    }

    /*
     * Returns true if either a read or write limit is set on this table.
     */
    public boolean hasThroughputLimits() {
        final TableImpl top = getTopLevelTable();
        return (top.limits == null) ? false : top.limits.hasThroughputLimits();
    }

    public boolean hasSizeLimit() {
        final TableImpl top = getTopLevelTable();
        return (top.limits == null) ? false : top.limits.hasSizeLimit();
    }

    /**
     * Throws IndexLimitException if the table hierarchy is at, or
     * above the index limit if one is specified.
     */
    private void checkIndexLimit(String indexName) {
        final TableLimits tl = getTableLimits();
        if ((tl == null) || !tl.hasIndexLimit()) {
            return;
        }
        final int indexLimit = tl.getIndexLimit();
        if (countIndexes(getTopLevelTable()) >= indexLimit) {
            throw new IndexLimitException(
                        name, indexLimit,
                        "Adding " + indexName + " to table: " +
                        name + " would exceed index limit of " + indexLimit);
        }
    }

    /* Recursively count the indexes in the tree */
    private int countIndexes(TableImpl t) {
        int count = indexes.size();
        for (Table c : t.children.values()) {
            count += countIndexes((TableImpl)c);
        }
        return count;
    }

    /**
     * Throws ChildTableLimitException if the table hierarchy is at, or
     * above the child table limit if one is specified.
     */
    void checkChildLimit(String childName) {
        final TableLimits tl = getTableLimits();
        if ((tl == null) || !tl.hasChildTableLimit()) {
            return;
        }
        final int childLimit = tl.getChildTableLimit();
        if (countChildren(getTopLevelTable()) >= childLimit) {
            throw new ChildTableLimitException(
                        name, childLimit,
                        "Adding a child table " + childName + " to table: " +
                        name + " would exceed the limit of " + childLimit);
        }
    }

    private int countChildren(TableImpl t) {
        int count = t.children.size();
        for (Table c : t.children.values()) {
            count += countChildren((TableImpl)c);
        }
        return count;
    }

    @Override
    public String toString() {
        return "Table[" + name + ", " +
            id + ", " + getSequenceNumber() + ", " +
            (parent == null ? "-" : parent.getFullName()) + ", " +
            indexes.size() + ", " +
            children.size() + ", " + status + ", " + getTableVersion() + "]";
    }

    /**
     * This returns the same string as {@link #getNamespace()} with the
     * exception of the INITIAL namespace which is returned as null.
     * See: {@link NameUtils}.
     */
    public String getInternalNamespace() {
        return namespace;
    }

    /**
     * This returns the same string as {@link #getInternalNamespace()} with
     * the exception of INITIAL namespace which is returned as the string
     * used in the query: {@link TableAPI#SYSDEFAULT_NAMESPACE_NAME}.
     */
    @Override
    public String getNamespace() {
        if (namespace == null) {
            return TableAPI.SYSDEFAULT_NAMESPACE_NAME;
        }
        return namespace;
    }

    /* used by table creation */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String getFullNamespaceName() {
        /* The namespace may be set multiple times, in the read/write window
         * below the first time the volatile is assigned, but it's not worth
         * worrying about.
         */
        return (fullNameSpaceName == null) ?
             (fullNameSpaceName =
              NameUtils.makeQualifiedName(namespace, getFullName())) :
             fullNameSpaceName;
     }

    /**
     * Finds the named table in this table's hierarchy.
     *
     * @param nsFullName a fully-qualified table name which must exist in
     * this table's hierarchy and could optionally be qualified by a namespace.
     * This means it has at least 2 components.
     *
     * This table (the starting table) must be a top-level table.
     *
     * @throws IllegalArgumentException if any component cannot be found.
     */
    private TableImpl findTable(String nsFullName) {
        final String ns = NameUtils.getNamespaceFromQualifiedName(nsFullName);
        /**
         * If the namespace is specified explicitly, check whether it matches
         * the target table namespace.
         */
        if (ns != null && !ns.isEmpty() &&
            !NameUtils.namespaceEquals(namespace,
                NameUtils.switchToInternalUse(ns))) {
            throw new IllegalArgumentException("Namespace does not match");
        }

        final String fullName =
            NameUtils.getFullNameFromQualifiedName(nsFullName);
        final String[] path = parseFullName(fullName);
        if (!path[0].equalsIgnoreCase(name)) {
            throw new IllegalArgumentException
                ("No such table: " + nsFullName);
        }
        Table target = this;
        for (int i = 1; i < path.length; i++) {
            target = target.getChildTable(path[i]);
            if (target == null) {
                throw new IllegalArgumentException
                    ("No such table: " + nsFullName);
            }
        }
        return (TableImpl) target;
    }

    /**
     * Returns true if the target table is an ancestor of the start table.
     * Uses equality of ids, which is cheaper than full table equality.
     *
     * Id equality may not work for transiently constructed tables, but
     * that is not the target for this code.
     */
    public static boolean isAncestorOf(TableImpl start, TableImpl target) {
        TableImpl currentParent = start.parent;
        while (currentParent != null) {
            if (currentParent.id == target.id) {
                return true;
            }
            currentParent = currentParent.parent;
        }
        return false;
    }

    /**
     * Validates a single component of an identifier for valid characters as
     * well as length. The name may come from a table name, in which case isId
     * will be true, or an index or field name, in which case isId is false.
     */
    public static void validateIdentifier(String name,
                                          int maxLen,
                                          String type) {

        if (!name.matches(VALID_NAME_CHAR_REGEX)) {
            throw new IllegalArgumentException
                (type + " may contain" +
                 " only alphanumeric values plus the character \"_\": " + name);
        }

        if (!Character.isLetter(name.charAt(0)) ||
            (name.charAt(0) == '_')) {
            throw new IllegalArgumentException
                (type + " must start with an alphabetic character");
        }

        if (name.length() > maxLen) {
            throw new IllegalArgumentException
                ("Illegal name: " + name +
                 ". " + type + " must be less than or equal to " +
                 maxLen + " characters.");
        }
    }

    /**
     * Validate a table name for both legal characters and length
     */
    public static void validateTableName(String tableName, boolean systemTable) {

        if (systemTable) {

            /*
             * A system table name must start with "SYS$" prefix. The dollar
             * sign of system table prefix name will be replaced with "_" when
             * generate Avro schema. It is neccesary for Avro that the rest of
             * the table name except prefix must be constrainted to
             * alphanumeric characters plus "_".
             */
            final String[] nameComps = tableName.split("\\$");
            if (nameComps.length != 2 ||
                !nameComps[0].equalsIgnoreCase(SYSTEM_TABLE_PREFIX_STRING)) {
                throw new IllegalCommandException(
                    "System table names must be of the format " +
                    SYSTEM_TABLE_PREFIX + "<name>");
            }
            tableName = nameComps[1];
        }
        validateIdentifier(tableName, MAX_ID_LENGTH, "Table names");
    }

    public static void validateNamespace(String namespace) {
        if (namespace == null) {
            return;
        }

        if (!namespace.matches(VALID_NAMESPACE_CHAR_REGEX)) {
            throw new IllegalArgumentException
                ("Namespaces may contain only " +
                 "alphanumeric values plus the characters \"_\" " +
                 "and \".\" : " + namespace);
        }

        if (!Character.isLetter(namespace.charAt(0))) {
            throw new IllegalArgumentException
                ("Namespaces must start with an alphabetic character");
        }

        if (namespace.length() > MAX_NAMESPACE_LENGTH) {
            throw new IllegalArgumentException
                ("Illegal namespace: " + namespace +
                 ". Namespaces must be less than or equal to " +
                 MAX_NAMESPACE_LENGTH + " characters.");
        }
    }

    public static void validateRegionName(String regionName) {
        if ((regionName == null) || regionName.isEmpty()) {
            throw new IllegalArgumentException("Region name cannot be null" +
                                               " or empty");
        }
        TableImpl.validateIdentifier(regionName,
                                     MAX_REGION_NAME_LENGTH,
                                     "Region names");
    }

    /**
     * Returns parts of given table name.
     *
     * @param fullName fully qualified name of a table. Can be null.
     * @return zero-length array if given name is null.
     *
     * (refer to SR25037 for added support for null table name)
     */
    static String[] parseFullName(String fullName) {
        if (fullName == null) {
            throw new IllegalArgumentException("null table name");
        }
        return fullName.split(SEPARATOR_REGEX);
    }

    /*
     * MetadataInfo
     */
    @Override
    public MetadataType getType() {
        return MetadataType.TABLE;
    }

    /**
     * Returns the sequence number of the last change to this table.
     */
    @Override
    public int getSequenceNumber() {
        return getTopLevelTable().sequenceNumber;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    /**
     * Populates the "dest" record with the information from the "src" record.
     * The 2 records may not belong to the same record type. The use of
     * dest.getFieldNames() ensures that the destination record controls the
     * specific fields copied. This varies for Row and PrimaryKey. IndexKey
     * does not use this method because it may reference nested fields.
     */
    private static void populateRecord(RecordValueImpl dest,
                                       RecordValue src) {
        assert !(dest instanceof IndexKeyImpl);
        assert !(src instanceof IndexKeyImpl);

        RecordDef srcDef = src.getDefinition();

        for (String fname : dest.getFieldNamesInternal()) {
            if (srcDef.contains(fname)) {
                FieldValue v = src.get(fname);
                if (v != null) {
                    dest.put(fname, v);
                }
            }
        }
        dest.validate();  // Is this needed ????
    }

    /**
     * Checks if a given index is a duplicate of an existing index. Two indices
     * are considered duplicate if all following conditions are true
     *
     * 1. The two indices are of the same type, e.g., both are secondary
     * indices or both are text indices;
     * 2. The two indices share the same field or same set of fields, e.g.,
     * both indices are defined on the same columns.
     *
     * @param index  index to check
     */
    void checkForDuplicateIndex(Index index) {
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            final Index existingIndex = entry.getValue();
            if (index.getType().equals(existingIndex.getType()) &&
                ((IndexImpl)index).getIndexFields().equals(
                    ((IndexImpl)existingIndex).getIndexFields())) {
                throw new IllegalCommandException
                    ("Index is a duplicate of an existing index with " +
                     "another name.  Existing index name: " +
                     entry.getKey() + ", new index name: " +
                     index.getName());
            }
        }
    }

    private void setTableVersion(Row row) {
        ((RowImpl)row).setTableVersion(getTableVersion());
    }

    /**
     * See findTableField(TablePath) for semantics.
     * This is internal for now, but public to allow test case access.
     */
    public FieldDefImpl findTableField(String fieldPath) {
        return findTableField(getFieldMap(), fieldPath);
    }

    static FieldDefImpl findTableField(FieldMap fieldMap, String fieldPath) {
        return findTableField(new TablePath(fieldMap, fieldPath));
    }

    /**
     * Locates the named field within the table's hierarchy.  The field
     * may be a simple, top-level field, or it may be in dot notation,
     * specifying a field in a nested type (record, map, array of (map|array)).
     * The ultimate field must be an indexable type.  That is checked in the
     * caller.
     *
     * @return the FieldDef for the field or null if the field does not exist.
     */
    static FieldDefImpl findTableField(TablePath tablePath) {

        assert(!tablePath.isEmpty());

        FieldDefImpl def =
            tablePath.getFieldMap().getFieldDef(tablePath.getStep(0));

        if (def == null || !tablePath.isComplex()) {
            return def;
        }

        /*
         * Call the FieldDef itself to navigate the names.
         */
        return def.findField(tablePath, 1 /*pos*/);
    }

    @Override
    public ResourceOwner getOwner() {
        return owner;
    }

    @Override
    public TimeToLive getDefaultTTL() {
        return ttl;
    }

    /**
     * Whether this table is system table, internal use only.
     */
    public boolean isSystemTable() {
        return sysTable;
    }

    /**
     * Whether this table need to be exported, internal use only.
     */
    public boolean dontExport() {
        /*
         * May change in future, currently only system tables don't need to
         * be exported.
         */
        return isSystemTable();
    }

    /**
     * An internal class to do the work of deserializing Avro-encoded table
     * records without creating a tree of objects. The values are decoded
     * directly into the target Row.
     *
     * If the reader and writer schemas are the same this is fast. If they are
     * not the same it's necessary to create a ResolvingDecoder to handle the
     * schema evolution. This is slightly slower, but still a lot faster than
     * creating a tree using a ResolvingDecoder unconditionally, which is what
     * the previous code did.
     *
     * TODO: add the ability to selectively decode, creating sparse Rows
     */
    private static class SimpleAvroReader {
        final private ValueReader<?> rowReader;
        /*
         * The reader (expected) schema. The writer schema does not need to
         * be part of the state.
         */
        final private ResolvingDecoder resolver; // null if no schema evolution
        final RecordDefImpl recordDef;
        final private Format valueFormat;
        final private TableVersionInfo info;

        /**
         * @param writer the writer schema used to write the record
         * @param reader the reader schema, which is the one expected by the
         * caller and represents the current state of the table schema
         */
        private SimpleAvroReader(RecordDefImpl writer,
                                 RecordDefImpl reader,
                                 ValueReader<?> rowReader,
                                 Format valueFormat) {
            this.rowReader = rowReader;
            this.recordDef = ((TableImpl)rowReader.getTable()).getRowDef();
            this.info = ((TableImpl)rowReader.getTable()).getVersionInfo();
            resolver = (writer.equals(reader) ? null :
                        getResolvingDecoder(writer, reader));
            this.valueFormat = valueFormat;
        }

        /**
         * Construct a resolving decoder to handle schema evolution
         */
        private static ResolvingDecoder getResolvingDecoder(
                RecordDefImpl actual, RecordDefImpl expected) {
            try {
                return DecoderFactory.get().resolvingDecoder(
                        actual, expected, null);
            } catch (IOException ioe) {
            }
            return null;
        }

        private void read(Decoder in)
                throws IOException {
            if (resolver != null) {
                // Schema evolution code path
                readWithResolver(in);
                return;
            }
            FieldMap fieldMap = recordDef.getFieldMap();

            for (int pos = 0; pos < fieldMap.size(); ++pos) {
                FieldMapEntry fme = fieldMap.getFieldMapEntry(pos);

                /*
                 * Primary key fields are not part of the serialized data
                 */
                if (!info.isPrimKeyAtPos(pos)) {
                    read(fme, in);
                }
            }
        }

        private void readWithResolver(Decoder in)
                throws IOException {

            resolver.configure(in);
            for (FieldMapEntry fme : resolver.readFieldOrder()) {
                read(fme, resolver);
            }
            resolver.drain();
        }

        // read field in Record, need to handle nullable
        private void read(FieldMapEntry fme, Decoder in) throws IOException {
            String fieldName = fme.getFieldName();
            FieldDefImpl def = fme.getFieldDef();
            if (fme.isNullable()) {
                int index = in.readIndex();
                if ((!fme.hasDefaultValue() && index == 0) ||
                    (fme.hasDefaultValue() && index == 1)) {
                    in.readNull();
                    rowReader.readNull(fieldName);
                    return;
                }
            }
            read(fieldName, def, in);
        }

        // read each data according to type
        private void read(String fieldName, FieldDefImpl def, Decoder in)
            throws IOException {
            byte[] bytes = null;
            switch (def.getType()) {

            case RECORD:
                readRecord(fieldName, def, in);
                break;
            case ENUM:
                rowReader.readEnum(fieldName, def, in.readEnum());
                break;
            case ARRAY:
                readArray(fieldName, def, in);
                break;
            case MAP:
                readMap(fieldName, def, in);
                break;
            case STRING:
                if (def.isUUIDString()) {
                    bytes = in.readBytes(null).array();
                    String UUIDString  = StringValueImpl.unpackUUID(bytes);
                    rowReader.readString(fieldName, UUIDString);
                    break;
                }
                rowReader.readString(fieldName, in.readString());
                break;
            case INTEGER:
                rowReader.readInteger(fieldName, in.readInt());
                break;
            case LONG:
                rowReader.readLong(fieldName, in.readLong());
                break;
            case FLOAT:
                rowReader.readFloat(fieldName, in.readFloat());
                break;
            case DOUBLE:
                rowReader.readDouble(fieldName, in.readDouble());
                break;
            case BOOLEAN:
                rowReader.readBoolean(fieldName, in.readBoolean());
                break;
            case JSON: // JSON is temporarily encased in a byte[]
                bytes = in.readBytes(null).array();
                deserializeJson(rowReader, fieldName, bytes,
                        getJsonSerialVersion(valueFormat));
                break;
            case BINARY:
                bytes = in.readBytes(null).array();
                rowReader.readBinary(fieldName, bytes);
                break;
            case TIMESTAMP:
                bytes = in.readBytes(null).array();
                rowReader.readTimestamp(fieldName, def, bytes);
                break;
            case NUMBER:
                bytes = in.readBytes(null).array();
                rowReader.readNumber(fieldName, bytes);
                break;
            case FIXED_BINARY:
                readFixed(fieldName, def, in);
                break;
            case ANY:
            case ANY_ATOMIC:
            case ANY_JSON_ATOMIC:
            case ANY_RECORD:
                throw new IllegalStateException(
                        "Wildcard types are not invalid: " + def.getType());
            default:
                throw new IllegalStateException(
                        "Unknown type: " + def.getType());
            }
        }

        private void readRecord(String fieldName,
                                FieldDefImpl def,
                                Decoder in)
            throws IOException {

            if (in instanceof ResolvingDecoder) {
                resolveRecord(fieldName, def, (ResolvingDecoder) in);
                return;
            }

            RecordDefImpl rdef = (RecordDefImpl) def;
            rowReader.startRecord(fieldName, rdef);
            FieldMap fieldMap = rdef.getFieldMap();

            for (int pos = 0; pos < fieldMap.size(); ++pos) {
                FieldMapEntry fme = fieldMap.getFieldMapEntry(pos);
                read(fme, in);
            }
            rowReader.endRecord();
        }

        private void readMap(String fieldName,
                             FieldDefImpl def,
                             Decoder in)
            throws IOException {

            MapDefImpl mdef = (MapDefImpl) def;
            rowReader.startMap(fieldName, def);
            for (long i = in.readMapStart(); i != 0; i = in.mapNext()) {
                for (long j = 0; j < i; j++) {
                    String key = in.readString();
                    read(key, mdef.getElement(), in);
                }
            }
            rowReader.endMap();
        }

        private void readArray(String fieldName,
                                   FieldDefImpl def,
                                   Decoder in)
             throws IOException {

            ArrayDefImpl adef = (ArrayDefImpl) def;
            rowReader.startArray(fieldName, def, null);
            for (long i = in.readArrayStart(); i != 0; i = in.arrayNext()) {
                for (long j = 0; j < i; j++) {
                    read(null, adef.getElement(), in);
                }
            }
            rowReader.endArray();
        }

        private void readFixed(String fieldName,
                               FieldDefImpl def,
                               Decoder in)
            throws IOException {

            FixedBinaryDefImpl fbdef = (FixedBinaryDefImpl) def;
            int size = fbdef.getSize();
            byte[] bytes = new byte[size];
            in.readFixed(bytes, 0, size);
            rowReader.readFixedBinary(fieldName, def, bytes);
        }

        /**
         * A variant of readRecord that is used for schema evolution
         * between the writer and reader schemas.
         */
        private void resolveRecord(String fieldName,
                                   FieldDefImpl def,
                                   ResolvingDecoder in)
            throws IOException {

            rowReader.startRecord(fieldName, def);
            for (FieldMapEntry fme : in.readFieldOrder()) {
                read(fme, in);
            }
            rowReader.endRecord();
        }
    }

    /**
     * Below are methods to serialize a Row into an Avro encoding but bypassing
     * the creation of a GenericRecord from the Row. This code uses
     * FieldDefImpl and FieldValueImpl instances as the schema rather than
     * avro schema. This is safe because the table definition is what generates
     * the avro schema in the first place. This code uses the Avro Encoder class,
     * which is responsible for the serialization format.
     *
     * @param encoder the Avro Encoder to use for serializing the value
     *
     * @param fieldValue the value to serialize
     *
     * @param fieldDef if not null, the definition of the type. This may be
     * different from fieldValue.getType() because it may be JSON, which
     * affects the serialization.
     */
    private void writeAvro(Encoder encoder,
                           FieldValueSerializer fieldValue,
                           FieldDef fieldDef,
                           Value.Format valFormat)
        throws IOException {

        if (fieldDef != null && fieldDef.isJson()) {
            serializeJson(encoder, fieldValue,
                          getJsonSerialVersion(valFormat));
            return;
        }
        switch (fieldValue.getType()) {
        case INTEGER:
            encoder.writeInt(fieldValue.getInt());
            break;
        case LONG:
            encoder.writeLong(fieldValue.getLong());
            break;
        case DOUBLE:
            encoder.writeDouble(fieldValue.getDouble());
            break;
        case FLOAT:
            encoder.writeFloat(fieldValue.getFloat());
            break;
        case NUMBER:
            encoder.writeBytes(fieldValue.getNumberBytes());
            break;
        case STRING:
            if (fieldDef != null && fieldDef.isUUIDString()){
                encoder.writeBytes(
                    StringValueImpl.packUUID(fieldValue.getString()));
                break;
            }
            encoder.writeString(fieldValue.getString());
            break;
        case BOOLEAN:
            encoder.writeBoolean(fieldValue.getBoolean());
            break;
        case BINARY:
            encoder.writeBytes(fieldValue.getBytes());
            break;
        case FIXED_BINARY:
            encoder.writeFixed(fieldValue.getFixedBytes());
            break;
        case ENUM:
            /*
             * this depends on Avro's indexes on enums being the same as ours
             */
            EnumDefImpl enumDef = (EnumDefImpl)fieldValue.getDefinition();
            encoder.writeEnum(enumDef.indexOf(fieldValue.getEnumString()));
            break;
        case TIMESTAMP:
            encoder.writeBytes(fieldValue.getTimestampBytes());
            break;
        case RECORD:
            writeAvroRecord(encoder, fieldValue.asRecordValueSerializer(),
                false, valFormat);
            break;
        case MAP:
            writeAvroMap(encoder, fieldValue.asMapValueSerializer(), valFormat);
            break;
        case ARRAY:
            writeAvroArray(encoder, fieldValue.asArrayValueSerializer(),
                valFormat);
            break;
        default:
            throw new IllegalStateException("Unexpected type: " + fieldValue);
        }
    }

    /**
     * Encode/write a record
     * @param encoder the Encoder instance responsible for serialization
     * @param record the RecordValueImpl to encode
     * @param isRow true if this is the first call of this method serializing
     * a row. This is needed to filter out primary key components
     */
    private void writeAvroRecord(Encoder encoder,
                                 RecordValueSerializer record,
                                 boolean isRow,
                                 Value.Format valFormat)
        throws IOException {
        writeAvroRecord(encoder, record, isRow, valFormat, null, null);
    }

    private void writeAvroRecord(Encoder encoder,
                                 RecordValueSerializer record,
                                 boolean isRow,
                                 Value.Format valFormat,
                                 KVStoreImpl store,
                                 GeneratedValueInfo genInfo)
        throws IOException {

        TableVersionInfo info = getVersionInfo();

        /*
         * The complication in this loop is that fields in records may
         * be nullable or not and they may have default values. Not-nullable
         * fields must have default values.
         *
         * Nullable fields are represented in Avro as a union, which is why
         * the writeIndex() calls are necessary to discriminate the type.
         *
         * Fields must be written in field order because Avro schemas are
         * ordered.
         */
        FieldMap fieldMap =
            ((RecordDefImpl)record.getDefinition()).getFieldMap();

        for (int pos = 0; pos < fieldMap.size(); ++pos) {

            FieldMapEntry fme = fieldMap.getFieldMapEntry(pos);

            if (!isRow || !info.isPrimKeyAtPos(pos)) {
                FieldValueSerializer fv = record.get(pos);

                /*
                 * A non-null store means that a value *may* need to be
                 * generated. It should be possible to combine the 2 generation
                 * cases -- identity column and UUID -- if desired.
                 */
                if (store != null) {
                    if (hasIdentityColumn()
                        && getIdentityColumn() == pos) {
                        fv = TableAPIImpl.fillIdentityValue(
                            record, pos, this, genInfo, store);
                    }
                    if (fv == null && hasUUIDcolumn() &&
                        isGeneratedByDefault(pos)) {
                        fv = getGeneratedUUID(genInfo, record, pos);
                    }
                }
                if (fv == null || fv.isNull()) {
                    if (fv == null) {
                        fv = fme.getDefaultValue();
                    }
                    if (fv.isNull()) {
                        if (!fme.isNullable()) {
                            String fieldName = fme.getFieldName();
                            throw new IllegalCommandException
                                ("The field can not be null: " + fieldName);
                        }
                        /*
                         * null is always the first choice in the union when
                         * there is no default values
                         */
                        encoder.writeIndex(fme.hasDefaultValue() ? 1 : 0);
                        encoder.writeNull();
                        continue;
                    }
                }

                if (fme.isNullable()) {
                    /*
                     * nullable fields with a default value generate schemas
                     * with the default type first in the union.
                     */
                    encoder.writeIndex(fme.hasDefaultValue() ? 0 : 1);
                }
                /*
                 * Add FieldDef so that writeAvro() can properly handle JSON.
                 * In the case of JSON the FieldValue may look like a simple
                 * atomic value (integer, string, etc).
                 */
                writeAvro(encoder, fv, fme.getFieldDef(), valFormat);
            }
        }
    }

    /**
     * Write a Map
     */
    private void writeAvroMap(Encoder encoder,
                              MapValueSerializer mapValue,
                              Value.Format valFormat)
        throws IOException {

        /*
         * If the map element is JSON pass that information to writeAvro() so
         * it can properly serialize the JSON. Otherwise the type is obtained
         * from the FieldValue.
         */
        MapDef mapDef = mapValue.getDefinition();
        FieldDef elementDef =
            mapDef.getElement().isJson() ? mapDef.getElement() : null;
        encoder.writeMapStart();
        encoder.setItemCount(mapValue.size());

        Iterator<Entry<String, FieldValueSerializer>> iter = mapValue.iterator();
        while(iter.hasNext()) {
            Entry<String, FieldValueSerializer> entry = iter.next();
            encoder.startItem();
            encoder.writeString(entry.getKey());
            writeAvro(encoder, entry.getValue(), elementDef, valFormat);
        }
        encoder.writeMapEnd();
    }

    /**
     * Write an Array
     */
    private void writeAvroArray(Encoder encoder,
                                ArrayValueSerializer arrayValue,
                                Value.Format valFormat)
        throws IOException {

        /*
         * If the array element is JSON pass that information to writeAvro() so
         * it can properly serialize the JSON. Otherwise the type is obtained
         * from the FieldValue.
         */
        ArrayDef arrayDef = arrayValue.getDefinition();
        FieldDef elementDef =
            arrayDef.getElement().isJson() ? arrayDef.getElement(): null;
        encoder.writeArrayStart();
        encoder.setItemCount(arrayValue.size());

        Iterator<FieldValueSerializer> iter = arrayValue.iterator();
        while(iter.hasNext()) {
            encoder.startItem();
            writeAvro(encoder, iter.next(), elementDef, valFormat);
        }
        encoder.writeArrayEnd();
    }

    /**
     * Serialize JSON as a single byte[]. This is not particularly efficient
     * because it not only constructs new objects it encapsulates JSON in
     * an Avro record as byte[]. This is, hopefully, a temporary measure until
     * all serialization can be done without Avro.
     */
    private static void serializeJson(Encoder encoder,
                                      FieldValueSerializer fieldValue,
                                      short jsonSerialVersion)
        throws IOException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutput out = new DataOutputStream(baos);
        FieldValueSerialization.writeFieldValueInternal(fieldValue, true, out,
                                                        jsonSerialVersion);
        encoder.writeBytes(baos.toByteArray());
    }

    /**
     * Deserialize JSON from a byte[]
     */
    private static void deserializeJson(ValueReader<?> reader,
                                        String fieldName,
                                        byte[] bytes,
                                        short jsonSerialVersion)
        throws IOException {

        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final DataInput din = new DataInputStream(bais);
        FieldValueSerialization.readFieldValue(reader, fieldName, null,
            din, jsonSerialVersion);
    }

    /**
     * Returns the serial version corresponding to the given value format,
     * it is used for serialization/deserialization of Json field value.
     */
    private static short getJsonSerialVersion(Format valueFormat) {
        return (valueFormat.compareTo(Format.TABLE_V1) >= 0) ?
                SerialVersion.CURRENT : 0;
    }

    /**
     * Create the RecordDef that defines the schema of PrimaryKey instances
     * for this table. It does not matter which version of the table is used;
     * the primary key cannot change.
     */
    private void createPrimKeyDef() {

        FieldMap pkFieldMap = null;
        FieldMap tableFieldMap = getFieldMap(version);

        pkFieldMap = new FieldMap();

        for (int i = 0; i < primaryKey.size(); ++i) {
            String pkFieldName = primaryKey.get(i);
            int pos = tableFieldMap.getFieldPos(pkFieldName);
            FieldMapEntry fme = tableFieldMap.getFieldMapEntry(pos).clone();

            /* mark the field as nullable without a default */
            fme.setNullable();
            pkFieldMap.put(fme);
        }

        primaryKeyDef = new RecordDefImpl(pkFieldMap, null);
    }

    public RecordDefImpl getPrimKeyDef() {
        return primaryKeyDef;
    }

    /**
     * Initializes per-version transient state. If validate is true, generate
     * schema and a primary key def as well. This is the common case. When
     * creating a TableImpl from a partially built table, from TableBuilder,
     * it will be false.
     */
    private void initializeVersionInfo(boolean validate) {
        tableVersionInfo = new ArrayList<>(versions.size());
        for (int i = 0; i < versions.size(); i++) {
            FieldMap fm = versions.get(i);
            tableVersionInfo.add(new TableVersionInfo(i + 1, fm));
        }
        if (validate) {
            /*
             * Generate the schema for the current version. This is used
             * for validation of the fields from an perspective during
             * table creation.
             */
            getValueRecordDef();
            createPrimKeyDef();
        }
        valueFormat = getValueFormat(SerialVersion.CURRENT);
    }

    /**
     * Re-serializes valBytes to the older value format corresponding to the
     * specified targetSerialVersion if needed.
     *
     * Return the re-serialized value bytes in older format if re-serialization
     * is done, otherwise return valBytes.
     */
    @SuppressWarnings("unused")
    public byte[] reserializeToOldValue(byte[] keyBytes,
                                        byte[] valBytes,
                                        short targetSerialVersion) {
        return valBytes;
    }

    /**
     * Returns the value format for the specified serial version.
     */
    Format getValueFormat(short serialVersion) {
        assert serialVersion >= SerialVersion.MINIMUM;
        if (valueFormat != null && serialVersion == SerialVersion.CURRENT) {
            return valueFormat;
        }
        if (isMultiRegion()) {
            if (serialVersion >=
                SerialVersion.MULTI_REGION_TABLE_VERSION) {
                return Format.MULTI_REGION_TABLE;
            }
            throw new IllegalArgumentException(
                "Expected serial version " +
                SerialVersion.MULTI_REGION_TABLE_VERSION +
                " or higher for multi-region table, found: " + serialVersion);
        }

        /*
         * If the table contains JSON field including a nested JSON field and
         * the give serialVersion >= v15, then use the new TABLE_V1 format,
         * otherwise use old TABLE format.
         *
         * If table contains JSON field or a complex field with nested JSON
         * field, then FieldMap.isPrecise() returns true, otherwise it returns
         * false.
         */
        boolean hasJsonField = !getFieldMap().isPrecise();
        if (hasJsonField) {
            return Format.TABLE_V1;
        }
        return Format.TABLE;
    }

    /**
     * An instance of this is created for each version of the table. It holds
     * information relevant to each version:
     * - primary key info
     * - Avro schema for the version. This is generated when needed, not on
     * construction, but cached afterwards.
     *
     * NOTE: this is not a static class so it has access to the containing
     * TableImpl but it must not directly access any fields that may be mutable
     * in the TableImpl.
     */
    private class TableVersionInfo {
        /* The position of the i-th prim key column in the table rows */
        final private int[] primKeyPositions;

        /* Whether the i-th table column is a prim key column */
        final private boolean[] isPrimKeyAtPos;

        final private int tableVersion;

        /* this is a duplicate of what's in the TableImpl */
        final private FieldMap fieldMap;

        /* this will be the same for all instances, but is handy here */
        final private RecordDefImpl recordDef;

        /*
         * a cached RecordDefImpl representing only the value (vs key) fields
         * of the current version of this table. It is used for schema evolution.
         */
        private RecordDefImpl valueRecordDef;

        /*
         * this allows the code to skip an attempt to generate a schema when
         * there is none for this version.
         */
        private boolean isKeyOnly;

        private TableVersionInfo(int tableVersion, FieldMap tableFieldMap) {
            this.tableVersion = tableVersion;
            this.fieldMap = tableFieldMap;
            primKeyPositions = new int[primaryKey.size()];
            isPrimKeyAtPos = new boolean[tableFieldMap.size()];
            if (!fieldMap.isEmpty()) {
                recordDef = new RecordDefImpl(getName(), fieldMap);
            } else {
                /* this only happens for a partially built table */
                recordDef = null;
            }
            initPositionInfo();
        }

        /*
         * Get a cached RecordDefImpl representing only the value (vs key)
         * fields of the current version of this table. It is used for schema
         * evolution.
         */
        private RecordDefImpl getValueRecordDef() {
            if (valueRecordDef == null && !isKeyOnly) {
                synchronized(this) {
                    if (valueRecordDef == null) { /* re-check under lock */
                        FieldMap fmap = generateValueFieldMap(tableVersion);
                        if (fmap == null) {
                            /* prevent future attempts to generate */
                            isKeyOnly = true;
                        } else {
                            valueRecordDef = new RecordDefImpl(fmap, null);
                        }
                    }
                }
            }
            return valueRecordDef;
        }

        private FieldMap getFieldMap() {
            return fieldMap;
        }

        private RecordDefImpl getRecordDef() {
            return recordDef;
        }

        private boolean isPrimKeyAtPos(int pos) {
            return isPrimKeyAtPos[pos];
        }

        private int[] getPrimKeyPositions() {
            return primKeyPositions;
        }

        private void initPositionInfo() {
            for (int i = 0; i < primaryKey.size(); i++) {
                String pkFieldName = primaryKey.get(i);
                int pos = fieldMap.getFieldPos(pkFieldName);
                assert (pos >= 0);
                primKeyPositions[i] = pos;
                isPrimKeyAtPos[pos] = true;
            }
        }
    }

    public FieldDefImpl getPrimKeyColumnDef(int i) {
        return primaryKeyDef.getFieldDef(i);
    }


    /**
     * Returns the minimum version of the server needed to support this
     * table. This version is based on when specific features and
     * datatypes used by this table were introduced.
     */
    public short getRequiredSerialVersion() {

        short requiredSerialVersion = SerialVersion.MINIMUM;

        for (int i = 0; i < versions.size(); i++) {
            FieldMap fieldMap = versions.get(i);
            requiredSerialVersion = (short)Math.max(requiredSerialVersion,
                fieldMap.getRequiredSerialVersion() );
        }

        if (hasIdentityColumn()) {
            requiredSerialVersion = (short)Math.max(requiredSerialVersion,
                                                    IDENTITY_VERSION);
        }

        if (isMultiRegion()) {
            requiredSerialVersion = (short)Math.max(requiredSerialVersion,
                                                    MULTI_REGION_TABLE_VERSION);
        }
        if (hasUUIDcolumn()) {
            requiredSerialVersion = (short)Math.max(requiredSerialVersion,
                                                    UUID_VERSION);
        }
        return requiredSerialVersion;
    }

    /**
     * Returns the total number of types defined in the table schema tree,
     * including definitions in nested types. The top-level RecordDef is not
     * counted.
     */
    public int countTypes() {
        int num = 0;
        for (String fname : getFieldMap().getFieldNames()) {
            FieldDefImpl def = (FieldDefImpl) getField(fname);
            num += def.countTypes();
        }
        return num;
    }

    /** For testing. */
    void setCheckDeserializeValueFormatHook(TestHook<Format> hook) {
        checkDeserializeValueFormatHook = hook;
    }

    /** For testing. */
    TestHook<Format> getCheckDeserializeValueFormatHook() {
        return checkDeserializeValueFormatHook;
    }

    /** For testing. */
    static void setTestSerializationVersion(final short testSerialVersion) {
        testCurrentSerialVersion = testSerialVersion;
    }

    /** For testing. */
    static short getTestSerializationVersion() {
        return testCurrentSerialVersion;
    }

    @SuppressWarnings("unchecked")
    ValueReader<RowImpl> initRowReader(RowImpl value) {
        RowImpl row = (value != null) ? value : createRow();
        ValueReader<?> reader = new RowReaderImpl(row);
        return (ValueReader<RowImpl>)reader;
    }

    void readKeyFields(ValueReader<?> reader, RowSerializer row) {
        if (row.isPrimaryKey()) {
            for (int i = 0; i < row.getDefinition().getNumFields(); i++) {
                String fname = getPrimaryKeyColumnName(i);
                FieldValueSerializer val = row.get(i);
                readFieldValue(reader, fname, val);
            }
        } else {
            for (int pos: getPrimKeyPositions()) {
                String fname = getFields().get(pos);
                FieldValueSerializer val = row.get(pos);
                readFieldValue(reader, fname, val);
            }
        }
    }

    private static void readFieldValue(ValueReader<?> reader,
                                       String fname,
                                       FieldValueSerializer value) {

        if (value.isNull()) {
            reader.readNull(fname);
            return;
        }

        switch (value.getType()) {
        case BINARY:
            reader.readBinary(fname, value.getBytes());
            break;
        case BOOLEAN:
            reader.readBoolean(fname, value.getBoolean());
            break;
        case DOUBLE:
            reader.readDouble(fname, value.getDouble());
            break;
        case ENUM:
            EnumDefImpl def = (EnumDefImpl)value.getDefinition();
            reader.readEnum(fname, def, def.indexOf(value.getEnumString()));
            break;
        case FIXED_BINARY:
            reader.readFixedBinary(fname, value.getDefinition(),
                value.getFixedBytes());
            break;
        case FLOAT:
            reader.readFloat(fname, value.getFloat());
            break;
        case INTEGER:
            reader.readInteger(fname, value.getInt());
            break;
        case LONG:
            reader.readLong(fname, value.getLong());
            break;
        case STRING:
            reader.readString(fname, value.getString());
            break;
        case TIMESTAMP:
            reader.readTimestamp(fname, value.getDefinition(),
                value.getTimestampBytes());
            break;
        case NUMBER:
            reader.readNumber(fname, value.getNumberBytes());
            break;
        case JSON:
            assert(value.isJsonNull());
            reader.readJsonNull(fname);
            break;
        default:
            throw new IllegalStateException("Unexpected type: " +
                value.getType());
        }
    }

    /**
     * Returns true if table has an identity column.
     */
    public boolean hasIdentityColumn() {
        return identityColumnInfo != null;
    }

    public IdentityColumnInfo getIdentityColumnInfo() {
        return identityColumnInfo;
    }

    /**
     * Returns the index of the identity column, -1 otherwise.
     */
    public int getIdentityColumn() {
        return identityColumnInfo == null ? -1 :
                                         identityColumnInfo.getIdentityColumn();
    }

    /**
     * Returns true if identity column has GENERATED ALWAYS option.
     */
    public boolean isIdentityGeneratedAlways() {
        return identityColumnInfo == null ? false :
                                 identityColumnInfo.isIdentityGeneratedAlways();
    }

    /**
     * Returns true if identity column has generated ON NULL option.
     */
    public boolean isIdentityOnNull() {
        return identityColumnInfo == null ? false :
                                          identityColumnInfo.isIdentityOnNull();
    }

    /**
     * Returns true if (1) the table has an identity column and (2) it is a
     * primary key field and (3) there is only one primary key field.
     * NOTE: if a more generic method to determine if the table has an identity
     * column as primary key in a multi-component key is needed, this can be
     * adapted. Right now it's single-purpose.
     */
    boolean isIdentityColumnPrimaryKey() {
        if (identityColumnInfo != null) {
            int position = identityColumnInfo.getIdentityColumn();
            int[] positions = getPrimKeyPositions();
            if (positions.length == 1 && position == positions[0]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the table has a generated UUID column as a primary key
     */
    boolean isGeneratedUUIDPrimaryKey() {
        if (generatedUUIDPosition >= 0) {
            int[] positions = getPrimKeyPositions();
            for (int i : positions) {
                if (i == generatedUUIDPosition) {
                    return true;
                }
            }
        }
        return false;
    }

    public SequenceDef getIdentitySequenceDef() {
        return identitySequenceDef;
    }

    public void walkTableInfo(TableEventHandler handler,
                              boolean includeChildTables,
                              RegionMapper regionMapper) {

        handler.start(namespace, name);

        if (getDefaultTTL() != null) {
            handler.ttl(getDefaultTTL());
        }

        if (owner != null) {
            handler.owner(owner.toString());
        }

        handler.systemTable(sysTable);

        if (description != null)  {
            handler.description(description);
        }

        if (parent != null) {
            handler.parent(parent.getName());
        }

        if (regionIds != null) {
            Map<Integer, String> regions =
                new LinkedHashMap<>(regionIds.size());
            for (Integer rid : regionIds) {
                String regionName = regionMapper != null ?
                    regionMapper.getRegionName(rid) : null;
                regions.put(rid, regionName);
            }
            String localName = regionMapper != null ?
                regionMapper.getRegionName(Region.LOCAL_REGION_ID) : null;
            handler.regions(regions, Region.LOCAL_REGION_ID, localName);
        }

        TableLimits tlimits = getTableLimits();
        if (tlimits != null) {
            handler.limits(tlimits);
        }

        /*
         * Fields
         */
        walkFields(getFieldMap(), handler);

        handler.primaryKey(primaryKey);

        if (primaryKeySizes != null) {
            handler.primaryKeySizes(primaryKeySizes);
        }

        handler.shardKey(shardKey);

        if (identityColumnInfo != null) {
            handler.identity(getFieldMap().getFieldName(
                                 identityColumnInfo.getIdentityColumn()),
                             identityColumnInfo.isIdentityGeneratedAlways(),
                             identityColumnInfo.isIdentityOnNull(),
                             getIdentitySequenceDef());
        }

        /*
         * indexes
         */
        if (indexes.size() != 0) {
            handler.startIndexes(indexes.size());
            int indexNum = 1;
            for (Map.Entry<String, Index> indexEntry : indexes.entrySet()) {
                IndexImpl impl = (IndexImpl) indexEntry.getValue();
                List<String> types = null;
                if (impl.getTypes() != null) {
                    types = new ArrayList<String>(impl.getTypes().size());
                    for (FieldDef.Type type : impl.getTypes()) {
                        if (type == null) {
                            types.add(null);
                        } else {
                            types.add(type.toString());
                        }
                    }
                }
                handler.index(this,
                              indexNum++,
                              impl.getName(),
                              impl.getDescription(),
                              impl.getType().toString().toLowerCase(),
                              impl.getFields(),
                              types,
                              impl.getAnnotationsInternal(),
                              impl.getProperties());
            }
            handler.endIndexes();
        }

        /*
         * child tables
         */
        if (children.size() != 0) {
            List<String> childTables = new ArrayList<>(children.size());
            for (Map.Entry<String, Table> childEntry :
                     children.entrySet()) {
                childTables.add(childEntry.getKey());
            }
            handler.children(childTables);
            if (includeChildTables) {
                handler.startChildTables(children.size());
                boolean first = true;
                for (Table child : children.values()) {
                    handler.startChildTable(first);
                    first = false;
                    ((TableImpl) child).walkTableInfo(handler,
                                                       includeChildTables,
                                                       regionMapper);
                    handler.endChildTable();
                }
                handler.endChildTables();
            }
        }

        handler.end();
    }

    static void walkFieldDefInfo(FieldDefImpl def, TableEventHandler handler) {
        handler.startField(true);
        handler.fieldInfo(def.getFieldName(),
                          def,
                          null,
                          null);
        if (def.isRecord()) {
            walkFields(((RecordDefImpl)def).getFieldMap(), handler);
        }
        if (def.isMap()) {
            walkCollectionElement(((MapDefImpl) def).getElement(), handler);
        }
        if (def.isArray()) {
            walkCollectionElement(((ArrayDefImpl) def).getElement(),
                                  handler);
        }
        handler.endField();
    }

    private static void walkFields(FieldMap tFields,
                                       TableEventHandler handler) {
        handler.startFields(tFields.size());
        boolean first = true;
        for (FieldMapEntry fme: tFields.getFieldProperties()) {
            FieldDefImpl def = fme.getFieldDef();

            handler.startField(first);
            handler.fieldInfo(fme.getFieldName(),
                              def,
                              fme.isNullable(),
                              fme.hasDefaultValue() ?
                              fme.getDefaultValue().toString() : null);
            first = false;

            if (def.isRecord()) {
                walkFields(((RecordDefImpl)def).getFieldMap(), handler);
            }
            if (def.isMap()) {
                walkCollectionElement(((MapDefImpl) def).getElement(), handler);
            }
            if (def.isArray()) {
                walkCollectionElement(((ArrayDefImpl) def).getElement(),
                                      handler);
            }
            handler.endField();
        }
        handler.endFields();
    }

    private static void walkCollectionElement(FieldDefImpl def,
                                              TableEventHandler handler) {
        handler.startCollection();
        handler.fieldInfo(null, // maybe def.getFieldName() ?
                          def,
                          null,
                          null);
        if (def.isRecord()) {
            walkFields(((RecordDefImpl)def).getFieldMap(), handler);
        }

        if (def.isMap()) {
            walkCollectionElement(((MapDefImpl) def).getElement(), handler);
        }
        if (def.isArray()) {
            walkCollectionElement(((ArrayDefImpl) def).getElement(),
                                  handler);
        }
        handler.endCollection();
    }

    static JsonFormatter createJsonFormatter(boolean pretty) {
        return pretty ? new JsonPrettyFormatter() :
                        new JsonFormatter();
    }

    /**
     * A JSON formatter class that implements TableEventHandler
     */
    public static class JsonFormatter implements TableEventHandler {
        protected final StringBuilder sb;
        static final char SEP = ':';
        static final char COMMA = ',';
        static final char QUOT = '"';
        static final char START_OBJ = '{';
        static final char END_OBJ = '}';
        static final char START_ARRAY = '[';
        static final char END_ARRAY = ']';

        public JsonFormatter() {
            sb = new StringBuilder();
        }

        /*
         * Methods likely to be overridden by pretty print
         */
        protected void indent() {}
        protected void unindent() {}
        protected void newline() {}
        protected void addIndent() {}
        protected void sep() {
            sb.append(SEP);
        }

        protected void startArray() {
            sb.append(START_ARRAY);
        }

        protected void endArray() {
            sb.append(END_ARRAY);
        }

        /* comma after field */
        protected void comma() {
            comma(true);
        }

        @SuppressWarnings("unused")
        protected void comma(boolean withCR) {
            sb.append(COMMA);
        }

        /* comma between array entries */
        protected void arrayComma() {
            sb.append(COMMA);
        }

        protected void appendString(String value) {
            if (value != null) {
                sb.append(QUOT).append(value).append(QUOT);
            } else {
                sb.append("null");
            }
        }

        protected void stringField(String key, String value) {
            appendString(key);
            sep();
            appendString(value);
        }

        protected void nonStringField(String key, String value) {
            appendString(key);
            sep();
            sb.append(value);
        }

        protected void arrayOfString(String key, List<String> list) {
            boolean first = true;
            comma();
            sb.append(QUOT).append(key).append(QUOT);
            sep();
            sb.append("[");
            for (String fieldName : list) {
                if (!first) {
                    arrayComma();
                } else {
                    first = false;
                }
                appendString(fieldName);
            }
            sb.append("]");
        }

        protected void startObject() {
            sb.append(START_OBJ);
            indent();
            newline();
        }

        protected void endObject() {
            unindent();
            newline();
            sb.append(END_OBJ);
        }

        @Override
        public void start(String namespace, String tableName) {
            startObject();
            nonStringField(JSON_VERSION, "1");
            comma();
            stringField(TYPE, "table");
            if (namespace != null) {
                comma();
                stringField("namespace", namespace);
            }
            comma();
            stringField("name", tableName);
        }

        @Override
        public void startChildTables(int numChildTables) {
            comma();
            appendString(CHILDTABLES);
            sep();
            sb.append("[");
        }

        @Override
        public void startChildTable(boolean isFirst) {
            if (isFirst) {
                return;
            }
            comma(false);
        }

        @Override
        public void endChildTable() {
            // nop
        }

        @Override
        public void endChildTables() {
            sb.append("]");
        }

        @Override
        public void owner(String owner) {
            if (owner != null) {
                comma();
                stringField(OWNER, owner);
            }
        }

        @Override
        public void ttl(TimeToLive ttl) {
            if (ttl != null) {
                comma();
                stringField("ttl", ttl.toString());
            }
        }

        @Override
        public void systemTable(boolean value) {
            if (value) {
                comma();
                appendString(SYSTABLE);
                sep();
                sb.append(Boolean.toString(value));
            }
        }

        @Override
        public void description(String description) {
            comma();
            stringField(DESC, description);
        }

        @Override
        public void parent(String parentName) {
            comma();
            stringField(PARENT, parentName);
        }

        @Override
        public void primaryKey(List<String> primaryKey) {
            arrayOfString(PRIMARYKEY, primaryKey);
        }

        @Override
        public void primaryKeySizes(List<Integer> primaryKeySizes) {
            boolean first = true;
            comma();
            sb.append(QUOT).append(PKEY_SIZES).append(QUOT);
            sep();
            sb.append("[");
            for (Integer i : primaryKeySizes) {
                if (!first) {
                    arrayComma();
                } else {
                    first = false;
                }
                sb.append(i.toString());
            }
            sb.append("]");
        }

        @Override
        public void shardKey(List<String> shardKey) {
            arrayOfString(SHARDKEY, shardKey);
        }

        @Override
        public void regions(Map<Integer, String> regions,
                            int localRegionId,
                            String localRegionName) {
            if (regions == null) {
                return;
            }
            comma();
            sb.append(QUOT).append(REGIONS).append(QUOT);
            sep();
            startObject();
            boolean first = true;
            for (Map.Entry<Integer, String> entry : regions.entrySet()) {
                if (!first) {
                    comma();
                } else {
                    first = false;
                }
                stringField(entry.getKey().toString(), entry.getValue());
            }
            if (!first) {
                comma();
            }
            stringField(getString(localRegionId), localRegionName);
            endObject();
        }

        @Override
        public void limits(TableLimits limits) {
            if (limits == null) {
                return;
            }
            comma();
            sb.append(QUOT).append(LIMITS).append(QUOT);
            sep();
            startObject();
            if (limits.hasThroughputLimits()) {
                nonStringField(READ_LIMIT,
                               getString(limits.getReadLimit()));
                comma();
                nonStringField(WRITE_LIMIT,
                               getString(limits.getWriteLimit()));
            }
            if (limits.hasSizeLimit()) {
                comma();
                nonStringField(SIZE_LIMIT,
                               getString(limits.getSizeLimit()));
            }
            if (limits.hasIndexLimit()) {
                comma();
                nonStringField(INDEX_LIMIT,
                               getString(limits.getIndexLimit()));
            }
            if (limits.hasIndexKeySizeLimit()) {
                comma();
                nonStringField(INDEX_KEY_SIZE_LIMIT,
                               getString(limits.getIndexKeySizeLimit()));
            }
            if (limits.hasChildTableLimit()) {
                comma();
                nonStringField(CHILD_TABLE_LIMIT,
                               getString(limits.getChildTableLimit()));
            }
            endObject();
        }

        private void booleanField(String name, boolean value) {
            comma();
            appendString(name);
            sep();
            sb.append(Boolean.toString(value));
        }

        @Override
        public void identity(String columnName,
                             boolean generatedAlways,
                             boolean onNull,
                             SequenceDef sequenceDef) {
            comma();
            appendString(IDENTITY);
            sep();
            startObject();
            stringField(NAME, columnName);
            booleanField(ALWAYS, generatedAlways);
            booleanField(NULL, onNull);
            if (sequenceDef != null) {
                comma();
                appendString(SEQUENCE);
                startObject();

                /*
                 * These values may be (are?) JSON objects
                 */
                appendString(START);
                sep();
                sb.append(sequenceDef.getStartValue().toJsonString(false));
                comma();
                appendString(INCREMENT);
                sep();
                sb.append(sequenceDef.getIncrementValue().toJsonString(false));
                comma();
                appendString(MIN);
                sep();
                sb.append(sequenceDef.getMinValue().toJsonString(false));
                comma();
                appendString(MAX);
                sep();
                sb.append(sequenceDef.getMaxValue().toJsonString(false));
                comma();
                appendString(CACHE);
                sep();
                sb.append(sequenceDef.getCacheValue().toJsonString(false));
                comma();
                booleanField(CYCLE, sequenceDef.getCycle());
                endObject();
            }
            endObject();
        }

        @Override
        public void children(List<String> childTables) {
            arrayOfString(CHILDREN, childTables);
        }

        @Override
        public void startIndexes(int numIndexes) {
            if (numIndexes != 0) {
                comma();
                appendString(INDEXES);
                sep();
                sb.append("[");
            }
        }

        @Override
        public void index(Table table,
                          int indexNumber,
                          String indexName,
                          String description,
                          String type,
                          List<String> fields,
                          List<String> types,
                          Map<String, String> annotations,
                          Map<String, String> properties) {

            if (indexNumber != 1) {
                comma(false);
            }
            startObject();
            stringField("name", indexName);
            comma();
            stringField(TYPE, type);
            if (description != null) {
                comma();
                stringField(DESC, description);
            }
            arrayOfString(FIELDS, fields);

            if (types != null) {
                arrayOfString(TYPES, types);
            }

            if (annotations != null && !annotations.isEmpty()) {
                mapOfString(ANNOTATIONS, annotations, true);
            }

            if (properties != null && !properties.isEmpty()) {
                mapOfString(PROPERTIES, properties, true);
            }

            endObject();
        }

        @Override
        public void endIndexes() {
            sb.append("]");
        }

        @Override
        public void startFields(int numFields) {
            comma();
            appendString(FIELDS);
            sep();
            sb.append("[");
        }

        @Override
        public void startField(boolean first) {
            if (!first) {
                comma(false);
            }
            startObject();
        }

        @Override
        public void fieldInfo(String name,
                              FieldDef fieldDef,
                              Boolean nullable,
                              String defaultValue) {
            if (name != null) {
                stringField(NAME, name);
                comma();
            }
            /* type is unconditional */
            stringField(TYPE, fieldDef.getType().toString());
            if (nullable != null) {
                comma();
                nonStringField(NULLABLE, nullable.toString());
            }
            if (defaultValue != null) {
                comma();
                if (fieldDef.isString() || fieldDef.isTimestamp() ||
                    fieldDef.isEnum()) {
                    stringField(DEFAULT, defaultValue);
                } else {
                    nonStringField(DEFAULT, defaultValue);
                }
            }

            /*
             * Special cases
             */

            if (fieldDef.isEnum()) {
                arrayOfString("symbols",
                              new ArrayList<String>(
                                  Arrays.asList(((EnumDefImpl) fieldDef).
                                                getValues())));
                comma();
                stringField(ENUM_NAME, name);
            }
            if (fieldDef.isFixedBinary()) {
                comma();
                nonStringField("size",
                               getString(((FixedBinaryDefImpl) fieldDef).
                                         getSize()));
            }

            if (fieldDef.isTimestamp()) {
                comma();
                nonStringField(TIMESTAMP_PRECISION,
                               getString(((TimestampDefImpl) fieldDef).
                                         getPrecision()));
            }
            if (fieldDef.isUUIDString()) {
                comma();
                nonStringField("uuid", "true");
                if (((StringDefImpl)fieldDef).isGenerated()) {
                    comma();
                    nonStringField("generated", "true");
                }
            }
        }

        @Override
        public void endField() {
            endObject();
        }

        @Override
        public void endFields() {
            sb.append("]");
        }

        @Override
        public void startCollection() {
            comma();
            appendString(COLLECTION);
            sep();
            startObject();
        }

        @Override
        public void endCollection() {
            endObject();
        }

        @Override
        public void end() {
            endObject();
        }

        @Override
        public String toString() {
            return sb.toString();
        }

        private String getString(int i) {
            return Integer.toString(i);
        }

        private void mapOfString(String key,
                                 Map<String, String> map,
                                 boolean withComma) {
            if (withComma) {
                comma();
            }
            sb.append(QUOT).append(key).append(QUOT);
            sep();
            startObject();
            boolean first = true;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) {
                    comma();
                } else {
                    first = false;
                }
                stringField(entry.getKey(), entry.getValue());
            }
            endObject();
        }
    }

    private static class JsonPrettyFormatter extends JsonFormatter {

        final static private String PRETTY_SEP = " : ";
        final static private String PRETTY_COMMA = ", ";
        final static private String CR = "\n";
        private String indent;
        private int currentIndent = 0;

        private void changeIndent(int num) {
            currentIndent += num;
            StringBuilder isb = new StringBuilder();
            for (int i = 0; i < currentIndent; i++) {
                isb.append(" ");
            }
            indent = isb.toString();
        }

        @Override
        protected void indent() {
            changeIndent(2);
        }

        @Override
        protected void unindent() {
            changeIndent(-2);
        }

        @Override
        protected void newline() {
            sb.append(CR).append(indent);
        }

        @Override
        protected void addIndent() {
            sb.append(indent);
        }

        @Override
        protected void sep() {
            sb.append(PRETTY_SEP);
        }

        @Override
        protected void arrayComma() {
            sb.append(PRETTY_COMMA);
        }

        @Override
        protected void comma(boolean withCR) {
            sb.append(COMMA);
            if (withCR) {
                newline();
            } else {
                sb.append(" ");
            }
        }
    }
}
