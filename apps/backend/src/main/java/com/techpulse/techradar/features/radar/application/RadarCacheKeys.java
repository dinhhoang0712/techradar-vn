package com.techpulse.techradar.features.radar.application;

/**
 * Central registry of the Redis wildcard pattern that covers every radar analytics
 * cache entry. Derived (at class-load time) from the individual use cases' own
 * {@code CACHE_KEY_PREFIX} constants so that cache-eviction call sites (admin rebuild,
 * scheduled ETL) can never silently drift out of sync if a use case ever renames its
 * key prefix.
 */
public final class RadarCacheKeys {

    /** Wildcard pattern that matches every radar analytics cache entry currently in use. */
    public static final String EVICT_ALL_PATTERN = commonPrefix(
            GetTopTechnologiesUseCase.CACHE_KEY_PREFIX,
            SearchTrendUseCase.CACHE_KEY_PREFIX) + "*";

    private RadarCacheKeys() {
    }

    private static String commonPrefix(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }
}
