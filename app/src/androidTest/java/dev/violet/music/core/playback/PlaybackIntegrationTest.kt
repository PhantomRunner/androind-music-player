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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realAudioQueueSeekBackgroundSystemControlsAndReconnect() = runBlocking {
        grantAudio()
        val fixtures = List(2) { TestAudio.create(context) }
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db").build()
        var scenario: ActivityScenario<MainActivity>? = null
        var facade: PlaybackController? = null
        try {
            val writer = RoomCatalogueWriter(database)
            MediaStoreScanner(context).scan { writer.importBatch(it, "playback-fixture-scan") }
            val ids = fixtures.map { requireNotNull(database.libraryDao().findAsset(MediaStore.VOLUME_EXTERNAL_PRIMARY, ContentUris.parseId(it))).trackId }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            facade = withContext(Dispatchers.Main) {
                Media3PlaybackController(context, LocalPlayableSourceResolver(context, database)).also { it.acquire() }
            }
            val player = requireNotNull(facade)
            withContext(Dispatchers.Main) { player.playIds(ids) }
            await(player) { it.isPlaying && it.current?.id == ids[0] && it.durationMs > 0 }
            assertEquals(ids, player.state.value.queue.map { it.id })

            withContext(Dispatchers.Main) { player.seekTo(5_000); player.toggleShuffle(); player.cycleRepeat() }
            await(player) { it.positionMs >= 4_000 && it.shuffle && it.repeat == RepeatMode.ALL }
            withContext(Dispatchers.Main) { player.cycleRepeat() }
            await(player) { it.repeat == RepeatMode.ONE }
            withContext(Dispatchers.Main) { player.playQueueItem(1) }
            await(player) { it.current?.id == ids[1] && it.isPlaying }

            scenario.moveToState(Lifecycle.State.CREATED)
            val before = player.state.value.positionMs
            await(player) { it.positionMs > before + 500 && it.isPlaying }
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
            try {
                val manager = context.getSystemService(MediaSessionManager::class.java)
                val system = manager.getActiveSessions(null).first { it.packageName == context.packageName }
                system.transportControls.pause()
                await(player) { !it.playWhenReady }
                system.transportControls.play()
                await(player) { it.isPlaying }
            } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }

            withContext(Dispatchers.Main) { player.release() }
            val reconnected = withContext(Dispatchers.Main) {
                Media3PlaybackController(context, LocalPlayableSourceResolver(context, database)).also { it.acquire() }
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
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db").build()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val facade = withContext(Dispatchers.Main) { Media3PlaybackController(context, LocalPlayableSourceResolver(context, database)).also { it.acquire() } }
        try {
            withContext(Dispatchers.Main) { facade.stop(); facade.playIds(listOf("missing-test-fixture-uuid")) }
            await(facade) { it.error == PlaybackError.UNAVAILABLE }
            assertTrue(!facade.state.value.isPlaying)
        } finally {
            withContext(Dispatchers.Main) { facade.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
        }
    }

    private suspend fun await(player: PlaybackController, predicate: (PlaybackUiState) -> Boolean) {
        withTimeout(20_000) { player.state.first(predicate) }
    }

    private fun grantAudio() {
        val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use {
            FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
    }
}
