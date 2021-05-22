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

import static oracle.kv.impl.async.StandardDialogTypeFamily.SERVICE_REGISTRY_DIALOG_TYPE;

import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;

import oracle.kv.impl.async.AsyncVersionedRemoteInitiator;
import oracle.kv.impl.async.CreatorEndpoint;

/**
 * An initiator (client-side) implementation of {@link ServiceRegistry}.
 *
 * @see ServiceRegistryAPI
 */
class ServiceRegistryInitiator extends AsyncVersionedRemoteInitiator
    implements ServiceRegistry {

    ServiceRegistryInitiator(CreatorEndpoint endpoint,
                             Logger logger) {
        super(endpoint, SERVICE_REGISTRY_DIALOG_TYPE, logger);
    }

    @Override
    protected GetSerialVersionCall getSerialVersionCall() {
        return new GetSerialVersionCall();
    }

    @Override
    public CompletableFuture<ServiceEndpoint> lookup(final short serialVersion,
                                                     final String name,
                                                     final long timeout) {
        return startDialog(serialVersion, new LookupCall(name), timeout);
    }

    @Override
    public CompletableFuture<Void> bind(final short serialVersion,
                                        final String name,
                                        final ServiceEndpoint serviceEndpoint,
                                        final long timeout) {
        return startDialog(serialVersion, new BindCall(name, serviceEndpoint),
                           timeout);
    }

    @Override
    public CompletableFuture<Void> unbind(final short serialVersion,
                                          final String name,
                                          final long timeout) {
        return startDialog(serialVersion, new UnbindCall(name), timeout);
    }

    @Override
    public CompletableFuture<List<String>> list(final short serialVersion,
                                                final long timeout) {
        return startDialog(serialVersion, new ListCall(), timeout);
    }
}
