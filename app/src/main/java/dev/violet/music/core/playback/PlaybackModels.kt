package dev.violet.music.core.playback

import dev.violet.music.core.model.TrackFilter
import kotlinx.coroutines.flow.StateFlow

data class PlayableTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val artworkUri: String?,
    val durationMs: Long,
    val uri: String,
    val mimeType: String?,
)

data class QueueItem(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
)

enum class RepeatMode { OFF, ALL, ONE }
enum class PlaybackError { CONNECTION, PERMISSION, UNAVAILABLE, EMPTY_QUEUE, PLAYBACK }

data class PlaybackUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val current: QueueItem? = null,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val error: PlaybackError? = null,
)

interface PlaybackController {
    val state: StateFlow<PlaybackUiState>
    fun acquire()
    fun release()
    fun reconnect()
    suspend fun play(filter: TrackFilter, trackId: String? = null, shuffle: Boolean = false)
    suspend fun playIds(ids: List<String>, startIndex: Int = 0, shuffle: Boolean = false)
    fun togglePlay()
    fun previous()
    fun next()
    fun seekTo(positionMs: Long)
    fun playQueueItem(index: Int)
    fun removeQueueItem(index: Int)
    fun toggleShuffle()
    fun cycleRepeat()
    fun stop()
    fun dismissError()
    fun reportError(error: PlaybackError)
}
