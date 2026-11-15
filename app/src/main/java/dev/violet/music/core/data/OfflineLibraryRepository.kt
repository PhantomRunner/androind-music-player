package dev.violet.music.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.media.LibraryIndexer
import dev.violet.music.core.model.TrackFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineLibraryRepository @Inject constructor(
    private val database: MusicDatabase,
    private val indexer: LibraryIndexer,
) : LibraryRepository {
    private val dao get() = database.libraryDao()
    private val pagingConfig = PagingConfig(
        pageSize = 50, initialLoadSize = 50, maxSize = 250, enablePlaceholders = false,
    )
    override val scanStatus = indexer.status
    override fun observeStats() = dao.stats()
    override fun observeLastScan() = dao.lastScan()
    override fun observeRecentlyAdded() = dao.recentTracks()
    override fun tracks(filter: TrackFilter) = Pager(pagingConfig) {
        dao.tracks(searchPattern(filter.query), filter.albumKey, filter.artistName, filter.recentlyAddedFirst)
    }.flow
    override fun albums() = Pager(pagingConfig) { dao.albums() }.flow
    override fun artists() = Pager(pagingConfig) { dao.artists() }.flow
    override suspend fun refresh() = indexer.refresh()
}

internal fun searchPattern(query: String): String = query.trim().takeIf { it.isNotEmpty() }?.let {
    "%${it.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%"
}.orEmpty()
