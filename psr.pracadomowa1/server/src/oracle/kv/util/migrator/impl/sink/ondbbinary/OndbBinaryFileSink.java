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

import static oracle.nosql.common.migrator.util.Constants.EXPORT_FORMAT_KEY;
import static oracle.nosql.common.migrator.util.Constants.EXPORT_VERSION_KEY;
import static oracle.nosql.common.migrator.util.Constants.ONDB_BINARY_TYPE;
import static oracle.nosql.common.migrator.util.MigratorUtils.formatElapseTime;
import static oracle.nosql.common.migrator.util.MigratorUtils.mbyteToByte;
import static oracle.nosql.common.migrator.util.MigratorUtils.toFilePath;
import static oracle.nosql.common.migrator.util.MigratorUtils.writeExportInfo;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.impl.test.TestHook;
import oracle.kv.impl.test.TestHookExecute;
import oracle.kv.util.expimp.utils.DataSerializer;
import oracle.kv.util.expimp.utils.exp.AbstractStoreOutput;
import oracle.kv.util.expimp.utils.exp.LocalStoreOutput;
import oracle.kv.util.expimp.utils.exp.ObjectStoreOutput;
import oracle.kv.util.migrator.impl.data.ondbbinary.RecordBytesEntry;
import oracle.nosql.common.migrator.DataSource;
import oracle.nosql.common.migrator.StateHandler;
import oracle.nosql.common.migrator.data.Entry;
import oracle.nosql.common.migrator.impl.FileConfig.FileStoreType;
import oracle.nosql.common.migrator.impl.sink.DataSinkBaseImpl;

/**
 * The sink that write binary entries supplied from nosqldb source to local
 * file system or object store on cloud storage.
 *
 * The data entries include table schema, table and KeyValue data and lob data.
 */
public class OndbBinaryFileSink extends DataSinkBaseImpl {

    private OndbBinaryFileSinkConfig config;
    private AbstractStoreOutput outputHdl;

    public OndbBinaryFileSink(OndbBinaryFileSinkConfig config,
                              StateHandler stateHandler,
                              Logger logger) {

        super(ONDB_BINARY_TYPE, makeName(config), stateHandler, logger);
        this.config = config;

        if (config.getFileStoreType() == FileStoreType.FILE) {
            outputHdl = new LocalStoreOutput(new File(config.getPath()),
                                             false /* exportTable */,
                                             logger);
        } else {
            assert(config.getFileStoreType() == FileStoreType.OCIC_OBJECT_STORE);
            outputHdl = new ObjectStoreOutput(config.getServiceName(),
                                              config.getUserName(),
                                              config.getPassword(),
                                              config.getServiceUrl(),
                                              config.getContainerName(),
                                              logger);
        }

        /* Set max file segement size in MB */
        if (config.getFileSizeMB() > 0) {
            AbstractStoreOutput.setMaxFileSegmentSize(
                mbyteToByte(config.getFileSizeMB()));
        }
    }

    private static String makeName(OndbBinaryFileSinkConfig cfg) {
        if (cfg.getFileStoreType() == FileStoreType.FILE) {
            return toFilePath(new File(cfg.getPath()));
        }
        return cfg.getServiceName() + "-" + cfg.getContainerName();
    }

    @Override
    protected void doWrite(DataSource[] sources) {
        int concurrency = Math.min(Runtime.getRuntime().availableProcessors(),
                                   sources.length);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        for (DataSource source : sources) {
            executor.submit(new OutputThread(source));
        }
        executor.shutdown();

        try {
            int count = 0;
            while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log(Level.INFO, "Writing continue.., wait " +
                                (++count) + " minutes");
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException(
                "Wait for completion of all tasks failed: " + ie.getMessage());
        }
    }

    @Override
    public void doPostWork() {
        outputHdl.waitForWriteTasksDone();
        if (config.getFileStoreType() == FileStoreType.FILE) {
            /*
             * Stores "exportFormat" and "exportVersion" to <path>/export.info
             * file.
             */
            Properties props = new Properties();
            props.put(EXPORT_FORMAT_KEY, ONDB_BINARY_TYPE);
            props.put(EXPORT_VERSION_KEY,
                      String.valueOf(DataSerializer.CURRENT_VERSION));
            writeExportInfo(config.getPath(), props);
        }
    }

    private class OutputThread extends Thread {
        private DataSource source;

        OutputThread(DataSource source) {
            this.source = source;
        }

        @Override
        public void run() {
            int count = 0;
            long startTime = System.currentTimeMillis();
            while(true) {
                try {
                    Entry entry = source.readNext();
                    if (entry == null) {
                        break;
                    }
                    assert (entry instanceof RecordBytesEntry);
                    RecordBytesEntry bentry = (RecordBytesEntry) entry;
                    outputHdl.write(bentry.getData());
                    count++;
                } catch (RuntimeException re) {
                    throw re;
                }
            }

            long endTime = System.currentTimeMillis();
            setComplete(source, endTime, count,
                        String.format("Exported " + count +
                                      " record from %s: %s", source.getName(),
                                      formatElapseTime(endTime - startTime)));

            runTestHookAfterExportSchemas(source);
        }
    }

    /*
     * Hook to induce schema changes. Used for testing
     */
    public static TestHook<String> CHANGE_HOOK;

    public static void setSchemaChangeHook(TestHook<String> testHook) {
        CHANGE_HOOK = testHook;
    }

    private static void runTestHookAfterExportSchemas(DataSource source) {
        if (source.getName().equals("tableSchema")) {
            /*
             * Hook to induce schema changes after exporting table schemas
             */
            assert TestHookExecute.doHookIfSet(CHANGE_HOOK, "TABLE,2");
        }
    }
}
