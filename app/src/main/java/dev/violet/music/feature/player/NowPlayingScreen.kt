package dev.violet.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.formatDuration
import kotlinx.coroutines.flow.StateFlow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NowPlayingScreen(
    state: PlaybackUiState,
    controller: PlaybackController,
    track: Track?,
    onMenu: (Track) -> Unit,
    onBack: () -> Unit = {},
    onArtist: (String) -> Unit = {},
    credits: StateFlow<List<ArtistCredit>>? = null,
    tint: Int = 0xFF101116.toInt(),
    onQueue: () -> Unit,
) {
    val current = state.current
    val artists =
        credits?.collectAsStateWithLifecycle()?.value.orEmpty().filter { it.role == "TRACK" }
    val color by animateColorAsState(Color(tint), label = "Artwork background")
    var drag by remember(current?.id, state.currentIndex) { mutableFloatStateOf(-1f) }
    val seekLabel = stringResource(R.string.seek)
    LazyColumn(
        modifier =
            Modifier.background(
                Brush.verticalGradient(listOf(color, MaterialTheme.colorScheme.background))
            ),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(dev.violet.music.core.ui.VioletIcons.Back, stringResource(R.string.back))
                }
                SectionTitle(stringResource(R.string.now_playing))
            }
        }
        if (!state.connected)
            item {
                MessageCard(
                    stringResource(R.string.player_unavailable),
                    stringResource(R.string.connection_error),
                    stringResource(R.string.retry),
                    controller::reconnect,
                )
            }
        if (current == null)
            item {
                MessageCard(
                    stringResource(R.string.nothing_playing),
                    stringResource(R.string.choose_track),
                )
            }
        else {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Artwork(current.artworkUri, 240.dp, revision = current.artworkVersion)
                    Text(current.title, style = MaterialTheme.typography.headlineMedium)
                    Text(current.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        artists.forEach { artist ->
                            TextButton(onClick = { onArtist(artist.id) }) { Text(artist.name) }
                        }
                    }
                }
            }
            if (state.buffering || state.loading)
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Column {
                    Slider(
                        value =
                            if (drag >= 0) drag
                            else if (state.durationMs > 0)
                                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                            else 0f,
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            if (drag >= 0) controller.seekTo((drag * state.durationMs).toLong())
                            drag = -1f
                        },
                        enabled = state.connected && state.durationMs > 0,
                        modifier = Modifier.semantics { contentDescription = seekLabel },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatDuration(
                                if (drag >= 0) (drag * state.durationMs).toLong()
                                else state.positionMs
                            )
                        )
                        Text(formatDuration(state.durationMs))
                    }
                }
            }
            item { TransportControls(state, controller, onQueue) }
            if (track != null)
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { onMenu(track) }) {
                            Icon(
                                PlaybackIcons.More,
                                stringResource(R.string.track_options, track.title),
                            )
                        }
                    }
                }
            item {
                TextButton(onClick = controller::stop) {
                    Text(stringResource(R.string.stop_playback))
                }
            }
        }
    }
}
