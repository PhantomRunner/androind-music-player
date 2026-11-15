package dev.violet.music.core.media

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

@Singleton
class LibraryIndexer @Inject constructor(
    private val scanner: DeviceScanner,
    private val writer: CatalogueWriter,
) {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val status = mutableStatus.asStateFlow()

    suspend fun refresh() {
        // A second refresh never queues another full scan behind the active one.
        if (!mutex.tryLock()) return
        var processed = 0
        mutableStatus.value = ScanStatus.Running()
        try {
            val scanId = UUID.randomUUID().toString()
            scanner.scan { batch ->
                writer.importBatch(batch, scanId)
                processed += batch.size
                mutableStatus.value = ScanStatus.Running(processed)
            }
            writer.completeScan(scanId, System.currentTimeMillis())
            mutableStatus.value = ScanStatus.Complete(processed)
        } catch (cancelled: CancellationException) {
            mutableStatus.value = ScanStatus.Idle
            throw cancelled
        } catch (_: SecurityException) {
            mutableStatus.value = ScanStatus.Failed(ScanFailure.PERMISSION)
        } catch (_: StorageChangedException) {
            mutableStatus.value = ScanStatus.Failed(ScanFailure.STORAGE_CHANGED)
        } catch (_: Exception) {
            // Do not expose provider messages containing user URIs or file names.
            mutableStatus.value = ScanStatus.Failed(ScanFailure.PROVIDER)
        } finally {
            mutex.unlock()
        }
    }
}
