package dev.violet.music.core.media

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.RoomCatalogueWriter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreScannerIntegrationTest {
    @Test
    fun actualMediaStoreAudioIsIndexedAndRescannedWithoutDuplicateIds() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
        else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use {
            FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Violet test fixture ${UUID.randomUUID()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/VioletTestFixtures")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        // Create only this test's own silent fixture. No existing audio is changed.
        val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        val database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { it.write(silentWav()) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            val writer = RoomCatalogueWriter(database)
            val scanner = MediaStoreScanner(context)
            val indexer = LibraryIndexer(scanner, writer)

            indexer.refresh()
            val first = database.libraryDao().findAsset(MediaStore.VOLUME_EXTERNAL_PRIMARY, ContentUris.parseId(uri))
            assertNotNull(first)
            assertEquals(uri.toString(), first?.uri)
            assertEquals("LOCAL", database.libraryDao().findTrack(requireNotNull(first).trackId)?.availability)

            indexer.refresh()
            assertEquals(first.trackId, database.libraryDao().findAsset(MediaStore.VOLUME_EXTERNAL_PRIMARY, ContentUris.parseId(uri))?.trackId)
        } finally {
            database.close()
            resolver.delete(uri, null, null) // Only the fixture URI created above.
        }
    }

    private fun silentWav(): ByteArray {
        val dataSize = 16_000 // One second, 8 kHz, mono, 16-bit PCM silence.
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
            put(ByteArray(dataSize))
        }.array()
    }
}
