package dev.violet.music.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.LoadingState
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.TrackRow
import dev.violet.music.core.ui.VioletIcons
import dev.violet.music.core.ui.coverUri
import dev.violet.music.core.ui.trackCountLabel
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PlaylistScreen(
    header: PlaylistUiState,
    entries: Flow<PagingData<PlaylistEntry>>,
    onPlay: (String) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onMenu: (Track) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAddTracks: () -> Unit,
    onApplyCover: (android.net.Uri) -> Unit = {},
    onRemoveCover: () -> Unit = {},
    coverBusy: Boolean = false,
    selection: dev.violet.music.feature.selection.SelectionViewModel? = null,
    collections: CollectionViewModel? = null,
    playlistId: String? = null,
    onEntryMenu: ((dev.violet.music.core.model.PlaylistEntry) -> Unit)? = null,
) {
    val items = entries.collectAsLazyPagingItems()
    val selectionState =
        selection?.uiState?.collectAsStateWithLifecycle()?.value
            ?: dev.violet.music.feature.selection.SelectionUiState()
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val playlist = header.playlist
    if (rename && playlist != null)
        PlaylistNameDialog(playlist.name, { rename = false }) {
            onRename(it)
            rename = false
        }
    if (delete && playlist != null)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_description, playlist.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header.loading) item { LoadingState() }
        else if (playlist == null)
            item {
                MessageCard(
                    stringResource(R.string.playlists),
                    stringResource(R.string.playlist_unavailable),
                )
            }
        else {
            item { SectionTitle(playlist.name) }
            item("add-tracks") {
                Button(
                    onClick = onAddTracks,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (playlist.trackCount == 0) R.string.select_tracks
                            else R.string.add_tracks
                        )
                    )
                }
            }
            item { dev.violet.music.core.ui.Artwork(playlist.coverUri(), 160.dp) }
            item { PlaylistCoverEditor(playlist, onApplyCover, onRemoveCover, coverBusy) }
            if (
                playlist.trackCount > 0 &&
                    selection != null &&
                    collections != null &&
                    playlistId != null
            )
                item("selection") {
                    dev.violet.music.feature.selection.SelectionToolbar(
                        selection,
                        dev.violet.music.core.model.SelectionSource(
                            "playlist:$playlistId",
                            playlistId = playlistId,
                        ),
                        collections,
                        startLabel = R.string.select_playlist_tracks,
                    )
                }
            item { Text(trackCountLabel(playlist.trackCount)) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { rename = true }) {
                        Text(stringResource(R.string.rename))
                    }
                    TextButton(onClick = { delete = true }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onPlayAll(false) }, enabled = playlist.trackCount > 0) {
                        Text(stringResource(R.string.play_all))
                    }
                    OutlinedButton(
                        onClick = { onPlayAll(true) },
                        enabled = playlist.trackCount > 0,
                    ) {
                        Text(stringResource(R.string.shuffle))
                    }
                }
            }
            if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading)
                item {
                    MessageCard(
                        stringResource(R.string.empty_playlist),
                        stringResource(R.string.add_tracks_description),
                    )
                }
            if (items.loadState.refresh is LoadState.Loading) item { LoadingState() }
            if (items.loadState.hasError)
                item {
                    MessageCard(
                        stringResource(R.string.playlists),
                        stringResource(R.string.load_error),
                        stringResource(R.string.retry),
                        items::retry,
                    )
                }
            items(items.itemCount, key = items.itemKey { it.id }) { index ->
                items[index]?.let { entry ->
                    Column {
                        fun toggle() {
                            selection?.toggle(
                                dev.violet.music.core.model.SelectedTrack(
                                    entry.id,
                                    entry.track.id,
                                    entry.track.availability == "LOCAL",
                                )
                            )
                        }
                        TrackRow(
                            entry.track,
                            onMenu = {
                                if (onEntryMenu != null) onEntryMenu(entry) else onMenu(entry.track)
                            },
                            onLongClick = ::toggle,
                            selected = entry.id in selectionState.selected,
                            selecting = selectionState.active,
                            onPlay = { if (selectionState.active) toggle() else onPlay(entry.id) },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = { onMove(entry.id, entry.position - 1) },
                                enabled = entry.position > 0,
                            ) {
                                Icon(
                                    VioletIcons.Back,
                                    stringResource(R.string.move_up),
                                    Modifier.rotate(90f),
                                )
                            }
                            IconButton(
                                onClick = { onMove(entry.id, entry.position + 1) },
                                enabled = entry.position < playlist.trackCount - 1,
                            ) {
                                Icon(
                                    VioletIcons.Back,
                                    stringResource(R.string.move_down),
                                    Modifier.rotate(-90f),
                                )
                            }
                            IconButton(onClick = { onRemove(entry.id) }) {
                                Icon(
                                    VioletIcons.Close,
                                    stringResource(R.string.remove_from_playlist),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
