package dev.violet.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dev.violet.music.R
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.pagedTracks
import kotlinx.coroutines.flow.Flow

@Composable
fun CollectionScreen(
    title: String,
    state: LibraryUiState,
    tracks: Flow<PagingData<Track>>,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onMenu: (Track) -> Unit,
) {
    val items = tracks.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("title") { SectionTitle(title) }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        if (items.itemCount > 0 && state.hasPermission) item("play-actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onPlayAll(false) }) { Text(stringResource(R.string.play_all)) }
                OutlinedButton(onClick = { onPlayAll(true) }) { Text(stringResource(R.string.shuffle)) }
            }
        }
        pagedTracks(items, onMenu = onMenu, onPlay = onPlay)
    }
}
