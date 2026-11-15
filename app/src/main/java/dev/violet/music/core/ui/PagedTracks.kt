package dev.violet.music.core.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import dev.violet.music.R
import dev.violet.music.core.model.Track

fun LazyListScope.pagedTracks(
    tracks: LazyPagingItems<Track>,
    emptySearch: Boolean = false,
    emptyFavourites: Boolean = false,
    onMenu: ((Track) -> Unit)? = null,
    selected: Set<String> = emptySet(),
    selecting: Boolean = false,
    rowEnabled: Boolean = true,
    onToggle: ((Track) -> Unit)? = null,
    onPlay: (Track) -> Unit,
) {
    if (tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0) {
        item("tracks-loading") { LoadingState() }
    } else if (tracks.loadState.refresh is LoadState.Error) {
        item("tracks-error") {
            MessageCard(
                stringResource(R.string.library),
                stringResource(R.string.load_error),
                stringResource(R.string.retry),
                tracks::retry,
            )
        }
    } else if (tracks.itemCount == 0) {
        item("tracks-empty") {
            MessageCard(
                stringResource(
                    when {
                        emptySearch -> R.string.search_empty
                        emptyFavourites -> R.string.empty_favourites_title
                        else -> R.string.empty_title
                    }
                ),
                stringResource(
                    when {
                        emptySearch -> R.string.search_empty_description
                        emptyFavourites -> R.string.empty_favourites_description
                        else -> R.string.empty_description
                    }
                ),
            )
        }
    }
    items(count = tracks.itemCount, key = tracks.itemKey { "track:${it.id}" }) { index ->
        tracks[index]?.let { track ->
            TrackRow(
                track,
                onMenu = onMenu?.let { action -> { action(track) } },
                onLongClick = onToggle?.let { action -> { action(track) } },
                selected = track.id in selected,
                selecting = selecting,
                enabled = rowEnabled,
                onPlay = { if (selecting) onToggle?.invoke(track) else onPlay(track) },
            )
        }
    }
    when (tracks.loadState.append) {
        is LoadState.Loading -> item("append-loading") { LoadingState() }
        is LoadState.Error ->
            item("append-error") {
                MessageCard(
                    stringResource(R.string.library),
                    stringResource(R.string.load_error),
                    stringResource(R.string.retry),
                    tracks::retry,
                )
            }
        else -> Unit
    }
}
