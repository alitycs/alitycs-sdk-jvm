package com.alitycs.sdk

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

class BatchManager(
    private val flushSize: Int,
    private val flushInterval: Long,
    private val maxQueueSize: Int,
    private val debug: Boolean,
    private val sendFn: suspend (BatchPayload) -> Unit
) {
    private val queue = ConcurrentLinkedQueue<AnalyticsEvent>()
    private val flushMutex = Mutex()
    private var timerJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (timerJob != null) return
        timerJob = scope.launch {
            while (isActive) {
                delay(flushInterval)
                if (queue.isNotEmpty()) {
                    flush()
                }
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    fun add(event: AnalyticsEvent) {
        if (queue.size >= maxQueueSize) {
            if (debug) {
                System.err.println("[Alitycs] Queue full — dropping event")
            }
            return
        }
        queue.add(event)
    }

    suspend fun flush() {
        flushMutex.withLock {
            if (queue.isEmpty()) return

            val events = mutableListOf<AnalyticsEvent>()
            while (queue.isNotEmpty()) {
                queue.poll()?.let { events.add(it) }
            }

            if (events.isEmpty()) return

            try {
                val payload = BatchPayload(
                    batchId = "batch_${generateId()}",
                    sentAt = System.currentTimeMillis(),
                    events = events
                )
                sendFn(payload)
            } catch (_: Exception) {
                if (debug) {
                    System.err.println("[Alitycs] Batch send failed — events dropped")
                }
            }
        }
    }

    val pending: Int get() = queue.size
}
