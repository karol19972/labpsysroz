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

package oracle.kv.impl.async.exception;

import oracle.kv.impl.async.DialogContext;

/**
 * This exception is thrown when {@link DialogContext#write} is called after
 * last message was written.
 */
public class ContextWriteFinException extends ContextWriteException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception.
     */
    public ContextWriteFinException() {
        super("The context is already finished writing", null);
    }

    /**
     * Return an {@link IllegalStateException} since this exception represents
     * an unexpected situation and does not include an underlying exception
     * cause.
     */
    @Override
    public Throwable getUserException() {
        return new IllegalStateException(getMessage(), this);
    }
}
