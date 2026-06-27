package com.alitycs.sdk

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class HttpTransportTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
    }

    private fun makePayload(): BatchPayload = BatchPayload(
        batchId = "batch_${generateId()}",
        sentAt = System.currentTimeMillis(),
        events = listOf(
            AnalyticsEvent(
                eventId = "evt_${generateId()}",
                event = "test_event",
                eventType = EventType.TRACK,
                anonymousId = "anon_123",
                sessionId = "sess_123",
                timestamp = System.currentTimeMillis(),
                properties = mapOf("key" to "value"),
                context = EventContext(sdkVersion = "1.0.0", sdkLanguage = "kotlin")
            )
        )
    )

    @Test
    fun `successful send on 200`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 3,
            debug = false
        )
        transport.send(makePayload())
        assertEquals(1, requestCount.get())
    }

    @Test
    fun `sends correct headers`() = runTest {
        var authHeader: String? = null
        var contentType: String? = null
        server.createContext("/events") { exchange ->
            authHeader = exchange.requestHeaders.getFirst("Authorization")
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "my-api-key",
            maxRetries = 0,
            debug = false
        )
        transport.send(makePayload())
        assertEquals("Bearer my-api-key", authHeader)
        assertEquals("application/json", contentType)
    }

    @Test
    fun `does not retry on 400`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(400, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 3,
            debug = false
        )
        transport.send(makePayload())
        assertEquals(1, requestCount.get())
    }

    @Test
    fun `retries on 429`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            val count = requestCount.incrementAndGet()
            if (count <= 2) {
                exchange.sendResponseHeaders(429, 0)
            } else {
                exchange.sendResponseHeaders(200, 0)
            }
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 3,
            debug = false
        )
        transport.send(makePayload())
        assertEquals(3, requestCount.get())
    }

    @Test
    fun `retries on 500`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            val count = requestCount.incrementAndGet()
            if (count <= 1) {
                exchange.sendResponseHeaders(500, 0)
            } else {
                exchange.sendResponseHeaders(200, 0)
            }
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 3,
            debug = false
        )
        transport.send(makePayload())
        assertEquals(2, requestCount.get())
    }

    @Test
    fun `exhausts retries and does not throw`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(500, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 2,
            debug = false
        )
        // Should not throw
        transport.send(makePayload())
        assertEquals(3, requestCount.get()) // initial + 2 retries
    }
}
