package dev.violet.music.feature.collection

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.PlaylistEntry
import dev.violet.music.core.model.PlaylistFilter
import dev.violet.music.core.model.PlaylistSort
import dev.violet.music.core.model.PlaylistSummary
import dev.violet.music.core.model.SelectedTrack
import dev.violet.music.core.model.SelectionSource
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import dev.violet.music.feature.selection.SelectionToolbar
import dev.violet.music.feature.selection.SelectionViewModel
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PlaylistScreen(
    id: String,
    header: PlaylistUiState,
    entries: Flow<PagingData<PlaylistEntry>>,
    query: String,
    filter: PlaylistFilter,
    canPlay: Boolean,
    onQuery: (String) -> Unit,
    onSort: (PlaylistSort) -> Unit,
    onPlay: (String) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onEntryMenu: (PlaylistEntry) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddTracks: () -> Unit,
    onApplyCover: (android.net.Uri) -> Unit,
    onRemoveCover: () -> Unit,
    coverBusy: Boolean,
    selection: SelectionViewModel,
    collections: CollectionViewModel,
) {
    val items = entries.collectAsLazyPagingItems()
    val selected by selection.uiState.collectAsStateWithLifecycle()
    val source = SelectionSource("playlist:$id", playlistId = id, playlistFilter = filter)
    val activity = LocalActivity.current
    LaunchedEffect(source) { selection.source(source) }
    DisposableEffect(id) {
        onDispose {
            if (activity?.isChangingConfigurations != true) selection.leave(source.context)
        }
    }

    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val playlist = header.playlist
    if (playlist != null) {
        if (rename) {
            PlaylistNameDialog(playlist.name, { rename = false }) {
                onRename(it)
                rename = false
            }
        }
        if (delete) {
            AlertDialog(
                onDismissRequest = { delete = false },
                title = { Text(stringResource(R.string.delete_playlist)) },
                text = { Text(stringResource(R.string.delete_playlist_description, playlist.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        delete = false
                        onDelete()
                    }) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { delete = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    val filterReady = query.trim() == filter.query
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(VioletLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            header.loading -> item("loading") { LoadingState() }
            playlist == null -> item("unavailable") {
                MessageCard(stringResource(R.string.playlists), stringResource(R.string.playlist_unavailable))
            }
            else -> {
                item("hero") { PlaylistHero(playlist) }
                item("actions") {
                    PlaylistActions(
                        empty = playlist.trackCount == 0,
                        canPlay = canPlay && filterReady,
                        onPlayAll = onPlayAll,
                        onAddTracks = onAddTracks,
                        onRename = { rename = true },
                        onDelete = { delete = true },
                    )
                }
                item("cover-editor") { PlaylistCoverEditor(playlist, onApplyCover, onRemoveCover, coverBusy) }
                if (playlist.trackCount > 0) {
                    item("filter") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CatalogueSearchField(query, onQuery, stringResource(R.string.playlist_search))
                            PlaylistSortMenu(filter.sort, onSort)
                        }
                    }
                }
                when {
                    !filterReady || items.loadState.refresh is LoadState.Loading -> {
                        item("tracks-loading") { LoadingState() }
                    }
                    items.loadState.refresh is LoadState.Error -> item("error") {
                        PlaylistLoadError(items::retry)
                    }
                    items.itemCount == 0 -> item("empty") {
                        if (playlist.trackCount == 0) {
                            MessageCard(stringResource(R.string.empty_playlist), stringResource(R.string.add_tracks_description))
                        } else {
                            MessageCard(stringResource(R.string.search_empty), stringResource(R.string.search_empty_description))
                        }
                    }
                    else -> {
                        item("selection") {
                            SelectionToolbar(
                                selection,
                                source,
                                collections,
                                clearOnDispose = false,
                                startLabel = R.string.select_playlist_tracks,
                            )
                        }
                        items(items.itemCount, key = items.itemKey { it.id }) { index ->
                            items[index]?.let { entry ->
                                val toggle = {
                                    selection.toggle(
                                        SelectedTrack(entry.id, entry.track.id, entry.track.availability == "LOCAL")
                                    )
                                }
                                TrackRow(
                                    entry.track,
                                    onMenu = { onEntryMenu(entry) },
                                    onLongClick = toggle,
                                    selected = entry.id in selected.selected,
                                    selecting = selected.active,
                                    onPlay = { if (selected.active) toggle() else onPlay(entry.id) },
                                )
                            }
                        }
                        when (items.loadState.append) {
                            is LoadState.Loading -> item("append-loading") { LoadingState() }
                            is LoadState.Error -> item("append-error") { PlaylistLoadError(items::retry) }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHero(playlist: PlaylistSummary) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.38f), colors.background)),
            MaterialTheme.shapes.large,
        ).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(playlist.coverUri(), modifier = Modifier.widthIn(max = 250.dp).fillMaxWidth().aspectRatio(1f))
        Text(
            playlist.name,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            trackCountLabel(playlist.trackCount),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistActions(
    empty: Boolean,
    canPlay: Boolean,
    onPlayAll: (Boolean) -> Unit,
    onAddTracks: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (empty) {
            Button(onClick = onAddTracks, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.select_tracks))
            }
        } else {
            Button(onClick = { onPlayAll(false) }, enabled = canPlay, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(PlaybackIcons.Play, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play_all))
            }
            FilledTonalButton(
                onClick = { onPlayAll(true) },
                enabled = canPlay,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(PlaybackIcons.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shuffle))
            }
            TextButton(onClick = onAddTracks, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_tracks))
            }
        }
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(PlaybackIcons.More, stringResource(R.string.playlist_options))
            }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistSortMenu(sort: PlaylistSort, onSort: (PlaylistSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sort_by_value, stringResource(sort.label)))
        }
        DropdownMenu(expanded, { expanded = false }) {
            for (option in PlaylistSort.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        onSort(option)
                    },
                    leadingIcon = { if (option == sort) Icon(PlaybackIcons.Play, null) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistLoadError(onRetry: () -> Unit) {
    MessageCard(
        stringResource(R.string.playlists),
        stringResource(R.string.load_error),
        stringResource(R.string.retry),
        onRetry,
    )
}

private val PlaylistSort.label: Int
    get() = when (this) {
        PlaylistSort.POSITION -> R.string.playlist_order
        PlaylistSort.TITLE -> R.string.title_asc
        PlaylistSort.ARTIST -> R.string.playlist_sort_artist
        PlaylistSort.ADDED -> R.string.recently_added
    }
