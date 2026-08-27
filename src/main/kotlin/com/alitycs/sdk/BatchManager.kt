package com.alitycs.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

class BatchManager(
    private val flushSize: Int,
    private val flushInterval: Long,
    private val maxQueueSize: Int,
    private val debug: Boolean,
    private val sendFn: suspend (BatchPayload) -> SendOutcome
) {
    private val queue = ConcurrentLinkedDeque<AnalyticsEvent>()
    private val flushMutex = Mutex()
    private var timerJob: Job? = null
    private var scope: CoroutineScope? = null

    private val deliveredTotalCounter = AtomicLong(0)
    private val requeuedTotalCounter = AtomicLong(0)
    private val lostTotalCounter = AtomicLong(0)

    /** Events confirmed delivered (2xx) since this manager was created. */
    val deliveredTotal: Long get() = deliveredTotalCounter.get()

    /** Events re-added to the queue head after a transient transport failure. */
    val requeuedTotal: Long get() = requeuedTotalCounter.get()

    /** Events permanently lost: locally rejected, queue-overflowed, or refused by the server. */
    val lostTotal: Long get() = lostTotalCounter.get()

    fun start(scope: CoroutineScope) {
        if (timerJob != null) return
        this.scope = scope
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
        scope = null
    }

    fun add(event: AnalyticsEvent) {
        if (queue.size >= maxQueueSize) {
            lostTotalCounter.incrementAndGet()
            warn("Queue full — dropping event '${event.event}'")
            return
        }
        queue.add(event)

        if (queue.size >= flushSize) {
            scope?.launch { flush() }
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            if (queue.isEmpty()) return

            val events = mutableListOf<AnalyticsEvent>()
            while (queue.isNotEmpty()) {
                queue.poll()?.let { events.add(it) }
            }

            if (events.isEmpty()) return

            // Ordered lists (not hash sets): requeueing must preserve original queue order,
            // and membership checks use identity so equal-but-distinct events stay distinct.
            val delivered = mutableListOf<AnalyticsEvent>()
            val lost = mutableListOf<AnalyticsEvent>()
            val failed = mutableListOf<AnalyticsEvent>()

            try {
                deliver(events, depth = 0, delivered = delivered, lost = lost, failed = failed)
            } catch (e: CancellationException) {
                // Never swallow cancellation — but never drop drained-but-undelivered events either.
                val undelivered = events.filterNot { e ->
                    delivered.containsIdentity(e) || lost.containsIdentity(e)
                }
                requeueAtHead(undelivered)
                throw e
            }

            if (failed.isNotEmpty()) {
                requeueAtHead(failed)
            }
        }
    }

    private fun List<AnalyticsEvent>.containsIdentity(event: AnalyticsEvent): Boolean =
        any { it === event }

    /**
     * Sends one payload and resolves its fate.
     *
     * - Success: events counted as delivered.
     * - Rejected with a whole-batch 400: the payload may contain a single invalid event, so the
     *   batch is split in half and each half is retried recursively (depth bounded by
     *   ~log2(batch)); an individual event the server still refuses is dropped with a warning.
     * - Other 4xx: deterministic refusal (e.g. bad API key) — dropped with a warning.
     * - TransportFailure: events are collected for re-queueing at the head, order preserved.
     */
    private suspend fun deliver(
        events: List<AnalyticsEvent>,
        depth: Int,
        delivered: MutableList<AnalyticsEvent>,
        lost: MutableList<AnalyticsEvent>,
        failed: MutableList<AnalyticsEvent>,
    ) {
        val payload = BatchPayload(
            batchId = "batch_${generateId()}",
            sentAt = System.currentTimeMillis(),
            events = events.toList()
        )

        val outcome = try {
            sendFn(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendOutcome.TransportFailure(e)
        }

        when (outcome) {
            is SendOutcome.Success -> {
                deliveredTotalCounter.addAndGet(events.size.toLong())
                delivered.addAll(events)
            }

            is SendOutcome.Rejected -> {
                if (outcome.isBatchReject && events.size > 1 && depth < MAX_SPLIT_DEPTH) {
                    val mid = events.size / 2
                    deliver(events.subList(0, mid), depth + 1, delivered, lost, failed)
                    deliver(events.subList(mid, events.size), depth + 1, delivered, lost, failed)
                } else {
                    lostTotalCounter.addAndGet(events.size.toLong())
                    warn(
                        "Server rejected ${events.size} event(s) with HTTP ${outcome.status} — " +
                            "dropping: ${events.map { it.eventId }}"
                    )
                    lost.addAll(events)
                }
            }

            is SendOutcome.TransportFailure -> {
                warn(
                    "Transport failure (${outcome.cause?.message ?: "unknown"}) — " +
                        "re-queueing ${events.size} event(s) at the head of the queue"
                )
                failed.addAll(events)
            }
        }
    }

    private fun requeueAtHead(events: Collection<AnalyticsEvent>) {
        if (events.isEmpty()) return
        requeuedTotalCounter.addAndGet(events.size.toLong())
        // Iterating in reverse keeps the original queue order once prepended at the head.
        events.reversed().forEach { queue.addFirst(it) }
    }

    private fun warn(message: String) {
        // Warn-level: always surfaced, never debug-gated — silent data loss is how batches
        // used to disappear.
        System.err.println("[Alitycs] WARN $message")
    }

    val pending: Int get() = queue.size

    companion object {
        /** Recursion guard for batch splitting: enough for ~2^32 events, far beyond queue limits. */
        const val MAX_SPLIT_DEPTH = 32
    }
}
