package dev.violet.music.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.PlaylistSummary
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.Artwork
import dev.violet.music.core.ui.LoadingState
import dev.violet.music.core.ui.trackCountLabel

@Composable
fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(playlist.artworkUri)
            Column {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                Text(trackCountLabel(playlist.trackCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlaylistNameDialog(initialName: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName.isEmpty()) R.string.new_playlist else R.string.rename_playlist)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
                supportingText = { Text(stringResource(R.string.playlist_name_limit)) }, isError = name.trim().length > 120,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.trim().length in 1..120) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(track: Track, onDismiss: () -> Unit, onFavourite: () -> Unit, onAdd: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge)
            Text(track.artistName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onFavourite, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (track.isFavourite) R.string.remove_favourite else R.string.add_favourite))
            }
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.add_to_playlist)) }
        }
    }
}

@Composable
fun PlaylistPicker(
    playlists: LazyPagingItems<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick = onCreate) { Text(stringResource(R.string.new_playlist)) } }
                if (playlists.loadState.refresh is LoadState.Loading) item { LoadingState() }
                if (playlists.loadState.hasError) item {
                    TextButton(onClick = playlists::retry) { Text(stringResource(R.string.retry)) }
                }
                items(playlists.itemCount, key = playlists.itemKey { it.id }) { index ->
                    playlists[index]?.let { PlaylistRow(it) { onSelect(it.id) } }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
