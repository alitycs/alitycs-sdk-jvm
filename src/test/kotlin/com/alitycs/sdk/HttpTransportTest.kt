package com.alitycs.sdk

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class HttpTransportTest {

    @TempDir
    lateinit var directory: Path

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
        assertEquals(SendOutcome.Success, transport.send(makePayload()))
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
    fun `does not retry on 400 and reports a whole-batch rejection`() = runTest {
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
        val outcome = transport.send(makePayload())
        assertEquals(1, requestCount.get())
        assertTrue(outcome is SendOutcome.Rejected)
        assertEquals(400, (outcome as SendOutcome.Rejected).status)
        assertTrue(outcome.isBatchReject)
    }

    @Test
    fun `non-batch 4xx is reported without batch-reject flag`() = runTest {
        server.createContext("/events") { exchange ->
            exchange.sendResponseHeaders(401, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 3,
            debug = false
        )
        val outcome = transport.send(makePayload())
        assertEquals(SendOutcome.Rejected(401, false), outcome)
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
    fun `honours a seconds-valued Retry-After from a 429`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.responseHeaders.add("Retry-After", "2")
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
        assertEquals(SendOutcome.Success, transport.send(makePayload()))
        assertEquals(2, requestCount.get())
        // Virtual time only advanced through the retry delay: at least Retry-After's
        // 2s (the default backoff would have been 1s), and far below the 10s cap.
        assertTrue(testScheduler.currentTime >= 2_000) {
            "Retry-After: 2 not honoured — waited only ${testScheduler.currentTime}ms"
        }
        assertTrue(testScheduler.currentTime <= 2_100)
    }

    @Test
    fun `honours an HTTP-date Retry-After from a 429`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.responseHeaders.add(
                    "Retry-After",
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        java.time.ZonedDateTime.now().plusSeconds(3)
                    )
                )
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
        assertEquals(SendOutcome.Success, transport.send(makePayload()))
        // Wall time elapses between the server formatting the date and the transport
        // parsing it, so allow generous slop — but stay above the 1s default backoff,
        // which is what an ignored header would have produced.
        assertTrue(testScheduler.currentTime >= 1_500) {
            "HTTP-date Retry-After not honoured — waited only ${testScheduler.currentTime}ms"
        }
        assertTrue(testScheduler.currentTime <= 3_100)
    }

    @Test
    fun `caps a huge Retry-After at ten seconds`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.responseHeaders.add("Retry-After", "3600")
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
        assertEquals(SendOutcome.Success, transport.send(makePayload()))
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun `a 429 without Retry-After keeps the default backoff`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
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
        assertEquals(SendOutcome.Success, transport.send(makePayload()))
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun `exhausts retries and reports a transport failure`() = runTest {
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
        val outcome = transport.send(makePayload())
        assertTrue(outcome is SendOutcome.TransportFailure)
        assertEquals(3, requestCount.get()) // initial + 2 retries
    }

    @Test
    fun `request timeout aborts a slow response`() = runTest {
        server.createContext("/events") { exchange ->
            Thread.sleep(5_000)
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()

        val transport = HttpTransport(
            endpoint = "http://localhost:$port/events",
            apiKey = "test-key",
            maxRetries = 0,
            debug = false,
            connectTimeoutMs = 2_000,
            requestTimeoutMs = 300
        )
        val start = System.currentTimeMillis()
        val outcome = transport.send(makePayload())
        val elapsed = System.currentTimeMillis() - start

        assertTrue(outcome is SendOutcome.TransportFailure)
        assertTrue(elapsed < 4_000) { "request timeout not applied — took ${elapsed}ms" }
    }

    @Test
    fun `config exposes sensible timeout defaults`() {
        assertEquals(5_000L, HttpTransport.DEFAULT_CONNECT_TIMEOUT_MS)
        assertEquals(10_000L, HttpTransport.DEFAULT_REQUEST_TIMEOUT_MS)
    }

    @Test
    fun `connect timeout fails fast against an unroutable address`() = runTest {
        val transport = HttpTransport(
            endpoint = "http://10.255.255.1:1/events", // non-routable, hangs on connect
            apiKey = "test-key",
            maxRetries = 0,
            debug = false,
            connectTimeoutMs = 500,
            requestTimeoutMs = 30_000
        )
        val start = System.currentTimeMillis()
        val outcome = transport.send(makePayload())
        val elapsed = System.currentTimeMillis() - start

        assertTrue(outcome is SendOutcome.TransportFailure)
        assertTrue(elapsed < 20_000) { "connect timeout not applied — took ${elapsed}ms" }
    }

    @Test
    fun `persisted batch is replayed byte identically after restart`() = runTest {
        val bodies = mutableListOf<String>()
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            bodies.add(exchange.requestBody.bufferedReader().readText())
            if (requestCount.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(500, 0)
            } else {
                exchange.sendResponseHeaders(202, 0)
            }
            exchange.close()
        }
        server.start()
        val path = directory.resolve("wal.json")
        val first = HttpTransport(
            "http://localhost:$port/events",
            "test-key",
            maxRetries = 0,
            debug = false,
            persistencePath = path.toString(),
        )
        val failure = first.send(makePayload()) as SendOutcome.TransportFailure
        assertTrue(failure.retained)
        assertEquals(1, first.durablePendingEvents)

        val restarted = HttpTransport(
            "http://localhost:$port/events",
            "test-key",
            maxRetries = 0,
            debug = false,
            persistencePath = path.toString(),
        )
        assertEquals(SendOutcome.Success, restarted.recover())
        assertEquals(bodies[0], bodies[1])
        assertEquals(0, restarted.durablePendingEvents)
        assertFalse(Files.exists(path))
    }

    @Test
    fun `restart honours persisted Retry-After deadline`() = runTest {
        val requestCount = AtomicInteger(0)
        server.createContext("/events") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.responseHeaders.add("Retry-After", "3")
                exchange.sendResponseHeaders(429, 0)
            } else {
                exchange.sendResponseHeaders(202, 0)
            }
            exchange.close()
        }
        server.start()
        val path = directory.resolve("wal.json")
        val first = HttpTransport(
            "http://localhost:$port/events",
            "test-key",
            maxRetries = 0,
            debug = false,
            persistencePath = path.toString(),
        )
        assertTrue(first.send(makePayload()) is SendOutcome.TransportFailure)

        val restarted = HttpTransport(
            "http://localhost:$port/events",
            "test-key",
            maxRetries = 0,
            debug = false,
            persistencePath = path.toString(),
        )
        assertEquals(SendOutcome.Success, restarted.recover())
        assertTrue(testScheduler.currentTime >= 2_500)
    }
}
