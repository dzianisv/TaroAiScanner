/**
 * Server-side subscription verification logic, separated from the HTTP
 * wrapper (index.js) for testability.
 *
 * Mirrors the KineticAiCoach verifySubscription pattern: it asks the Google
 * Play Developer API whether a purchaseToken maps to a genuinely entitled
 * subscription, instead of trusting the client's local acknowledge/state check.
 *
 * The caller (index.js) may inject its own fetchFn; the default uses
 * google-auth-library + the Play Developer API via ADC (Application Default
 * Credentials). In Cloud Functions/Run this resolves to the runtime service
 * account, which must have the "androidpublisher" role in Google Cloud IAM and
 * "Enable API access" linked in the Play Console for this app.
 */

const PACKAGE_NAME = "com.aistudio.mystictarot.qxrptl";

// States that grant entitlement. Grace period keeps a lapsed-but-recoverable
// payer entitled while Play retries billing; everything else (expired,
// canceled, on-hold, paused, pending, unknown) is treated as not entitled.
const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

const ANDROID_PUBLISHER_SCOPE =
  "https://www.googleapis.com/auth/androidpublisher";

/**
 * Default implementation — calls the Google Play Developer API using ADC.
 * Endpoint: purchases.subscriptionsv2.get.
 */
async function fetchSubscriptionFromPlay(purchaseToken) {
  const { GoogleAuth } = require("google-auth-library");
  const auth = new GoogleAuth({ scopes: [ANDROID_PUBLISHER_SCOPE] });
  const client = await auth.getClient();
  const url =
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
    PACKAGE_NAME +
    "/purchases/subscriptionsv2/tokens/" +
    encodeURIComponent(purchaseToken);
  const response = await client.request({ url });
  return response.data;
}

/**
 * Verify a purchase token. Accepts an optional fetchFn override for testing.
 *
 * @param {string} purchaseToken
 * @param {object} [options]
 * @param {function} [options.fetchFn] async function that calls the Play API
 * @returns {Promise<{verified:boolean, subscriptionState:string, latestOrderId:(string|null), expiryTime:(string|null), productId:(string|null)}>}
 */
async function verifyPurchase(
  purchaseToken,
  { fetchFn = fetchSubscriptionFromPlay } = {}
) {
  const subscription = await fetchFn(purchaseToken);
  const state = subscription.subscriptionState || "UNKNOWN";
  const verified = ENTITLED_STATES.has(state);
  const lineItem = subscription.lineItems && subscription.lineItems[0];

  return {
    verified,
    subscriptionState: state,
    latestOrderId: subscription.latestOrderId || null,
    expiryTime: (lineItem && lineItem.expiryTime) || null,
    productId: (lineItem && lineItem.productId) || null,
  };
}

module.exports = {
  verifyPurchase,
  fetchSubscriptionFromPlay,
  PACKAGE_NAME,
  ENTITLED_STATES,
};
