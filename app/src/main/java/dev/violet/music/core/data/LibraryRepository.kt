package dev.violet.music.core.data

import androidx.paging.PagingData
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.LibraryStats
import dev.violet.music.core.model.SummarySort
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    val scanStatus: StateFlow<ScanStatus>

    fun observeStats(): Flow<LibraryStats>

    fun observeLastScan(): Flow<Long?>

    fun observeRecentlyAdded(): Flow<List<Track>>

    fun observeTrack(id: String): Flow<Track?>

    fun information(id: String): Flow<dev.violet.music.core.model.TrackInformation?>

    fun tracks(filter: TrackFilter = TrackFilter()): Flow<PagingData<Track>>

    fun albums(
        query: String = "",
        sort: SummarySort = SummarySort.NAME_ASC,
        artistId: String? = null,
    ): Flow<PagingData<AlbumSummary>>

    fun artists(
        query: String = "",
        sort: SummarySort = SummarySort.NAME_ASC,
    ): Flow<PagingData<ArtistSummary>>

    fun preview(filter: TrackFilter): Flow<List<Track>>

    suspend fun ids(
        filter: TrackFilter,
        availableOnly: Boolean = false,
        limit: Int? = filter.limit,
        offset: Int = 0,
    ): List<String>

    suspend fun refresh()
}
