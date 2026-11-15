package dev.violet.music.feature.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.R
import dev.violet.music.core.model.SelectedTrack
import dev.violet.music.core.model.SelectionSource
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.ui.*
import dev.violet.music.feature.library.LibraryUiState
import dev.violet.music.feature.selection.SelectionViewModel
import dev.violet.music.feature.selection.SelectionSourceEffect
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
    collections: CollectionViewModel,
    pickerEntryId: String,
    onQuery: (String) -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val items = tracks.collectAsLazyPagingItems()
    val state by selection.uiState.collectAsStateWithLifecycle()
    val addition by collections.addition.collectAsStateWithLifecycle()
    val source =
        remember(playlistId, filter) { SelectionSource("playlist-add:$playlistId", filter) }
    SelectionSourceEffect(selection, source, startSelecting = true)
    BackHandler { if (!addition.pending) onClose() }
    val queryPending = query != filter.query
    val busy = state.source != source || !state.active || addition.pending || state.loading || state.updating ||
        queryPending || items.loadState.refresh is LoadState.Loading
    val playlist = header.playlist
    val toggle: (Track) -> Unit = { track ->
        selection.toggle(SelectedTrack(track.id, track.id, track.availability == "LOCAL"), source)
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
                    CatalogueSearchField(
                        query,
                        onQuery,
                        stringResource(R.string.search_tracks_to_add),
                        enabled = !addition.pending,
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
                if (!queryPending) {
                    pagedTracks(
                        items,
                        emptySearch = query.isNotBlank(),
                        selected = state.selected.keys,
                        selecting = true,
                        rowEnabled = !busy,
                        onToggle = toggle,
                        onPlay = toggle,
                    )
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (addition.working) Text(stringResource(R.string.checking_playlist_tracks))
        if (state.error)
            Text(stringResource(R.string.collection_error), color = MaterialTheme.colorScheme.error)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    collections.add(
                        playlistId,
                        selection.playlistAddItems(source.context),
                        PlaylistAddOrigin(source.context, pickerEntryId),
                    )
                },
                enabled = playlist != null && !busy && state.selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_selected_tracks, state.selected.size))
            }
            TextButton(onClick = onClose, enabled = !addition.pending) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
