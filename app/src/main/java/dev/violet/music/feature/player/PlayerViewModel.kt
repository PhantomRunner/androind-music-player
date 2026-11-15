package dev.violet.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.violet.music.core.data.CollectionRepository
import dev.violet.music.core.data.LibraryRepository
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel
@Inject
constructor(
    val controller: PlaybackController,
    private val collections: CollectionRepository,
    private val library: LibraryRepository,
    private val artists: dev.violet.music.core.data.ArtistRepository,
    colors: dev.violet.music.core.data.ArtworkColorRepository,
) : ViewModel() {
    val uiState = controller.state
    val currentTrack =
        uiState
            .map { it.current?.id }
            .distinctUntilChanged()
            .flatMapLatest { if (it == null) flowOf(null) else library.observeTrack(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var request: Job? = null
    val credits =
        uiState
            .map { it.current?.id }
            .distinctUntilChanged()
            .flatMapLatest { if (it == null) flowOf(emptyList()) else artists.credits(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artworkTint =
        currentTrack
            .flatMapLatest { track ->
                kotlinx.coroutines.flow.flow {
                    emit(colors.color(track?.artworkUri, track?.artworkVersion ?: 0))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0xFF101116.toInt())

    fun trackMenu(id: String, onTrack: (dev.violet.music.core.model.Track) -> Unit) {
        viewModelScope.launch { library.observeTrack(id).first()?.let(onTrack) }
    }

    fun enqueue(ids: List<String>, next: Boolean = false) {
        viewModelScope.launch {
            try {
                controller.enqueue(ids, next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                controller.reportError(PlaybackError.PLAYBACK)
            }
        }
    }

    init {
        controller.acquire()
    }

    fun play(trackId: String?, filter: TrackFilter = TrackFilter(), shuffle: Boolean = false) {
        request?.cancel()
        request = viewModelScope.launch { controller.play(filter, trackId, shuffle) }
    }

    fun playIds(ids: List<String>, startIndex: Int = 0) {
        request?.cancel()
        request = viewModelScope.launch { controller.playIds(ids, startIndex) }
    }

    fun playPlaylist(
        id: String,
        entryId: String? = null,
        shuffle: Boolean = false,
        filter: PlaylistFilter = PlaylistFilter(),
    ) {
        request?.cancel()
        request =
            viewModelScope.launch {
                try {
                    val queue = collections.queue(id, filter)
                    val index =
                        if (entryId == null) 0 else queue.indexOfFirst { it.entryId == entryId }
                    if (index < 0) {
                        controller.reportError(PlaybackError.UNAVAILABLE)
                        return@launch
                    }
                    controller.playIds(queue.map { it.trackId }, index, shuffle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    controller.reportError(PlaybackError.PLAYBACK)
                }
            }
    }

    override fun onCleared() {
        controller.release()
    }
}
