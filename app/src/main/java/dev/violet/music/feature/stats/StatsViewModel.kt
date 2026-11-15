package dev.violet.music.feature.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CalendarClock
import dev.violet.music.core.data.HistoryRepository
import dev.violet.music.core.data.SettingsRepository
import dev.violet.music.core.model.HistoryPeriod
import dev.violet.music.core.model.ListeningStats
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class StatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val period: HistoryPeriod = HistoryPeriod.MONTH,
    val page: Int = 0,
    val stats: ListeningStats = ListeningStats(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel
@Inject
constructor(
    history: HistoryRepository,
    settings: SettingsRepository,
    clock: CalendarClock,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val period = saved.getStateFlow<String?>("stats.period", null)
    private val page = saved.getStateFlow("stats.page", 0)
    val uiState =
        combine(settings.preferences, period, page, clock.changes) { prefs, chosen, p, _ ->
                (chosen?.let(HistoryPeriod::valueOf) ?: prefs.period) to p
            }
            .flatMapLatest { (period, page) ->
                history
                    .stats(period, page)
                    .map { StatsUiState(false, false, period, page, it) }
                    .onStart { emit(StatsUiState(period = period, page = page)) }
                    .catch {
                        emit(
                            StatsUiState(
                                loading = false,
                                error = true,
                                period = period,
                                page = page,
                            )
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun period(value: HistoryPeriod) {
        saved["stats.period"] = value.name
        saved["stats.page"] = 0
    }

    fun page(value: Int) {
        saved["stats.page"] = value.coerceAtLeast(0)
    }
}
