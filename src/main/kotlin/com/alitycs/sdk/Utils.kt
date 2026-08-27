package com.alitycs.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val json = Json { encodeDefaults = true }

/**
 * Canonical ingestion limits, mirroring the server-side EventValidator exactly.
 * Events violating any of these limits are rejected locally at build time: they are
 * never queued and never sent, because the server rejects an entire batch when a
 * single event violates them.
 */
object Limits {
    const val MAX_PROPERTIES_COUNT = 50
    const val MAX_PROPERTY_KEY_LENGTH = 100
    const val MAX_PROPERTY_VALUE_LENGTH = 1000
    const val MAX_EVENT_SIZE_BYTES = 64 * 1024

    /** Constant overhead the server adds to every event when estimating its size. */
    const val EVENT_SIZE_OVERHEAD = 200

    /** Timestamps below this value are seconds-scale, not epoch milliseconds. */
    const val MIN_EPOCH_MILLIS = 1_000_000_000_000L

    const val MAX_EVENT_AGE_DAYS = 7L
    const val REVENUE_VERSION = 1
    const val MAX_REVENUE_FACT_ID_LENGTH = 200
    const val REVENUE_CURRENCY_PATTERN = "^[A-Z]{3}$"
}

/** Thrown when an event violates a canonical ingestion limit and is rejected locally. */
class EventRejectedException(message: String) : IllegalArgumentException(message)

fun generateId(): String = java.util.UUID.randomUUID().toString()

fun serializeProperties(props: Map<String, Any?>): Map<String, String> {
    if (props.size > Limits.MAX_PROPERTIES_COUNT) {
        throw EventRejectedException(
            "Event rejected locally: ${props.size} properties exceeds the maximum of " +
                "${Limits.MAX_PROPERTIES_COUNT} per event"
        )
    }
    val result = mutableMapOf<String, String>()
    for ((key, value) in props) {
        if (key.length > Limits.MAX_PROPERTY_KEY_LENGTH) {
            throw EventRejectedException(
                "Event rejected locally: property key '$key' exceeds the maximum of " +
                    "${Limits.MAX_PROPERTY_KEY_LENGTH} characters"
            )
        }
        when (value) {
            null -> continue
            is String -> result[key] = value
            is Number, is Boolean -> result[key] = value.toString()
            is Map<*, *> -> result[key] = json.encodeToString(JsonElement.serializer(), toJsonElement(value))
            is Collection<*> -> result[key] = json.encodeToString(JsonElement.serializer(), toJsonElement(value))
            else -> result[key] = value.toString()
        }
        if (result.getValue(key).length > Limits.MAX_PROPERTY_VALUE_LENGTH) {
            throw EventRejectedException(
                "Event rejected locally: value for property key '$key' exceeds the maximum of " +
                    "${Limits.MAX_PROPERTY_VALUE_LENGTH} characters"
            )
        }
    }
    return result
}

/**
 * Validates a fully built event against the canonical server limits.
 * Throws [EventRejectedException] listing every violation; never mutates or truncates data.
 */
fun validateEvent(event: AnalyticsEvent) {
    val errors = mutableListOf<String>()

    if (event.event.isBlank()) {
        errors.add("action is required and cannot be blank")
    }
    if (event.userId.isNullOrBlank() && event.anonymousId.isBlank()) {
        errors.add("at least one of userId or anonymousId is required")
    }

    validateTimestamp(event.timestamp, errors)
    validateRevenuePayload(event.revenue, errors)

    if (event.properties.size > Limits.MAX_PROPERTIES_COUNT) {
        errors.add(
            "properties contains too many entries " +
                "(max ${Limits.MAX_PROPERTIES_COUNT}, got ${event.properties.size})"
        )
    }
    event.properties.forEach { (key, value) ->
        if (key.length > Limits.MAX_PROPERTY_KEY_LENGTH) {
            errors.add("property key '$key' exceeds the maximum of ${Limits.MAX_PROPERTY_KEY_LENGTH} characters")
        }
        if (value.length > Limits.MAX_PROPERTY_VALUE_LENGTH) {
            errors.add(
                "value for property key '$key' exceeds the maximum of " +
                    "${Limits.MAX_PROPERTY_VALUE_LENGTH} characters"
            )
        }
    }

    val estimatedSize =
        (event.userId?.length ?: 0) +
            event.anonymousId.length +
            event.event.length +
            event.properties.entries.sumOf { it.key.length + it.value.length } +
            Limits.EVENT_SIZE_OVERHEAD
    if (estimatedSize > Limits.MAX_EVENT_SIZE_BYTES) {
        errors.add(
            "event size (~$estimatedSize bytes) exceeds the maximum allowed size " +
                "(${Limits.MAX_EVENT_SIZE_BYTES} bytes)"
        )
    }

    if (errors.isNotEmpty()) {
        throw EventRejectedException("Event rejected locally: " + errors.joinToString("; "))
    }
}

private fun validateTimestamp(timestamp: Long, errors: MutableList<String>) {
    if (timestamp < Limits.MIN_EPOCH_MILLIS) {
        errors.add(
            "timestamp must be epoch milliseconds (got $timestamp, which looks like seconds-scale)"
        )
        return
    }
    val now = System.currentTimeMillis()
    val maxAge = now - Limits.MAX_EVENT_AGE_DAYS * 24 * 60 * 60 * 1000L
    if (timestamp > now) {
        errors.add("timestamp cannot be in the future")
    } else if (timestamp < maxAge) {
        errors.add("timestamp is too old (older than ${Limits.MAX_EVENT_AGE_DAYS} days)")
    }
}

private fun validateRevenuePayload(revenue: RevenuePayload?, errors: MutableList<String>) {
    if (revenue == null) return
    if (revenue.version != Limits.REVENUE_VERSION) errors.add("revenue.version must be 1")
    if (revenue.factId.isBlank() || revenue.factId.length > Limits.MAX_REVENUE_FACT_ID_LENGTH) {
        errors.add("revenue.factId must be between 1 and ${Limits.MAX_REVENUE_FACT_ID_LENGTH} characters")
    }
    if (!revenue.currency.matches(Regex(Limits.REVENUE_CURRENCY_PATTERN))) {
        errors.add("revenue.currency must be a three-letter uppercase currency code")
    }
    // Per-kind exclusive fields mirror the server: RevenuePayload factories already prevent
    // cross-kind fields structurally, so only kind membership is re-checked here.
    if (revenue.kind !in listOf("transaction", "mrr_snapshot", "mrr_baseline_complete")) {
        errors.add("revenue.kind is not supported")
    }
}

private fun toJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) }
        )
        is Collection<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
}
