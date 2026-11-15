package dev.violet.music.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackError
import dev.violet.music.feature.collection.CollectionDialogs
import dev.violet.music.feature.collection.CollectionDialogsState
import dev.violet.music.feature.collection.CollectionViewModel
import dev.violet.music.feature.collection.PlaylistDuplicateDialog
import dev.violet.music.feature.library.LibraryViewModel
import dev.violet.music.feature.player.MiniPlayer
import dev.violet.music.feature.player.PlayerViewModel
import kotlinx.coroutines.launch

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
    val addition by collectionViewModel.addition.collectAsStateWithLifecycle()
    val selection by selectionViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(addition.completion) {
        val completion = addition.completion ?: return@LaunchedEffect
        completion.origin?.let { origin ->
            selectionViewModel.finishPlaylistAdd(origin.selectionContext)
            if (origin.pickerEntryId != null && navController.currentBackStackEntry?.id == origin.pickerEntryId)
                navController.popBackStack()
        }
        val result = completion.result
        val message = if (result.missing == 0) {
            context.getString(R.string.playlist_add_finished, result.added, result.duplicatesSkipped)
        } else {
            context.getString(
                R.string.playlist_add_finished_with_missing, result.added, result.duplicatesSkipped, result.missing,
            )
        }
        collectionViewModel.cancelAddition()
        scope.launch { snackbar.showSnackbar(message) }
    }
    if (addition.duplicateCount > 0) {
        PlaylistDuplicateDialog(
            addition.duplicateCount,
            onSkip = { collectionViewModel.resolveDuplicates(allow = false) },
            onAllow = { collectionViewModel.resolveDuplicates(allow = true) },
            onCancel = collectionViewModel::cancelAddition,
        )
    }
    val route = entry?.destination?.route ?: "home"
    val tab = mainTabs.firstOrNull { it.route == route }
    var savedTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(tab) { if (tab != null) savedTab = tab.route }
    val isPicker = route.endsWith("/add-tracks")
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (route != "now-playing")
                TopAppBar(
                    title = {
                        if (tab != null) {
                            Text(
                                stringResource(tab.label),
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                    navigationIcon = {
                        if (tab == null) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                enabled = !isPicker || !addition.pending,
                            ) {
                                Icon(VioletIcons.Back, stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (route == "library") {
                            IconButton(onClick = { dialogs.creating = true }) {
                                Icon(VioletIcons.Add, stringResource(R.string.new_playlist))
                            }
                        }
                        if (route == "home" || route == "library") {
                            IconButton(onClick = {
                                navController.navigate("stats") { launchSingleTop = true }
                            }) {
                                Icon(VioletIcons.Stats, stringResource(R.string.listening_stats))
                            }
                        }
                        if (tab != null) {
                            IconButton(
                                onClick = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                }
                            ) {
                                Icon(VioletIcons.Settings, stringResource(R.string.settings))
                            }
                        }
                    },
                )
        },
        bottomBar = {
            if (route != "now-playing" && !keyboardVisible && !isPicker) {
                Column {
                    MiniPlayer(playback, playerViewModel.controller) {
                        navController.navigate("now-playing") { launchSingleTop = true }
                    }
                    VioletNavigationBar(tab?.route ?: savedTab) { target ->
                        if (target != route) navController.openMainTab(target)
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
