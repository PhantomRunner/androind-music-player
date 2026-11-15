package dev.violet.music.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import dev.violet.music.core.database.FavouriteEntity
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.PlaylistEntity
import dev.violet.music.core.database.PlaylistEntryEntity
import dev.violet.music.core.model.PlaylistAddItem
import dev.violet.music.core.model.PlaylistAddResult
import dev.violet.music.core.model.PlaylistDuplicatePolicy
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.model.PlaylistQueueItem
import dev.violet.music.core.model.validatedPlaylistName
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map

@Singleton
class RoomCollectionRepository @Inject constructor(private val database: MusicDatabase) :
    CollectionRepository {
    private val dao
        get() = database.collectionDao()

    private val config =
        PagingConfig(pageSize = 50, initialLoadSize = 50, maxSize = 250, enablePlaceholders = false)

    override fun playlists() = Pager(config) { dao.playlists() }.flow

    override fun recentPlaylists() = dao.recentPlaylists()

    override fun filteredPlaylists(query: String, sort: dev.violet.music.core.model.SummarySort) =
        Pager(config) {
                dao.queryPlaylists(
                    androidx.sqlite.db.SimpleSQLiteQuery(
                        "SELECT p.id,p.name,p.coverFileName,COUNT(e.id) AS trackCount,MAX(t.artworkUri) AS artworkUri FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId=p.id LEFT JOIN tracks t ON t.id=e.trackId WHERE p.name LIKE ? ESCAPE '!' GROUP BY p.id ORDER BY ${summaryOrder(sort,"p.name","p.updatedAt","p.createdAt")},p.id",
                        arrayOf(searchPattern(query).ifEmpty { "%" }),
                    )
                )
            }
            .flow

    override fun observePlaylist(id: String) = dao.playlist(id)

    override fun entries(id: String, filter: PlaylistFilter) =
        Pager(config) { dao.entries(id, searchPattern(filter.query), filter.sort.name) }
            .flow
            .map { page -> page.map { PlaylistEntry(it.entryId, it.position, it.track) } }

    override fun hasPlayableTracks(id: String, query: String) =
        dao.hasPlayableTracks(id, searchPattern(query))

    override suspend fun queue(id: String, filter: PlaylistFilter) =
        dao.queue(id, searchPattern(filter.query), filter.sort.name)
            .map { PlaylistQueueItem(it.entryId, it.trackId) }

    override suspend fun create(name: String): String {
        val validName = validatedPlaylistName(name)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertPlaylist(PlaylistEntity(id, name = validName, createdAt = now, updatedAt = now))
        return id
    }

    override suspend fun rename(id: String, name: String) {
        dao.rename(id, validatedPlaylistName(name), System.currentTimeMillis())
    }

    override suspend fun delete(id: String): String? =
        database.withTransaction {
            val cover = dao.findPlaylist(id)?.coverFileName
            dao.deletePlaylist(id)
            cover
        }

    override suspend fun setFavourite(trackId: String, favourite: Boolean) {
        if (favourite) dao.insertFavourite(FavouriteEntity(trackId, System.currentTimeMillis()))
        else dao.removeFavourite(trackId)
    }

    override suspend fun addTracks(
        playlistId: String,
        items: List<PlaylistAddItem>,
        operationId: String,
        policy: PlaylistDuplicatePolicy,
    ): PlaylistAddResult = database.withTransaction {
        if (dao.findPlaylist(playlistId) == null) return@withTransaction PlaylistAddResult.MissingPlaylist

        val seen = mutableSetOf<String>()
        val conflicts = mutableSetOf<String>()
        val additions = mutableListOf<Pair<String, String>>()
        var applied = 0
        var missing = 0
        for (batch in items.distinctBy { it.key }.chunked(100)) {
            currentCoroutineContext().ensureActive()
            val entryIds = batch.map { item ->
                UUID.nameUUIDFromBytes("$playlistId:$operationId:${item.key}".toByteArray(Charsets.UTF_8))
                    .toString()
            }
            val existing = dao.existingEntryIds(entryIds).toHashSet()
            val trackIds = batch.map { it.trackId }.distinct()
            val known = database.libraryDao().identities(trackIds).mapTo(mutableSetOf()) { it.trackId }
            seen.addAll(dao.memberTrackIds(playlistId, trackIds))

            for ((index, item) in batch.withIndex()) {
                val entryId = entryIds[index]
                if (entryId in existing) {
                    applied++
                    continue
                }
                if (item.trackId !in known) {
                    missing++
                    continue
                }
                if (!seen.add(item.trackId)) {
                    conflicts += item.key
                    if (policy == PlaylistDuplicatePolicy.Skip) continue
                }
                additions += entryId to item.trackId
            }
        }
        val approved = (policy as? PlaylistDuplicatePolicy.Allow)?.keys.orEmpty()
        if (policy != PlaylistDuplicatePolicy.Skip && conflicts.any { it !in approved }) {
            return@withTransaction PlaylistAddResult.NeedsConfirmation(conflicts.toSet())
        }
        if (additions.isNotEmpty()) {
            var position = dao.nextPosition(playlistId)
            val now = System.currentTimeMillis()
            for (batch in additions.chunked(100)) {
                dao.insertEntries(batch.map { (id, trackId) ->
                    PlaylistEntryEntity(id, playlistId, trackId, position++, now)
                })
            }
            dao.touch(playlistId, now)
        }
        PlaylistAddResult.Completed(
            added = applied + additions.size,
            duplicatesSkipped = if (policy == PlaylistDuplicatePolicy.Skip) conflicts.size else 0,
            missing = missing,
        )
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
