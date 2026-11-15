package dev.violet.music.core.data

import androidx.paging.PagingData
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.PlaylistQueueItem
import dev.violet.music.core.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun playlists(): Flow<PagingData<PlaylistSummary>>
    fun observePlaylist(id: String): Flow<PlaylistSummary?>
    fun entries(id: String): Flow<PagingData<PlaylistEntry>>
    suspend fun queue(id: String): List<PlaylistQueueItem>
    suspend fun create(name: String): String
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun setFavourite(trackId: String, favourite: Boolean)
    suspend fun addTrack(playlistId: String, trackId: String)
    suspend fun removeEntry(entryId: String)
    suspend fun moveEntry(entryId: String, position: Int)
}
