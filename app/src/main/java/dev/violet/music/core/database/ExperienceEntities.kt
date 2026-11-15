package dev.violet.music.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "artists", indices = [Index(value = ["normalizedName"], unique = true)])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val serverId: String? = null,
)

// Automatic candidates remain stored underneath a manual override, so reset is lossless.
@Entity(
    tableName = "artist_credits",
    primaryKeys = ["trackId", "artistId", "role", "source"],
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["id"],
                childColumns = ["trackId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = ArtistEntity::class,
                parentColumns = ["id"],
                childColumns = ["artistId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("artistId"), Index(value = ["trackId", "role", "source"])],
)
data class ArtistCreditEntity(
    val trackId: String,
    val artistId: String,
    val role: String,
    val source: String,
    val position: Int,
)

@Entity(
    tableName = "artist_overrides",
    primaryKeys = ["trackId", "role"],
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["id"],
                childColumns = ["trackId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
data class ArtistOverrideEntity(val trackId: String, val role: String)

@Entity(tableName = "preferences")
data class PreferenceEntity(@PrimaryKey val key: String, val value: String)

@Entity(
    tableName = "playback_events",
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["id"],
                childColumns = ["trackId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["trackId", "startedAt"]), Index(value = ["qualified", "startedAt"])],
)
data class PlaybackEventEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val startedAt: Long,
    val listenedMs: Long,
    val qualified: Boolean,
    val completed: Boolean,
    val finalized: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "listening_segments",
    primaryKeys = ["eventId", "sequence"],
    foreignKeys =
        [
            ForeignKey(
                entity = PlaybackEventEntity::class,
                parentColumns = ["id"],
                childColumns = ["eventId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["startedAt", "endedAt"])],
)
data class ListeningSegmentEntity(
    val eventId: String,
    val sequence: Int,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
)

const val EFFECTIVE_CREDIT =
    "(c.source = 'MANUAL' OR NOT EXISTS (SELECT 1 FROM artist_overrides o WHERE o.trackId = c.trackId AND o.role = c.role))"
