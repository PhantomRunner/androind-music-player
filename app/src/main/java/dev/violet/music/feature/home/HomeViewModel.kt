package dev.violet.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CalendarClock
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.data.LibraryRepository
import dev.violet.music.core.data.SettingsRepository
import dev.violet.music.core.model.*
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val period: HistoryPeriod = HistoryPeriod.MONTH,
    val quickPicks: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel
@Inject
constructor(
    library: LibraryRepository,
    collections: CollectionRepository,
    settings: SettingsRepository,
    clock: CalendarClock,
) : ViewModel() {
    val uiState =
        combine(settings.preferences, clock.changes) { prefs, _ -> prefs.period }
            .flatMapLatest { period ->
                combine(
                    library.preview(
                        TrackFilter(
                            sort = TrackSort.MOST_PLAYED,
                            periodStart = period.start(),
                            qualifiedOnly = true,
                            limit = 8,
                        )
                    ),
                    library.preview(
                        TrackFilter(
                            sort = TrackSort.RECENTLY_PLAYED,
                            qualifiedOnly = true,
                            limit = 8,
                        )
                    ),
                    library.preview(TrackFilter(sort = TrackSort.NEWEST, limit = 8)),
                    collections.recentPlaylists(),
                ) { quick, recent, added, playlists ->
                    HomeUiState(false, false, period, quick, recent, added, playlists)
                }
            }
            .catch { emit(HomeUiState(loading = false, error = true)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
