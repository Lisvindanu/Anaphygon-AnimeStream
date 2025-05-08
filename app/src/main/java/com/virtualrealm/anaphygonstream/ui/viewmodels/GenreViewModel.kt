package com.virtualrealm.anaphygonstream.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualrealm.anaphygonstream.data.models.AnimeItem
import com.virtualrealm.anaphygonstream.data.models.Pagination
import com.virtualrealm.anaphygonstream.data.repository.AnimeMultiApiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GenreViewModel : ViewModel() {
    private val repository = AnimeMultiApiRepository()

    data class Genre(
        val id: String,
        val name: String
    )

    data class GenreUiState(
        val isLoading: Boolean = false,
        val genres: List<Genre> = emptyList(),
        val animeList: List<AnimeItem> = emptyList(),
        val pagination: Pagination? = null,
        val selectedGenre: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(GenreUiState())
    val uiState: StateFlow<GenreUiState> = _uiState.asStateFlow()

    fun loadGenres() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.getGenres()
                if (response.ok) {
                    val genres = response.data.map { genreMap ->
                        Genre(
                            id = genreMap["id"] ?: "",
                            name = genreMap["name"] ?: ""
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            genres = genres
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun loadAnimeByGenre(genreId: String, page: Int = 1) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                // Find the genre name from the list
                selectedGenre = it.genres.find { genre -> genre.id == genreId }?.name
                    ?: it.selectedGenre
            )
        }

        viewModelScope.launch {
            try {
                val response = repository.getAnimeByGenre(genreId, page)
                if (response.ok) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            animeList = response.data.animeList,
                            pagination = response.pagination
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }
}