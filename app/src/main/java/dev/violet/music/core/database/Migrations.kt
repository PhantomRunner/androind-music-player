package dev.violet.music.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS favourites (trackId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(trackId), FOREIGN KEY(trackId) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id TEXT NOT NULL, serverId TEXT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, localRevision INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_entries (id TEXT NOT NULL, playlistId TEXT NOT NULL, trackId TEXT NOT NULL, position INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_entries_playlistId_position ON playlist_entries(playlistId, position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_entries_trackId ON playlist_entries(trackId)")
    }
}
