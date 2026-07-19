package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.TarotReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiTarotService {

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val tarotAdapter = moshi.adapter(TarotReading::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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
        proxyUrl: String = ""
    ): TarotReading? = withContext(Dispatchers.IO) {
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
            "$proxyUrl${separator}model=$MODEL_NAME"
        } else {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return fallback reading immediately if no direct API key and no proxy configured
                return@withContext getMockReading("The Star", "Upright")
            }
            "$BASE_URL?key=$apiKey"
        }

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
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

    private fun getMockReading(name: String, orientation: String): TarotReading {
        return TarotReading(
            cardName = name,
            orientation = orientation,
            summary = "Hope, faith, and cosmic alignment are guiding your path forward today.",
            generalMeaning = "The Star brings renewed hope, faith, and a sense of being blessed by the universe. It suggests that you are entering a period of spiritual healing and peace after a time of trials. Your inner light is shining bright, and you are being called to trust the natural flow of your life.",
            advice = "Open your heart to healing. Trust your intuition and follow the gentle tugs of your inspiration.",
            warning = "Avoid slipping into passive dreaming; remember to take real-world action to anchor your visions.",
            luckyElements = listOf("Midnight Blue", "11:11 PM", "Number 17", "Aquarius / Air")
        )
    }
}
