package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.TarotReading
import com.example.data.TarotDeck
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiTarotService {

    const val MODEL_CHAT = "chat-auto"
    const val MODEL_DESCRIBE = "describe-auto"
    const val MODEL_SCAN = "scan-auto"

    private fun resolveDirectModelName(modelAlias: String): String {
        return when (modelAlias) {
            MODEL_CHAT, MODEL_DESCRIBE, MODEL_SCAN -> "gemini-3.6-flash"
            else -> modelAlias
        }
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val tarotAdapter = moshi.adapter(TarotReading::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Medium timeouts for image scanning operations
    private val scanClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    // Chat is a full LLM completion, not a "quick user interaction": the model
    // writes up to three paragraphs, which regularly takes well over 8s. Using
    // the old 8s textClient here made every chat turn fail with SocketTimeoutException and
    // surface as "The ethereal connection was interrupted: timeout" -- and,
    // unlike the reading paths, chat has no mock fallback, so the feature was
    // simply broken. Give it the same headroom as the scan/reading client.
    private val chatClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Same reasoning as chatClient: interpreting a drawn card is a full LLM
    // completion (five prose fields of JSON), not a "quick user interaction".
    // It used to run on the 8s textClient read timeout inside a 10s coroutine
    // budget and fall back to getMockReading() -- so a slow model silently
    // served canned text as if it were the AI's answer.
    private val readingClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Overall deadline for a reading call; must exceed readingClient's read timeout. */
    private const val READING_DEADLINE_MS = 75_000L

    /** Thrown when neither a proxy nor a usable API key is configured. */
    class NotConfiguredException : IllegalStateException(
        "No AI connection configured. Add a proxy URL or your Gemini API key in Settings, " +
            "or switch on Offline Mode for sample readings."
    )

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress to JPEG to save bandwidth while keeping details clear
        compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cleanJsonBody(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (text.startsWith("```")) {
            text = text.substringAfter("```").substringBeforeLast("```").trim()
        }
        return text
    }

    suspend fun analyzeTarotCard(
        bitmap: Bitmap,
        promptContext: String = "Single Card Draw",
        proxyUrl: String = "",
        offlineMode: Boolean = false,
        customApiKey: String = "",
        idToken: String = ""
    ): TarotReading? = withContext(Dispatchers.IO) {
        if (offlineMode) {
            // User explicitly asked for Offline Mode: serve the bundled sample
            // reading, flagged so the UI labels it as NOT an AI answer.
            return@withContext getMockReading("The Star", "Upright", isOffline = true)
        }

        val base64Image = bitmap.toBase64()

        val prompt = """
            You are a wise and highly intuitive, professional Tarot Card Reader. 
            Analyze the physical tarot card shown in the uploaded image.
            Identify the Tarot card name and whether it is oriented Upright or Reversed.
            Context of the reading: $promptContext.
            
            Return your complete response strictly as a JSON object matching this exact schema:
            {
              "cardName": "Name of the card",
              "orientation": "Upright" or "Reversed",
              "summary": "A beautiful 1-sentence summary of the card's energy today",
              "generalMeaning": "Detailed paragraph exploring the general interpretation and psychological/spiritual archetype of this card",
              "advice": "Actionable positive guidance/steps for the seeker based on this card",
              "warning": "A gentle warning or pitfall to avoid under this card's energy",
              "luckyElements": ["A lucky color", "A lucky hour or time", "A key number", "An aligned element or astrological sign"]
            }
            Do not include any other markdown, text or explanation outside the JSON. Return only valid raw JSON.
        """.trimIndent()

        // Construct request body using JSONObject
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        // Build request depending on proxy configuration
        val requestUrl = if (proxyUrl.isNotEmpty()) {
            val separator = if (proxyUrl.contains("?")) "&" else "?"
            "$proxyUrl${separator}model=$MODEL_SCAN"
        } else {
            val apiKey = if (customApiKey.isNotEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Not configured is a real error, not a reading. Never dress the
                // canned sample up as the AI's answer.
                throw NotConfiguredException()
            }
            val targetModel = resolveDirectModelName(MODEL_SCAN)
            "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
        }

        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(requestBody)

        if (proxyUrl.isNotEmpty() && idToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $idToken")
        }

        val request = requestBuilder.build()

        try {
            val response = scanClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = try {
                    JSONObject(errorBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                throw Exception(errorMessage)
            }
            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(responseBody)
            
            val candidates = responseJson.optJSONArray("candidates")
            val text = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: return@withContext null

            val cleanedJson = cleanJsonBody(text)
            return@withContext tarotAdapter.fromJson(cleanedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun interpretVirtualCard(
        cardName: String,
        orientation: String,
        spreadType: String = "Single Card Draw",
        proxyUrl: String = "",
        offlineMode: Boolean = false,
        customApiKey: String = "",
        idToken: String = ""
    ): TarotReading? = withContext(Dispatchers.IO) {
        if (offlineMode) {
            return@withContext getMockReading(cardName, orientation, isOffline = true)
        }

        val prompt = """
            You are a wise and highly intuitive, professional Tarot Card Reader. 
            Interpret the drawn virtual card: $cardName in its $orientation position.
            Context of the reading: $spreadType.
            
            Return your complete response strictly as a JSON object matching this exact schema:
            {
              "cardName": "$cardName",
              "orientation": "$orientation",
              "summary": "A beautiful 1-sentence summary of the card's energy today",
              "generalMeaning": "Detailed paragraph exploring the general interpretation and psychological/spiritual archetype of this card",
              "advice": "Actionable positive guidance/steps for the seeker based on this card",
              "warning": "A gentle warning or pitfall to avoid under this card's energy",
              "luckyElements": ["A lucky color", "A lucky hour or time", "A key number", "An aligned element or astrological sign"]
            }
            Do not include any other markdown, text or explanation outside the JSON. Return only valid raw JSON.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val requestUrl = if (proxyUrl.isNotEmpty()) {
            val separator = if (proxyUrl.contains("?")) "&" else "?"
            "$proxyUrl${separator}model=$MODEL_DESCRIBE"
        } else {
            val apiKey = if (customApiKey.isNotEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                throw NotConfiguredException()
            }
            val targetModel = resolveDirectModelName(MODEL_DESCRIBE)
            "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
        }

        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(requestBody)

        if (proxyUrl.isNotEmpty() && idToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $idToken")
        }

        val request = requestBuilder.build()

        // No getMockReading() fallback here on purpose: a failed AI call must
        // surface as an honest error the user can retry from, never as canned
        // text rendered indistinguishably from a real reading.
        try {
            return@withContext withTimeout(READING_DEADLINE_MS) {
                val response = readingClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    val errorMessage = try {
                        JSONObject(errorBody).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    throw Exception(errorMessage)
                }
                val responseBody = response.body?.string() ?: return@withTimeout null
                val responseJson = JSONObject(responseBody)

                val candidates = responseJson.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: return@withTimeout null

                val cleanedJson = cleanJsonBody(text)
                tarotAdapter.fromJson(cleanedJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun chatWithTarotMaster(
        history: List<com.example.data.TarotChatMessageEntity>,
        newMessageText: String,
        attachedMimeType: String? = null,
        attachedBytes: ByteArray? = null,
        proxyUrl: String = "",
        offlineMode: Boolean = false,
        customApiKey: String = "",
        idToken: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (offlineMode) {
            return@withContext "The celestial paths are quiet today. In offline mode, the oracle reflects your own inner light. Contemplate your current card draws for guidance, or toggle Online Mode to engage the Gemini AI."
        }

        val contentsArray = JSONArray()

        val systemPrompt = "You are a wise, highly intuitive, compassionate professional Tarot Master. " +
                "Guide the seeker with ancient tarot wisdom, modern psychology, and celestial insight. " +
                "Be mysterious yet practical. Keep your answers beautifully structured, readable, and under 3 paragraphs."

        // Add history
        history.forEach { msg ->
            val role = if (msg.sender == "user") "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", msg.text)
                    })
                })
            })
        }

        // Add the new user message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "$systemPrompt\n\nSeeker: $newMessageText")
                })
                
                if (attachedBytes != null && attachedMimeType != null) {
                    val base64Data = Base64.encodeToString(attachedBytes, Base64.NO_WRAP)
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", attachedMimeType)
                            put("data", base64Data)
                        })
                    })
                }
            })
        })

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val requestUrl = if (proxyUrl.isNotEmpty()) {
            val separator = if (proxyUrl.contains("?")) "&" else "?"
            "$proxyUrl${separator}model=$MODEL_CHAT"
        } else {
            val apiKey = if (customApiKey.isNotEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext "The cosmic link is quiet. Connect to your direct Gemini API key or proxy to begin your chat."
            }
            val targetModel = resolveDirectModelName(MODEL_CHAT)
            "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
        }

        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(requestBody)

        if (proxyUrl.isNotEmpty() && idToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $idToken")
        }

        val request = requestBuilder.build()

        try {
            val response = chatClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = try {
                    JSONObject(errorBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                throw Exception(errorMessage)
            }
            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(responseBody)
            
            val candidates = responseJson.optJSONArray("candidates")
            val text = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            
            text
        } catch (e: Exception) {
            e.printStackTrace()
            "The ethereal connection was interrupted: ${e.localizedMessage}"
        }
    }

    /**
     * Bundled offline interpretation. NEVER return this from a failed AI call:
     * callers must pass isOffline = true so the UI can label it, and the only
     * legitimate caller is the explicit Offline Mode branch.
     */
    internal fun getMockReading(
        name: String,
        orientation: String,
        isOffline: Boolean = false
    ): TarotReading {
        val card = TarotDeck.majorArcana.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: TarotDeck.majorArcana.first()
        
        val meaning = if (orientation.equals("Upright", ignoreCase = true)) card.uprightMeaning else card.reversedMeaning
        val summaryStr = "The energies of ${card.name} (${orientation}) flow around your life questions today, carrying ${card.keywords.take(2).joinToString(" and ").lowercase()}."
        
        val luckyColors = listOf("Mystic Purple", "Celestial Blue", "Golden Aurum", "Crimson Flame", "Emerald Dawn", "Midnight Velvet")
        val luckyHours = listOf("3:33 AM", "11:11 AM", "7:07 PM", "12:12 PM", "8:08 AM", "10:10 PM")
        
        val color = luckyColors.random()
        val hour = luckyHours.random()
        val number = "Number ${(1..22).random()}"
        
        return TarotReading(
            cardName = card.name,
            orientation = orientation,
            summary = summaryStr,
            generalMeaning = meaning,
            advice = "Meditate on the archetype of ${card.name} (${orientation}). Use its wisdom to ${card.keywords.last().lowercase()}.",
            warning = "Beware of over-relying on superficial solutions. Real transformation requires balancing ${card.element} energies.",
            luckyElements = listOf(color, hour, number, "${card.astrologicalSign} / ${card.element}"),
            isOffline = isOffline
        )
    }
}
