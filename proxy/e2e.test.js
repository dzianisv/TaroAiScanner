// End-to-end integration test for the geminiProxy Cloud Function.
//
// Tier 1 (always runs, no secrets): verifies the deployed proxy's HTTP contract
//   - GET            -> 200 health
//   - POST no auth   -> 401
//   - POST bad auth  -> 401
//
// Live contract verified 2026-07 against project taro-ai-502921:
//   * The LIVE deployed function is named `secureGeminiProxy` and answers at
//     https://securegeminiproxy-248382356220.us-central1.run.app
//       - GET            -> 200 {"status":"ok","service":"taro-secure-gemini-proxy"}
//       - POST no auth   -> 401 {"error":"Unauthorized: missing Bearer token"}
//       - POST bad auth  -> 401 {"error":"Unauthorized: invalid or expired token"}
//   * The URL baked into the Android app
//     (geminiproxy-...-ais-us-east5-...cloudfunctions.net) is DEAD (404) — a
//     real production bug tracked separately, NOT exercised by this test.
//   This repo now exports the matching `secureGeminiProxy` implementation.
//
// Tier 2 (happy path, runs only if secrets are present): obtains a real Firebase
//   ID token via Identity Toolkit signInWithPassword, then POSTs a minimal
//   generateContent body and asserts 200 + candidates. Skips gracefully if any
//   of the required secrets are missing.
//
// Uses only Node's built-in test runner (node --test) and global fetch — no new
// production dependencies. Requires Node >= 18.

const { test } = require('node:test');
const assert = require('node:assert');

const PROXY_URL =
  process.env.PROXY_URL ||
  'https://securegeminiproxy-248382356220.us-central1.run.app';

const {
  FIREBASE_WEB_API_KEY,
  TEST_USER_EMAIL,
  TEST_USER_PASSWORD,
} = process.env;

const TIER2_READY = Boolean(
  FIREBASE_WEB_API_KEY && TEST_USER_EMAIL && TEST_USER_PASSWORD
);

// ---------------------------------------------------------------------------
// Tier 1 — contract checks (no secrets)
// ---------------------------------------------------------------------------

test('Tier1: GET returns the production health contract', async () => {
  const res = await fetch(PROXY_URL, { method: 'GET' });
  assert.strictEqual(res.status, 200, `expected 200, got ${res.status}`);
  assert.deepStrictEqual(await res.json(), {
    status: 'ok',
    service: 'taro-secure-gemini-proxy',
  });
});

test('Tier1: POST without Authorization returns 401', async () => {
  const res = await fetch(PROXY_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  assert.strictEqual(res.status, 401, `expected 401, got ${res.status}`);
});

test('Tier1: POST with malformed/invalid Bearer returns 401', async () => {
  const res = await fetch(PROXY_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer garbage-not-a-real-token',
    },
    body: '{}',
  });
  assert.strictEqual(res.status, 401, `expected 401, got ${res.status}`);
});

// ---------------------------------------------------------------------------
// Tier 2 — happy path (requires secrets, else skipped)
//
// NOTE: even with valid Tier-2 secrets, this currently fails against
// taro-ai-502921 because that project's Gemini prepayment credits are depleted
// (upstream returns 429 RESOURCE_EXHAUSTED). Fund the project or use BYOK to get
// a real 200 + candidates. Model ids gemini-3.5-flash and gemini-3.6-flash were
// both verified VALID (resolve past model lookup), so the proxy's model mapping
// is fine — the blocker is billing, not the model id.
// ---------------------------------------------------------------------------

test(
  'Tier2: authenticated POST proxies generateContent and returns candidates',
  { skip: TIER2_READY ? false : 'Tier-2 secrets not set (FIREBASE_WEB_API_KEY / TEST_USER_EMAIL / TEST_USER_PASSWORD)' },
  async () => {
    // 1. Sign in to obtain a Firebase ID token.
    const signInRes = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_WEB_API_KEY}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: TEST_USER_EMAIL,
          password: TEST_USER_PASSWORD,
          returnSecureToken: true,
        }),
      }
    );
    assert.strictEqual(
      signInRes.status,
      200,
      `signInWithPassword failed: ${signInRes.status} ${await signInRes.text()}`
    );
    const { idToken } = await signInRes.json();
    assert.ok(idToken, 'no idToken returned from Identity Toolkit');

    // 2. Call the proxy with a minimal generateContent body.
    const res = await fetch(`${PROXY_URL}?model=chat-auto`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${idToken}`,
      },
      body: JSON.stringify({
        contents: [{ parts: [{ text: 'Reply with exactly: OK' }] }],
      }),
    });

    const bodyText = await res.text();
    assert.strictEqual(res.status, 200, `expected 200, got ${res.status}: ${bodyText}`);

    const data = JSON.parse(bodyText);
    assert.ok(
      Array.isArray(data.candidates) && data.candidates.length > 0,
      `expected candidates in response, got: ${bodyText.slice(0, 300)}`
    );
  }
);
