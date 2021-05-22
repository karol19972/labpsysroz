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
package oracle.kv.util.migrator.impl.sink.ondbbinary;

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
 * The factory that creates OndbBinaryFileSink object.
 */
public class OndbBinaryFileSinkFactory extends FactoryBase
    implements DataSinkFactory {

    public OndbBinaryFileSinkFactory() {
        super(ONDB_BINARY_TYPE, OndbBinaryFileSinkConfig.COMMAND_ARGS);
    }

    @Override
    public DataSink createDataSink(DataSinkConfig config,
                                   String sourceType,
                                   StateHandler stateHandler,
                                   Logger logger) {
        return new OndbBinaryFileSink((OndbBinaryFileSinkConfig)config,
                                      stateHandler, logger);
    }

    @Override
    public DataSinkConfig parseJson(InputStream in, int configVersion) {
        return new OndbBinaryFileSinkConfig(in, configVersion);
    }

    @Override
    public DataSinkConfig createConfig(MigratorCommandParser parser) {
        return new OndbBinaryFileSinkConfig(parser);
    }

    @Override
    public boolean isTextual() {
        return true;
    }
}
