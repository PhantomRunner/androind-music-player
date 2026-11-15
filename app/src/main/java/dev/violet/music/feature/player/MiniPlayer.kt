package dev.violet.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.ui.Artwork
import dev.violet.music.core.ui.PlaybackIcons

@Composable
fun MiniPlayer(state: PlaybackUiState, controller: PlaybackController, tint: Int, onOpen: () -> Unit) {
    val current = state.current ?: return
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        color = lerp(colors.surfaceVariant, Color(tint), 0.52f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(current.artworkUri, 42.dp, revision = current.artworkVersion)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        current.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        current.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = controller::togglePlay, enabled = state.connected) {
                    Icon(
                        if (state.playWhenReady) PlaybackIcons.Pause else PlaybackIcons.Play,
                        stringResource(if (state.playWhenReady) R.string.pause else R.string.play),
                    )
                }
                IconButton(onClick = controller::next, enabled = state.connected) {
                    Icon(PlaybackIcons.Next, stringResource(R.string.next))
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = colors.outline.copy(alpha = 0.45f),
                )
            }
        }
    }
}
