package dev.violet.music.core.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.data.LibraryRepository
import dev.violet.music.core.data.OfflineLibraryRepository
import dev.violet.music.core.data.RoomCollectionRepository
import dev.violet.music.core.database.ALL_MIGRATIONS
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.RoomCatalogueWriter
import dev.violet.music.core.media.CatalogueWriter
import dev.violet.music.core.media.DeviceScanner
import dev.violet.music.core.media.MediaStoreScanner
import dev.violet.music.core.playback.Media3PlaybackController
import dev.violet.music.core.playback.PlaybackController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun creditReader(
        implementation: dev.violet.music.core.media.Media3AudioCreditReader
    ): dev.violet.music.core.media.AudioCreditReader

    @Binds abstract fun playback(implementation: Media3PlaybackController): PlaybackController

    @Binds abstract fun repository(implementation: OfflineLibraryRepository): LibraryRepository

    @Binds abstract fun collections(implementation: RoomCollectionRepository): CollectionRepository

    @Binds abstract fun scanner(implementation: MediaStoreScanner): DeviceScanner

    @Binds abstract fun writer(implementation: RoomCatalogueWriter): CatalogueWriter

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): MusicDatabase =
            Room.databaseBuilder(context, MusicDatabase::class.java, "violet-catalogue.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
