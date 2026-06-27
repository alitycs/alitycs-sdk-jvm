package com.alitycs.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val json = Json { encodeDefaults = true }

fun generateId(): String = java.util.UUID.randomUUID().toString()

fun serializeProperties(props: Map<String, Any?>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for ((key, value) in props) {
        when (value) {
            null -> continue
            is String -> result[key] = value
            is Number, is Boolean -> result[key] = value.toString()
            is Map<*, *> -> result[key] = json.encodeToString(JsonElement.serializer(), toJsonElement(value))
            is Collection<*> -> result[key] = json.encodeToString(JsonElement.serializer(), toJsonElement(value))
            else -> result[key] = value.toString()
        }
    }
    return result
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
