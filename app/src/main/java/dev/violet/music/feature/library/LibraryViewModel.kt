package dev.violet.music.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.*
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.model.*
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryUiState(
    val stats: LibraryStats = LibraryStats(),
    val recentTracks: List<Track> = emptyList(),
    val lastScan: Long? = null,
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val hasPermission: Boolean = false,
    val loading: Boolean = true,
    val catalogueError: Boolean = false,
    val recentSort: Boolean = false,
    val libraryQuery: String = "",
    val category: LibraryCategory = LibraryCategory.ALL,
    val preferences: LibraryPreferences = LibraryPreferences(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val repository: LibraryRepository,
    collections: CollectionRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
    private val clock: CalendarClock,
    private val artistRepository: ArtistRepository,
) : ViewModel() {
    private val mutablePreferenceErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val preferenceErrors = mutablePreferenceErrors.asSharedFlow()

    private fun updatePreference(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutablePreferenceErrors.tryEmit(Unit)
            }
        }
    }

    private val permission = MutableStateFlow(false)
    private var checkedOnEntry = false
    private val libraryQuery = saved.getStateFlow("library.query", "")
    private val category = saved.getStateFlow("library.category", LibraryCategory.ALL.name)
    val preferences =
        combine(settings.preferences, clock.changes) { prefs, _ -> prefs }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPreferences())
    private val catalogue =
        combine(
                repository.observeStats(),
                repository.observeRecentlyAdded(),
                repository.observeLastScan(),
                repository.scanStatus,
            ) { stats, recent, lastScan, scan ->
                LibraryUiState(stats, recent, lastScan, scan, loading = false)
            }
            .catch { emit(LibraryUiState(loading = false, catalogueError = true)) }
    val uiState =
        combine(catalogue, permission, preferences, libraryQuery, category) {
                state,
                granted,
                prefs,
                text,
                category ->
                state.copy(
                    hasPermission = granted,
                    recentSort = prefs.trackSort() == TrackSort.NEWEST,
                    libraryQuery = text,
                    category = LibraryCategory.valueOf(category),
                    preferences = prefs,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
    private val browse =
        combine(preferences, libraryQuery.debounce(300), clock.changes) { prefs, text, _ ->
            TrackFilter(query = text, sort = prefs.trackSort(), periodStart = prefs.period.start())
        }
    val tracks = browse.flatMapLatest(repository::tracks).cachedIn(viewModelScope)
    val favourites =
        browse
            .flatMapLatest { repository.tracks(it.copy(favouritesOnly = true)) }
            .cachedIn(viewModelScope)
    val albums =
        combine(preferences, libraryQuery.debounce(300)) { prefs, text ->
                text to prefs.summarySort(LibraryCategory.ALBUMS)
            }
            .flatMapLatest { (text, sort) -> repository.albums(text, sort) }
            .cachedIn(viewModelScope)
    val artists =
        combine(preferences, libraryQuery.debounce(300)) { prefs, text ->
                text to prefs.summarySort(LibraryCategory.ARTISTS)
            }
            .flatMapLatest { (text, sort) -> repository.artists(text, sort) }
            .cachedIn(viewModelScope)
    val playlists =
        combine(preferences, libraryQuery.debounce(300)) { prefs, text ->
                text to prefs.summarySort(LibraryCategory.PLAYLISTS)
            }
            .flatMapLatest { (text, sort) -> collections.filteredPlaylists(text, sort) }
            .cachedIn(viewModelScope)
    fun updateLibraryQuery(value: String) {
        saved["library.query"] = value
    }

    fun category(value: LibraryCategory) {
        saved["library.category"] = value.name
    }

    fun sortByRecent(value: Boolean) =
        sort(LibraryCategory.TRACKS, if (value) TrackSort.NEWEST.name else TrackSort.TITLE_ASC.name)

    fun sort(category: LibraryCategory, value: String) {
        updatePreference { settings.sort(category, value) }
    }

    fun mode(category: LibraryCategory, value: DisplayMode) {
        updatePreference { settings.mode(category, value) }
    }

    fun period(value: HistoryPeriod) {
        updatePreference { settings.period(value) }
    }

    fun filter() =
        TrackFilter(
            query = uiState.value.libraryQuery,
            sort = preferences.value.trackSort(),
            periodStart = preferences.value.period.start(),
        )

    fun collectionFilter(kind: String): TrackFilter =
        when (kind) {
            "quick" ->
                TrackFilter(
                    sort = TrackSort.MOST_PLAYED,
                    qualifiedOnly = true,
                    periodStart = preferences.value.period.start(),
                )
            "most" ->
                TrackFilter(
                    sort = TrackSort.MOST_PLAYED,
                    qualifiedOnly = true,
                    periodStart = preferences.value.period.start(),
                    limit = 50,
                )
            "recent" -> TrackFilter(sort = TrackSort.RECENTLY_PLAYED, qualifiedOnly = true)
            "added" -> TrackFilter(sort = TrackSort.NEWEST)
            else -> TrackFilter(favouritesOnly = true)
        }

    fun collectionTracks(kind: String) =
        combine(preferences, clock.changes) { _, _ -> collectionFilter(kind) }
            .flatMapLatest(repository::tracks)
            .cachedIn(viewModelScope)

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

    fun artistTracks(id: String) = repository.tracks(TrackFilter(artistId = id))

    fun artistAlbums(id: String) = repository.albums(artistId = id)

    fun artistName(id: String) = artistRepository.name(id)
}
