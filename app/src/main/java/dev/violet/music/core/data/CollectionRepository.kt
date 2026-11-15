package dev.violet.music.core.data

import androidx.paging.PagingData
import dev.violet.music.core.model.PlaylistAddItem
import dev.violet.music.core.model.PlaylistAddResult
import dev.violet.music.core.model.PlaylistDuplicatePolicy
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.model.PlaylistQueueItem
import dev.violet.music.core.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun playlists(): Flow<PagingData<PlaylistSummary>>

    fun recentPlaylists(): Flow<List<PlaylistSummary>>

    fun filteredPlaylists(
        query: String,
        sort: dev.violet.music.core.model.SummarySort,
    ): Flow<PagingData<PlaylistSummary>>

    fun observePlaylist(id: String): Flow<PlaylistSummary?>

    fun entries(id: String, filter: PlaylistFilter = PlaylistFilter()): Flow<PagingData<PlaylistEntry>>

    fun hasPlayableTracks(id: String, query: String): Flow<Boolean>

    suspend fun queue(id: String, filter: PlaylistFilter = PlaylistFilter()): List<PlaylistQueueItem>

    suspend fun create(name: String): String

    suspend fun rename(id: String, name: String)

    suspend fun delete(id: String): String?

    suspend fun setFavourite(trackId: String, favourite: Boolean)

    suspend fun addTracks(
        playlistId: String,
        items: List<PlaylistAddItem>,
        operationId: String,
        policy: PlaylistDuplicatePolicy = PlaylistDuplicatePolicy.Check,
    ): PlaylistAddResult

    suspend fun removeEntry(entryId: String)

    suspend fun moveEntry(entryId: String, position: Int)
}
