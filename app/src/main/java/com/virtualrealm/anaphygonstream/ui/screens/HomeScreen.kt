package com.virtualrealm.anaphygonstream.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.virtualrealm.anaphygonstream.data.models.AnimeItem
import com.virtualrealm.anaphygonstream.ui.viewmodels.AnimeListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToGenres: () -> Unit = {},
    viewModel: AnimeListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> viewModel.loadOngoingAnime()
            1 -> viewModel.loadCompleteAnime()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anime Stream") },
                actions = {
                    IconButton(onClick = { onNavigateToSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }

                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Browse by Genre") },
                                onClick = {
                                    onNavigateToGenres()
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    viewModel.refreshCurrentTab()
                                    showMenu = false
                                }
                            )
                        }
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ongoing") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Completed") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading anime...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (uiState.error != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Oops! Something went wrong",
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
                                when (selectedTab) {
                                    0 -> viewModel.loadOngoingAnime()
                                    1 -> viewModel.loadCompleteAnime()
                                }
                            }
                        ) {
                            Text("Try Again")
                        }
                    }
                } else if (uiState.animeList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No anime found",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                when (selectedTab) {
                                    0 -> viewModel.loadOngoingAnime()
                                    1 -> viewModel.loadCompleteAnime()
                                }
                            }
                        ) {
                            Text("Refresh")
                        }
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
                                                when (selectedTab) {
                                                    0 -> viewModel.loadOngoingAnime(prevPage)
                                                    1 -> viewModel.loadCompleteAnime(prevPage)
                                                }
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
                                                when (selectedTab) {
                                                    0 -> viewModel.loadOngoingAnime(nextPage)
                                                    1 -> viewModel.loadCompleteAnime(nextPage)
                                                }
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
fun AnimeItem(
    anime: AnimeItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                SubcomposeAsyncImage(
                    model = anime.poster,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Image\nNot Available",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )

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
                        text = anime.releaseDay,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = anime.latestReleaseDate,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}