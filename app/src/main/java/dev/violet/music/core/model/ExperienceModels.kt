package dev.violet.music.core.model

import java.time.Instant
import java.time.ZoneId

enum class HistoryPeriod(val days: Long?) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    ALL(null);

    fun start(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        days?.let {
            now.atZone(zone)
                .toLocalDate()
                .minusDays(it - 1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } ?: 0L
}

enum class LibraryCategory {
    ALL,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    TRACKS,
}

enum class DisplayMode {
    LIST,
    GRID,
}

enum class TrackSort {
    TITLE_ASC,
    TITLE_DESC,
    NEWEST,
    OLDEST,
    MOST_PLAYED,
    LEAST_PLAYED,
    RECENTLY_PLAYED,
}

enum class SummarySort {
    NAME_ASC,
    NAME_DESC,
    NEWEST,
    OLDEST,
}

enum class ArtistRole {
    TRACK,
    ALBUM,
}

enum class CreditSource {
    METADATA,
    INFERRED,
    MANUAL,
}

data class ArtistCredit(
    val id: String,
    val name: String,
    val role: String,
    val source: String,
    val position: Int,
)

data class RankedListening(val id: String, val name: String, val plays: Long, val listenedMs: Long)

data class ListeningTotals(val plays: Long = 0, val listenedMs: Long = 0)

data class ActivityBucket(val start: Long, val end: Long, val label: String, val listenedMs: Long)

data class ListeningStats(
    val totals: ListeningTotals = ListeningTotals(),
    val tracks: List<RankedListening> = emptyList(),
    val artists: List<RankedListening> = emptyList(),
    val albums: List<RankedListening> = emptyList(),
    val days: List<ActivityBucket> = emptyList(),
    val overview: List<ActivityBucket> = emptyList(),
    val hasOlderDays: Boolean = false,
)

data class LibraryPreferences(
    val period: HistoryPeriod = HistoryPeriod.MONTH,
    val modes: Map<LibraryCategory, DisplayMode> = emptyMap(),
    val sorts: Map<LibraryCategory, String> = emptyMap(),
) {
    fun mode(category: LibraryCategory) =
        modes[category]
            ?: if (category in listOf(LibraryCategory.ARTISTS, LibraryCategory.ALBUMS))
                DisplayMode.GRID
            else DisplayMode.LIST

    fun trackSort() =
        sorts[LibraryCategory.TRACKS]?.let { runCatching { TrackSort.valueOf(it) }.getOrNull() }
            ?: TrackSort.TITLE_ASC

    fun summarySort(category: LibraryCategory) =
        sorts[category]?.let { runCatching { SummarySort.valueOf(it) }.getOrNull() }
            ?: if (category == LibraryCategory.PLAYLISTS) SummarySort.NEWEST
            else SummarySort.NAME_ASC
}
