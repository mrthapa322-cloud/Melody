package com.amarthapa.melody

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.pow
import kotlin.math.sqrt

private val MelodyIvory = Color(0xFFF5F0E5)
private val MelodyInk = Color(0xFF292A24)
private val MelodyOlive = Color(0xFF454C3D)

// ============================================================
// ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val playbackViewModel: MelodyViewModel = viewModel()

            var darkTheme by rememberSaveable {
                mutableStateOf(false)
            }

            MelodyTheme(darkTheme = darkTheme) {
                MelodyApp(
                    viewModel = playbackViewModel,
                    darkTheme = darkTheme,
                    onThemeChange = { darkTheme = it }
                )
            }
        }
    }
}

// ============================================================
// THEME
// ============================================================

@Composable
private fun MelodyTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val lightColors = lightColorScheme(
        primary = MelodyOlive,
        onPrimary = MelodyIvory,
        secondary = Color(0xFF85866E),
        background = MelodyIvory,
        onBackground = MelodyInk,
        surface = MelodyIvory,
        onSurface = MelodyInk,
        surfaceVariant = Color(0xFFE8E1D3),
        onSurfaceVariant = Color(0xFF625F55)
    )

    val darkColors = darkColorScheme(
        primary = Color(0xFFD2D8BE),
        onPrimary = Color(0xFF25261F),
        secondary = Color(0xFFB8B69C),
        background = Color(0xFF20211D),
        onBackground = Color(0xFFF2EBDD),
        surface = Color(0xFF20211D),
        onSurface = Color(0xFFF2EBDD),
        surfaceVariant = Color(0xFF35362E),
        onSurfaceVariant = Color(0xFFC6C2B3)
    )

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        content = content
    )
}

// ============================================================
// MAIN COMPOSE APP
// ============================================================

@Composable
private fun MelodyApp(
    viewModel: MelodyViewModel,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable {
        mutableStateOf("Listen")
    }

    var hasVisualizerPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Audio library permission differs by Android version.
    val audioLibraryPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val libraryPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                viewModel.refreshDeviceLibrary()
            } else {
                viewModel.setLibraryError(
                    "Audio access is needed to scan your music library."
                )
            }
        }

    val visualizerPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasVisualizerPermission = granted
        }

    val audioPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importFiles(uris)
                currentScreen = "Listen"
            }
        }

    val requestLibraryScan: () -> Unit = {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                audioLibraryPermission
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            viewModel.refreshDeviceLibrary()
        } else {
            libraryPermissionLauncher.launch(
                audioLibraryPermission
            )
        }
    }

    val chooseMusic: () -> Unit = {
        audioPicker.launch(arrayOf("audio/*"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val tabs = listOf(
                    "Listen" to Icons.Default.Headphones,
                    "Library" to Icons.Default.LibraryMusic,
                    "Settings" to Icons.Default.Settings
                )

                tabs.forEach { (label, icon) ->
                    NavigationBarItem(
                        selected = currentScreen == label,
                        onClick = {
                            currentScreen = label

                            if (label == "Library") {
                                requestLibraryScan()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(label)
                        }
                    )
                }
            }
        }
    ) { padding ->

        when (currentScreen) {

            "Listen" -> ListenScreen(
                modifier = Modifier.padding(padding),
                track = uiState.tracks.getOrNull(
                    uiState.selectedIndex
                ),
                isPlaying = uiState.isPlaying,
                position = uiState.positionMs,
                duration = uiState.durationMs,
                audioSessionId = uiState.audioSessionId,
                hasVisualizerPermission = hasVisualizerPermission,
                onRequestVisualizerPermission = {
                    visualizerPermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                },
                onOpenLibrary = {
                    currentScreen = "Library"
                    requestLibraryScan()
                },
                onPlayPause = {
                    if (
                        uiState.selectedIndex < 0 &&
                        !uiState.controllerReady
                    ) {
                        currentScreen = "Library"
                        requestLibraryScan()
                    } else if (uiState.selectedIndex < 0) {
                        currentScreen = "Library"
                        requestLibraryScan()
                    } else {
                        viewModel.togglePlayPause()
                    }
                },
                onPrevious = {
                    viewModel.previous()
                },
                onNext = {
                    viewModel.next()
                },
                onSeek = { newPosition ->
                    viewModel.seekTo(newPosition)
                }
            )

            "Library" -> LibraryScreen(
                modifier = Modifier.padding(padding),
                tracks = uiState.tracks,
                selectedIndex = uiState.selectedIndex,
                isLoading = uiState.isLoadingLibrary,
                errorMessage = uiState.libraryError,
                onRefresh = requestLibraryScan,
                onChooseMusic = chooseMusic,
                onTrackSelected = { index ->
                    viewModel.playTrack(index)
                    currentScreen = "Listen"
                }
            )

            "Settings" -> SettingsScreen(
                modifier = Modifier.padding(padding),
                darkTheme = darkTheme,
                onThemeChange = onThemeChange
            )
        }
    }
}

// ============================================================
// LISTEN SCREEN
// ============================================================

@Composable
private fun ListenScreen(
    modifier: Modifier = Modifier,
    track: AudioTrack?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    audioSessionId: Int,
    hasVisualizerPermission: Boolean,
    onRequestVisualizerPermission: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var isSeeking by remember(track?.uri) {
        mutableStateOf(false)
    }

    var seekPosition by remember(track?.uri) {
        mutableFloatStateOf(0f)
    }

    val sliderMax = duration.coerceAtLeast(1L).toFloat()

    val sliderValue = if (isSeeking) {
        seekPosition.coerceIn(0f, sliderMax)
    } else {
        position.toFloat().coerceIn(0f, sliderMax)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "MELODY / PERSONAL AUDIO",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Listen closely.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(Modifier.height(20.dp))

        LiveAudioVisualizer(
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            hasPermission = hasVisualizerPermission,
            onRequestPermission = onRequestVisualizerPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(255.dp)
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = "A LITTLE SPACE FOR SOUND",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = if (track != null) {
                "Your local music"
            } else {
                "Choose a song. Make the moment yours."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { value ->
                seekPosition = value
                isSeeking = true
            },
            onValueChangeFinished = {
                onSeek(seekPosition.toLong())
                isSeeking = false
            },
            valueRange = 0f..sliderMax,
            enabled = duration > 0L,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = track != null,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous track",
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.width(20.dp))

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (isPlaying) {
                        "Pause"
                    } else {
                        "Play"
                    },
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.width(20.dp))

            IconButton(
                onClick = onNext,
                enabled = track != null,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next track",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        FilledTonalButton(
            onClick = onOpenLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null
            )

            Spacer(Modifier.width(10.dp))

            Text("Browse music library")
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ============================================================
// LIVE AUDIO VISUALIZER
// ============================================================

@Composable
private fun LiveAudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 48

    var bars by remember {
        mutableStateOf(List(barCount) { 0.035f })
    }

    var unavailable by remember {
        mutableStateOf(false)
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    DisposableEffect(
        audioSessionId,
        hasPermission,
        isPlaying
    ) {
        var visualizer: Visualizer? = null
        var active = true

        unavailable = false

        if (
            hasPermission &&
            isPlaying &&
            audioSessionId > 0
        ) {
            try {
                val current = Visualizer(audioSessionId)
                visualizer = current

                val range = Visualizer.getCaptureSizeRange()

                current.captureSize = 1024.coerceIn(
                    range[0],
                    range[1]
                )

                current.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {

                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            // Melody uses the FFT spectrum below.
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null) return

                            val newBars = fftToBars(
                                fft,
                                barCount
                            )

                            mainHandler.post {
                                if (active) {
                                    bars = newBars
                                }
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )

                current.enabled = true

            } catch (_: Exception) {
                unavailable = true

                try {
                    visualizer?.release()
                } catch (_: Exception) {
                    // Ignore cleanup failures.
                }

                visualizer = null
            }
        } else {
            bars = List(barCount) { 0.035f }
        }

        onDispose {
            active = false
            mainHandler.removeCallbacksAndMessages(null)

            visualizer?.let { current ->
                try {
                    current.setDataCaptureListener(
                        null,
                        0,
                        false,
                        false
                    )
                } catch (_: Exception) {
                    // Continue cleanup.
                }

                try {
                    current.enabled = false
                } catch (_: Exception) {
                    // Continue cleanup.
                }

                try {
                    current.release()
                } catch (_: Exception) {
                    // Continue cleanup.
                }
            }
        }
    }

    val background = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 14.dp,
                    vertical = 24.dp
                )
        ) {
            val spacing =
                size.width / (bars.size + 1).toFloat()

            val centerY = size.height / 2f

            val strokeWidth = (spacing * 0.48f)
                .coerceAtLeast(2.dp.toPx())

            bars.forEachIndexed { index, value ->

                val level = if (isPlaying && hasPermission) {
                    value.coerceIn(0.025f, 1f)
                } else {
                    0.035f
                }

                val barHeight = (size.height * 0.88f * level)
                    .coerceAtLeast(3.dp.toPx())

                val x = spacing * (index + 1)

                drawLine(
                    color = if (index % 5 == 0) {
                        primary
                    } else {
                        secondary.copy(alpha = 0.85f)
                    },
                    start = Offset(
                        x,
                        centerY - barHeight / 2f
                    ),
                    end = Offset(
                        x,
                        centerY + barHeight / 2f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Let the sound move.",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Allow audio permission for live " +
                            "visualization. Melody does not save " +
                            "or send audio recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Button(onClick = onRequestPermission) {
                    Text("Enable visualizer")
                }
            }
        } else {
            val message = when {
                unavailable ->
                    "LIVE VISUALIZATION UNAVAILABLE"

                !isPlaying ->
                    "PLAY MUSIC TO SEE THE SPECTRUM"

                audioSessionId <= 0 ->
                    "PREPARING LIVE SPECTRUM"

                else -> null
            }

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}

private fun fftToBars(
    fft: ByteArray,
    barCount: Int
): List<Float> {
    val maxBin = (fft.size / 2 - 1).coerceAtLeast(1)

    return List(barCount) { bar ->

        val startBin = maxBin.toDouble()
            .pow(bar.toDouble() / barCount)
            .toInt()
            .coerceIn(1, maxBin)

        val endBin = maxBin.toDouble()
            .pow((bar + 1).toDouble() / barCount)
            .toInt()
            .coerceIn(startBin, maxBin)

        var peak = 0.0

        for (bin in startBin..endBin) {
            val index = bin * 2

            if (index + 1 >= fft.size) continue

            val real = fft[index].toInt()
            val imaginary = fft[index + 1].toInt()

            val magnitude = sqrt(
                real.toDouble() * real +
                        imaginary.toDouble() * imaginary
            ) / 128.0

            peak = maxOf(peak, magnitude)
        }

        (sqrt(peak) * 1.25)
            .toFloat()
            .coerceIn(0.035f, 1f)
    }
}

// ============================================================
// LIBRARY SCREEN
// ============================================================

@Composable
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onChooseMusic: () -> Unit,
    onTrackSelected: (Int) -> Unit
) {
    var query by remember {
        mutableStateOf("")
    }

    val filteredTracks = tracks
        .mapIndexed { index, track -> index to track }
        .filter { (_, track) ->
            track.title.contains(query, ignoreCase = true)
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Text(
            text = "YOUR COLLECTION",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search music"
                )
            },
            placeholder = {
                Text("Find a song")
            }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${tracks.size} audio files",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = onRefresh,
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Scanning…" else "Refresh")
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading && tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()

                Spacer(Modifier.height(16.dp))

                Text("Scanning your music library…")
            }

        } else if (filteredTracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (tracks.isEmpty()) {
                        "Your library starts here."
                    } else {
                        "No matching songs."
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Scan your device or import audio files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                FilledTonalButton(onClick = onRefresh) {
                    Text("Scan device music")
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onChooseMusic) {
                    Text("Import specific files")
                }
            }

        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredTracks,
                    key = { it.first }
                ) { item ->
                    val index = item.first
                    val track = item.second
                    val isSelected = index == selectedIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.10f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable {
                                onTrackSelected(index)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.MusicNote
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp)
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = if (isSelected) {
                                    "Selected track"
                                } else {
                                    "Local audio"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SETTINGS SCREEN
// ============================================================

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Text(
            text = "MAKE IT YOURS",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (darkTheme) {
                    Icons.Default.DarkMode
                } else {
                    Icons.Default.LightMode
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (darkTheme) {
                        "Dark theme"
                    } else {
                        "Light theme"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Warm paper or quiet night.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = darkTheme,
                onCheckedChange = onThemeChange
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "About Melody",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "A little space for sound.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "An original music player designed around " +
                    "your listening experience, with a quiet " +
                    "interface and live audio visualization.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Melody • First edition",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

// ============================================================
// TIME FORMATTER
// ============================================================

private fun formatTime(milliseconds: Long): String {
    val totalSeconds =
        milliseconds.coerceAtLeast(0L) / 1000L

    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
