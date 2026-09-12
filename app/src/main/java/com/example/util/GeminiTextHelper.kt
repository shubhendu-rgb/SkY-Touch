package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiTextHelper {
    private const val TAG = "GeminiTextHelper"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(customApiKey: String?): String {
        return customApiKey?.trim().orEmpty()
    }

    suspend fun transformText(
        apiKey: String,
        modelName: String,
        inputText: String,
        instruction: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val effectiveKey = getEffectiveApiKey(apiKey)
        if (effectiveKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is required. Please enter your Gemini API key in Text Assistant settings.")
            )
        }

        val promptText = if (inputText.isNotBlank()) {
            "Instruction:\n$instruction\n\nOriginal Text:\n$inputText"
        } else {
            "Instruction:\n$instruction"
        }

        try {
            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", promptText)
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", "You are an intelligent inline text refinement assistant. Transform the provided text exactly according to the user instruction. Return ONLY the final transformed output without preamble, quotes, markdown formatting or commentary unless requested.")
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put("systemInstruction", systemInstructionObj)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.3)
                    put("topP", 0.95)
                }
                put("generationConfig", generationConfig)
            }

            val requestUrl = "$BASE_URL/$modelName:generateContent?key=$effectiveKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorJson = JSONObject(responseBody)
                    errorJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Gemini API failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No generation candidates returned from Gemini."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                return@withContext Result.failure(Exception("Empty content received from Gemini."))
            }

            val generatedText = parts.getJSONObject(0).optString("text", "").trim()
            if (generatedText.isEmpty()) {
                return@withContext Result.failure(Exception("Received blank response from Gemini."))
            }

            Result.success(generatedText)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini text transform", e)
            Result.failure(e)
        }
    }

    suspend fun testApiKey(apiKey: String, modelName: String = "gemini-2.0-flash"): Result<String> = withContext(Dispatchers.IO) {
        val effectiveKey = getEffectiveApiKey(apiKey)
        if (effectiveKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("API key is empty."))
        }

        val startTime = System.currentTimeMillis()
        try {
            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", "Respond with the single word: OK")
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val requestUrl = "$BASE_URL/$modelName:generateContent?key=$effectiveKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorJson = JSONObject(responseBody)
                    errorJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            Result.success("Connection verified in ${duration}ms using model $modelName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
