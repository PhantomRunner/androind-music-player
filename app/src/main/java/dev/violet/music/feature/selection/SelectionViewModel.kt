package dev.violet.music.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.SelectionRepository
import dev.violet.music.core.model.*
import dev.violet.music.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SelectionUiState(
    val active: Boolean = false,
    val selected: Map<String, SelectedTrack> = emptyMap(),
    val loading: Boolean = false,
    val running: Boolean = false,
    val processed: Int = 0,
    val skipped: Int = 0,
    val error: Boolean = false,
    val updating: Boolean = false,
)

@HiltViewModel
class SelectionViewModel
@Inject
constructor(private val repository: SelectionRepository, private val playback: PlaybackController) :
    ViewModel() {
    private val state = MutableStateFlow(SelectionUiState())
    val uiState = state.asStateFlow()
    private var source: SelectionSource? = null
    private var job: Job? = null
    private var watch: Job? = null
    @Volatile private var generation = 0
    private val edits = Mutex()

    fun source(value: SelectionSource) {
        if (source == value) return
        val previous = source
        source = value
        watch?.cancel()
        watch =
            viewModelScope.launch {
                repository.changes(value).drop(1).collect {
                    if (
                        state.value.active &&
                            state.value.selected.isNotEmpty() &&
                            !state.value.running &&
                            !state.value.loading &&
                            !state.value.updating
                    )
                        load(all = false)
                }
            }
        if (previous?.context != value.context) {
            clear()
            return
        }
        if (state.value.active && !state.value.running) load(all = false)
    }

    fun begin() {
        if (!state.value.running) state.value = state.value.copy(active = true)
    }

    fun toggle(item: SelectedTrack) {
        if (state.value.loading || state.value.running || state.value.updating) return
        val request = generation
        state.value = state.value.copy(active = true, updating = true)
        viewModelScope.launch(Dispatchers.Default) {
            edits.withLock {
                val selected = state.value.selected.toMutableMap()
                if (selected.remove(item.key) == null) selected[item.key] = item
                val result = selected.toMap()
                withContext(Dispatchers.Main.immediate) {
                    if (generation == request)
                        state.value =
                            state.value.copy(selected = result, error = false, updating = false)
                }
            }
        }
    }

    fun clear() {
        generation++
        job?.cancel()
        state.value = SelectionUiState()
    }

    fun cancel() {
        generation++
        job?.cancel()
        state.value =
            state.value.copy(
                active = false,
                selected = emptyMap(),
                loading = false,
                running = false,
                updating = false,
            )
    }

    /** A departing destination must not clear selection already owned by the next screen. */
    fun leave(context: String) {
        if (source?.context == context) {
            cancel()
            watch?.cancel()
            source = null
        }
    }

    fun dismissResult() {
        state.value = state.value.copy(processed = 0, skipped = 0, error = false)
    }

    fun playlistAddItems(context: String): List<PlaylistAddItem> {
        val current = state.value
        if (source?.context != context || current.loading || current.updating || current.running)
            return emptyList()
        return current.selected.values.map { PlaylistAddItem(it.key, it.trackId) }
    }

    fun finishPlaylistAdd(context: String) {
        if (source?.context == context) clear()
    }

    fun selectAll() = load(all = true)

    private fun load(all: Boolean) {
        val current = source ?: return
        val selected = state.value.selected
        generation++
        val request = generation
        job?.cancel()
        state.value =
            state.value.copy(active = true, loading = true, error = false, updating = false)
        job =
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val result = linkedMapOf<String, SelectedTrack>()
                    var offset = 0
                    while (true) {
                        ensureActive()
                        val rows = repository.page(current, offset)
                        for (row in rows) if (all || row.key in selected) result[row.key] = row
                        offset += rows.size
                        if (rows.size < 100) break
                        yield()
                    }
                    if (generation == request)
                        state.value = state.value.copy(selected = result.toMap(), loading = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == request)
                        state.value = state.value.copy(loading = false, error = true)
                }
            }
    }

    fun run(action: BulkAction, playlistId: String? = null, onComplete: (() -> Unit)? = null) {
        if (
            state.value.running ||
                state.value.loading ||
                state.value.updating ||
                state.value.selected.isEmpty()
        )
            return
        val selected = state.value.selected.values.toList()
        generation++
        val request = generation
        state.value = state.value.copy(running = true, processed = 0, skipped = 0, error = false)
        job =
            viewModelScope.launch(Dispatchers.Default) {
                val seen = mutableSetOf<String>()
                try {
                    for (batch in selected.chunked(100)) {
                        ensureActive()
                        val input =
                            if (
                                action == BulkAction.ADD_FAVOURITES ||
                                    action == BulkAction.REMOVE_FAVOURITES
                            )
                                batch.filter { seen.add(it.trackId) }
                            else batch
                        val result =
                            if (action == BulkAction.ADD_QUEUE) {
                                val available = repository.refresh(input).filter { it.available }
                                val added =
                                    withContext(Dispatchers.Main.immediate) {
                                        playback.enqueue(available.map { it.trackId })
                                    }
                                BulkResult(added, input.size - added)
                            } else repository.mutate(action, input, playlistId)
                        if (generation != request) return@launch
                        state.value =
                            state.value.copy(
                                processed = state.value.processed + result.updated,
                                skipped = state.value.skipped + result.skipped,
                            )
                        yield()
                    }
                    if (generation == request) {
                        state.value =
                            state.value.copy(running = false, active = false, selected = emptyMap())
                        withContext(Dispatchers.Main.immediate) {
                            if (generation == request) onComplete?.invoke()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == request)
                        state.value = state.value.copy(running = false, error = true)
                }
            }
    }
}
