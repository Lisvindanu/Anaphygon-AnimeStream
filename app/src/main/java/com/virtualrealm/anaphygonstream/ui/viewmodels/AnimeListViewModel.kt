package com.virtualrealm.anaphygonstream.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualrealm.anaphygonstream.data.models.AnimeItem
import com.virtualrealm.anaphygonstream.data.models.Pagination
import com.virtualrealm.anaphygonstream.data.repository.AnimeMultiApiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnimeListViewModel : ViewModel() {
    private val TAG = "AnimeListViewModel"
    private val repository = AnimeMultiApiRepository()
    private var searchJob: Job? = null
    private var currentLoadJob: Job? = null

    data class AnimeListUiState(
        val isLoading: Boolean = false,
        val animeList: List<AnimeItem> = emptyList(),
        val pagination: Pagination? = null,
        val error: String? = null,
        val searchQuery: String = "",
        val currentTab: Int = 0, // 0 for ongoing, 1 for completed
        val lastSearchQuery: String = "", // Track the last executed search
        val isRefreshing: Boolean = false
    )

    private val _uiState = MutableStateFlow(AnimeListUiState())
    val uiState: StateFlow<AnimeListUiState> = _uiState.asStateFlow()

    fun loadOngoingAnime(page: Int = 1) {
        // Cancel any ongoing job to prevent race conditions
        currentLoadJob?.cancel()

        _uiState.update { it.copy(isLoading = true, error = null, currentTab = 0) }
        currentLoadJob = viewModelScope.launch {
            try {
                val response = repository.getOngoingAnime(page)
                if (response.ok) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            animeList = response.data?.animeList ?: emptyList(),
                            pagination = response.pagination,
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
                Log.e(TAG, "Error loading ongoing anime", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load ongoing anime: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun loadCompleteAnime(page: Int = 1) {
        // Cancel any ongoing job
        currentLoadJob?.cancel()

        _uiState.update { it.copy(isLoading = true, error = null, currentTab = 1) }
        currentLoadJob = viewModelScope.launch {
            try {
                val response = repository.getCompleteAnime(page)
                if (response.ok) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            animeList = response.data?.animeList ?: emptyList(),
                            pagination = response.pagination,
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
                Log.e(TAG, "Error loading completed anime", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load completed anime: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        // Cancel any previous search job
        searchJob?.cancel()
        searchJob = null

        if (query.length >= 3) {
            // Debounce search for better UX
            searchJob = viewModelScope.launch {
                try {
                    delay(500) // Debounce 500ms
                    searchAnime()
                } catch (e: CancellationException) {
                    // Coroutine was cancelled, ignore
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search debounce", e)
                }
            }
        } else if (query.isEmpty()) {
            // Clear results when query is cleared
            _uiState.update {
                it.copy(
                    animeList = emptyList(),
                    pagination = null,
                    error = null,
                    lastSearchQuery = ""
                )
            }
        }
    }

    fun searchAnime(page: Int = 1) {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    animeList = emptyList(),
                    pagination = null,
                    error = "Please enter a search term",
                    lastSearchQuery = ""
                )
            }
            return
        }

        // Cancel any ongoing search
        currentLoadJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                lastSearchQuery = query
            )
        }

        currentLoadJob = viewModelScope.launch {
            try {
                val response = repository.searchAnime(query, page)

                _uiState.update {
                    if (response.ok) {
                        it.copy(
                            isLoading = false,
                            animeList = response.data?.animeList ?: emptyList(),
                            pagination = response.pagination,
                            error = if (response.data?.animeList?.isEmpty() == true)
                                "No results found for '$query'"
                            else null
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            error = response.message
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error searching anime: $query", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun refreshCurrentTab() {
        when (_uiState.value.currentTab) {
            0 -> loadOngoingAnime()
            1 -> loadCompleteAnime()
        }
    }

    fun retryLastOperation() {
        val currentState = _uiState.value

        // If there was a search query, retry the search
        if (currentState.lastSearchQuery.isNotBlank()) {
            _uiState.update { it.copy(searchQuery = currentState.lastSearchQuery) }
            searchAnime()
        } else {
            // Otherwise retry the current tab
            refreshCurrentTab()
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentLoadJob?.cancel()
        searchJob?.cancel()
    }
}