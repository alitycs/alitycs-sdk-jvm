package com.alitycs.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class AlitycsTest {

    private fun makeConfig(port: Int = 0): AlitycsConfig = AlitycsConfig(
        apiKey = "test-key",
        endpoint = if (port > 0) "http://localhost:$port/events" else "http://localhost:1/events",
        flushInterval = 60_000L,
        flushSize = 100,
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
        assertEquals(1, sdk.pending)
    }

    @Test
    fun `multiple track calls accumulate in queue`() {
        val sdk = Alitycs.init(makeConfig())
        sdk.track("event1")
        sdk.track("event2")
        sdk.track("event3")
        assertEquals(3, sdk.pending)
    }
}
