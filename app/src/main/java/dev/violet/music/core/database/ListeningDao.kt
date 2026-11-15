package dev.violet.music.core.database

import androidx.room.Dao
import androidx.room.Query
import dev.violet.music.core.model.ListeningTotals
import dev.violet.music.core.model.RankedListening
import kotlinx.coroutines.flow.Flow

// Event counts and segment time are aggregated independently, never multiplied by a join.
private const val TIME_SUM =
    "COALESCE(SUM(s.listenedMs * 1.0 * (MIN(s.endedAt, :until) - MAX(s.startedAt, :since)) / MAX(1, s.endedAt - s.startedAt)), 0)"
private const val RANK_CTE =
    "WITH plays AS (SELECT trackId, COUNT(*) AS plays FROM playback_events WHERE qualified = 1 AND startedAt >= :since AND startedAt < :until GROUP BY trackId), " +
        "listened AS (SELECT e.trackId, CAST($TIME_SUM AS INTEGER) AS listenedMs FROM listening_segments s JOIN playback_events e ON e.id = s.eventId WHERE s.endedAt > :since AND s.startedAt < :until GROUP BY e.trackId) "

@Dao
interface ListeningDao {
    @Query(
        "SELECT (SELECT COUNT(*) FROM playback_events WHERE qualified = 1 AND startedAt >= :since AND startedAt < :until) AS plays, CAST((SELECT $TIME_SUM FROM listening_segments s WHERE s.endedAt > :since AND s.startedAt < :until) AS INTEGER) AS listenedMs"
    )
    suspend fun totals(since: Long, until: Long): ListeningTotals

    @Query(
        RANK_CTE +
            "SELECT t.id, t.title AS name, COALESCE(p.plays, 0) AS plays, COALESCE(l.listenedMs, 0) AS listenedMs FROM tracks t LEFT JOIN plays p ON p.trackId = t.id LEFT JOIN listened l ON l.trackId = t.id WHERE COALESCE(p.plays,0) > 0 ORDER BY plays DESC, listenedMs DESC, t.title COLLATE NOCASE, t.id LIMIT 20"
    )
    suspend fun tracks(since: Long, until: Long): List<RankedListening>

    @Query(
        RANK_CTE +
            "SELECT a.id, a.name, SUM(COALESCE(p.plays,0)) AS plays, SUM(COALESCE(l.listenedMs,0)) AS listenedMs FROM artists a JOIN artist_credits c ON c.artistId = a.id LEFT JOIN plays p ON p.trackId = c.trackId LEFT JOIN listened l ON l.trackId = c.trackId WHERE c.role = 'TRACK' AND $EFFECTIVE_CREDIT GROUP BY a.id HAVING SUM(COALESCE(p.plays,0)) > 0 OR SUM(COALESCE(l.listenedMs,0)) > 0 ORDER BY plays DESC, listenedMs DESC, a.name COLLATE NOCASE, a.id LIMIT 20"
    )
    suspend fun artists(since: Long, until: Long): List<RankedListening>

    @Query(
        RANK_CTE +
            "SELECT t.albumKey AS id, MIN(t.albumTitle) AS name, SUM(COALESCE(p.plays,0)) AS plays, SUM(COALESCE(l.listenedMs,0)) AS listenedMs FROM tracks t LEFT JOIN plays p ON p.trackId = t.id LEFT JOIN listened l ON l.trackId = t.id GROUP BY t.albumKey HAVING SUM(COALESCE(p.plays,0)) > 0 OR SUM(COALESCE(l.listenedMs,0)) > 0 ORDER BY plays DESC, listenedMs DESC, name COLLATE NOCASE, id LIMIT 20"
    )
    suspend fun albums(since: Long, until: Long): List<RankedListening>

    @Query("SELECT COUNT(*) FROM artist_credits") fun creditChanges(): Flow<Long>

    @Query("SELECT COUNT(*) FROM artist_overrides") fun overrideChanges(): Flow<Long>
}
