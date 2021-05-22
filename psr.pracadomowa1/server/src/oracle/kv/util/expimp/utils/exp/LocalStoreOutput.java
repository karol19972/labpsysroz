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

package oracle.kv.util.expimp.utils.exp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.util.expimp.utils.Chunk;
import oracle.kv.util.expimp.utils.exp.CustomStream.CustomInputStream;

/**
 * An implementation of AbstractStoreOutput that manages output to local file
 * system.
 */
public class LocalStoreOutput extends AbstractStoreOutput {

    /*
     * Directory path inside the export package holding all the table data files
     */
    private File tableFolder;

    /*
     * Directory path inside the export package holding all the LOB data files
     */
    private File lobFolder;

    /*
     * Directory path inside the export package holding None format data files
     */
    private File otherFolder;

    /*
     * Directory inside the export package holding the schema definition files
     */
    private File schemaFolder;

    /*
     * Export folder directory names
     */
    private static final String schemaFolderName = "SchemaDefinition";
    private static final String dataFolderName = "Data";
    private static final String lobFolderName = "LOB";
    private static final String tableFolderName = "Table";
    private static final String otherFolderName = "Other";

    /*
     * Size of exported LOB file segment = 1GB
     */
    private static final long fileSize = 1000 * 1000 * 1000;

    private static final int EXPORT_BUFFER_SIZE = 4096;

    /**
     * Constructor that creates the export package directory structure
     *
     * @param exportFolder path in local file system for export package
     */
    public LocalStoreOutput(File exportFolder,
                            boolean exportTable,
                            Logger logger) {

        super(logger);

        schemaFolder = new File(exportFolder, schemaFolderName);
        schemaFolder.mkdir();

        File dataFolder = new File(exportFolder, dataFolderName);
        tableFolder = new File(dataFolder, tableFolderName);
        tableFolder.mkdirs();

        if (!exportTable) {
            lobFolder = new File(dataFolder, lobFolderName);
            lobFolder.mkdir();
            otherFolder = new File(dataFolder, otherFolderName);
            otherFolder.mkdir();
        }
    }

    /**
     * Export the file segment to the export package in local file system
     *
     * @param fileName file being exported
     * @param chunkSequence identifier for the file segment being exported
     * @param stream input stream reading bytes from kvstore into export store
     */
    @Override
    public boolean doExport(String fileName,
                            String chunkSequence,
                            CustomInputStream stream) {

        OutputStream output = null;
        File file;
        if (fileName.equals(schemaFolderName)) {

            /*
             * Exported entity is a schema definition file segment
             */
            file = getFile(schemaFolder,
                           fileName + "-" + chunkSequence +  ".txt");

        } else if (fileName.contains(otherFolderName)) {

            /*
             * Exported entity is OtherData file segment
             */
            file = getFile(otherFolder,
                           fileName + "-" + chunkSequence + ".data");

        } else if (fileName.contains(lobFolderName)) {

            /*
             * Exported entity is a LOB file segment
             */
            file = getFile(new File(lobFolder, fileName),
                           fileName + "-" + chunkSequence + ".data");

        } else {

            /*
             * Exported entity is a table file segment
             */
            file = getFile(new File(tableFolder, fileName),
                           fileName + "-" + chunkSequence + ".data");
        }

        try {

            /*
             * Export the file segment into the export package
             */
            output = new FileOutputStream(file);
            exportDataStream(stream, output);
        } catch (Exception e) {

            logger.log(Level.SEVERE, "Exception exporting " + fileName +
                       ". Chunk sequence: " + chunkSequence, e);

            return false;
        } finally {

            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {

                    logger.log(Level.SEVERE, "Exception exporting " + fileName +
                               ". Chunk sequence: " + chunkSequence, e);

                    return false;
                }
            }

            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {

                    logger.log(Level.SEVERE, "Exception exporting " + fileName +
                               ". Chunk sequence: " + chunkSequence, e);

                    return false;
                }
            }
        }

        return true;
    }

    private File getFile(File dir,  String fileName) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                logger.log(Level.SEVERE, "Fail to create directory: " + dir);
                throw new IllegalArgumentException("Fail to create directory");
            }
        }
        return new File(dir, fileName);
    }

    /**
     * Export the stream of data from kvstore to the local filesystem
     *
     * @param input the InputStream to read from
     * @param output the OuputStream to write to
     * @throws IOException
     */
    private void exportDataStream(final InputStream input,
                                  final OutputStream output)
        throws IOException {

        int bytesRead = 0;
        byte[] buffer = new byte[EXPORT_BUFFER_SIZE];

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    /**
     * No work needs to be done post export in case of local file system
     */
    @Override
    public void doPostExportWork(Map<String, Chunk> chunks) {
    }

    /**
     * Returns the maximum size of lob file segment that will be exported
     */
    @Override
    public long getMaxLobFileSize() {
        return fileSize;
    }
}