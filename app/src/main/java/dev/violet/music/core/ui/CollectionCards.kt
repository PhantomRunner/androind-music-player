package dev.violet.music.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
fun CollectionShortcut(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(modifier = modifier, onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = shape) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(shape).background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                    )
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
fun ArtworkCard(
    title: String,
    subtitle: String,
    uri: String?,
    onClick: () -> Unit,
    revision: Long = 0,
    onMenu: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(8.dp)) {
            Artwork(uri, modifier = Modifier.fillMaxWidth().aspectRatio(1f), revision = revision)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 2.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onMenu != null) {
                    IconButton(onClick = onMenu) {
                        Icon(PlaybackIcons.More, stringResource(R.string.track_options, title))
                    }
                }
            }
        }
    }
}
