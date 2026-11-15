package dev.violet.music.core.playback

import android.content.ContentUris
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
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
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun shuffleReorderPreservesOccurrenceIdentityPositionAndActualNextItem() = runBlocking {
        grantAudio()
        val fixtures = List(3) { TestAudio.create(context) }
        val database =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                .build()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val facade =
            withContext(Dispatchers.Main) {
                Media3PlaybackController(context, LocalPlayableSourceResolver(context, database))
                    .also { it.acquire() }
            }
        try {
            val writer = RoomCatalogueWriter(database)
            MediaStoreScanner(context).scan { writer.importBatch(it, "queue-occurrence-fixture") }
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
            repeat(4) {
                withContext(Dispatchers.Main) {
                    facade.playIds(listOf(ids[0], ids[0], ids[1], ids[2]))
                }
                await(facade, "start") {
                    it.isPlaying &&
                        it.queue.size == 4 &&
                        it.queue.all { item -> !item.occurrenceId.startsWith("pending:") }
                }
                assertEquals(4, facade.state.value.queue.map { it.occurrenceId }.distinct().size)
                withContext(Dispatchers.Main) {
                    if (!facade.state.value.shuffle) facade.toggleShuffle()
                }
                await(facade, "shuffle") { it.shuffle }
                val before = facade.state.value
                val moving = before.queue.last().occurrenceId
                val target = before.queue.first().occurrenceId
                val expected =
                    requireNotNull(movedOrder(before.queue.map { it.occurrenceId }, moving, target))
                withContext(Dispatchers.Main) { facade.moveOccurrence(moving, target) }
                await(facade, "reorder") { it.queue.map { q -> q.occurrenceId } == expected }
                assertTrue(facade.state.value.shuffle)
                assertEquals(before.current?.occurrenceId, facade.state.value.current?.occurrenceId)
                assertTrue(facade.state.value.positionMs + 500 >= before.positionMs)
                withContext(Dispatchers.Main) { facade.enqueue(listOf(ids[2]), next = true) }
                val previousOccurrences = before.queue.map { it.occurrenceId }.toSet()
                await(facade, "play-next") { state ->
                    state.queue.size == 5 &&
                        state.queue.getOrNull(state.currentIndex + 1)?.let {
                            it.id == ids[2] && it.occurrenceId !in previousOccurrences
                        } == true
                }
                val next = facade.state.value.queue[facade.state.value.currentIndex + 1]
                assertEquals(ids[2], next.id)
                val afterInsert = facade.state.value.queue.map { it.occurrenceId }
                withContext(Dispatchers.Main) {
                    facade.next()
                    facade.moveOccurrence(afterInsert.first(), afterInsert.last())
                }
                await(facade, "next-and-move") {
                    it.current?.occurrenceId == next.occurrenceId &&
                        it.queue.map { q -> q.occurrenceId } ==
                            movedOrder(afterInsert, afterInsert.first(), afterInsert.last())
                }
                assertTrue(facade.state.value.shuffle)
                assertEquals(5, facade.state.value.queue.map { it.occurrenceId }.distinct().size)
                withContext(Dispatchers.Main) { facade.stop() }
            }
        } finally {
            withContext(Dispatchers.Main) { facade.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
            fixtures.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test
    fun serviceHistoryRecordsShortListeningSeparatelyFromQualifiedAttemptsAndIgnoresPause() =
        runBlocking {
            grantAudio()
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            val previousVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0)
            val uri = TestAudio.create(context, seconds = 4)
            val database =
                Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                    .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                    .build()
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            val facade =
                withContext(Dispatchers.Main) {
                    Media3PlaybackController(
                            context,
                            LocalPlayableSourceResolver(context, database),
                        )
                        .also { it.acquire() }
                }
            try {
                val writer = RoomCatalogueWriter(database)
                MediaStoreScanner(context).scan {
                    writer.importBatch(it, "history-service-fixture")
                }
                val id =
                    requireNotNull(
                            database
                                .libraryDao()
                                .findAsset(
                                    MediaStore.VOLUME_EXTERNAL_PRIMARY,
                                    ContentUris.parseId(uri),
                                )
                        )
                        .trackId
                withContext(Dispatchers.Main) { facade.playIds(listOf(id)) }
                await(facade) { it.isPlaying && it.positionMs > 300 }
                withContext(Dispatchers.Main) { facade.stop() }
                withTimeout(10_000) {
                    while (
                        database.experienceDao().trackEvents(id).none {
                            it.finalized && it.listenedMs > 0
                        }
                    ) delay(50)
                }
                val short = database.experienceDao().trackEvents(id).single()
                assertFalse(short.qualified)
                assertTrue(short.listenedMs > 0)
                withContext(Dispatchers.Main) { facade.playIds(listOf(id)) }
                await(facade) { it.isPlaying && it.positionMs > 400 }
                withContext(Dispatchers.Main) { facade.togglePlay() }
                await(facade) { !it.isPlaying }
                delay(1_000)
                val paused = database.experienceDao().trackEvents(id).last()
                assertFalse(paused.qualified)
                withContext(Dispatchers.Main) { facade.togglePlay() }
                await(facade) { it.isPlaying }
                withTimeout(10_000) {
                    while (
                        database.experienceDao().trackEvents(id).none {
                            it.id != short.id && it.qualified
                        }
                    ) delay(50)
                }
                withContext(Dispatchers.Main) { facade.stop() }
                withTimeout(10_000) {
                    while (database.experienceDao().trackEvents(id).any { !it.finalized }) delay(50)
                }
                val events = database.experienceDao().trackEvents(id)
                assertEquals(2, events.size)
                assertEquals(1, events.count { it.qualified })
                val totals = database.listeningDao().totals(0, System.currentTimeMillis() + 1)
                assertTrue(totals.listenedMs >= events.sumOf { it.listenedMs })
                assertTrue(events.last().listenedMs < 3_500)
            } finally {
                withContext(Dispatchers.Main) { facade.release() }
                scenario.close()
                context.stopService(Intent(context, PlaybackService::class.java))
                database.close()
                context.contentResolver.delete(uri, null, null)
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, previousVolume, 0)
            }
        }

    @Test
    fun realAudioQueueSeekBackgroundSystemControlsAndReconnect() = runBlocking {
        grantAudio()
        val fixtures = List(2) { TestAudio.create(context) }
        val database =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                .build()
        var scenario: ActivityScenario<MainActivity>? = null
        var facade: PlaybackController? = null
        try {
            val writer = RoomCatalogueWriter(database)
            MediaStoreScanner(context).scan { writer.importBatch(it, "playback-fixture-scan") }
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
            facade =
                withContext(Dispatchers.Main) {
                    Media3PlaybackController(
                            context,
                            LocalPlayableSourceResolver(context, database),
                        )
                        .also { it.acquire() }
                }
            val player = requireNotNull(facade)
            withContext(Dispatchers.Main) { player.playIds(ids) }
            await(player) { it.isPlaying && it.current?.id == ids[0] && it.durationMs > 0 }
            assertEquals(ids, player.state.value.queue.map { it.id })

            withContext(Dispatchers.Main) {
                player.seekTo(5_000)
                player.toggleShuffle()
                player.cycleRepeat()
            }
            await(player) { it.positionMs >= 4_000 && it.shuffle && it.repeat == RepeatMode.ALL }
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.ONE }
            // Queue rows use the actual shuffled traversal, so select the intended occurrence.
            withContext(Dispatchers.Main) {
                player.playOccurrence(
                    player.state.value.queue.first { it.id == ids[1] }.occurrenceId
                )
            }
            await(player) { it.current?.id == ids[1] && it.isPlaying }

            scenario.moveToState(Lifecycle.State.CREATED)
            val before = player.state.value.positionMs
            await(player) { it.positionMs > before + 500 && it.isPlaying }
            instrumentation.uiAutomation.adoptShellPermissionIdentity(
                "android.permission.MEDIA_CONTENT_CONTROL"
            )
            try {
                val manager = context.getSystemService(MediaSessionManager::class.java)
                val system =
                    manager.getActiveSessions(null).first { it.packageName == context.packageName }
                system.transportControls.pause()
                await(player) { !it.playWhenReady }
                system.transportControls.play()
                await(player) { it.isPlaying }
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }

            withContext(Dispatchers.Main) { player.release() }
            val reconnected =
                withContext(Dispatchers.Main) {
                    Media3PlaybackController(
                            context,
                            LocalPlayableSourceResolver(context, database),
                        )
                        .also { it.acquire() }
                }
            facade = reconnected
            await(reconnected) { it.connected && it.isPlaying && it.current?.id == ids[1] }
            withContext(Dispatchers.Main) { reconnected.removeQueueItem(0) }
            await(reconnected) { it.queue.size == 1 }
            withContext(Dispatchers.Main) { reconnected.stop() }
            await(reconnected) { it.queue.isEmpty() && !it.isPlaying }
        } finally {
            withContext(Dispatchers.Main) { facade?.release() }
            scenario?.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
            fixtures.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test
    fun unknownTrackFailsWithoutStartingPlayback() = runBlocking {
        grantAudio()
        val database =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                .build()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val facade =
            withContext(Dispatchers.Main) {
                Media3PlaybackController(context, LocalPlayableSourceResolver(context, database))
                    .also { it.acquire() }
            }
        try {
            withContext(Dispatchers.Main) {
                facade.stop()
                facade.playIds(listOf("missing-test-fixture-uuid"))
            }
            await(facade) { it.error == PlaybackError.UNAVAILABLE }
            assertTrue(!facade.state.value.isPlaying)
        } finally {
            withContext(Dispatchers.Main) { facade.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
        }
    }

    private suspend fun await(
        player: PlaybackController,
        label: String = "state",
        predicate: (PlaybackUiState) -> Boolean,
    ) {
        try {
            withTimeout(20_000) { player.state.first(predicate) }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            val state = player.state.value
            throw AssertionError(
                "Playback condition $label timed out: connected=${state.connected}, playing=${state.isPlaying}, shuffle=${state.shuffle}, index=${state.currentIndex}, source order=${state.queue.map {it.sourceIndex}}, repeat=${state.repeat}, error=${state.error}",
                error,
            )
        }
    }

    private fun grantAudio() {
        val permission =
            if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
            else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
            .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }
}
