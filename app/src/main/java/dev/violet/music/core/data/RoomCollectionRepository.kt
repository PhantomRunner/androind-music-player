package dev.violet.music.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import dev.violet.music.core.database.FavouriteEntity
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.PlaylistEntity
import dev.violet.music.core.database.PlaylistEntryEntity
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.PlaylistQueueItem
import dev.violet.music.core.model.validatedPlaylistName
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class RoomCollectionRepository @Inject constructor(private val database: MusicDatabase) : CollectionRepository {
    private val dao get() = database.collectionDao()
    private val config = PagingConfig(pageSize = 50, initialLoadSize = 50, maxSize = 250, enablePlaceholders = false)
    override fun playlists() = Pager(config) { dao.playlists() }.flow
    override fun observePlaylist(id: String) = dao.playlist(id)
    override fun entries(id: String) = Pager(config) { dao.entries(id) }.flow.map { page ->
        page.map { PlaylistEntry(it.entryId, it.position, it.track) }
    }
    override suspend fun queue(id: String) = dao.queue(id).map { PlaylistQueueItem(it.entryId, it.trackId) }

    override suspend fun create(name: String): String {
        val validName = validatedPlaylistName(name)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertPlaylist(PlaylistEntity(id, name = validName, createdAt = now, updatedAt = now))
        return id
    }
    override suspend fun rename(id: String, name: String) { dao.rename(id, validatedPlaylistName(name), System.currentTimeMillis()) }
    override suspend fun delete(id: String) { dao.deletePlaylist(id) }
    override suspend fun setFavourite(trackId: String, favourite: Boolean) {
        if (favourite) dao.insertFavourite(FavouriteEntity(trackId, System.currentTimeMillis()))
        else dao.removeFavourite(trackId)
    }
    override suspend fun addTrack(playlistId: String, trackId: String) {
        database.withTransaction {
            dao.insertEntry(PlaylistEntryEntity(UUID.randomUUID().toString(), playlistId, trackId, dao.nextPosition(playlistId), System.currentTimeMillis()))
            dao.touch(playlistId, System.currentTimeMillis())
        }
    }
    override suspend fun removeEntry(entryId: String) {
        database.withTransaction {
            val entry = dao.entry(entryId) ?: return@withTransaction
            dao.deleteEntry(entryId)
            dao.compactAfterRemoval(entry.playlistId, entry.position)
            dao.touch(entry.playlistId, System.currentTimeMillis())
        }
    }
    override suspend fun moveEntry(entryId: String, position: Int) {
        database.withTransaction {
            val entry = dao.entry(entryId) ?: return@withTransaction
            val target = position.coerceIn(0, dao.entryCount(entry.playlistId) - 1)
            if (target == entry.position) return@withTransaction
            dao.shiftRange(entry.playlistId, entry.id, entry.position, target)
            dao.setPosition(entry.id, target)
            dao.touch(entry.playlistId, System.currentTimeMillis())
        }
    }
}
