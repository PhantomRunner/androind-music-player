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
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MusicDatabase::class.java)

    @Test
    fun migrationFromCatalogueV1PreservesIdentityMetadataAndLastScan() {
        val name = "migration-fixture-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO tracks (id, serverId, title, artistName, albumTitle, albumKey, durationMs, artworkUri, origin, availability, dateAdded) VALUES ('fixture-uuid', 'fixture-server-id', 'Fixture title', 'Fixture artist', 'Fixture album', 'fixture:1', 60000, NULL, 'DEVICE', 'LOCAL', 1)")
            execSQL("INSERT INTO media_assets (id, trackId, volumeName, mediaStoreId, uri, available, lastSeenScanId, sizeBytes, mimeType, lastModified) VALUES ('fixture-asset-id', 'fixture-uuid', 'fixture', 1, 'content://media/fixture/audio/media/1', 1, 'scan-fixture', 1024, 'audio/mpeg', 1)")
            execSQL("INSERT INTO scan_state (`key`, value) VALUES ('last_success', 123)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).apply {
            query("SELECT id, serverId FROM tracks").use { it.moveToFirst(); assertEquals("fixture-uuid", it.getString(0)); assertEquals("fixture-server-id", it.getString(1)) }
            query("SELECT trackId FROM media_assets").use { it.moveToFirst(); assertEquals("fixture-uuid", it.getString(0)) }
            query("SELECT value FROM scan_state").use { it.moveToFirst(); assertEquals(123, it.getInt(0)) }
            execSQL("INSERT INTO favourites (trackId, createdAt) VALUES ('fixture-uuid', 1)")
            execSQL("INSERT INTO playlists (id, serverId, name, createdAt, updatedAt, localRevision) VALUES ('playlist-fixture', NULL, 'Fixture playlist', 1, 1, 1)")
            execSQL("INSERT INTO playlist_entries (id, playlistId, trackId, position, addedAt) VALUES ('entry-fixture', 'playlist-fixture', 'fixture-uuid', 0, 1)")
            close()
        }
    }
}
