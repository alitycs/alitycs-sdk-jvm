package com.alitycs.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@Serializable
internal data class DurableBatchRecord(
    val batchId: String,
    val body: String,
    val eventCount: Int,
    val pausedUntilMs: Long? = null,
)

@Serializable
private data class DurableBatchState(
    val version: Int = 1,
    val batches: List<DurableBatchRecord> = emptyList(),
)

/** Atomic file-backed WAL for serialized batches awaiting a terminal outcome. */
internal class FileBatchStore(
    pathValue: String?,
    private val maxPendingEvents: Int = DEFAULT_MAX_PENDING_EVENTS,
) {
    private val path: Path? = pathValue?.let(Path::of)
    private val json = Json { ignoreUnknownKeys = true }
    private val records = linkedMapOf<String, DurableBatchRecord>()
    private val startupEvicted = mutableListOf<DurableBatchRecord>()

    init {
        require(maxPendingEvents > 0) { "maxPendingEvents must be positive" }
        val durablePath = path
        if (durablePath != null && Files.exists(durablePath)) {
            try {
                val state = json.decodeFromString<DurableBatchState>(Files.readString(durablePath))
                require(state.version == 1) { "unsupported persistence version ${state.version}" }
                state.batches.forEach { records[it.batchId] = it }
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "Invalid Alitycs persistence file at $durablePath",
                    error,
                )
            }
            startupEvicted.addAll(evictOverflow())
            if (startupEvicted.isNotEmpty()) write()
        }
    }

    val enabled: Boolean get() = path != null

    @Synchronized
    fun put(record: DurableBatchRecord): List<DurableBatchRecord> {
        if (path == null) return emptyList()
        val previous = LinkedHashMap(records)
        return try {
            records[record.batchId] = record
            val evicted = evictOverflow()
            write()
            evicted
        } catch (error: Exception) {
            restore(previous)
            throw error
        }
    }

    @Synchronized
    fun acknowledge(batchId: String) {
        if (path == null || !records.containsKey(batchId)) return
        val previous = LinkedHashMap(records)
        try {
            records.remove(batchId)
            write()
        } catch (error: Exception) {
            restore(previous)
            throw error
        }
    }

    @Synchronized
    fun pause(batchId: String, pausedUntilMs: Long?) {
        if (path == null) return
        val current = records[batchId] ?: return
        val previous = LinkedHashMap(records)
        try {
            records[batchId] = current.copy(pausedUntilMs = pausedUntilMs)
            write()
        } catch (error: Exception) {
            restore(previous)
            throw error
        }
    }

    @Synchronized
    fun snapshot(): List<DurableBatchRecord> = records.values.toList()

    @Synchronized
    fun pendingEvents(): Int = records.values.sumOf { it.eventCount }

    @Synchronized
    fun contains(batchId: String): Boolean = records.containsKey(batchId)

    @Synchronized
    fun startupEvictions(): List<DurableBatchRecord> = startupEvicted.toList()

    private fun evictOverflow(): List<DurableBatchRecord> {
        val evicted = mutableListOf<DurableBatchRecord>()
        var pending = records.values.sumOf { it.eventCount }
        val iterator = records.entries.iterator()
        while (pending > maxPendingEvents && iterator.hasNext()) {
            val record = iterator.next().value
            iterator.remove()
            pending -= record.eventCount
            evicted.add(record)
        }
        return evicted
    }

    private fun restore(previous: LinkedHashMap<String, DurableBatchRecord>) {
        records.clear()
        records.putAll(previous)
    }

    private fun write() {
        val durablePath = path ?: return
        if (records.isEmpty()) {
            Files.deleteIfExists(durablePath)
            forceParentDirectory(durablePath)
            return
        }
        durablePath.parent?.let(Files::createDirectories)
        val temporary = durablePath.resolveSibling(
            ".${durablePath.fileName}.tmp-${ProcessHandle.current().pid()}-${Thread.currentThread().id}"
        )
        try {
            val bytes = json.encodeToString(
                DurableBatchState.serializer(),
                DurableBatchState(batches = records.values.toList()),
            ).toByteArray(Charsets.UTF_8)
            FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(
                    temporary,
                    durablePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, durablePath, StandardCopyOption.REPLACE_EXISTING)
            }
            forceParentDirectory(durablePath)
        } catch (error: Exception) {
            Files.deleteIfExists(temporary)
            throw error
        }
    }

    private fun forceParentDirectory(durablePath: Path) {
        val parent = durablePath.parent ?: return
        try {
            FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // Some filesystems do not allow opening a directory as a channel. The data file
            // itself was still forced before its atomic replacement.
        }
    }

    companion object {
        const val DEFAULT_MAX_PENDING_EVENTS = 1000
    }
}
