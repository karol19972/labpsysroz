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

package oracle.kv;

import java.util.Map;
import java.util.HashMap;

/**
 * @hidden
 * Constants representing all of the possible parameters that can be placed
 * in the External Table config file.
 */
public class ParamConstant {
    private final static Map<String, ParamConstant> allParams =
        new HashMap<String, ParamConstant>();

    /* Publish Parameters */
    public final static ParamConstant CONNECTION_URL =
        new ParamConstant("oracle.kv.exttab.connection.url");
    public final static ParamConstant CONNECTION_USER =
        new ParamConstant("oracle.kv.exttab.connection.user");
    public final static ParamConstant CONNECTION_TNS_ADMIN_PATH =
        new ParamConstant("oracle.kv.exttab.connection.tns_admin");
    public final static ParamConstant CONNECTION_TNS_ENTRY_NAME =
        new ParamConstant("oracle.kv.exttab.connection.tnsEntryName");
    public final static ParamConstant CONNECTION_WALLET_PATH =
        new ParamConstant("oracle.kv.exttab.connection.wallet_location");
    public final static ParamConstant EXT_TABLE_NAME =
        new ParamConstant("oracle.kv.exttab.tableName");

    /* Preprocessor Parameters */
    public final static ParamConstant KVSTORE_NAME =
        new ParamConstant("oracle.kv.kvstore");
    public final static ParamConstant KVSTORE_NODES =
        new ParamConstant("oracle.kv.hosts");
    public final static ParamConstant BATCH_SIZE =
        new ParamConstant("oracle.kv.batchSize");
    public final static ParamConstant PARENT_KEY =
        new ParamConstant("oracle.kv.parentKey");
    public final static ParamConstant SUB_RANGE =
        new ParamConstant("oracle.kv.subRange");
    public final static ParamConstant DEPTH =
        new ParamConstant("oracle.kv.depth");
    public final static ParamConstant CONSISTENCY =
        new ParamConstant("oracle.kv.consistency");
    public final static ParamConstant TIMEOUT =
        new ParamConstant("oracle.kv.timeout");
    public final static ParamConstant KEY_DELIMITER =
        new ParamConstant("oracle.kv.keyDelimiter");
    public final static ParamConstant VARIABLE_SIZE_BYTES =
        new ParamConstant("oracle.kv.variableSizeBytes");
    public final static ParamConstant FORMATTER_CLASS =
        new ParamConstant("oracle.kv.formatterClass");
    public final static ParamConstant KVSTORE_SECURITY =
        new ParamConstant("oracle.kv.security");
    public final static ParamConstant AUTH_USER_PWD_PROPERTY =
        new ParamConstant("oracle.kv.auth.pwd");

    /* To support table API */
    public final static ParamConstant TABLE_NAME =
        new ParamConstant("oracle.kv.tableName");
    public final static ParamConstant PRIMARY_KEY =
        new ParamConstant("oracle.kv.primaryKey");
    public final static ParamConstant MAX_REQUESTS =
        new ParamConstant("oracle.kv.maxRequests");
    public final static ParamConstant MAX_BATCHES =
        new ParamConstant("oracle.kv.maxBatches");
    public final static ParamConstant FIELD_RANGE =
        new ParamConstant("oracle.kv.fieldRange");

    /* To support Hadoop/Hive integration with the table API */
    public final static ParamConstant KVHADOOP_NODES =
        new ParamConstant("oracle.kv.hadoop.hosts");

    /* Generated by Publish Utility. */
    public final static ParamConstant FILE_NUMBER =
        new ParamConstant("oracle.kv.exttab.externalTableFileNumber");
    public final static ParamConstant TOTAL_FILES =
        new ParamConstant("oracle.kv.exttab.totalExternalTableFiles");

    private String paramName;

    private ParamConstant(final String paramName) {
        this.paramName = paramName;
        allParams.put(paramName, this);
    }

    public String getName() {
        return paramName;
    }

    public static Map<String, ParamConstant> getAllParams() {
        return allParams;
    }
}
