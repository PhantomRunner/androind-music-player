package dev.violet.music.core.database

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.data.PlaylistCoverRepository
import dev.violet.music.core.data.RoomCollectionRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PlaylistCoverIntegrationTest {
    @Test
    fun privateCoverPersistsAndFailedReplacementKeepsPreviousImageAndOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cover-fixture-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
        val uri =
            requireNotNull(
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "Violet cover test fixture ${UUID.randomUUID()}.png",
                        )
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VioletTestFixtures")
                    },
                )
            )
        var coverName: String? = null
        try {
            val bitmap =
                Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(0xFFFFFFFF.toInt())
                }
            requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
            val tint =
                dev.violet.music.core.data.ArtworkColorRepository(context).color(uri.toString())
            assertTrue(
                androidx.core.graphics.ColorUtils.calculateContrast(0xFFA78BFA.toInt(), tint) >= 4.5
            )
            assertTrue(
                androidx.core.graphics.ColorUtils.calculateContrast(0xFFA9ABBA.toInt(), tint) >= 4.5
            )
            val original =
                requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val id = RoomCollectionRepository(db).create("Cover fixture")
            PlaylistCoverRepository(context, db).apply(id, uri)
            coverName = requireNotNull(db.collectionDao().findPlaylist(id)?.coverFileName)
            val file = File(context.filesDir, "playlist-covers/$coverName")
            assertTrue(file.isFile)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertTrue(bounds.outWidth <= 1024 && bounds.outHeight <= 1024)
            db.close()
            db = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
            assertEquals(coverName, db.collectionDao().findPlaylist(id)?.coverFileName)
            val result = runCatching {
                PlaylistCoverRepository(context, db)
                    .apply(
                        id,
                        android.net.Uri.parse(
                            "content://media/external/images/media/9223372036854775807"
                        ),
                    )
            }
            assertTrue(result.isFailure)
            assertEquals(coverName, db.collectionDao().findPlaylist(id)?.coverFileName)
            assertTrue(file.isFile)
            assertArrayEquals(
                original,
                requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() },
            )
            PlaylistCoverRepository(context, db).remove(id)
            assertNull(db.collectionDao().findPlaylist(id)?.coverFileName)
            assertFalse(file.exists())
        } finally {
            coverName?.let { File(context.filesDir, "playlist-covers/$it").delete() }
            db.close()
            context.deleteDatabase(name)
            context.contentResolver.delete(uri, null, null)
        }
    }
}
