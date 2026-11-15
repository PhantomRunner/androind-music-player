package dev.violet.music.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.violet.music.core.data.*
import dev.violet.music.core.media.*
import dev.violet.music.core.model.*
import dev.violet.music.core.playback.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExperienceIntegrationTest {
    @Test
    fun cachedAndFailedReadsPreserveAlbumArtistRelationships() = runBlocking {
        var failed = false
        val writer =
            RoomCatalogueWriter(
                db,
                AudioCreditReader {
                    if (failed) null
                    else EmbeddedCredits(listOf("Track artist"), listOf("Album artist"))
                },
            )
        writer.importBatch(listOf(fixture(1)), "first")
        val track = id(1)
        writer.importBatch(listOf(fixture(1)), "cached")
        assertEquals(
            "Album artist",
            ArtistRepository(db).credits(track).first().single { it.role == "ALBUM" }.name,
        )
        failed = true
        writer.importBatch(listOf(fixture(1).copy(lastModified = 2)), "failed-metadata")
        assertEquals(
            "Album artist",
            ArtistRepository(db).credits(track).first().single { it.role == "ALBUM" }.name,
        )
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MusicDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
    }

    @After
    fun close() {
        db.close()
    }

    private fun fixture(id: Long, artist: String = "Fixture artist") =
        DeviceAudio(
            "fixture",
            id,
            "content://media/fixture/audio/media/$id",
            "Fixture $id",
            artist,
            "Fixture album",
            "fixture:1",
            null,
            60_000,
            id,
            1,
            1024,
            "audio/mpeg",
        )

    private suspend fun id(media: Long) =
        requireNotNull(db.libraryDao().findAsset("fixture", media)).trackId

    private suspend fun addEvent(
        track: String,
        event: String,
        start: Long,
        duration: Long,
        qualified: Boolean,
    ) {
        db.experienceDao()
            .event(
                PlaybackEventEntity(
                    event,
                    track,
                    start,
                    duration,
                    qualified,
                    false,
                    true,
                    start + duration,
                )
            )
        db.experienceDao()
            .segments(listOf(ListeningSegmentEntity(event, 0, start, start + duration, duration)))
    }

    @Test
    fun manualCreditsSurviveMetadataChangesAndResetRestoresLatestAutomaticResolution() =
        runBlocking {
            var embedded =
                EmbeddedCredits(listOf("Alan Walker", "Ava Max"), listOf("Fixture album artist"))
            val writer = RoomCatalogueWriter(db, AudioCreditReader { embedded })
            writer.importBatch(listOf(fixture(1, "Alan Walker, Ava Max")), "a")
            val track = id(1)
            val collections = RoomCollectionRepository(db)
            val playlist = collections.create("Preserved fixture")
            collections.addTracks(playlist, listOf(PlaylistAddItem(track, track)), "fixture")
            collections.setFavourite(track, true)
            val artists = ArtistRepository(db)
            assertEquals(2, artists.credits(track).first().count { it.role == "TRACK" })
            artists.correct(track, ArtistRole.TRACK, listOf("Local correction"))
            embedded = EmbeddedCredits(listOf("New metadata artist"), emptyList())
            writer.importBatch(
                listOf(fixture(1, "New metadata artist").copy(lastModified = 2)),
                "b",
            )
            assertEquals(
                listOf("Local correction"),
                artists.credits(track).first().filter { it.role == "TRACK" }.map { it.name },
            )
            assertEquals(
                "MANUAL",
                artists.credits(track).first().first { it.role == "TRACK" }.source,
            )
            assertEquals(
                listOf(track),
                db.libraryDao()
                    .selectIds(trackQuery(TrackFilter(query = "Local correction"), idsOnly = true)),
            )
            artists.reset(track, ArtistRole.TRACK)
            assertEquals(
                listOf("New metadata artist"),
                artists.credits(track).first().filter { it.role == "TRACK" }.map { it.name },
            )
            assertEquals(track, id(1))
            assertTrue(db.collectionDao().isFavourite(track))
            assertEquals(track, collections.queue(playlist).single().trackId)
        }

    @Test
    fun qualifiedCountsAndAllListeningTimeAreIndependentAndCollaborationsDoNotDuplicateTotals() =
        runBlocking {
            val writer =
                RoomCatalogueWriter(
                    db,
                    AudioCreditReader {
                        EmbeddedCredits(listOf("First artist", "Second artist"), emptyList())
                    },
                )
            writer.importBatch(listOf(fixture(1)), "a")
            val track = id(1)
            addEvent(track, "qualified", 1_000, 30_000, true)
            addEvent(track, "short", 40_000, 5_000, false)
            val totals = db.listeningDao().totals(0, 100_000)
            assertEquals(1, totals.plays)
            assertEquals(35_000, totals.listenedMs)
            val artists = db.listeningDao().artists(0, 100_000)
            assertEquals(2, artists.size)
            artists.forEach {
                assertEquals(1, it.plays)
                assertEquals(35_000, it.listenedMs)
            }
            ArtistRepository(db).correct(track, ArtistRole.TRACK, listOf("Corrected artist"))
            assertEquals(
                listOf("Corrected artist"),
                db.listeningDao().artists(0, 100_000).map { it.name },
            )
            assertEquals(2, db.experienceDao().trackEvents(track).size)
            assertEquals(totals, db.listeningDao().totals(0, 100_000))
        }

    @Test
    fun listeningSegmentsAreClippedAcrossMidnightAndRebucketedForTimeZone() = runBlocking {
        RoomCatalogueWriter(db).importBatch(listOf(fixture(1)), "a")
        val start = Instant.parse("2026-11-14T23:59:55Z").toEpochMilli()
        addEvent(id(1), "crossing", start, 10_000, false)
        val midnight = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()
        assertEquals(5_000, db.listeningDao().totals(midnight, midnight + 60_000).listenedMs)
        assertEquals(0, db.listeningDao().totals(midnight, midnight + 60_000).plays)
        val history = HistoryRepository(db)
        history.awaitIdle()
        val now = Instant.parse("2026-11-15T12:00:00Z")
        val utc = history.load(HistoryPeriod.WEEK, now = now, zone = ZoneId.of("UTC"))
        assertEquals(listOf(5_000L, 5_000L), utc.days.takeLast(2).map { it.listenedMs })
        val tokyo = history.load(HistoryPeriod.WEEK, now = now, zone = ZoneId.of("Asia/Tokyo"))
        assertEquals(10_000, tokyo.days.last().listenedMs)
        assertEquals(utc.totals, tokyo.totals)
    }

    @Test
    fun checkpointsAreIdempotentAndNeverRegressQualification() = runBlocking {
        RoomCatalogueWriter(db).importBatch(listOf(fixture(1)), "a")
        val history = HistoryRepository(db)
        history.awaitIdle()
        val attempt = ListeningAccumulator(id(1), 0, "attempt")
        attempt.advance(0, 0, true, 60_000)
        attempt.advance(10_000, 10_000, true, 60_000)
        val first = attempt.checkpoint(10_000)
        attempt.advance(30_000, 30_000, false, 60_000)
        val final = attempt.checkpoint(30_000, complete = true, finalize = true)
        history.persist(first)
        history.persist(first)
        history.persist(final)
        history.persist(final)
        history.persist(first)
        val event = db.experienceDao().trackEvents(id(1)).single()
        assertTrue(event.qualified)
        assertTrue(event.completed)
        assertTrue(event.finalized)
        assertEquals(ListeningTotals(1, 30_000), db.listeningDao().totals(0, 40_000))
    }

    @Test
    fun leastPlayedIncludesZeroPlaysAndGeneratedLimitDoesNotSubstituteUnavailableTracks() =
        runBlocking {
            val writer = RoomCatalogueWriter(db)
            writer.importBatch((1L..3L).map { fixture(it) }, "a")
            addEvent(id(2), "play", 10, 30_000, true)
            val least =
                db.libraryDao()
                    .selectIds(
                        trackQuery(TrackFilter(sort = TrackSort.LEAST_PLAYED), idsOnly = true)
                    )
            assertEquals(id(2), least.last())
            val most = TrackFilter(sort = TrackSort.MOST_PLAYED, qualifiedOnly = true, limit = 1)
            assertEquals(listOf(id(2)), db.libraryDao().selectIds(trackQuery(most, idsOnly = true)))
            writer.importBatch(listOf(fixture(1), fixture(3)), "verified")
            writer.completeScan("verified", 100)
            val resolver = LocalPlayableSourceResolver(context, db)
            assertTrue(resolver.queueIds(most).isEmpty())
        }

    @Test
    fun inferredCreditsUseIndependentEvidenceEvenWhenCollaborationWasImportedFirst() = runBlocking {
        val writer = RoomCatalogueWriter(db)
        writer.importBatch(listOf(fixture(1, "Alan Walker, Ava Max")), "a")
        writer.importBatch(listOf(fixture(2, "Alan Walker"), fixture(3, "Ava Max")), "a")
        writer.completeScan("a", 100)
        val credits = ArtistRepository(db).credits(id(1)).first()
        assertEquals(setOf("Alan Walker", "Ava Max"), credits.map { it.name }.toSet())
        assertTrue(credits.all { it.source == "INFERRED" })
    }

    @Test
    fun selectAllReadsBeyondPagesAndBulkAppendsAreIdempotentWithDuplicateOccurrences() =
        runBlocking {
            RoomCatalogueWriter(db).importBatch((1L..205L).map { fixture(it) }, "a")
            val selection = SelectionRepository(db)
            val source = SelectionSource("fixture", TrackFilter())
            val rows =
                selection.page(source, 0) +
                    selection.page(source, 100) +
                    selection.page(source, 200)
            assertEquals(205, rows.size)
            assertEquals(205, rows.map { it.key }.distinct().size)
            val collections = RoomCollectionRepository(db)
            val playlist = collections.create("Bulk fixture")
            val items = rows.map { PlaylistAddItem(it.key, it.trackId) }
            repeat(2) {
                assertEquals(
                    PlaylistAddResult.Completed(205, 0, 0),
                    collections.addTracks(playlist, items, "one-operation"),
                )
            }
            assertEquals(205, collections.queue(playlist).size)
            val duplicateKeys = setOf("one", "two")
            val duplicates = duplicateKeys.map { PlaylistAddItem(it, rows.first().trackId) }
            assertEquals(
                PlaylistAddResult.NeedsConfirmation(duplicateKeys),
                collections.addTracks(playlist, duplicates, "duplicate-operation"),
            )
            repeat(2) {
                assertEquals(
                    PlaylistAddResult.Completed(2, 0, 0),
                    collections.addTracks(
                        playlist, duplicates, "duplicate-operation", PlaylistDuplicatePolicy.Allow(duplicateKeys),
                    ),
                )
            }
            assertEquals(207, collections.queue(playlist).size)
            val missing = SelectedTrack("missing", "missing", false)
            assertEquals(
                BulkResult(0, 1),
                selection.mutate(
                    BulkAction.ADD_FAVOURITES,
                    listOf(missing),
                    null,
                ),
            )
        }

    @Test
    fun settingsSurviveReopenAndPartialScanPreservesCorrectedCredits() = runBlocking {
        val writer = RoomCatalogueWriter(db)
        writer.importBatch(listOf(fixture(1)), "a")
        val artists = ArtistRepository(db)
        artists.correct(id(1), ArtistRole.TRACK, listOf("Preserved correction"))
        LibraryIndexer(
                DeviceScanner { batch ->
                    batch(listOf(fixture(2)))
                    throw SecurityException()
                },
                writer,
            )
            .refresh()
        assertEquals("Preserved correction", artists.credits(id(1)).first().single().name)
        val name = "settings-fixture-${java.util.UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
        try {
            val settings = SettingsRepository(persistent)
            settings.period(HistoryPeriod.QUARTER)
            settings.mode(LibraryCategory.ALBUMS, DisplayMode.LIST)
            settings.sort(LibraryCategory.TRACKS, TrackSort.LEAST_PLAYED.name)
            persistent.close()
            persistent = Room.databaseBuilder(context, MusicDatabase::class.java, name).build()
            val restored = SettingsRepository(persistent).preferences.first()
            assertEquals(HistoryPeriod.QUARTER, restored.period)
            assertEquals(DisplayMode.LIST, restored.mode(LibraryCategory.ALBUMS))
            assertEquals(TrackSort.LEAST_PLAYED, restored.trackSort())
        } finally {
            persistent.close()
            context.deleteDatabase(name)
        }
    }
}
