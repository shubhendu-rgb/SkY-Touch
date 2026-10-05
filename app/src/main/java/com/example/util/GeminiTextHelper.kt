package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object GeminiTextHelper {
    private const val TAG = "GeminiTextHelper"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class Credential {
        data class ApiKey(val key: String) : Credential()
        data class OAuthToken(val token: String) : Credential()
        data class Invalid(val message: String) : Credential()
    }

    /**
     * Resolves and normalizes model names. Automatically migrates deprecated models
     * (e.g. gemini-2.0-flash, gemini-1.5-flash) to currently active models like gemini-2.5-flash.
     */
    fun resolveModelName(rawModel: String?): String {
        val model = rawModel?.trim().orEmpty()
        return when {
            model.isBlank() -> "gemini-2.5-flash"
            model.startsWith("gemini-1.5") || 
            model.startsWith("gemini-2.0") || 
            model == "gemini-pro" -> "gemini-2.5-flash"
            else -> model
        }
    }

    /**
     * Extracts and validates user input for API keys or OAuth tokens.
     * Prevents common user pitfalls (e.g. pasting OAuth Client IDs, Bearer headers, surrounding quotes).
     */
    fun parseCredential(rawInput: String?): Credential {
        if (rawInput.isNullOrBlank()) {
            return Credential.Invalid("API key is required. Please enter your Gemini API key in Text Assistant settings.")
        }
        var cleaned = rawInput.trim()

        // Remove surrounding single or double quotes
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'"))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }

        // Remove accidental key= or api_key= prefixes
        if (cleaned.startsWith("key=", ignoreCase = true)) {
            cleaned = cleaned.substring(4).trim()
        } else if (cleaned.startsWith("api_key=", ignoreCase = true)) {
            cleaned = cleaned.substring(8).trim()
        }

        // Check for Google Cloud OAuth 2.0 Client ID (e.g. 123456789-abcdef.apps.googleusercontent.com)
        if (cleaned.contains(".apps.googleusercontent.com", ignoreCase = true) || cleaned.endsWith(".googleusercontent.com", ignoreCase = true)) {
            return Credential.Invalid(
                "You entered a Google OAuth 2.0 Client ID instead of a Gemini API Key.\n\n" +
                "Google Gemini REST API requires a standard Gemini API Key (starts with 'AIzaSy...').\n\n" +
                "How to get a valid key in 10 seconds:\n" +
                "1. Go to https://aistudio.google.com/apikey\n" +
                "2. Tap 'Create API key'\n" +
                "3. Copy and paste the key here."
            )
        }

        // Automatically extract AIzaSy key if surrounded by other characters/labels
        val aizaRegex = Regex("AIzaSy[A-Za-z0-9_-]{33}")
        val match = aizaRegex.find(cleaned)
        if (match != null) {
            return Credential.ApiKey(match.value)
        }

        // Check for Service Account JSON
        if (cleaned.startsWith("{") && cleaned.contains("\"private_key\"")) {
            return Credential.Invalid(
                "You entered a Service Account JSON key. Please use a standard Gemini API key from https://aistudio.google.com/apikey instead."
            )
        }

        // Check for Bearer prefix
        if (cleaned.startsWith("Bearer ", ignoreCase = true)) {
            val token = cleaned.substring(7).trim()
            return if (token.startsWith("ya29.")) {
                Credential.OAuthToken(token)
            } else {
                Credential.ApiKey(token)
            }
        }

        // Check for OAuth access token (ya29.xxx)
        if (cleaned.startsWith("ya29.")) {
            return Credential.OAuthToken(cleaned)
        }

        return Credential.ApiKey(cleaned)
    }

    fun getEffectiveApiKey(customApiKey: String?): String {
        return when (val cred = parseCredential(customApiKey)) {
            is Credential.ApiKey -> cred.key
            is Credential.OAuthToken -> cred.token
            is Credential.Invalid -> ""
        }
    }

    private fun getCertificateSha1(context: Context?): String? {
        if (context == null) return null
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return null
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(
        credential: Credential,
        modelName: String,
        jsonBodyString: String,
        attachAndroidCert: Boolean = false,
        context: Context? = null
    ): Request {
        val resolvedModel = resolveModelName(modelName)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBodyString.toRequestBody(mediaType)

        val reqBuilder = Request.Builder()
        reqBuilder.header("Content-Type", "application/json; charset=utf-8")

        val requestUrl: String = when (credential) {
            is Credential.OAuthToken -> {
                reqBuilder.header("Authorization", "Bearer ${credential.token}")
                "$BASE_URL/$resolvedModel:generateContent"
            }
            is Credential.ApiKey -> {
                reqBuilder.header("x-goog-api-key", credential.key)
                "$BASE_URL/$resolvedModel:generateContent?key=${credential.key}"
            }
            is Credential.Invalid -> {
                "$BASE_URL/$resolvedModel:generateContent"
            }
        }

        // Only attach Android package and certificate headers if requested for restricted keys
        if (attachAndroidCert && context != null) {
            try {
                reqBuilder.header("X-Android-Package", context.packageName)
                val certSha1 = getCertificateSha1(context)
                if (!certSha1.isNullOrBlank()) {
                    reqBuilder.header("X-Android-Cert", certSha1)
                }
            } catch (_: Exception) {}
        }

        return reqBuilder.url(requestUrl).post(requestBody).build()
    }

    private fun executeRequest(
        credential: Credential,
        modelName: String,
        jsonBodyString: String,
        context: Context?
    ): Pair<Int, String> {
        // First attempt: Standard API key without Android restriction headers (works with Google AI Studio keys)
        val initialRequest = buildRequest(credential, modelName, jsonBodyString, attachAndroidCert = false, context = context)
        val response = client.newCall(initialRequest).execute()
        val responseBody = response.body?.string().orEmpty()
        val code = response.code

        // If failed with 401/403 and context exists, retry with Android restriction headers for restricted Cloud Console keys
        if ((code == 401 || code == 403) && context != null && !responseBody.contains("API key not valid")) {
            try {
                val retryRequest = buildRequest(credential, modelName, jsonBodyString, attachAndroidCert = true, context = context)
                val retryResponse = client.newCall(retryRequest).execute()
                val retryBody = retryResponse.body?.string().orEmpty()
                if (retryResponse.isSuccessful || !retryBody.contains("Expected OAuth 2 access token")) {
                    return Pair(retryResponse.code, retryBody)
                }
            } catch (_: Exception) {}
        }

        return Pair(code, responseBody)
    }

    private fun parseErrorMessage(responseCode: Int, responseBody: String): String {
        return try {
            val errorJson = JSONObject(responseBody)
            val errorObj = errorJson.optJSONObject("error")
            val message = errorObj?.optString("message") ?: "HTTP $responseCode: $responseBody"
            val status = errorObj?.optString("status") ?: ""

            when {
                message.contains("Expected OAuth 2 access token", ignoreCase = true) ||
                message.contains("invalid authentication credentials", ignoreCase = true) -> {
                    "Invalid Authentication Credentials:\n\n" +
                    "Google rejected the credential provided.\n\n" +
                    "• In Google Cloud Console or Google AI Studio, ensure you create an 'API Key' (starts with 'AIzaSy...'), NOT an 'OAuth 2.0 Client ID'.\n" +
                    "• Ensure the 'Generative Language API' is enabled on your Google Cloud project.\n" +
                    "• Recommended: Generate a free Gemini API key in 1 click at https://aistudio.google.com/apikey"
                }
                message.contains("API key not valid", ignoreCase = true) || message.contains("API_KEY_INVALID", ignoreCase = true) -> {
                    "API Key Not Valid:\nGoogle did not recognize this API key. Please verify that the entire key (starting with AIzaSy) was copied correctly from https://aistudio.google.com/apikey"
                }
                message.contains("Method doesn't allow unregistered callers", ignoreCase = true) || status == "PERMISSION_DENIED" -> {
                    "Access Denied (Permission Denied):\nThe Generative Language API is disabled or caller identity is missing. Ensure the Google Generative Language API is enabled in your Google Cloud Console."
                }
                message.contains("Resource has been exhausted", ignoreCase = true) || responseCode == 429 -> {
                    "Rate Limit Exceeded: Model quota reached. Please try again in a few moments or switch model version to gemini-2.5-flash."
                }
                else -> message
            }
        } catch (_: Exception) {
            "HTTP $responseCode: $responseBody"
        }
    }

    suspend fun transformText(
        apiKey: String,
        modelName: String,
        inputText: String,
        instruction: String,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val credential = parseCredential(apiKey)
        if (credential is Credential.Invalid) {
            return@withContext Result.failure(IllegalStateException(credential.message))
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

            val (code, responseBody) = executeRequest(credential, modelName, jsonBody.toString(), context)

            if (code !in 200..299) {
                val errorMsg = parseErrorMessage(code, responseBody)
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

    suspend fun testApiKey(
        apiKey: String,
        modelName: String = "gemini-2.5-flash",
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val credential = parseCredential(apiKey)
        if (credential is Credential.Invalid) {
            return@withContext Result.failure(IllegalStateException(credential.message))
        }

        val resolvedModel = resolveModelName(modelName)
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

            val (code, responseBody) = executeRequest(credential, resolvedModel, jsonBody.toString(), context)
            val duration = System.currentTimeMillis() - startTime

            if (code !in 200..299) {
                val errorMsg = parseErrorMessage(code, responseBody)
                return@withContext Result.failure(Exception(errorMsg))
            }

            Result.success("Connection verified in ${duration}ms using model $resolvedModel")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
