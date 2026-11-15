package dev.violet.music.core.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.violet.music.core.data.searchPattern
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.media.AudioPermission
import dev.violet.music.core.model.TrackFilter
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** One resolver for UI requests and service callbacks. Never trusts an incoming URI. */
class LocalPlayableSourceResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    suspend fun queueIds(filter: TrackFilter): List<String> = database.libraryDao().queueIds(
        searchPattern(filter.query), filter.albumKey, filter.artistName, filter.recentlyAddedFirst,
    )

    suspend fun resolve(ids: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!AudioPermission.isGranted(context)) throw SecurityException("Audio permission required")
        val items = ArrayList<MediaItem>(ids.size)
        // Bound SQL parameters and mapping work; only the playback queue retains all items.
        for (batch in ids.chunked(100)) {
            currentCoroutineContext().ensureActive()
            val rows = database.libraryDao().playableTracks(batch).associateBy { it.id }
            for (id in batch) {
                val track = rows[id] ?: throw FileNotFoundException("Track is unavailable")
                val uri = track.uri.toUri()
                if (uri.scheme != "content" || uri.authority != "media") throw FileNotFoundException("Invalid local source")
                items += MediaItem.Builder().setMediaId(id).setUri(uri).setMimeType(track.mimeType)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artistName).setAlbumTitle(track.albumTitle)
                        .setArtworkUri(track.artworkUri?.toUri()).setDurationMs(track.durationMs)
                        .setIsPlayable(true).setIsBrowsable(false).build())
                    .build()
            }
        }
        items
    }

    suspend fun verifyReadable(item: MediaItem) = withContext(Dispatchers.IO) {
        val uri = item.localConfiguration?.uri ?: throw FileNotFoundException("No local source")
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
            ?: throw FileNotFoundException("Track is unavailable")
    }
}
