package dev.violet.music

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.feature.library.LibraryViewModel
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CollectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun favouriteCreateAddRenameRecreateAndDeletePlaylist() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission =
            if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
            else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
            .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        val uri = TestAudio.create(context)
        val database =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*dev.violet.music.core.database.ALL_MIGRATIONS)
                .build()
        try {
            compose.activityRule.scenario.recreate()
            lateinit var library: LibraryViewModel
            compose.activityRule.scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
            }
            withTimeout(15_000) {
                library.uiState.first { it.hasPermission && it.scanStatus !is ScanStatus.Running }
            }
            // A file added after app entry needs the same explicit refresh as normal use.
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Scan device music").performClick()
            compose.onNodeWithContentDescription("Go back").performClick()
            val asset =
                withTimeout(15_000) {
                    var found =
                        database
                            .libraryDao()
                            .findAsset(MediaStore.VOLUME_EXTERNAL_PRIMARY, ContentUris.parseId(uri))
                    while (found == null) {
                        delay(100)
                        found =
                            database
                                .libraryDao()
                                .findAsset(
                                    MediaStore.VOLUME_EXTERNAL_PRIMARY,
                                    ContentUris.parseId(uri),
                                )
                    }
                    found
                }
            val track = requireNotNull(database.libraryDao().findTrack(asset.trackId))
            compose.onNode(hasText("Library") and hasClickAction()).performClick()
            compose.onNode(hasText("Tracks") and hasClickAction()).performClick()
            compose.onNodeWithText("Search your library").performTextInput(track.title)
            compose.onNodeWithText("Search your library").performImeAction()
            waitForText(track.title)
            val options = "Options for ${track.title}"
            scrollTo(hasContentDescription(options))
            compose.onNodeWithContentDescription(options).performClick()
            waitForText("Add to Favourites")
            compose.onNodeWithText("Add to Favourites").performClick()
            scrollTo(hasContentDescription(options))
            compose.onNodeWithContentDescription(options).performClick()
            waitForText("Add to playlist")
            compose.onNodeWithText("Add to playlist").performClick()
            compose.onNodeWithText("New playlist").performClick()
            val name = "MVP test ${UUID.randomUUID().toString().take(8)}"
            compose.onNodeWithText("Playlist name").performTextInput(name)
            compose.onNodeWithText("Save").performClick()
            waitForText(name)
            compose.onNodeWithText(name).assertIsDisplayed()
            val playlistId = database.collectionDao().recentPlaylists().first().single { it.name == name }.id
            withTimeout(10_000) {
                while (database.collectionDao().entryCount(playlistId) != 1) delay(50)
            }
            scrollTo(hasContentDescription(options))
            compose.onNodeWithContentDescription(options).performClick()
            waitForText("Add to playlist")
            compose.onNodeWithText("Add to playlist").performClick()
            compose.onNode(hasText(name) and hasAnyAncestor(isDialog())).performClick()
            waitForText("Repeated tracks")
            assertEquals(1, database.collectionDao().entryCount(playlistId))
            compose.onNodeWithText("Add with duplicates").performClick()
            withTimeout(10_000) {
                while (database.collectionDao().entryCount(playlistId) != 2) delay(50)
            }
            scrollTo(hasContentDescription("Playlist options"))
            compose.onNodeWithContentDescription("Playlist options").performClick()
            compose.onNodeWithText("Rename").performClick()
            compose.onNodeWithText("Playlist name").performTextReplacement("$name renamed")
            compose.onNodeWithText("Save").performClick()
            waitForText("$name renamed")
            compose.activityRule.scenario.recreate()
            waitForText("$name renamed")
            assertTrue(database.libraryDao().track(track.id).first()?.isFavourite == true)
            scrollTo(hasContentDescription("Playlist options"))
            compose.onNodeWithContentDescription("Playlist options").performClick()
            compose.onNode(hasText("Delete") and hasClickAction()).performClick()
            compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("$name renamed").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(database.libraryDao().track(track.id).first()?.isFavourite == true)
        } finally {
            database.close()
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
