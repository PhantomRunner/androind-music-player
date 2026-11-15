package dev.violet.music.feature.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.violet.music.R
import dev.violet.music.core.model.ArtistCredit
import dev.violet.music.core.model.ArtistRole
import dev.violet.music.core.ui.Artwork
import dev.violet.music.core.ui.formatDuration

@Composable
fun ArtistChoiceDialog(
    credits: List<ArtistCredit>,
    onDismiss: () -> Unit,
    onArtist: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_artist)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                credits
                    .distinctBy { it.id }
                    .forEach { artist ->
                        TextButton(
                            onClick = { onArtist(artist.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(artist.name)
                        }
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TrackInformationDialog(id: String, viewModel: CollectionViewModel, onDismiss: () -> Unit) {
    val track by remember(id) { viewModel.track(id) }.collectAsStateWithLifecycle(null)
    val info by remember(id) { viewModel.information(id) }.collectAsStateWithLifecycle(null)
    val credits by remember(id) { viewModel.credits(id) }.collectAsStateWithLifecycle(emptyList())
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    var role by rememberSaveable(id) { mutableStateOf(ArtistRole.TRACK.name) }
    var names by
        rememberSaveable(id, role, editing) {
            mutableStateOf(credits.filter { it.role == role }.joinToString("\n") { it.name })
        }
    if (editing)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.edit_artists)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow {
                        ArtistRole.entries.forEach { value ->
                            FilterChip(
                                role == value.name,
                                { role = value.name },
                                label = {
                                    Text(
                                        stringResource(
                                            if (value == ArtistRole.TRACK) R.string.track_artists
                                            else R.string.album_artists
                                        )
                                    )
                                },
                            )
                        }
                    }
                    Text(stringResource(R.string.artist_correction_hint))
                    OutlinedTextField(
                        value = names,
                        onValueChange = { names = it },
                        minLines = 3,
                        maxLines = 8,
                    )
                    TextButton(
                        onClick = {
                            viewModel.resetCredits(id, ArtistRole.valueOf(role))
                            editing = false
                        }
                    ) {
                        Text(stringResource(R.string.reset_credits))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.correct(id, ArtistRole.valueOf(role), names.lines())
                        editing = false
                    },
                    enabled =
                        names.lines().map(String::trim).filter(String::isNotEmpty).let {
                            it.size in 1..30 && it.all { name -> name.length <= 200 }
                        },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    else
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.track_information)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    track?.let { value ->
                        Artwork(value.artworkUri, 96.dp)
                        Text(value.title, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.original_credit, value.artistName))
                        Text(value.albumTitle)
                        Text(
                            stringResource(
                                R.string.duration_label,
                                formatDuration(value.durationMs),
                            )
                        )
                        Text(
                            stringResource(
                                if (value.availability == "LOCAL") R.string.on_device_track
                                else R.string.unavailable
                            )
                        )
                    }
                    info?.mimeType?.let { Text(stringResource(R.string.music_format, it)) }
                    credits.forEach { credit ->
                        Text(
                            "${credit.name} · ${stringResource(if(credit.role=="TRACK") R.string.track_artists else R.string.album_artists)} · ${stringResource(when(credit.source){"MANUAL"->R.string.credit_manual
"INFERRED"->R.string.credit_inferred
else->R.string.credit_metadata})}"
                        )
                    }
                    TextButton(onClick = { editing = true }) {
                        Text(stringResource(R.string.edit_artists))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
            },
        )
}
