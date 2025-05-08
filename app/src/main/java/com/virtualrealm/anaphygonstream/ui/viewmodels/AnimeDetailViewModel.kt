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
                if (response.ok) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            animeDetail = response.data,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response.message
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading anime detail: $animeId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun retryLoadAnimeDetail(animeId: String) {
        loadAnimeDetail(animeId)
    }
}