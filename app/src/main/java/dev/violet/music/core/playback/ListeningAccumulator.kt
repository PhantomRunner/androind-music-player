package dev.violet.music.core.playback

import dev.violet.music.core.database.ListeningSegmentEntity
import dev.violet.music.core.database.PlaybackEventEntity
import java.util.UUID
import kotlin.math.abs

data class HistoryCheckpoint(
    val event: PlaybackEventEntity,
    val segments: List<ListeningSegmentEntity>,
)

/** Pure attempt accounting. Media position never contributes to elapsed listening time. */
class ListeningAccumulator(
    private val trackId: String,
    private val startedAt: Long,
    private val eventId: String = UUID.randomUUID().toString(),
) {
    private var listened = 0L
    private var activeAt: Long? = null
    private var wallAt = startedAt
    private var sequence = 0
    private var qualified = false
    private var finished = false
    private val segments = mutableListOf<ListeningSegmentEntity>()

    fun advance(realtime: Long, wall: Long, audible: Boolean, durationMs: Long) {
        if (finished) return
        activeAt?.let { previous ->
            val delta = (realtime - previous).coerceAtLeast(0)
            if (delta > 0) {
                val previousSegment = segments.lastOrNull()
                if (previousSegment != null && abs(previousSegment.endedAt - wallAt) <= 5) {
                    segments[segments.lastIndex] =
                        previousSegment.copy(
                            endedAt = previousSegment.endedAt + delta,
                            listenedMs = previousSegment.listenedMs + delta,
                        )
                } else
                    segments +=
                        ListeningSegmentEntity(eventId, sequence++, wallAt, wallAt + delta, delta)
                listened += delta
            }
        }
        val threshold = if (durationMs > 0) minOf(30_000L, (durationMs + 1) / 2) else 30_000L
        qualified = qualified || listened >= threshold
        activeAt = if (audible) realtime else null
        // Reset the wall-clock anchor at each boundary: clock/time-zone changes add no time.
        wallAt = wall
    }

    fun checkpoint(
        wall: Long,
        complete: Boolean = false,
        finalize: Boolean = false,
    ): HistoryCheckpoint {
        val result =
            HistoryCheckpoint(
                PlaybackEventEntity(
                    eventId,
                    trackId,
                    startedAt,
                    listened,
                    qualified,
                    complete,
                    finalize,
                    wall,
                ),
                segments.toList(),
            )
        segments.clear()
        finished = finished || finalize
        return result
    }

    val isQualified
        get() = qualified

    val listenedMs
        get() = listened
}
