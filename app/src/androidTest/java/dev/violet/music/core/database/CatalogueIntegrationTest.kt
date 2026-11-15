package dev.violet.music.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.data.OfflineLibraryRepository
import dev.violet.music.core.data.searchPattern
import dev.violet.music.core.media.DeviceAudio
import dev.violet.music.core.media.DeviceScanner
import dev.violet.music.core.media.LibraryIndexer
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MusicDatabase
    private lateinit var writer: RoomCatalogueWriter
    private val dao get() = database.libraryDao()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        writer = RoomCatalogueWriter(database)
    }

    @After
    fun closeDatabase() { database.close() }

    @Test
    fun importAndMetadataUpdatesKeepTrackAndAssetIds() = runBlocking {
        val initial = fixture(1)
        writer.importBatch(listOf(initial), "first")
        writer.completeScan("first", 10)
        val asset = requireNotNull(dao.findAsset(initial.volume, initial.mediaId))
        val track = requireNotNull(dao.findTrack(asset.trackId))
        dao.upsertTrack(track.copy(serverId = "test-fixture-server-id"))

        repeat(3) { writer.importBatch(listOf(initial.copy(title = "Updated fixture")), "next") }
        writer.completeScan("next", 20)

        val updatedAsset = requireNotNull(dao.findAsset(initial.volume, initial.mediaId))
        assertEquals(asset.id, updatedAsset.id)
        assertEquals(track.id, updatedAsset.trackId)
        val updatedTrack = requireNotNull(dao.findTrack(track.id))
        assertEquals("Updated fixture", updatedTrack.title)
        assertEquals("test-fixture-server-id", updatedTrack.serverId)
        assertEquals(1, dao.stats().first().trackCount)
        assertNotEquals(initial.mediaId.toString(), track.id)
    }

    @Test
    fun sameMediaIdOnDifferentVolumesCreatesDistinctTracks() = runBlocking {
        writer.importBatch(listOf(fixture(1), fixture(1).copy(volume = "fixture_sdcard")), "first")
        writer.completeScan("first", 10)
        assertEquals(2, dao.stats().first().trackCount)
        assertNotEquals(dao.findAsset("external_primary", 1)?.trackId, dao.findAsset("fixture_sdcard", 1)?.trackId)
    }

    @Test
    fun successfulEmptyScanMarksUnavailableAndReappearanceRestoresSameId() = runBlocking {
        writer.importBatch(listOf(fixture(1)), "first")
        writer.completeScan("first", 10)
        val originalId = requireNotNull(dao.findAsset("external_primary", 1)).trackId

        writer.completeScan("empty", 20)
        assertEquals("UNAVAILABLE", dao.findTrack(originalId)?.availability)
        assertEquals(1, dao.stats().first().trackCount)
        assertEquals(1, dao.stats().first().unavailableCount)

        writer.importBatch(listOf(fixture(1)), "restored")
        writer.completeScan("restored", 30)
        assertEquals(originalId, dao.findAsset("external_primary", 1)?.trackId)
        assertEquals("LOCAL", dao.findTrack(originalId)?.availability)
    }

    @Test
    fun failedPartialRefreshPreservesUnseenRowsAndSuccessfulTimestamp() = runBlocking {
        writer.importBatch(listOf(fixture(1), fixture(2)), "first")
        writer.completeScan("first", 10)
        val indexer = LibraryIndexer(DeviceScanner { batch ->
            batch(listOf(fixture(1).copy(title = "Changed fixture")))
            throw IllegalStateException("Simulated provider interruption")
        }, writer)
        val repository = OfflineLibraryRepository(database, indexer)

        repository.refresh()

        assertTrue(repository.scanStatus.value is ScanStatus.Failed)
        assertEquals(2, repository.observeStats().first().trackCount)
        assertEquals(0, repository.observeStats().first().unavailableCount)
        assertEquals(10L, repository.observeLastScan().first())
        val unseen = requireNotNull(dao.findAsset("external_primary", 2))
        assertTrue(unseen.available)
        assertEquals("LOCAL", dao.findTrack(unseen.trackId)?.availability)
    }

    @Test
    fun deniedRefreshNeverChangesExistingCatalogue() = runBlocking {
        writer.importBatch(listOf(fixture(1)), "first")
        writer.completeScan("first", 10)
        val indexer = LibraryIndexer(DeviceScanner { throw SecurityException() }, writer)
        val repository = OfflineLibraryRepository(database, indexer)

        repository.refresh()

        assertEquals(1, repository.observeStats().first().trackCount)
        assertEquals(0, repository.observeStats().first().unavailableCount)
        assertEquals(10L, repository.observeLastScan().first())
    }

    @Test
    fun searchMatchesTitleArtistAlbumAndEscapesWildcards() = runBlocking {
        writer.importBatch(listOf(
            fixture(1).copy(title = "100%_fixture", artist = "North", album = "Sky"),
            fixture(2).copy(title = "Ordinary fixture", artist = "South", album = "Forest"),
        ), "first")

        for (query in listOf("100%_", "north", "sky")) {
            val results = loadTracks(query)
            assertEquals(1, results.size)
            assertEquals("100%_fixture", results.single().title)
        }
        assertTrue(loadTracks("missing fixture").isEmpty())
        assertEquals(2, loadTracks("").size)
    }

    @Test
    fun pagedQueriesAndRecentListAreBounded() = runBlocking {
        writer.importBatch((1L..150L).map { fixture(it).copy(dateAdded = it) }, "first")
        assertEquals(8, dao.recentTracks().first().size)
        assertEquals(150L, dao.recentTracks().first().first().dateAdded)
        val source = dao.tracks("", null, null, false)
        val page = source.load(PagingSource.LoadParams.Refresh<Int>(null, 50, false))
        assertTrue(page is PagingSource.LoadResult.Page)
        assertEquals(50, (page as PagingSource.LoadResult.Page).data.size)
        assertNotNull(page.nextKey)
    }

    @Test
    fun failedBatchRollsBackItsEarlierWrites() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fixture_failure BEFORE INSERT ON tracks " +
                "WHEN NEW.title = 'Rejected fixture' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END",
        )
        val result = runCatching {
            writer.importBatch(listOf(fixture(1), fixture(2).copy(title = "Rejected fixture")), "first")
        }
        assertTrue(result.isFailure)
        assertEquals(0, dao.stats().first().trackCount)
        assertEquals(null, dao.findAsset("external_primary", 1))
    }

    @Test
    fun catalogueAndIdentitySurviveDatabaseReopen() = runBlocking {
        val name = "catalogue-test-${UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
        try {
            val persistentWriter = RoomCatalogueWriter(persistent)
            persistentWriter.importBatch(listOf(fixture(1)), "first")
            persistentWriter.completeScan("first", 10)
            val originalId = persistent.libraryDao().findAsset("external_primary", 1)?.trackId
            persistent.close()

            persistent = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
            assertEquals(originalId, persistent.libraryDao().findAsset("external_primary", 1)?.trackId)
            assertEquals(1, persistent.libraryDao().stats().first().trackCount)
            assertEquals(10L, persistent.libraryDao().lastScan().first())
        } finally {
            persistent.close()
            // Only this test's randomly named database, never the application catalogue.
            context.deleteDatabase(name)
        }
    }

    private suspend fun loadTracks(query: String): List<Track> {
        val source = dao.tracks(searchPattern(query), null, null, false)
        val result = source.load(PagingSource.LoadParams.Refresh<Int>(null, 50, false))
        return when (result) {
            is PagingSource.LoadResult.Page -> result.data
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("Unexpected query invalidation")
        }
    }

    private fun fixture(id: Long) = DeviceAudio(
        "external_primary", id, "content://media/external_primary/audio/media/$id",
        "Test fixture $id", "Fixture artist", "Fixture album", "external_primary:1",
        null, 60_000, 1, 1, 1_024, "audio/mpeg",
    )
}
