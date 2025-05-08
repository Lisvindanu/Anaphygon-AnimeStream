package com.virtualrealm.anaphygonstream.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.virtualrealm.anaphygonstream.data.models.AnimeItem
import com.virtualrealm.anaphygonstream.ui.viewmodels.AnimeListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: AnimeListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Remember whether we've already performed a search
    var hasSearched by remember { mutableStateOf(false) }
    var showEmptySearchError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            // Request focus on the search field when the screen is shown
            delay(300) // Brief delay to ensure the UI is ready
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus request errors
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Anime") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search anime...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            showEmptySearchError = false
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (uiState.searchQuery.isBlank()) {
                            showEmptySearchError = true
                        } else {
                            keyboardController?.hide()
                            viewModel.searchAnime()
                            hasSearched = true
                            showEmptySearchError = false
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                isError = showEmptySearchError,
                supportingText = if (showEmptySearchError) {
                    { Text("Please enter a search term") }
                } else null
            )

            // Search button
            Button(
                onClick = {
                    if (uiState.searchQuery.isBlank()) {
                        showEmptySearchError = true
                    } else {
                        keyboardController?.hide()
                        coroutineScope.launch {
                            try {
                                viewModel.searchAnime()
                                hasSearched = true
                                showEmptySearchError = false
                            } catch (e: Exception) {
                                // Handle error gracefully
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                enabled = uiState.searchQuery.isNotBlank() && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.error != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Search Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = uiState.error ?: "Unknown error occurred",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (uiState.searchQuery.isNotBlank()) {
                                    viewModel.searchAnime()
                                }
                            },
                            enabled = uiState.searchQuery.isNotBlank()
                        ) {
                            Text("Try Again")
                        }
                    }
                } else if (hasSearched && uiState.animeList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Try a different search term or check your spelling",
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (!hasSearched) {
                    // Initial state - show some helpful text
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Search for your favorite anime",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Enter a title above to begin searching",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(uiState.animeList) { anime ->
                                SearchAnimeItem(
                                    anime = anime,
                                    onClick = { onNavigateToDetail(anime.animeId) }
                                )
                            }
                        }

                        // Pagination controls
                        uiState.pagination?.let { pagination ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pagination.hasPrevPage) {
                                    Button(
                                        onClick = {
                                            pagination.prevPage?.let { prevPage ->
                                                viewModel.searchAnime(prevPage)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text("Previous")
                                    }
                                }

                                Text(
                                    text = "Page ${pagination.currentPage} of ${pagination.totalPages}",
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                if (pagination.hasNextPage) {
                                    Button(
                                        onClick = {
                                            pagination.nextPage?.let { nextPage ->
                                                viewModel.searchAnime(nextPage)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text("Next")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAnimeItem(
    anime: AnimeItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Use SubcomposeAsyncImage instead of AsyncImage to properly handle loading and error states
                    SubcomposeAsyncImage(
                        model = anime.poster,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        error = {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Image unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    )
                }

                // Episode badge in the top-right corner
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Eps: ${anime.episodes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // Add null safety for releaseDay
                        text = anime.releaseDay ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        // Add null safety for latestReleaseDate
                        text = anime.latestReleaseDate ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}