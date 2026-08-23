package com.alitycs.sdk.integration

import com.alitycs.sdk.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class SdkIntegrationTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private val receivedBodies = CopyOnWriteArrayList<String>()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.createContext("/events") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            receivedBodies.add(body)
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun `end-to-end track and flush sends valid batch payload`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.track("button_click", mapOf("button" to "submit"))
        sdk.flush()
        sdk.shutdown()

        assertEquals(1, receivedBodies.size)

        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        assertTrue(batch["batchId"]!!.jsonPrimitive.content.startsWith("batch_"))
        assertTrue(batch["sentAt"]!!.jsonPrimitive.long > 0)

        val events = batch["events"]!!.jsonArray
        assertEquals(1, events.size)

        val event = events[0].jsonObject
        assertEquals("button_click", event["event"]!!.jsonPrimitive.content)
        assertEquals("track", event["eventType"]!!.jsonPrimitive.content)
        assertTrue(event["eventId"]!!.jsonPrimitive.content.startsWith("evt_"))
        assertTrue(event["timestamp"]!!.jsonPrimitive.long > 0)
        assertNotNull(event["anonymousId"])
        assertNotNull(event["sessionId"])

        val props = event["properties"]!!.jsonObject
        assertEquals("submit", props["button"]!!.jsonPrimitive.content)

        val ctx = event["context"]!!.jsonObject
        assertEquals("1.0.0", ctx["sdkVersion"]!!.jsonPrimitive.content)
        assertEquals("kotlin", ctx["sdkLanguage"]!!.jsonPrimitive.content)
    }

    @Test
    fun `identify sets userId on subsequent events`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.identify("user-456", mapOf("email" to "test@example.com"))
        sdk.track("page_load")
        sdk.flush()
        sdk.shutdown()

        assertEquals(1, receivedBodies.size)

        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        val events = batch["events"]!!.jsonArray
        assertEquals(2, events.size)

        // Both events should have userId
        for (event in events) {
            assertEquals("user-456", event.jsonObject["userId"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `global properties are included in events`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.setGlobalProperties(mapOf("appVersion" to "2.0", "env" to "test"))
        sdk.track("event1", mapOf("custom" to "prop"))
        sdk.flush()
        sdk.shutdown()

        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        val event = batch["events"]!!.jsonArray[0].jsonObject
        val props = event["properties"]!!.jsonObject

        assertEquals("2.0", props["appVersion"]!!.jsonPrimitive.content)
        assertEquals("test", props["env"]!!.jsonPrimitive.content)
        assertEquals("prop", props["custom"]!!.jsonPrimitive.content)
    }

    @Test
    fun `non-batched mode sends immediately`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = false
            )
        )

        sdk.track("immediate_event")
        // Give coroutine time to execute
        kotlinx.coroutines.delay(500)
        sdk.shutdown()

        assertTrue(receivedBodies.size >= 1)
        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        val events = batch["events"]!!.jsonArray
        assertEquals(1, events.size)
        assertEquals("immediate_event", events[0].jsonObject["event"]!!.jsonPrimitive.content)
    }

    @Test
    fun `page event has correct eventType`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.page("Dashboard")
        sdk.flush()
        sdk.shutdown()

        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        val event = batch["events"]!!.jsonArray[0].jsonObject
        assertEquals("page", event["eventType"]!!.jsonPrimitive.content)
        assertEquals("Dashboard", event["event"]!!.jsonPrimitive.content)
    }

    @Test
    fun `captureError serializes the error event type`() = runTest {
        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "integration-test-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.captureError("checkout_failed", mapOf("code" to "PAYMENT"))
        sdk.flush()
        sdk.shutdown()

        val batch = json.parseToJsonElement(receivedBodies[0]).jsonObject
        val event = batch["events"]!!.jsonArray[0].jsonObject
        assertEquals("error", event["eventType"]!!.jsonPrimitive.content)
        assertEquals("checkout_failed", event["event"]!!.jsonPrimitive.content)
        assertEquals("PAYMENT", event["properties"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `authorization header is sent correctly`() = runTest {
        var authHeader: String? = null
        server.removeContext("/events")
        server.createContext("/events") { exchange ->
            authHeader = exchange.requestHeaders.getFirst("Authorization")
            val body = exchange.requestBody.bufferedReader().readText()
            receivedBodies.add(body)
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }

        val sdk = Alitycs.init(
            AlitycsConfig(
                apiKey = "my-secret-key",
                endpoint = "http://localhost:$port/events",
                batching = true,
                flushInterval = 60_000L,
                flushSize = 100
            )
        )

        sdk.track("test")
        sdk.flush()
        sdk.shutdown()

        assertEquals("Bearer my-secret-key", authHeader)
    }
}
