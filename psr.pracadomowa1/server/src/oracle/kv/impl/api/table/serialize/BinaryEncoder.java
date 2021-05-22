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

import static oracle.kv.impl.util.SerializationUtil.LOCAL_BUFFER_SIZE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import oracle.kv.impl.api.table.serialize.util.Utf8;

/**
 * An abstract {@link Encoder} for binary encoding.
 * <p/>
 * To construct and configure instances, use {@link EncoderFactory}
 * 
 * @see EncoderFactory
 * @see BufferedBinaryEncoder
 * @see Encoder
 * @see Decoder
 */
public abstract class BinaryEncoder extends Encoder {

    /** A thread-local byte buffer used by writeString. */
    private static final ThreadLocal<ByteBuffer> stringByteBuffer =
        ThreadLocal.withInitial(() -> ByteBuffer.allocate(LOCAL_BUFFER_SIZE));

    /** A thread-local UTF8 CharsetEncoder used by writeString. */
    private static final ThreadLocal<CharsetEncoder> utf8Encoder =
        ThreadLocal.withInitial(() -> StandardCharsets.UTF_8.newEncoder());

    @Override
    public void writeNull() throws IOException {
    }

    @Override
    public void writeString(Utf8 utf8) throws IOException {
        this.writeBytes(utf8.getBytes(), 0, utf8.getByteLength());
    }

    @Override
    public void writeString(String string) throws IOException {
        final int charLength = string.length();
        if (0 == charLength) {
            writeZero();
            return;
        }
        final ByteBuffer bytes = getUTF8Bytes(string);
        final int utf8Length = bytes.remaining();
        writeInt(utf8Length);
        writeFixed(bytes.array(), 0, utf8Length);
    }

    /**
     * Gets the encoded version of the String.
     *
     * @see CharsetEncoder#encode(CharBuffer)
     */
    private ByteBuffer getUTF8Bytes(String string)
        throws CharacterCodingException {

        /* Convert String to CharBuffer */
        final CharBuffer chars = CharBuffer.wrap(string);

        /* Use thread local encoder */
        final CharsetEncoder encoder = utf8Encoder.get().reset();

        /* Get result buffer, using local cache if possible */
        int bufferSize =
            (int) (chars.remaining() * encoder.averageBytesPerChar());
        ByteBuffer bytes;
        if (bufferSize <= LOCAL_BUFFER_SIZE) {
            bufferSize = LOCAL_BUFFER_SIZE;
            bytes = stringByteBuffer.get();
            bytes.clear();
        } else {
            bytes = ByteBuffer.allocate(bufferSize);
        }

        /* Try encoding, increasing buffer size as needed */
        while (true) {
            CoderResult result = chars.hasRemaining() ?
                encoder.encode(chars, bytes, true) :
                CoderResult.UNDERFLOW;
            if (result.isUnderflow()) {
                result = encoder.flush(bytes);
            }
            if (result.isUnderflow()) {
                break;
            }
            if (result.isOverflow()) {

                /* Double size. Note size was at least LOCAL_BUFFER_SIZE. */
                bufferSize = 2 * bufferSize;
                final ByteBuffer newBytes = ByteBuffer.allocate(bufferSize);
                bytes.flip();
                newBytes.put(bytes);
                bytes = newBytes;
            } else {
                result.throwException();
            }
        }

        /* Done */
        bytes.flip();
        return bytes;
    }

    @Override
    public void writeBytes(ByteBuffer bytes) throws IOException {
        int len = bytes.limit() - bytes.position();
        if (0 == len) {
            writeZero();
        } else {
            writeInt(len);
            writeFixed(bytes);
        }
    }

    @Override
    public void writeBytes(byte[] bytes, int start, int len)
            throws IOException {
        if (0 == len) {
            writeZero();
            return;
        }
        this.writeInt(len);
        this.writeFixed(bytes, start, len);
    }

    @Override
    public void writeEnum(int e) throws IOException {
        this.writeInt(e);
    }

    @Override
    public void writeArrayStart() throws IOException {
    }

    @Override
    public void setItemCount(long itemCount) throws IOException {
        if (itemCount > 0) {
            this.writeLong(itemCount);
        }
    }

    @Override
    public void startItem() throws IOException {
    }

    @Override
    public void writeArrayEnd() throws IOException {
        writeZero();
    }

    @Override
    public void writeMapStart() throws IOException {
    }

    @Override
    public void writeMapEnd() throws IOException {
        writeZero();
    }

    @Override
    public void writeIndex(int unionIndex) throws IOException {
        writeInt(unionIndex);
    }

    /** Write a zero byte to the underlying output. **/
    protected abstract void writeZero() throws IOException;

    /**
     * Returns the number of bytes currently buffered by this encoder. If this
     * Encoder does not buffer, this will always return zero.
     * <p/>
     * Call {@link #flush()} to empty the buffer to the underlying output.
     */
    public abstract int bytesBuffered();

}

