package dev.violet.music.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.LibraryStats
import dev.violet.music.core.model.Track
import dev.violet.music.core.playback.PlayableTrack
import kotlinx.coroutines.flow.Flow

private const val TRACK_COLUMNS = "id, title, artistName, albumTitle, albumKey, durationMs, " +
    "artworkUri, availability, dateAdded, EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id) AS isFavourite"

@Dao
interface LibraryDao {
    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE " +
            "(:pattern = '' OR title LIKE :pattern ESCAPE '!' OR " +
            "artistName LIKE :pattern ESCAPE '!' OR albumTitle LIKE :pattern ESCAPE '!') " +
            "AND (:albumKey IS NULL OR albumKey = :albumKey) " +
            "AND (:artistName IS NULL OR artistName = :artistName COLLATE NOCASE) " +
            "AND (:favouritesOnly = 0 OR EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id)) " +
            "ORDER BY CASE WHEN :recent THEN dateAdded END DESC, title COLLATE NOCASE, id",
    )
    fun tracks(pattern: String, albumKey: String?, artistName: String?, recent: Boolean, favouritesOnly: Boolean = false): PagingSource<Int, Track>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE id = :id")
    fun track(id: String): Flow<Track?>

    @Query("SELECT $TRACK_COLUMNS FROM tracks ORDER BY dateAdded DESC, title COLLATE NOCASE, id LIMIT 8")
    fun recentTracks(): Flow<List<Track>>

    @Query(
        "SELECT COUNT(*) AS trackCount, COUNT(DISTINCT albumKey) AS albumCount, " +
            "COUNT(DISTINCT artistName COLLATE NOCASE) AS artistCount, " +
            "COALESCE(SUM(availability = 'UNAVAILABLE'), 0) AS unavailableCount FROM tracks",
    )
    fun stats(): Flow<LibraryStats>

    @Query(
        "SELECT albumKey, MIN(albumTitle) AS title, " +
            "CASE WHEN COUNT(DISTINCT artistName COLLATE NOCASE) > 1 THEN 'Various artists' " +
            "ELSE MIN(artistName) END AS artistName, MAX(artworkUri) AS artworkUri, " +
            "COUNT(*) AS trackCount FROM tracks GROUP BY albumKey ORDER BY title COLLATE NOCASE, albumKey",
    )
    fun albums(): PagingSource<Int, AlbumSummary>

    @Query(
        "SELECT MIN(artistName) AS name, COUNT(*) AS trackCount, " +
            "COUNT(DISTINCT albumKey) AS albumCount FROM tracks " +
            "GROUP BY artistName COLLATE NOCASE ORDER BY name COLLATE NOCASE",
    )
    fun artists(): PagingSource<Int, ArtistSummary>

    @Query("SELECT value FROM scan_state WHERE `key` = 'last_success'")
    fun lastScan(): Flow<Long?>

    @Query("SELECT * FROM media_assets WHERE volumeName = :volume AND mediaStoreId = :mediaId LIMIT 1")
    suspend fun findAsset(volume: String, mediaId: Long): MediaAssetEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findTrack(id: String): TrackEntity?

    @Query(
        "SELECT id FROM tracks WHERE availability = 'LOCAL' AND " +
            "(:pattern = '' OR title LIKE :pattern ESCAPE '!' OR artistName LIKE :pattern ESCAPE '!' " +
            "OR albumTitle LIKE :pattern ESCAPE '!') " +
            "AND (:albumKey IS NULL OR albumKey = :albumKey) " +
            "AND (:artistName IS NULL OR artistName = :artistName COLLATE NOCASE) " +
            "AND (:favouritesOnly = 0 OR EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id)) " +
            "ORDER BY CASE WHEN :recent THEN dateAdded END DESC, title COLLATE NOCASE, id",
    )
    suspend fun queueIds(pattern: String, albumKey: String?, artistName: String?, recent: Boolean, favouritesOnly: Boolean = false): List<String>

    @Query(
        "SELECT t.id, t.title, t.artistName, t.albumTitle, t.artworkUri, t.durationMs, a.uri, a.mimeType " +
            "FROM tracks t JOIN media_assets a ON a.trackId = t.id " +
            "WHERE t.id IN (:ids) AND a.available = 1 AND t.availability = 'LOCAL'",
    )
    suspend fun playableTracks(ids: List<String>): List<PlayableTrack>

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    suspend fun upsertAsset(asset: MediaAssetEntity)

    @Upsert
    suspend fun upsertScanState(state: ScanStateEntity)

    // Called ONLY after every query completed, permission and mounted volumes were rechecked.
    @Query("UPDATE media_assets SET available = (lastSeenScanId = :scanId)")
    suspend fun reconcileAssets(scanId: String)

    @Query(
        "UPDATE tracks SET availability = CASE WHEN EXISTS " +
            "(SELECT 1 FROM media_assets a WHERE a.trackId = tracks.id AND a.available = 1) " +
            "THEN 'LOCAL' ELSE 'UNAVAILABLE' END WHERE origin = 'DEVICE'",
    )
    suspend fun reconcileAvailability()
}
