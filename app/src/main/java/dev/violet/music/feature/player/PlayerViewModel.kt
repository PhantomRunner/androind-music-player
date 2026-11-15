package dev.violet.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.data.LibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import dev.violet.music.core.playback.PlaybackError
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    val controller: PlaybackController,
    private val collections: CollectionRepository,
    library: LibraryRepository,
) : ViewModel() {
    val uiState = controller.state
    val currentTrack = uiState.map { it.current?.id }.distinctUntilChanged().flatMapLatest {
        if (it == null) flowOf(null) else library.observeTrack(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var request: Job? = null
    init { controller.acquire() }
    fun play(trackId: String?, filter: TrackFilter = TrackFilter(), shuffle: Boolean = false) {
        request?.cancel()
        request = viewModelScope.launch { controller.play(filter, trackId, shuffle) }
    }
    fun playIds(ids: List<String>, startIndex: Int = 0) {
        request?.cancel()
        request = viewModelScope.launch { controller.playIds(ids, startIndex) }
    }
    fun playPlaylist(id: String, entryId: String? = null, shuffle: Boolean = false) {
        request?.cancel()
        request = viewModelScope.launch {
            try {
                val queue = collections.queue(id)
                val index = if (entryId == null) 0 else queue.indexOfFirst { it.entryId == entryId }
                if (index < 0) {
                    controller.reportError(PlaybackError.UNAVAILABLE)
                    return@launch
                }
                controller.playIds(queue.map { it.trackId }, index, shuffle)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { controller.reportError(PlaybackError.PLAYBACK) }
        }
    }
    override fun onCleared() { controller.release() }
}
