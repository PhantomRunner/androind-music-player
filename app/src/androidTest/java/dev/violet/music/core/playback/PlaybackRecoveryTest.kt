package dev.violet.music.core.playback

import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.MainActivity
import dev.violet.music.TestAudio
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.RoomCatalogueWriter
import dev.violet.music.core.media.MediaStoreScanner
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.feature.library.LibraryViewModel
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PlaybackRecoveryTest {
    @Test
    fun duplicateSelectionMissingMiddleAndAllMissingRepeatQueueRecoverSafely() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission =
            if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
            else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
            .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        val fixtures = List(3) { TestAudio.create(context, seconds = 60) }
        val remaining = fixtures.toMutableSet()
        val database =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                .build()
        var scenario: ActivityScenario<MainActivity>? = null
        var facade: PlaybackController? = null
        try {
            val writer = RoomCatalogueWriter(database)
            MediaStoreScanner(context).scan { writer.importBatch(it, "recovery-fixture-scan") }
            val ids =
                fixtures.map {
                    requireNotNull(
                            database
                                .libraryDao()
                                .findAsset(
                                    MediaStore.VOLUME_EXTERNAL_PRIMARY,
                                    ContentUris.parseId(it),
                                )
                        )
                        .trackId
                }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            lateinit var library: LibraryViewModel
            scenario.onActivity { library = ViewModelProvider(it)[LibraryViewModel::class.java] }
            withTimeout(20_000) { library.uiState.first { it.scanStatus is ScanStatus.Complete } }
            val player =
                withContext(Dispatchers.Main) {
                    Media3PlaybackController(
                            context,
                            LocalPlayableSourceResolver(context, database),
                        )
                        .also { it.acquire() }
                }
            facade = player
            withContext(Dispatchers.Main) { player.playIds(listOf(ids[0], ids[2], ids[0]), 2) }
            await(player) { it.isPlaying && it.currentIndex == 2 }
            assertEquals(listOf(ids[0], ids[2], ids[0]), player.state.value.queue.map { it.id })

            withContext(Dispatchers.Main) { player.playIds(ids) }
            await(player) { it.isPlaying && it.current?.id == ids[0] }
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.ALL }
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.ONE }
            context.contentResolver.delete(fixtures[1], null, null)
            remaining.remove(fixtures[1])
            withContext(Dispatchers.Main) { player.playQueueItem(1) }
            await(player) { it.isPlaying && it.current?.id == ids[2] }
            assertEquals(RepeatMode.ONE, player.state.value.repeat)

            context.contentResolver.delete(fixtures[0], null, null)
            remaining.remove(fixtures[0])
            context.contentResolver.delete(fixtures[2], null, null)
            remaining.remove(fixtures[2])
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.OFF }
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.ALL }
            withContext(Dispatchers.Main) { player.playQueueItem(0) }
            await(player) { it.error == PlaybackError.PLAYBACK && !it.playWhenReady }
            delay(700)
            assertFalse(player.state.value.isPlaying)
            assertEquals(3, player.state.value.queue.size)
            ids.forEach { assertNotNull(database.libraryDao().findTrack(it)) }
        } finally {
            withContext(Dispatchers.Main) { facade?.release() }
            scenario?.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
            remaining.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    private suspend fun await(player: PlaybackController, predicate: (PlaybackUiState) -> Boolean) {
        withTimeout(20_000) { player.state.first(predicate) }
    }
}
