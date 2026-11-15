package dev.violet.music.core.model

data class SelectedTrack(val key: String, val trackId: String, val available: Boolean)

data class SelectionSource(
    val context: String,
    val filter: TrackFilter? = null,
    val playlistId: String? = null,
)

enum class BulkAction {
    ADD_QUEUE,
    ADD_FAVOURITES,
    REMOVE_FAVOURITES,
    REMOVE_PLAYLIST,
}

data class BulkResult(val updated: Int = 0, val skipped: Int = 0)
