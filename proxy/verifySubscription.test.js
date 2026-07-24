const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  verifyPurchase,
  PACKAGE_NAME,
  ENTITLED_STATES,
} = require("./lib/verifySubscription");

// Real Play subscriptionsv2 response shapes (trimmed to fields we read).
const ACTIVE_RESPONSE = {
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  latestOrderId: "GPA.1234-5678-9012-34567",
  lineItems: [
    {
      productId: "mystic_tarot_premium_monthly",
      expiryTime: "2026-08-23T04:15:00Z",
    },
  ],
};

const CANCELED_RESPONSE = {
  subscriptionState: "SUBSCRIPTION_STATE_CANCELED",
  latestOrderId: "GPA.0000-1111-2222-33333",
  lineItems: [{ productId: "mystic_tarot_premium_monthly", expiryTime: "2026-08-01T00:00:00Z" }],
};

test("package name matches the real Play app", () => {
  assert.equal(PACKAGE_NAME, "com.aistudio.mystictarot.qxrptl");
});

test("entitled states include ACTIVE and GRACE only", () => {
  assert.ok(ENTITLED_STATES.has("SUBSCRIPTION_STATE_ACTIVE"));
  assert.ok(ENTITLED_STATES.has("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"));
  assert.ok(!ENTITLED_STATES.has("SUBSCRIPTION_STATE_CANCELED"));
  assert.ok(!ENTITLED_STATES.has("SUBSCRIPTION_STATE_EXPIRED"));
});

test("verifyPurchase returns verified=true for an active subscription", async () => {
  const result = await verifyPurchase("token-active", {
    fetchFn: async (t) => {
      assert.equal(t, "token-active");
      return ACTIVE_RESPONSE;
    },
  });
  assert.deepEqual(result, {
    verified: true,
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    latestOrderId: "GPA.1234-5678-9012-34567",
    expiryTime: "2026-08-23T04:15:00Z",
    productId: "mystic_tarot_premium_monthly",
  });
});

test("verifyPurchase returns verified=false for a canceled subscription", async () => {
  const result = await verifyPurchase("token-cancel", {
    fetchFn: async () => CANCELED_RESPONSE,
  });
  assert.equal(result.verified, false);
  assert.equal(result.subscriptionState, "SUBSCRIPTION_STATE_CANCELED");
});

test("verifyPurchase tolerates missing lineItems", async () => {
  const result = await verifyPurchase("token-empty", {
    fetchFn: async () => ({ subscriptionState: "SUBSCRIPTION_STATE_ACTIVE" }),
  });
  assert.equal(result.verified, true);
  assert.equal(result.productId, null);
  assert.equal(result.expiryTime, null);
});

test("verifyPurchase surfaces Play API errors to the caller", async () => {
  await assert.rejects(
    () =>
      verifyPurchase("token-403", {
        fetchFn: async () => {
          const e = new Error("permission denied");
          e.code = 403;
          throw e;
        },
      }),
    /permission denied/
  );
});

// --- HTTP handler routing: /verify-subscription reuses bearer auth ---
const { _test } = require("./index");
const { createHandler } = _test;

function mockRes() {
  return {
    statusCode: 200,
    body: null,
    headers: {},
    set() {},
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    send(payload) {
      this.body = payload;
      return this;
    },
  };
}

const baseOverrides = {
  env: {},
  logger: { info() {}, warn() {}, error() {} },
  verifyFirebaseToken: async () => ({ uid: "user-123" }),
};

test("POST /verify-subscription returns entitlement result for valid token", async () => {
  const handler = createHandler({
    ...baseOverrides,
    verifyPurchase: async (t) => {
      assert.equal(t, "tok-1");
      return { verified: true, subscriptionState: "SUBSCRIPTION_STATE_ACTIVE" };
    },
  });
  const req = {
    method: "POST",
    path: "/verify-subscription",
    headers: { authorization: "Bearer good" },
    body: { purchaseToken: "tok-1" },
    query: {},
  };
  const res = mockRes();
  await handler(req, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.verified, true);
});

test("POST /verify-subscription rejects missing purchaseToken", async () => {
  const handler = createHandler({ ...baseOverrides, verifyPurchase: async () => ({}) });
  const req = {
    method: "POST",
    path: "/verify-subscription",
    headers: { authorization: "Bearer good" },
    body: {},
    query: {},
  };
  const res = mockRes();
  await handler(req, res);
  assert.equal(res.statusCode, 400);
});

test("POST /verify-subscription requires a bearer token", async () => {
  const handler = createHandler({ ...baseOverrides });
  const req = {
    method: "POST",
    path: "/verify-subscription",
    headers: {},
    body: { purchaseToken: "tok-1" },
    query: {},
  };
  const res = mockRes();
  await handler(req, res);
  assert.equal(res.statusCode, 401);
});

test("POST /verify-subscription maps Play API failure to 502", async () => {
  const handler = createHandler({
    ...baseOverrides,
    verifyPurchase: async () => {
      throw new Error("play down");
    },
  });
  const req = {
    method: "POST",
    path: "/verify-subscription",
    headers: { authorization: "Bearer good" },
    body: { purchaseToken: "tok-1" },
    query: {},
  };
  const res = mockRes();
  await handler(req, res);
  assert.equal(res.statusCode, 502);
});
