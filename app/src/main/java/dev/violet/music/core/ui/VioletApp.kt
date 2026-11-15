package dev.violet.music.core.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.violet.music.R
import dev.violet.music.feature.home.HomeScreen
import dev.violet.music.feature.library.CollectionScreen
import dev.violet.music.feature.library.LibraryScreen
import dev.violet.music.feature.library.LibraryViewModel
import dev.violet.music.feature.search.SearchScreen
import dev.violet.music.feature.settings.SettingsScreen
import dev.violet.music.feature.player.PlayerViewModel
import dev.violet.music.feature.player.MiniPlayer
import dev.violet.music.feature.player.NowPlayingScreen
import dev.violet.music.feature.player.QueueScreen
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.core.playback.PlaybackError

private data class Destination(val route: String, val label: Int, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", R.string.home, VioletIcons.Home),
    Destination("search", R.string.search, VioletIcons.Search),
    Destination("library", R.string.library, VioletIcons.Library),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VioletApp(
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onPermission: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by playerViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(playback.error) {
        playback.error?.let { error ->
            val message = when (error) {
                PlaybackError.CONNECTION -> R.string.connection_error
                PlaybackError.PERMISSION -> R.string.play_permission_error
                PlaybackError.UNAVAILABLE -> R.string.unavailable_error
                PlaybackError.EMPTY_QUEUE -> R.string.empty_queue_error
                PlaybackError.PLAYBACK -> R.string.playback_error
            }
            val result = snackbar.showSnackbar(
                context.getString(message),
                actionLabel = if (error == PlaybackError.CONNECTION) context.getString(R.string.retry) else null,
            )
            if (result == SnackbarResult.ActionPerformed) playerViewModel.controller.reconnect()
            playerViewModel.controller.dismissError()
        }
    }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isCollection = route.startsWith("album/") || route.startsWith("artist/")
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (isCollection || route in listOf("settings", "now-playing", "queue")) IconButton(onClick = { navController.popBackStack() }) {
                        Icon(VioletIcons.Back, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (route != "settings") IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                        Icon(VioletIcons.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (route != "now-playing") MiniPlayer(playback, playerViewModel.controller) {
                    navController.navigate("now-playing") { launchSingleTop = true }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route || (isCollection && destination.route == "library"),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        state, { navController.navigate("library") }, { navController.navigate("search") },
                        onPermission, onAndroidSettings, viewModel::refresh,
                        onPlay = { track -> playerViewModel.playIds(state.recentTracks.map { it.id }, state.recentTracks.indexOfFirst { it.id == track.id }) },
                    )
                }
                composable("search") {
                    SearchScreen(state, viewModel.searchResults, viewModel::updateQuery, onPermission, onAndroidSettings, viewModel::refresh) {
                        playerViewModel.play(it.id, TrackFilter(query = state.query))
                    }
                }
                composable("library") {
                    LibraryScreen(
                        state, viewModel.tracks, viewModel.albums, viewModel.artists,
                        onAlbum = { navController.navigate("album/${Uri.encode(it.albumKey)}/${Uri.encode(it.title)}") },
                        onArtist = { navController.navigate("artist/${Uri.encode(it.name)}") },
                        onPermission, onAndroidSettings, viewModel::refresh,
                        onPlay = { playerViewModel.play(it.id) }, onPlayAll = { playerViewModel.play(null, shuffle = it) },
                    )
                }
                composable("settings") {
                    SettingsScreen(state, onPermission, onAndroidSettings, viewModel::refresh)
                }
                composable(
                    "album/{key}/{title}",
                    arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("title") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val key = requireNotNull(backStackEntry.arguments?.getString("key"))
                    val title = requireNotNull(backStackEntry.arguments?.getString("title"))
                    val tracks = remember(key) { viewModel.albumTracks(key) }
                    CollectionScreen(title, state, tracks, onPermission, onAndroidSettings, viewModel::refresh,
                        onPlay = { playerViewModel.play(it.id, TrackFilter(albumKey = key)) },
                        onPlayAll = { playerViewModel.play(null, TrackFilter(albumKey = key), it) })
                }
                composable("artist/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { backStackEntry ->
                    val name = requireNotNull(backStackEntry.arguments?.getString("name"))
                    val tracks = remember(name) { viewModel.artistTracks(name) }
                    CollectionScreen(name, state, tracks, onPermission, onAndroidSettings, viewModel::refresh,
                        onPlay = { playerViewModel.play(it.id, TrackFilter(artistName = name)) },
                        onPlayAll = { playerViewModel.play(null, TrackFilter(artistName = name), it) })
                }
                composable("now-playing") {
                    NowPlayingScreen(playback, playerViewModel.controller) { navController.navigate("queue") }
                }
                composable("queue") { QueueScreen(playback, playerViewModel.controller) }
            }
        }
    }
}
