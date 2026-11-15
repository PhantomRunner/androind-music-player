package dev.violet.music.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryIndexerTest {
    @Test
    fun `successful scan commits completeness after all batches`() = runTest {
        val writer = RecordingWriter()
        val indexer = LibraryIndexer(DeviceScanner { batch ->
            batch(listOf(audio(1)))
            assertEquals(0, writer.completions)
            batch(listOf(audio(2)))
        }, writer)

        indexer.refresh()

        assertEquals(listOf(1L, 2L), writer.mediaIds)
        assertEquals(1, writer.completions)
        assertEquals(ScanStatus.Complete(2), indexer.status.value)
        assertTrue(writer.completedAt > 0)
        assertEquals(1, writer.scanIds.distinct().size)
    }

    @Test
    fun `partial provider failure never reconciles missing entries`() = runTest {
        val writer = RecordingWriter()
        val indexer = LibraryIndexer(DeviceScanner { batch ->
            batch(listOf(audio(1)))
            throw IllegalStateException("Provider stopped")
        }, writer)

        indexer.refresh()

        assertEquals(listOf(1L), writer.mediaIds)
        assertEquals(0, writer.completions)
        assertEquals(ScanStatus.Failed(ScanFailure.PROVIDER), indexer.status.value)
    }

    @Test
    fun `denied access and changing volumes do not finalize a scan`() = runTest {
        for ((failure, reason) in listOf(
            SecurityException() to ScanFailure.PERMISSION,
            StorageChangedException() to ScanFailure.STORAGE_CHANGED,
        )) {
            val writer = RecordingWriter()
            val indexer = LibraryIndexer(DeviceScanner { throw failure }, writer)
            indexer.refresh()
            assertTrue(writer.mediaIds.isEmpty())
            assertEquals(0, writer.completions)
            assertEquals(ScanStatus.Failed(reason), indexer.status.value)
        }
    }

    @Test
    fun `failed database batch never finalizes scan`() = runTest {
        var finalized = false
        val writer = object : CatalogueWriter {
            override suspend fun importBatch(records: List<DeviceAudio>, scanId: String) {
                throw IllegalStateException("Database unavailable")
            }
            override suspend fun completeScan(scanId: String, completedAt: Long) { finalized = true }
        }
        val indexer = LibraryIndexer(DeviceScanner { it(listOf(audio(1))) }, writer)

        indexer.refresh()

        assertFalse(finalized)
        assertEquals(ScanStatus.Failed(ScanFailure.PROVIDER), indexer.status.value)
    }

    @Test
    fun `overlapping refresh is ignored and next refresh uses a new marker`() = runTest {
        val release = CompletableDeferred<Unit>()
        val writer = RecordingWriter()
        var calls = 0
        val indexer = LibraryIndexer(DeviceScanner { batch ->
            calls++
            release.await()
            batch(listOf(audio(1)))
        }, writer)
        val first = launch { indexer.refresh() }
        runCurrent()

        indexer.refresh()
        assertEquals(1, calls)
        release.complete(Unit)
        first.join()
        indexer.refresh()

        assertEquals(2, calls)
        assertEquals(2, writer.completions)
        assertNotEquals(writer.scanIds[0], writer.scanIds[1])
    }

    @Test
    fun `cancellation preserves catalogue and unlocks next refresh`() = runTest {
        var cancel = true
        val writer = RecordingWriter()
        val indexer = LibraryIndexer(DeviceScanner { batch ->
            if (cancel) throw CancellationException()
            batch(listOf(audio(1)))
        }, writer)

        val cancelled = launch { indexer.refresh() }
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        assertEquals(0, writer.completions)
        assertEquals(ScanStatus.Idle, indexer.status.value)

        cancel = false
        indexer.refresh()
        assertEquals(1, writer.completions)
    }

    @Test
    fun `empty completed scan is distinguishable from failed query`() = runTest {
        val writer = RecordingWriter()
        val indexer = LibraryIndexer(DeviceScanner { }, writer)
        indexer.refresh()
        assertEquals(1, writer.completions)
        assertEquals(ScanStatus.Complete(0), indexer.status.value)
    }

    private class RecordingWriter : CatalogueWriter {
        val mediaIds = mutableListOf<Long>()
        val scanIds = mutableListOf<String>()
        var completions = 0
        var completedAt = 0L
        override suspend fun importBatch(records: List<DeviceAudio>, scanId: String) {
            mediaIds += records.map { it.mediaId }
            scanIds += scanId
        }
        override suspend fun completeScan(scanId: String, completedAt: Long) {
            completions++
            this.completedAt = completedAt
        }
    }

    private fun audio(id: Long) = DeviceAudio(
        "external_primary", id, "content://media/external_primary/audio/media/$id",
        "Test fixture $id", "Fixture artist", "Fixture album", "external_primary:1",
        null, 60_000, 1, 1, 1_024, "audio/mpeg",
    )
}
