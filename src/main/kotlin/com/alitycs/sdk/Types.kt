package com.alitycs.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    @SerialName("track") TRACK,
    @SerialName("identify") IDENTIFY,
    @SerialName("page") PAGE,
    @SerialName("error") ERROR
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
    /** Reserved: collected client-side but currently discarded by server-side ingestion. */
    val osName: String? = null,
    /** Reserved: collected client-side but currently discarded by server-side ingestion. */
    val osVersion: String? = null,
    /** Reserved: collected client-side but currently discarded by server-side ingestion. */
    val jvmVersion: String? = null
)

@Serializable
data class RevenuePayload private constructor(
    val version: Int = 1,
    val kind: String,
    val factId: String,
    val amount: String? = null,
    val currency: String,
    val customerId: String? = null,
    val subscriptionId: String? = null,
    val mrrAmount: String? = null,
    val expectedActiveSubscriptions: Int? = null,
) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun transaction(
            factId: String,
            amount: String,
            currency: String,
            customerId: String? = null,
        ): RevenuePayload =
            RevenuePayload(
                kind = "transaction",
                factId = factId,
                amount = amount,
                currency = currency,
                customerId = customerId,
            ).validated()

        @JvmStatic
        fun mrrSnapshot(
            factId: String,
            subscriptionId: String,
            customerId: String,
            mrrAmount: String,
            currency: String,
        ): RevenuePayload =
            RevenuePayload(
                kind = "mrr_snapshot",
                factId = factId,
                subscriptionId = subscriptionId,
                customerId = customerId,
                mrrAmount = mrrAmount,
                currency = currency,
            ).validated()

        @JvmStatic
        fun mrrBaselineComplete(
            factId: String,
            currency: String,
            expectedActiveSubscriptions: Int,
        ): RevenuePayload =
            RevenuePayload(
                kind = "mrr_baseline_complete",
                factId = factId,
                currency = currency,
                expectedActiveSubscriptions = expectedActiveSubscriptions,
            ).validated()
    }

    private fun validated(): RevenuePayload {
		require(factId.isNotBlank() && factId.length <= 200) {
			"Revenue factId must be between 1 and 200 characters"
		}
        require(currency.matches(Regex("^[A-Z]{3}$"))) {
            "Revenue currency must be a three-letter uppercase code"
        }
        val decimal = amount ?: mrrAmount
        require(decimal == null || decimal.matches(Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d{1,9})?$"))) {
            "Revenue amounts must be non-exponent decimal strings with at most 9 fraction digits"
        }
		require(decimal == null || decimal.toBigDecimal().precision() <= 38) {
			"Revenue amounts must not exceed 38 digits of precision"
		}
        require(kind != "mrr_snapshot" || requireNotNull(mrrAmount).toBigDecimal().signum() >= 0) {
            "MRR snapshot amount must be non-negative"
        }
        require(expectedActiveSubscriptions == null || expectedActiveSubscriptions >= 0) {
            "Expected active subscriptions must be non-negative"
        }
        return this
    }
}

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
    val revenue: RevenuePayload? = null,
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
