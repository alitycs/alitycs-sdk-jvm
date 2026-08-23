package com.alitycs.sdk

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RevenuePayloadTest {
    @Test
    fun `transaction serializes as the trusted revenue contract`() {
        val payload =
            RevenuePayload.transaction(
                factId = "payment-123",
                amount = "71.123456789",
                currency = "USD",
                customerId = "customer-1",
            )
        val json = Json.encodeToString(payload)

        assertEquals(1, payload.version)
        assertEquals("transaction", payload.kind)
        assertTrue(json.contains("\"factId\":\"payment-123\""))
        assertTrue(json.contains("\"amount\":\"71.123456789\""))
        assertTrue(json.contains("\"currency\":\"USD\""))
    }

    @Test
    fun `recurring factories preserve baseline completeness semantics`() {
        val snapshot =
            RevenuePayload.mrrSnapshot(
                factId = "snapshot-1",
                subscriptionId = "subscription-1",
                customerId = "customer-1",
                mrrAmount = "10.250000000",
                currency = "EUR",
            )
        val baseline =
            RevenuePayload.mrrBaselineComplete(
                factId = "baseline-1",
                currency = "EUR",
                expectedActiveSubscriptions = 1,
            )

        assertEquals("mrr_snapshot", snapshot.kind)
        assertEquals("mrr_baseline_complete", baseline.kind)
        assertEquals(1, baseline.expectedActiveSubscriptions)
    }

    @Test
    fun `factories reject lossy or ambiguous values`() {
        assertThrows<IllegalArgumentException> {
            RevenuePayload.transaction("exponent", "1e3", "USD")
        }
        assertThrows<IllegalArgumentException> {
            RevenuePayload.transaction("fraction", "1.1234567890", "USD")
        }
        assertThrows<IllegalArgumentException> {
            RevenuePayload.transaction("currency", "1", "usd")
        }
        assertThrows<IllegalArgumentException> {
            RevenuePayload.mrrSnapshot("snapshot", "subscription", "customer", "-1", "USD")
        }
        assertThrows<IllegalArgumentException> {
            RevenuePayload.transaction("precision", "123456789012345678901234567890123456789", "USD")
        }
    }
}
