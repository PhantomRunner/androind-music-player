package dev.violet.music

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.data.SettingsRepository
import dev.violet.music.core.database.*
import dev.violet.music.core.media.*
import dev.violet.music.core.model.*
import dev.violet.music.feature.player.PlayerViewModel
import dev.violet.music.feature.selection.SelectionViewModel
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LibraryExperienceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun seed(): Pair<MusicDatabase, List<String>> {
        val db =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        val settings = SettingsRepository(db)
        settings.mode(LibraryCategory.TRACKS, DisplayMode.LIST)
        settings.sort(LibraryCategory.TRACKS, TrackSort.TITLE_ASC.name)
        RoomCatalogueWriter(db)
            .importBatch(
                (0 until 65).map { index ->
                    DeviceAudio(
                        "ui-fixture",
                        index.toLong(),
                        "content://media/ui-fixture/audio/media/$index",
                        "UI test fixture ${index.toString().padStart(3,'0')}",
                        "UI artist",
                        "UI album",
                        "ui-fixture:album",
                        null,
                        60_000,
                        index.toLong(),
                        1,
                        1024,
                        "audio/mpeg",
                    )
                },
                "ui-fixture",
            )
        return db to
            (0 until 65).map {
                requireNotNull(db.libraryDao().findAsset("ui-fixture", it.toLong())).trackId
            }
    }

    private fun library(query: String) {
        compose.onNode(hasText("Library") and hasClickAction()).performClick()
        compose.onNode(hasText("Tracks") and hasClickAction()).performClick()
        compose.onNodeWithText("Search your library").performTextReplacement(query)
        compose.onNodeWithText("Search your library").performImeAction()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("UI test fixture 000").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scroll(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    @Test
    fun fullCollectionSelectionIntersectsFiltersAndBulkFavouriteCompletes() = runBlocking {
        val (db, ids) = seed()
        lateinit var selection: SelectionViewModel
        compose.activityRule.scenario.onActivity {
            selection = ViewModelProvider(it)[SelectionViewModel::class.java]
        }
        try {
            library("UI test fixture")
            scroll(hasText("UI test fixture 000"))
            compose.onNodeWithText("UI test fixture 000").performTouchInput { longClick() }
            compose.waitUntil { selection.uiState.value.active }
            scroll(hasText("Select all"))
            compose.onNodeWithText("Select all").performClick()
            compose.waitUntil(10_000) {
                !selection.uiState.value.loading && selection.uiState.value.selected.size == 65
            }
            scroll(hasText("Search your library"))
            compose
                .onNodeWithText("Search your library")
                .performTextReplacement("UI test fixture 00")
            compose.waitUntil(10_000) {
                !selection.uiState.value.loading && selection.uiState.value.selected.size == 10
            }
            scroll(hasText("Search your library"))
            compose.onNodeWithText("Search your library").performTextReplacement("UI test fixture")
            compose.waitUntil(10_000) {
                !selection.uiState.value.loading && selection.uiState.value.selected.size == 10
            }
            scroll(hasText("Grid view"))
            compose.onNodeWithText("Grid view").performClick()
            compose.waitUntil {
                compose.onAllNodesWithText("List view").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(10, selection.uiState.value.selected.size)
            scroll(hasText("Add to Favourites"))
            compose.onNodeWithText("Add to Favourites").performClick()
            compose.waitUntil(10_000) {
                !selection.uiState.value.active && !selection.uiState.value.running
            }
            assertTrue(ids.take(10).all { db.collectionDao().isFavourite(it) })
            assertTrue(ids.drop(10).none { db.collectionDao().isFavourite(it) })
        } finally {
            ids.forEach { db.collectionDao().removeFavourite(it) }
            db.close()
        }
    }

    @Test
    fun returningFromAlbumAndPlayerPreservesBrowsingAndActiveSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permission =
            if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
            else "android.permission.READ_EXTERNAL_STORAGE"
        instrumentation.uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
            .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        val uri = TestAudio.create(context, seconds = 120)
        val (db, _) = seed()
        lateinit var player: PlayerViewModel
        compose.activityRule.scenario.onActivity {
            player = ViewModelProvider(it)[PlayerViewModel::class.java]
        }
        try {
            val writer = RoomCatalogueWriter(db)
            MediaStoreScanner(context).scan { writer.importBatch(it, "ui-playing-fixture") }
            val id =
                requireNotNull(
                        db.libraryDao()
                            .findAsset(MediaStore.VOLUME_EXTERNAL_PRIMARY, ContentUris.parseId(uri))
                    )
                    .trackId
            library("UI test fixture")
            scroll(hasContentDescription("Options for UI test fixture 035"))
            compose.onNodeWithContentDescription("Options for UI test fixture 035").performClick()
            compose.onNodeWithText("Go to album").performScrollTo().performClick()
            compose.waitUntil {
                compose.onAllNodesWithText("UI album").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Go back").performClick()
            compose.onNodeWithText("UI test fixture 035").assertIsDisplayed()
            withContext(Dispatchers.Main) { player.play(id) }
            withTimeout(15_000) { player.uiState.first { it.current?.id == id && it.isPlaying } }
            val occurrence = player.uiState.value.current?.occurrenceId
            val title = requireNotNull(db.libraryDao().findTrack(id)).title
            repeat(3) {
                compose.onNodeWithText(title).performClick()
                compose.onNodeWithText("Now playing").assertIsDisplayed()
                compose.onNodeWithContentDescription("Go back").performClick()
                compose.onNodeWithText("UI test fixture 035").assertIsDisplayed()
            }
            assertTrue(player.uiState.value.isPlaying)
            assertEquals(occurrence, player.uiState.value.current?.occurrenceId)
            // Exercise the real long-press drag gesture with duplicate occurrences.
            withContext(Dispatchers.Main) { player.playIds(listOf(id, id, id)) }
            withTimeout(10_000) {
                player.uiState.first {
                    it.queue.size == 3 &&
                        it.isPlaying &&
                        it.queue.all { item -> !item.occurrenceId.startsWith("pending:") }
                }
            }
            val beforeDrag = player.uiState.value.queue.map { it.occurrenceId }
            compose.onNodeWithText(title).performClick()
            scroll(hasContentDescription("Queue"))
            compose.onNodeWithContentDescription("Queue").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Up next").assertIsDisplayed()
            val handles =
                compose
                    .onAllNodesWithContentDescription(
                        "Drag to reorder $title",
                        useUnmergedTree = true,
                    )
            val distance = handles[1].fetchSemanticsNode().boundsInRoot.center.y -
                handles[0].fetchSemanticsNode().boundsInRoot.center.y
            handles[0].performTouchInput {
                val start = center
                swipe(
                    curve = { time ->
                        start +
                            Offset(
                                0f,
                                distance *
                                    ((time - 650).coerceAtLeast(0) / 350f).coerceIn(0f, 1f),
                            )
                    },
                    durationMillis = 1_000,
                )
            }
            try {
                withTimeout(10_000) {
                    player.uiState.first {
                        it.queue.map { item -> item.occurrenceId } ==
                            listOf(beforeDrag[1], beforeDrag[0], beforeDrag[2])
                    }
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError(
                    "Drag order: ${player.uiState.value.queue.map {beforeDrag.indexOf(it.occurrenceId)}}",
                    error,
                )
            }
            assertTrue(player.uiState.value.isPlaying)
            assertEquals(beforeDrag[0], player.uiState.value.current?.occurrenceId)
            compose.onNodeWithText("Show earlier tracks (1)").assertIsDisplayed().performClick()
            compose.onNodeWithText("Hide earlier tracks (1)").assertIsDisplayed()
            compose.onNodeWithContentDescription("Go back").performClick()
            compose.onNodeWithContentDescription("Go back").performClick()
            compose.onNodeWithText("UI test fixture 035").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("UI test fixture 035").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("UI test fixture 035").assertIsDisplayed()
            assertEquals(id, player.uiState.value.current?.id)
        } finally {
            withContext(Dispatchers.Main) { player.controller.stop() }
            db.close()
            context.contentResolver.delete(uri, null, null)
        }
    }
}
