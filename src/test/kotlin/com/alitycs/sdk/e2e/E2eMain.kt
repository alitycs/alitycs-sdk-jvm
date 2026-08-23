package com.alitycs.sdk.e2e

import com.alitycs.sdk.Alitycs
import com.alitycs.sdk.AlitycsConfig

fun main() {
    val apiKey = requiredEnvironment("ALITYCS_API_KEY")
    val endpoint = requiredEnvironment("ALITYCS_ENDPOINT")
    val runId = requiredEnvironment("ALITYCS_RUN_ID")
    val eventName = "sdk_jvm_track_$runId"
    val userId = "sdk-jvm-user-$runId"
    val sdk = Alitycs.init(
        AlitycsConfig(
            apiKey = apiKey,
            endpoint = endpoint,
            flushInterval = 60_000,
            flushSize = 10,
            maxRetries = 0,
            debug = false,
        )
    )

    try {
        sdk.setGlobalProperties(
            mapOf(
                "test_run_id" to runId,
                "sdk_package" to "jvm",
                "scenario" to "jvm-subprocess",
            )
        )
        sdk.identify(userId, mapOf("runtime" to "jvm"))
        sdk.track(eventName, mapOf("source" to "jvm-sdk-e2e"))
        sdk.flushBlocking()
    } finally {
        sdk.shutdownBlocking()
    }

    println("JVM SDK E2E emitted identify and $eventName")
}

private fun requiredEnvironment(name: String): String {
    return requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) {
        "$name is required"
    }
}
