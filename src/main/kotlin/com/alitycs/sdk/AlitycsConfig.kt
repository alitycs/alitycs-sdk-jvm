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
    val batching: Boolean = true
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey is required" }
    }
}
