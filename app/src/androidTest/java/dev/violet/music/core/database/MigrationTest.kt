package dev.violet.music.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MusicDatabase::class.java)

    @Test
    fun v2ToV4KeepsDuplicateEntriesFavouritesAndTrackIdentity() {
        val name = "overhaul-v2-fixture.db"
        helper.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO tracks VALUES ('track','server','Fixture','Combined, Credit','Album','fixture:1',60000,NULL,'DEVICE','LOCAL',1)"
            )
            execSQL("INSERT INTO favourites VALUES ('track',5)")
            execSQL("INSERT INTO playlists VALUES ('playlist',NULL,'Preserved',1,2,7)")
            execSQL(
                "INSERT INTO playlist_entries VALUES ('entry-one','playlist','track',0,1),('entry-two','playlist','track',1,2)"
            )
            execSQL("INSERT INTO scan_state VALUES ('last_success',123)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, *ALL_MIGRATIONS).apply {
            query("SELECT id,serverId FROM tracks").use {
                it.moveToFirst()
                assertEquals("track", it.getString(0))
                assertEquals("server", it.getString(1))
            }
            query("SELECT id,position FROM playlist_entries ORDER BY position").use {
                assertEquals(2, it.count)
                it.moveToFirst()
                assertEquals("entry-one", it.getString(0))
                assertEquals(0, it.getInt(1))
                it.moveToNext()
                assertEquals("entry-two", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            query("SELECT COUNT(*) FROM favourites").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            query("SELECT COUNT(*) FROM artist_credits WHERE trackId='track' AND source='METADATA'")
                .use {
                    it.moveToFirst()
                    assertEquals(1, it.getInt(0))
                }
            query("SELECT coverFileName,localRevision FROM playlists").use {
                it.moveToFirst()
                org.junit.Assert.assertTrue(it.isNull(0))
                assertEquals(7, it.getInt(1))
            }
            close()
        }
    }

    @Test
    fun v3ToV4PreservesHistoryCorrectionsAndPreferences() {
        val name = "overhaul-v3-fixture.db"
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO tracks VALUES ('track',NULL,'Fixture','Original','Album','fixture:1',60000,NULL,'DEVICE','LOCAL',1,NULL)"
            )
            execSQL("INSERT INTO artists VALUES ('artist','Corrected','corrected',NULL)")
            execSQL("INSERT INTO artist_credits VALUES ('track','artist','TRACK','MANUAL',0)")
            execSQL("INSERT INTO artist_overrides VALUES ('track','TRACK')")
            execSQL("INSERT INTO playback_events VALUES ('event','track',1000,5000,0,0,1,6000)")
            execSQL("INSERT INTO listening_segments VALUES ('event',0,1000,6000,5000)")
            execSQL("INSERT INTO preferences VALUES ('history.period','WEEK')")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).apply {
            query("SELECT source FROM artist_credits").use {
                it.moveToFirst()
                assertEquals("MANUAL", it.getString(0))
            }
            query("SELECT listenedMs,qualified FROM playback_events").use {
                it.moveToFirst()
                assertEquals(5000, it.getInt(0))
                assertEquals(0, it.getInt(1))
            }
            query("SELECT value FROM preferences").use {
                it.moveToFirst()
                assertEquals("WEEK", it.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrationFromCatalogueV1PreservesIdentityMetadataAndLastScan() {
        val name = "migration-fixture-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO tracks (id, serverId, title, artistName, albumTitle, albumKey, durationMs, artworkUri, origin, availability, dateAdded) VALUES ('fixture-uuid', 'fixture-server-id', 'Fixture title', 'Fixture artist', 'Fixture album', 'fixture:1', 60000, NULL, 'DEVICE', 'LOCAL', 1)"
            )
            execSQL(
                "INSERT INTO media_assets (id, trackId, volumeName, mediaStoreId, uri, available, lastSeenScanId, sizeBytes, mimeType, lastModified) VALUES ('fixture-asset-id', 'fixture-uuid', 'fixture', 1, 'content://media/fixture/audio/media/1', 1, 'scan-fixture', 1024, 'audio/mpeg', 1)"
            )
            execSQL("INSERT INTO scan_state (`key`, value) VALUES ('last_success', 123)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).apply {
            query("SELECT id, serverId FROM tracks").use {
                it.moveToFirst()
                assertEquals("fixture-uuid", it.getString(0))
                assertEquals("fixture-server-id", it.getString(1))
            }
            query("SELECT trackId FROM media_assets").use {
                it.moveToFirst()
                assertEquals("fixture-uuid", it.getString(0))
            }
            query("SELECT value FROM scan_state").use {
                it.moveToFirst()
                assertEquals(123, it.getInt(0))
            }
            execSQL("INSERT INTO favourites (trackId, createdAt) VALUES ('fixture-uuid', 1)")
            execSQL(
                "INSERT INTO playlists (id, serverId, name, createdAt, updatedAt, localRevision) VALUES ('playlist-fixture', NULL, 'Fixture playlist', 1, 1, 1)"
            )
            execSQL(
                "INSERT INTO playlist_entries (id, playlistId, trackId, position, addedAt) VALUES ('entry-fixture', 'playlist-fixture', 'fixture-uuid', 0, 1)"
            )
            close()
        }
    }
}
