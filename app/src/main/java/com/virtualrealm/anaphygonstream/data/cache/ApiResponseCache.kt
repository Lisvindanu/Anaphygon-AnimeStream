package com.virtualrealm.anaphygonstream.data.cache

import com.virtualrealm.anaphygonstream.data.models.ApiResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * A simple in-memory cache for API responses to reduce network requests
 * and provide fallback data when the network is unavailable
 */
class ApiResponseCache {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry<*>>()

    // Default expiry time is 5 minutes
    private val DEFAULT_CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(5)

    // Shorter cache for volatile data like episode details (1 minute)
    private val SHORT_CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(1)

    // Longer cache for relatively stable data like anime details (1 hour)
    private val LONG_CACHE_DURATION_MS = TimeUnit.HOURS.toMillis(1)

    /**
     * Get cached response for the given key if it exists and is not expired
     */
    suspend fun <T> get(key: String): ApiResponse<T>? {
        return cacheMutex.withLock {
            val entry = cache[key] as? CacheEntry<T> ?: return null
            val cacheExpiry = when {
                key.startsWith("episode_") -> SHORT_CACHE_DURATION_MS
                key.startsWith("detail_") -> LONG_CACHE_DURATION_MS
                else -> DEFAULT_CACHE_DURATION_MS
            }

            if (System.currentTimeMillis() - entry.timestamp > cacheExpiry) {
                // Cache expired, remove it
                cache.remove(key)
                null
            } else {
                entry.data
            }
        }
    }

    /**
     * Store response in cache with the current timestamp
     */
    suspend fun <T> put(key: String, data: ApiResponse<T>) {
        cacheMutex.withLock {
            cache[key] = CacheEntry(data, System.currentTimeMillis())
        }
    }

    /**
     * Remove a specific entry from the cache
     */
    suspend fun remove(key: String) {
        cacheMutex.withLock {
            cache.remove(key)
        }
    }

    /**
     * Clear all cached entries
     */
    suspend fun clear() {
        cacheMutex.withLock {
            cache.clear()
        }
    }

    /**
     * Clear expired entries to free up memory
     */
    suspend fun clearExpired() {
        cacheMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val keysToRemove = mutableListOf<String>()

            cache.forEach { (key, entry) ->
                val cacheExpiry = when {
                    key.startsWith("episode_") -> SHORT_CACHE_DURATION_MS
                    key.startsWith("detail_") -> LONG_CACHE_DURATION_MS
                    else -> DEFAULT_CACHE_DURATION_MS
                }

                if (currentTime - entry.timestamp > cacheExpiry) {
                    keysToRemove.add(key)
                }
            }

            keysToRemove.forEach { cache.remove(it) }
        }
    }

    /**
     * Cache entry with data and timestamp
     */
    private data class CacheEntry<T>(
        val data: ApiResponse<T>,
        val timestamp: Long
    )
}