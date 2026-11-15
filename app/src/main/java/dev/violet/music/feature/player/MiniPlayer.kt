package dev.violet.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.ui.Artwork
import dev.violet.music.core.ui.PlaybackIcons

@Composable
fun MiniPlayer(state: PlaybackUiState, controller: PlaybackController, onOpen: () -> Unit) {
    val current = state.current ?: return
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Artwork(current.artworkUri, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(current.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = controller::togglePlay, enabled = state.connected) {
                    Icon(if (state.playWhenReady) PlaybackIcons.Pause else PlaybackIcons.Play, stringResource(if (state.playWhenReady) R.string.pause else R.string.play))
                }
                IconButton(onClick = controller::next, enabled = state.connected) { Icon(PlaybackIcons.Next, stringResource(R.string.next)) }
            }
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
