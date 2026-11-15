package dev.violet.music.core.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.room.withTransaction
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.violet.music.core.database.MusicDatabase
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class PlaylistCoverRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    suspend fun apply(id: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content")
            val directory =
                File(context.filesDir, "playlist-covers").apply { check(exists() || mkdirs()) }
            val name = "${UUID.randomUUID()}.jpg"
            val temporary = File(directory, "$name.tmp")
            val target = File(directory, name)
            var committed = false
            try {
                val result =
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(1024)
                            .scale(Scale.FIT)
                            .precision(coil.size.Precision.EXACT)
                            .allowHardware(false)
                            .build()
                    )
                check(result is SuccessResult) { "Image could not be decoded" }
                val bitmap = result.drawable.toBitmap()
                temporary.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it))
                }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(target))
                val previous =
                    database.withTransaction {
                        val playlist = requireNotNull(database.collectionDao().findPlaylist(id))
                        database.collectionDao().cover(id, name, System.currentTimeMillis())
                        playlist.coverFileName
                    }
                committed = true
                removeOwned(previous)
            } finally {
                temporary.delete()
                if (!committed) target.delete()
            }
        }

    suspend fun remove(id: String) =
        withContext(Dispatchers.IO) {
            val previous =
                database.withTransaction {
                    val playlist =
                        database.collectionDao().findPlaylist(id) ?: return@withTransaction null
                    database.collectionDao().cover(id, null, System.currentTimeMillis())
                    playlist.coverFileName
                }
            removeOwned(previous)
        }

    fun removeOwned(name: String?) {
        if (name != null && name.matches(Regex("[a-f0-9-]{36}\\.jpg")))
            File(context.filesDir, "playlist-covers/$name").delete()
    }
}
