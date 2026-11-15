package dev.violet.music.feature.search

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import dev.violet.music.feature.collection.CollectionViewModel
import dev.violet.music.feature.collection.PlaylistRow
import dev.violet.music.feature.library.LibraryUiState
import dev.violet.music.feature.selection.SelectionToolbar
import dev.violet.music.feature.selection.SelectionViewModel

@Composable
fun SearchScreen(
    search: SearchViewModel,
    catalogue: LibraryUiState,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track, TrackFilter) -> Unit,
    onMenu: (Track, TrackFilter) -> Unit,
    onAlbum: (AlbumSummary) -> Unit,
    onArtist: (ArtistSummary) -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    selection: SelectionViewModel,
    collections: CollectionViewModel,
) {
    val state by search.uiState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var requestFocus by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val activity = LocalActivity.current
    val isLanding = state.mode == SearchMode.LANDING
    val showingHistory = state.mode == SearchMode.INPUT && state.query.isBlank()
    val trackSource = SelectionSource("search", TrackFilter(query = state.effectiveQuery))
    val trackResults = !isLanding && !showingHistory && state.category == SearchCategory.TRACKS

    fun close() {
        keyboard?.hide()
        focus.clearFocus()
        search.close()
    }

    BackHandler(enabled = !isLanding && !keyboardVisible, onBack = ::close)
    LaunchedEffect(isLanding, requestFocus) {
        if (!isLanding && requestFocus) {
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboard?.show()
            requestFocus = false
        }
    }
    LaunchedEffect(trackResults, trackSource) {
        if (trackResults) selection.source(trackSource) else selection.leave(trackSource.context)
    }
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) selection.leave(trackSource.context)
        }
    }

    if (isLanding) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(VioletLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
        ) {
            item("search-trigger") {
                Surface(
                    onClick = {
                        requestFocus = true
                        search.openInput()
                    },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(VioletIcons.Search, null)
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            libraryNotices(catalogue, onPermission, onSettings, onRefresh)
            item("browse") { BrowseCategories(search::browse) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = VioletLayout.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogueSearchField(
                    state.query,
                    search::updateQuery,
                    stringResource(R.string.search_hint),
                    Modifier.weight(1f).focusRequester(focusRequester),
                    onSubmit = { search.submit(state.query) },
                )
                TextButton(onClick = ::close) { Text(stringResource(R.string.cancel)) }
            }
            key(if (showingHistory) null else state.category, state.effectiveQuery) {
                val results =
                    if (!showingHistory && state.query.trim() == state.effectiveQuery) {
                        searchResults(
                            search, state, trackSource, selection, collections, onPlay, onMenu,
                            onAlbum, onArtist, onPlaylist,
                        )
                    } else null
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(VioletLayout.contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        showingHistory -> recentSearches(state.recent, search)
                        results == null -> item("debounce") { LoadingState() }
                        else -> {
                            item("result-filters") {
                                SearchFilters(state.category, search::select)
                            }
                            results()
                        }
                    }
                    libraryNotices(catalogue, onPermission, onSettings, onRefresh)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseCategories(onBrowse: (SearchCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.search_browse))
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (category in SearchCategory.entries) {
                val colors = MaterialTheme.colorScheme
                Surface(
                    onClick = { onBrowse(category) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surface,
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 100.dp)
                            .background(Brush.linearGradient(listOf(
                                colors.surfaceVariant,
                                colors.primaryContainer.copy(alpha = 0.28f + category.ordinal * 0.07f),
                            )))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(category.icon, null, tint = colors.primary, modifier = Modifier.size(30.dp))
                        Text(stringResource(category.label), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilters(active: SearchCategory, onSelect: (SearchCategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (category in SearchCategory.entries) {
            FilterChip(
                selected = active == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.label)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

private fun LazyListScope.recentSearches(recent: List<String>, search: SearchViewModel) {
    item("recent-title") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.recent_searches),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            if (recent.isNotEmpty()) {
                TextButton(onClick = search::clearHistory) { Text(stringResource(R.string.clear_recent_searches)) }
            }
        }
    }
    if (recent.isEmpty()) {
        item("recent-empty") {
            Text(stringResource(R.string.no_recent_searches), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(recent, key = { "recent:$it" }) { query ->
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        Surface(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                search.repeat(query)
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(VioletIcons.History, null, tint = MaterialTheme.colorScheme.primary)
                Text(query, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { search.remove(query) }) {
                    Icon(VioletIcons.Close, stringResource(R.string.remove_recent_search, query))
                }
            }
        }
    }
}

@Composable
private fun searchResults(
    search: SearchViewModel,
    state: SearchUiState,
    source: SelectionSource,
    selection: SelectionViewModel,
    collections: CollectionViewModel,
    onPlay: (Track, TrackFilter) -> Unit,
    onMenu: (Track, TrackFilter) -> Unit,
    onAlbum: (AlbumSummary) -> Unit,
    onArtist: (ArtistSummary) -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
): LazyListScope.() -> Unit {
    val query = state.effectiveQuery
    return when (state.category) {
        SearchCategory.TRACKS -> {
            val tracks = remember(query) { search.tracks(query) }.collectAsLazyPagingItems()
            val selected by selection.uiState.collectAsStateWithLifecycle()
            val filter = TrackFilter(query = query)
            val content: LazyListScope.() -> Unit = {
                item("track-selection") {
                    SelectionToolbar(selection, source, collections, clearOnDispose = false)
                }
                pagedTracks(
                    tracks,
                    emptySearch = query.isNotEmpty(),
                    onMenu = { onMenu(it, filter) },
                    selected = selected.selected.keys,
                    selecting = selected.active,
                    onToggle = { selection.toggle(SelectedTrack(it.id, it.id, it.availability == "LOCAL")) },
                    onPlay = {
                        search.submit(query)
                        onPlay(it, filter)
                    },
                )
            }
            content
        }
        SearchCategory.ALBUMS -> {
            val albums = remember(query) { search.albums(query) }.collectAsLazyPagingItems()
            pagedResults(albums, query, AlbumSummary::albumKey) { album ->
                AlbumRow(album) {
                    search.submit(query)
                    onAlbum(album)
                }
            }
        }
        SearchCategory.ARTISTS -> {
            val artists = remember(query) { search.artists(query) }.collectAsLazyPagingItems()
            pagedResults(artists, query, ArtistSummary::id) { artist ->
                ArtistRow(artist) {
                    search.submit(query)
                    onArtist(artist)
                }
            }
        }
        SearchCategory.PLAYLISTS -> {
            val playlists = remember(query) { search.playlists(query) }.collectAsLazyPagingItems()
            pagedResults(playlists, query, PlaylistSummary::id) { playlist ->
                PlaylistRow(playlist) {
                    search.submit(query)
                    onPlaylist(playlist)
                }
            }
        }
    }
}

private fun <T : Any> pagedResults(
    results: LazyPagingItems<T>,
    query: String,
    id: (T) -> String,
    row: @Composable (T) -> Unit,
): LazyListScope.() -> Unit = {
    when {
        results.loadState.hasError -> item("error") {
            MessageCard(
                stringResource(R.string.search),
                stringResource(R.string.load_error),
                stringResource(R.string.retry),
                results::retry,
            )
        }
        results.loadState.refresh is LoadState.Loading -> item("loading") { LoadingState() }
        results.itemCount == 0 -> item("empty") {
            MessageCard(
                stringResource(if (query.isEmpty()) R.string.empty_title else R.string.search_empty),
                stringResource(if (query.isEmpty()) R.string.empty_description else R.string.search_empty_description),
            )
        }
    }
    items(results.itemCount, key = results.itemKey(id)) { index -> results[index]?.let { row(it) } }
    if (results.loadState.append is LoadState.Loading) {
        item("append") { LoadingState() }
    }
}

private val SearchCategory.label: Int
    get() = when (this) {
        SearchCategory.TRACKS -> R.string.tracks
        SearchCategory.ALBUMS -> R.string.albums
        SearchCategory.ARTISTS -> R.string.artists
        SearchCategory.PLAYLISTS -> R.string.playlists
    }

private val SearchCategory.icon
    get() = when (this) {
        SearchCategory.TRACKS -> VioletIcons.Music
        SearchCategory.ALBUMS -> VioletIcons.Album
        SearchCategory.ARTISTS -> VioletIcons.Artist
        SearchCategory.PLAYLISTS -> VioletIcons.Library
    }
