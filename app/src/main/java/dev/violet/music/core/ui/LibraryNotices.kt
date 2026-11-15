package dev.violet.music.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.media.ScanFailure
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.feature.library.LibraryUiState

fun LazyListScope.libraryNotices(
    state: LibraryUiState,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (!state.hasPermission) item("permission") { PermissionCard(onPermission, onSettings) }
    when (val scan = state.scanStatus) {
        is ScanStatus.Running -> item("scan") {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scanning, trackCountLabel(scan.processed)), color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is ScanStatus.Failed -> item("scan") {
            val description = when (scan.reason) {
                ScanFailure.PERMISSION -> R.string.scan_permission_error
                ScanFailure.STORAGE_CHANGED -> R.string.scan_storage_error
                ScanFailure.PROVIDER -> R.string.scan_error
            }
            MessageCard(
                stringResource(R.string.indexing_title), stringResource(description),
                if (state.hasPermission) stringResource(R.string.retry) else null, onRefresh,
            )
        }
        else -> Unit
    }
    if (state.catalogueError) item("catalogue-error") {
        MessageCard(stringResource(R.string.library), stringResource(R.string.load_error))
    }
}

@Composable
fun PermissionCard(onPermission: () -> Unit, onSettings: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.permission_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.grant_access))
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.android_settings))
            }
        }
    }
}
