package dev.violet.music.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.violet.music.core.model.Track

@Entity(
    tableName = "favourites",
    foreignKeys = [ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.RESTRICT)],
)
data class FavouriteEntity(@PrimaryKey val trackId: String, val createdAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val localRevision: Long = 1,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["playlistId", "position"]), Index("trackId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
)

data class PlaylistEntryRow(val entryId: String, val position: Int, @Embedded val track: Track)
data class PlaylistQueueRow(val entryId: String, val trackId: String)
