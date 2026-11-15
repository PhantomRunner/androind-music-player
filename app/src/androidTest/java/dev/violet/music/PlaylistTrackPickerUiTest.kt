package dev.violet.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.database.ALL_MIGRATIONS
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.RoomCatalogueWriter
import dev.violet.music.core.media.DeviceAudio
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistTrackPickerUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun emptyPlaylistSelectTracksOpensCatalogueAndPreservesSelectionAcrossRecreation() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database =
                Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
            val volume = "playlist-picker-fixture"
            val prefix = "Playlist picker fixture"
            var playlistId: String? = null
            val ids = mutableListOf<String>()
            try {
                RoomCatalogueWriter(database)
                    .importBatch(
                        (0 until 65).map { index ->
                            DeviceAudio(
                                volume,
                                index.toLong(),
                                "content://media/$volume/audio/media/$index",
                                "$prefix ${index.toString().padStart(3, '0')}",
                                "Picker test artist",
                                "Picker test album",
                                "$volume:album",
                                null,
                                60_000,
                                index.toLong(),
                                1,
                                1024,
                                "audio/mpeg",
                            )
                        },
                        volume,
                    )
                for (index in 0 until 65) ids +=
                    requireNotNull(database.libraryDao().findAsset(volume, index.toLong())).trackId

                compose.onNode(hasText("Library") and hasClickAction()).performClick()
                compose.onNode(hasText("Playlists") and hasClickAction()).performClick()
                compose.onNodeWithContentDescription("New playlist").performClick()
                val name = "Picker test ${UUID.randomUUID().toString().take(8)}"
                compose.onNodeWithText("Playlist name").performTextInput(name)
                compose.onNodeWithText("Save").performClick()
                waitForText(name)
                playlistId =
                    database.collectionDao().recentPlaylists().first().single { it.name == name }.id
                val id = requireNotNull(playlistId)

                scrollTo(hasText("Select tracks"))
                compose.onNodeWithText("Select tracks").performClick()
                waitForText("Search tracks to add")
                compose.onNodeWithText("Search tracks to add").performTextReplacement("$prefix 000")
                compose.onNodeWithText("Search tracks to add").performImeAction()
                waitForSelectionReady()
                val firstTrack = hasText("$prefix 000") and !hasSetTextAction()
                scrollTo(firstTrack)
                compose.onNode(firstTrack).performClick()
                waitForText("Add selected (1)")
                compose.onNodeWithText("Cancel").performClick()
                waitForPickerToClose()
                assertEquals(0, database.collectionDao().entryCount(id))

                scrollTo(hasText("Select tracks"))
                compose.onNodeWithText("Select tracks").performClick()
                waitForText("Search tracks to add")
                compose.onNodeWithText("Search tracks to add").performTextReplacement(prefix)
                compose.onNodeWithText("Search tracks to add").performImeAction()
                waitForSelectionReady()
                compose.onNodeWithText("Select all").performClick()
                waitForText("Add selected (65)")
                compose.activityRule.scenario.recreate()
                waitForText("Add selected (65)")
                compose.onNodeWithText("Search tracks to add").assertTextContains(prefix)
                compose.onNodeWithText("Add selected (65)").performClick()
                waitForPickerToClose()
                withTimeout(10_000) {
                    while (database.collectionDao().entryCount(id) != 65) delay(50)
                }
                assertEquals(ids, database.collectionDao().queue(id).map { it.trackId })
                compose.activityRule.scenario.recreate()
                waitForText(name)
                assertEquals(ids, database.collectionDao().queue(id).map { it.trackId })
                scrollTo(hasText("Add tracks"))
                compose.onNodeWithText("Add tracks").performClick()
                waitForText("Search tracks to add")
                compose.onNodeWithText("Search tracks to add").performTextReplacement("$prefix 000")
                compose.onNodeWithText("Search tracks to add").performImeAction()
                waitForSelectionReady()
                scrollTo(firstTrack)
                compose.onNode(firstTrack).performClick()
                waitForText("Add selected (1)")
                compose.onNodeWithText("Add selected (1)").performClick()
                waitForText("Repeated tracks")
                assertEquals(65, database.collectionDao().entryCount(id))
                compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
                compose.onNodeWithText("Add selected (1)").assertIsEnabled().performClick()
                waitForText("Skip duplicates")
                compose.onNodeWithText("Skip duplicates").performClick()
                waitForPickerToClose()
                assertEquals(ids, database.collectionDao().queue(id).map { it.trackId })
                scrollTo(hasText("Select playlist tracks"))
                compose.onNodeWithText("Select playlist tracks").performClick()
                waitForText("0 selected")
                compose.onNodeWithText("Cancel").performClick()
                assertEquals(ids, database.collectionDao().queue(id).map { it.trackId })
            } finally {
                playlistId?.let { database.collectionDao().deletePlaylist(it) }
                for (id in ids) {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM artist_credits WHERE trackId=?",
                        arrayOf(id),
                    )
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM media_assets WHERE trackId=?",
                        arrayOf(id),
                    )
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM tracks WHERE id=?",
                        arrayOf(id),
                    )
                }
                database.close()
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

    private fun waitForSelectionReady() {
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(hasText("Select all") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForPickerToClose() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Search tracks to add").fetchSemanticsNodes().isEmpty()
        }
    }
}
