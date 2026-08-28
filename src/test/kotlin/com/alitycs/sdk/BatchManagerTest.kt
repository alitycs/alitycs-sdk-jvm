package com.alitycs.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)

class BatchManagerTest {

    private fun makeEvent(name: String = "test_event"): AnalyticsEvent = AnalyticsEvent(
        eventId = "evt_${generateId()}",
        event = name,
        eventType = EventType.TRACK,
        anonymousId = "anon_123",
        sessionId = "sess_123",
        timestamp = System.currentTimeMillis(),
        properties = mapOf("key" to "value"),
        context = EventContext(sdkVersion = "1.0.0", sdkLanguage = "kotlin")
    )

    @Test
    fun `add enqueues events`() {
        val manager = BatchManager(
            flushSize = 10,
            flushInterval = 10_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { SendOutcome.Success }
        )
        manager.add(makeEvent())
        assertEquals(1, manager.pending)
    }

    @Test
    fun `flush sends all queued events`() = runTest {
        val sent = AtomicReference<BatchPayload?>(null)
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 10_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = {
                sent.set(it)
                SendOutcome.Success
            }
        )
        manager.add(makeEvent("event1"))
        manager.add(makeEvent("event2"))
        assertEquals(2, manager.pending)

        manager.flush()
        assertEquals(0, manager.pending)
        assertNotNull(sent.get())
        assertEquals(2, sent.get()!!.events.size)
        assertTrue(sent.get()!!.batchId.startsWith("batch_"))
        assertEquals(2, manager.deliveredTotal)
    }

    @Test
    fun `drops events when queue is full`() {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 10_000L,
            maxQueueSize = 3,
            debug = false,
            sendFn = { SendOutcome.Success }
        )
        manager.add(makeEvent("e1"))
        manager.add(makeEvent("e2"))
        manager.add(makeEvent("e3"))
        manager.add(makeEvent("e4")) // should be dropped and counted as lost
        assertEquals(3, manager.pending)
        assertEquals(1, manager.lostTotal)
    }

    @Test
    fun `flush is no-op when queue is empty`() = runTest {
        var called = false
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 10_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = {
                called = true
                SendOutcome.Success
            }
        )
        manager.flush()
        assertFalse(called)
    }

    @Test
    fun `periodic flush fires on interval`() = runTest {
        val sent = AtomicReference<BatchPayload?>(null)
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 5_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = {
                sent.set(it)
                SendOutcome.Success
            }
        )
        manager.start(this)
        manager.add(makeEvent())

        advanceTimeBy(5_001)
        assertNotNull(sent.get())
        manager.stop()
    }

    @Test
    fun `flush size schedules delivery immediately`() = runTest {
        val sent = AtomicReference<BatchPayload?>(null)
        val manager = BatchManager(
            flushSize = 2,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = {
                sent.set(it)
                SendOutcome.Success
            },
        )
        manager.start(this)

        manager.add(makeEvent("event1"))
        manager.add(makeEvent("event2"))
        runCurrent()

        assertEquals(listOf("event1", "event2"), sent.get()?.events?.map { it.event })
        assertEquals(0, manager.pending)
        manager.stop()
    }

    @Test
    fun `batch 400 rejection splits payload and delivers both halves`() = runTest {
        val payloads = mutableListOf<BatchPayload>()
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { payload ->
                synchronized(payloads) { payloads.add(payload) }
                if (payloads.size == 1) {
                    SendOutcome.Rejected(status = 400, isBatchReject = true)
                } else {
                    SendOutcome.Success
                }
            }
        )
        val names = listOf("e1", "e2", "e3", "e4")
        names.forEach { manager.add(makeEvent(it)) }

        manager.flush()

        assertEquals(0, manager.pending)
        // First recorded payload is the rejected whole batch; only the split halves delivered.
        assertEquals(names, payloads.drop(1).flatMap { it.events.map { e -> e.event } })
        assertEquals(4, manager.deliveredTotal)
        assertEquals(0, manager.lostTotal)
        assertEquals(3, payloads.size) // whole batch + [e1,e2] + [e3,e4]
    }

    @Test
    fun `split recursion keeps queue order across halves`() = runTest {
        val payloads = mutableListOf<BatchPayload>()
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { payload ->
                synchronized(payloads) { payloads.add(payload) }
                if (payloads.size < 6) {
                    // Reject until the final single-event batches arrive.
                    SendOutcome.Rejected(status = 400, isBatchReject = true)
                } else {
                    SendOutcome.Success
                }
            }
        )
        val names = listOf("a", "b", "c", "d")
        names.forEach { manager.add(makeEvent(it)) }

        manager.flush()

        // Calls: [a,b,c,d] -> [a,b] -> [a] -> [b] -> [c,d] -> [c] -> [d]; last two are accepted
        // singles and must arrive in original queue order.
        assertEquals(listOf("c", "d"), payloads.takeLast(2).map { it.events.single().event })
        assertEquals(0, manager.pending)
    }

    @Test
    fun `single event rejected by batch 400 is counted lost not requeued`() = runTest {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { SendOutcome.Rejected(status = 400, isBatchReject = true) }
        )
        manager.add(makeEvent("poison"))

        manager.flush()

        assertEquals(0, manager.pending)
        assertEquals(1, manager.lostTotal)
        assertEquals(0, manager.requeuedTotal)
    }

    @Test
    fun `transport failure requeues events at head preserving order`() = runTest {
        val payloads = mutableListOf<List<String>>()
        var failFirst = true
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { payload ->
                var outcome: SendOutcome = SendOutcome.Success
                synchronized(payloads) {
                    if (failFirst) {
                        failFirst = false
                        outcome = SendOutcome.TransportFailure(Exception("boom"))
                    } else {
                        payloads.add(payload.events.map { it.event })
                    }
                }
                outcome
            }
        )
        listOf("first", "second", "third").forEach { manager.add(makeEvent(it)) }

        manager.flush()
        assertEquals(3, manager.pending)
        assertEquals(3, manager.requeuedTotal)

        // New events appended after the requeued survivors keep global FIFO order.
        manager.add(makeEvent("fourth"))
        manager.flush()

        assertEquals(0, manager.pending)
        assertEquals(listOf("first", "second", "third", "fourth"), payloads.single())
        assertEquals(4, manager.deliveredTotal)
    }

    @Test
    fun `non-batch 4xx rejection drops the batch with lost counter`() = runTest {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { SendOutcome.Rejected(status = 401, isBatchReject = false) }
        )
        manager.add(makeEvent())
        manager.add(makeEvent())

        manager.flush()

        assertEquals(0, manager.pending)
        assertEquals(2, manager.lostTotal)
        assertEquals(0, manager.requeuedTotal)
    }

    @Test
    fun `cancellation propagates and drained events are requeued`() = runTest {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { throw CancellationException("scope cancelled") }
        )
        manager.add(makeEvent("kept1"))
        manager.add(makeEvent("kept2"))

        assertThrows<CancellationException> { manager.flush() }

        assertEquals(2, manager.pending)
        assertEquals(0, manager.lostTotal)
    }

    @Test
    fun `durable retained failure stays in WAL without an in-memory duplicate`() = runTest {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { SendOutcome.TransportFailure(Exception("offline"), retained = true) },
            durablePendingEvents = { 2 },
            durable = true,
        )
        manager.add(makeEvent("retained1"))
        manager.add(makeEvent("retained2"))

        manager.flush()

        assertEquals(2, manager.pending)
        assertEquals(0, manager.requeuedTotal)
        assertEquals(0, manager.lostTotal)
    }

    @Test
    fun `durable cancellation conservatively restores drained events`() = runTest {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 60_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { throw CancellationException("cancelled before WAL ownership is known") },
            durable = true,
        )
        manager.add(makeEvent("kept1"))
        manager.add(makeEvent("kept2"))

        assertThrows<CancellationException> { manager.flush() }

        assertEquals(2, manager.pending)
        assertEquals(2, manager.requeuedTotal)
        assertEquals(0, manager.lostTotal)
    }

    @Test
    fun `batch splitting has a hard request amplification bound`() = runTest {
        var calls = 0
        val manager = BatchManager(
            flushSize = 200,
            flushInterval = 60_000L,
            maxQueueSize = 200,
            debug = false,
            sendFn = {
                calls += 1
                SendOutcome.Rejected(status = 400, isBatchReject = true)
            },
        )
        repeat(100) { manager.add(makeEvent("event$it")) }

        manager.flush()

        assertEquals(BatchManager.MAX_SENDS_PER_FLUSH, calls)
        assertEquals(100, manager.lostTotal)
        assertEquals(0, manager.pending)
    }
}
