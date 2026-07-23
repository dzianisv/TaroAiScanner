package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wraps Google Play Billing for the "premium_monthly" subscription.
 *
 * Lifecycle: construct once (e.g. in MainActivity), call [startConnection], and
 * [endConnection] when done. Observe [isPremium] for entitlement and [priceText]
 * for the localized price string.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "BillingManager"
        const val PREMIUM_MONTHLY = "premium_monthly"
        private const val MAX_RETRIES = 3
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isPremium = MutableStateFlow(false)
    /** True while an active premium_monthly subscription is owned. */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _priceText = MutableStateFlow<String?>(null)
    /** Localized price string from ProductDetails, e.g. "$4.99". Null until loaded. */
    val priceText: StateFlow<String?> = _priceText.asStateFlow()

    private var premiumDetails: ProductDetails? = null
    private var retryCount = 0

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            onConnected()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryCount = 0
                    onConnected()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    retryConnection()
                }
            }

            override fun onBillingServiceDisconnected() {
                retryConnection()
            }
        })
    }

    private fun retryConnection() {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            scope.launch {
                kotlinx.coroutines.delay(1000L * retryCount)
                startConnection()
            }
        }
    }

    private fun onConnected() {
        queryProductDetails()
        queryPurchases()
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val details = productDetailsList.firstOrNull { it.productId == PREMIUM_MONTHLY }
            premiumDetails = details
            _priceText.value = details
                ?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice
        }
    }

    /** Restore entitlement by querying currently owned SUBS purchases. */
    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                var owned = false
                for (purchase in purchases) {
                    if (isActivePremium(purchase)) {
                        owned = true
                        handlePurchase(purchase)
                    }
                }
                _isPremium.value = owned
            }
        }
    }

    /** Launch the Play purchase flow for premium_monthly. Returns false if unavailable. */
    fun launchBillingFlow(activity: Activity): Boolean {
        val details = premiumDetails ?: run {
            Log.w(TAG, "launchBillingFlow: product details not loaded")
            startConnection()
            return false
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.w(TAG, "launchBillingFlow: no offer token")
            return false
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            var owned = false
            for (purchase in purchases) {
                if (isActivePremium(purchase)) {
                    owned = true
                    handlePurchase(purchase)
                }
            }
            if (owned) _isPremium.value = true
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Purchase canceled by user")
        } else {
            Log.w(TAG, "onPurchasesUpdated error: ${result.debugMessage}")
        }
    }

    private fun isActivePremium(purchase: Purchase): Boolean =
        purchase.products.contains(PREMIUM_MONTHLY) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        _isPremium.value = true
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }

    fun endConnection() {
        if (billingClient.isReady) billingClient.endConnection()
    }
}
