package com.example.billing

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result of a server-side entitlement check against the Play Developer API,
 * proxied through the Taro `verifySubscription` Cloud Function.
 *
 * @property verified true only when Play reports an entitled subscription
 *   state (ACTIVE or IN_GRACE_PERIOD).
 * @property subscriptionState the raw Play subscription state, for logging.
 */
data class VerificationResult(
    val verified: Boolean,
    val subscriptionState: String,
)

/**
 * Verifies a Play purchase token with the server before entitlement is trusted.
 *
 * Returning `null` means the check could not be completed (network error,
 * missing proxy URL / auth token, malformed response). Callers treat `null` as
 * "inconclusive" and MUST NOT revoke entitlement on it — only an explicit
 * [VerificationResult] with `verified == false` is proof of a bad purchase.
 * This mirrors the KineticAiCoach behavior: never punish a paying user for a
 * transient backend failure, but do trust an authoritative negative.
 */
interface EntitlementVerifier {
    suspend fun verify(purchaseToken: String, productId: String): VerificationResult?
}

/**
 * Default [EntitlementVerifier] that POSTs to the Taro proxy's
 * `verifySubscription` endpoint with the caller's Firebase ID token.
 *
 * The verify URL is derived from the configured Gemini proxy URL by swapping
 * the `securegeminiproxy` host segment for `verifysubscription`, so both
 * functions share one deployment origin and the user only configures one URL.
 *
 * @param proxyUrlProvider returns the current Gemini proxy URL (may be blank).
 * @param idTokenProvider returns a fresh Firebase ID token (may be blank).
 */
class HttpEntitlementVerifier(
    private val proxyUrlProvider: suspend () -> String,
    private val idTokenProvider: suspend () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) : EntitlementVerifier {

    override suspend fun verify(purchaseToken: String, productId: String): VerificationResult? {
        val proxyUrl = proxyUrlProvider().trim()
        val idToken = idTokenProvider().trim()
        if (proxyUrl.isEmpty() || idToken.isEmpty()) {
            // No backend configured / not signed in — cannot verify. Inconclusive.
            return null
        }

        val verifyUrl = deriveVerifyUrl(proxyUrl) ?: return null

        val payload = JSONObject()
            .put("purchaseToken", purchaseToken)
            .put("productId", productId)
            .toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(verifyUrl)
            .header("Authorization", "Bearer $idToken")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "verifySubscription HTTP ${response.code}")
                    return null
                }
                val text = response.body?.string() ?: return null
                val json = JSONObject(text)
                VerificationResult(
                    verified = json.optBoolean("verified", false),
                    subscriptionState = json.optString("subscriptionState", "UNKNOWN"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifySubscription call failed", e)
            null
        }
    }

    companion object {
        const val TAG = "EntitlementVerifier"

        /**
         * Derive the verifySubscription URL from a Gemini proxy URL. Strips any
         * query string, then replaces a `securegeminiproxy` host/path segment
         * with `verifysubscription`. Returns null if no known segment is found.
         */
        fun deriveVerifyUrl(proxyUrl: String): String? {
            val noQuery = proxyUrl.substringBefore("?")
            if (!noQuery.contains("securegeminiproxy", ignoreCase = true)) return null
            return noQuery.replace("securegeminiproxy", "verifysubscription", ignoreCase = true)
        }
    }
}
