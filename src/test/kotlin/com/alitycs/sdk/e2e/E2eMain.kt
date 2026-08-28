package com.alitycs.sdk.e2e

import com.alitycs.sdk.Alitycs
import com.alitycs.sdk.AlitycsConfig
import com.alitycs.sdk.EventOptions

fun main() {
    val apiKey = requiredEnvironment("ALITYCS_API_KEY")
    val endpoint = requiredEnvironment("ALITYCS_ENDPOINT")
    val runId = requiredEnvironment("ALITYCS_RUN_ID")
    val phase = System.getenv("ALITYCS_E2E_PHASE")?.trim().orEmpty()
    val stateFile = System.getenv("ALITYCS_STATE_FILE")?.trim()?.takeIf { it.isNotEmpty() }
    val eventName = "sdk_jvm_track_$runId"
    val userId = "sdk-jvm-user-$runId"
    val sdk = Alitycs.init(
        AlitycsConfig(
            apiKey = apiKey,
            endpoint = if (phase == "first") requiredEnvironment("ALITYCS_FAILURE_ENDPOINT") else endpoint,
            flushInterval = 60_000,
            flushSize = 10,
            maxRetries = 0,
            debug = false,
            persistencePath = stateFile,
        )
    )

    if (phase == "first") {
        sdk.setGlobalProperties(
            mapOf(
                "test_run_id" to runId,
                "sdk_package" to "jvm",
                "scenario" to "jvm-restart",
            )
        )
        sdk.track("sdk_jvm_restart_$runId")
        sdk.flushBlocking()
        Runtime.getRuntime().halt(0)
    }
    if (phase == "restart") {
        sdk.flushBlocking()
        sdk.shutdownBlocking()
        return
    }

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
        sdk.track(
            "sdk_jvm_request_a_$runId",
            options = EventOptions(userId = "sdk-jvm-request-a-$runId"),
        )
        sdk.track(
            "sdk_jvm_request_b_$runId",
            options = EventOptions(userId = "sdk-jvm-request-b-$runId"),
        )
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
