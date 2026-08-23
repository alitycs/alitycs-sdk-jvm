package com.alitycs.sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
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
            sendFn = {}
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
            sendFn = { sent.set(it) }
        )
        manager.add(makeEvent("event1"))
        manager.add(makeEvent("event2"))
        assertEquals(2, manager.pending)

        manager.flush()
        assertEquals(0, manager.pending)
        assertNotNull(sent.get())
        assertEquals(2, sent.get()!!.events.size)
        assertTrue(sent.get()!!.batchId.startsWith("batch_"))
    }

    @Test
    fun `drops events when queue is full`() {
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 10_000L,
            maxQueueSize = 3,
            debug = false,
            sendFn = {}
        )
        manager.add(makeEvent("e1"))
        manager.add(makeEvent("e2"))
        manager.add(makeEvent("e3"))
        manager.add(makeEvent("e4")) // should be dropped
        assertEquals(3, manager.pending)
    }

    @Test
    fun `flush is no-op when queue is empty`() = runTest {
        var called = false
        val manager = BatchManager(
            flushSize = 100,
            flushInterval = 10_000L,
            maxQueueSize = 100,
            debug = false,
            sendFn = { called = true }
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
            sendFn = { sent.set(it) }
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
            sendFn = { sent.set(it) },
        )
        manager.start(this)

        manager.add(makeEvent("event1"))
        manager.add(makeEvent("event2"))
        runCurrent()

        assertEquals(listOf("event1", "event2"), sent.get()?.events?.map { it.event })
        assertEquals(0, manager.pending)
        manager.stop()
    }
}
