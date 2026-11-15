package dev.violet.music.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.violet.music.core.data.artistKey
import java.util.UUID

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favourites (trackId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(trackId), FOREIGN KEY(trackId) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playlists (id TEXT NOT NULL, serverId TEXT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, localRevision INTEGER NOT NULL, PRIMARY KEY(id))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_entries (id TEXT NOT NULL, playlistId TEXT NOT NULL, trackId TEXT NOT NULL, position INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_entries_playlistId_position ON playlist_entries(playlistId, position)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_entries_trackId ON playlist_entries(trackId)"
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN albumArtistName TEXT")
            db.execSQL(
                "ALTER TABLE media_assets ADD COLUMN metadataVersion INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "CREATE TABLE artists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalizedName TEXT NOT NULL, serverId TEXT)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_artists_normalizedName ON artists(normalizedName)"
            )
            db.execSQL(
                "CREATE TABLE artist_credits (trackId TEXT NOT NULL, artistId TEXT NOT NULL, role TEXT NOT NULL, source TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(trackId, artistId, role, source), FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE RESTRICT, FOREIGN KEY(artistId) REFERENCES artists(id) ON DELETE RESTRICT)"
            )
            db.execSQL("CREATE INDEX index_artist_credits_artistId ON artist_credits(artistId)")
            db.execSQL(
                "CREATE INDEX index_artist_credits_trackId_role_source ON artist_credits(trackId, role, source)"
            )
            db.execSQL(
                "CREATE TABLE artist_overrides (trackId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(trackId, role), FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE TABLE preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE playback_events (id TEXT NOT NULL PRIMARY KEY, trackId TEXT NOT NULL, startedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL, qualified INTEGER NOT NULL, completed INTEGER NOT NULL, finalized INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX index_playback_events_trackId_startedAt ON playback_events(trackId, startedAt)"
            )
            db.execSQL(
                "CREATE INDEX index_playback_events_qualified_startedAt ON playback_events(qualified, startedAt)"
            )
            db.execSQL(
                "CREATE TABLE listening_segments (eventId TEXT NOT NULL, sequence INTEGER NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL, PRIMARY KEY(eventId, sequence), FOREIGN KEY(eventId) REFERENCES playback_events(id) ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX index_listening_segments_startedAt_endedAt ON listening_segments(startedAt, endedAt)"
            )
            db.query("SELECT id, artistName FROM tracks ORDER BY id").use { rows ->
                while (rows.moveToNext()) {
                    val name = rows.getString(1)
                    val key = artistKey(name)
                    var id: String? = null
                    db.query("SELECT id FROM artists WHERE normalizedName = ?", arrayOf(key)).use {
                        if (it.moveToFirst()) id = it.getString(0)
                    }
                    val artistId =
                        id
                            ?: UUID.randomUUID().toString().also {
                                db.execSQL(
                                    "INSERT INTO artists (id,name,normalizedName,serverId) VALUES (?,?,?,NULL)",
                                    arrayOf(it, name, key),
                                )
                            }
                    db.execSQL(
                        "INSERT INTO artist_credits VALUES (?,?,'TRACK','METADATA',0)",
                        arrayOf(rows.getString(0), artistId),
                    )
                }
            }
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN coverFileName TEXT")
        }
    }

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
