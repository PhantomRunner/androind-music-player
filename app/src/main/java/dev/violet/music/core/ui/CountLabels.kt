package dev.violet.music.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.violet.music.R
import dev.violet.music.core.model.LibraryStats

@Composable
fun trackCountLabel(count: Int): String = pluralStringResource(R.plurals.track_count, count, count)

@Composable
fun collectionCountLabel(stats: LibraryStats): String = stringResource(
    R.string.collection_counts,
    trackCountLabel(stats.trackCount),
    pluralStringResource(R.plurals.album_count, stats.albumCount, stats.albumCount),
    pluralStringResource(R.plurals.artist_count, stats.artistCount, stats.artistCount),
)
