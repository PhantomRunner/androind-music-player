package dev.violet.music.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.model.PlaylistAddItem
import dev.violet.music.core.model.PlaylistAddResult
import dev.violet.music.core.model.PlaylistDuplicatePolicy
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.model.PlaylistSummary
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistUiState(val loading: Boolean = true, val playlist: PlaylistSummary? = null)

data class PlaylistAddOrigin(val selectionContext: String, val pickerEntryId: String? = null)

data class PlaylistAddCompletion(
    val result: PlaylistAddResult.Completed,
    val origin: PlaylistAddOrigin?,
)

data class PlaylistAddUiState(
    val working: Boolean = false,
    val duplicateCount: Int = 0,
    val completion: PlaylistAddCompletion? = null,
) {
    val pending: Boolean get() = working || duplicateCount > 0
}

private data class PlaylistAddRequest(
    val playlistId: String,
    val items: List<PlaylistAddItem>,
    val operationId: String,
    val origin: PlaylistAddOrigin?,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionViewModel
@Inject
constructor(
    private val repository: CollectionRepository,
    private val library: dev.violet.music.core.data.LibraryRepository,
    private val artists: dev.violet.music.core.data.ArtistRepository,
    private val covers: dev.violet.music.core.data.PlaylistCoverRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val playlists = repository.playlists().cachedIn(viewModelScope)
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()
    private val mutableCoverBusy = MutableStateFlow(false)
    val coverBusy = mutableCoverBusy.asStateFlow()
    private val mutableAddition = MutableStateFlow(PlaylistAddUiState())
    val addition = mutableAddition.asStateFlow()
    private var addRequest: PlaylistAddRequest? = null
    private var duplicateKeys = emptySet<String>()
    val addTracksQuery = saved.getStateFlow("playlist.add.query", "")
    val addTracksFilter =
        addTracksQuery
            .debounce(300)
            .map { dev.violet.music.core.model.TrackFilter(query = it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                dev.violet.music.core.model.TrackFilter(query = addTracksQuery.value),
            )
    val addTrackCandidates = addTracksFilter.flatMapLatest(library::tracks).cachedIn(viewModelScope)

    fun updateAddTracksQuery(value: String) {
        saved["playlist.add.query"] = value
    }

    fun applyCover(id: String, uri: android.net.Uri) = edit {
        mutableCoverBusy.value = true
        try {
            covers.apply(id, uri)
        } finally {
            mutableCoverBusy.value = false
        }
    }

    fun removeCover(id: String) = edit { covers.remove(id) }

    fun dismissError() {
        mutableError.value = false
    }

    fun playlist(id: String): Flow<PlaylistUiState> =
        repository
            .observePlaylist(id)
            .map { PlaylistUiState(loading = false, playlist = it) }
            .catch { emit(PlaylistUiState(loading = false)) }

    fun entries(id: String, filter: PlaylistFilter) = repository.entries(id, filter)

    fun hasPlayableTracks(id: String, query: String) = repository.hasPlayableTracks(id, query)

    fun track(id: String) = library.observeTrack(id)

    fun information(id: String) = library.information(id)

    fun credits(id: String) = artists.credits(id)

    fun correct(id: String, role: dev.violet.music.core.model.ArtistRole, names: List<String>) =
        edit {
            artists.correct(id, role, names)
        }

    fun resetCredits(id: String, role: dev.violet.music.core.model.ArtistRole) = edit {
        artists.reset(id, role)
    }

    private fun edit(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = true
            }
        }
    }

    fun create(name: String, onCreated: (String) -> Unit) = edit {
        onCreated(repository.create(name))
    }

    fun rename(id: String, name: String) = edit { repository.rename(id, name) }

    fun delete(id: String, onDeleted: () -> Unit) = edit {
        val name = repository.delete(id)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            covers.removeOwned(name)
        }
        onDeleted()
    }

    fun favourite(id: String, value: Boolean) = edit { repository.setFavourite(id, value) }

    fun add(playlistId: String, trackId: String) =
        add(playlistId, listOf(PlaylistAddItem(trackId, trackId)), null)

    fun add(playlistId: String, items: List<PlaylistAddItem>, origin: PlaylistAddOrigin?) {
        if (addRequest != null || mutableAddition.value.completion != null || items.isEmpty()) return
        addRequest = PlaylistAddRequest(playlistId, items, UUID.randomUUID().toString(), origin)
        performAdd(PlaylistDuplicatePolicy.Check)
    }

    fun resolveDuplicates(allow: Boolean) {
        if (duplicateKeys.isEmpty() || mutableAddition.value.working) return
        performAdd(
            if (allow) PlaylistDuplicatePolicy.Allow(duplicateKeys) else PlaylistDuplicatePolicy.Skip
        )
    }

    fun cancelAddition() {
        if (mutableAddition.value.working) return
        addRequest = null
        duplicateKeys = emptySet()
        mutableAddition.value = PlaylistAddUiState()
    }

    private fun performAdd(policy: PlaylistDuplicatePolicy) {
        val request = addRequest ?: return
        mutableAddition.value = PlaylistAddUiState(working = true)
        viewModelScope.launch {
            try {
                when (val result = repository.addTracks(request.playlistId, request.items, request.operationId, policy)) {
                    is PlaylistAddResult.NeedsConfirmation -> {
                        duplicateKeys = result.keys
                        mutableAddition.value = PlaylistAddUiState(duplicateCount = result.keys.size)
                    }
                    is PlaylistAddResult.Completed -> {
                        addRequest = null
                        duplicateKeys = emptySet()
                        mutableAddition.value = PlaylistAddUiState(
                            completion = PlaylistAddCompletion(result, request.origin)
                        )
                    }
                    PlaylistAddResult.MissingPlaylist -> {
                        mutableAddition.value = PlaylistAddUiState()
                        cancelAddition()
                        mutableError.value = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableAddition.value = PlaylistAddUiState()
                cancelAddition()
                mutableError.value = true
            }
        }
    }

    fun remove(entryId: String) = edit { repository.removeEntry(entryId) }

    fun move(entryId: String, position: Int) = edit { repository.moveEntry(entryId, position) }
}
