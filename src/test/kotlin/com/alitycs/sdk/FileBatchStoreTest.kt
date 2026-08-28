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
}
