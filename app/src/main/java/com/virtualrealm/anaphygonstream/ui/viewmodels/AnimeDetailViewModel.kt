package com.virtualrealm.anaphygonstream.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualrealm.anaphygonstream.data.models.AnimeDetailResponse
import com.virtualrealm.anaphygonstream.data.repository.AnimeMultiApiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.io.IOException

class AnimeDetailViewModel : ViewModel() {
    private val TAG = "AnimeDetailViewModel"
    private val repository = AnimeMultiApiRepository()

    data class AnimeDetailUiState(
        val isLoading: Boolean = false,
        val animeDetail: AnimeDetailResponse? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(AnimeDetailUiState())
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    fun loadAnimeDetail(animeId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val response = repository.getAnimeDetail(animeId)

                if (response.ok && response.data != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            animeDetail = response.data,
                            error = null
                        )
                    }
                } else {
                    val errorMsg = "Failed to load anime details: ${response.message ?: "Unknown error"}"
                    Log.e(TAG, errorMsg)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Let cancellations propagate normally
                throw e
            } catch (e: UnknownHostException) {
                // Network connectivity issues
                Log.e(TAG, "Network error loading anime detail: $animeId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Network unavailable. Please check your internet connection."
                    )
                }
            } catch (e: IOException) {
                // Other IO issues (timeouts, etc.)
                Log.e(TAG, "IO error loading anime detail: $animeId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Connection error: ${e.message ?: "Unknown network error"}"
                    )
                }
            } catch (e: Exception) {
                // All other exceptions
                Log.e(TAG, "Error loading anime detail: $animeId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun retryLoadAnimeDetail(animeId: String) {
        loadAnimeDetail(animeId)
    }

    override fun onCleared() {
        super.onCleared()
        // Any cleanup code if needed
    }
}