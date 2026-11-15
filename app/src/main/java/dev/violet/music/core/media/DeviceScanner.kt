package dev.violet.music.core.media

/** External OS identity, deliberately separate from the app's track UUID. */
data class DeviceAudio(
    val volume: String,
    val mediaId: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: String,
    val artworkUri: String?,
    val durationMs: Long,
    val dateAdded: Long,
    val lastModified: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val albumArtistName: String? = null,
)

fun interface DeviceScanner {
    /** Returns only after every mounted volume was read and access was rechecked. */
    suspend fun scan(onBatch: suspend (List<DeviceAudio>) -> Unit)
}

interface CatalogueWriter {
    suspend fun importBatch(records: List<DeviceAudio>, scanId: String)

    suspend fun completeScan(scanId: String, completedAt: Long)
}

enum class ScanFailure {
    PERMISSION,
    STORAGE_CHANGED,
    PROVIDER,
}

sealed interface ScanStatus {
    data object Idle : ScanStatus

    data class Running(val processed: Int = 0) : ScanStatus

    data class Complete(val processed: Int) : ScanStatus

    data class Failed(val reason: ScanFailure) : ScanStatus
}

class StorageChangedException : IllegalStateException("Storage changed during indexing")
