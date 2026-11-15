package dev.violet.music.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionCommand
import java.util.UUID

const val QUEUE_OCCURRENCE = "violet.queue.occurrence"
const val QUEUE_ACTION = "violet.queue.action"
val QueueCommand
    get() = SessionCommand(QUEUE_ACTION, Bundle.EMPTY)

fun MediaItem.occurrenceId(): String = mediaMetadata.extras?.getString(QUEUE_OCCURRENCE).orEmpty()

fun MediaItem.withOccurrence(): MediaItem =
    buildUpon()
        .setMediaMetadata(
            mediaMetadata
                .buildUpon()
                .setExtras(
                    Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
                        putString(QUEUE_OCCURRENCE, UUID.randomUUID().toString())
                    }
                )
                .build()
        )
        .build()

/** Snapshot the Media3 traversal, including its actual shuffle order. No independent queue. */
fun playbackOrder(player: Player): List<Int> {
    val timeline = player.currentTimeline
    if (timeline.isEmpty) return emptyList()
    val result = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
    while (index != -1 && result.size < timeline.windowCount) {
        result += index
        index =
            timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
    }
    return result
}

fun <T> movedOrder(order: List<T>, source: T, target: T): List<T>? {
    val from = order.indexOf(source)
    val to = order.indexOf(target)
    if (from < 0 || to < 0) return null
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
