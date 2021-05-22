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

package oracle.kv.impl.api.table.serialize;

public class SerializationRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SerializationRuntimeException(Throwable cause) { super(cause); }
    public SerializationRuntimeException(String message) { super(message); }
    public SerializationRuntimeException(String message, Throwable cause) {
      super(message, cause);
    }
  }

