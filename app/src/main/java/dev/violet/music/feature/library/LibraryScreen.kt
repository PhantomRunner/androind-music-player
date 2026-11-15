package dev.violet.music.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.AlbumRow
import dev.violet.music.core.ui.ArtistRow
import dev.violet.music.core.ui.LoadingState
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.pagedTracks
import dev.violet.music.core.ui.collectionCountLabel
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    tracks: Flow<PagingData<Track>>,
    albums: Flow<PagingData<AlbumSummary>>,
    artists: Flow<PagingData<ArtistSummary>>,
    onAlbum: (AlbumSummary) -> Unit,
    onArtist: (ArtistSummary) -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val trackItems = tracks.collectAsLazyPagingItems()
    val albumItems = albums.collectAsLazyPagingItems()
    val artistItems = artists.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("title") { SectionTitle(stringResource(R.string.library)) }
        item("counts") {
            Text(collectionCountLabel(state.stats))
        }
        item("filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.tracks, R.string.albums, R.string.artists).forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index, onClick = { tab = index },
                        label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        when (tab) {
            0 -> pagedTracks(trackItems)
            1 -> {
                summaryStatus(albumItems)
                items(albumItems.itemCount, key = albumItems.itemKey { "album:${it.albumKey}" }) { index ->
                    albumItems[index]?.let { AlbumRow(it) { onAlbum(it) } }
                }
            }
            2 -> {
                summaryStatus(artistItems)
                items(artistItems.itemCount, key = artistItems.itemKey { "artist:${it.name}" }) { index ->
                    artistItems[index]?.let { ArtistRow(it) { onArtist(it) } }
                }
            }
        }
    }
}

private fun <T : Any> LazyListScope.summaryStatus(items: LazyPagingItems<T>) {
    when {
        items.loadState.refresh is LoadState.Loading -> item { LoadingState() }
        items.loadState.refresh is LoadState.Error || items.loadState.append is LoadState.Error -> item {
            MessageCard(
                stringResource(R.string.library), stringResource(R.string.load_error),
                stringResource(R.string.retry), items::retry,
            )
        }
        items.itemCount == 0 -> item {
            MessageCard(stringResource(R.string.empty_title), stringResource(R.string.empty_description))
        }
    }
}
