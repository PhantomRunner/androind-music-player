package dev.violet.music.core.data

import androidx.paging.PagingData
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.LibraryStats
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    val scanStatus: StateFlow<ScanStatus>
    fun observeStats(): Flow<LibraryStats>
    fun observeLastScan(): Flow<Long?>
    fun observeRecentlyAdded(): Flow<List<Track>>
    fun tracks(filter: TrackFilter = TrackFilter()): Flow<PagingData<Track>>
    fun albums(): Flow<PagingData<AlbumSummary>>
    fun artists(): Flow<PagingData<ArtistSummary>>
    suspend fun refresh()
}
