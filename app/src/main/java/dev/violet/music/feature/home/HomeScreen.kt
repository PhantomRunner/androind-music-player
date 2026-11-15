package dev.violet.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import dev.violet.music.feature.library.LibraryUiState

private data class HomeTrackSection(val title: Int, val kind: String, val tracks: List<Track>)

@Composable
fun HomeScreen(
    state: HomeUiState,
    catalogue: LibraryUiState,
    onCollection: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track, String) -> Unit,
    onMenu: (Track, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(VioletLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
    ) {
        item("shortcuts") { HomeShortcuts(onCollection) }
        libraryNotices(catalogue, onPermission, onSettings, onRefresh)
        when {
            state.loading -> item("loading") { LoadingState() }
            state.error -> item("error") {
                MessageCard(stringResource(R.string.home), stringResource(R.string.load_error))
            }
            else -> {
                val sections = listOf(
                    HomeTrackSection(R.string.recently_played, "recent", state.recentlyPlayed),
                    HomeTrackSection(R.string.quick_picks, "quick", state.quickPicks),
                    HomeTrackSection(R.string.recently_added, "added", state.recentlyAdded),
                )
                for (section in sections.filter { it.tracks.isNotEmpty() }) {
                    item(section.kind) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(stringResource(section.title)) {
                                onCollection(section.kind)
                            }
                            if (section.kind == "quick") {
                                Text(
                                    stringResource(state.period.label()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(section.tracks, key = { it.id }) { track ->
                                    Box(Modifier.width(VioletLayout.carouselWidth)) {
                                        ArtworkCard(
                                            track.title,
                                            track.artistName,
                                            track.artworkUri,
                                            onClick = { onPlay(track, section.kind) },
                                            revision = track.artworkVersion,
                                            onMenu = { onMenu(track, section.kind) },
                                            enabled = track.availability == "LOCAL",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.playlists.isNotEmpty()) {
                    item("playlists") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(stringResource(R.string.your_playlists)) {
                                onCollection("playlists")
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.playlists, key = { it.id }) { playlist ->
                                    Box(Modifier.width(VioletLayout.carouselWidth)) {
                                        PlaylistCard(playlist) { onPlaylist(playlist.id) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (catalogue.hasPermission && !catalogue.loading && catalogue.stats.trackCount == 0) {
                    item("empty") {
                        MessageCard(
                            stringResource(R.string.empty_title),
                            stringResource(R.string.empty_description),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeShortcuts(onCollection: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollectionShortcut(
            stringResource(R.string.favourites),
            PlaybackIcons.Heart,
            { onCollection("favourites") },
            Modifier.weight(1f),
        )
        CollectionShortcut(
            stringResource(R.string.recently_played),
            VioletIcons.History,
            { onCollection("recent") },
            Modifier.weight(1f),
        )
        CollectionShortcut(
            stringResource(R.string.playlists),
            VioletIcons.Library,
            { onCollection("playlists") },
            Modifier.weight(1f),
        )
        CollectionShortcut(
            stringResource(R.string.recently_added),
            VioletIcons.Add,
            { onCollection("added") },
            Modifier.weight(1f),
        )
    }
}
