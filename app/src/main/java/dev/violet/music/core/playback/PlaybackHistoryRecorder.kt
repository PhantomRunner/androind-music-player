package dev.violet.music.core.playback

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import dev.violet.music.core.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Attached to the service player, never to a screen/controller subscription. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackHistoryRecorder(
    private val player: ExoPlayer,
    context: Context,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) : AnalyticsListener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var attempt: ListeningAccumulator? = null
    private var occurrence: String? = null
    private var duration = 0L
    private var seekedToEnd = false
    private var checkpointAt = SystemClock.elapsedRealtime()
    private var ticker: Job? = null

    init {
        player.addAnalyticsListener(this)
    }

    private fun audible() =
        player.isPlaying &&
            player.volume > 0 &&
            !player.isDeviceMuted &&
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0

    private fun sample(flush: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val current = player.currentMediaItem ?: return
        val key = current.occurrenceId()
        if (attempt == null && audible()) {
            occurrence = key
            duration =
                player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0)
                    ?: current.mediaMetadata.durationMs
                    ?: 0
            attempt = ListeningAccumulator(current.mediaId, wall)
            seekedToEnd = false
            checkpointAt = now
        }
        val recording = attempt ?: return
        if (key != occurrence) return
        val before = recording.isQualified
        if (player.duration > 0) duration = player.duration
        recording.advance(now, wall, audible(), duration)
        if (flush || before != recording.isQualified || now - checkpointAt >= 10_000) {
            history.record(recording.checkpoint(wall))
            checkpointAt = now
        }
    }

    private fun finish(complete: Boolean) {
        val recording = attempt ?: return
        recording.advance(
            SystemClock.elapsedRealtime(),
            System.currentTimeMillis(),
            false,
            duration,
        )
        history.record(recording.checkpoint(System.currentTimeMillis(), complete, finalize = true))
        attempt = null
        occurrence = null
    }

    fun restart() {
        finish(false)
        sample(true)
    }

    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (
            mediaItem?.occurrenceId() != occurrence ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        ) {
            finish(
                !seekedToEnd &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            )
        }
        sample(true)
    }

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        if (player.playbackState == Player.STATE_ENDED) finish(!seekedToEnd)
        else if (player.playbackState == Player.STATE_IDLE) finish(false)
        else sample(flush = !player.isPlaying)
        if (player.isPlaying && ticker?.isActive != true)
            ticker =
                scope.launch {
                    while (player.isPlaying) {
                        sample()
                        delay(250)
                    }
                }
        if (!player.isPlaying) {
            ticker?.cancel()
            ticker = null
        }
    }

    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (
            reason == Player.DISCONTINUITY_REASON_SEEK &&
                oldPosition.mediaItemIndex == newPosition.mediaItemIndex
        )
            seekedToEnd = duration > 0 && newPosition.positionMs >= duration
        sample(true)
    }

    fun close() {
        ticker?.cancel()
        finish(false)
        player.removeAnalyticsListener(this)
    }
}
