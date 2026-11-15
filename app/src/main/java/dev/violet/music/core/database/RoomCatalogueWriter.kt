package dev.violet.music.core.database

import androidx.room.withTransaction
import dev.violet.music.core.data.ArtistRepository
import dev.violet.music.core.data.resolveArtistCredit
import dev.violet.music.core.media.AudioCreditReader
import dev.violet.music.core.media.CatalogueWriter
import dev.violet.music.core.media.DeviceAudio
import dev.violet.music.core.model.ArtistRole
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomCatalogueWriter
@Inject
constructor(private val database: MusicDatabase, private val creditReader: AudioCreditReader) :
    CatalogueWriter {
    constructor(database: MusicDatabase) : this(database, AudioCreditReader { null })

    override suspend fun importBatch(records: List<DeviceAudio>, scanId: String) {
        val enrichment =
            records.associateWith { record ->
                val asset = database.libraryDao().findAsset(record.volume, record.mediaId)
                if (
                    asset == null ||
                        asset.metadataVersion < 1 ||
                        asset.lastModified != record.lastModified ||
                        asset.sizeBytes != record.sizeBytes
                )
                    creditReader.read(record.contentUri)
                else null
            }
        database.withTransaction {
            val dao = database.libraryDao()
            val artists = ArtistRepository(database)
            val known = database.experienceDao().knownNames().toSet()
            for (record in records) {
                val asset = dao.findAsset(record.volume, record.mediaId)
                val existing = asset?.let { dao.findTrack(it.trackId) }
                val trackId = existing?.id ?: UUID.randomUUID().toString()
                val embedded = enrichment[record]
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
                        albumArtistName =
                            record.albumArtistName
                                ?: if (embedded != null)
                                    embedded.albumArtists
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(", ")
                                else existing?.albumArtistName,
                    )
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
                        metadataVersion =
                            if (enrichment[record] != null) 1
                            else if (
                                asset?.lastModified == record.lastModified &&
                                    asset.sizeBytes == record.sizeBytes
                            )
                                asset.metadataVersion
                            else 0,
                    )
                )
                if (embedded != null || existing == null || existing.artistName != record.artist) {
                    artists.automatic(
                        trackId,
                        ArtistRole.TRACK,
                        resolveArtistCredit(record.artist, embedded?.trackArtists.orEmpty(), known),
                    )
                }
                if (
                    embedded != null ||
                        (record.albumArtistName != null &&
                            record.albumArtistName != existing?.albumArtistName)
                ) {
                    artists.automatic(
                        trackId,
                        ArtistRole.ALBUM,
                        resolveArtistCredit(
                            record.albumArtistName,
                            embedded?.albumArtists.orEmpty(),
                            known,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun completeScan(scanId: String, completedAt: Long): Unit =
        withContext(Dispatchers.IO) {
            val artistRepository = ArtistRepository(database)
            val known = database.experienceDao().knownNames().toSet()
            var after = ""
            while (true) {
                val candidates = database.experienceDao().ambiguousTracks(after)
                if (candidates.isEmpty()) break
                for (track in candidates) {
                    val resolved = resolveArtistCredit(track.artistName, emptyList(), known)
                    if (resolved.source == dev.violet.music.core.model.CreditSource.INFERRED)
                        artistRepository.automatic(track.id, ArtistRole.TRACK, resolved)
                }
                after = candidates.last().id
            }
            database.withTransaction {
                database.libraryDao().apply {
                    reconcileAssets(scanId)
                    reconcileAvailability()
                    upsertScanState(ScanStateEntity("last_success", completedAt))
                }
            }
            Unit
        }
}
