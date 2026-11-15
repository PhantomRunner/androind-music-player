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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onPermission: () -> Unit,
    onAndroidSettings: () -> Unit,
    miniPlayerSlot: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    if (isCollection || route == "settings") IconButton(onClick = { navController.popBackStack() }) {
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
                // Intentionally empty until a real shared Media3 session exists.
                miniPlayerSlot()
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        state, { navController.navigate("library") }, { navController.navigate("search") },
                        onPermission, onAndroidSettings, viewModel::refresh,
                    )
                }
                composable("search") {
                    SearchScreen(state, viewModel.searchResults, viewModel::updateQuery, onPermission, onAndroidSettings, viewModel::refresh)
                }
                composable("library") {
                    LibraryScreen(
                        state, viewModel.tracks, viewModel.albums, viewModel.artists,
                        onAlbum = { navController.navigate("album/${Uri.encode(it.albumKey)}/${Uri.encode(it.title)}") },
                        onArtist = { navController.navigate("artist/${Uri.encode(it.name)}") },
                        onPermission, onAndroidSettings, viewModel::refresh,
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
                    CollectionScreen(title, state, tracks, onPermission, onAndroidSettings, viewModel::refresh)
                }
                composable("artist/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { backStackEntry ->
                    val name = requireNotNull(backStackEntry.arguments?.getString("name"))
                    val tracks = remember(name) { viewModel.artistTracks(name) }
                    CollectionScreen(name, state, tracks, onPermission, onAndroidSettings, viewModel::refresh)
                }
            }
        }
    }
}
