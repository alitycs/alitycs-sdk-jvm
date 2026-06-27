package com.alitycs.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    @SerialName("track") TRACK,
    @SerialName("identify") IDENTIFY,
    @SerialName("page") PAGE
}

@Serializable
data class EventContext(
    val sdkVersion: String,
    val sdkLanguage: String,
    val locale: String? = null,
    val timezone: String? = null,
    val userAgent: String? = null,
    val url: String? = null,
    val referrer: String? = null,
    val screen: Map<String, String>? = null,
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val jvmVersion: String? = null
)

@Serializable
data class AnalyticsEvent(
    val eventId: String,
    val event: String,
    val eventType: EventType,
    val userId: String? = null,
    val anonymousId: String,
    val sessionId: String,
    val timestamp: Long,
    val properties: Map<String, String>,
    val context: EventContext
)

@Serializable
data class BatchPayload(
    val batchId: String,
    val sentAt: Long,
    val events: List<AnalyticsEvent>
)

data class SessionData(
    val id: String,
    val anonymousId: String,
    var userId: String? = null,
    val startTime: Long,
    var lastActivity: Long
)
