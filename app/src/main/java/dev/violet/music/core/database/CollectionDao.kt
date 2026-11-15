package dev.violet.music.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.violet.music.core.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

private const val PLAYLIST_SUMMARY =
    "SELECT p.id, p.name, p.coverFileName, COUNT(e.id) AS trackCount, MAX(t.artworkUri) AS artworkUri " +
        "FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId = p.id LEFT JOIN tracks t ON t.id = e.trackId "

@Dao
interface CollectionDao {
    @Query("SELECT * FROM playlists WHERE id=:id")
    suspend fun findPlaylist(id: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId=:id")
    fun entryChanges(id: String): Flow<Long>

    @Query(
        "UPDATE playlists SET coverFileName=:name,updatedAt=:now,localRevision=localRevision+1 WHERE id=:id"
    )
    suspend fun cover(id: String, name: String?, now: Long)

    @Query(PLAYLIST_SUMMARY + "GROUP BY p.id ORDER BY p.updatedAt DESC, p.id LIMIT 8")
    fun recentPlaylists(): Flow<List<PlaylistSummary>>

    @androidx.room.RawQuery(
        observedEntities = [PlaylistEntity::class, PlaylistEntryEntity::class, TrackEntity::class]
    )
    fun queryPlaylists(
        query: androidx.sqlite.db.SupportSQLiteQuery
    ): PagingSource<Int, PlaylistSummary>

    @Query(PLAYLIST_SUMMARY + "GROUP BY p.id ORDER BY p.updatedAt DESC, p.id")
    fun playlists(): PagingSource<Int, PlaylistSummary>

    @Query(PLAYLIST_SUMMARY + "WHERE p.id = :id GROUP BY p.id")
    fun playlist(id: String): Flow<PlaylistSummary?>

    @Query(
        "SELECT e.id AS entryId, e.position, t.id, t.title, t.artistName, t.albumTitle, t.albumKey, " +
            "t.durationMs, t.artworkUri, t.availability, t.dateAdded, " +
            "(SELECT COALESCE(MAX(a.lastModified),0) FROM media_assets a WHERE a.trackId=t.id) AS artworkVersion, " +
            "EXISTS(SELECT 1 FROM favourites f WHERE f.trackId = t.id) AS isFavourite " +
            "FROM playlist_entries e JOIN tracks t ON t.id = e.trackId WHERE e.playlistId = :id ORDER BY e.position, e.id"
    )
    fun entries(id: String): PagingSource<Int, PlaylistEntryRow>

    @Query(
        "SELECT e.id AS entryId, e.trackId FROM playlist_entries e JOIN tracks t ON t.id = e.trackId " +
            "WHERE e.playlistId = :id AND t.availability = 'LOCAL' ORDER BY e.position, e.id"
    )
    suspend fun queue(id: String): List<PlaylistQueueRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE trackId = :id") suspend fun removeFavourite(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE trackId = :id)")
    suspend fun isFavourite(id: String): Boolean

    @Insert suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query(
        "UPDATE playlists SET name = :name, updatedAt = :now, localRevision = localRevision + 1 WHERE id = :id"
    )
    suspend fun rename(id: String, name: String, now: Long)

    @Query(
        "UPDATE playlists SET updatedAt = :now, localRevision = localRevision + 1 WHERE id = :id"
    )
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: String)

    @Insert suspend fun insertEntry(entry: PlaylistEntryEntity)

    @Query("SELECT * FROM playlist_entries WHERE id = :id")
    suspend fun entry(id: String): PlaylistEntryEntity?

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :id")
    suspend fun entryCount(id: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :id")
    suspend fun nextPosition(id: String): Int

    @Query("DELETE FROM playlist_entries WHERE id = :id") suspend fun deleteEntry(id: String)

    @Query(
        "UPDATE playlist_entries SET position = position - 1 WHERE playlistId = :playlistId AND position > :removed"
    )
    suspend fun compactAfterRemoval(playlistId: String, removed: Int)

    @Query(
        "UPDATE playlist_entries SET position = CASE WHEN :target < :old THEN position + 1 ELSE position - 1 END " +
            "WHERE playlistId = :playlistId AND id != :entryId AND position BETWEEN MIN(:old, :target) AND MAX(:old, :target)"
    )
    suspend fun shiftRange(playlistId: String, entryId: String, old: Int, target: Int)

    @Query("UPDATE playlist_entries SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)
}
