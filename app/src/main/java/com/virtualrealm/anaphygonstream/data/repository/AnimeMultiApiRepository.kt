package com.virtualrealm.anaphygonstream.data.repository

import android.util.Log
import com.virtualrealm.anaphygonstream.data.api.ApiClient
import com.virtualrealm.anaphygonstream.data.cache.ApiResponseCache
import com.virtualrealm.anaphygonstream.data.models.AnimeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.AnimeListResponse
import com.virtualrealm.anaphygonstream.data.models.ApiResponse
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AnimeMultiApiRepository {
    private val TAG = "AnimeRepository"
    private val api = ApiClient.animeApi
    private val cache = ApiResponseCache()

    suspend fun getOngoingAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        // First try the cache
        val cacheKey = "ongoing_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                // Try both API sources with proper fallback
                val response = tryWithFallbacks(
                    // Try Otakudesu first
                    primaryCall = { api.getOngoingAnime(page) },
                    // Then try Samehadaku with different ordering options
                    fallbackCalls = listOf(
                        { api.getHomeAnime() },
                        { api.getSamehadakuOngoing(page, "update") },
                        { api.getSamehadakuOngoing(page, "latest") },
                        { api.getSamehadakuRecent(page) }
                    ),
                    cacheKey = cacheKey,
                    errorMessage = "Failed to load ongoing anime"
                )
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching ongoing anime", e)
                createErrorResponse("Failed to load ongoing anime. Please check your internet connection or try again later.")
            }
        }
    }

    suspend fun getCompleteAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        // Try the cache first
        val cacheKey = "complete_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                // Define the call types - important: no suspend functions called here
                val apiCalls = listOf(
                    "getCompleteAnime",
                    "getAnimeList",
                    "getSamehadakuCompleted:update",
                    "getSamehadakuCompleted:latest"
                )

                // Try each endpoint separately to isolate failures
                for ((index, callType) in apiCalls.withIndex()) {
                    try {
                        val response = when (callType) {
                            "getCompleteAnime" -> api.getCompleteAnime(page)
                            "getAnimeList" -> api.getAnimeList(page)
                            "getSamehadakuCompleted:update" -> api.getSamehadakuCompleted(page, "update")
                            "getSamehadakuCompleted:latest" -> api.getSamehadakuCompleted(page, "latest")
                            else -> null
                        }

                        // Check if we got a valid response
                        if (response != null && response.ok && isValidResponse(response)) {
                            // Cache successful response
                            if (response.data?.animeList?.isNotEmpty() == true) {
                                cache.put(cacheKey, response)
                                return@withContext response
                            }
                        } else {
                            Log.w(TAG, "API call #${index + 1} failed with status: ${response?.statusCode}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "API call #${index + 1} failed with exception", e)
                        // Continue to next API call
                    }
                }

                // If all APIs failed, return a clean error response
                createErrorResponse("Failed to load completed anime. All available sources failed. Please try again later.")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching complete anime", e)
                createErrorResponse("Failed to load completed anime. Please check your internet connection or try again later.")
            }
        }
    }

    suspend fun getAnimeDetail(animeId: String): ApiResponse<AnimeDetailResponse> {
        // Try cache first
        val cacheKey = "detail_$animeId"
        cache.get<AnimeDetailResponse>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                // Try Otakudesu first, then Samehadaku
                val response = tryWithFallbacks(
                    primaryCall = { api.getAnimeDetail(animeId) },
                    fallbackCalls = listOf(
                        { api.getSamehadakuAnimeDetail(animeId) }
                    ),
                    cacheKey = cacheKey,
                    errorMessage = "Failed to load anime details"
                )
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime detail: $animeId", e)
                createErrorResponse("Failed to load anime details. Please check your internet connection or try again later.")
            }
        }
    }

    suspend fun getEpisodeDetail(episodeId: String): ApiResponse<EpisodeDetailResponse> {
        // Try cache first
        val cacheKey = "episode_$episodeId"
        cache.get<EpisodeDetailResponse>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                // Try Otakudesu first, then Samehadaku
                val response = tryWithFallbacks(
                    primaryCall = { api.getEpisodeDetail(episodeId) },
                    fallbackCalls = listOf(
                        { api.getSamehadakuEpisodeDetail(episodeId) }
                    ),
                    cacheKey = cacheKey,
                    errorMessage = "Failed to load episode"
                )
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching episode detail: $episodeId", e)
                createErrorResponse("Failed to load episode. Please check your internet connection or try again later.")
            }
        }
    }

    // Other methods unchanged...

    // Generic method to try multiple API calls with fallbacks
    private suspend fun <T> tryWithFallbacks(
        primaryCall: suspend () -> ApiResponse<T>,
        fallbackCalls: List<suspend () -> ApiResponse<T>>,
        cacheKey: String?,
        errorMessage: String
    ): ApiResponse<T> {
        // Try primary API call
        try {
            val response = primaryCall()
            if (response.ok && isValidResponse(response)) {
                if (cacheKey != null) {
                    cache.put(cacheKey, response)
                }
                return response
            } else {
                Log.w(TAG, "Primary API call failed: ${response.statusCode} - ${response.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Primary API call failed with exception", e)
        }

        // Try each fallback
        for ((index, fallbackCall) in fallbackCalls.withIndex()) {
            try {
                val response = fallbackCall()
                if (response.ok && isValidResponse(response)) {
                    // Mark as coming from fallback
                    val adaptedResponse = ApiResponse(
                        statusCode = 200,
                        statusMessage = "Success (fallback)",
                        message = "Data retrieved from alternative source",
                        ok = true,
                        data = response.data,
                        pagination = response.pagination
                    )

                    if (cacheKey != null) {
                        cache.put(cacheKey, adaptedResponse)
                    }
                    return adaptedResponse
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback API call #${index + 1} failed", e)
            }
        }

        return createErrorResponse("$errorMessage. All available sources failed.")
    }

    // Helper to check if a response has valid data
    private fun <T> isValidResponse(response: ApiResponse<T>): Boolean {
        if (response.data == null) return false

        // For AnimeListResponse, check if the list is not empty
        if (response.data is AnimeListResponse) {
            return (response.data as AnimeListResponse).animeList.isNotEmpty()
        }

        return true
    }

    // Helper to create a consistent error response
    private fun <T> createErrorResponse(errorMessage: String): ApiResponse<T> {
        return ApiResponse(
            statusCode = 500,
            statusMessage = "Error",
            message = errorMessage,
            ok = false,
            data = null as T,
            pagination = null
        )
    }
}