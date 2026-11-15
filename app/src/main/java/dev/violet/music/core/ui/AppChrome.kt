package dev.violet.music.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import dev.violet.music.R

internal data class MainTab(val route: String, val label: Int, val icon: ImageVector)

internal val mainTabs = listOf(
    MainTab("home", R.string.home, VioletIcons.Home),
    MainTab("search", R.string.search, VioletIcons.Search),
    MainTab("library", R.string.library, VioletIcons.Library),
)

internal fun NavHostController.openMainTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
internal fun VioletNavigationBar(active: String, onTab: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in mainTabs) {
                val selected = tab.route == active
                val labelColor =
                    if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f)
                        .heightIn(min = 60.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab.route) })
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.background(
                            color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            shape = MaterialTheme.shapes.large,
                        ).padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(tab.icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else labelColor)
                    }
                    Text(
                        stringResource(tab.label),
                        color = labelColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
