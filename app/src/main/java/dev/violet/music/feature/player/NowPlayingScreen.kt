package dev.violet.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.violet.music.R
import dev.violet.music.core.model.ArtistCredit
import dev.violet.music.core.model.Track
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.ui.Artwork
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.PlaybackIcons
import dev.violet.music.core.ui.VioletIcons
import dev.violet.music.core.ui.formatDuration
import kotlinx.coroutines.flow.StateFlow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NowPlayingScreen(
    state: PlaybackUiState,
    controller: PlaybackController,
    track: Track?,
    onMenu: (Track) -> Unit,
    onFavourite: (Track) -> Unit,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
    credits: StateFlow<List<ArtistCredit>>,
    tint: Int,
    onQueue: () -> Unit,
) {
    val current = state.current
    val details = track?.takeIf { it.id == current?.id }
    val creditsState by credits.collectAsStateWithLifecycle()
    val artists = creditsState.filter { it.role == "TRACK" }
    val color by animateColorAsState(Color(tint), label = "Artwork background")
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(color, colors.background))
        ),
    ) {
        Box(Modifier.padding(horizontal = 12.dp)) {
            PlayerHeader(
                stringResource(R.string.now_playing),
                onBack,
                onOptions = details?.let { item -> { onMenu(item) } },
                optionsLabel = details?.let { stringResource(R.string.track_options, it.title) },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.connected) {
                item("connection") {
                    MessageCard(
                        stringResource(R.string.player_unavailable),
                        stringResource(R.string.connection_error),
                        stringResource(R.string.retry),
                        controller::reconnect,
                    )
                }
            }
            if (current == null) {
                item("empty") {
                    MessageCard(stringResource(R.string.nothing_playing), stringResource(R.string.choose_track))
                }
            } else {
                item("artwork") {
                    Artwork(
                        current.artworkUri,
                        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f),
                        revision = current.artworkVersion,
                    )
                }
                item("track") {
                    Row(
                        Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(current.title, style = MaterialTheme.typography.headlineMedium)
                            if (artists.isEmpty() || details == null) {
                                Text(current.artist, color = colors.onSurfaceVariant)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (artist in artists) {
                                        TextButton(
                                            onClick = { onArtist(artist.id) },
                                            contentPadding = PaddingValues(end = 8.dp),
                                        ) {
                                            Text(artist.name, color = colors.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (details != null) {
                            IconToggleButton(
                                checked = details.isFavourite,
                                onCheckedChange = { onFavourite(details) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    PlaybackIcons.Heart,
                                    stringResource(
                                        if (details.isFavourite) R.string.remove_favourite else R.string.add_favourite
                                    ),
                                    tint = if (details.isFavourite) colors.primary else colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.buffering || state.loading) {
                    item("buffering") { LinearProgressIndicator(Modifier.widthIn(max = 440.dp).fillMaxWidth()) }
                }
                item("seek") { PlayerSeekBar(state, current.occurrenceId, controller::seekTo) }
                item("transport") {
                    Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) { TransportControls(state, controller) }
                }
                item("actions") {
                    FlowRow(
                        Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onQueue) {
                            Icon(PlaybackIcons.Queue, stringResource(R.string.queue))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.queue))
                        }
                        TextButton(onClick = controller::stop, enabled = state.connected) {
                            Text(stringResource(R.string.stop_playback))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerHeader(
    title: String,
    onBack: () -> Unit,
    close: Boolean = false,
    onOptions: (() -> Unit)? = null,
    optionsLabel: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                if (close) VioletIcons.Close else VioletIcons.Back,
                stringResource(R.string.back),
                modifier = if (close) Modifier else Modifier.rotate(-90f),
            )
        }
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (onOptions == null) {
            Spacer(Modifier.width(48.dp))
        } else {
            IconButton(onClick = onOptions, modifier = Modifier.size(48.dp)) {
                Icon(PlaybackIcons.More, optionsLabel)
            }
        }
    }
}

@Composable
private fun PlayerSeekBar(state: PlaybackUiState, occurrenceId: String, onSeek: (Long) -> Unit) {
    var drag by remember(occurrenceId) { mutableFloatStateOf(-1f) }
    val seekLabel = stringResource(R.string.seek)
    val position = if (drag >= 0f) (drag * state.durationMs).toLong() else state.positionMs
    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Slider(
            value = if (state.durationMs > 0) {
                (position.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                if (drag >= 0f) onSeek((drag * state.durationMs).toLong())
                drag = -1f
            },
            enabled = state.connected && state.durationMs > 0,
            modifier = Modifier.semantics { contentDescription = seekLabel },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}
