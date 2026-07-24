const assert = require("node:assert/strict");
const { test } = require("node:test");

const {
  verifyPurchase,
  PACKAGE_NAME,
} = require("./lib/verifySubscription");

const { createVerifyHandler } = require("./index")._test;

// ---------------------------------------------------------------------------
// lib/verifySubscription.js — pure business logic, driven with a fake fetchFn
// so no secrets or network are needed.
// ---------------------------------------------------------------------------

test("PACKAGE_NAME matches the Play Store app", () => {
  assert.equal(PACKAGE_NAME, "com.aistudio.mystictarot.qxrptl");
});

test("verified=true for SUBSCRIPTION_STATE_ACTIVE", async () => {
  const result = await verifyPurchase("token-active", {
    fetchFn: async () => ({
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      latestOrderId: "GPA.1234",
      lineItems: [
        { expiryTime: "2026-08-23T00:00:00Z", productId: "mystic_tarot_premium_monthly" },
      ],
    }),
  });

  assert.equal(result.verified, true);
  assert.equal(result.subscriptionState, "SUBSCRIPTION_STATE_ACTIVE");
  assert.equal(result.latestOrderId, "GPA.1234");
  assert.equal(result.expiryTime, "2026-08-23T00:00:00Z");
  assert.equal(result.productId, "mystic_tarot_premium_monthly");
});

test("verified=true for grace period", async () => {
  const result = await verifyPurchase("token-grace", {
    fetchFn: async () => ({
      subscriptionState: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
      latestOrderId: "GPA.5678",
      lineItems: [
        { expiryTime: "2026-07-30T00:00:00Z", productId: "mystic_tarot_premium_monthly" },
      ],
    }),
  });

  assert.equal(result.verified, true);
  assert.equal(result.subscriptionState, "SUBSCRIPTION_STATE_IN_GRACE_PERIOD");
});

test("verified=false for expired subscription", async () => {
  const result = await verifyPurchase("token-expired", {
    fetchFn: async () => ({
      subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
      latestOrderId: "GPA.9999",
      lineItems: [
        { expiryTime: "2026-06-23T00:00:00Z", productId: "mystic_tarot_premium_monthly" },
      ],
    }),
  });

  assert.equal(result.verified, false);
  assert.equal(result.subscriptionState, "SUBSCRIPTION_STATE_EXPIRED");
});

test("verified=false for canceled subscription with no line items", async () => {
  const result = await verifyPurchase("token-canceled", {
    fetchFn: async () => ({
      subscriptionState: "SUBSCRIPTION_STATE_CANCELED",
      latestOrderId: null,
      lineItems: [],
    }),
  });

  assert.equal(result.verified, false);
  assert.equal(result.subscriptionState, "SUBSCRIPTION_STATE_CANCELED");
  assert.equal(result.latestOrderId, null);
  assert.equal(result.expiryTime, null);
  assert.equal(result.productId, null);
});

test("verified=false and state UNKNOWN when Play omits subscriptionState", async () => {
  const result = await verifyPurchase("token-weird", {
    fetchFn: async () => ({}),
  });

  assert.equal(result.verified, false);
  assert.equal(result.subscriptionState, "UNKNOWN");
});

// ---------------------------------------------------------------------------
// verifySubscription HTTP handler — auth, validation, and success/error paths.
// ---------------------------------------------------------------------------

function responseRecorder() {
  return {
    body: undefined,
    headers: {},
    statusCode: 200,
    set(name, value) {
      this.headers[name] = value;
      return this;
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(body) {
      this.body = body;
      return this;
    },
    send(body) {
      this.body = body;
      return this;
    },
  };
}

function verifyDeps(overrides = {}) {
  return {
    env: { GOOGLE_WEB_CLIENT_ID: "web-client" },
    logger: { error() {}, warn() {} },
    verifyFirebaseToken: async () => ({ uid: "firebase-user" }),
    verifyGoogleToken: async () => ({ sub: "google-user" }),
    verifyPurchase: async () => ({
      verified: true,
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      latestOrderId: "GPA.1",
      expiryTime: "2026-08-23T00:00:00Z",
      productId: "mystic_tarot_premium_monthly",
    }),
    ...overrides,
  };
}

function verifyRequest(overrides = {}) {
  return {
    method: "POST",
    headers: { authorization: "Bearer valid-token" },
    body: { purchaseToken: "tok-123", productId: "mystic_tarot_premium_monthly" },
    ...overrides,
  };
}

async function invokeVerify(req, deps = {}) {
  const res = responseRecorder();
  await createVerifyHandler(verifyDeps(deps))(req, res);
  return res;
}

test("OPTIONS preflight returns 204", async () => {
  const res = await invokeVerify(verifyRequest({ method: "OPTIONS" }));
  assert.equal(res.statusCode, 204);
});

test("non-POST returns 405", async () => {
  const res = await invokeVerify(verifyRequest({ method: "GET" }));
  assert.equal(res.statusCode, 405);
});

test("missing Bearer token returns 401", async () => {
  const res = await invokeVerify(verifyRequest({ headers: {} }));
  assert.equal(res.statusCode, 401);
});

test("invalid token returns 401", async () => {
  const res = await invokeVerify(verifyRequest(), {
    verifyFirebaseToken: async () => {
      throw new Error("bad");
    },
    verifyGoogleToken: async () => {
      throw new Error("bad");
    },
  });
  assert.equal(res.statusCode, 401);
});

test("missing purchaseToken returns 400", async () => {
  const res = await invokeVerify(verifyRequest({ body: { productId: "x" } }));
  assert.equal(res.statusCode, 400);
});

test("valid request returns the verification result", async () => {
  const res = await invokeVerify(verifyRequest());
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.verified, true);
  assert.equal(res.body.subscriptionState, "SUBSCRIPTION_STATE_ACTIVE");
  assert.equal(res.body.productId, "mystic_tarot_premium_monthly");
});

test("verified=false is passed through with 200 (client revokes entitlement)", async () => {
  const res = await invokeVerify(verifyRequest(), {
    verifyPurchase: async () => ({
      verified: false,
      subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
      latestOrderId: null,
      expiryTime: null,
      productId: null,
    }),
  });
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.verified, false);
});

test("Play API failure returns 502 without leaking details", async () => {
  const res = await invokeVerify(verifyRequest(), {
    verifyPurchase: async () => {
      throw new Error("play 403 secret detail");
    },
  });
  assert.equal(res.statusCode, 502);
  assert.equal(res.body.error.message, "Failed to verify subscription with Google Play.");
});
