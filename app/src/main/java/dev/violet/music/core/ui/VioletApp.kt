package dev.violet.music.core.ui

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackError
import dev.violet.music.feature.collection.CollectionDialogs
import dev.violet.music.feature.collection.CollectionDialogsState
import dev.violet.music.feature.collection.CollectionViewModel
import dev.violet.music.feature.library.LibraryViewModel
import dev.violet.music.feature.player.MiniPlayer
import dev.violet.music.feature.player.PlayerViewModel

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

private val destinations =
    listOf(
        Destination("home", R.string.home, VioletIcons.Home),
        Destination("search", R.string.search, VioletIcons.Search),
        Destination("library", R.string.library, VioletIcons.Library),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VioletApp(
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    collectionViewModel: CollectionViewModel,
    homeViewModel: dev.violet.music.feature.home.HomeViewModel,
    statsViewModel: dev.violet.music.feature.stats.StatsViewModel,
    selectionViewModel: dev.violet.music.feature.selection.SelectionViewModel,
    onPermission: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    val playback by playerViewModel.uiState.collectAsStateWithLifecycle()
    val collectionError by collectionViewModel.error.collectAsStateWithLifecycle()
    val selection by selectionViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.preferenceErrors.collect {
            snackbar.showSnackbar(context.getString(R.string.settings_error))
        }
    }
    LaunchedEffect(selection.running, selection.active, selection.processed, selection.skipped) {
        if (
            !selection.running && !selection.active && selection.processed + selection.skipped > 0
        ) {
            snackbar.showSnackbar(
                context.getString(R.string.bulk_finished, selection.processed, selection.skipped)
            )
            selectionViewModel.dismissResult()
        }
    }
    LaunchedEffect(collectionError) {
        if (collectionError) {
            snackbar.showSnackbar(context.getString(R.string.collection_error))
            collectionViewModel.dismissError()
        }
    }
    LaunchedEffect(playback.error) {
        playback.error?.let { error ->
            val message =
                when (error) {
                    PlaybackError.CONNECTION -> R.string.connection_error
                    PlaybackError.PERMISSION -> R.string.play_permission_error
                    PlaybackError.UNAVAILABLE -> R.string.unavailable_error
                    PlaybackError.EMPTY_QUEUE -> R.string.empty_queue_error
                    PlaybackError.PLAYBACK -> R.string.playback_error
                }
            val result =
                snackbar.showSnackbar(
                    context.getString(message),
                    actionLabel =
                        if (error == PlaybackError.CONNECTION) context.getString(R.string.retry)
                        else null,
                )
            if (result == SnackbarResult.ActionPerformed) playerViewModel.controller.reconnect()
            playerViewModel.controller.dismissError()
        }
    }
    val navController = rememberNavController()
    val dialogs = remember { CollectionDialogsState() }
    CollectionDialogs(
        dialogs,
        collectionViewModel,
        playerViewModel,
        onArtist = {
            navController.navigate("artist/${android.net.Uri.encode(it)}") {
                launchSingleTop = true
            }
        },
        onAlbum = { key, title ->
            navController.navigate(
                "album/${android.net.Uri.encode(key)}/${android.net.Uri.encode(title)}"
            ) {
                launchSingleTop = true
            }
        },
        onCreated = { navController.navigate("playlist/$it") },
    )
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isCollection =
        route.startsWith("album/") ||
            route.startsWith("artist/") ||
            route.startsWith("playlist/") ||
            route.startsWith("collection/")
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (route != "now-playing")
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                    navigationIcon = {
                        if (
                            isCollection ||
                                route in listOf("settings", "now-playing", "queue", "stats")
                        )
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(VioletIcons.Back, stringResource(R.string.back))
                            }
                    },
                    actions = {
                        if (route != "settings")
                            IconButton(
                                onClick = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                }
                            ) {
                                Icon(VioletIcons.Settings, stringResource(R.string.settings))
                            }
                    },
                )
        },
        bottomBar = {
            if (route != "now-playing")
                Column {
                    if (route != "now-playing")
                        MiniPlayer(playback, playerViewModel.controller) {
                            navController.navigate("now-playing") { launchSingleTop = true }
                        }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected =
                                    route == destination.route ||
                                        (isCollection && destination.route == "library"),
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
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
            AppNavigation(
                navController,
                viewModel,
                playerViewModel,
                collectionViewModel,
                homeViewModel,
                statsViewModel,
                selectionViewModel,
                onPermission,
                onAndroidSettings,
                onTrackMenu = { track, filter, occurrence ->
                    dialogs.menuTrack = track
                    dialogs.menuFilter = filter
                    dialogs.menuOccurrence = occurrence
                    dialogs.menuPlaylist = null
                },
                onCreatePlaylist = { dialogs.creating = true },
                onPlaylistTrackMenu = { track, id, entry ->
                    dialogs.menuTrack = track
                    dialogs.menuFilter = null
                    dialogs.menuOccurrence = null
                    dialogs.menuPlaylist =
                        dev.violet.music.feature.collection.PlaylistMenuContext(id, entry)
                },
            )
        }
    }
}
