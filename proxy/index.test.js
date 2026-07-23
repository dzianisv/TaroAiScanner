const assert = require("node:assert/strict");
const { test } = require("node:test");

const {
  createHandler,
  isRetryableProviderError,
  normalizeContents,
  normalizeModelAlias,
  validateRequestBody,
} = require("./index")._test;

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

function request(overrides = {}) {
  return {
    method: "POST",
    headers: { authorization: "Bearer valid-token" },
    query: { model: "scan-auto" },
    body: {
      contents: [{ parts: [{ text: "Read this card" }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.7 },
    },
    ...overrides,
  };
}

function dependencies(overrides = {}) {
  return {
    env: {
      GCLOUD_PROJECT: "test-project",
      VERTEX_LOCATION: "global",
      GENAI_MODEL: "configured-model",
      GEMINI_API_KEY: "server-secret-key",
    },
    logger: { error() {}, warn() {} },
    verifyFirebaseToken: async () => ({ uid: "firebase-user" }),
    verifyGoogleToken: async () => ({ sub: "google-user" }),
    createVertexClient: () => ({
      models: { generateContent: async () => ({ candidates: [{ content: {} }] }) },
    }),
    createDeveloperClient: () => ({
      models: { generateContent: async () => ({ candidates: [{ fallback: true }] }) },
    }),
    ...overrides,
  };
}

async function invoke(req, deps = {}) {
  const res = responseRecorder();
  await createHandler(dependencies(deps))(req, res);
  return res;
}

test("GET returns the production health contract", async () => {
  const res = await invoke(request({ method: "GET", headers: {}, query: {}, body: undefined }));
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, { status: "ok", service: "taro-secure-gemini-proxy" });
});

test("OPTIONS returns CORS preflight and unsupported methods return 405", async () => {
  const preflight = await invoke(request({ method: "OPTIONS", headers: {}, query: {}, body: undefined }));
  assert.equal(preflight.statusCode, 204);
  assert.equal(preflight.headers["Access-Control-Allow-Headers"], "Authorization, Content-Type");

  const put = await invoke(request({ method: "PUT" }));
  assert.equal(put.statusCode, 405);
  assert.deepEqual(put.body, { error: { message: "Method not allowed." } });
});

test("missing and malformed Bearer tokens remain 401", async () => {
  const missing = await invoke(request({ headers: {} }));
  assert.equal(missing.statusCode, 401);
  assert.match(missing.body.error.message, /missing Bearer/i);

  const malformed = await invoke(request({ headers: { authorization: "Bearer token with spaces" } }));
  assert.equal(malformed.statusCode, 401);
});

test("Google token fallback requires a configured strict audience", async () => {
  let googleCalls = 0;
  const noAudience = await invoke(request(), {
    env: { GCLOUD_PROJECT: "test-project" },
    verifyFirebaseToken: async () => { throw new Error("not Firebase"); },
    verifyGoogleToken: async () => { googleCalls += 1; },
  });
  assert.equal(noAudience.statusCode, 401);
  assert.equal(googleCalls, 0);

  const audiences = [];
  const strictAudience = await invoke(request(), {
    env: { GCLOUD_PROJECT: "test-project", GOOGLE_WEB_CLIENT_ID: "web-client-id" },
    verifyFirebaseToken: async () => { throw new Error("not Firebase"); },
    verifyGoogleToken: async (_token, audience) => {
      audiences.push(audience);
      return { sub: "google-user" };
    },
  });
  assert.equal(strictAudience.statusCode, 200);
  assert.deepEqual(audiences, ["web-client-id"]);
});

test("model aliases are allowlisted and all use GENAI_MODEL", async () => {
  assert.equal(normalizeModelAlias("scan-auto s"), "scan-auto+s");
  assert.equal(normalizeModelAlias("scan-auto+s"), "scan-auto+s");

  const models = [];
  const allowed = await invoke(request({ query: { model: "scan-auto s" } }), {
    createVertexClient: () => ({
      models: {
        generateContent: async (input) => {
          models.push(input.model);
          return { candidates: [] };
        },
      },
    }),
  });
  assert.equal(allowed.statusCode, 200);
  assert.deepEqual(models, ["configured-model"]);

  const arbitrary = await invoke(request({ query: { model: "gemini-expensive-preview" } }));
  assert.equal(arbitrary.statusCode, 400);
});

test("raw request config is translated to SDK config and raw response is returned", async () => {
  const calls = [];
  const upstream = { candidates: [{ content: { parts: [{ text: "{}" }] } }], usageMetadata: {} };
  const res = await invoke(request(), {
    createVertexClient: (project, location) => {
      assert.equal(project, "test-project");
      assert.equal(location, "global");
      return {
        models: {
          generateContent: async (input) => {
            calls.push(input);
            return upstream;
          },
        },
      };
    },
  });
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, upstream);
  // Vertex requires a role on each content entry; the proxy injects role:"user"
  // for parts-only bodies (the Taro client shape) before forwarding.
  assert.deepEqual(calls[0].contents, [{ role: "user", parts: [{ text: "Read this card" }] }]);
  assert.deepEqual(calls[0].config, request().body.generationConfig);
});

test("parts-only contents get role:user injected; existing roles preserved", () => {
  assert.deepEqual(
    normalizeContents([{ parts: [{ text: "hi" }] }]),
    [{ role: "user", parts: [{ text: "hi" }] }],
  );
  assert.deepEqual(
    normalizeContents([{ role: "model", parts: [{ text: "hi" }] }]),
    [{ role: "model", parts: [{ text: "hi" }] }],
  );
  assert.deepEqual(
    normalizeContents([
      { parts: [{ text: "a" }] },
      { role: "model", parts: [{ text: "b" }] },
    ]),
    [
      { role: "user", parts: [{ text: "a" }] },
      { role: "model", parts: [{ text: "b" }] },
    ],
  );
});

test("validation preserves multimodal parts and rejects unsafe configuration", () => {
  const multimodal = request({
    body: {
      contents: [{
        role: "user",
        parts: [
          { text: "Identify" },
          { inlineData: { mimeType: "image/jpeg", data: "aGVsbG8=" } },
        ],
      }],
      generationConfig: { responseMimeType: "text/plain", temperature: 0 },
    },
  });
  assert.equal(validateRequestBody(multimodal), null);
  assert.match(validateRequestBody(request({ body: { contents: [] } })), /non-empty/);
  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ text: "x" }] }], generationConfig: { responseMimeType: "text/html" } },
  })), /responseMimeType/);
  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ text: "x" }] }], generationConfig: { temperature: 2.1 } },
  })), /temperature/);
  assert.match(validateRequestBody(request({
    headers: { "content-length": String(8 * 1024 * 1024 + 1) },
  })), /8 MB/);
});

test("Developer API fallback runs only for retryable Vertex failures", async () => {
  let fallbackCalls = 0;
  const fallbackDeps = (vertexError) => ({
    createVertexClient: () => ({
      models: { generateContent: async () => { throw vertexError; } },
    }),
    createDeveloperClient: (apiKey) => {
      assert.equal(apiKey, "server-secret-key");
      fallbackCalls += 1;
      return { models: { generateContent: async () => ({ candidates: [{ fallback: true }] }) } };
    },
  });

  const quota = await invoke(request(), fallbackDeps({ status: 429 }));
  assert.equal(quota.statusCode, 200);
  assert.equal(fallbackCalls, 1);

  const validation = await invoke(request(), fallbackDeps({ status: 400 }));
  assert.equal(validation.statusCode, 502);
  assert.equal(fallbackCalls, 1);
  assert.deepEqual(validation.body, { error: { message: "AI service is temporarily unavailable." } });
});

test("provider retry classification covers availability but not auth or validation", () => {
  assert.equal(isRetryableProviderError({ status: 503 }), true);
  assert.equal(isRetryableProviderError({ code: "RESOURCE_EXHAUSTED" }), true);
  assert.equal(isRetryableProviderError({ response: { status: 408 } }), true);
  assert.equal(isRetryableProviderError({ status: 400 }), false);
  assert.equal(isRetryableProviderError({ status: 401 }), false);
  assert.equal(isRetryableProviderError({ status: 403 }), false);
});

test("CORS headers are set on POST success and error responses", async () => {
  const success = await invoke(request());
  assert.equal(success.headers["Access-Control-Allow-Origin"], "*");
  assert.equal(success.headers["Access-Control-Allow-Methods"], "GET, POST, OPTIONS");

  const error = await invoke(request({ headers: {} }));
  assert.equal(error.headers["Access-Control-Allow-Origin"], "*");
});

test("provider retry classification handles @google/genai error shapes", () => {
  assert.equal(isRetryableProviderError({ status: 500 }), true);
  assert.equal(isRetryableProviderError({ code: 14 }), true);
  assert.equal(isRetryableProviderError({ code: 8 }), true);
  assert.equal(isRetryableProviderError({ status: "UNAVAILABLE" }), true);
  assert.equal(isRetryableProviderError({ status: "RESOURCE_EXHAUSTED" }), true);
  assert.equal(isRetryableProviderError({ code: 400 }), false);
  assert.equal(isRetryableProviderError({ code: "INVALID_ARGUMENT" }), false);
  assert.equal(isRetryableProviderError(null), false);
  assert.equal(isRetryableProviderError(undefined), false);
});

test("missing GCLOUD_PROJECT returns 502 with error log", async () => {
  const logs = [];
  const res = await invoke(request(), {
    env: { GEMINI_API_KEY: "key" },
    logger: { error: (...args) => logs.push(args), warn() {} },
  });
  assert.equal(res.statusCode, 502);
  assert.deepEqual(res.body, { error: { message: "AI service is temporarily unavailable." } });
  assert.ok(logs.some(args => args.some(a => String(a).includes("GCLOUD_PROJECT"))));
});

test("inlineData body parts validate content correctly", () => {
  assert.equal(validateRequestBody(request({
    body: { contents: [{ parts: [{ text: "hi" }] }] },
  })), null);

  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ text: 42 }] }] },
  })), /text.*string/i);

  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ inlineData: { mimeType: "image/jpeg" } }] }] },
  })), /data.*non-empty/i);

  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ inlineData: { data: "aaaa" } }] }] },
  })), /mimeType.*non-empty/i);

  assert.equal(validateRequestBody(request({
    body: { contents: [{ parts: [{ inlineData: { mimeType: "image/jpeg", data: "aaaa" } }] }] },
  })), null);

  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{ inlineData: { mimeType: "text/html", data: "aaaa" } }] }] },
  })), /Unsupported.*mimeType/i);

  assert.match(validateRequestBody(request({
    body: { contents: [{ parts: [{}] }] },
  })), /text.*or.*inlineData/i);
});

test("upstream failures return opaque nested 502 without secrets", async () => {
  const logs = [];
  const secret = "do-not-leak-this-key";
  const res = await invoke(request(), {
    env: { GCLOUD_PROJECT: "test-project", GEMINI_API_KEY: secret },
    logger: { error: (...args) => logs.push(args), warn: (...args) => logs.push(args) },
    createVertexClient: () => ({
      models: { generateContent: async () => { throw new Error(`Vertex failed ${secret}`); } },
    }),
  });
  assert.equal(res.statusCode, 502);
  assert.equal(JSON.stringify(res.body).includes(secret), false);
  assert.equal(JSON.stringify(logs).includes(secret), false);
});
