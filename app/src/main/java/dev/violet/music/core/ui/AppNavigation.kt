package dev.violet.music.core.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.feature.collection.CollectionViewModel
import dev.violet.music.feature.collection.PlaylistScreen
import dev.violet.music.feature.collection.PlaylistUiState
import dev.violet.music.feature.home.HomeScreen
import dev.violet.music.feature.library.CollectionScreen
import dev.violet.music.feature.library.LibraryScreen
import dev.violet.music.feature.library.LibraryViewModel
import dev.violet.music.feature.player.NowPlayingScreen
import dev.violet.music.feature.player.PlayerViewModel
import dev.violet.music.feature.player.QueueScreen
import dev.violet.music.feature.search.SearchScreen
import dev.violet.music.feature.settings.SettingsScreen

@Composable
fun AppNavigation(
    nav: NavHostController,
    library: LibraryViewModel,
    player: PlayerViewModel,
    collections: CollectionViewModel,
    onPermission: () -> Unit,
    onAndroidSettings: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    val state by library.uiState.collectAsStateWithLifecycle()
    val playback by player.uiState.collectAsStateWithLifecycle()
    val currentTrack by player.currentTrack.collectAsStateWithLifecycle()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state, { nav.navigate("library") }, { nav.navigate("search") }, onPermission, onAndroidSettings, library::refresh,
                onPlay = { track -> player.playIds(state.recentTracks.map { it.id }, state.recentTracks.indexOfFirst { it.id == track.id }) },
                onMenu = onTrackMenu,
            )
        }
        composable("search") {
            SearchScreen(
                state, library.searchResults, library::updateQuery, onPermission, onAndroidSettings, library::refresh,
                onPlay = { player.play(it.id, TrackFilter(query = state.query)) }, onMenu = onTrackMenu,
            )
        }
        composable("library") {
            LibraryScreen(
                state, library.tracks, library.albums, library.artists, library.favourites, collections.playlists,
                onAlbum = { nav.navigate("album/${Uri.encode(it.albumKey)}/${Uri.encode(it.title)}") },
                onArtist = { nav.navigate("artist/${Uri.encode(it.name)}") },
                onPermission, onAndroidSettings, library::refresh,
                onPlay = { track, favourites -> player.play(track.id, TrackFilter(recentlyAddedFirst = state.recentSort, favouritesOnly = favourites)) },
                onPlayAll = { shuffle, favourites -> player.play(null, TrackFilter(recentlyAddedFirst = state.recentSort, favouritesOnly = favourites), shuffle) },
                onMenu = onTrackMenu, onPlaylist = { nav.navigate("playlist/${it.id}") }, onCreatePlaylist = onCreatePlaylist,
                onSort = library::sortByRecent,
            )
        }
        composable("settings") { SettingsScreen(state, onPermission, onAndroidSettings, library::refresh) }
        composable("album/{key}/{title}", arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("title") { type = NavType.StringType })) { entry ->
            val key = requireNotNull(entry.arguments?.getString("key"))
            val title = requireNotNull(entry.arguments?.getString("title"))
            val tracks = remember(key) { library.albumTracks(key) }
            CollectionScreen(title, state, tracks, onPermission, onAndroidSettings, library::refresh,
                onPlay = { player.play(it.id, TrackFilter(albumKey = key)) }, onPlayAll = { player.play(null, TrackFilter(albumKey = key), it) }, onMenu = onTrackMenu)
        }
        composable("artist/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            val name = requireNotNull(entry.arguments?.getString("name"))
            val tracks = remember(name) { library.artistTracks(name) }
            CollectionScreen(name, state, tracks, onPermission, onAndroidSettings, library::refresh,
                onPlay = { player.play(it.id, TrackFilter(artistName = name)) }, onPlayAll = { player.play(null, TrackFilter(artistName = name), it) }, onMenu = onTrackMenu)
        }
        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            val header by remember(id) { collections.playlist(id) }.collectAsStateWithLifecycle(PlaylistUiState())
            val tracks = remember(id) { collections.entries(id) }
            PlaylistScreen(
                header, tracks, onPlay = { player.playPlaylist(id, it) }, onPlayAll = { player.playPlaylist(id, shuffle = it) },
                onMenu = onTrackMenu, onRename = { collections.rename(id, it) }, onDelete = { collections.delete(id) { nav.popBackStack() } },
                onMove = collections::move, onRemove = collections::remove, onLibrary = { nav.navigate("library") },
            )
        }
        composable("now-playing") {
            NowPlayingScreen(playback, player.controller, currentTrack, onTrackMenu) { nav.navigate("queue") }
        }
        composable("queue") { QueueScreen(playback, player.controller) }
    }
}

