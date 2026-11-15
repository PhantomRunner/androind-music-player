package dev.violet.music.feature.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import dev.violet.music.R
import dev.violet.music.core.model.PlaylistSummary
import dev.violet.music.core.ui.Artwork

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PlaylistCoverEditor(
    playlist: PlaylistSummary,
    onApply: (android.net.Uri) -> Unit,
    onRemove: () -> Unit,
    busy: Boolean,
) {
    var pending by rememberSaveable(playlist.id) { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pending = uri?.toString()
        }
    FlowRow {
        TextButton(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.choose_cover))
        }
        if (playlist.coverFileName != null)
            TextButton(onClick = onRemove, enabled = !busy) {
                Text(stringResource(R.string.remove_cover))
            }
    }
    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(stringResource(R.string.cover_preview)) },
            text = { Artwork(uri, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onApply(uri.toUri())
                        pending = null
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.apply_cover))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
