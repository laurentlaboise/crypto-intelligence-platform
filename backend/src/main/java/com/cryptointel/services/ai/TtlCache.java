package com.cryptointel.services.ai;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class TtlCache<V> {

    private final ConcurrentHashMap<String, CacheEntry<V>> map = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(String key) {
        CacheEntry<V> entry = map.get(key);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expiresAt)) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(String key, V value) {
        map.put(key, new CacheEntry<>(value, Instant.now().plusMillis(ttlMillis)));
    }

    public void evict(String key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    private static class CacheEntry<V> {
        final V value;
        final Instant expiresAt;

        CacheEntry(V value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
