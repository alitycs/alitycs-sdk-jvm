package com.alitycs.sdk

import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HttpTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val maxRetries: Int,
    private val debug: Boolean
) {
    private val client: HttpClient = HttpClient.newHttpClient()
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun send(payload: BatchPayload) {
        val body = json.encodeToString(BatchPayload.serializer(), payload)
        var lastError: Exception? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                val delayMs = minOf(1000L * (1L shl (attempt - 1)), 10_000L)
                delay(delayMs)
            }

            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $apiKey")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val status = response.statusCode()

                if (status in 200..299) return

                if (status in 400..499 && status != 429) {
                    if (debug) {
                        System.err.println("[Alitycs] Transport: $status — not retrying")
                    }
                    return
                }

                lastError = Exception("HTTP $status")
            } catch (e: Exception) {
                lastError = e
            }

            if (debug && attempt < maxRetries) {
                System.err.println("[Alitycs] Transport: attempt ${attempt + 1} failed, retrying...")
            }
        }

        if (debug && lastError != null) {
            System.err.println("[Alitycs] Transport: all retries exhausted — dropping batch: ${lastError.message}")
        }
    }
}
