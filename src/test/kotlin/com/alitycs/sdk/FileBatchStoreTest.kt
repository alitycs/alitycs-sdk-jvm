package com.alitycs.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

class FileBatchStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `persists reloads pauses and acknowledges exact records`() {
        val path = directory.resolve("nested/wal.json")
        val store = FileBatchStore(path.toString())
        val record = DurableBatchRecord("batch_exact", "{\"batchId\":\"batch_exact\"}", 2)

        store.put(record)
        store.pause(record.batchId, 123456L)
        assertEquals(2, store.pendingEvents())
        assertTrue(Files.exists(path))

        val restarted = FileBatchStore(path.toString())
        assertEquals(record.body, restarted.snapshot().single().body)
        assertEquals(123456L, restarted.snapshot().single().pausedUntilMs)

        restarted.acknowledge(record.batchId)
        assertEquals(0, restarted.pendingEvents())
        assertFalse(Files.exists(path))
    }

    @Test
    fun `disabled store is a no-op`() {
        val store = FileBatchStore(null)
        store.put(DurableBatchRecord("batch", "{}", 1))
        store.pause("batch", 1L)
        store.acknowledge("batch")
        assertFalse(store.enabled)
        assertTrue(store.snapshot().isEmpty())
        assertEquals(0, store.pendingEvents())
    }

    @Test
    fun `corrupt state fails initialization`() {
        val path = directory.resolve("wal.json")
        Files.writeString(path, "not-json")
        assertThrows<IllegalArgumentException> { FileBatchStore(path.toString()) }
    }

    @Test
    fun `durable event bound evicts the oldest complete batches`() {
        val path = directory.resolve("bounded-wal.json")
        val store = FileBatchStore(path.toString(), maxPendingEvents = 3)
        val oldest = DurableBatchRecord("batch_oldest", "{\"batchId\":\"batch_oldest\"}", 2)
        val newest = DurableBatchRecord("batch_newest", "{\"batchId\":\"batch_newest\"}", 2)

        assertTrue(store.put(oldest).isEmpty())
        assertEquals(listOf(oldest), store.put(newest))
        assertEquals(listOf(newest), store.snapshot())
        assertEquals(2, store.pendingEvents())

        val restarted = FileBatchStore(path.toString(), maxPendingEvents = 3)
        assertEquals(listOf("batch_newest"), restarted.snapshot().map { it.batchId })
    }
}
