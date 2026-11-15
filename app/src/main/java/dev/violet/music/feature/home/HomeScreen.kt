package dev.violet.music.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.ui.LoadingState
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.TrackRow
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.collectionCountLabel
import dev.violet.music.feature.library.LibraryUiState
import dev.violet.music.core.model.Track

@Composable
fun HomeScreen(
    state: LibraryUiState,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("hero") {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.surface)),
                    RoundedCornerShape(28.dp),
                ).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.offline_collection), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.home_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onLibrary) { Text(stringResource(R.string.browse_library)) }
            }
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        if (state.loading) item { LoadingState() }
        if (!state.loading && !state.catalogueError) {
            item("counts") {
                Text(
                    collectionCountLabel(state.stats),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item("recent-heading") { SectionTitle(stringResource(R.string.recently_added)) }
            if (state.recentTracks.isEmpty()) item("empty") {
                MessageCard(
                    stringResource(R.string.empty_title), stringResource(R.string.empty_description),
                    if (state.hasPermission) stringResource(R.string.scan_library) else null, onRefresh,
                )
            }
            items(state.recentTracks, key = { it.id }) { track -> TrackRow(track) { onPlay(track) } }
            item("search-shortcut") {
                TextButton(onClick = onSearch) { Text(stringResource(R.string.find_something)) }
            }
        }
    }
}
