package com.amarthapa.melody

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(
            audioSessionId: Int
        ) {
            _audioSessionId.value = audioSessionId
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()

        player = exoPlayer

        // Configure audio playback and audio focus.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer.setAudioAttributes(
            audioAttributes,
            true
        )

        // Pause when headphones are disconnected.
        exoPlayer.setHandleAudioBecomingNoisy(true)

        exoPlayer.addListener(playerListener)

        _audioSessionId.value = exoPlayer.audioSessionId

        mediaSession = MediaSession.Builder(
            this,
            exoPlayer
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        player?.removeListener(playerListener)

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        _audioSessionId.value = 0

        super.onDestroy()
    }

    companion object {
        private val _audioSessionId = MutableStateFlow(0)

        val audioSessionId: StateFlow<Int> =
            _audioSessionId.asStateFlow()
    }
}
