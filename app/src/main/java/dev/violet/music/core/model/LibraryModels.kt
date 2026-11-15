package dev.violet.music.core.model

data class Track(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val albumKey: String,
    val durationMs: Long,
    val artworkUri: String?,
    val availability: String,
    val dateAdded: Long,
)

data class AlbumSummary(
    val albumKey: String,
    val title: String,
    val artistName: String,
    val artworkUri: String?,
    val trackCount: Int,
)

data class ArtistSummary(val name: String, val trackCount: Int, val albumCount: Int)

data class LibraryStats(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val unavailableCount: Int = 0,
)

data class TrackFilter(
    val query: String = "",
    val albumKey: String? = null,
    val artistName: String? = null,
    val recentlyAddedFirst: Boolean = false,
)
