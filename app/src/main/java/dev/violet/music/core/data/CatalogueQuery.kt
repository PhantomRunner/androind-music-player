package dev.violet.music.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.violet.music.core.database.EFFECTIVE_CREDIT
import dev.violet.music.core.model.SummarySort
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.model.TrackSort

private const val TRACK_PROJECTION =
    "t.id, t.title, t.artistName, t.albumTitle, t.albumKey, t.durationMs, t.artworkUri, t.availability, t.dateAdded, (SELECT COALESCE(MAX(a.lastModified),0) FROM media_assets a WHERE a.trackId=t.id) AS artworkVersion, EXISTS(SELECT 1 FROM favourites f WHERE f.trackId=t.id) AS isFavourite"

fun usesHistory(filter: TrackFilter) =
    filter.qualifiedOnly ||
        (!filter.recentlyAddedFirst &&
            filter.sort in
                listOf(TrackSort.MOST_PLAYED, TrackSort.LEAST_PLAYED, TrackSort.RECENTLY_PLAYED))

fun trackQuery(
    filter: TrackFilter,
    idsOnly: Boolean = false,
    availableOnly: Boolean = false,
    limit: Int? = filter.limit,
    offset: Int = 0,
    selection: Boolean = false,
): SimpleSQLiteQuery {
    val history = usesHistory(filter)
    val args = mutableListOf<Any>()
    if (history) args += filter.periodStart
    val where = mutableListOf<String>()
    if (filter.query.isNotBlank()) {
        val pattern = searchPattern(filter.query)
        where +=
            "(t.title LIKE ? ESCAPE '!' OR t.artistName LIKE ? ESCAPE '!' OR t.albumTitle LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM artist_credits c JOIN artists a ON a.id=c.artistId WHERE c.trackId=t.id AND $EFFECTIVE_CREDIT AND a.name LIKE ? ESCAPE '!'))"
        repeat(4) { args += pattern }
    }
    filter.albumKey?.let {
        where += "t.albumKey=?"
        args += it
    }
    filter.artistId?.let {
        where +=
            "EXISTS(SELECT 1 FROM artist_credits c WHERE c.trackId=t.id AND c.artistId=? AND c.role='TRACK' AND $EFFECTIVE_CREDIT)"
        args += it
    }
    filter.artistName?.let {
        where += "t.artistName=? COLLATE NOCASE"
        args += it
    }
    if (filter.favouritesOnly) where += "EXISTS(SELECT 1 FROM favourites f WHERE f.trackId=t.id)"
    if (filter.qualifiedOnly) where += "COALESCE(h.plays,0)>0"
    if (availableOnly) where += "t.availability='LOCAL'"
    val order =
        when (if (filter.recentlyAddedFirst) TrackSort.NEWEST else filter.sort) {
            TrackSort.TITLE_ASC -> "t.title COLLATE NOCASE ASC"
            TrackSort.TITLE_DESC -> "t.title COLLATE NOCASE DESC"
            TrackSort.NEWEST -> "t.dateAdded DESC, t.title COLLATE NOCASE"
            TrackSort.OLDEST -> "t.dateAdded ASC, t.title COLLATE NOCASE"
            TrackSort.MOST_PLAYED ->
                "COALESCE(h.plays,0) DESC, h.lastPlayed DESC, t.title COLLATE NOCASE"
            TrackSort.LEAST_PLAYED -> "COALESCE(h.plays,0) ASC, t.title COLLATE NOCASE"
            TrackSort.RECENTLY_PLAYED -> "h.lastPlayed DESC, t.title COLLATE NOCASE"
        }
    val projection =
        if (selection) "t.id AS `key`,t.id AS trackId,(t.availability='LOCAL') AS available"
        else if (idsOnly) "t.id" else TRACK_PROJECTION
    val sql =
        "SELECT $projection FROM tracks t" +
            (if (history)
                " LEFT JOIN (SELECT trackId, COUNT(*) AS plays, MAX(startedAt) AS lastPlayed FROM playback_events WHERE qualified=1 AND startedAt>=? GROUP BY trackId) h ON h.trackId=t.id"
            else "") +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY $order, t.id" +
            (limit?.let { " LIMIT ${it.coerceAtLeast(0)} OFFSET ${offset.coerceAtLeast(0)}" } ?: "")
    return SimpleSQLiteQuery(sql, args.toTypedArray())
}

fun albumQuery(query: String, sort: SummarySort, artistId: String? = null): SimpleSQLiteQuery {
    val conditions = mutableListOf<String>()
    val args = mutableListOf<Any>()
    if (query.isNotBlank()) {
        conditions +=
            "(t.albumTitle LIKE ? ESCAPE '!' OR COALESCE(t.albumArtistName,t.artistName) LIKE ? ESCAPE '!')"
        repeat(2) { args += searchPattern(query) }
    }
    artistId?.let {
        conditions +=
            "EXISTS(SELECT 1 FROM artist_credits c WHERE c.trackId=t.id AND c.artistId=? AND $EFFECTIVE_CREDIT)"
        args += it
    }
    val order = summaryOrder(sort, "title", "MAX(t.dateAdded)", "MIN(t.dateAdded)")
    return SimpleSQLiteQuery(
        "SELECT t.albumKey, MIN(t.albumTitle) AS title, COALESCE(MIN(t.albumArtistName), CASE WHEN COUNT(DISTINCT t.artistName)>1 THEN 'Various artists' ELSE MIN(t.artistName) END) AS artistName, MAX(t.artworkUri) AS artworkUri, COUNT(*) AS trackCount FROM tracks t" +
            (if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")) +
            " GROUP BY t.albumKey ORDER BY $order, t.albumKey",
        args.toTypedArray(),
    )
}

fun artistQuery(query: String, sort: SummarySort): SimpleSQLiteQuery =
    SimpleSQLiteQuery(
        "SELECT a.id, a.name, COUNT(DISTINCT t.id) AS trackCount, COUNT(DISTINCT t.albumKey) AS albumCount, MAX(t.artworkUri) AS artworkUri FROM artists a JOIN artist_credits c ON c.artistId=a.id JOIN tracks t ON t.id=c.trackId WHERE c.role='TRACK' AND $EFFECTIVE_CREDIT AND a.name LIKE ? ESCAPE '!' GROUP BY a.id ORDER BY ${summaryOrder(sort,"a.name","MAX(t.dateAdded)","MIN(t.dateAdded)")}, a.id",
        arrayOf(searchPattern(query).ifEmpty { "%" }),
    )

fun summaryOrder(sort: SummarySort, name: String, newest: String, oldest: String) =
    when (sort) {
        SummarySort.NAME_ASC -> "$name COLLATE NOCASE ASC"
        SummarySort.NAME_DESC -> "$name COLLATE NOCASE DESC"
        SummarySort.NEWEST -> "$newest DESC, $name COLLATE NOCASE"
        SummarySort.OLDEST -> "$oldest ASC, $name COLLATE NOCASE"
    }
