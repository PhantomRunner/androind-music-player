package dev.violet.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.VioletIcons

@Composable
fun QueueScreen(state: PlaybackUiState, controller: PlaybackController) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle(stringResource(R.string.queue)) }
        if (state.queue.isEmpty()) item { MessageCard(stringResource(R.string.nothing_playing), stringResource(R.string.choose_track)) }
        itemsIndexed(state.queue, key = { index, _ -> index }) { index, item ->
            Surface(
                onClick = { controller.playQueueItem(index) },
                color = if (index == state.currentIndex) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text((index + 1).toString(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { controller.removeQueueItem(index) }, enabled = state.connected) {
                        Icon(VioletIcons.Close, stringResource(R.string.remove_from_queue))
                    }
                }
            }
        }
    }
}
