package dev.violet.music.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.model.PlaylistSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PlaylistUiState(val loading: Boolean = true, val playlist: PlaylistSummary? = null)

@HiltViewModel
class CollectionViewModel
@Inject
constructor(
    private val repository: CollectionRepository,
    private val library: dev.violet.music.core.data.LibraryRepository,
    private val artists: dev.violet.music.core.data.ArtistRepository,
    private val covers: dev.violet.music.core.data.PlaylistCoverRepository,
) : ViewModel() {
    val playlists = repository.playlists().cachedIn(viewModelScope)
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()
    private val mutableCoverBusy = MutableStateFlow(false)
    val coverBusy = mutableCoverBusy.asStateFlow()

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

    fun entries(id: String) = repository.entries(id)

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

    fun add(playlistId: String, trackId: String) = edit { repository.addTrack(playlistId, trackId) }

    fun remove(entryId: String) = edit { repository.removeEntry(entryId) }

    fun move(entryId: String, position: Int) = edit { repository.moveEntry(entryId, position) }
}
