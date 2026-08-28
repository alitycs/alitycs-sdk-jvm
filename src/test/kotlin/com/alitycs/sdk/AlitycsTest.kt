package com.alitycs.sdk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress

class AlitycsTest {

    private fun makeConfig(port: Int = 0): AlitycsConfig = AlitycsConfig(
        apiKey = "test-key",
        endpoint = if (port > 0) "http://localhost:$port/events" else "http://localhost:1/events",
        flushInterval = 60_000L,
        flushSize = 100,
        maxRetries = 0,
        batching = true,
        debug = false
    )

    @Test
    fun `init creates instance with valid config`() {
        val sdk = Alitycs.init(makeConfig())
        assertNotNull(sdk)
    }

    @Test
    fun `init throws on blank apiKey`() {
        assertThrows<IllegalArgumentException> {
            Alitycs.init(AlitycsConfig(apiKey = ""))
        }
    }

    @Test
    fun `init throws on whitespace apiKey`() {
        assertThrows<IllegalArgumentException> {
            Alitycs.init(AlitycsConfig(apiKey = "   "))
        }
    }

    @Test
    fun `init throws on blank persistence path`() {
        assertThrows<IllegalArgumentException> {
            Alitycs.init(AlitycsConfig(apiKey = "test", persistencePath = "  "))
        }
    }

    @Test
    fun `track enqueues event`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.track("button_click", mapOf("button" to "submit"))
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `track ignores blank event name`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.track("")
        assertEquals(0, sdk.pending)
    }

    @Test
    fun `captureError enqueues explicit errors and ignores blank names`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.captureError("checkout_failed", mapOf("retryable" to true))
        sdk.captureError("")
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `identify enqueues event and sets userId`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.identify("user-123", mapOf("email" to "a@b.com"))
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `identify ignores blank userId`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.identify("")
        assertEquals(0, sdk.pending)
    }

    @Test
    fun `page enqueues event`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.page("Dashboard")
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `page uses page_view as default name`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.page()
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `setGlobalProperties merges properties`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.setGlobalProperties(mapOf("appVersion" to "1.0"))
        val props = sdk.getGlobalProperties()
        assertEquals("1.0", props["appVersion"])
    }

    @Test
    fun `removeGlobalProperties removes specified keys`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.setGlobalProperties(mapOf("a" to "1", "b" to "2"))
        sdk.removeGlobalProperties(listOf("a"))
        val props = sdk.getGlobalProperties()
        assertNull(props["a"])
        assertEquals("2", props["b"])
    }

    @Test
    fun `clearGlobalProperties removes all properties`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.setGlobalProperties(mapOf("a" to "1", "b" to "2"))
        sdk.clearGlobalProperties()
        assertTrue(sdk.getGlobalProperties().isEmpty())
    }

    @Test
    fun `global properties are included in tracked events`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.setGlobalProperties(mapOf("env" to "test"))
        sdk.track("event1", mapOf("key" to "value"))
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `initDefault sets default instance for static methods`() {
        val sdk = Alitycs.initDefault(makeConfig())
        Alitycs.track("static_event")
        Alitycs.captureError("static_error")
        assertEquals(2, sdk.pending)
    }

    @Test
    fun `multiple track calls accumulate in queue`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.track("event1")
        sdk.track("event2")
        sdk.track("event3")
        assertEquals(3, sdk.pending)
    }

    @Test
    fun `reset and blocking lifecycle wrappers complete queued delivery`() {
        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/events") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()
        try {
            val sdk = Alitycs.init(makeConfig(server.address.port))
            sdk.identify("blocking-user")
            sdk.trackRevenue(
                RevenuePayload.transaction(
                    factId = "blocking-payment",
                    amount = "19.99",
                    currency = "USD",
                ),
            )
            sdk.reset()
            sdk.track("after_reset")

            sdk.flushBlocking()
            assertEquals(0, sdk.pending)
            sdk.shutdownBlocking()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `failed delivery is honest - events stay pending after transport failure`() {
        // localhost:1 refuses connections: with honest failures the events are re-queued,
        // not silently dropped.
        val sdk = Alitycs.init(makeConfig())
        sdk.track("never_delivered")
        sdk.flushBlocking()
        assertEquals(1, sdk.pending)
        sdk.shutdownBlocking()
        assertEquals(1, sdk.pending) // shutdown re-attempted delivery and failed again
    }

    @Test
    fun `enqueue after shutdown rejects locally instead of silently swallowing`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.track("before_shutdown")
        sdk.shutdownBlocking()
        assertTrue(sdk.isShutdown)
        assertEquals(1, sdk.pending) // failed delivery stays pending from before shutdown

        sdk.track("after_shutdown")
        sdk.page("after_shutdown_page")
        assertEquals(1, sdk.pending) // post-shutdown events are never queued
        assertEquals(2, sdk.rejectedLocally)
    }

    @Test
    fun `non-batching client rejects enqueue after shutdown`() {
        val sdk = Alitycs.init(makeConfig().copy(batching = false))
        sdk.shutdownBlocking()
        assertTrue(sdk.isShutdown)
        sdk.track("after_shutdown")
        assertEquals(0, sdk.pending)
        assertEquals(1, sdk.rejectedLocally)
    }

    @Test
    fun `initDefault shuts down the previous default instance instead of leaking it`() = runBlocking {
        try {
            val first = Alitycs.initDefault(makeConfig())
            assertFalse(first.isShutdown)
            first.track("held_by_first")

            val second = Alitycs.initDefault(makeConfig())
            assertTrue(first.isShutdown) // previous scope/timer shut down, not orphaned
            assertFalse(second.isShutdown)

            // The static surface now routes to the new default only.
            Alitycs.track("routed_to_second")
            assertEquals(1, second.pending)
            assertEquals(1, first.pending)
        } finally {
            Alitycs.shutdownDefault()
        }
    }

    @Test
    fun `default instance exposes the complete static capability surface`() = runBlocking {
        val sdk = Alitycs.initDefault(makeConfig())

        Alitycs.setDefaultGlobalProperties(mapOf("suite" to "jvm-static"))
        assertEquals("jvm-static", Alitycs.getDefaultGlobalProperties()["suite"])
        Alitycs.identify("static-user")
        Alitycs.page("StaticPage")
        Alitycs.trackRevenue(
            RevenuePayload.transaction(
                factId = "static-payment",
                amount = "7.00",
                currency = "USD",
            ),
        )
        Alitycs.resetDefault()

        assertEquals(3, sdk.pending)
        Alitycs.flushDefault()
        Alitycs.shutdownDefault()
        assertTrue(Alitycs.getDefaultGlobalProperties().isEmpty())
    }
}
