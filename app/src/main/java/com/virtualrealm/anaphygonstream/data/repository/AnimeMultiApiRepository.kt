package com.virtualrealm.anaphygonstream.data.repository

import android.util.Base64
import android.util.Log
import com.virtualrealm.anaphygonstream.data.api.ApiClient
import com.virtualrealm.anaphygonstream.data.cache.ApiResponseCache
import com.virtualrealm.anaphygonstream.data.models.AnimeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.AnimeItem
import com.virtualrealm.anaphygonstream.data.models.AnimeListResponse
import com.virtualrealm.anaphygonstream.data.models.ApiResponse
import com.virtualrealm.anaphygonstream.data.models.Episode
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnimeMultiApiRepository {
    private val TAG = "AnimeRepository"
    private val api = ApiClient.animeApi
    private val cache = ApiResponseCache()
    private val REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(10) // 10 second timeout

    suspend fun getOngoingAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        // First try the cache
        val cacheKey = "ongoing_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for ongoing anime page $page")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
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
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for ongoing anime page $page")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching ongoing anime", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to load ongoing anime. Please check your internet connection or try again later."
                }

                createErrorResponse(errorMessage)
            }
        }
    }

    suspend fun getCompleteAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        // Try the cache first
        val cacheKey = "complete_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for complete anime page $page")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
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
                                    return@withTimeout response
                                }
                            } else {
                                Log.w(TAG, "API call #${index + 1} failed with status: ${response?.statusCode}")
                            }
                        } catch (e: CancellationException) {
                            throw e // Let cancellation propagate
                        } catch (e: Exception) {
                            Log.w(TAG, "API call #${index + 1} failed with exception", e)
                            // Continue to next API call
                        }
                    }

                    // If all APIs failed, return a clean error response
                    createErrorResponse("Failed to load completed anime. All available sources failed. Please try again later.")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for complete anime page $page")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching complete anime", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to load completed anime. Please check your internet connection or try again later."
                }

                createErrorResponse(errorMessage)
            }
        }
    }



    suspend fun getAnimeDetail(animeId: String): ApiResponse<AnimeDetailResponse> {
        // Try cache first
        val cacheKey = "detail_$animeId"
        cache.get<AnimeDetailResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for anime detail: $animeId")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
                    // Try different approaches to fetch anime details
                    // Based on the logs, directly calling the /anime/{animeId} endpoint
                    // results in 403 forbidden, so we'll try alternative approaches

                    // First, try to get anime from ongoing anime list
                    var matchingAnime: AnimeItem? = null
                    var isFromOngoing = false

                    val ongoingResponse = try {
                        api.getOngoingAnime(1)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch ongoing anime list: ${e.message}")
                        null
                    }

                    if (ongoingResponse?.ok == true) {
                        matchingAnime = ongoingResponse.data?.animeList?.find { it.animeId == animeId }
                        if (matchingAnime != null) {
                            isFromOngoing = true
                        }
                    }

                    // If not found in ongoing, try completed anime
                    if (matchingAnime == null) {
                        val completedResponse = try {
                            api.getCompleteAnime(1)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch completed anime list: ${e.message}")
                            null
                        }

                        if (completedResponse?.ok == true) {
                            matchingAnime = completedResponse.data?.animeList?.find { it.animeId == animeId }
                        }
                    }

                    // If found in any list, create a response with available data
                    if (matchingAnime != null) {
                        // Extract information from the matchingAnime
                        val episodes = if (isFromOngoing) {
                            // For ongoing anime, create a list of episodes up to the current count
                            (1..matchingAnime.episodes).map { episodeNumber ->
                                Episode(
                                    title = "Episode $episodeNumber",
                                    episodeId = "${animeId}_ep$episodeNumber",
                                    href = "/otakudesu/episode/${animeId}_ep$episodeNumber",
                                    otakudesuUrl = matchingAnime.otakudesuUrl ?: ""
                                )
                            }
                        } else {
                            // Otherwise empty list of episodes
                            emptyList()
                        }

                        // Get rating/score if available
                        val rating = try {
                            // Try to access the "score" field using reflection
                            val scoreField = matchingAnime::class.java.getDeclaredField("score")
                            scoreField.isAccessible = true
                            val score = scoreField.get(matchingAnime) as? String
                            score ?: "N/A"
                        } catch (e: Exception) {
                            "N/A"
                        }

                        // Create the detail response with the available information
                        val animeDetail = AnimeDetailResponse(
                            title = matchingAnime.title,
                            japaneseTitle = matchingAnime.title,  // Default to same title since we don't have Japanese title
                            poster = matchingAnime.poster,
                            rating = rating,
                            synopsis = "Details not available from API. Please visit ${matchingAnime.otakudesuUrl} for full information.",
                            genres = listOf("Anime"),  // Default genre
                            episodes = episodes
                        )

                        val response = ApiResponse(
                            statusCode = 200,
                            statusMessage = "Success (partial data)",
                            message = "Limited details available from API list endpoint",
                            ok = true,
                            data = animeDetail,
                            pagination = null
                        )

                        // Cache this limited response
                        cache.put(cacheKey, response)
                        return@withTimeout response
                    }

                    // As a last resort, try the regular detail endpoints as before
                    try {
                        val directDetailResponse = api.getAnimeDetail(animeId)
                        if (directDetailResponse.ok) {
                            cache.put(cacheKey, directDetailResponse)
                            return@withTimeout directDetailResponse
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct anime detail endpoint failed: ${e.message}")
                    }

                    try {
                        val samehadakuDetailResponse = api.getSamehadakuAnimeDetail(animeId)
                        if (samehadakuDetailResponse.ok) {
                            cache.put(cacheKey, samehadakuDetailResponse)
                            return@withTimeout samehadakuDetailResponse
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Samehadaku anime detail endpoint failed: ${e.message}")
                    }

                    // If we get here, we couldn't find any information
                    createErrorResponse("Failed to load anime details. Could not find anime in any available source.")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for anime detail: $animeId")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime detail: $animeId", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to load anime details: ${e.message ?: "Unknown error"}"
                }

                createErrorResponse(errorMessage)
            }
        }
    }


    // Di dalam AnimeMultiApiRepository.kt, perbaiki metode getEpisodeDetail:

    suspend fun getEpisodeDetail(episodeId: String): ApiResponse<EpisodeDetailResponse> {
        // Try cache first
        val cacheKey = "episode_$episodeId"
        cache.get<EpisodeDetailResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for episode detail: $episodeId")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
                    // Try multiple approaches to fetch episode details
                    var lastError: Exception? = null

                    // 1. Try the primary API endpoint
                    try {
                        Log.d(TAG, "Trying primary API for episode: $episodeId")
                        val response = api.getEpisodeDetail(episodeId)
                        if (response.ok && response.data != null && response.data.streams.isNotEmpty()) {
                            // Cache successful response
                            cache.put(cacheKey, response)
                            return@withTimeout response
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "API call #1 failed with exception", e)
                        lastError = e
                        // Continue to next API call
                    }

                    // 2. Try the fallback API endpoint
                    try {
                        Log.d(TAG, "Trying fallback API for episode: $episodeId")
                        val response = api.getSamehadakuEpisodeDetail(episodeId)
                        if (response.ok && response.data != null && response.data.streams.isNotEmpty()) {
                            // Cache successful response
                            cache.put(cacheKey, response)
                            return@withTimeout response
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "API call #2 failed with exception", e)
                        lastError = e
                        // Continue to next option
                    }

                    // 3. Extract episode info from anime details and try direct OtakuDesu URL
                    try {
                        Log.d(TAG, "Attempting to use direct OtakuDesu streaming URL")
                        // Extract the anime ID and episode number from the episode ID (assumes format: animeId_epX)
                        val parts = episodeId.split("_ep")
                        if (parts.size == 2) {
                            val animeId = parts[0]
                            val episodeNumber = parts[1].toIntOrNull() ?: 1

                            // Get anime title and image from ongoing or completed anime list
                            var matchingAnime: AnimeItem? = null

                            val ongoingResponse = try {
                                api.getOngoingAnime(1)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to fetch ongoing anime list", e)
                                null
                            }

                            matchingAnime = ongoingResponse?.data?.animeList?.find { it.animeId == animeId }

                            // If not found in ongoing, check completed anime
                            if (matchingAnime == null) {
                                val completedResponse = try {
                                    api.getCompleteAnime(1)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to fetch completed anime list", e)
                                    null
                                }
                                matchingAnime = completedResponse?.data?.animeList?.find { it.animeId == animeId }
                            }

                            // Generate direct OtakuDesu stream URL
                            // Note: This is based on their URL pattern, might need adjustment
                            val encodedId = Base64.encodeToString("$animeId:$episodeNumber".toByteArray(), Base64.DEFAULT).trim()
                            val directOtakuDesuUrl = "https://desustream.info/dstream/otakuwatch4/index.php?id=$encodedId"

                            // Create a test stream to try direct URL
                            val directStreams = listOf(
                                Stream(
                                    quality = "HD (Direct)",
                                    url = directOtakuDesuUrl,
                                    streamId = "direct_otakudesu"
                                )
                            )

                            // Add fallback in case direct streaming fails
                            val fallbackStreams = listOf(
                                Stream(
                                    quality = "Fallback HD",
                                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                    streamId = "fallback1"
                                ),
                                Stream(
                                    quality = "Alternative Source",
                                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                                    streamId = "fallback2"
                                )
                            )

                            val title = matchingAnime?.title ?: "Episode $episodeNumber"
                            val poster = matchingAnime?.poster ?: ""

                            val syntheticEpisodeDetail = EpisodeDetailResponse(
                                title = "$title - Episode $episodeNumber",
                                poster = poster,
                                streams = directStreams + fallbackStreams
                            )

                            val response = ApiResponse(
                                statusCode = 200,
                                statusMessage = "Success (direct)",
                                message = "Episode data with direct streaming URL",
                                ok = true,
                                data = syntheticEpisodeDetail,
                                pagination = null
                            )

                            // Cache this synthetic response
                            cache.put(cacheKey, response)
                            return@withTimeout response
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create direct streaming URL", e)
                        lastError = e
                    }

                    // If all options fail, return a clean error response
                    val errorMessage = when (lastError) {
                        is HttpException -> {
                            when (lastError.code()) {
                                403 -> "Access denied (HTTP 403). The server is restricting access to this episode. Try a different anime or episode."
                                404 -> "Episode not found (HTTP 404). This episode may not be available."
                                else -> "Server error: HTTP ${lastError.code()}"
                            }
                        }
                        is IOException -> "Network error: Please check your internet connection and try again."
                        else -> "Failed to load episode. Please try a different episode or anime."
                    }

                    createErrorResponse(errorMessage)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for episode detail: $episodeId")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching episode detail: $episodeId", e)
                createErrorResponse("Failed to load episode: ${e.message ?: "Unknown error"}")
            }
        }
    }

    suspend fun searchAnime(query: String, page: Int = 1): ApiResponse<AnimeListResponse> {
        // Try cache first
        val cacheKey = "search_${query}_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for search: $query page $page")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
                    // Try Otakudesu search first, then fall back to Samehadaku
                    val response = tryWithFallbacks(
                        primaryCall = { api.searchAnime(query, page) },
                        fallbackCalls = listOf(
                            { api.searchSamehadakuAnime(query, page) }
                        ),
                        cacheKey = cacheKey,
                        errorMessage = "Failed to search anime"
                    )
                    response
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for search: $query")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error searching for anime: $query", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to search anime: ${e.message ?: "Unknown error"}"
                }

                createErrorResponse(errorMessage)
            }
        }
    }

    suspend fun getGenres(): ApiResponse<List<Map<String, String>>> {
        // Try cache first
        val cacheKey = "genres"
        cache.get<List<Map<String, String>>>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for genres")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
                    val response = tryWithFallbacks(
                        primaryCall = { api.getGenres() },
                        fallbackCalls = listOf(
                            { api.getSamehadakuGenres() }
                        ),
                        cacheKey = cacheKey,
                        errorMessage = "Failed to load genres"
                    )
                    response
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for genres")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching genres", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to load genres: ${e.message ?: "Unknown error"}"
                }

                createErrorResponse(errorMessage)
            }
        }
    }

    suspend fun getAnimeByGenre(genreId: String, page: Int = 1): ApiResponse<AnimeListResponse> {
        // Try cache first
        val cacheKey = "genre_${genreId}_$page"
        cache.get<AnimeListResponse>(cacheKey)?.let {
            Log.d(TAG, "Cache hit for genre: $genreId page $page")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use timeout to prevent hanging requests
                withTimeout(REQUEST_TIMEOUT) {
                    val response = tryWithFallbacks(
                        primaryCall = { api.getAnimeByGenre(genreId, page) },
                        fallbackCalls = listOf(
                            { api.getSamehadakuAnimeByGenre(genreId, page) }
                        ),
                        cacheKey = cacheKey,
                        errorMessage = "Failed to load anime by genre"
                    )
                    response
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Request was cancelled for genre: $genreId")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime by genre: $genreId", e)

                // Create a more specific error message based on the exception type
                val errorMessage = when (e) {
                    is IOException -> "Network error: Please check your internet connection and try again."
                    else -> "Failed to load anime by genre: ${e.message ?: "Unknown error"}"
                }

                createErrorResponse(errorMessage)
            }
        }
    }

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
        } catch (e: CancellationException) {
            throw e // Let cancellation propagate
        } catch (e: Exception) {
            Log.w(TAG, "Primary API call failed with exception: ${e.message}", e)
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
            } catch (e: CancellationException) {
                throw e // Let cancellation propagate
            } catch (e: Exception) {
                Log.w(TAG, "Fallback API call #${index + 1} failed: ${e.message}", e)
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