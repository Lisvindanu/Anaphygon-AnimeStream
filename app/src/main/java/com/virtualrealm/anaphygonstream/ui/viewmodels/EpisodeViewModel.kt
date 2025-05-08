package com.virtualrealm.anaphygonstream.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.Stream
import com.virtualrealm.anaphygonstream.data.repository.AnimeMultiApiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                                error = "No streaming sources available for this episode."
                            )
                        }
                        return@launch
                    }

                    // Sort streams by quality (assuming higher quality has higher resolution indicators)
                    val sortedStreams = streams.sortedByDescending {
                        extractResolution(it.quality)
                    }

                    // Get the best quality stream that's likely to work
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
                } else {
                    Log.e(TAG, "API error loading episode: ${response.statusCode} - ${response.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response.message ?: "Failed to load episode"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading episode detail: $episodeId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load episode: ${e.localizedMessage ?: "Unknown error"}"
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

    // Helper function to determine the best stream to start with
    private fun findBestStream(streams: List<Stream>): Stream {
        if (streams.isEmpty()) {
            throw IllegalArgumentException("Stream list cannot be empty")
        }

        // Categorize streams by likely reliability
        val mediumQualityStreams = streams.filter {
            val resolution = extractResolution(it.quality)
            resolution in 360..720 && !it.quality.contains("HD", ignoreCase = true)
        }

        // Prefer medium quality (most reliable)
        if (mediumQualityStreams.isNotEmpty()) {
            return mediumQualityStreams.first()
        }

        // Fallback to highest quality that's not labeled as "HD" (which may be less reliable)
        val nonHDStreams = streams.filter { !it.quality.contains("HD", ignoreCase = true) }
        if (nonHDStreams.isNotEmpty()) {
            return nonHDStreams.first()
        }

        // If all else fails, use the first stream
        return streams.first()
    }

    // Helper function to extract resolution numbers from quality string
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


}