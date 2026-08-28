package com.alitycs.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
internal class FileBatchStore(pathValue: String?) {
    private val path: Path? = pathValue?.let(Path::of)
    private val json = Json { ignoreUnknownKeys = true }
    private val records = linkedMapOf<String, DurableBatchRecord>()

    init {
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
        }
    }

    val enabled: Boolean get() = path != null

    @Synchronized
    fun put(record: DurableBatchRecord) {
        if (path == null) return
        records[record.batchId] = record
        write()
    }

    @Synchronized
    fun acknowledge(batchId: String) {
        if (path == null || records.remove(batchId) == null) return
        write()
    }

    @Synchronized
    fun pause(batchId: String, pausedUntilMs: Long?) {
        if (path == null) return
        val current = records[batchId] ?: return
        records[batchId] = current.copy(pausedUntilMs = pausedUntilMs)
        write()
    }

    @Synchronized
    fun snapshot(): List<DurableBatchRecord> = records.values.toList()

    @Synchronized
    fun pendingEvents(): Int = records.values.sumOf { it.eventCount }

    private fun write() {
        val durablePath = path ?: return
        if (records.isEmpty()) {
            Files.deleteIfExists(durablePath)
            return
        }
        durablePath.parent?.let(Files::createDirectories)
        val temporary = durablePath.resolveSibling(
            ".${durablePath.fileName}.tmp-${ProcessHandle.current().pid()}-${Thread.currentThread().id}"
        )
        Files.writeString(
            temporary,
            json.encodeToString(
                DurableBatchState.serializer(),
                DurableBatchState(batches = records.values.toList()),
            ),
        )
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
    }
}
