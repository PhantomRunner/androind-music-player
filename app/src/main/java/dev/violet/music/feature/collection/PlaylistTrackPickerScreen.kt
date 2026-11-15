package dev.violet.music.feature.collection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.R
import dev.violet.music.core.model.BulkAction
import dev.violet.music.core.model.SelectedTrack
import dev.violet.music.core.model.SelectionSource
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.ui.*
import dev.violet.music.feature.library.LibraryUiState
import dev.violet.music.feature.selection.SelectionViewModel
import kotlinx.coroutines.flow.Flow

/** Adds directly to the destination playlist without changing the active playback queue. */
@Composable
fun PlaylistTrackPickerScreen(
    playlistId: String,
    header: PlaylistUiState,
    library: LibraryUiState,
    query: String,
    filter: TrackFilter,
    tracks: Flow<PagingData<Track>>,
    selection: SelectionViewModel,
    onQuery: (String) -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val items = tracks.collectAsLazyPagingItems()
    val state by selection.uiState.collectAsStateWithLifecycle()
    val source =
        remember(playlistId, filter) { SelectionSource("playlist-add:$playlistId", filter) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val activity = LocalActivity.current
    LaunchedEffect(source) {
        selection.source(source)
        selection.begin()
    }
    DisposableEffect(source.context) {
        onDispose {
            if (activity?.isChangingConfigurations != true) selection.leave(source.context)
        }
    }
    BackHandler(onBack = onClose)
    val busy = state.running || state.loading || state.updating || query != filter.query
    val playlist = header.playlist
    fun toggle(track: Track) {
        if (!busy)
            selection.toggle(SelectedTrack(track.id, track.id, track.availability == "LOCAL"))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("title") { SectionTitle(stringResource(R.string.add_tracks)) }
            if (header.loading) item("loading-playlist") { LoadingState() }
            else if (playlist == null)
                item("missing-playlist") {
                    MessageCard(
                        stringResource(R.string.playlists),
                        stringResource(R.string.playlist_unavailable),
                    )
                }
            else {
                item("playlist-name") {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                }
                item("search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_tracks_to_add)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = {
                                    keyboard?.hide()
                                    focus.clearFocus()
                                }
                            ),
                        trailingIcon = {
                            if (query.isNotEmpty())
                                IconButton(onClick = { onQuery("") }, enabled = !state.running) {
                                    Icon(VioletIcons.Close, stringResource(R.string.clear_search))
                                }
                        },
                    )
                }
                item("selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.selected_count, state.selected.size))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = selection::selectAll, enabled = !busy) {
                                Text(stringResource(R.string.select_all))
                            }
                            TextButton(onClick = selection::clear, enabled = !busy) {
                                Text(stringResource(R.string.deselect_all))
                            }
                        }
                    }
                }
                libraryNotices(library, onPermission, onSettings, onRefresh)
                pagedTracks(
                    items,
                    emptySearch = query.isNotBlank(),
                    selected = state.selected.keys,
                    selecting = true,
                    onToggle = ::toggle,
                    onPlay = ::toggle,
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.running) Text(stringResource(R.string.bulk_running, state.processed))
        if (state.error)
            Text(stringResource(R.string.collection_error), color = MaterialTheme.colorScheme.error)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { selection.run(BulkAction.ADD_PLAYLIST, playlistId, onClose) },
                enabled = playlist != null && !busy && state.selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_selected_tracks, state.selected.size))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
        }
    }
}
