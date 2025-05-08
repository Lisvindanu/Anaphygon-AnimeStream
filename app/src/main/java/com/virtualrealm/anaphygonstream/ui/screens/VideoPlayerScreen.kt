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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.virtualrealm.anaphygonstream.ui.viewmodels.EpisodeViewModel
import com.virtualrealm.anaphygonstream.utils.VideoExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "VideoPlayerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    episodeId: String,
    onNavigateBack: () -> Unit,
    viewModel: EpisodeViewModel = viewModel(),
    navController: NavController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Player state
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var playerControlsVisible by remember { mutableStateOf(true) }
    var isPlayingFallbackVideo by remember { mutableStateOf(false) }
    var useWebView by remember { mutableStateOf(false) }
    val maxRetries = 3

    // Load episode details when screen first appears
    LaunchedEffect(episodeId) {
        viewModel.loadEpisodeDetail(episodeId)
    }

    // Lifecycle observer for player
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (playbackError == null) {
                        exoPlayer?.play()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer?.release()
                    exoPlayer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle stream URL changes and set up the player
    LaunchedEffect(uiState.selectedStreamUrl) {
        uiState.selectedStreamUrl?.let { url ->
            // Check if this is a fallback video
            isPlayingFallbackVideo = url.contains("gtv-videos-bucket") ||
                    url.contains("commondatastorage.googleapis.com")

            // Check if the URL is likely a direct media URL or a player page
            val isLikelyDirectMedia = url.endsWith(".mp4") ||
                    url.endsWith(".m3u8") ||
                    url.contains("direct") ||
                    isPlayingFallbackVideo

            // If it's likely a player page, try to extract the actual video URL
            if (!isLikelyDirectMedia && !url.contains("example.com")) {
                // Show extraction indicator
                isExtracting = true

                try {
                    Log.d(TAG, "Attempting to extract direct URL from: $url")
                    val extractedUrl = VideoExtractor.extractVideoUrl(url)

                    if (!extractedUrl.isNullOrEmpty()) {
                        // Successfully extracted direct video URL
                        Log.d(TAG, "Successfully extracted direct URL: $extractedUrl")

                        // Update the viewModel with the extracted URL
                        viewModel.updateStreamUrl(extractedUrl, uiState.selectedStreamQuality + " (Direct)")

                        // Exit this LaunchedEffect - it will be triggered again with the new URL
                        isExtracting = false
                        return@let
                    } else {
                        Log.d(TAG, "Failed to extract direct URL")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during video extraction: ${e.message}", e)
                }

                isExtracting = false
            }

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

                // Create appropriate MediaSource based on URL type
                val mediaSourceFactory = if (url.contains(".m3u8")) {
                    // HLS (m3u8) stream
                    HlsMediaSource.Factory(httpDataSourceFactory)
                } else {
                    // Standard progressive stream (mp4, etc)
                    ProgressiveMediaSource.Factory(httpDataSourceFactory)
                }

                // Build the ExoPlayer with our custom MediaSourceFactory
                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build().apply {
                        // Create appropriate MediaItem based on URL type
                        val mediaItem = if (url.contains(".m3u8")) {
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                        } else {
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
                                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                                        "This video format is not supported by the player."
                                    PlaybackException.ERROR_CODE_TIMEOUT ->
                                        "Operation timed out. Try again or select a different quality."
                                    PlaybackException.ERROR_CODE_UNSPECIFIED ->
                                        if (error.message?.contains("format", ignoreCase = true) == true) {
                                            "Unrecognized video format. This may be a webpage instead of a video file."
                                        } else {
                                            "Unknown playback error: ${error.message ?: "No details available"}"
                                        }
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

            // Check if we should try URL extraction again
            uiState.selectedStreamUrl?.let { url ->
                val isPlayerPage = !url.endsWith(".mp4") && !url.endsWith(".m3u8") &&
                        !isPlayingFallbackVideo && !url.contains("direct")

                if (isPlayerPage && retryCount <= 1) {
                    // For first retry on player pages, try to extract directly
                    try {
                        val extractedUrl = VideoExtractor.extractVideoUrl(url)
                        if (!extractedUrl.isNullOrEmpty()) {
                            viewModel.updateStreamUrl(extractedUrl, uiState.selectedStreamQuality + " (Direct)")

                            isRetrying = false
                            playbackError = null
                            return@LaunchedEffect
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during retry extraction: ${e.message}", e)
                    }
                }

                // Standard media retry
                try {
                    // Create a new media item with potentially different settings
                    val mediaItem = if (url.contains(".m3u8")) {
                        MediaItem.Builder()
                            .setUri(Uri.parse(url))
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    } else {
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
                    onRetry = { viewModel.loadEpisodeDetail(episodeId) },
                    onTryWebView = {
                        // Trigger WebView fallback for current episode
                        useWebView = true
                    }
                )
            } else if (useWebView) {
                // WebView player implementation
                uiState.episodeDetail?.streams?.firstOrNull()?.let { stream ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Add a header with info and back button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { useWebView = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Player")
                            }
                            Text(
                                text = "WebView Player (Original Source)",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // WebView implementation
                        AndroidView(
                            factory = { context ->
                                android.webkit.WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        mediaPlaybackRequiresUserGesture = false
                                        javaScriptCanOpenWindowsAutomatically = true
                                        setSupportMultipleWindows(true)
                                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    }
                                    webViewClient = android.webkit.WebViewClient()
                                    webChromeClient = android.webkit.WebChromeClient()

                                    // Load the original URL, not the potentially extracted one
                                    loadUrl(stream.url)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
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

                        // Show URL extraction notice
                        if (isExtracting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
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
                                        text = "Extracting video URL...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "This may take a moment",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // WebView fallback button
                                    OutlinedButton(
                                        onClick = { useWebView = true }
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Use WebView")
                                    }
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
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                                text = "The actual episode stream is unavailable due to API restrictions. A demo video is playing instead.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Add a button to try WebView as an alternative
                                            OutlinedButton(
                                                onClick = { useWebView = true }
                                            ) {
                                                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Try WebView Player")
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
                                        text = "Use WebView player for embedded content",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Link to original website
                                val originalWebsite = detail.streams.firstOrNull()?.let {
                                    if (it.url.startsWith("http")) {
                                        val domain = Uri.parse(it.url).host ?: ""
                                        "https://$domain"
                                    } else null
                                }

                                if (!originalWebsite.isNullOrEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Visit original source website",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                try {
                                                    uriHandler.openUri(originalWebsite)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error opening URI", e)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Advanced options card (only show when needed)
                        if (!isPlayingFallbackVideo) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Advanced Options",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = { useWebView = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open in WebView")
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    OutlinedButton(
                                        onClick = {
                                            // Extract the animeId from the episodeId
                                            val parts = episodeId.split("_ep")
                                            if (parts.size == 2) {
                                                val animeId = parts[0]
                                                try {
                                                    uriHandler.openUri("https://otakudesu.cloud/anime/$animeId")
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error opening URI", e)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Public, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Visit Official Source")
                                    }

                                    // Add a button to refresh the source data
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.loadEpisodeDetail(episodeId)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Refresh Source Data")
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

@Composable
fun ErrorView(
    error: String,
    onRetry: () -> Unit,
    onTryWebView: () -> Unit
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

        // Primary action: Retry
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary action: Try WebView
        OutlinedButton(onClick = onTryWebView) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try WebView Player")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tertiary action: Try another episode
        TextButton(
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