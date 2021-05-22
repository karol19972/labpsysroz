/*-
 *
 *  This file is part of Oracle NoSQL Database
 *  Copyright (C) 2011, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 *  Oracle NoSQL Database is free software: you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation, version 3.
 *
 *  Oracle NoSQL Database is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License in the LICENSE file along with Oracle NoSQL Database.  If not,
 *  see <http://www.gnu.org/licenses/>.
 *
 *  An active Oracle commercial licensing agreement for this product
 *  supercedes this license.
 *
 *  For more information please contact:
 *
 *  Vice President Legal, Development
 *  Oracle America, Inc.
 *  5OP-10
 *  500 Oracle Parkway
 *  Redwood Shores, CA 94065
 *
 *  or
 *
 *  berkeleydb-info_us@oracle.com
 *
 *  [This line intentionally left blank.]
 *  [This line intentionally left blank.]
 *  [This line intentionally left blank.]
 *  [This line intentionally left blank.]
 *  [This line intentionally left blank.]
 *  [This line intentionally left blank.]
 *  EOF
 *
 */

package com.sleepycat.je.utilint;

import com.sleepycat.je.log.FileManager.FIOStatsCollector;

/**
 * Marker interface identifying a Thread as being capable of collecting
 * File JE I/O statistics for I/O done by the thread.
 */
public interface FIOStatsCollectingThread {

    /**
     * Collect file i/o stats. If the thread is marked as an
     * FIOStatsCollectingThread, use the collector associated with it.
     * Otherwise associate the stats with the "misc" bucket.
     */
    static void collectIf(boolean read, long bytes,
                          FIOStatsCollector miscCollector) {
        final Thread daemonThread = Thread.currentThread();
        final FIOStatsCollectingThread fscThread =
            (daemonThread instanceof FIOStatsCollectingThread) ?
                ((FIOStatsCollectingThread)daemonThread) : null;

        if (fscThread != null) {
            fscThread.collect(read, bytes);
        } else {
            miscCollector.collect(read, bytes);
        }
    }

    /* The default method when one is not provided by the thread: It discards
     * the statistics.
     */
    default void collect(@SuppressWarnings("unused") boolean read,
                         @SuppressWarnings("unused") long bytes) {}
}
