package dev.violet.music.core.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult

@androidx.annotation.OptIn(UnstableApi::class)
class ServiceQueue(
    private val player: ExoPlayer,
    private val resolver: LocalPlayableSourceResolver,
    private val history: PlaybackHistoryRecorder,
) {
    private fun index(id: String?) =
        if (id == null) -1
        else
            (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).occurrenceId() == id
            } ?: -1

    suspend fun execute(args: Bundle): SessionResult {
        val id = args.getString("id")
        when (args.getString("operation")) {
            "next" -> {
                player.seekToNextMediaItem()
                if (player.playerError != null) player.prepare()
            }
            "previous" -> {
                player.seekToPrevious()
                if (player.playerError != null) player.prepare()
            }
            "play" -> {
                val index = index(id)
                if (index < 0) return SessionResult(SessionError.ERROR_BAD_VALUE)
                if (index == player.currentMediaItemIndex) history.restart()
                player.seekToDefaultPosition(index)
                player.prepare()
                player.play()
            }
            "remove" -> {
                val index = index(id)
                if (index < 0) return SessionResult(SessionError.ERROR_BAD_VALUE)
                player.removeMediaItem(index)
            }
            "move" -> {
                val from = index(id)
                val target = index(args.getString("target"))
                val order =
                    movedOrder(playbackOrder(player), from, target)
                        ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
                if (player.shuffleModeEnabled)
                    player.setShuffleOrder(
                        ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime())
                    )
                else player.moveMediaItem(from, target)
            }
            "enqueue" -> {
                val ids = args.getStringArrayList("tracks").orEmpty().take(100)
                val items =
                    resolver.resolve(ids, skipUnavailable = true).map { it.withOccurrence() }
                if (items.isEmpty())
                    return SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply { putInt("skipped", ids.size) },
                    )
                val order = playbackOrder(player)
                val oldSize = player.mediaItemCount
                val anchor = index(args.getString("after"))
                val next = args.getBoolean("next")
                if (player.shuffleModeEnabled) {
                    player.addMediaItems(items)
                    val updated = order.toMutableList()
                    val at =
                        if (next)
                            (order.indexOf(
                                    if (anchor >= 0) anchor else player.currentMediaItemIndex
                                ) + 1)
                                .coerceAtLeast(0)
                        else order.size
                    updated.addAll(at, (oldSize until oldSize + items.size).toList())
                    player.setShuffleOrder(
                        ShuffleOrder.DefaultShuffleOrder(updated.toIntArray(), System.nanoTime())
                    )
                } else {
                    val at =
                        if (next && oldSize > 0)
                            ((if (anchor >= 0) anchor else player.currentMediaItemIndex) + 1)
                                .coerceIn(0, oldSize)
                        else oldSize
                    player.addMediaItems(at, items)
                }
                if (oldSize == 0) {
                    player.pause()
                    player.prepare()
                }
                return SessionResult(
                    SessionResult.RESULT_SUCCESS,
                    Bundle().apply {
                        putString("last", items.last().occurrenceId())
                        putInt("skipped", ids.size - items.size)
                    },
                )
            }
            else -> return SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }
}
