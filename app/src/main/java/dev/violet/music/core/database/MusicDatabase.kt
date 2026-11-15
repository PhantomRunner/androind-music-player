package dev.violet.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            TrackEntity::class,
            MediaAssetEntity::class,
            ScanStateEntity::class,
            FavouriteEntity::class,
            PlaylistEntity::class,
            PlaylistEntryEntity::class,
            ArtistEntity::class,
            ArtistCreditEntity::class,
            ArtistOverrideEntity::class,
            PreferenceEntity::class,
            PlaybackEventEntity::class,
            ListeningSegmentEntity::class,
        ],
    version = 4,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun collectionDao(): CollectionDao

    abstract fun experienceDao(): ExperienceDao

    abstract fun listeningDao(): ListeningDao
}
