package dev.violet.music.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.data.LibraryRepository
import dev.violet.music.core.data.SettingsRepository
import dev.violet.music.core.model.SummarySort
import dev.violet.music.core.model.TrackFilter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchCategory { TRACKS, ALBUMS, ARTISTS, PLAYLISTS }

enum class SearchMode { LANDING, INPUT, BROWSE }

data class SearchUiState(
    val mode: SearchMode = SearchMode.LANDING,
    val category: SearchCategory = SearchCategory.TRACKS,
    val query: String = "",
    val effectiveQuery: String = "",
    val recent: List<String> = emptyList(),
)

@HiltViewModel
@OptIn(FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val collections: CollectionRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mode = saved.getStateFlow("search.mode", SearchMode.LANDING.name)
    private val category = saved.getStateFlow("search.category", SearchCategory.TRACKS.name)
    private val query = saved.getStateFlow("search.query", "")
    private val effectiveQuery = query.map(String::trim).distinctUntilChanged().debounce {
        if (it.isEmpty()) 0L else 300L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, query.value.trim())
    private val mutableHistoryErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val historyErrors = mutableHistoryErrors.asSharedFlow()
    val uiState = combine(
        mode, category, query, effectiveQuery,
        settings.recentSearches.catch {
            mutableHistoryErrors.tryEmit(Unit)
            emit(emptyList())
        },
    ) { surface, section, text, applied, recent ->
        SearchUiState(SearchMode.valueOf(surface), SearchCategory.valueOf(section), text, applied, recent)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SearchUiState(SearchMode.valueOf(mode.value), SearchCategory.valueOf(category.value), query.value, effectiveQuery.value),
    )

    fun openInput() {
        select(SearchCategory.TRACKS)
        saved["search.mode"] = SearchMode.INPUT.name
    }

    fun browse(value: SearchCategory) {
        select(value)
        saved["search.mode"] = SearchMode.BROWSE.name
    }

    fun close() {
        saved["search.mode"] = SearchMode.LANDING.name
        updateQuery("")
    }

    fun select(value: SearchCategory) {
        saved["search.category"] = value.name
    }

    fun updateQuery(value: String) {
        saved["search.query"] = value
    }

    fun submit(query: String) = updateHistory { settings.recordSearch(query) }

    fun repeat(query: String) {
        updateQuery(query)
        submit(query)
    }

    fun remove(query: String) = updateHistory { settings.removeSearch(query) }

    fun clearHistory() = updateHistory { settings.clearSearches() }

    fun tracks(query: String) = library.tracks(TrackFilter(query = query))

    fun albums(query: String) = library.albums(query)

    fun artists(query: String) = library.artists(query)

    fun playlists(query: String) = collections.filteredPlaylists(query, SummarySort.NAME_ASC)

    private fun updateHistory(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableHistoryErrors.tryEmit(Unit)
            }
        }
    }
}
