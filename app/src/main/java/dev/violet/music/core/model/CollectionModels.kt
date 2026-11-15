package dev.violet.music.core.model

data class PlaylistSummary(
    val id: String,
    val name: String,
    val trackCount: Int,
    val artworkUri: String?,
    val coverFileName: String? = null,
)

data class PlaylistEntry(val id: String, val position: Int, val track: Track)

data class PlaylistQueueItem(val entryId: String, val trackId: String)

data class PlaylistAddItem(val key: String, val trackId: String)

sealed interface PlaylistDuplicatePolicy {
    data object Check : PlaylistDuplicatePolicy

    data object Skip : PlaylistDuplicatePolicy

    data class Allow(val keys: Set<String>) : PlaylistDuplicatePolicy
}

sealed interface PlaylistAddResult {
    data object MissingPlaylist : PlaylistAddResult

    data class NeedsConfirmation(val keys: Set<String>) : PlaylistAddResult

    data class Completed(val added: Int, val duplicatesSkipped: Int, val missing: Int) :
        PlaylistAddResult
}

internal fun validatedPlaylistName(name: String): String =
    name.trim().also {
        require(it.isNotEmpty() && it.length <= 120) { "Playlist name must be 1–120 characters" }
    }
