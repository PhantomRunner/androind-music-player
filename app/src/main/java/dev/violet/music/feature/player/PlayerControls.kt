package dev.violet.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.playback.RepeatMode
import dev.violet.music.core.ui.PlaybackIcons

@Composable
fun TransportControls(state: PlaybackUiState, controller: PlaybackController, onQueue: () -> Unit) {
    val repeatLabel = stringResource(when (state.repeat) { RepeatMode.OFF -> R.string.repeat_off; RepeatMode.ALL -> R.string.repeat_all; RepeatMode.ONE -> R.string.repeat_one })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = controller::previous, enabled = state.connected, modifier = Modifier.size(64.dp)) {
            Icon(PlaybackIcons.Previous, stringResource(R.string.previous))
        }
        FilledIconButton(onClick = controller::togglePlay, enabled = state.connected, modifier = Modifier.size(72.dp)) {
            Icon(if (state.playWhenReady) PlaybackIcons.Pause else PlaybackIcons.Play, stringResource(if (state.playWhenReady) R.string.pause else R.string.play))
        }
        IconButton(onClick = controller::next, enabled = state.connected, modifier = Modifier.size(64.dp)) {
            Icon(PlaybackIcons.Next, stringResource(R.string.next))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconToggleButton(checked = state.shuffle, onCheckedChange = { controller.toggleShuffle() }, enabled = state.connected) {
            Icon(PlaybackIcons.Shuffle, stringResource(R.string.shuffle))
        }
        IconButton(onClick = controller::cycleRepeat, enabled = state.connected, modifier = Modifier.semantics { contentDescription = repeatLabel }) {
            if (state.repeat == RepeatMode.ONE) Text("1", color = MaterialTheme.colorScheme.primary)
            else Icon(
                PlaybackIcons.Repeat, null,
                tint = if (state.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onQueue) { Icon(PlaybackIcons.Queue, stringResource(R.string.queue)) }
    }
}
