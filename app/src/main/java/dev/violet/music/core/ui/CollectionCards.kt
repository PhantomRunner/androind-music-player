package dev.violet.music.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.model.*

@Composable
fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
    }
}

@Composable
fun TrackCard(
    track: Track,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    Surface(
        modifier =
            Modifier.combinedClickable(
                onLongClick = onLongClick,
                onClick = { if (selecting || track.availability == "LOCAL") onPlay() },
            ),
        shape = MaterialTheme.shapes.large,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(
                track.artworkUri,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                revision = track.artworkVersion,
            )
            if (selecting) Checkbox(checked = selected, onCheckedChange = { onPlay() })
            Text(
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                track.artistName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (track.availability == "LOCAL") formatDuration(track.durationMs)
                    else stringResource(R.string.unavailable),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = onMenu) {
                    Icon(PlaybackIcons.More, stringResource(R.string.track_options, track.title))
                }
            }
        }
    }
}

@Composable
fun AlbumCard(album: AlbumSummary, onClick: () -> Unit) =
    ArtworkCard(album.title, album.artistName, album.artworkUri, onClick)

@Composable
fun ArtistCard(artist: ArtistSummary, onClick: () -> Unit) =
    ArtworkCard(artist.name, trackCountLabel(artist.trackCount), artist.artworkUri, onClick)

@Composable
fun PlaylistCard(playlist: PlaylistSummary, onClick: () -> Unit) =
    ArtworkCard(playlist.name, trackCountLabel(playlist.trackCount), playlist.coverUri(), onClick)

@Composable
fun ArtworkCard(title: String, subtitle: String, uri: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(uri, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
