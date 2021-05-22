package com.sleepycat.je.utilint;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Get;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.evictor.OffHeapCache;

public class Preload {

    /**
     * Preloads INs and (optionally) LNs in the given DB, stopping when the
     * cache is filled up to the given percentage. This simply uses a cursor
     * to warm the cache.
     *
     * @return true if the cache is filled to the given percentage.
     */
    public static boolean preloadDb(final Database db,
                                    final boolean loadLNs,
                                    final int cachePercent) {
        assert cachePercent >= 0 && cachePercent <= 100 : cachePercent;
        final Environment env = db.getEnvironment();
        final EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        final MemoryBudget memoryBudget = envImpl.getMemoryBudget();
        final OffHeapCache offHeapCache = envImpl.getOffHeapCache();

        final long cacheBudget =
            memoryBudget.getMaxMemory() + offHeapCache.getMaxMemory();

        final long maxBytes = (cacheBudget * cachePercent) / 100;

        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry data = loadLNs ? new DatabaseEntry() : null;

        try (final Cursor cursor = db.openCursor(null, null)) {
            while (cursor.get(key, data, Get.NEXT, null) != null) {

                final long usedBytes = memoryBudget.getCacheMemoryUsage() +
                    offHeapCache.getUsedMemory();

                if (usedBytes >= maxBytes) {
                    return true;
                }
            }
            return false;
        }
    }
}
