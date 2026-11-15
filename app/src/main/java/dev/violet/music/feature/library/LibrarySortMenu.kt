package dev.violet.music.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import dev.violet.music.R
import dev.violet.music.core.model.*
import dev.violet.music.core.ui.PlaybackIcons

fun TrackSort.label() =
    when (this) {
        TrackSort.TITLE_ASC -> R.string.title_asc
        TrackSort.TITLE_DESC -> R.string.title_desc
        TrackSort.NEWEST -> R.string.recently_added
        TrackSort.OLDEST -> R.string.oldest_added
        TrackSort.MOST_PLAYED -> R.string.most_played
        TrackSort.LEAST_PLAYED -> R.string.least_played
        TrackSort.RECENTLY_PLAYED -> R.string.recently_played
    }

@Composable
fun LibrarySortMenu(
    category: LibraryCategory,
    preferences: LibraryPreferences,
    onSort: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (category == LibraryCategory.ALL) return
    val current =
        if (category == LibraryCategory.TRACKS) preferences.trackSort().label()
        else preferences.summarySort(category).label(category)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.sort_by_value, stringResource(current)))
        }
        DropdownMenu(expanded, { expanded = false }) {
            if (category == LibraryCategory.TRACKS)
                TrackSort.entries
                    .filter { it != TrackSort.RECENTLY_PLAYED }
                    .forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.label())) },
                            onClick = {
                                onSort(sort.name)
                                expanded = false
                            },
                            leadingIcon = {
                                if (preferences.trackSort() == sort) Icon(PlaybackIcons.Play, null)
                            },
                        )
                    }
            else
                SummarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.label(category))) },
                        onClick = {
                            onSort(sort.name)
                            expanded = false
                        },
                        leadingIcon = {
                            if (preferences.summarySort(category) == sort)
                                Icon(PlaybackIcons.Play, null)
                        },
                    )
                }
        }
    }
}

private fun SummarySort.label(category: LibraryCategory) =
    when (this) {
        SummarySort.NAME_ASC -> R.string.name_asc
        SummarySort.NAME_DESC -> R.string.name_desc
        SummarySort.NEWEST ->
            if (category == LibraryCategory.PLAYLISTS) R.string.recently_updated
            else R.string.recently_added
        SummarySort.OLDEST ->
            if (category == LibraryCategory.PLAYLISTS) R.string.oldest_created
            else R.string.oldest_added
    }
