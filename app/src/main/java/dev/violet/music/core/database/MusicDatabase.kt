package dev.violet.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, MediaAssetEntity::class, ScanStateEntity::class, FavouriteEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun collectionDao(): CollectionDao
}
