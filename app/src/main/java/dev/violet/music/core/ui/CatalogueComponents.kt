package dev.violet.music.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.violet.music.R
import dev.violet.music.core.model.AlbumSummary
import dev.violet.music.core.model.ArtistSummary
import dev.violet.music.core.model.Track
import java.util.Locale

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 12.dp).semantics { heading() },
    )
}

@Composable
fun Artwork(uri: String?, size: Dp? = null, modifier: Modifier = Modifier, revision: Long = 0) {
    val actualSize = size ?: 56.dp
    Box(
        (if (modifier == Modifier) Modifier.size(actualSize) else modifier)
            .clip(RoundedCornerShape(dev.violet.music.core.ui.theme.VioletLayout.artworkRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            VioletIcons.Music,
            contentDescription = null,
            modifier = Modifier.size(actualSize * 0.4f),
            tint = MaterialTheme.colorScheme.primary,
        )
        if (uri != null)
            AsyncImage(
                model =
                    coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        )
                        .data(uri)
                        .memoryCacheKey("$uri:$revision")
                        .diskCacheKey("$uri:$revision")
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
fun TrackRow(
    track: Track,
    onMenu: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
    enabled: Boolean = true,
    onPlay: (() -> Unit)? = null,
) {
    val playModifier =
        when {
            selecting && onPlay != null -> Modifier.toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onPlay() },
            )
            onPlay != null || onLongClick != null -> Modifier.combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.play),
                onLongClickLabel = stringResource(R.string.select_track, track.title),
                onLongClick = onLongClick,
                onClick = { if (track.availability == "LOCAL") onPlay?.invoke() },
            )
            else -> Modifier
        }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp).then(playModifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selecting)
            androidx.compose.material3.Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.size(48.dp),
                enabled = enabled,
            )
        Artwork(track.artworkUri, revision = track.artworkVersion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.isFavourite)
                Icon(
                    PlaybackIcons.Heart,
                    stringResource(R.string.favourites),
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            Text(
                "${track.artistName} · ${track.albumTitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.availability == "UNAVAILABLE")
                Text(
                    stringResource(R.string.unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onMenu != null)
            IconButton(onClick = onMenu) {
                Icon(PlaybackIcons.More, stringResource(R.string.track_options, track.title))
            }
    }
}

@Composable
fun AlbumRow(album: AlbumSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(album.artworkUri)
            Column(Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.titleMedium)
                Text(album.artistName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    trackCountLabel(album.trackCount),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun ArtistRow(artist: ArtistSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(artist.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(artist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.artist_counts,
                        trackCountLabel(artist.trackCount),
                        pluralStringResource(
                            R.plurals.album_count,
                            artist.albumCount,
                            artist.albumCount,
                        ),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MessageCard(
    title: String,
    description: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null)
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(action)
                }
        }
    }
}

@Composable
fun LoadingState() {
    Row(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(stringResource(R.string.loading))
    }
}

internal fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return if (seconds >= 3_600)
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
