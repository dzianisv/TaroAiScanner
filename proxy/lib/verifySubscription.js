/**
 * Server-side subscription verification for Mystic Tarot.
 *
 * Calls the Google Play Developer API purchases.subscriptionsv2 endpoint with
 * the runtime service account's ADC (scoped to androidpublisher). Business
 * logic is isolated here for testability; the HTTP wrapper lives in index.js.
 *
 * Ported from KineticAiCoach functions/lib/verifySubscription.js (PR #8).
 */

const PACKAGE_NAME = "com.aistudio.mystictarot.qxrptl";

const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

const ANDROID_PUBLISHER_SCOPE =
  "https://www.googleapis.com/auth/androidpublisher";

/**
 * Default implementation — calls the Play Developer API using ADC.
 * In Cloud Run / Functions this resolves to the runtime service account, which
 * must hold the androidpublisher IAM role AND be linked under Play Console
 * "API access" for this app. Otherwise this throws a 403 at runtime.
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
 * @param {{ fetchFn?: function }} [options]
 * @returns {Promise<{verified:boolean, subscriptionState:string, latestOrderId:?string, expiryTime:?string, productId:?string}>}
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
