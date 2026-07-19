# Mystic Tarot - Gemini API Proxy (GCP Cloud Functions)

This directory contains a complete, production-ready **Google Cloud Function** (the Google Cloud equivalent of AWS Lambda) that acts as a secure intermediary between your mobile application and the Gemini API.

## Why use a Proxy?
Directly calling the Gemini API from a mobile application requires embedding an API key in the client application code or configuration. If your app is decompiled or if requests are intercepted, your API key could be compromised.

By deploying this lightweight Node.js proxy on **Google Cloud Functions**, you can:
1. **Secure Your Credentials:** The `GEMINI_API_KEY` remains securely stored in GCP (environment variables or Secret Manager) and is never sent to the device.
2. **Control Rate Limits & Costs:** Add authentication, rate limiting, or payload validation inside the Cloud Function to protect your Gemini quotas.
3. **Change Models Dynamically:** Switch Gemini models (e.g., from `gemini-2.5-flash` to `gemini-2.5-pro` or newer) without needing to update and re-publish your Android app.

---

## Deployment Instructions

### Prerequisites
1. Installed [Google Cloud SDK (gcloud CLI)](https://cloud.google.com/sdk/docs/install).
2. A GCP Project with billing enabled.
3. Your Gemini API Key from Google AI Studio.

### Step 1: Initialize your GCP Project
Open your terminal inside this `/proxy` folder and run:
```bash
# Log in to your Google Account
gcloud auth login

# Set your active project ID
gcloud config set project YOUR_GCP_PROJECT_ID

# Enable the required Cloud Functions and Cloud Build APIs
gcloud services enable cloudfunctions.googleapis.com cloudbuild.googleapis.com
```

### Step 2: Deploy to Google Cloud Functions
Deploy the function using the `gcloud functions deploy` command. Replace `YOUR_ACTUAL_GEMINI_API_KEY` with your real key:

```bash
gcloud functions deploy geminiProxy \
  --runtime=nodejs18 \
  --trigger-http \
  --allow-unauthenticated \
  --region=us-central1 \
  --set-env-vars GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY \
  --entry-point=geminiProxy
```

*Note: `--allow-unauthenticated` makes the function publicly accessible so your Android app can call it. If you want, you can secure it with Firebase App Check or Custom Auth Headers.*

### Step 3: Copy your Endpoint URL
Once the deployment completes successfully, the CLI will output an `httpsTrigger` URL, for example:
`https://us-central1-your-project.cloudfunctions.net/geminiProxy`

---

## Android App Integration
Open your Mystic Tarot app, go to settings or click on the **Oracle Settings** icon in the App, and paste this URL into the **Custom Proxy URL** field. 

The Android app will automatically route all Tarot scanning requests through your secure proxy instead of calling the direct Google API, keeping your secrets perfectly secure!
