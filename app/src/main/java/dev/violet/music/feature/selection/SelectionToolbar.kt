package dev.violet.music.feature.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.feature.collection.*

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SelectionToolbar(
    viewModel: SelectionViewModel,
    source: SelectionSource,
    collections: CollectionViewModel,
    clearOnDispose: Boolean = true,
    @androidx.annotation.StringRes startLabel: Int = R.string.select_tracks,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val addition by collections.addition.collectAsStateWithLifecycle()
    LaunchedEffect(source) { viewModel.source(source) }
    DisposableEffect(source.context) {
        onDispose { if (clearOnDispose) viewModel.leave(source.context) }
    }
    BackHandler(state.active) { if (!addition.pending) viewModel.cancel() }
    val busy = state.running || addition.pending
    var choosing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    if (!state.active) {
        TextButton(onClick = viewModel::begin) { Text(stringResource(startLabel)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.selected_count, state.selected.size),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.loading || addition.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.running) Text(stringResource(R.string.bulk_running, state.processed))
        if (state.error)
            Text(stringResource(R.string.collection_error), color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::selectAll, enabled = !busy && !state.loading) {
                Text(stringResource(R.string.select_all))
            }
            TextButton(onClick = viewModel::clear, enabled = !busy) {
                Text(stringResource(R.string.deselect_all))
            }
            TextButton(onClick = viewModel::cancel, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
        if (state.selected.isNotEmpty() && !busy && !state.loading && !state.updating)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { choosing = true }) {
                    Text(stringResource(R.string.add_to_playlist))
                }
                TextButton(onClick = { viewModel.run(BulkAction.ADD_QUEUE) }) {
                    Text(stringResource(R.string.add_to_queue))
                }
                TextButton(onClick = { viewModel.run(BulkAction.ADD_FAVOURITES) }) {
                    Text(stringResource(R.string.add_favourite))
                }
                TextButton(onClick = { viewModel.run(BulkAction.REMOVE_FAVOURITES) }) {
                    Text(stringResource(R.string.remove_favourite))
                }
                if (source.playlistId != null)
                    TextButton(onClick = { removing = true }) {
                        Text(stringResource(R.string.remove_selected))
                    }
            }
    }
    if (choosing && !creating)
        PlaylistPicker(
            collections.playlists.collectAsLazyPagingItems(),
            { choosing = false },
            { id ->
                choosing = false
                collections.add(id, viewModel.playlistAddItems(source.context), PlaylistAddOrigin(source.context))
            },
            { creating = true },
        )
    if (creating)
        PlaylistNameDialog(
            onDismiss = {
                creating = false
                choosing = false
            },
            onSave = { name ->
                creating = false
                choosing = false
                val items = viewModel.playlistAddItems(source.context)
                if (items.isNotEmpty())
                    collections.create(name) { collections.add(it, items, PlaylistAddOrigin(source.context)) }
            },
        )
    if (removing)
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.remove_selected)) },
            text = { Text(stringResource(R.string.remove_selected_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        viewModel.run(BulkAction.REMOVE_PLAYLIST, source.playlistId)
                    }
                ) {
                    Text(stringResource(R.string.remove_from_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
}
