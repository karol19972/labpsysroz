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

package oracle.kv.impl.measurement;

import java.io.Serializable;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import oracle.kv.impl.monitor.Metrics;

/**
 * A wrapper for a java.util.logging message issued at a service, which
 * is forwarded to the AdminService to display in the store-wide consolidated
 * view.
 */
public class LoggerMessage implements Measurement, Serializable {

    // TODO: Is it too heavyweight to send the LogRecord? An alternative is to
    // send the message level, timestamp and string.

    private static final long serialVersionUID = 1L;

    /**
     * A log record formatter so that message parameters can get substituted in
     * the string output for a LoggerMessage object.
     */
    private static final Formatter logRecordFormatter = new SimpleFormatter();

    private final LogRecord logRecord;

    public LoggerMessage(LogRecord logRecord) {
        this.logRecord = logRecord;
    }

    @Override
    public int getId() {
        return Metrics.LOG_MSG.getId();
    }

    @Override
    public String toString() {
        return logRecordFormatter.formatMessage(logRecord);
    }

    public LogRecord getLogRecord() {
        return logRecord;
    }

    @Override
    public long getStart() {
        return 0;
    }

    @Override
    public long getEnd() {
        return 0;
    }
}
