/*-
 * Copyright (C) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sleepycat.je.evictor;

/**
 * Used to create OffHeapAllocator instances.
 */
public class OffHeapAllocatorFactory {

    private OffHeapAllocator defaultAllocator;

    OffHeapAllocatorFactory()
        throws ClassNotFoundException, IllegalAccessException,
        InstantiationException {

        /*
         * The CHeapAllocator class should not be referenced symbolically here
         * or by any other other class. This is necessary to avoid a linkage
         * error if JE is run on a JVM without the Unsafe class. Therefore we
         * load CHeapAllocator and create an instance using reflection.
         */
        final Class<?> cls =
            Class.forName("com.sleepycat.je.evictor.CHeapAllocator");

        defaultAllocator = (OffHeapAllocator) cls.newInstance();
    }

    /**
     * @return null if the default allocator is not available on this JVM,
     * presumably because the Unsafe class is not available.
     */
    public OffHeapAllocator getDefaultAllocator() {
        return defaultAllocator;
    }
}
