package dev.violet.music.core.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.feature.collection.*
import dev.violet.music.feature.home.*
import dev.violet.music.feature.library.*
import dev.violet.music.feature.player.*
import dev.violet.music.feature.search.SearchScreen
import dev.violet.music.feature.settings.SettingsScreen
import dev.violet.music.feature.stats.*

@Composable
fun AppNavigation(
    nav: NavHostController,
    library: LibraryViewModel,
    player: PlayerViewModel,
    collections: CollectionViewModel,
    home: HomeViewModel,
    stats: StatsViewModel,
    selection: dev.violet.music.feature.selection.SelectionViewModel,
    onPermission: () -> Unit,
    onAndroidSettings: () -> Unit,
    onTrackMenu: (Track, TrackFilter?, String?) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistTrackMenu: (Track, String, String) -> Unit,
) {
    val state by library.uiState.collectAsStateWithLifecycle()
    val playback by player.uiState.collectAsStateWithLifecycle()
    val currentTrack by player.currentTrack.collectAsStateWithLifecycle()
    val tint by player.artworkTint.collectAsStateWithLifecycle()
    val coverBusy by collections.coverBusy.collectAsStateWithLifecycle()
    fun album(key: String, title: String) {
        nav.navigate("album/${Uri.encode(key)}/${Uri.encode(title)}") { launchSingleTop = true }
    }
    fun artist(id: String) {
        nav.navigate("artist/${Uri.encode(id)}") { launchSingleTop = true }
    }
    fun collection(kind: String) {
        if (kind == "playlists") {
            library.category(LibraryCategory.PLAYLISTS)
            nav.navigate("library") { launchSingleTop = true }
        } else nav.navigate("collection/$kind") { launchSingleTop = true }
    }
    NavHost(nav, startDestination = "home") {
        composable("home") {
            val homeState by home.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                homeState,
                state,
                ::collection,
                { nav.navigate("playlist/$it") },
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { track, kind -> player.play(track.id, library.collectionFilter(kind)) },
                onMenu = { track, kind -> onTrackMenu(track, library.collectionFilter(kind), null) },
            )
        }
        composable("search") {
            SearchScreen(
                state,
                library.searchResults,
                library::updateQuery,
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { player.play(it.id, TrackFilter(query = state.query)) },
                onMenu = { onTrackMenu(it, TrackFilter(query = state.query), null) },
                selection = selection,
                collections = collections,
            )
        }
        composable("library") {
            LibraryScreen(
                state,
                library.tracks,
                library.albums,
                library.artists,
                library.playlists,
                onAlbum = { album(it.albumKey, it.title) },
                onArtist = { artist(it.id) },
                onPlaylist = { nav.navigate("playlist/${it.id}") },
                onCollection = ::collection,
                onStats = { nav.navigate("stats") },
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { player.play(it.id, library.filter()) },
                onPlayAll = { player.play(null, library.filter(), it) },
                onMenu = { onTrackMenu(it, library.filter(), null) },
                onCreatePlaylist,
                onCategory = library::category,
                onQuery = library::updateLibraryQuery,
                onMode = { library.mode(state.category, it) },
                onSort = { library.sort(state.category, it) },
                selection = selection,
                collections = collections,
            )
        }
        composable("settings") {
            SettingsScreen(
                state,
                onPermission,
                onAndroidSettings,
                library::refresh,
                library::period,
            ) {
                nav.navigate("stats")
            }
        }
        composable("stats") {
            val statsState by stats.uiState.collectAsStateWithLifecycle()
            StatsScreen(
                statsState,
                stats::period,
                stats::page,
                { player.play(it) },
                ::artist,
                ::album,
            )
        }
        composable("collection/{kind}") { entry ->
            val kind = requireNotNull(entry.arguments?.getString("kind"))
            val tracks = remember(kind) { library.collectionTracks(kind) }
            val title =
                stringResource(
                    when (kind) {
                        "quick" -> R.string.quick_picks
                        "most" -> R.string.most_played
                        "recent" -> R.string.recently_played
                        "added" -> R.string.recently_added
                        else -> R.string.favourites
                    }
                )
            CollectionScreen(
                title,
                state,
                tracks,
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { player.play(it.id, library.collectionFilter(kind)) },
                onPlayAll = { player.play(null, library.collectionFilter(kind), it) },
                onMenu = { onTrackMenu(it, library.collectionFilter(kind), null) },
                selection = selection,
                collections = collections,
                selectionSource =
                    SelectionSource("collection:$kind", library.collectionFilter(kind)),
                generated = kind == "most",
                subtitle =
                    if (kind == "quick" || kind == "most")
                        stringResource(state.preferences.period.label())
                    else null,
            )
        }
        composable(
            "album/{key}/{title}",
            arguments =
                listOf(
                    navArgument("key") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
        ) { entry ->
            val key = requireNotNull(entry.arguments?.getString("key"))
            val title = requireNotNull(entry.arguments?.getString("title"))
            val tracks = remember(key) { library.albumTracks(key) }
            CollectionScreen(
                title,
                state,
                tracks,
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { player.play(it.id, TrackFilter(albumKey = key)) },
                onPlayAll = { player.play(null, TrackFilter(albumKey = key), it) },
                onMenu = { onTrackMenu(it, TrackFilter(albumKey = key), null) },
                selection = selection,
                collections = collections,
                selectionSource = SelectionSource("album:$key", TrackFilter(albumKey = key)),
            )
        }
        composable(
            "artist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            val name by remember(id) { library.artistName(id) }.collectAsStateWithLifecycle(null)
            val tracks = remember(id) { library.artistTracks(id) }
            val albums = remember(id) { library.artistAlbums(id) }
            CollectionScreen(
                name.orEmpty(),
                state,
                tracks,
                onPermission,
                onAndroidSettings,
                library::refresh,
                onPlay = { player.play(it.id, TrackFilter(artistId = id)) },
                onPlayAll = { player.play(null, TrackFilter(artistId = id), it) },
                onMenu = { onTrackMenu(it, TrackFilter(artistId = id), null) },
                albums = albums,
                onAlbum = { album(it.albumKey, it.title) },
                selection = selection,
                collections = collections,
                selectionSource = SelectionSource("artist:$id", TrackFilter(artistId = id)),
            )
        }
        composable(
            "playlist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            val header by
                remember(id) { collections.playlist(id) }
                    .collectAsStateWithLifecycle(PlaylistUiState())
            val tracks = remember(id) { collections.entries(id) }
            PlaylistScreen(
                header,
                tracks,
                onPlay = { player.playPlaylist(id, it) },
                onPlayAll = { player.playPlaylist(id, shuffle = it) },
                onMenu = { onTrackMenu(it, null, null) },
                onRename = { collections.rename(id, it) },
                onDelete = { collections.delete(id) { nav.popBackStack() } },
                onMove = collections::move,
                onRemove = collections::remove,
                onAddTracks = {
                    collections.updateAddTracksQuery("")
                    nav.navigate("playlist/$id/add-tracks") { launchSingleTop = true }
                },
                onApplyCover = { collections.applyCover(id, it) },
                onRemoveCover = { collections.removeCover(id) },
                coverBusy = coverBusy,
                selection = selection,
                collections = collections,
                playlistId = id,
                onEntryMenu = { onPlaylistTrackMenu(it.track, id, it.id) },
            )
        }
        composable(
            "playlist/{id}/add-tracks",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            val header by
                remember(id) { collections.playlist(id) }
                    .collectAsStateWithLifecycle(PlaylistUiState())
            val query by collections.addTracksQuery.collectAsStateWithLifecycle()
            val filter by collections.addTracksFilter.collectAsStateWithLifecycle()
            PlaylistTrackPickerScreen(
                id,
                header,
                state,
                query,
                filter,
                collections.addTrackCandidates,
                selection,
                collections,
                entry.id,
                collections::updateAddTracksQuery,
                onPermission,
                onAndroidSettings,
                library::refresh,
            ) {
                nav.popBackStack()
            }
        }
        composable(
            "now-playing",
            enterTransition = { slideInVertically(tween(220)) { it / 5 } + fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(140)) },
            popExitTransition = { slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180)) },
        ) {
            NowPlayingScreen(
                playback,
                player.controller,
                currentTrack,
                { onTrackMenu(it, null, playback.current?.occurrenceId) },
                onBack = { nav.popBackStack() },
                onArtist = ::artist,
                credits = player.credits,
                tint = tint,
            ) {
                nav.navigate("queue") { launchSingleTop = true }
            }
        }
        composable("queue") {
            QueueScreen(
                playback,
                player.controller,
                onMenu = { item ->
                    player.trackMenu(item.id) { onTrackMenu(it, null, item.occurrenceId) }
                },
            )
        }
    }
}
