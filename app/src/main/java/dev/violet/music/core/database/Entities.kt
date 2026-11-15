package dev.violet.music.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index("title"), Index("artistName"), Index("albumTitle"), Index("albumKey")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val albumKey: String,
    val durationMs: Long,
    val artworkUri: String?,
    val origin: String = "DEVICE",
    val availability: String = "LOCAL",
    val dateAdded: Long,
)

@Entity(
    tableName = "media_assets",
    foreignKeys = [ForeignKey(
        entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("trackId"), Index(value = ["volumeName", "mediaStoreId"], unique = true)],
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val volumeName: String,
    val mediaStoreId: Long,
    val uri: String,
    val available: Boolean,
    val lastSeenScanId: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModified: Long,
)

@Entity(tableName = "scan_state")
data class ScanStateEntity(@PrimaryKey val key: String, val value: Long)
