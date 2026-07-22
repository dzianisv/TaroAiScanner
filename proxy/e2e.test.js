// End-to-end integration test for the geminiProxy Cloud Function.
//
// Tier 1 (always runs, no secrets): verifies the deployed proxy's HTTP contract
//   - GET            -> 405
//   - POST no auth   -> 401
//   - POST bad auth  -> 401
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
  'https://geminiproxy-us-central1-ais-us-east5-652628ab15984c6da.cloudfunctions.net';

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

test('Tier1: GET returns 405 (POST-only)', async () => {
  const res = await fetch(PROXY_URL, { method: 'GET' });
  assert.strictEqual(res.status, 405, `expected 405, got ${res.status}`);
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
