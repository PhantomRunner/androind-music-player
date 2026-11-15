package dev.violet.music.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.media.LibraryIndexer
import dev.violet.music.core.model.SummarySort
import dev.violet.music.core.model.TrackFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineLibraryRepository
@Inject
constructor(private val database: MusicDatabase, private val indexer: LibraryIndexer) :
    LibraryRepository {
    private val dao
        get() = database.libraryDao()

    private val pagingConfig =
        PagingConfig(pageSize = 50, initialLoadSize = 50, maxSize = 250, enablePlaceholders = false)
    override val scanStatus = indexer.status

    override fun observeStats() = dao.stats()

    override fun observeLastScan() = dao.lastScan()

    override fun observeRecentlyAdded() = dao.recentTracks()

    override fun observeTrack(id: String) = dao.track(id)

    override fun information(id: String) = dao.information(id)

    override fun tracks(filter: TrackFilter) =
        Pager(pagingConfig) {
                if (usesHistory(filter)) dao.queryTracks(trackQuery(filter))
                else dao.queryCatalogue(trackQuery(filter))
            }
            .flow

    override fun albums(query: String, sort: SummarySort, artistId: String?) =
        Pager(pagingConfig) { dao.queryAlbums(albumQuery(query, sort, artistId)) }.flow

    override fun artists(query: String, sort: SummarySort) =
        Pager(pagingConfig) { dao.queryArtists(artistQuery(query, sort)) }.flow

    override fun preview(filter: TrackFilter) =
        if (usesHistory(filter))
            dao.observeTracks(trackQuery(filter.copy(limit = filter.limit ?: 8)))
        else dao.observeCatalogue(trackQuery(filter.copy(limit = filter.limit ?: 8)))

    override suspend fun ids(
        filter: TrackFilter,
        availableOnly: Boolean,
        limit: Int?,
        offset: Int,
    ): List<String> {
        val ids =
            dao.selectIds(
                trackQuery(
                    filter,
                    idsOnly = true,
                    availableOnly = availableOnly && filter.limit == null,
                    limit = limit,
                    offset = offset,
                )
            )
        if (!availableOnly || filter.limit == null) return ids
        val available = ids.chunked(100).flatMap { dao.availableIds(it) }.toSet()
        return ids.filter { it in available }
    }

    override suspend fun refresh() = indexer.refresh()
}

internal fun searchPattern(query: String): String =
    query
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "%${it.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%" }
        .orEmpty()
