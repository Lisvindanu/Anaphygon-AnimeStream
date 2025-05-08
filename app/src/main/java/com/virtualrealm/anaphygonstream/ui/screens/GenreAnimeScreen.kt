package com.virtualrealm.anaphygonstream.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.virtualrealm.anaphygonstream.ui.viewmodels.GenreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreAnimeScreen(
    genreId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: GenreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(genreId) {
        viewModel.loadAnimeByGenre(genreId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Genre: ${uiState.selectedGenre ?: "Loading..."}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    Text(
                        text = "Failed to load anime",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uiState.error ?: "Unknown error occurred",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { viewModel.loadAnimeByGenre(genreId) }) {
                        Text("Try Again")
                    }
                }
            } else if (uiState.animeList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No anime found for this genre",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.animeList) { anime ->
                            AnimeItem(
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
                                            viewModel.loadAnimeByGenre(genreId, prevPage)
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
                                            viewModel.loadAnimeByGenre(genreId, nextPage)
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