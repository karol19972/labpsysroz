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

package oracle.kv.impl.diagnostic.execution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import oracle.kv.impl.diagnostic.LogFileInfo;
import oracle.kv.impl.diagnostic.LogFileInfo.LogFileType;
import oracle.kv.impl.diagnostic.LogInfo;
import oracle.kv.impl.diagnostic.LogSectionFileInfo;



/**
 * The class is to detect which logs in a log file are belong to master log and
 * extract the master log items.
 *
 * The format of master log items in log file is as follows:
 *
 * item 1    --
 * item 2      |
 * item 3      |
 * item 4      | -- non-master log items
 * item 5      |
 * ...         |
 * item 100  --
 * item 101  --
 * ...         | -- master log items
 * item 150  --
 * item 151
 * ...
 *
 * So the master log items in log files are continuous. The continuous master
 * log items is called master log section in this class. This class is to
 * extract all master log sections from log files and then store the sections
 * into a file and give return to indicate the info of the master log sections.
 */
public class MasterLogExtractor extends LogExtractor {
    private String MASTER_CHANGED_STR = "Master changed to ";
    private String TEMP_FILE_SUFFIX = "_masterlog.tmp";
    private String UNDER_LINE = "_";
    private String prefixName;

    /* List to store the log section file info generated by this class */
    private List<LogSectionFileInfo> sectionList =
            new ArrayList<LogSectionFileInfo>();

    public MasterLogExtractor(LogFileType logFileType, String prefixName) {
        super(logFileType);
        this.prefixName = prefixName;
    }

    @Override
    protected void extract(Map<String, List<LogFileInfo>> logFileInfoMap) {

        for (Map.Entry<String, List<LogFileInfo>> entry :
                logFileInfoMap.entrySet()) {
            String nodeName = entry.getKey();
            List<LogFileInfo> logFileInfoList = entry.getValue();
            if (!logFileInfoList.isEmpty()) {
                File file = new File(prefixName + UNDER_LINE + nodeName +
                                     TEMP_FILE_SUFFIX);

                BufferedWriter bw = null;
                List<String> timestampList = null;
                try {
                    bw = new BufferedWriter(new FileWriter(file));
                    /* Sort the file in the list by sequence ID */
                    Collections.sort(logFileInfoList,
                                     new LogFileInfo.LogFileInfoComparator());
                    timestampList = extractMasterLog(bw, logFileInfoList);
                } catch (IOException ex) {
                } finally {
                    if (bw != null) {
                        try {
                            bw.close();
                        } catch (IOException ex) {
                        }
                    }
                }

                /* No master log section found, delete the result file */
                if (timestampList == null || timestampList.isEmpty()) {
                    file.delete();
                } else {
                    /* Add log section file info into result list */
                    LogSectionFileInfo fileInfo =
                                    new LogSectionFileInfo(file,timestampList);
                    sectionList.add(fileInfo);
                }
            }
        }
    }

    /**
     * Get the result list of log section file info
     * @return the result list
     */
    public List<LogSectionFileInfo> getLogSectionFileInfoList() {
        return sectionList;
    }

    /**
     * Extract master log for a node.
     *
     * @return the time stamp list of the first log item for master log items.
     */
    private List<String> extractMasterLog(BufferedWriter bw,
                                  List<LogFileInfo> logFileInfoList) {
        BufferedReader br = null;
        boolean isExtractLog = false;

        /* Flag is to indicate whether find an node become a master. */
        boolean isNoFoundMasterLog = true;

        /* Save the logs of the first master before it becomes master. */
        List<LogInfo> preLogList = new ArrayList<LogInfo>();

        /* Save the logs of master section */
        List<LogInfo> logList = new ArrayList<LogInfo>();

        /* Save the time stamp of all master log sections */
        List<String> timestampList = new ArrayList<String>();

        for (LogFileInfo logFileInfo : logFileInfoList) {
            try {
                br = new BufferedReader(
                        new FileReader(new File(logFileInfo.getFilePath())));
                String line;
                while ((line = br.readLine()) != null) {


                    /*
                     * Set isNoFoundMasterLog as false When find
                     * MASTER_CHANGED_STR in log file. MASTER_CHANGED_STR
                     * output in log file means an become a master.
                     */
                    if (line.contains(MASTER_CHANGED_STR)) {
                        isNoFoundMasterLog = false;
                    }

                    /*
                     * Before find MASTER_CHANGED_STR, store all logs in
                     * preLogList.
                     */
                    if (isNoFoundMasterLog) {
                        preLogList.add(new LogInfo(line));
                    }

                    if (isExtractLog) {
                        /*
                         * During extracting log, log containing
                         * MASTER_CHANGED_STR means the master is changed to
                         * another node. And stop extracting log from this
                         * log file.
                         */
                        if (line.contains(MASTER_CHANGED_STR)) {
                            /*
                             * Write the master log of this  master log
                             * section into file.
                             */
                             String timestamp = writeMasterLog(bw, preLogList,
                                     logList);
                             timestampList.add(timestamp);

                            /*
                             * Set isExtractLog as false to ensure the
                             * extracting of this section ends
                             */
                            isExtractLog = false;
                        } else {
                            /*
                             * Add the line into logList when extracts does not
                             * end.
                             */
                            logList.add(new LogInfo(line));
                        }
                    } else {
                        /*
                         * When find the log indicate the master become the
                         * owner of this log, start to extract log.
                         */
                        if (line.contains(MASTER_CHANGED_STR +
                                         logFileInfo.getNodeID())) {
                            isExtractLog = true;
                            logList.add(new LogInfo(line));
                        }

                        /*
                         * When find the log indicate the master become another
                         * node, clear all logs in preLogList. Because another
                         * node becoming the master means the owner of log
                         * never become the first master.
                         */
                        if (line.contains(MASTER_CHANGED_STR) &&
                           !line.contains(MASTER_CHANGED_STR +
                                          logFileInfo.getNodeID())) {
                            preLogList.clear();
                        }
                    }
                }

            } catch (Exception ex) {
            } finally {
                try {
                    if (br != null)
                        br.close();
                } catch (IOException ex) {

                }
            }
        }

        try {
            if (preLogList.size()>0 || logList.size()>0) {
                /*
                 * Write the master log of this master log section into file.
                 */
                String timestamp = writeMasterLog(bw, preLogList, logList);
                timestampList.add(timestamp);
            }

        } catch (IOException ex) {

        }

        return timestampList;
    }

    /**
     * Write master log items into file.
     *
     * @return the time stamp of the first log.
     */
    private String writeMasterLog(BufferedWriter bw,
                                List<LogInfo> preLogList,
                                List<LogInfo> logList) throws IOException {
        String logTimestampString = null;
        /*
         * Get the time stamp of the first log of the master log items.
         */
        if (!preLogList.isEmpty()) {
            logTimestampString =
                    preLogList.get(0).getTimestampString();

        } else if (!logList.isEmpty()) {
            logTimestampString =
                    logList.get(0).getTimestampString();
        }

        /* Output the logs into file */
        for (LogInfo logInfo : preLogList) {
            bw.write(logInfo.toString());
            bw.newLine();
        }
        preLogList.clear();

        for (LogInfo logInfo : logList) {
            bw.write(logInfo.toString());
            bw.newLine();
        }
        logList.clear();

        return logTimestampString;
    }
}
