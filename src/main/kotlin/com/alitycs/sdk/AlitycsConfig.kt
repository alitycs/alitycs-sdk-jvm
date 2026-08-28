package com.alitycs.sdk

data class AlitycsConfig @JvmOverloads constructor(
    val apiKey: String,
    val endpoint: String = "https://api.alitycs.com/events",
    val flushInterval: Long = 10_000L,
    val flushSize: Int = 25,
    val maxQueueSize: Int = 1000,
    val maxRetries: Int = 3,
    val debug: Boolean = false,
    val sessionTimeout: Long = 30 * 60 * 1000L,
    val batching: Boolean = true,
    val connectTimeoutMs: Long = HttpTransport.DEFAULT_CONNECT_TIMEOUT_MS,
    val requestTimeoutMs: Long = HttpTransport.DEFAULT_REQUEST_TIMEOUT_MS,
    /** Optional file WAL for exact batch replay after process restart. */
    val persistencePath: String? = null,
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey is required" }
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(requestTimeoutMs > 0) { "requestTimeoutMs must be positive" }
        require(persistencePath == null || persistencePath.isNotBlank()) {
            "persistencePath must be non-blank when provided"
        }
    }
}
