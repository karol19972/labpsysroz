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

package oracle.kv.impl.async.registry;

import static oracle.kv.impl.util.ObjectUtil.checkNull;
import static oracle.kv.impl.util.SerializationUtil.readNonNullSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.readString;
import static oracle.kv.impl.util.SerializationUtil.writeNonNullSequenceLength;
import static oracle.kv.impl.util.SerializationUtil.writeString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import oracle.kv.impl.async.AsyncVersionedRemote;
import oracle.kv.impl.util.ReadFastExternal;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The remote interface for the service registry, which maps service names to
 * service endpoints.
 *
 * @see ServiceRegistryAPI
 */
public interface ServiceRegistry extends AsyncVersionedRemote {

    /** The IDs for methods in this interface. */
    enum RegistryMethodOp implements MethodOp {

        /**
         * The ID for the {@link AsyncVersionedRemote#getSerialVersion}
         * method, with ordinal 0.
         */
        GET_SERIAL_VERSION(0, GetSerialVersionCall::new),

        /**
         * The ID for the {@link ServiceRegistry#lookup} method, with ordinal
         * 1.
         */
        LOOKUP(1, LookupCall::new),

        /**
         * The ID for the {@link ServiceRegistry#bind} method, with ordinal 2.
         */
        BIND(2, BindCall::new),

        /**
         * The ID for the {@link ServiceRegistry#unbind} method, with ordinal
         * 3.
         */
        UNBIND(3, UnbindCall::new),

        /**
         * The ID for the {@link ServiceRegistry#list} method, with ordinal 4.
         */
        LIST(4, ListCall::new);

        private static final RegistryMethodOp[] VALUES = values();

        private final ReadFastExternal<MethodCall<?>> reader;

        RegistryMethodOp(final int ordinal,
                         final ReadFastExternal<MethodCall<?>> reader) {
            if (ordinal != ordinal()) {
                throw new IllegalArgumentException("Wrong ordinal");
            }
            this.reader = reader;
        }

        /**
         * Returns the {@code RegistryMethodOp} with the specified ordinal.
         *
         * @param ordinal the ordinal
         * @return the {@code RegistryMethodOp}
         * @throws IllegalArgumentException if the value is not found
         */
        public static RegistryMethodOp valueOf(int ordinal) {
            try {
                return VALUES[ordinal];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException(
                    "Wrong ordinal for RegistryMethodOp: " + ordinal, e);
            }
        }

        @Override
        public int getValue() {
            return ordinal();
        }

        @Override
        public MethodCall<?> readRequest(final DataInput in,
                                         final short serialVersion)
            throws IOException
        {
            return reader.readFastExternal(in, serialVersion);
        }

        @Override
        public String toString() {
            return name() + '(' + ordinal() + ')';
        }
    }

    /** Method call for getSerialVersion */
    class GetSerialVersionCall extends AbstractGetSerialVersionCall {
        GetSerialVersionCall() { }
        @SuppressWarnings("unused")
        GetSerialVersionCall(DataInput in, short serialVersion) { }
        @Override
        public RegistryMethodOp getMethodOp() {
            return RegistryMethodOp.GET_SERIAL_VERSION;
        }
        @Override
        public String describeCall() {
            return "ServiceRegistry.GetSerialVersionCall";
        }
    }

    /** Method call for lookup */
    class LookupCall implements MethodCall<ServiceEndpoint> {
        final String name;
        LookupCall(final String name) { this.name = name; }
        LookupCall(final DataInput in, final short serialVersion)
            throws IOException
        {
            this(readString(in, serialVersion));
        }
        @Override
        public RegistryMethodOp getMethodOp() {
            return RegistryMethodOp.LOOKUP;
        }
        @Override
        public void writeFastExternal(final DataOutput out,
                                      final short serialVersion)
            throws IOException
        {
            writeString(out, serialVersion, name);
        }
        @Override
        public void writeResponse(final @Nullable ServiceEndpoint endpoint,
                                  final DataOutput out,
                                  final short serialVersion)
            throws IOException
        {
            if (endpoint != null) {
                out.writeBoolean(true);
                endpoint.writeFastExternal(out, serialVersion);
            } else {
                out.writeBoolean(false);
            }
        }

        @Override
        public @Nullable ServiceEndpoint readResponse(
            final DataInput in, final short serialVersion)
            throws IOException
        {
            return in.readBoolean() ?
                new ServiceEndpoint(in, serialVersion) :
                null;
        }
        @Override
        public String describeCall() {
            return "ServiceRegistry.LookupCall[name=" + name + "]";
        }
    }

    /** Method call for bind */
    class BindCall extends MethodCallVoid {
        final String name;
        final ServiceEndpoint endpoint;
        BindCall(final String name, final ServiceEndpoint endpoint) {
            this.name = name;
            this.endpoint = endpoint;
        }
        BindCall(final DataInput in, final short serialVersion)
            throws IOException
        {
            this(readString(in, serialVersion),
                 new ServiceEndpoint(in, serialVersion));
        }
        @Override
        public RegistryMethodOp getMethodOp() {
            return RegistryMethodOp.BIND;
        }
        @Override
        public void writeFastExternal(final DataOutput out,
                                      final short serialVersion)
            throws IOException
        {
            writeString(out, serialVersion, name);
            endpoint.writeFastExternal(out, serialVersion);
        }
        @Override
        public String describeCall() {
            return "ServiceRegistry.BindCall[" +
                "name=" + name + " endpoint=" + endpoint + "]";
        }
    }

    /** Method call for unbind */
    class UnbindCall extends MethodCallVoid {
        final String name;
        UnbindCall(final String name) { this.name = name; }
        UnbindCall(final DataInput in, final short serialVersion)
            throws IOException
        {
            this(readString(in, serialVersion));
        }
        @Override
        public RegistryMethodOp getMethodOp() {
            return RegistryMethodOp.UNBIND;
        }
        @Override
        public void writeFastExternal(final DataOutput out,
                                      final short serialVersion)
            throws IOException
        {
            writeString(out, serialVersion, name);
        }
        @Override
        public String describeCall() {
            return "ServiceRegistry.UnbindCall[name=" + name + "]";
        }
    }

    /** Method call for list */
    class ListCall implements MethodCall<List<String>> {
        ListCall() { }
        @SuppressWarnings("unused")
        ListCall(DataInput in, short serialVersion) { }
        @Override
        public RegistryMethodOp getMethodOp() {
            return RegistryMethodOp.LIST;
        }
        @Override
        public void writeFastExternal(DataOutput out, short serialVersion) { }
        @Override
        public void writeResponse(final @Nullable List<String> response,
                                  final DataOutput out,
                                  final short serialVersion)
            throws IOException
        {
            final List<String> r = checkNull("response", response);
            writeNonNullSequenceLength(out, r.size());
            for (final String name : r) {
                writeString(out, serialVersion, name);
            }
        }
        @Override
        public List<String> readResponse(final DataInput in,
                                         final short serialVersion)
            throws IOException
        {
            final int count = readNonNullSequenceLength(in);
            final List<String> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add(readString(in, serialVersion));
            }
            return result;
        }
        @Override
        public String describeCall() {
            return "ServiceRegistry.ListCall";
        }
    }

    /**
     * Look up an entry in the registry.
     *
     * @param serialVersion the serial version to use for communication
     * @param name the name of the entry
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return future that returns the service endpoint or {@code null}
     * if the entry is not found
     */
    CompletableFuture<ServiceEndpoint> lookup(short serialVersion,
                                              String name,
                                              long timeoutMillis);

    /**
     * Set an entry in the registry.
     *
     * @param serialVersion the serial version to use for communication
     * @param name the name of the entry
     * @param endpoint the endpoint to associate with the name
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return future for when the operation is complete
     */
    CompletableFuture<Void> bind(short serialVersion,
                                 String name,
                                 ServiceEndpoint endpoint,
                                 long timeoutMillis);

    /**
     * Remove an entry from the registry.
     *
     * @param serialVersion the serial version to use for communication
     * @param name the name of the entry
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return future for when the operation is complete
     */
    CompletableFuture<Void> unbind(short serialVersion,
                                   String name,
                                   long timeoutMillis);

    /**
     * List the entries in the registry.
     *
     * @param serialVersion the serial version to use for communication
     * @param timeoutMillis the timeout for the operation in milliseconds
     * @return future that returns a list entry names
     */
    CompletableFuture<List<String>> list(short serialVersion,
                                         long timeoutMillis);
}
