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

// Play HTTP statuses that unambiguously mean "this purchase token is not a
// valid entitlement": 400 = malformed token, 410 = token permanently gone.
// Returning an authoritative negative for these closes the fraud gap where a
// forged token errors out and the client keeps entitlement by falling back to
// "inconclusive".
//
// Deliberately NOT included: 401/403 (server ACL / Play API access not yet
// granted), 404 (also returned when packageName is wrong), and any 5xx. Those
// are configuration or transient faults — they must stay inconclusive, because
// treating a misconfiguration as "not entitled" would mass-revoke paying users.
const NOT_ENTITLED_STATUSES = new Set([400, 410]);

const INVALID_TOKEN_STATE = "INVALID_TOKEN";

/** Best-effort extraction of the HTTP status from a gaxios/Play error. */
function playErrorStatus(error) {
  if (!error) return null;
  if (error.response && typeof error.response.status === "number") {
    return error.response.status;
  }
  if (typeof error.status === "number") return error.status;
  if (typeof error.code === "number") return error.code;
  return null;
}

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
  let subscription;
  try {
    subscription = await fetchFn(purchaseToken);
  } catch (error) {
    if (NOT_ENTITLED_STATUSES.has(playErrorStatus(error))) {
      // Authoritative: Play says this token is malformed or gone.
      return {
        verified: false,
        subscriptionState: INVALID_TOKEN_STATE,
        latestOrderId: null,
        expiryTime: null,
        productId: null,
      };
    }
    // Permission / not-found / transient — let the caller report it as
    // inconclusive so entitlement is left untouched.
    throw error;
  }

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
  playErrorStatus,
  PACKAGE_NAME,
  ENTITLED_STATES,
  NOT_ENTITLED_STATUSES,
  INVALID_TOKEN_STATE,
};
