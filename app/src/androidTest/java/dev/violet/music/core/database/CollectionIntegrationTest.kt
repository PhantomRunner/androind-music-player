package dev.violet.music.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.data.RoomCollectionRepository
import dev.violet.music.core.data.SelectionRepository
import dev.violet.music.core.data.searchPattern
import dev.violet.music.core.media.DeviceAudio
import dev.violet.music.core.media.DeviceScanner
import dev.violet.music.core.media.LibraryIndexer
import dev.violet.music.core.model.PlaylistAddItem
import dev.violet.music.core.model.PlaylistAddResult
import dev.violet.music.core.model.PlaylistDuplicatePolicy
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.model.PlaylistSort
import dev.violet.music.core.model.SelectionSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MusicDatabase
    private lateinit var repository: RoomCollectionRepository
    private lateinit var writer: RoomCatalogueWriter
    private lateinit var ids: List<String>

    @Before
    fun prepare() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        repository = RoomCollectionRepository(database)
        writer = RoomCatalogueWriter(database)
        writer.importBatch((1L..3L).map(::fixture), "initial")
        writer.completeScan("initial", 10)
        ids = (1L..3L).map { requireNotNull(database.libraryDao().findAsset("fixture_volume", it)).trackId }
    }
    @After fun close() { database.close() }

    @Test
    fun duplicateEntriesReorderAndRemovalKeepContiguousPositions() = runBlocking {
        val id = repository.create(" Test playlist ")
        val tracks = listOf(
            PlaylistAddItem("first", ids[0]),
            PlaylistAddItem("second", ids[1]),
            PlaylistAddItem("repeat", ids[0]),
        )
        assertEquals(
            PlaylistAddResult.NeedsConfirmation(setOf("repeat")),
            repository.addTracks(id, tracks, "original"),
        )
        assertTrue(repository.queue(id).isEmpty())
        assertEquals(
            PlaylistAddResult.Completed(3, 0, 0),
            repository.addTracks(id, tracks, "original", PlaylistDuplicatePolicy.Allow(setOf("repeat"))),
        )
        val original = entries(id)
        assertEquals(3, original.map { it.entryId }.distinct().size)
        assertEquals(listOf(ids[0], ids[1], ids[0]), repository.queue(id).map { it.trackId })
        repository.moveEntry(original[2].entryId, 0)
        assertEquals(listOf(original[2].entryId, original[0].entryId, original[1].entryId), entries(id).map { it.entryId })
        repository.removeEntry(original[0].entryId)
        assertEquals(listOf(0, 1), entries(id).map { it.position })
        repository.addFixtureTrack(id, ids[2])
        assertEquals(listOf(0, 1, 2), entries(id).map { it.position })
        assertEquals("Test playlist", repository.observePlaylist(id).first()?.name)
    }

    @Test
    fun playlistDeletionKeepsTracksFavouritesAndOtherPlaylists() = runBlocking {
        val first = repository.create("First fixture")
        val second = repository.create("Second fixture")
        repository.addFixtureTrack(first, ids[0]); repository.addFixtureTrack(second, ids[0])
        repository.setFavourite(ids[0], true)
        repository.rename(first, "Renamed fixture")
        assertEquals("Renamed fixture", repository.observePlaylist(first).first()?.name)
        repository.delete(first)
        assertEquals(null, repository.observePlaylist(first).first())
        assertEquals(1, entries(second).size)
        assertTrue(database.libraryDao().track(ids[0]).first()?.isFavourite == true)
        assertEquals(3, database.libraryDao().stats().first().trackCount)
    }

    @Test
    fun favouritesAndMembershipSurviveFailedAndMissingMediaScans() = runBlocking {
        val playlist = repository.create("Preserved fixture")
        repository.addFixtureTrack(playlist, ids[0]); repository.setFavourite(ids[0], true)
        LibraryIndexer(DeviceScanner { batch -> batch(listOf(fixture(2))); throw SecurityException() }, writer).refresh()
        assertEquals(0, database.libraryDao().stats().first().unavailableCount)
        assertEquals(1, entries(playlist).size)
        writer.completeScan("verified-empty", 20)
        val saved = requireNotNull(database.libraryDao().track(ids[0]).first())
        assertTrue(saved.isFavourite)
        assertEquals("UNAVAILABLE", saved.availability)
        assertEquals(1, entries(playlist).size)
        assertTrue(repository.queue(playlist).isEmpty())
        writer.importBatch(listOf(fixture(1)), "restored"); writer.completeScan("restored", 30)
        assertEquals(ids[0], repository.queue(playlist).single().trackId)
        assertTrue(database.libraryDao().track(ids[0]).first()?.isFavourite == true)
    }

    @Test
    fun concurrentAppendsAreAtomicAndFavouriteWritesIdempotent() = runBlocking {
        val playlist = repository.create("Concurrent fixture")
        val results = coroutineScope {
            List(20) { async(Dispatchers.IO) { repository.addFixtureTrack(playlist, ids[0]) } }.awaitAll()
        }
        assertEquals(1, results.count { it == PlaylistAddResult.Completed(1, 0, 0) })
        assertEquals(19, results.count { it is PlaylistAddResult.NeedsConfirmation })
        assertEquals(listOf(0), entries(playlist).map { it.position })
        repeat(2) { repository.setFavourite(ids[0], true) }
        assertEquals(listOf(ids[0]), database.libraryDao().queueIds("", null, null, false, true))
        repeat(2) { repository.setFavourite(ids[0], false) }
        assertTrue(database.libraryDao().queueIds("", null, null, false, true).isEmpty())
    }

    @Test
    fun confirmationDoesNotApproveNewConflictsAndSkipAddsOnlyNewTracks() = runBlocking {
        val id = repository.create("Conflict fixture")
        repository.addFixtureTrack(id, ids[0])
        val selected = ids.mapIndexed { index, trackId -> PlaylistAddItem("selected-$index", trackId) }
        assertEquals(
            PlaylistAddResult.NeedsConfirmation(setOf("selected-0")),
            repository.addTracks(id, selected, "pending"),
        )
        assertEquals(1, entries(id).size)
        repository.addFixtureTrack(id, ids[1])
        assertEquals(
            PlaylistAddResult.NeedsConfirmation(setOf("selected-0", "selected-1")),
            repository.addTracks(id, selected, "pending", PlaylistDuplicatePolicy.Allow(setOf("selected-0"))),
        )
        assertEquals(2, entries(id).size)
        repeat(2) {
            assertEquals(
                PlaylistAddResult.Completed(1, 2, 0),
                repository.addTracks(id, selected, "pending", PlaylistDuplicatePolicy.Skip),
            )
        }
        assertEquals(ids, repository.queue(id).map { it.trackId })
    }

    @Test
    fun playlistFilterAndSortAreSharedByEntriesSelectionAndPlayback() = runBlocking {
        val id = repository.create("Playlist browsing fixture")
        val tracks = listOf(ids[2], ids[0], ids[1], ids[0]).mapIndexed { index, trackId ->
            PlaylistAddItem("entry-$index", trackId)
        }
        repository.addTracks(id, tracks, "fixture", PlaylistDuplicatePolicy.Allow(setOf("entry-3")))
        val original = entries(id)
        val title = PlaylistFilter(sort = PlaylistSort.TITLE)
        val sorted = listOf(original[1], original[3], original[2], original[0]).map { it.entryId }
        assertEquals(sorted, entries(id, title).map { it.entryId })
        assertEquals(sorted, repository.queue(id, title).map { it.entryId })

        val filtered = title.copy(query = "fixture 1")
        val matching = sorted.take(2)
        assertEquals(matching, entries(id, filtered).map { it.entryId })
        assertEquals(matching, repository.queue(id, filtered).map { it.entryId })
        val source = SelectionSource("filtered", playlistId = id, playlistFilter = filtered)
        assertEquals(matching, SelectionRepository(database).page(source, 0).map { it.key })
        assertTrue(repository.hasPlayableTracks(id, filtered.query).first())
        assertTrue(repository.queue(id, filtered.copy(query = "%")).isEmpty())
        assertEquals(original, entries(id))
    }

    @Test
    fun collectionsSurviveDatabaseReopen() = runBlocking {
        val name = "collection-test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
        try {
            RoomCatalogueWriter(db).importBatch(listOf(fixture(1)), "first")
            val trackId = requireNotNull(db.libraryDao().findAsset("fixture_volume", 1)).trackId
            var repo = RoomCollectionRepository(db)
            val playlist = repo.create("Persistent fixture")
            repo.addFixtureTrack(playlist, trackId); repo.setFavourite(trackId, true)
            val entryId = repo.queue(playlist).single().entryId
            db.close()
            db = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
            repo = RoomCollectionRepository(db)
            assertEquals(entryId, repo.queue(playlist).single().entryId)
            assertEquals(trackId, repo.queue(playlist).single().trackId)
            assertTrue(db.libraryDao().track(trackId).first()?.isFavourite == true)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private suspend fun RoomCollectionRepository.addFixtureTrack(playlistId: String, trackId: String) =
        addTracks(playlistId, listOf(PlaylistAddItem(trackId, trackId)), UUID.randomUUID().toString())

    private suspend fun entries(id: String, filter: PlaylistFilter = PlaylistFilter()): List<PlaylistEntryRow> {
        val paging = database.collectionDao().entries(id, searchPattern(filter.query), filter.sort.name)
        return when (val result = paging.load(PagingSource.LoadParams.Refresh<Int>(null, 50, false))) {
            is PagingSource.LoadResult.Page -> result.data
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("Unexpected invalidation")
        }
    }
    private fun fixture(id: Long) = DeviceAudio("fixture_volume", id, "content://media/fixture_volume/audio/media/$id", "Collection test fixture $id", "Fixture artist", "Fixture album", "fixture_volume:1", null, 60_000, id, 1, 1_024, "audio/mpeg")
}
