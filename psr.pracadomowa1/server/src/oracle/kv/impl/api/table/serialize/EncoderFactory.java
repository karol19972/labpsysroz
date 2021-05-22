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
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package oracle.kv.impl.api.table.serialize;

import java.io.OutputStream;

/**
 * A factory for creating and configuring {@link Encoder} instances.
 * <p/>
 * Factory methods that create Encoder instances are thread-safe.
 * Multiple instances with different configurations can be cached
 * by an application.
 * 
 * @see Encoder
 * @see BinaryEncoder
 * @see BufferedBinaryEncoder
 */

public class EncoderFactory {
    private static final int DEFAULT_BUFFER_SIZE = 2048;
    private static final int DEFAULT_BLOCK_BUFFER_SIZE = 64 * 1024;

    private static final EncoderFactory DEFAULT_FACTORY = new DefaultEncoderFactory();

    protected int binaryBufferSize = DEFAULT_BUFFER_SIZE;
    protected int binaryBlockSize = DEFAULT_BLOCK_BUFFER_SIZE;

    /**
     * Returns an immutable static DecoderFactory with default configuration.
     * All configuration methods throw RuntimeExceptions if called.
     */
    public static EncoderFactory get() {
        return DEFAULT_FACTORY;
    }

    /**
     * Configures this factory to use the specified buffer size when creating
     * Encoder instances that buffer their output. The default buffer size is
     * 2048 bytes.
     * 
     * @param size
     *            The buffer size to configure new instances with. Valid values
     *            are in the range [32, 16*1024*1024]. Values outside this range
     *            are set to the nearest value in the range. Values less than
     *            256 will limit performance but will consume less memory if the
     *            BinaryEncoder is short-lived, values greater than 8*1024 are
     *            not likely to improve performance but may be useful for the
     *            downstream OutputStream.
     * @return This factory, to enable method chaining:
     * 
     *         <pre>
     *         EncoderFactory factory = new EncoderFactory().configureBufferSize(4096);
     *         </pre>
     * 
     * @see #binaryEncoder(OutputStream, byte[])
     */
    public EncoderFactory configureBufferSize(int size) {
        if (size < 32)
            size = 32;
        if (size > 16 * 1024 * 1024)
            size = 16 * 1024 * 1024;
        this.binaryBufferSize = size;
        return this;
    }

    /**
     * Creates a {@link BinaryEncoder} with the OutputStream provided as the
     * destination for written data. If {@code buffer} is non-null, it will be
     * used as the buffer if its size matches the configured buffer size.
     * <p>
     * The {@link BinaryEncoder} implementation returned may buffer its output.
     * Data may not appear on the underlying OutputStream until
     * {@link Encoder#flush()} is called. The buffer size is configured with
     * {@link #configureBufferSize(int)}.
     * </p>
     * <p/>
     * {@link BinaryEncoder} instances returned by this method are not
     * thread-safe
     *
     * @param out
     *            The OutputStream to write to. Cannot be null.
     * @param buffer
     *            a byte array to use as the binary buffer if its size matches
     *            the configured buffer size, or null
     * @return A BinaryEncoder that uses <i>out</i> as its data output.
     * @see BufferedBinaryEncoder
     * @see Encoder
     */
    public BinaryEncoder binaryEncoder(OutputStream out, byte[] buffer) {
        return new BufferedBinaryEncoder(out, binaryBufferSize, buffer);
    }

    /**
     * Returns the binary buffer size.
     *
     * @return the binary buffer size
     */
    public int getBinaryBufferSize() {
        return binaryBufferSize;
    }

    // default encoder is not mutable
    private static class DefaultEncoderFactory extends EncoderFactory {
        @Override
        public EncoderFactory configureBufferSize(int size) {
            throw new RuntimeException(
                    "Default EncoderFactory cannot be configured");
        }
    }
}
