package dev.violet.music.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.violet.music.core.model.PlaylistSummary
import java.io.File

@Composable
fun PlaylistSummary.coverUri(): String? {
    val context = LocalContext.current
    return coverFileName
        ?.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }
        ?.let { android.net.Uri.fromFile(File(context.filesDir, "playlist-covers/$it")).toString() }
        ?: artworkUri
}
