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

package oracle.kv.util.migrator.impl.sink.ondb;

import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;
import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;

import java.io.InputStream;
import java.util.logging.Logger;

import oracle.nosql.common.migrator.DataSink;
import oracle.nosql.common.migrator.DataSinkConfig;
import oracle.nosql.common.migrator.DataSinkFactory;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.StateHandler;
import oracle.nosql.common.migrator.impl.FactoryBase;

/**
 * Creates StoreSink object to write to NoSQL store
 */
public class OndbSinkFactory extends FactoryBase implements DataSinkFactory {

    public OndbSinkFactory() {
        super(ONDB_TYPE, OndbSinkConfig.COMMAND_ARGS);
    }

    @Override
    public DataSink createDataSink(DataSinkConfig config,
                                   String sourceType,
                                   StateHandler stateHandler,
                                   Logger logger) {

        OndbSinkConfig cfg = (OndbSinkConfig)config;
        if (sourceType.equals(ONDB_BINARY_TYPE)) {
            return new OndbBinarySink(cfg, stateHandler, logger);
        }
        return new OndbRowSink(cfg, stateHandler, logger);
    }

    @Override
    public DataSinkConfig parseJson(InputStream in, int configVersion) {
        return new OndbSinkConfig(in, configVersion);
    }

    @Override
    public DataSinkConfig createConfig(MigratorCommandParser parser){
        return new OndbSinkConfig(parser);
    }

    @Override
    public String getConfigCommandArgs(String type) {
        if (type.equalsIgnoreCase(ONDB_BINARY_TYPE)) {
            return OndbSinkConfig.COMMAND_ARGS_BINARY;
        }
        return super.getConfigCommandArgs(type);
    }
}
