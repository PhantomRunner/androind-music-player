package dev.violet.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.playback.RepeatMode
import dev.violet.music.core.ui.PlaybackIcons

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TransportControls(
    state: PlaybackUiState,
    controller: PlaybackController,
    compact: Boolean = false,
) {
    val enabled = state.connected && state.current != null
    val repeatLabel = stringResource(
        when (state.repeat) {
            RepeatMode.OFF -> R.string.repeat_off
            RepeatMode.ALL -> R.string.repeat_all
            RepeatMode.ONE -> R.string.repeat_one
        }
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { controller.toggleShuffle() },
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp),
        ) {
            Icon(PlaybackIcons.Shuffle, stringResource(R.string.shuffle))
        }
        IconButton(
            onClick = controller::previous,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp),
        ) {
            Icon(PlaybackIcons.Previous, stringResource(R.string.previous))
        }
        FilledIconButton(
            onClick = controller::togglePlay,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically).size(if (compact) 52.dp else 68.dp),
        ) {
            Icon(
                if (state.playWhenReady) PlaybackIcons.Pause else PlaybackIcons.Play,
                stringResource(if (state.playWhenReady) R.string.pause else R.string.play),
                modifier = Modifier.size(if (compact) 24.dp else 30.dp),
            )
        }
        IconButton(
            onClick = controller::next,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp),
        ) {
            Icon(PlaybackIcons.Next, stringResource(R.string.next))
        }
        IconButton(
            onClick = controller::cycleRepeat,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp),
        ) {
            BadgedBox(badge = {
                if (state.repeat == RepeatMode.ONE) Badge { Text("1") }
            }) {
                Icon(
                    PlaybackIcons.Repeat,
                    repeatLabel,
                    tint = if (state.repeat == RepeatMode.OFF || !enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}
