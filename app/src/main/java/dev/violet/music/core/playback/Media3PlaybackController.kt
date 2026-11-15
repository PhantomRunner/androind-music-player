package dev.violet.music.core.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.violet.music.core.model.TrackFilter
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3PlaybackController
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val resolver: LocalPlayableSourceResolver,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackUiState())
    override val state = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var connectionJob: Job? = null
    private var positionJob: Job? = null
    private var clients = 0

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish(
                    events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                        events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                        events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                mutableState.value = mutableState.value.copy(error = PlaybackError.PLAYBACK)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePositionSampling()
            }
        }

    init {
        scope.launch { mutableState.subscriptionCount.collect { updatePositionSampling() } }
    }

    override fun acquire() {
        clients++
        if (clients == 1) reconnect()
    }

    override fun release() {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients == 0) {
            connectionJob?.cancel()
            positionJob?.cancel()
            val old = controller
            controller = null
            val oldFuture = future
            future = null
            if (old != null) {
                old.removeListener(listener)
                old.release()
            } else oldFuture?.let(MediaController::releaseFuture)
            mutableState.value = mutableState.value.copy(connected = false, isPlaying = false)
        }
    }

    override fun reconnect() {
        if (clients == 0 || connectionJob?.isActive == true) return
        connectionJob =
            scope.launch {
                for (attempt in 0..2) {
                    try {
                        connectedController()
                        return@launch
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        future?.let(MediaController::releaseFuture)
                        future = null
                        if (attempt < 2) delay(500L * (attempt + 1))
                    }
                }
                mutableState.value =
                    mutableState.value.copy(
                        connected = false,
                        loading = false,
                        error = PlaybackError.CONNECTION,
                    )
            }
    }

    private suspend fun connectedController(): MediaController {
        controller
            ?.takeIf { it.isConnected }
            ?.let {
                return it
            }
        val pending =
            future
                ?: MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .setListener(
                        object : MediaController.Listener {
                            override fun onDisconnected(disconnected: MediaController) {
                                if (controller !== disconnected) return
                                controller = null
                                future = null
                                disconnected.removeListener(listener)
                                disconnected.release()
                                positionJob?.cancel()
                                mutableState.value =
                                    mutableState.value.copy(
                                        connected = false,
                                        isPlaying = false,
                                        error = PlaybackError.CONNECTION,
                                    )
                                reconnect()
                            }
                        }
                    )
                    .buildAsync()
                    .also { future = it }
        val result = pending.await()
        currentCoroutineContext().ensureActive()
        if (controller !== result) {
            controller = result
            result.addListener(listener)
            publish(true)
        }
        return result
    }

    override suspend fun play(filter: TrackFilter, trackId: String?, shuffle: Boolean) {
        try {
            val ids = resolver.queueIds(filter).toMutableList()
            // A tap can race a refreshed/debounced list: preserve the explicitly selected item.
            if (trackId != null && trackId !in ids) ids.add(0, trackId)
            playIds(ids, if (trackId == null) 0 else ids.indexOf(trackId), shuffle)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reportError(PlaybackError.PLAYBACK)
        }
    }

    override suspend fun playIds(ids: List<String>, startIndex: Int, shuffle: Boolean) {
        if (ids.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = PlaybackError.EMPTY_QUEUE)
            return
        }
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        try {
            val items = resolver.resolve(ids)
            val index = if (shuffle) items.indices.random() else startIndex.coerceIn(items.indices)
            resolver.verifyReadable(items[index])
            val player = connectedController()
            currentCoroutineContext().ensureActive()
            player.shuffleModeEnabled = shuffle
            player.setMediaItems(items, index, 0)
            player.prepare()
            player.play()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            mutableState.value = mutableState.value.copy(error = PlaybackError.PERMISSION)
        } catch (_: FileNotFoundException) {
            mutableState.value = mutableState.value.copy(error = PlaybackError.UNAVAILABLE)
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(error = PlaybackError.PLAYBACK)
        } finally {
            mutableState.value = mutableState.value.copy(loading = false)
        }
    }

    private fun command(action: MediaController.() -> Unit) {
        val player = controller
        if (player == null) {
            mutableState.value = mutableState.value.copy(error = PlaybackError.CONNECTION)
            reconnect()
        } else
            try {
                player.action()
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(error = PlaybackError.PLAYBACK)
            }
    }

    override fun togglePlay() = command {
        if (playWhenReady && playbackState != Player.STATE_ENDED) pause()
        else {
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
            if (playerError != null || playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    override fun previous() = queueCommand("previous", "")

    override fun next() = queueCommand("next", "")

    override fun seekTo(positionMs: Long) = command {
        seekTo(positionMs.coerceIn(0, duration.coerceAtLeast(0)))
    }

    override fun playQueueItem(index: Int) {
        state.value.queue.getOrNull(index)?.let { playOccurrence(it.occurrenceId) }
    }

    override fun removeQueueItem(index: Int) {
        state.value.queue.getOrNull(index)?.let { removeOccurrence(it.occurrenceId) }
    }

    private fun queueCommand(operation: String, id: String, target: String? = null) {
        scope.launch {
            try {
                val result =
                    connectedController()
                        .sendCustomCommand(
                            QueueCommand,
                            Bundle().apply {
                                putString("operation", operation)
                                putString("id", id)
                                putString("target", target)
                            },
                        )
                        .await()
                if (result.resultCode < 0) reportError(PlaybackError.UNAVAILABLE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reportError(PlaybackError.CONNECTION)
            }
        }
    }

    override fun playOccurrence(id: String) = queueCommand("play", id)

    override fun removeOccurrence(id: String) = queueCommand("remove", id)

    override fun moveOccurrence(id: String, target: String) = queueCommand("move", id, target)

    override suspend fun enqueue(ids: List<String>, next: Boolean): Int {
        var anchor: String? = null
        var skipped = 0
        var added = 0
        for (batch in ids.chunked(100)) {
            currentCoroutineContext().ensureActive()
            val result =
                connectedController()
                    .sendCustomCommand(
                        QueueCommand,
                        Bundle().apply {
                            putString("operation", "enqueue")
                            putStringArrayList("tracks", ArrayList(batch))
                            putBoolean("next", next)
                            putString("after", anchor)
                        },
                    )
                    .await()
            if (result.resultCode < 0) {
                reportError(
                    if (result.resultCode == SessionError.ERROR_PERMISSION_DENIED)
                        PlaybackError.PERMISSION
                    else PlaybackError.PLAYBACK
                )
                return added
            }
            anchor = result.extras.getString("last") ?: anchor
            skipped += result.extras.getInt("skipped")
            added += batch.size - result.extras.getInt("skipped")
        }
        if (skipped > 0) reportError(PlaybackError.UNAVAILABLE)
        return added
    }

    override fun toggleShuffle() = command { shuffleModeEnabled = !shuffleModeEnabled }

    override fun cycleRepeat() = command {
        repeatMode =
            when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    override fun stop() = command {
        pause()
        stop()
        clearMediaItems()
    }

    override fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    override fun reportError(error: PlaybackError) {
        mutableState.value = mutableState.value.copy(error = error)
    }

    private fun publish(rebuildQueue: Boolean = false) {
        val player = controller ?: return
        val queue =
            if (rebuildQueue)
                playbackOrder(player).map { index ->
                    val item = player.getMediaItemAt(index)
                    QueueItem(
                        item.mediaId,
                        item.mediaMetadata.title?.toString().orEmpty(),
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        item.mediaMetadata.artworkUri?.toString(),
                        item.occurrenceId().ifEmpty { "pending:$index" },
                        index,
                        item.mediaMetadata.extras?.getLong("artwork.version") ?: 0,
                    )
                }
            else mutableState.value.queue
        mutableState.value =
            mutableState.value.copy(
                connected = true,
                queue = queue,
                currentIndex =
                    queue.indexOfFirst { it.sourceIndex == player.currentMediaItemIndex },
                current = queue.firstOrNull { it.sourceIndex == player.currentMediaItemIndex },
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs =
                    player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                repeat =
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        else -> RepeatMode.OFF
                    },
                shuffle = player.shuffleModeEnabled,
            )
        updatePositionSampling()
    }

    private fun updatePositionSampling() {
        if (controller?.isPlaying != true || mutableState.subscriptionCount.value == 0) {
            positionJob?.cancel()
            positionJob = null
        } else if (positionJob?.isActive != true)
            positionJob =
                scope.launch {
                    while (controller?.isPlaying == true) {
                        controller?.let {
                            mutableState.value =
                                mutableState.value.copy(
                                    positionMs = it.currentPosition.coerceAtLeast(0)
                                )
                        }
                        delay(500)
                    }
                }
    }
}
