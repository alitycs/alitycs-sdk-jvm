package com.alitycs.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Outcome of a batch send attempt. The transport never swallows failures silently. */
sealed class SendOutcome {
    /** Batch was accepted by the server (2xx). */
    object Success : SendOutcome()

    /**
     * Server definitively refused the payload (HTTP 4xx other than 429).
     * [isBatchReject] is true when the server rejected the whole batch (HTTP 400),
     * which may be caused by a single invalid event.
     */
    data class Rejected(val status: Int, val isBatchReject: Boolean) : SendOutcome()

    /** Transient failure: transport error, timeout, 429/5xx after all retries. */
    data class TransportFailure(
        val cause: Exception? = null,
        /** Absolute epoch-millisecond deadline supplied by the final 429, if any. */
        val retryAfterUntilMs: Long? = null,
        /** True when the exact request body is already owned by the durable WAL. */
        val retained: Boolean = false,
    ) : SendOutcome()
}

class HttpTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val maxRetries: Int,
    private val debug: Boolean,
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    persistencePath: String? = null,
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build()
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }
    private val durableStore = FileBatchStore(persistencePath)

    suspend fun send(payload: BatchPayload): SendOutcome {
        val body = json.encodeToString(BatchPayload.serializer(), payload)
        val record = DurableBatchRecord(payload.batchId, body, payload.events.size)
        durableStore.put(record)
        return sendRecord(record)
    }

    /** Replays every unacknowledged batch exactly as serialized before the first attempt. */
    suspend fun recover(): SendOutcome {
        for (record in durableStore.snapshot()) {
            val remaining = (record.pausedUntilMs ?: 0L) - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            val outcome = sendRecord(record)
            if (outcome is SendOutcome.TransportFailure) return outcome
        }
        return SendOutcome.Success
    }

    val durablePendingEvents: Int get() = durableStore.pendingEvents()
    val durableEnabled: Boolean get() = durableStore.enabled

    private suspend fun sendRecord(record: DurableBatchRecord): SendOutcome {
        val body = record.body
        var lastError: Exception? = null
        var retryAfterMs: Long? = null
        var retryAfterUntilMs: Long? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                // A 429's Retry-After (delta-seconds or HTTP-date) replaces the default
                // backoff for the attempt that follows it, still capped at 10s.
                val delayMs = retryAfterMs?.coerceAtMost(MAX_BACKOFF_MS)
                    ?: minOf(1000L * (1L shl (attempt - 1)), MAX_BACKOFF_MS)
                retryAfterMs = null
                delay(delayMs)
            }

            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $apiKey")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val status = response.statusCode()

                if (status in 200..299) {
                    durableStore.acknowledge(record.batchId)
                    return SendOutcome.Success
                }

                if (status in 400..499 && status != 429) {
                    warn("$status — not retrying")
                    durableStore.acknowledge(record.batchId)
                    return SendOutcome.Rejected(
                        status = status,
                        isBatchReject = status == HTTP_BATCH_REJECT_STATUS
                    )
                }

                if (status == 429) {
                    retryAfterMs = response.headers().firstValue("Retry-After").orElse(null)?.let { parseRetryAfterMs(it) }
                    retryAfterUntilMs = retryAfterMs?.let { System.currentTimeMillis() + it }
                }
                lastError = Exception("HTTP $status")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }

            if (debug && attempt < maxRetries) {
                System.err.println("[Alitycs] Transport: attempt ${attempt + 1} failed, retrying...")
            }
        }

        durableStore.pause(record.batchId, retryAfterUntilMs)
        warn("all retries exhausted — batch retained for restart: ${lastError?.message}")
        return SendOutcome.TransportFailure(lastError, retryAfterUntilMs, retained = durableStore.enabled)
    }

    /**
     * Parses a Retry-After header value into milliseconds: a delta-seconds integer or an
     * HTTP-date (RFC 1123). Returns null when unparseable; a date in the past yields 0.
     */
    private fun parseRetryAfterMs(value: String): Long? {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { return it.coerceAtLeast(0) * 1000 }
        return try {
            val whenParsed = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (whenParsed.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun warn(message: String) {
        // Warn-level: always surfaced, never debug-gated — dropped batches must be visible.
        System.err.println("[Alitycs] WARN Transport: $message")
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS = 10_000L
        /** Upper bound for any single retry delay, including a server-suggested Retry-After. */
        const val MAX_BACKOFF_MS = 10_000L
        private const val HTTP_BATCH_REJECT_STATUS = 400
    }
}
