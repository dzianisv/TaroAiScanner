package com.example.billing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for server-side entitlement verification. No Android
 * framework, network, or BillingClient required.
 */
class EntitlementVerifierTest {

    @Test
    fun `deriveVerifyUrl swaps the securegeminiproxy segment`() {
        val proxy = "https://securegeminiproxy-248382356220.us-central1.run.app"
        val expected = "https://verifysubscription-248382356220.us-central1.run.app"
        assertEquals(expected, HttpEntitlementVerifier.deriveVerifyUrl(proxy))
    }

    @Test
    fun `deriveVerifyUrl strips any query string before swapping`() {
        val proxy = "https://securegeminiproxy-x.run.app?model=scan-auto"
        assertEquals(
            "https://verifysubscription-x.run.app",
            HttpEntitlementVerifier.deriveVerifyUrl(proxy),
        )
    }

    @Test
    fun `deriveVerifyUrl returns null for an unknown host`() {
        assertNull(HttpEntitlementVerifier.deriveVerifyUrl("https://example.com/proxy"))
    }

    @Test
    fun `verify returns null when proxy url is blank`() = runTest {
        val verifier = HttpEntitlementVerifier(
            proxyUrlProvider = { "" },
            idTokenProvider = { "tok" },
        )
        assertNull(verifier.verify("purchase-token", BillingManager.PREMIUM_MONTHLY))
    }

    @Test
    fun `verify returns null when id token is blank`() = runTest {
        val verifier = HttpEntitlementVerifier(
            proxyUrlProvider = { "https://securegeminiproxy-x.run.app" },
            idTokenProvider = { "" },
        )
        assertNull(verifier.verify("purchase-token", BillingManager.PREMIUM_MONTHLY))
    }

    @Test
    fun `an explicit negative result is distinguishable from inconclusive`() {
        // Contract check: consumers must treat verified=false as a revoke
        // signal, and null as "keep local entitlement".
        val denied: VerificationResult? =
            VerificationResult(verified = false, subscriptionState = "SUBSCRIPTION_STATE_EXPIRED")
        val inconclusive: VerificationResult? = null

        assertTrue(denied != null && !denied.verified)
        assertNull(inconclusive)
    }
}
