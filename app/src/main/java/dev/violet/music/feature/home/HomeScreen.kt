package dev.violet.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import dev.violet.music.feature.library.LibraryUiState

data class HomeTrackSection(val title: Int, val kind: String, val tracks: List<Track>)

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
        contentPadding = PaddingValues(VioletLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(VioletLayout.sectionSpacing),
    ) {
        item("title") {
            Text(stringResource(R.string.home), style = MaterialTheme.typography.headlineLarge)
        }
        libraryNotices(catalogue, onPermission, onSettings, onRefresh)
        when {
            state.loading -> item { LoadingState() }
            state.error ->
                item {
                    MessageCard(stringResource(R.string.home), stringResource(R.string.load_error))
                }
            else -> {
                listOf(
                        HomeTrackSection(R.string.quick_picks, "quick", state.quickPicks),
                        HomeTrackSection(R.string.recently_played, "recent", state.recentlyPlayed),
                        HomeTrackSection(R.string.recently_added, "added", state.recentlyAdded),
                    )
                    .forEach { section ->
                        item("${section.kind}-heading") {
                            Column {
                                SectionHeader(stringResource(section.title)) {
                                    onCollection(section.kind)
                                }
                                if (section.kind == "quick")
                                    Text(
                                        stringResource(state.period.label()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            }
                        }
                        item("${section.kind}-tracks") {
                            if (section.tracks.isEmpty())
                                MessageCard(
                                    stringResource(
                                        if (section.kind == "added") R.string.empty_title
                                        else R.string.history_empty
                                    ),
                                    stringResource(
                                        if (section.kind == "added") R.string.empty_description
                                        else R.string.history_empty_description
                                    ),
                                )
                            else
                                LazyRow(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(VioletLayout.itemSpacing)
                                ) {
                                    items(section.tracks, key = { it.id }) { track ->
                                        Box(Modifier.width(VioletLayout.carouselWidth)) {
                                            TrackCard(
                                                track,
                                                { onMenu(track, section.kind) },
                                                { onPlay(track, section.kind) },
                                            )
                                        }
                                    }
                                }
                        }
                    }
                item("playlists-heading") {
                    SectionHeader(stringResource(R.string.your_playlists)) {
                        onCollection("playlists")
                    }
                }
                item("playlists") {
                    if (state.playlists.isEmpty())
                        MessageCard(
                            stringResource(R.string.empty_playlists_title),
                            stringResource(R.string.empty_playlists_description),
                        )
                    else
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing)
                        ) {
                            items(state.playlists, key = { it.id }) { playlist ->
                                Box(Modifier.width(VioletLayout.carouselWidth)) {
                                    PlaylistCard(playlist) { onPlaylist(playlist.id) }
                                }
                            }
                        }
                }
            }
        }
    }
}
