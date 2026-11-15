package dev.violet.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.pagedTracks
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CollectionScreen(
    title: String,
    state: LibraryUiState,
    tracks: Flow<PagingData<Track>>,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onMenu: (Track) -> Unit,
    albums: Flow<PagingData<dev.violet.music.core.model.AlbumSummary>>? = null,
    onAlbum: (dev.violet.music.core.model.AlbumSummary) -> Unit = {},
    selection: dev.violet.music.feature.selection.SelectionViewModel? = null,
    collections: dev.violet.music.feature.collection.CollectionViewModel? = null,
    selectionSource: dev.violet.music.core.model.SelectionSource? = null,
    generated: Boolean = false,
    subtitle: String? = null,
) {
    val items = tracks.collectAsLazyPagingItems()
    val albumItems = albums?.collectAsLazyPagingItems()
    val selectionState =
        selection?.uiState?.collectAsStateWithLifecycle()?.value
            ?: dev.violet.music.feature.selection.SelectionUiState()
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("title") {
            androidx.compose.foundation.layout.Column {
                SectionTitle(title)
                if (generated)
                    Text(
                        stringResource(R.string.generated_playlist),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                subtitle?.let { Text(it) }
            }
        }
        if (selection != null && collections != null && selectionSource != null)
            item("selection") {
                dev.violet.music.feature.selection.SelectionToolbar(
                    selection,
                    selectionSource,
                    collections,
                )
            }
        if (albumItems != null && albumItems.itemCount > 0) {
            item("albums-title") { SectionTitle(stringResource(R.string.albums)) }
            items(
                albumItems.itemCount,
                key = albumItems.itemKey { "artist-album:${it.albumKey}" },
            ) { index ->
                albumItems[index]?.let { album ->
                    dev.violet.music.core.ui.AlbumRow(album) { onAlbum(album) }
                }
            }
            item("tracks-title") { SectionTitle(stringResource(R.string.tracks)) }
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        if (items.itemCount > 0 && state.hasPermission)
            item("play-actions") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onPlayAll(false) }) {
                        Text(stringResource(R.string.play_all))
                    }
                    OutlinedButton(onClick = { onPlayAll(true) }) {
                        Text(stringResource(R.string.shuffle))
                    }
                }
            }
        pagedTracks(
            items,
            onMenu = onMenu,
            selected = selectionState.selected.keys,
            selecting = selectionState.active,
            onToggle =
                selection?.let { vm ->
                    { track ->
                        vm.toggle(
                            dev.violet.music.core.model.SelectedTrack(
                                track.id,
                                track.id,
                                track.availability == "LOCAL",
                            )
                        )
                    }
                },
            onPlay = onPlay,
        )
    }
}
