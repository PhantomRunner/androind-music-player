package dev.violet.music.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.violet.music.core.model.ArtistCredit
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperienceDao {
    @Query(
        "SELECT t.* FROM tracks t WHERE t.id>:after AND (t.artistName LIKE '%,%' OR t.artistName LIKE '%&%' OR t.artistName LIKE '% feat%' OR t.artistName LIKE '% ft.%' OR t.artistName LIKE '%;%') AND (SELECT COUNT(*) FROM artist_credits c WHERE c.trackId=t.id AND c.role='TRACK' AND c.source='METADATA')=1 AND NOT EXISTS(SELECT 1 FROM artist_credits c WHERE c.trackId=t.id AND c.source='INFERRED' AND c.role='TRACK') ORDER BY t.id LIMIT 128"
    )
    suspend fun ambiguousTracks(after: String): List<TrackEntity>

    @Query("SELECT * FROM playback_events WHERE trackId=:id ORDER BY startedAt,id")
    suspend fun trackEvents(id: String): List<PlaybackEventEntity>

    @Query("SELECT * FROM preferences") fun preferences(): Flow<List<PreferenceEntity>>

    @Query("SELECT value FROM preferences WHERE `key` = :key")
    fun observePreference(key: String): Flow<String?>

    @Query("SELECT value FROM preferences WHERE `key` = :key")
    suspend fun readPreference(key: String): String?

    @Upsert suspend fun preference(value: PreferenceEntity)

    @Query("SELECT * FROM artists WHERE normalizedName = :name")
    suspend fun artist(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertArtist(artist: ArtistEntity)

    @Query(
        "SELECT a.id, a.name, c.role, c.source, c.position FROM artist_credits c JOIN artists a ON a.id = c.artistId WHERE c.trackId = :id AND $EFFECTIVE_CREDIT ORDER BY c.role DESC, c.position, a.id"
    )
    fun credits(id: String): Flow<List<ArtistCredit>>

    @Query("SELECT name FROM artists WHERE id = :id") fun artistName(id: String): Flow<String?>

    @Query(
        "SELECT normalizedName FROM artists a WHERE EXISTS (SELECT 1 FROM artist_credits c WHERE c.artistId = a.id AND c.source = 'METADATA' AND c.role = 'TRACK')"
    )
    suspend fun knownNames(): List<String>

    @Query("DELETE FROM artist_credits WHERE trackId = :id AND role = :role AND source != 'MANUAL'")
    suspend fun clearAutomatic(id: String, role: String)

    @Query("DELETE FROM artist_credits WHERE trackId = :id AND role = :role AND source = 'MANUAL'")
    suspend fun clearManual(id: String, role: String)

    @Upsert suspend fun credit(credit: ArtistCreditEntity)

    @Upsert suspend fun override(value: ArtistOverrideEntity)

    @Query("DELETE FROM artist_overrides WHERE trackId = :id AND role = :role")
    suspend fun reset(id: String, role: String)

    @Query("SELECT * FROM playback_events WHERE id = :id")
    suspend fun event(id: String): PlaybackEventEntity?

    @Upsert suspend fun event(event: PlaybackEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun segments(values: List<ListeningSegmentEntity>)

    @Query("UPDATE playback_events SET finalized = 1 WHERE finalized = 0")
    suspend fun finalizeInterrupted()

    @Query("SELECT COUNT(*) FROM playback_events") fun changes(): Flow<Long>

    @Query("SELECT MIN(startedAt) FROM listening_segments") suspend fun earliestListening(): Long?
}
