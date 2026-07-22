const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const axios = require("axios");

// Initialize Firebase Admin SDK
admin.initializeApp();

/**
 * Secure Firebase Cloud Function acting as a proxy to the Gemini API.
 * Requires a valid Firebase Auth ID Token in the Authorization header.
 */
exports.geminiProxy = onRequest({ cors: true, timeoutSeconds: 60 }, async (req, res) => {
  // 1. Only support POST requests
  if (req.method !== 'POST') {
    return res.status(405).json({
      error: { message: 'Only POST requests are supported.' }
    });
  }

  // 2. Validate Authorization Header (Firebase Auth ID Token)
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    console.warn('Unauthorized request: Missing or malformed Authorization header.');
    return res.status(401).json({
      error: { message: 'Unauthorized: Missing or malformed Firebase Auth credentials.' }
    });
  }

  const idToken = authHeader.split('Bearer ')[1];
  try {
    // Verify the ID Token using Firebase Admin SDK
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    console.log(`Authenticated request from user UID: ${decodedToken.uid}`);
  } catch (authError) {
    console.error('Authentication verification failed:', authError.message);
    return res.status(401).json({
      error: { message: `Unauthorized: Invalid Firebase Auth credentials. ${authError.message}` }
    });
  }

  // 3. Retrieve the Gemini API Key from server environment / secrets
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error('CRITICAL: GEMINI_API_KEY environment variable is missing on the server.');
    return res.status(500).json({
      error: { message: 'Proxy configuration error: API Key is missing on the server.' }
    });
  }

  // 4. Retrieve model alias from query parameter and map to concrete model
  const MODEL_MAPPING = {
    "chat-auto": "gemini-3.6-flash",
    "describe-auto": "gemini-3.6-flash",
    "scan-auto": "gemini-3.6-flash"
  };

  const requestedModel = req.query.model || 'chat-auto';
  const targetModel = MODEL_MAPPING[requestedModel] || requestedModel || 'gemini-3.6-flash';
  const targetUrl = `https://generativelanguage.googleapis.com/v1beta/models/${targetModel}:generateContent?key=${apiKey}`;

  try {
    console.log(`Forwarding authenticated request to Gemini API (usecase: ${requestedModel} -> model: ${targetModel})`);
    
    const response = await axios.post(targetUrl, req.body, {
      headers: {
        'Content-Type': 'application/json'
      },
      timeout: 60000 // 60 seconds timeout
    });

    // Relay the response payload back to the client
    return res.status(response.status).json(response.data);
  } catch (error) {
    console.error('Error proxying request to Gemini API:', error.message);
    
    if (error.response) {
      return res.status(error.response.status).json(error.response.data);
    } else if (error.request) {
      return res.status(504).json({
        error: { message: 'Gateway Timeout: No response received from Gemini API.' }
      });
    } else {
      return res.status(500).json({
        error: { message: `Internal Proxy Error: ${error.message}` }
      });
    }
  }
});
