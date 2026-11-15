package dev.violet.music.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.LibraryRepository
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.model.LibraryStats
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val stats: LibraryStats = LibraryStats(),
    val recentTracks: List<Track> = emptyList(),
    val lastScan: Long? = null,
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val query: String = "",
    val hasPermission: Boolean = false,
    val loading: Boolean = true,
    val catalogueError: Boolean = false,
    val recentSort: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val permission = MutableStateFlow(false)
    private var checkedOnEntry = false
    private val query = savedStateHandle.getStateFlow("query", "")
    private val recentSort = savedStateHandle.getStateFlow("recentSort", false)
    private val catalogue = combine(
        repository.observeStats(), repository.observeRecentlyAdded(), repository.observeLastScan(),
        repository.scanStatus, query,
    ) { stats, recent, lastScan, scan, text ->
        LibraryUiState(stats, recent, lastScan, scan, text, loading = false)
    }.catch { emit(LibraryUiState(loading = false, catalogueError = true)) }

    val uiState = combine(catalogue, permission, recentSort) { state, granted, sort ->
        state.copy(hasPermission = granted, recentSort = sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val tracks = recentSort.flatMapLatest { repository.tracks(TrackFilter(recentlyAddedFirst = it)) }.cachedIn(viewModelScope)
    val favourites = recentSort.flatMapLatest { repository.tracks(TrackFilter(recentlyAddedFirst = it, favouritesOnly = true)) }.cachedIn(viewModelScope)
    val albums = repository.albums().cachedIn(viewModelScope)
    val artists = repository.artists().cachedIn(viewModelScope)
    val searchResults = query.debounce(300).distinctUntilChanged().flatMapLatest {
        repository.tracks(TrackFilter(query = it))
    }.cachedIn(viewModelScope)

    fun updateQuery(value: String) { savedStateHandle["query"] = value }
    fun sortByRecent(value: Boolean) { savedStateHandle["recentSort"] = value }

    fun permissionChecked(granted: Boolean) {
        val shouldScan = granted && (!checkedOnEntry || !permission.value)
        checkedOnEntry = true
        permission.value = granted
        if (shouldScan) refresh()
    }

    fun refresh() {
        if (permission.value) viewModelScope.launch { repository.refresh() }
    }

    fun albumTracks(key: String) = repository.tracks(TrackFilter(albumKey = key))
    fun artistTracks(name: String) = repository.tracks(TrackFilter(artistName = name))
}
