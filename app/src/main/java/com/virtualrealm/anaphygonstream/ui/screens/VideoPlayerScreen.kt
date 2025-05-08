package com.virtualrealm.anaphygonstream.ui.screens

import android.net.Uri
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.virtualrealm.anaphygonstream.ui.viewmodels.EpisodeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VideoPlayerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    episodeId: String,
    onNavigateBack: () -> Unit,
    viewModel: EpisodeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    // Player state
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var playerControlsVisible by remember { mutableStateOf(true) }
    var isPlayingFallbackVideo by remember { mutableStateOf(false) }
    val maxRetries = 3

    // Load episode details when screen first appears
    LaunchedEffect(episodeId) {
        viewModel.loadEpisodeDetail(episodeId)
    }

    // Handle stream URL changes and set up the player
    LaunchedEffect(uiState.selectedStreamUrl) {
        uiState.selectedStreamUrl?.let { url ->
            // Check if this is a fallback video
            isPlayingFallbackVideo = url.contains("gtv-videos-bucket") ||
                    url.contains("commondatastorage.googleapis.com")

            // Release existing player if any
            exoPlayer?.release()

            // Reset error state when trying a new URL
            playbackError = null
            isRetrying = false
            retryCount = 0

            Log.d(TAG, "Setting up player for URL: $url")

            try {
                // Create and configure custom HttpDataSource.Factory with appropriate headers
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setUserAgent("Mozilla/5.0 (Linux; Android 12; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .setDefaultRequestProperties(mapOf(
                        "Accept" to "*/*",
                        "Origin" to "https://otakudesu.cloud",
                        "Referer" to "https://otakudesu.cloud/",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Range" to "bytes=0-"
                    ))

                // Create a MediaSourceFactory that uses our custom HttpDataSource.Factory
                val mediaSourceFactory = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(httpDataSourceFactory)

                // Build the ExoPlayer with our custom MediaSourceFactory
                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build().apply {
                        // Use appropriate media item creation based on stream type
                        val mediaItem = if (url.contains("m3u8") || url.contains("mpd")) {
                            // Use HLS or DASH for adaptive streams
                            val mimeType = when {
                                url.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                                url.contains("mpd") -> MimeTypes.APPLICATION_MPD
                                else -> null
                            }
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMimeType(mimeType)
                                .build()
                        } else {
                            // Regular progressive stream
                            MediaItem.fromUri(Uri.parse(url))
                        }

                        setMediaItem(mediaItem)

                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                Log.e(TAG, "Playback error: ${error.errorCode} - ${error.message}", error)

                                val errorMessage = when (error.errorCode) {
                                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                                        "Network connection failed. Check your internet connection."
                                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                        "Connection timed out. The server is taking too long to respond."
                                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                        "Server error: Access denied (HTTP 403). Try a different quality."
                                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                                        "Video not found (HTTP 404). Try a different quality."
                                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                                        "The video file is corrupted or in an unsupported format."
                                    PlaybackException.ERROR_CODE_TIMEOUT ->
                                        "Operation timed out. Try again or select a different quality."
                                    else -> "Failed to play video: ${error.message ?: "Unknown error"}"
                                }

                                playbackError = errorMessage
                                isBuffering = false
                            }

                            override fun onPlaybackStateChanged(state: Int) {
                                when (state) {
                                    Player.STATE_BUFFERING -> {
                                        isBuffering = true
                                        Log.d(TAG, "Video buffering...")
                                    }
                                    Player.STATE_READY -> {
                                        isBuffering = false
                                        Log.d(TAG, "Video ready to play")
                                    }
                                    Player.STATE_ENDED -> {
                                        isBuffering = false
                                        Log.d(TAG, "Video playback ended")
                                    }
                                    Player.STATE_IDLE -> {
                                        isBuffering = false
                                        Log.d(TAG, "Player in idle state")
                                    }
                                }
                            }
                        })

                        playWhenReady = true
                        prepare()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up ExoPlayer", e)
                playbackError = "Failed to initialize player: ${e.message}"
            }
        }
    }

    // Auto-retry for playback errors
    LaunchedEffect(playbackError, isRetrying) {
        if (playbackError != null && !isRetrying && retryCount < maxRetries) {
            isRetrying = true
            retryCount++

            // Wait before retrying
            delay(2000)

            Log.d(TAG, "Auto-retrying playback (attempt $retryCount of $maxRetries)")

            // Try to play again with the same URL but with modified attributes
            uiState.selectedStreamUrl?.let { url ->
                try {
                    // Create a new media item with potentially different settings
                    val mediaItem = if (url.contains("m3u8") || url.contains("mpd")) {
                        // For HLS/DASH streams, try with explicit mime type
                        val mimeType = when {
                            url.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                            url.contains("mpd") -> MimeTypes.APPLICATION_MPD
                            else -> null
                        }
                        MediaItem.Builder()
                            .setUri(Uri.parse(url))
                            .setMimeType(mimeType)
                            .build()
                    } else {
                        // For regular streams, try with the raw URL
                        MediaItem.fromUri(Uri.parse(url))
                    }

                    // Apply to player
                    exoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }

                    playbackError = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error during retry", e)
                    playbackError = "Failed to retry playback: ${e.message}"
                }
            }

            isRetrying = false
            // If we still have an error after retry, it will be updated by the listener
        }
    }

    // Auto-hide player controls after a delay
    LaunchedEffect(playerControlsVisible) {
        if (playerControlsVisible) {
            delay(5000) // Hide controls after 5 seconds of inactivity
            playerControlsVisible = false
        }
    }

    // Cleanup player when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Releasing ExoPlayer")
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiState.episodeDetail?.title ?: "Episode")
                        if (isPlayingFallbackVideo) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge {
                                Text("DEMO")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show current stream quality
                    uiState.selectedStreamQuality?.let { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = "Quality",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = quality,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading episode...")
                }
            } else if (uiState.error != null) {
                ErrorView(
                    error = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.loadEpisodeDetail(episodeId) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Video player container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clickable { playerControlsVisible = !playerControlsVisible }
                    ) {
                        // Show poster as background
                        uiState.episodeDetail?.poster?.let { poster ->
                            AsyncImage(
                                model = poster,
                                contentDescription = "Episode Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = if (playbackError != null) 0.5f else 0.2f
                            )
                        }

                        // Show the video player when no error
                        if (playbackError == null) {
                            AndroidView(
                                factory = { context ->
                                    StyledPlayerView(context).apply {
                                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        setShowBuffering(StyledPlayerView.SHOW_BUFFERING_ALWAYS)
                                        player = exoPlayer
                                        useController = playerControlsVisible
                                        controllerAutoShow = false
                                        setControllerVisibilityListener(StyledPlayerView.ControllerVisibilityListener { visibility ->
                                            playerControlsVisible = visibility == StyledPlayerView.VISIBLE
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { playerView ->
                                    playerView.player = exoPlayer
                                    playerView.useController = playerControlsVisible
                                }
                            )
                        }

                        // Show fallback video notice
                        if (isPlayingFallbackVideo && !isBuffering && playbackError == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Playing demo video. Actual episode stream unavailable.",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Show buffering indicator
                        if (isBuffering && playbackError == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Buffering...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Show auto-retry indicator
                        if (isRetrying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Auto-retrying... (Attempt $retryCount of $maxRetries)",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Show error overlay
                        if (playbackError != null && !isRetrying) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(48.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = playbackError ?: "Video playback error",
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        uiState.selectedStreamUrl?.let { url ->
                                            exoPlayer?.apply {
                                                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                                                setMediaItem(mediaItem)
                                                prepare()
                                                play()
                                            }
                                            playbackError = null
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Try Again")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Show button to try different quality
                                uiState.episodeDetail?.let { detail ->
                                    if (detail.streams.size > 1) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.tryAlternativeStream()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            )
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Try Different Quality")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Quality selection options
                    uiState.episodeDetail?.let { detail ->
                        if (detail.streams.size > 1) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Available Qualities",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        detail.streams.forEach { stream ->
                                            val isSelected = stream.url == uiState.selectedStreamUrl

                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.selectStreamQuality(stream.url) },
                                                label = { Text(stream.quality) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Episode information and help card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isPlayingFallbackVideo) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "API Restriction Notice",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "The actual episode stream is unavailable due to API restrictions (HTTP 403 Forbidden). A demo video is playing instead.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Add a button to visit the official website
                                            val originalWebsite = uiState.episodeDetail?.let {
                                                val animeId = episodeId.split("_ep").firstOrNull() ?: ""
                                                "https://otakudesu.cloud/anime/$animeId"
                                            }

                                            if (!originalWebsite.isNullOrEmpty()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            uriHandler.openUri(originalWebsite)
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Error opening URI", e)
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Watch on Official Website")
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Divider()

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "If you experience playback issues:",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Try a different video quality",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Try refreshing the video",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Watch directly on the official website",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorView(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Failed to load episode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                // This would ideally navigate to a different episode
                // For now, just retry
                onRetry()
            }
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Another Episode")
        }
    }
}

// FlowRow composable for arranging items in a flow layout
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        // Extract spacing values with explicit types
        val horizontalSpacing = when (horizontalArrangement) {
            is Arrangement.HorizontalOrVertical -> horizontalArrangement.spacing.roundToPx()
            else -> 0
        }
        val verticalSpacing = when (verticalArrangement) {
            is Arrangement.HorizontalOrVertical -> verticalArrangement.spacing.roundToPx()
            else -> 0
        }

        val rows = mutableListOf<Row>()
        var rowWidth = 0
        var rowMaxHeight = 0
        var rowItems = mutableListOf<Placeable>()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)

            if (rowWidth + placeable.width > constraints.maxWidth && rowItems.isNotEmpty()) {
                // Create a new row
                rows.add(Row(items = rowItems, maxHeight = rowMaxHeight))
                rowItems = mutableListOf()
                rowWidth = 0
                rowMaxHeight = 0
            }

            rowItems.add(placeable)
            rowWidth += placeable.width + horizontalSpacing
            rowMaxHeight = maxOf(rowMaxHeight, placeable.height)
        }

        if (rowItems.isNotEmpty()) {
            rows.add(Row(items = rowItems, maxHeight = rowMaxHeight))
        }

        val totalHeight = rows.sumOf { it.maxHeight } + ((rows.size - 1) * verticalSpacing).coerceAtLeast(0)

        layout(constraints.maxWidth, totalHeight) {
            var y = 0

            rows.forEach { row ->
                val rowHorizontalSpacing = if (row.items.size > 1) horizontalSpacing else 0
                val totalItemsWidth = row.items.sumOf { it.width } + ((row.items.size - 1) * rowHorizontalSpacing)
                val availableSpace = constraints.maxWidth - totalItemsWidth

                var x = when (horizontalArrangement) {
                    Arrangement.Start, is Arrangement.HorizontalOrVertical -> 0
                    Arrangement.Center -> availableSpace / 2
                    Arrangement.End -> availableSpace
                    Arrangement.SpaceEvenly -> {
                        if (row.items.size > 0) availableSpace / (row.items.size + 1) else 0
                    }
                    Arrangement.SpaceBetween -> {
                        if (row.items.size > 1) 0 else availableSpace / 2
                    }
                    Arrangement.SpaceAround -> {
                        if (row.items.size > 0) availableSpace / (row.items.size * 2) else 0
                    }
                    else -> 0
                }

                row.items.forEachIndexed { index, placeable ->
                    val itemY = when (verticalArrangement) {
                        Arrangement.Top, is Arrangement.HorizontalOrVertical -> y
                        Arrangement.Center -> y + (row.maxHeight - placeable.height) / 2
                        Arrangement.Bottom -> y + row.maxHeight - placeable.height
                        else -> y
                    }

                    placeable.placeRelative(x, itemY)

                    // Calculate spacing between items
                    val incrementX = placeable.width + when (horizontalArrangement) {
                        Arrangement.SpaceEvenly -> {
                            if (row.items.size > 0) availableSpace / (row.items.size + 1) else horizontalSpacing
                        }
                        Arrangement.SpaceBetween -> {
                            if (row.items.size > 1) {
                                availableSpace / (row.items.size - 1)
                            } else horizontalSpacing
                        }
                        Arrangement.SpaceAround -> {
                            if (row.items.size > 0) availableSpace / row.items.size else horizontalSpacing
                        }
                        else -> horizontalSpacing
                    }

                    x += incrementX
                }

                y += row.maxHeight + verticalSpacing
            }
        }
    }
}

private data class Row(
    val items: List<Placeable>,
    val maxHeight: Int
)