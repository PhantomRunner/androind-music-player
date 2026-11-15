package dev.violet.music.core.data

import androidx.room.withTransaction
import dev.violet.music.core.database.*
import dev.violet.music.core.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class SelectionRepository @Inject constructor(private val database: MusicDatabase) {
    fun changes(source: SelectionSource): kotlinx.coroutines.flow.Flow<*> =
        if (source.playlistId != null) database.collectionDao().entryChanges(source.playlistId)
        else
            kotlinx.coroutines.flow.combine(
                database.libraryDao().stats(),
                database.listeningDao().creditChanges(),
                database.listeningDao().overrideChanges(),
            ) { _, _, _ ->
                Unit
            }

    suspend fun refresh(items: List<SelectedTrack>): List<SelectedTrack> {
        val current =
            database.libraryDao().identities(items.map { it.trackId }.distinct()).associateBy {
                it.trackId
            }
        return items.mapNotNull { item ->
            current[item.trackId]?.let { item.copy(available = it.available) }
        }
    }

    suspend fun page(source: SelectionSource, offset: Int, limit: Int = 100): List<SelectedTrack> {
        source.playlistId?.let { id ->
            val filter = source.playlistFilter
            return database.collectionDao()
                .selectedEntries(id, searchPattern(filter.query), filter.sort.name, limit, offset)
        }
        val filter = source.filter ?: return emptyList()
        val size = filter.limit?.let { minOf(limit, (it - offset).coerceAtLeast(0)) } ?: limit
        return database
            .libraryDao()
            .selection(trackQuery(filter, limit = size, offset = offset, selection = true))
    }

    /** Applies at most one page of selected items in a transaction. */
    suspend fun mutate(
        action: BulkAction,
        items: List<SelectedTrack>,
        playlistId: String?,
    ): BulkResult {
        require(items.size <= 100)
        return database.withTransaction {
            val dao = database.collectionDao()
            var updated = 0
            var skipped = 0
            for (item in items) {
                currentCoroutineContext().ensureActive()
                if (database.libraryDao().findTrack(item.trackId) == null) {
                    skipped++
                    continue
                }
                when (action) {
                    BulkAction.ADD_FAVOURITES ->
                        dao.insertFavourite(
                            FavouriteEntity(item.trackId, System.currentTimeMillis())
                        )
                    BulkAction.REMOVE_FAVOURITES -> dao.removeFavourite(item.trackId)
                    BulkAction.REMOVE_PLAYLIST -> {
                        val entry = dao.entry(item.key)
                        if (entry == null || entry.playlistId != playlistId) {
                            skipped++
                            continue
                        }
                        dao.deleteEntry(entry.id)
                        dao.compactAfterRemoval(entry.playlistId, entry.position)
                    }
                    BulkAction.ADD_QUEUE -> error("Queue actions belong to the playback controller")
                }
                updated++
            }
            if (playlistId != null && action == BulkAction.REMOVE_PLAYLIST && updated > 0)
                dao.touch(playlistId, System.currentTimeMillis())
            BulkResult(updated, skipped)
        }
    }
}
