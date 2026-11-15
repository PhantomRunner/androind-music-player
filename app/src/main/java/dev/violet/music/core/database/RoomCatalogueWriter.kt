package dev.violet.music.core.database

import androidx.room.withTransaction
import dev.violet.music.core.media.CatalogueWriter
import dev.violet.music.core.media.DeviceAudio
import java.util.UUID
import javax.inject.Inject

class RoomCatalogueWriter @Inject constructor(private val database: MusicDatabase) : CatalogueWriter {
    override suspend fun importBatch(records: List<DeviceAudio>, scanId: String) {
        database.withTransaction {
            val dao = database.libraryDao()
            for (record in records) {
                val asset = dao.findAsset(record.volume, record.mediaId)
                val existing = asset?.let { dao.findTrack(it.trackId) }
                val trackId = existing?.id ?: UUID.randomUUID().toString()
                dao.upsertTrack(
                    TrackEntity(
                        id = trackId,
                        serverId = existing?.serverId,
                        title = record.title,
                        artistName = record.artist,
                        albumTitle = record.album,
                        albumKey = record.albumKey,
                        durationMs = record.durationMs,
                        artworkUri = record.artworkUri,
                        dateAdded = record.dateAdded,
                    ),
                )
                dao.upsertAsset(
                    MediaAssetEntity(
                        id = asset?.id ?: UUID.randomUUID().toString(),
                        trackId = trackId,
                        volumeName = record.volume,
                        mediaStoreId = record.mediaId,
                        uri = record.contentUri,
                        available = true,
                        lastSeenScanId = scanId,
                        sizeBytes = record.sizeBytes,
                        mimeType = record.mimeType,
                        lastModified = record.lastModified,
                    ),
                )
            }
        }
    }

    override suspend fun completeScan(scanId: String, completedAt: Long) {
        database.withTransaction {
            database.libraryDao().apply {
                reconcileAssets(scanId)
                reconcileAvailability()
                upsertScanState(ScanStateEntity("last_success", completedAt))
            }
        }
    }
}
