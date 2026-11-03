package com.amarthapa.melody

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioTrack(
    val uri: Uri,
    val title: String
)

data class MelodyUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val selectedIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoadingLibrary: Boolean = false,
    val libraryError: String? = null,
    val controllerReady: Boolean = false,
    val audioSessionId: Int = 0
)

@OptIn(UnstableApi::class)
class MelodyViewModel
    (
    application: Application
) : AndroidViewModel(application) {

    private val app = application

    private val _uiState = MutableStateFlow(
        MelodyUiState()
    )

    val uiState: StateFlow<MelodyUiState> =
        _uiState.asStateFlow()

    private var controller: MediaController? = null

    private var controllerFuture:
            ListenableFuture<MediaController>? = null

    private val pendingActions =
        mutableListOf<(MediaController) -> Unit>()

    private val mainExecutor =
        ContextCompat.getMainExecutor(application)

    // Imported files remain part of the library after a rescan.
    private var importedTracks = emptyList<AudioTrack>()

    private val playerListener = object : Player.Listener {

        override fun onEvents(
            player: Player,
            events: Player.Events
        ) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(
            isPlaying: Boolean
        ) {
            updatePlaybackState()
        }
    }

    init {
        connectToPlaybackService()

        // Receive the current audio session ID for the visualizer.
        viewModelScope.launch {
            PlaybackService.audioSessionId.collect { id ->
                _uiState.update {
                    it.copy(audioSessionId = id)
                }
            }
        }

        // Update progress while the ViewModel is alive.
        viewModelScope.launch {
            while (isActive) {
                updatePlaybackState()
                delay(300L)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun connectToPlaybackService() {
        val token = SessionToken(
            app,
            ComponentName(
                app,
                PlaybackService::class.java
            )
        )

        val future = MediaController.Builder(
            app,
            token
        ).buildAsync()

        controllerFuture = future

        future.addListener(
            {
                try {
                    val connectedController = future.get()

                    controller = connectedController

                    connectedController.addListener(
                        playerListener
                    )

                    _uiState.update {
                        it.copy(controllerReady = true)
                    }

                    updatePlaybackState()

                    val actions = pendingActions.toList()
                    pendingActions.clear()

                    actions.forEach { action ->
                        action(connectedController)
                    }

                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            libraryError =
                                "Could not connect to the playback service."
                        )
                    }
                }
            },
            mainExecutor
        )
    }

    private fun withController(
        action: (MediaController) -> Unit
    ) {
        val currentController = controller

        if (currentController != null) {
            action(currentController)
        } else {
            pendingActions.add(action)
        }
    }

    private fun updatePlaybackState() {
        val currentController = controller ?: return

        val currentUri = currentController
            .currentMediaItem
            ?.localConfiguration
            ?.uri

        val state = _uiState.value

        val matchingIndex = if (currentUri != null) {
            state.tracks.indexOfFirst {
                it.uri == currentUri
            }
        } else {
            -1
        }

        val playerDuration = currentController.duration

        val safeDuration = if (
            playerDuration == C.TIME_UNSET ||
            playerDuration < 0L
        ) {
            0L
        } else {
            playerDuration
        }

        _uiState.update {
            it.copy(
                isPlaying = currentController.isPlaying,
                positionMs = currentController.currentPosition
                    .coerceAtLeast(0L),
                durationMs = safeDuration,
                selectedIndex = when {
                    currentUri == null -> -1
                    matchingIndex >= 0 -> matchingIndex
                    else -> it.selectedIndex
                }
            )
        }
    }

    // --------------------------------------------------------
    // DEVICE LIBRARY
    // --------------------------------------------------------

    fun refreshDeviceLibrary() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingLibrary = true,
                    libraryError = null
                )
            }

            try {
                val deviceTracks = withContext(
                    Dispatchers.IO
                ) {
                    loadDeviceAudio(app)
                }

                val mergedTracks = (
                        deviceTracks + importedTracks
                        ).distinctBy { it.uri }

                _uiState.update {
                    it.copy(
                        tracks = mergedTracks,
                        isLoadingLibrary = false,
                        libraryError = null
                    )
                }

                updatePlaybackState()

            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        isLoadingLibrary = false,
                        libraryError =
                            "Allow audio access and scan again."
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingLibrary = false,
                        libraryError =
                            "Unable to scan the music library."
                    )
                }
            }
        }
    }

    fun setLibraryError(message: String) {
        _uiState.update {
            it.copy(libraryError = message)
        }
    }

    // --------------------------------------------------------
    // IMPORT FILES
    // --------------------------------------------------------

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val newTracks = withContext(Dispatchers.IO) {
                uris.map { uri ->

                    try {
                        app.contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (_: Exception) {
                        // Temporary picker access can still work.
                    }

                    val displayName =
                        app.contentResolver.query(
                            uri,
                            arrayOf(
                                OpenableColumns.DISPLAY_NAME
                            ),
                            null,
                            null,
                            null
                        )?.use { cursor ->

                            if (cursor.moveToFirst()) {
                                cursor.getString(0)
                            } else {
                                null
                            }

                        } ?: uri.lastPathSegment
                        ?: "Unknown audio"

                    AudioTrack(
                        uri = uri,
                        title = displayName.substringBeforeLast(
                            '.',
                            displayName
                        )
                    )
                }
            }

            importedTracks = (
                    importedTracks + newTracks
                    ).distinctBy { it.uri }

            val mergedTracks = (
                    _uiState.value.tracks + newTracks
                    ).distinctBy { it.uri }

            _uiState.update {
                it.copy(
                    tracks = mergedTracks,
                    libraryError = null
                )
            }

            // Preserve the current app's import-and-play behavior.
            val firstIndex = _uiState.value.tracks.indexOfFirst {
                it.uri == newTracks.first().uri
            }

            if (firstIndex >= 0) {
                playTrack(firstIndex)
            }
        }
    }

    // --------------------------------------------------------
    // PLAYBACK CONTROLS
    // --------------------------------------------------------

    private fun buildMediaItem(
        track: AudioTrack
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .build()

        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playTrack(index: Int) {
        if (index !in _uiState.value.tracks.indices) return

        withController { mediaController ->

            val currentTracks = _uiState.value.tracks

            if (index !in currentTracks.indices) return@withController

            mediaController.setMediaItems(
                currentTracks.map { buildMediaItem(it) },
                index,
                0L
            )

            mediaController.prepare()
            mediaController.play()

            updatePlaybackState()
        }
    }

    fun togglePlayPause() {
        withController { mediaController ->

            if (mediaController.isPlaying) {
                mediaController.pause()
            } else if (mediaController.mediaItemCount > 0) {

                if (
                    mediaController.playbackState ==
                    Player.STATE_ENDED
                ) {
                    mediaController.seekTo(0L)
                }

                mediaController.play()
            }

            updatePlaybackState()
        }
    }

    fun next() {
        withController { mediaController ->
            if (mediaController.hasNextMediaItem()) {
                mediaController.seekToNextMediaItem()
                mediaController.play()
            }
            updatePlaybackState()
        }
    }

    fun previous() {
        withController { mediaController ->
            if (mediaController.hasPreviousMediaItem()) {
                mediaController.seekToPreviousMediaItem()
                mediaController.play()
            }
            updatePlaybackState()
        }
    }

    fun seekTo(positionMs: Long) {
        withController { mediaController ->
            mediaController.seekTo(
                positionMs.coerceAtLeast(0L)
            )
            updatePlaybackState()
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller = null

        pendingActions.clear()

        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }

        controllerFuture = null

        super.onCleared()
    }
}

// ------------------------------------------------------------
// MediaStore query used by the ViewModel
// ------------------------------------------------------------

private suspend fun loadDeviceAudio(
    context: Context
): List<AudioTrack> = withContext(Dispatchers.IO) {

    val result = mutableListOf<AudioTrack>()

    val collection =
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.MediaColumns.DISPLAY_NAME
    )

    val selection =
        "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    val sortOrder =
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        sortOrder
    )?.use { cursor ->

        val idColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media._ID
        )

        val titleColumn = cursor.getColumnIndex(
            MediaStore.Audio.Media.TITLE
        )

        val nameColumn = cursor.getColumnIndex(
            MediaStore.MediaColumns.DISPLAY_NAME
        )

        while (cursor.moveToNext()) {

            val id = cursor.getLong(idColumn)

            val title = if (titleColumn >= 0) {
                cursor.getString(titleColumn)
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }

            val name = if (nameColumn >= 0) {
                cursor.getString(nameColumn)
            } else {
                null
            }

            val displayTitle = title
                ?: name?.substringBeforeLast('.', name)
                ?: "Unknown audio"

            result.add(
                AudioTrack(
                    uri = ContentUris.withAppendedId(
                        collection,
                        id
                    ),
                    title = displayTitle
                )
            )
        }
    }

    result.distinctBy { it.uri }
}
