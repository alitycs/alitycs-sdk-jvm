package com.alitycs.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

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
}
