const { GoogleGenAI } = require("@google/genai");
const admin = require("firebase-admin");
const { getApps, initializeApp } = require("firebase-admin/app");
const { defineSecret } = require("firebase-functions/params");
const { onRequest } = require("firebase-functions/v2/https");
const { OAuth2Client } = require("google-auth-library");

if (!getApps().length) {
  const projectId =
    process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
  initializeApp(projectId ? { projectId } : undefined);
}

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const oauth2Client = new OAuth2Client();
const MAX_REQUEST_BYTES = 8 * 1024 * 1024;
const ALLOWED_MODEL_ALIASES = new Set([
  "chat-auto",
  "chat-auto+s",
  "describe-auto",
  "describe-auto+s",
  "scan-auto",
  "scan-auto+s",
]);
const RETRYABLE_HTTP_STATUSES = new Set([408, 429, 500, 502, 503, 504]);
const GRPC_RETRYABLE_CODES = new Set([4, 8, 10, 13, 14]);
const RETRYABLE_PROVIDER_CODES = new Set([
  "ABORTED",
  "DEADLINE_EXCEEDED",
  "ECONNRESET",
  "ETIMEDOUT",
  "INTERNAL",
  "RESOURCE_EXHAUSTED",
  "UNAVAILABLE",
]);

function nestedError(message) {
  return { error: { message } };
}

function normalizeModelAlias(value) {
  if (value === undefined) return "chat-auto";
  if (typeof value !== "string") return null;

  const alias = value.trim();
  // Express decodes a literal '+' in a query string as a space.
  return alias.endsWith(" s") ? `${alias.slice(0, -2)}+s` : alias;
}

const ALLOWED_MIME_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
  "image/heic",
  "image/heif",
  "application/pdf",
]);

function validateRequestBody(req) {
  const contentLength = Number(req.headers && req.headers["content-length"]);
  if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
    return "Request body exceeds the 8 MB limit.";
  }

  const body = req.body;
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return "Request body must be a JSON object.";
  }

  let serializedBytes;
  try {
    serializedBytes = Buffer.byteLength(JSON.stringify(body), "utf8");
  } catch {
    return "Request body must be valid JSON.";
  }
  const rawBytes = Buffer.isBuffer(req.rawBody) ? req.rawBody.length : 0;
  if (Math.max(serializedBytes, rawBytes) > MAX_REQUEST_BYTES) {
    return "Request body exceeds the 8 MB limit.";
  }

  if (!Array.isArray(body.contents) || body.contents.length === 0) {
    return "'contents' must be a non-empty array.";
  }
  if (body.contents.some((content) => (
    !content || typeof content !== "object" ||
    !Array.isArray(content.parts) || content.parts.length === 0
  ))) {
    return "Each content item must contain a non-empty 'parts' array.";
  }

  for (const content of body.contents) {
    for (const part of content.parts) {
      if (part.inlineData !== undefined) {
        if (!part.inlineData || typeof part.inlineData !== "object") {
          return "'inlineData' must be an object.";
        }
        if (typeof part.inlineData.mimeType !== "string" || !part.inlineData.mimeType) {
          return "'inlineData.mimeType' must be a non-empty string.";
        }
        if (!ALLOWED_MIME_TYPES.has(part.inlineData.mimeType)) {
          return `Unsupported 'inlineData.mimeType': ${part.inlineData.mimeType}.`;
        }
        if (typeof part.inlineData.data !== "string" || !part.inlineData.data) {
          return "'inlineData.data' must be a non-empty base64 string.";
        }
      } else if (part.text !== undefined) {
        if (typeof part.text !== "string") {
          return "'text' must be a string.";
        }
      } else {
        return "Each part must contain 'text' or 'inlineData'.";
      }
    }
  }

  if (body.generationConfig !== undefined) {
    const config = body.generationConfig;
    if (!config || typeof config !== "object" || Array.isArray(config)) {
      return "'generationConfig' must be an object.";
    }
    if (
      config.responseMimeType !== undefined &&
      config.responseMimeType !== "application/json" &&
      config.responseMimeType !== "text/plain"
    ) {
      return "'responseMimeType' must be 'application/json' or 'text/plain'.";
    }
    if (
      config.temperature !== undefined &&
      (typeof config.temperature !== "number" ||
        !Number.isFinite(config.temperature) ||
        config.temperature < 0 || config.temperature > 2)
    ) {
      return "'temperature' must be a number between 0 and 2.";
    }
  }

  return null;
}

function providerStatus(error) {
  const candidates = [
    error && error.status,
    error && error.code,
    error && error.statusCode,
    error && error.httpStatusCode,
    error && error.response && error.response.status,
  ];
  const status = candidates.map(Number).find(Number.isFinite);
  return status || null;
}

function providerCode(error) {
  const code = error && (error.code || (error.error && error.error.status));
  return typeof code === "string" ? code.toUpperCase() : null;
}

function isRetryableProviderError(error) {
  const status = providerStatus(error);
  if (status !== null && status >= 100) return RETRYABLE_HTTP_STATUSES.has(status);

  if (!error) return false;

  const raw = error.code !== undefined ? error.code : (error.error && error.error.status);
  if (typeof raw === "string" && RETRYABLE_PROVIDER_CODES.has(raw.toUpperCase())) return true;
  if (typeof raw === "number" && GRPC_RETRYABLE_CODES.has(raw)) return true;

  const statusStr = error.status;
  if (typeof statusStr === "string" && RETRYABLE_PROVIDER_CODES.has(statusStr.toUpperCase())) return true;

  return false;
}

async function authenticate(token, dependencies) {
  try {
    return await dependencies.verifyFirebaseToken(token);
  } catch {
    const audience = dependencies.env.GOOGLE_WEB_CLIENT_ID;
    if (!audience) throw new Error("invalid token");

    const payload = await dependencies.verifyGoogleToken(token, audience);
    if (!payload || typeof payload.sub !== "string" || !payload.sub) {
      throw new Error("invalid token");
    }
    return payload;
  }
}

async function generate(client, model, body) {
  return client.models.generateContent({
    model,
    contents: body.contents,
    config: body.generationConfig,
  });
}

function productionDependencies() {
  return {
    env: process.env,
    logger: console,
    verifyFirebaseToken: (token) => admin.auth().verifyIdToken(token),
    verifyGoogleToken: async (token, audience) => {
      const ticket = await oauth2Client.verifyIdToken({ idToken: token, audience });
      return ticket.getPayload();
    },
    createVertexClient: (project, location) => new GoogleGenAI({
      vertexai: true,
      project,
      location,
    }),
    createDeveloperClient: (apiKey) => new GoogleGenAI({ apiKey }),
  };
}

function createHandler(overrides = {}) {
  const dependencies = { ...productionDependencies(), ...overrides };

  return async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.set("Access-Control-Max-Age", "3600");

    if (req.method === "OPTIONS") return res.status(204).send("");
    if (req.method === "GET") {
      return res.status(200).json({
        status: "ok",
        service: "taro-secure-gemini-proxy",
      });
    }
    if (req.method !== "POST") {
      return res.status(405).json(nestedError("Method not allowed."));
    }

    const authorization = req.headers && req.headers.authorization;
    const bearer = typeof authorization === "string"
      ? authorization.match(/^Bearer ([^\s]+)$/)
      : null;
    if (!bearer) {
      return res.status(401).json(nestedError("Unauthorized: missing Bearer token."));
    }

    try {
      await authenticate(bearer[1], dependencies);
    } catch {
      return res.status(401).json(nestedError("Unauthorized: invalid or expired token."));
    }

    const alias = normalizeModelAlias(req.query && req.query.model);
    if (!alias || !ALLOWED_MODEL_ALIASES.has(alias)) {
      return res.status(400).json(nestedError("Unsupported model alias."));
    }

    const validationError = validateRequestBody(req);
    if (validationError) {
      return res.status(400).json(nestedError(validationError));
    }

    const model = dependencies.env.GENAI_MODEL || "gemini-3.6-flash";
    const project = dependencies.env.GCLOUD_PROJECT;
    const location = dependencies.env.VERTEX_LOCATION || "global";
    if (!project) {
      dependencies.logger.error("Gemini proxy is missing GCLOUD_PROJECT.");
      return res.status(502).json(nestedError("AI service is temporarily unavailable."));
    }

    try {
      const vertexClient = dependencies.createVertexClient(project, location);
      const response = await generate(vertexClient, model, req.body);
      return res.status(200).json(response);
    } catch (vertexError) {
      const retryable = isRetryableProviderError(vertexError);
      dependencies.logger.warn("Vertex Gemini request failed.", {
        retryable,
        status: providerStatus(vertexError) || "unknown",
      });

      const apiKey = dependencies.env.GEMINI_API_KEY;
      if (!retryable || !apiKey) {
        return res.status(502).json(nestedError("AI service is temporarily unavailable."));
      }

      try {
        const developerClient = dependencies.createDeveloperClient(apiKey);
        const response = await generate(developerClient, model, req.body);
        return res.status(200).json(response);
      } catch (developerError) {
        dependencies.logger.warn("Gemini Developer API fallback failed.", {
          status: providerStatus(developerError) || "unknown",
        });
        return res.status(502).json(nestedError("AI service is temporarily unavailable."));
      }
    }
  };
}

exports.secureGeminiProxy = onRequest(
  {
    cors: true,
    timeoutSeconds: 60,
    secrets: [geminiApiKey],
  },
  createHandler(),
);

Object.defineProperty(exports, "_test", {
  value: {
    createHandler,
    isRetryableProviderError,
    normalizeModelAlias,
    validateRequestBody,
  },
});
