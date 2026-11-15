package dev.violet.music.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.core.model.DisplayMode
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import dev.violet.music.feature.collection.PlaylistRow
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    tracks: Flow<PagingData<Track>>,
    albums: Flow<PagingData<AlbumSummary>>,
    artists: Flow<PagingData<ArtistSummary>>,
    playlists: Flow<PagingData<PlaylistSummary>>,
    onAlbum: (AlbumSummary) -> Unit,
    onArtist: (ArtistSummary) -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    onCollection: (String) -> Unit,
    onStats: () -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onMenu: (Track) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCategory: (LibraryCategory) -> Unit,
    onQuery: (String) -> Unit,
    onMode: (DisplayMode) -> Unit,
    onSort: (String) -> Unit,
    selection: dev.violet.music.feature.selection.SelectionViewModel? = null,
    collections: dev.violet.music.feature.collection.CollectionViewModel? = null,
) {
    val trackItems = tracks.collectAsLazyPagingItems()
    val albumItems = albums.collectAsLazyPagingItems()
    val artistItems = artists.collectAsLazyPagingItems()
    val playlistItems = playlists.collectAsLazyPagingItems()
    val category = state.category
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val mode = state.preferences.mode(category)
    val favouritesLabel = stringResource(R.string.favourites)
    val mostLabel = stringResource(R.string.most_played)
    val holder = rememberSaveableStateHolder()
    val selectionState =
        selection?.uiState?.collectAsStateWithLifecycle()?.value
            ?: dev.violet.music.feature.selection.SelectionUiState()
    fun toggle(track: Track) {
        selection?.toggle(SelectedTrack(track.id, track.id, track.availability == "LOCAL"))
    }
    DisposableEffect(Unit) { onDispose { selection?.leave("library") } }
    LaunchedEffect(category) {
        if (category != LibraryCategory.ALL && category != LibraryCategory.TRACKS)
            selection?.cancel()
    }
    holder.SaveableStateProvider(category.name) {
        val scroll = rememberLazyGridState()
        var previousQuery by rememberSaveable { mutableStateOf(state.libraryQuery) }
        LaunchedEffect(state.libraryQuery) {
            if (previousQuery != state.libraryQuery) {
                scroll.scrollToItem(0)
                previousQuery = state.libraryQuery
            }
        }
        LazyVerticalGrid(
            columns =
                if (mode == DisplayMode.LIST) GridCells.Fixed(1)
                else GridCells.Adaptive(VioletLayout.gridMinimum),
            state = scroll,
            contentPadding = PaddingValues(VioletLayout.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
        ) {
            item("title", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle(stringResource(R.string.library))
                    TextButton(onClick = onStats) { Text(stringResource(R.string.listening_stats)) }
                }
            }
            item("filters", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryCategory.entries.forEach { value ->
                        FilterChip(
                            category == value,
                            { onCategory(value) },
                            label = { Text(stringResource(value.label())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            item("search", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_search)) },
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
                        if (state.libraryQuery.isNotEmpty())
                            IconButton(onClick = { onQuery("") }) {
                                Icon(VioletIcons.Close, stringResource(R.string.clear_search))
                            }
                    },
                )
            }
            item("display", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LibrarySortMenu(category, state.preferences, onSort)
                    TextButton(
                        onClick = {
                            onMode(
                                if (mode == DisplayMode.LIST) DisplayMode.GRID else DisplayMode.LIST
                            )
                        }
                    ) {
                        Text(
                            stringResource(
                                if (mode == DisplayMode.LIST) R.string.grid_view
                                else R.string.list_view
                            )
                        )
                    }
                }
            }
            if (
                selection != null &&
                    collections != null &&
                    (category == LibraryCategory.ALL || category == LibraryCategory.TRACKS)
            )
                item("selection", span = { GridItemSpan(maxLineSpan) }) {
                    dev.violet.music.feature.selection.SelectionToolbar(
                        selection,
                        SelectionSource(
                            "library",
                            TrackFilter(
                                query = state.libraryQuery,
                                sort = state.preferences.trackSort(),
                                periodStart = state.preferences.period.start(),
                            ),
                        ),
                        collections,
                        clearOnDispose = false,
                    )
                }
            libraryNotices(state, onPermission, onSettings, onRefresh)
            if (category == LibraryCategory.ALL || category == LibraryCategory.PLAYLISTS) {
                if (
                    state.libraryQuery.isBlank() ||
                        favouritesLabel.contains(state.libraryQuery, ignoreCase = true)
                )
                    item("favourites") {
                        SystemCollection(
                            stringResource(R.string.favourites),
                            stringResource(R.string.your_playlists),
                        ) {
                            onCollection("favourites")
                        }
                    }
                if (
                    state.libraryQuery.isBlank() ||
                        mostLabel.contains(state.libraryQuery, ignoreCase = true)
                )
                    item("most") {
                        SystemCollection(
                            stringResource(R.string.most_played),
                            stringResource(R.string.generated_playlist),
                            generated = true,
                        ) {
                            onCollection("most")
                        }
                    }
                item("create", span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = onCreatePlaylist) {
                        Text(stringResource(R.string.new_playlist))
                    }
                }
                if (category == LibraryCategory.ALL)
                    item("playlist-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.playlists)) {
                            onCategory(LibraryCategory.PLAYLISTS)
                        }
                    }
                items(
                    if (category == LibraryCategory.ALL) minOf(4, playlistItems.itemCount)
                    else playlistItems.itemCount,
                    key = playlistItems.itemKey { "playlist:${it.id}" },
                ) { i ->
                    playlistItems[i]?.let {
                        if (mode == DisplayMode.GRID) PlaylistCard(it) { onPlaylist(it) }
                        else PlaylistRow(it) { onPlaylist(it) }
                    }
                }
            }
            if (category == LibraryCategory.ALL || category == LibraryCategory.ARTISTS) {
                if (category == LibraryCategory.ALL)
                    item("artist-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.artists)) {
                            onCategory(LibraryCategory.ARTISTS)
                        }
                    }
                items(
                    if (category == LibraryCategory.ALL) minOf(4, artistItems.itemCount)
                    else artistItems.itemCount,
                    key = artistItems.itemKey { "artist:${it.id}" },
                ) { i ->
                    artistItems[i]?.let {
                        if (mode == DisplayMode.GRID) ArtistCard(it) { onArtist(it) }
                        else ArtistRow(it) { onArtist(it) }
                    }
                }
            }
            if (category == LibraryCategory.ALL || category == LibraryCategory.ALBUMS) {
                if (category == LibraryCategory.ALL)
                    item("album-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.albums)) {
                            onCategory(LibraryCategory.ALBUMS)
                        }
                    }
                items(
                    if (category == LibraryCategory.ALL) minOf(4, albumItems.itemCount)
                    else albumItems.itemCount,
                    key = albumItems.itemKey { "album:${it.albumKey}" },
                ) { i ->
                    albumItems[i]?.let {
                        if (mode == DisplayMode.GRID) AlbumCard(it) { onAlbum(it) }
                        else AlbumRow(it) { onAlbum(it) }
                    }
                }
            }
            if (category == LibraryCategory.ALL || category == LibraryCategory.TRACKS) {
                if (category == LibraryCategory.ALL)
                    item("track-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tracks)) {
                            onCategory(LibraryCategory.TRACKS)
                        }
                    }
                else
                    item("play-actions", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing)
                        ) {
                            Button(
                                onClick = { onPlayAll(false) },
                                enabled = state.hasPermission && trackItems.itemCount > 0,
                            ) {
                                Text(stringResource(R.string.play_all))
                            }
                            OutlinedButton(
                                onClick = { onPlayAll(true) },
                                enabled = state.hasPermission && trackItems.itemCount > 0,
                            ) {
                                Text(stringResource(R.string.shuffle))
                            }
                        }
                    }
                items(
                    if (category == LibraryCategory.ALL) minOf(4, trackItems.itemCount)
                    else trackItems.itemCount,
                    key = trackItems.itemKey { "track:${it.id}" },
                ) { i ->
                    trackItems[i]?.let { track ->
                        val play = { if (selectionState.active) toggle(track) else onPlay(track) }
                        if (mode == DisplayMode.GRID)
                            TrackCard(
                                track,
                                { onMenu(track) },
                                play,
                                onLongClick = { toggle(track) },
                                selected = track.id in selectionState.selected,
                                selecting = selectionState.active,
                            )
                        else
                            TrackRow(
                                track,
                                onMenu = { onMenu(track) },
                                onLongClick = { toggle(track) },
                                selected = track.id in selectionState.selected,
                                selecting = selectionState.active,
                                onPlay = play,
                            )
                    }
                }
            }
            val load =
                when (category) {
                    LibraryCategory.ARTISTS -> artistItems.loadState
                    LibraryCategory.ALBUMS -> albumItems.loadState
                    LibraryCategory.PLAYLISTS -> playlistItems.loadState
                    else -> trackItems.loadState
                }
            if (load.refresh is LoadState.Loading || load.append is LoadState.Loading)
                item("loading", span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
            if (load.hasError)
                item("error", span = { GridItemSpan(maxLineSpan) }) {
                    MessageCard(
                        stringResource(R.string.library),
                        stringResource(R.string.load_error),
                        stringResource(R.string.retry),
                    ) {
                        trackItems.retry()
                        albumItems.retry()
                        artistItems.retry()
                        playlistItems.retry()
                    }
                }
            val count =
                when (category) {
                    LibraryCategory.ARTISTS -> artistItems.itemCount
                    LibraryCategory.ALBUMS -> albumItems.itemCount
                    LibraryCategory.PLAYLISTS -> playlistItems.itemCount
                    else -> trackItems.itemCount
                }
            if (
                count == 0 &&
                    load.refresh is LoadState.NotLoading &&
                    category != LibraryCategory.PLAYLISTS
            )
                item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    MessageCard(
                        stringResource(
                            if (state.libraryQuery.isBlank()) R.string.empty_title
                            else R.string.search_empty
                        ),
                        stringResource(
                            if (state.libraryQuery.isBlank()) R.string.empty_description
                            else R.string.search_empty_description
                        ),
                    )
                }
        }
    }
}

fun LibraryCategory.label() =
    when (this) {
        LibraryCategory.ALL -> R.string.all
        LibraryCategory.PLAYLISTS -> R.string.playlists
        LibraryCategory.ARTISTS -> R.string.artists
        LibraryCategory.ALBUMS -> R.string.albums
        LibraryCategory.TRACKS -> R.string.tracks
    }

@Composable
private fun SystemCollection(
    title: String,
    description: String,
    generated: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (generated) PlaybackIcons.Repeat else PlaybackIcons.Heart, null)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
