package dev.violet.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(val controller: PlaybackController) : ViewModel() {
    val uiState = controller.state
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
    override fun onCleared() { controller.release() }
}
