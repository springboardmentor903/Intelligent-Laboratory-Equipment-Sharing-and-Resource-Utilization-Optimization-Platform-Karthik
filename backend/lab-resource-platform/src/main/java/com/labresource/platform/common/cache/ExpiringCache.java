package com.labresource.platform.common.cache;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache with configurable TTL (default 2 minutes).
 */
public class ExpiringCache<K, V> {

    private final long ttlMillis;
    private final ConcurrentHashMap<K, CacheEntry<V>> map = new ConcurrentHashMap<>();

    public ExpiringCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(K key) {
        CacheEntry<V> entry = map.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() <= entry.expiresAt()) {
                return entry.value();
            }
            map.remove(key, entry);
        }
        return null;
    }

    public void put(K key, V value) {
        if (key != null && value != null) {
            map.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
        }
    }

    public void invalidate(K key) {
        if (key != null) {
            map.remove(key);
        }
    }

    public void clear() {
        map.clear();
    }

    private record CacheEntry<T>(T value, long expiresAt) {}
}
