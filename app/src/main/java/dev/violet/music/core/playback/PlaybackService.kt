package dev.violet.music.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.violet.music.MainActivity
import dev.violet.music.core.data.HistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject lateinit var resolver: LocalPlayableSourceResolver
    @Inject lateinit var history: HistoryRepository
    private var recorder: PlaybackHistoryRecorder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private val failedIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer.Builder(this).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            currentMediaItem?.mediaId?.let(failedIds::add)
                            if (currentTimeline.isEmpty || currentMediaItemIndex == C.INDEX_UNSET) {
                                pause()
                                return
                            }
                            // Never loop forever over an unreadable queue, including repeat-all.
                            val recoveryRepeat =
                                if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF
                                else repeatMode
                            var candidate =
                                currentTimeline.getNextWindowIndex(
                                    currentMediaItemIndex,
                                    recoveryRepeat,
                                    shuffleModeEnabled,
                                )
                            val checked = mutableSetOf<Int>()
                            while (candidate != C.INDEX_UNSET && checked.add(candidate)) {
                                if (getMediaItemAt(candidate).mediaId !in failedIds) {
                                    seekToDefaultPosition(candidate)
                                    prepare()
                                    play()
                                    return
                                }
                                candidate =
                                    currentTimeline.getNextWindowIndex(
                                        candidate,
                                        recoveryRepeat,
                                        shuffleModeEnabled,
                                    )
                            }
                            pause()
                        }
                    }
                )
            }
        val historyRecorder =
            PlaybackHistoryRecorder(player, this, history, scope).also { recorder = it }
        val queue = ServiceQueue(player, resolver, historyRecorder)
        val activity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        session =
            MediaSession.Builder(this, player)
                .setSessionActivity(activity)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult =
                            MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                                .setAvailableSessionCommands(
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                        .buildUpon()
                                        .apply {
                                            if (controller.packageName == packageName)
                                                add(QueueCommand)
                                        }
                                        .build()
                                )
                                .build()

                        override fun onCustomCommand(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> =
                            scope.future {
                                if (
                                    controller.packageName != packageName ||
                                        customCommand.customAction != QUEUE_ACTION
                                )
                                    return@future SessionResult(
                                        SessionError.ERROR_PERMISSION_DENIED
                                    )
                                try {
                                    queue.execute(args)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: SecurityException) {
                                    SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                                } catch (_: Exception) {
                                    SessionResult(SessionError.ERROR_IO)
                                }
                            }

                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: List<MediaItem>,
                        ): ListenableFuture<List<MediaItem>> =
                            scope.future {
                                val resolved =
                                    resolver.resolve(mediaItems.map { it.mediaId }).map {
                                        it.withOccurrence()
                                    }
                                failedIds.clear()
                                resolved
                            }
                    }
                )
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session.takeIf { controllerInfo.packageName == packageName || controllerInfo.isTrusted }

    override fun onDestroy() {
        recorder?.close()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
