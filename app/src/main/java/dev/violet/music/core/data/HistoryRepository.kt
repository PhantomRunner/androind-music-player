package dev.violet.music.core.data

import androidx.room.withTransaction
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.model.ActivityBucket
import dev.violet.music.core.model.HistoryPeriod
import dev.violet.music.core.model.ListeningStats
import dev.violet.music.core.playback.HistoryCheckpoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class HistoryRepository @Inject constructor(private val database: MusicDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface Write {
        data class Checkpoint(val value: HistoryCheckpoint) : Write

        data class Barrier(val done: CompletableDeferred<Unit>) : Write
    }

    private val writes = Channel<Write>(Channel.UNLIMITED)

    init {
        scope.launch {
            retryWrite { database.experienceDao().finalizeInterrupted() }
            for (write in writes) when (write) {
                is Write.Checkpoint -> retryWrite { persist(write.value) }
                is Write.Barrier -> write.done.complete(Unit)
            }
        }
    }

    private suspend fun retryWrite(action: suspend () -> Unit) {
        var delayMs = 1_000L
        while (true) try {
            action()
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: android.database.sqlite.SQLiteException) {
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(10_000)
        }
    }

    fun record(checkpoint: HistoryCheckpoint) {
        writes.trySend(Write.Checkpoint(checkpoint))
    }

    internal suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        writes.send(Write.Barrier(done))
        done.await()
    }

    suspend fun persist(checkpoint: HistoryCheckpoint) =
        database.withTransaction {
            val dao = database.experienceDao()
            val previous = dao.event(checkpoint.event.id)
            // A delayed retry may add missing segments, but cannot regress an event.
            if (
                previous == null ||
                    (!previous.finalized && checkpoint.event.listenedMs >= previous.listenedMs)
            )
                dao.event(checkpoint.event)
            dao.segments(checkpoint.segments)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun stats(period: HistoryPeriod, dayPage: Int = 0) =
        combine(
                database.experienceDao().changes(),
                database.listeningDao().creditChanges(),
                database.listeningDao().overrideChanges(),
            ) { _, _, _ ->
                Unit
            }
            .mapLatest { load(period, dayPage) }

    suspend fun load(
        period: HistoryPeriod,
        dayPage: Int = 0,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ListeningStats =
        withContext(Dispatchers.IO) {
            val since = period.start(now, zone)
            val until = now.toEpochMilli()
            val dao = database.listeningDao()
            val today = now.atZone(zone).toLocalDate()
            val earliest =
                database.experienceDao().earliestListening()?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                } ?: today
            val pageEnd = today.minusDays(dayPage.coerceAtLeast(0) * 30L)
            val periodFirst =
                if (period == HistoryPeriod.ALL) earliest
                else Instant.ofEpochMilli(since).atZone(zone).toLocalDate()
            val first =
                maxOf(periodFirst, pageEnd.minusDays((period.days?.coerceAtMost(30) ?: 30) - 1))
            val days = mutableListOf<ActivityBucket>()
            var day = first
            while (!day.isAfter(pageEnd) && !day.isAfter(today)) {
                val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val end =
                    minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), until)
                days +=
                    ActivityBucket(
                        start,
                        end,
                        day.format(DateTimeFormatter.ofPattern("d MMM")),
                        dao.totals(start, end).listenedMs,
                    )
                day = day.plusDays(1)
            }
            val overview =
                if (period == HistoryPeriod.ALL) {
                    val values = mutableListOf<ActivityBucket>()
                    var month = earliest.withDayOfMonth(1)
                    while (!month.isAfter(today)) {
                        val start = month.atStartOfDay(zone).toInstant().toEpochMilli()
                        val end =
                            minOf(
                                month.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                                until,
                            )
                        values +=
                            ActivityBucket(
                                start,
                                end,
                                month.format(DateTimeFormatter.ofPattern("MMM yy")),
                                dao.totals(start, end).listenedMs,
                            )
                        month = month.plusMonths(1)
                    }
                    values
                } else {
                    val values = mutableListOf<ActivityBucket>()
                    var start = periodFirst
                    while (!start.isAfter(today)) {
                        val end = minOf(start.plusDays(7), today.plusDays(1))
                        val a = start.atStartOfDay(zone).toInstant().toEpochMilli()
                        val b = minOf(end.atStartOfDay(zone).toInstant().toEpochMilli(), until)
                        values +=
                            ActivityBucket(
                                a,
                                b,
                                start.format(DateTimeFormatter.ofPattern("d MMM")),
                                dao.totals(a, b).listenedMs,
                            )
                        start = end
                    }
                    values
                }
            ListeningStats(
                dao.totals(since, until),
                dao.tracks(since, until),
                dao.artists(since, until),
                dao.albums(since, until),
                days,
                overview,
                first > periodFirst,
            )
        }
}
