package dev.violet.music.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.LibraryStats
import dev.violet.music.core.model.Track
import dev.violet.music.core.playback.PlayableTrack
import kotlinx.coroutines.flow.Flow

private const val TRACK_COLUMNS =
    "id, title, artistName, albumTitle, albumKey, durationMs, " +
        "artworkUri, availability, dateAdded, (SELECT COALESCE(MAX(a.lastModified),0) FROM media_assets a WHERE a.trackId=tracks.id) AS artworkVersion, EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id) AS isFavourite"

@Dao
interface LibraryDao {
    @RawQuery
    suspend fun selection(
        query: SupportSQLiteQuery
    ): List<dev.violet.music.core.model.SelectedTrack>

    @Query(
        "SELECT id AS `key`,id AS trackId,(availability='LOCAL') AS available FROM tracks WHERE id IN (:ids)"
    )
    suspend fun identities(ids: List<String>): List<dev.violet.music.core.model.SelectedTrack>

    @Query(
        "SELECT MAX(a.mimeType) AS mimeType, COALESCE(MAX(a.sizeBytes),0) AS sizeBytes, t.albumArtistName FROM tracks t LEFT JOIN media_assets a ON a.trackId=t.id WHERE t.id=:id GROUP BY t.id"
    )
    fun information(id: String): Flow<dev.violet.music.core.model.TrackInformation?>

    @Query("SELECT id FROM tracks WHERE id IN (:ids) AND availability='LOCAL'")
    suspend fun availableIds(ids: List<String>): List<String>

    @RawQuery(
        observedEntities =
            [
                TrackEntity::class,
                FavouriteEntity::class,
                ArtistCreditEntity::class,
                ArtistOverrideEntity::class,
                ArtistEntity::class,
                PlaybackEventEntity::class,
            ]
    )
    fun queryTracks(query: SupportSQLiteQuery): PagingSource<Int, Track>

    @RawQuery(
        observedEntities =
            [
                TrackEntity::class,
                MediaAssetEntity::class,
                FavouriteEntity::class,
                ArtistCreditEntity::class,
                ArtistOverrideEntity::class,
                ArtistEntity::class,
            ]
    )
    fun queryCatalogue(query: SupportSQLiteQuery): PagingSource<Int, Track>

    @RawQuery(
        observedEntities =
            [
                TrackEntity::class,
                FavouriteEntity::class,
                ArtistCreditEntity::class,
                ArtistOverrideEntity::class,
                ArtistEntity::class,
                PlaybackEventEntity::class,
            ]
    )
    fun observeTracks(query: SupportSQLiteQuery): Flow<List<Track>>

    @RawQuery(
        observedEntities =
            [
                TrackEntity::class,
                MediaAssetEntity::class,
                FavouriteEntity::class,
                ArtistCreditEntity::class,
                ArtistOverrideEntity::class,
                ArtistEntity::class,
            ]
    )
    fun observeCatalogue(query: SupportSQLiteQuery): Flow<List<Track>>

    @RawQuery suspend fun selectIds(query: SupportSQLiteQuery): List<String>

    @RawQuery(
        observedEntities =
            [TrackEntity::class, ArtistCreditEntity::class, ArtistOverrideEntity::class]
    )
    fun queryAlbums(query: SupportSQLiteQuery): PagingSource<Int, AlbumSummary>

    @RawQuery(
        observedEntities =
            [
                TrackEntity::class,
                ArtistEntity::class,
                ArtistCreditEntity::class,
                ArtistOverrideEntity::class,
            ]
    )
    fun queryArtists(query: SupportSQLiteQuery): PagingSource<Int, ArtistSummary>

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE " +
            "(:pattern = '' OR title LIKE :pattern ESCAPE '!' OR " +
            "artistName LIKE :pattern ESCAPE '!' OR albumTitle LIKE :pattern ESCAPE '!') " +
            "AND (:albumKey IS NULL OR albumKey = :albumKey) " +
            "AND (:artistName IS NULL OR artistName = :artistName COLLATE NOCASE) " +
            "AND (:favouritesOnly = 0 OR EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id)) " +
            "ORDER BY CASE WHEN :recent THEN dateAdded END DESC, title COLLATE NOCASE, id"
    )
    fun tracks(
        pattern: String,
        albumKey: String?,
        artistName: String?,
        recent: Boolean,
        favouritesOnly: Boolean = false,
    ): PagingSource<Int, Track>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE id = :id") fun track(id: String): Flow<Track?>

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks ORDER BY dateAdded DESC, title COLLATE NOCASE, id LIMIT 8"
    )
    fun recentTracks(): Flow<List<Track>>

    @Query(
        "SELECT COUNT(*) AS trackCount, COUNT(DISTINCT albumKey) AS albumCount, " +
            "(SELECT COUNT(DISTINCT c.artistId) FROM artist_credits c WHERE c.role='TRACK' AND $EFFECTIVE_CREDIT) AS artistCount, " +
            "COALESCE(SUM(availability = 'UNAVAILABLE'), 0) AS unavailableCount FROM tracks"
    )
    fun stats(): Flow<LibraryStats>

    @Query(
        "SELECT albumKey, MIN(albumTitle) AS title, " +
            "CASE WHEN COUNT(DISTINCT artistName COLLATE NOCASE) > 1 THEN 'Various artists' " +
            "ELSE MIN(artistName) END AS artistName, MAX(artworkUri) AS artworkUri, " +
            "COUNT(*) AS trackCount FROM tracks GROUP BY albumKey ORDER BY title COLLATE NOCASE, albumKey"
    )
    fun albums(): PagingSource<Int, AlbumSummary>

    @Query(
        "SELECT a.id, a.name, MAX(t.artworkUri) AS artworkUri, COUNT(DISTINCT t.id) AS trackCount, " +
            "COUNT(DISTINCT t.albumKey) AS albumCount FROM artists a JOIN artist_credits c ON c.artistId = a.id JOIN tracks t ON t.id = c.trackId " +
            "WHERE c.role = 'TRACK' AND $EFFECTIVE_CREDIT GROUP BY a.id ORDER BY a.name COLLATE NOCASE, a.id"
    )
    fun artists(): PagingSource<Int, ArtistSummary>

    @Query("SELECT value FROM scan_state WHERE `key` = 'last_success'") fun lastScan(): Flow<Long?>

    @Query(
        "SELECT * FROM media_assets WHERE volumeName = :volume AND mediaStoreId = :mediaId LIMIT 1"
    )
    suspend fun findAsset(volume: String, mediaId: Long): MediaAssetEntity?

    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun findTrack(id: String): TrackEntity?

    @Query(
        "SELECT id FROM tracks WHERE availability = 'LOCAL' AND " +
            "(:pattern = '' OR title LIKE :pattern ESCAPE '!' OR artistName LIKE :pattern ESCAPE '!' " +
            "OR albumTitle LIKE :pattern ESCAPE '!') " +
            "AND (:albumKey IS NULL OR albumKey = :albumKey) " +
            "AND (:artistName IS NULL OR artistName = :artistName COLLATE NOCASE) " +
            "AND (:favouritesOnly = 0 OR EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = tracks.id)) " +
            "ORDER BY CASE WHEN :recent THEN dateAdded END DESC, title COLLATE NOCASE, id"
    )
    suspend fun queueIds(
        pattern: String,
        albumKey: String?,
        artistName: String?,
        recent: Boolean,
        favouritesOnly: Boolean = false,
    ): List<String>

    @Query(
        "SELECT t.id, t.title, t.artistName, t.albumTitle, t.artworkUri, t.durationMs, a.uri, a.mimeType, a.lastModified AS artworkVersion " +
            "FROM tracks t JOIN media_assets a ON a.trackId = t.id " +
            "WHERE t.id IN (:ids) AND a.available = 1 AND t.availability = 'LOCAL'"
    )
    suspend fun playableTracks(ids: List<String>): List<PlayableTrack>

    @Upsert suspend fun upsertTrack(track: TrackEntity)

    @Upsert suspend fun upsertAsset(asset: MediaAssetEntity)

    @Upsert suspend fun upsertScanState(state: ScanStateEntity)

    // Called ONLY after every query completed, permission and mounted volumes were rechecked.
    @Query("UPDATE media_assets SET available = (lastSeenScanId = :scanId)")
    suspend fun reconcileAssets(scanId: String)

    @Query(
        "UPDATE tracks SET availability = CASE WHEN EXISTS " +
            "(SELECT 1 FROM media_assets a WHERE a.trackId = tracks.id AND a.available = 1) " +
            "THEN 'LOCAL' ELSE 'UNAVAILABLE' END WHERE origin = 'DEVICE'"
    )
    suspend fun reconcileAvailability()
}
