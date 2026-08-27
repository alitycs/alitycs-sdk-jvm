package com.alitycs.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class UtilsTest {

    @Test
    fun `generateId returns valid UUID format`() {
        val id = generateId()
        assertNotNull(id)
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `generateId returns unique values`() {
        val ids = (1..100).map { generateId() }.toSet()
        assertEquals(100, ids.size)
    }

    @Test
    fun `serializeProperties converts primitives to strings`() {
        val result = serializeProperties(mapOf(
            "string" to "hello",
            "number" to 42,
            "double" to 3.14,
            "boolean" to true
        ))
        assertEquals("hello", result["string"])
        assertEquals("42", result["number"])
        assertEquals("3.14", result["double"])
        assertEquals("true", result["boolean"])
    }

    @Test
    fun `serializeProperties skips null values`() {
        val result = serializeProperties(mapOf(
            "present" to "value",
            "absent" to null
        ))
        assertEquals(1, result.size)
        assertEquals("value", result["present"])
        assertNull(result["absent"])
    }

    @Test
    fun `serializeProperties converts objects to JSON strings`() {
        val result = serializeProperties(mapOf(
            "nested" to mapOf("key" to "value")
        ))
        assertEquals("""{"key":"value"}""", result["nested"])
    }

    @Test
    fun `serializeProperties converts collections to JSON strings`() {
        val result = serializeProperties(mapOf(
            "list" to listOf(1, 2, 3)
        ))
        assertEquals("[1,2,3]", result["list"])
    }

    @Test
    fun `serializeProperties rejects oversized property key`() {
        val exception = assertThrows<EventRejectedException> {
            serializeProperties(mapOf("k".repeat(101) to "value"))
        }
        assertTrue(exception.message!!.contains("property key"))
    }

    @Test
    fun `serializeProperties rejects oversized property value`() {
        val exception = assertThrows<EventRejectedException> {
            serializeProperties(mapOf("key" to "v".repeat(1001)))
        }
        assertTrue(exception.message!!.contains("value for property key"))
    }

    @Test
    fun `serializeProperties rejects more than fifty properties`() {
        val props = (1..51).associate { "key$it" to "value" }
        val exception = assertThrows<EventRejectedException> { serializeProperties(props) }
        assertTrue(exception.message!!.contains("exceeds the maximum"))
    }

    @Test
    fun `serializeProperties accepts values exactly at the limits`() {
        val result = serializeProperties(mapOf("k".repeat(100) to "v".repeat(1000)))
        assertEquals(1000, result.getValue("k".repeat(100)).length)
    }

    private fun validEvent(properties: Map<String, String> = mapOf("key" to "value")): AnalyticsEvent =
        AnalyticsEvent(
            eventId = "evt_${generateId()}",
            event = "test_event",
            eventType = EventType.TRACK,
            anonymousId = "anon_123",
            sessionId = "sess_123",
            timestamp = System.currentTimeMillis(),
            properties = properties,
            context = EventContext(sdkVersion = "1.0.0", sdkLanguage = "kotlin")
        )

    @Test
    fun `validateEvent accepts a well-formed event`() {
        validateEvent(validEvent())
    }

    @Test
    fun `validateEvent rejects seconds-scale timestamps`() {
        val event = validEvent().copy(timestamp = System.currentTimeMillis() / 1000)
        val exception = assertThrows<EventRejectedException> { validateEvent(event) }
        assertTrue(exception.message!!.contains("epoch milliseconds"))
    }

    @Test
    fun `validateEvent rejects future timestamps`() {
        val event = validEvent().copy(timestamp = System.currentTimeMillis() + 60_000)
        val exception = assertThrows<EventRejectedException> { validateEvent(event) }
        assertTrue(exception.message!!.contains("future"))
    }

    @Test
    fun `validateEvent rejects events older than seven days`() {
        val event = validEvent().copy(timestamp = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L)
        val exception = assertThrows<EventRejectedException> { validateEvent(event) }
        assertTrue(exception.message!!.contains("too old"))
    }

    @Test
    fun `validateEvent rejects events over the estimated size limit`() {
        // Estimate = userId(0) + anonymousId(8) + action(11) + keys/values + 200 overhead.
        val bigValue = "v".repeat(64 * 1024)
        val event = validEvent(mapOf("big" to bigValue))
        val exception = assertThrows<EventRejectedException> { validateEvent(event) }
        assertTrue(exception.message!!.contains("exceeds the maximum allowed size"))
    }

    @Test
    fun `validateEvent accumulates multiple violations in one message`() {
        val event = validEvent(
            mapOf(
                "k".repeat(101) to "value",
                "another" to "v".repeat(1001),
            )
        ).copy(event = "")
        val exception = assertThrows<EventRejectedException> { validateEvent(event) }
        assertTrue(exception.message!!.contains("; "))
    }

    @Test
    fun `limits mirror the canonical ingestion contract`() {
        assertEquals(50, Limits.MAX_PROPERTIES_COUNT)
        assertEquals(100, Limits.MAX_PROPERTY_KEY_LENGTH)
        assertEquals(1000, Limits.MAX_PROPERTY_VALUE_LENGTH)
        assertEquals(64 * 1024, Limits.MAX_EVENT_SIZE_BYTES)
        assertEquals(200, Limits.EVENT_SIZE_OVERHEAD)
        assertEquals(1_000_000_000_000L, Limits.MIN_EPOCH_MILLIS)
        assertEquals(7L, Limits.MAX_EVENT_AGE_DAYS)
        assertEquals(1, Limits.REVENUE_VERSION)
        assertEquals(200, Limits.MAX_REVENUE_FACT_ID_LENGTH)
    }
}
