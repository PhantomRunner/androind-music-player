package dev.violet.music.core.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MediaStoreScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceScanner {
    override suspend fun scan(onBatch: suspend (List<DeviceAudio>) -> Unit) = withContext(Dispatchers.IO) {
        checkPermission()
        val volumes = mountedVolumes()
        if (volumes.isEmpty()) throw StorageChangedException()
        for (volume in volumes.sorted()) {
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(volume)
            } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            query(collection).use { cursor ->
                // A null cursor is a failed query, never an empty successful scan.
                checkNotNull(cursor) { "Media provider returned no cursor" }
                val columns = Columns(cursor)
                val batch = ArrayList<DeviceAudio>(BATCH_SIZE)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    batch += columns.read(cursor, volume, collection)
                    if (batch.size == BATCH_SIZE) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onBatch(batch.toList())
            }
        }
        checkPermission()
        if (volumes != mountedVolumes()) throw StorageChangedException()
    }

    private fun checkPermission() {
        if (!AudioPermission.isGranted(context)) throw SecurityException("Audio access required")
    }

    private fun mountedVolumes(): Set<String> = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.getExternalVolumeNames(context)
    } else {
        when (Environment.getExternalStorageState()) {
            Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY -> setOf("external")
            else -> emptySet()
        }
    }

    private suspend fun query(collection: Uri): Cursor? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            val cursor = context.contentResolver.query(
                collection, PROJECTION, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media._ID} ASC", signal,
            )
            continuation.resume(cursor) { _, result, _ -> result?.close() }
        } catch (error: Exception) {
            continuation.resumeWith(Result.failure(error))
        }
    }

    private class Columns(cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        private val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        private val displayName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        private val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        private val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        private val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        private val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        private val added = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        private val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        private val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        private val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

        fun read(cursor: Cursor, volume: String, collection: Uri): DeviceAudio {
            val mediaId = cursor.getLong(id)
            val albumMediaId = cursor.getLong(albumId)
            val albumCollection = "content://media/$volume/audio/albumart".toUri()
            return DeviceAudio(
                volume = volume,
                mediaId = mediaId,
                contentUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                title = normalizeMetadata(cursor.getString(title))
                    ?: normalizeMetadata(cursor.getString(displayName))?.substringBeforeLast('.')
                    ?: "Untitled track",
                artist = normalizeMetadata(cursor.getString(artist)) ?: "Unknown artist",
                album = normalizeMetadata(cursor.getString(album)) ?: "Unknown album",
                albumKey = "$volume:$albumMediaId",
                artworkUri = albumMediaId.takeIf { it > 0 }?.let {
                    ContentUris.withAppendedId(albumCollection, it).toString()
                },
                durationMs = cursor.getLong(duration).coerceAtLeast(0),
                dateAdded = cursor.getLong(added).coerceAtLeast(0),
                lastModified = cursor.getLong(modified).coerceAtLeast(0),
                sizeBytes = cursor.getLong(size).coerceAtLeast(0),
                mimeType = cursor.getString(mime),
            )
        }
    }

    companion object {
        // Only metadata is buffered; the cursor owns its bounded platform CursorWindow.
        const val BATCH_SIZE = 128
        private val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
        )
    }
}
