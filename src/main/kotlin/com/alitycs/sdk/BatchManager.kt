package com.alitycs.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class BatchManager(
    private val flushSize: Int,
    private val flushInterval: Long,
    private val maxQueueSize: Int,
    private val debug: Boolean,
    private val sendFn: suspend (BatchPayload) -> SendOutcome,
    private val recoverFn: suspend () -> SendOutcome = { SendOutcome.Success },
    private val durablePendingEvents: () -> Int = { 0 },
    private val durable: Boolean = false,
) {
    private val queue = ArrayDeque<AnalyticsEvent>()
    private val queueLock = Any()
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
                if (hasQueuedEvents()) {
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
        val accepted: Boolean
        val shouldFlush: Boolean
        synchronized(queueLock) {
            accepted = queue.size < maxQueueSize
            if (accepted) queue.addLast(event)
            shouldFlush = accepted && queue.size >= flushSize
        }
        if (!accepted) {
            lostTotalCounter.incrementAndGet()
            warn("Queue full — dropping event '${event.event}'")
            return
        }

        if (shouldFlush) {
            scope?.launch { flush() }
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            if (recoverFn() is SendOutcome.TransportFailure) return
            val events = synchronized(queueLock) {
                buildList(queue.size) {
                    while (queue.isNotEmpty()) add(queue.removeFirst())
                }
            }

            if (events.isEmpty()) return

            // Ordered lists (not hash sets): requeueing must preserve original queue order,
            // and membership checks use identity so equal-but-distinct events stay distinct.
            val delivered = mutableListOf<AnalyticsEvent>()
            val lost = mutableListOf<AnalyticsEvent>()
            val failed = mutableListOf<AnalyticsEvent>()
            val splitBudget = SplitBudget(MAX_SENDS_PER_FLUSH)

            try {
                deliver(
                    events,
                    depth = 0,
                    splitBudget = splitBudget,
                    delivered = delivered,
                    lost = lost,
                    failed = failed,
                )
            } catch (e: CancellationException) {
                // Never swallow cancellation — but never drop drained-but-undelivered events either.
                // A durable send may be cancelled before the WAL write finishes, so requeue
                // conservatively. Stable event IDs make a retained duplicate safe to replay.
                val undelivered = events.filterNot { event ->
                    delivered.containsIdentity(event) || lost.containsIdentity(event)
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
        splitBudget: SplitBudget,
        delivered: MutableList<AnalyticsEvent>,
        lost: MutableList<AnalyticsEvent>,
        failed: MutableList<AnalyticsEvent>,
    ) {
        if (splitBudget.remaining <= 0) {
            lostTotalCounter.addAndGet(events.size.toLong())
            warn(
                "Batch-split request limit reached — dropping ${events.size} event(s): " +
                    events.map { it.eventId }
            )
            lost.addAll(events)
            return
        }
        splitBudget.remaining -= 1
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
                    deliver(
                        events.subList(0, mid),
                        depth + 1,
                        splitBudget,
                        delivered,
                        lost,
                        failed,
                    )
                    deliver(
                        events.subList(mid, events.size),
                        depth + 1,
                        splitBudget,
                        delivered,
                        lost,
                        failed,
                    )
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
                        if (durable && outcome.retained) {
                            "exact batch retained for restart"
                        } else {
                            "re-queueing ${events.size} event(s) at the head of the queue"
                        }
                )
                if (!durable || !outcome.retained) failed.addAll(events)
            }
        }
    }

    private fun requeueAtHead(events: Collection<AnalyticsEvent>) {
        if (events.isEmpty()) return
        val overflow = mutableListOf<AnalyticsEvent>()
        synchronized(queueLock) {
            // Iterating in reverse keeps the original queue order once prepended at the head.
            events.reversed().forEach { queue.addFirst(it) }
            // Retried survivors take priority; discard the newest tail if concurrent producers
            // filled the bounded queue while this flush was in flight.
            while (queue.size > maxQueueSize) overflow.add(queue.removeLast())
        }
        requeuedTotalCounter.addAndGet((events.size - overflow.size).toLong())
        if (overflow.isNotEmpty()) {
            lostTotalCounter.addAndGet(overflow.size.toLong())
            warn(
                "Queue full while restoring a failed batch — dropping ${overflow.size} " +
                    "newest event(s): ${overflow.map { it.eventId }}"
            )
        }
    }

    private fun warn(message: String) {
        // Warn-level: always surfaced, never debug-gated — silent data loss is how batches
        // used to disappear.
        System.err.println("[Alitycs] WARN $message")
    }

    private fun hasQueuedEvents(): Boolean = synchronized(queueLock) { queue.isNotEmpty() }

    val pending: Int
        get() = synchronized(queueLock) { queue.size } + durablePendingEvents()

    private data class SplitBudget(var remaining: Int)

    companion object {
        /** Recursion guard for batch splitting: enough for ~2^32 events, far beyond queue limits. */
        const val MAX_SPLIT_DEPTH = 32
        /** Hard request-amplification bound for recursively isolating HTTP 400 poison events. */
        const val MAX_SENDS_PER_FLUSH = 64
    }
}
