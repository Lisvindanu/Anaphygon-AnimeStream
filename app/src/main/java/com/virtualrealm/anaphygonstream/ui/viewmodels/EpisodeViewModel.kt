package com.virtualrealm.anaphygonstream.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.Stream
import com.virtualrealm.anaphygonstream.data.repository.AnimeMultiApiRepository
import com.virtualrealm.anaphygonstream.utils.VideoExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.IOException

class EpisodeViewModel : ViewModel() {
    private val TAG = "EpisodeViewModel"
    private val repository = AnimeMultiApiRepository()

    data class EpisodeUiState(
        val isLoading: Boolean = false,
        val episodeDetail: EpisodeDetailResponse? = null,
        val selectedStreamUrl: String? = null,
        val selectedStreamQuality: String? = null,
        val error: String? = null,
        val availableStreamQualities: List<Stream> = emptyList()
    )

    private val _uiState = MutableStateFlow(EpisodeUiState())
    val uiState: StateFlow<EpisodeUiState> = _uiState.asStateFlow()


    fun loadEpisodeDetail(episodeId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.getEpisodeDetail(episodeId)
                if (response.ok && response.data != null) {
                    val streams = response.data.streams

                    if (streams.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                episodeDetail = response.data,
                                error = "No streaming sources available for this episode. Try another episode."
                            )
                        }
                        return@launch
                    }

                    // Sort streams by quality (assuming higher quality has higher resolution indicators)
                    val sortedStreams = streams.sortedByDescending {
                        extractResolution(it.quality)
                    }

                    // Try to extract real video URL from all available streams
                    var directVideoUrl: String? = null
                    var selectedStream: Stream? = null

                    for (stream in sortedStreams) {
                        val extractedUrl = VideoExtractor.extractVideoUrl(stream.url)
                        if (!extractedUrl.isNullOrEmpty()) {
                            directVideoUrl = extractedUrl
                            selectedStream = stream
                            Log.d(TAG, "Successfully extracted video URL from ${stream.quality}: $extractedUrl")
                            break
                        }
                    }

                    if (directVideoUrl != null && selectedStream != null) {
                        // Create modified stream list with direct URL
                        val modifiedStreams = sortedStreams.map { originalStream ->
                            if (originalStream.url == selectedStream.url) {
                                // Replace the URL in the selected stream with the direct URL
                                Stream(
                                    quality = "${originalStream.quality} (Direct)",
                                    url = directVideoUrl,
                                    streamId = originalStream.streamId
                                )
                            } else {
                                // Keep original streams as-is
                                originalStream
                            }
                        }

                        // Update UI state with the direct video URL
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                episodeDetail = response.data.copy(streams = modifiedStreams),
                                selectedStreamUrl = directVideoUrl,
                                selectedStreamQuality = "${selectedStream.quality} (Direct)",
                                availableStreamQualities = modifiedStreams,
                                error = null
                            )
                        }
                    } else {
                        // No direct URL could be extracted, fall back to default behavior
                        val bestStream = findBestStream(sortedStreams)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                episodeDetail = response.data,
                                selectedStreamUrl = bestStream.url,
                                selectedStreamQuality = bestStream.quality,
                                availableStreamQualities = sortedStreams,
                                error = null
                            )
                        }
                    }
                } else {
                    // Better error handling for API errors
                    val errorMsg = when {
                        response.statusCode == 403 -> "Access denied: The API server rejected the request. Try again later."
                        response.statusCode == 404 -> "Episode not found: This episode may not be available yet."
                        else -> response.message ?: "Failed to load episode"
                    }

                    Log.e(TAG, "API error loading episode: ${response.statusCode} - $errorMsg")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading episode detail: $episodeId", e)

                // More user-friendly error message
                val errorMsg = when (e) {
                    is java.net.UnknownHostException -> "No internet connection. Please check your network and try again."
                    is java.net.SocketTimeoutException -> "Connection timed out. The server is taking too long to respond."
                    is IOException -> "Network error. Please check your connection and try again."
                    else -> "Failed to load episode: ${e.message ?: "Unknown error"}"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    fun retryLoadEpisodeDetail(episodeId: String) {
        loadEpisodeDetail(episodeId)
    }

    fun selectStreamQuality(url: String) {
        val currentStreams = _uiState.value.episodeDetail?.streams ?: return
        val selectedStream = currentStreams.find { it.url == url } ?: return

        _uiState.update {
            it.copy(
                selectedStreamUrl = url,
                selectedStreamQuality = selectedStream.quality
            )
        }
    }

    fun tryAlternativeStream() {
        val currentState = _uiState.value
        val currentUrl = currentState.selectedStreamUrl
        val streams = currentState.availableStreamQualities

        if (streams.isEmpty()) return

        // Find a different stream quality to try
        val currentIndex = streams.indexOfFirst { it.url == currentUrl }
        val nextIndex = if (currentIndex < 0 || currentIndex >= streams.size - 1) 0 else currentIndex + 1

        // Select the next stream
        val nextStream = streams[nextIndex]
        _uiState.update {
            it.copy(
                selectedStreamUrl = nextStream.url,
                selectedStreamQuality = nextStream.quality
            )
        }
    }

    private fun findBestStream(streams: List<Stream>): Stream {
        if (streams.isEmpty()) {
            throw IllegalArgumentException("Stream list cannot be empty")
        }

        // First, try to find streams that are from reliable sources
        val reliableDomains = listOf(
            "googleapis.com", // Google storage is reliable
            "cloudfront.net", // AWS CDN is reliable
            "akamaized.net"   // Akamai CDN is reliable
        )

        // Check if any streams are from reliable domains
        val reliableStreams = streams.filter { stream ->
            reliableDomains.any { domain -> stream.url.contains(domain, ignoreCase = true) }
        }

        if (reliableStreams.isNotEmpty()) {
            // Choose the highest quality reliable stream
            return reliableStreams.sortedByDescending { extractResolution(it.quality) }.first()
        }

        // If no reliable streams, categorize by quality
        // Prefer medium quality (most reliable) in the 360p-720p range
        val mediumQualityStreams = streams.filter {
            val resolution = extractResolution(it.quality)
            resolution in 360..720
        }

        // Prefer medium quality (most reliable)
        if (mediumQualityStreams.isNotEmpty()) {
            return mediumQualityStreams.first()
        }

        // Fallback to first stream in list
        return streams.first()
    }


    private fun extractResolution(quality: String): Int {
        // Extract numbers from strings like "720p", "1080p", "480p"
        val resolutionRegex = "(\\d+)p".toRegex()
        val match = resolutionRegex.find(quality)

        return match?.groupValues?.get(1)?.toIntOrNull() ?: when {
            quality.contains("HD", ignoreCase = true) -> 720
            quality.contains("SD", ignoreCase = true) -> 480
            quality.contains("360", ignoreCase = true) -> 360
            quality.contains("720", ignoreCase = true) -> 720
            quality.contains("1080", ignoreCase = true) -> 1080
            else -> 0 // Default lowest priority
        }
    }

    // In EpisodeViewModel.kt, add a function to refresh the stream URL
    fun refreshStreamUrl(episodeId: String) {
        viewModelScope.launch {
            try {
                val response = repository.getEpisodeDetail(episodeId)
                if (response.ok && response.data != null) {
                    val streamUrl = findBestStream(response.data.streams.sortedByDescending {
                        extractResolution(it.quality)
                    }).url

                    _uiState.update {
                        it.copy(selectedStreamUrl = streamUrl)
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Add this method to your EpisodeViewModel.kt:

    /**
     * Updates the selected stream URL and quality
     */
    fun updateStreamUrl(url: String, quality: String? = null) {
        val currentQuality = _uiState.value.selectedStreamQuality ?: "Unknown"
        _uiState.update {
            it.copy(
                selectedStreamUrl = url,
                selectedStreamQuality = quality ?: currentQuality
            )
        }
    }

}