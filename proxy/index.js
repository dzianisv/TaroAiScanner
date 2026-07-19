const functions = require('@google-cloud/functions-framework');
const axios = require('axios');

// Enable CORS for pre-flight and standard requests
const handleCors = (req, res) => {
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return true;
  }
  return false;
};

/**
 * HTTP Cloud Function that acts as a secure proxy to the Gemini API.
 * This prevents exposing the GEMINI_API_KEY inside the mobile client binary.
 */
functions.http('geminiProxy', async (req, res) => {
  if (handleCors(req, res)) return;

  if (req.method !== 'POST') {
    return res.status(405).json({
      error: { message: 'Only POST requests are supported.' }
    });
  }

  // Fetch the API Key from the Cloud Function environment variables
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error('CRITICAL: GEMINI_API_KEY environment variable is missing.');
    return res.status(500).json({
      error: { message: 'Proxy configuration error: API Key is missing on the server.' }
    });
  }

  // Default to gemini-2.5-flash unless specified
  const modelName = req.query.model || 'gemini-2.5-flash';
  const targetUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;

  try {
    console.log(`Forwarding request to Gemini API for model: ${modelName}`);
    
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
      // The request was made and the server responded with a status code outside the 2xx range
      return res.status(error.response.status).json(error.response.data);
    } else if (error.request) {
      // The request was made but no response was received
      return res.status(504).json({
        error: { message: 'Gateway Timeout: No response received from Gemini API.' }
      });
    } else {
      // Something else happened in setting up the request
      return res.status(500).json({
        error: { message: `Internal Proxy Error: ${error.message}` }
      });
    }
  }
});
