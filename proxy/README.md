# Taro Secure Gemini Proxy

`secureGeminiProxy` is a Firebase Functions v2 HTTP handler for Node.js 22. It keeps provider credentials off the Android device and preserves the Gemini `generateContent` wire contract used by Taro:

- `GET` returns `{"status":"ok","service":"taro-secure-gemini-proxy"}`.
- `POST ?model=chat-auto` accepts raw `contents` and optional `generationConfig`.
- Successful responses are raw Gemini responses with `candidates`.
- Errors use `{"error":{"message":"..."}}`, which the Android client already parses.

## Security and Routing

- Every `POST` needs `Authorization: Bearer <Firebase ID token>`.
- Firebase Auth is the primary verifier. A standard Google ID token is accepted only when a strict `GOOGLE_WEB_CLIENT_ID` audience is configured.
- Model query values are aliases, not provider model names. Supported aliases are `chat-auto`, `describe-auto`, `scan-auto`, and their `+s` reasoning variants. All aliases resolve to `GENAI_MODEL`.
- Vertex AI with Application Default Credentials is always attempted first.
- The Gemini Developer API is attempted only after a retryable Vertex quota or availability failure and only when the `GEMINI_API_KEY` secret is available.
- Requests are limited to 8 MB after parsing. `contents` must be non-empty. `responseMimeType` is limited to `application/json` or `text/plain`, and `temperature` to `0..2`.

The function has a public IAM invoker because mobile clients do not possess Google Cloud IAM tokens. This does not make the application API anonymous: the handler rejects POST requests without a valid application ID token.

## Local Verification

From this directory:

```bash
npm ci
npm test
npm run check
npm run test:e2e
```

`npm run test:e2e` always checks the deployed Tier-1 health/auth contract. The authenticated Gemini request runs only when `FIREBASE_WEB_API_KEY`, `TEST_USER_EMAIL`, and `TEST_USER_PASSWORD` are set. Set `PROXY_URL` to test a non-default deployment.

## Google Cloud Setup

Enable the required APIs once:

```bash
gcloud services enable \
  aiplatform.googleapis.com \
  cloudbuild.googleapis.com \
  cloudfunctions.googleapis.com \
  run.googleapis.com \
  secretmanager.googleapis.com
```

Grant the function's runtime service account Vertex access:

```bash
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$RUNTIME_SERVICE_ACCOUNT" \
  --role="roles/aiplatform.user"
```

Store `GEMINI_API_KEY` in Secret Manager before deployment. Never pass a key with `--set-env-vars` or place one in a tracked file. Follow the organization's secret runbook to create or rotate the secret and grant the runtime service account access.

Deploy from `proxy/` as a second-generation Node.js 22 function:

```bash
gcloud functions deploy secureGeminiProxy \
  --gen2 \
  --runtime=nodejs22 \
  --region="$REGION" \
  --source=. \
  --entry-point=secureGeminiProxy \
  --trigger-http \
  --allow-unauthenticated \
  --service-account="$RUNTIME_SERVICE_ACCOUNT" \
  --set-env-vars="GCLOUD_PROJECT=$PROJECT_ID,VERTEX_LOCATION=global,GENAI_MODEL=gemini-3.6-flash" \
  --set-secrets="GEMINI_API_KEY=GEMINI_API_KEY:latest"
```

Add `,GOOGLE_WEB_CLIENT_ID=$GOOGLE_WEB_CLIENT_ID` to `--set-env-vars` only when Google OAuth ID-token fallback is needed. Firebase ID-token verification remains enabled without it. Pinning a numbered secret version instead of `latest` is preferable when releases require deterministic rollback.

No deployment is performed by the test or CI scripts.
