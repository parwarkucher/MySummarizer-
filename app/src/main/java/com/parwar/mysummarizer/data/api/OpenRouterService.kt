package com.parwar.mysummarizer.data.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class Message(
    val role: String,
    val content: String
)

data class Choice(
    @SerializedName("message") val message: Message,
    @SerializedName("index") val index: Int = 0,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class OpenRouterErrorMetadata(
    @SerializedName("raw") val raw: String?,
    @SerializedName("provider_name") val providerName: String?
)

data class OpenRouterError(
    @SerializedName("message") val message: String?,
    @SerializedName("code") val code: Int?,
    @SerializedName("metadata") val metadata: OpenRouterErrorMetadata?
)

data class OpenRouterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("created") val created: Long,
    @SerializedName("model") val model: String?,
    @SerializedName("usage") val usage: Usage?,
    @SerializedName("error") val error: OpenRouterError?
)

data class Provider(
    @SerializedName("order") val order: List<String>,
    @SerializedName("allow_fallbacks") val allowFallbacks: Boolean = false
)

data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val provider: Provider? = null
)

interface OpenRouterService {
    @POST("chat/completions")
    suspend fun getSummary(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/perwendel/MySummarizer",
        @Header("X-Title") title: String = "MySummarizer",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse = withContext(Dispatchers.IO) {
        var retryCount = 0
        val maxRetries = 5
        var lastError: Exception? = null

        while (retryCount < maxRetries) {
            try {
                Log.d("OpenRouterService", "Attempt ${retryCount + 1}/$maxRetries: Sending request")
                val response = getSummaryInternal(apiKey, referer, title, contentType, request)

                // Check for error in response
                if (response.error != null) {
                    val errorMessage = response.error.message ?: "Unknown error"
                    val errorCode = response.error.code
                    val provider = response.error.metadata?.providerName

                    // Handle rate limit errors specially
                    if (errorCode == 429 || errorMessage.contains("rate limit", ignoreCase = true)) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            val delayMs = 1000L * (1 shl retryCount) // Exponential backoff
                            Log.d("OpenRouterService", "Rate limit hit from provider $provider. Retrying in ${delayMs}ms...")
                            delay(delayMs)
                            continue
                        }
                    }

                    // Convert error to user-friendly message
                    throw Exception(getUserFriendlyError(errorCode, errorMessage, provider))
                }

                // Log successful response details
                response.usage?.let { usage ->
                    Log.d("OpenRouterService", "Response received. Tokens used - Prompt: ${usage.promptTokens}, " +
                            "Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                }

                return@withContext response

            } catch (e: Exception) {
                lastError = e
                Log.e("OpenRouterService", "Error during API call (attempt ${retryCount + 1}/$maxRetries): ${e.message}")

                // Check if it's worth retrying
                if (shouldRetry(e)) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        val delayMs = 1000L * (1 shl retryCount)
                        Log.d("OpenRouterService", "Retrying in ${delayMs}ms...")
                        delay(delayMs)
                        continue
                    }
                }

                // If we shouldn't retry or have exhausted retries, throw with user-friendly message
                throw Exception(getUserFriendlyError(null, e.message, null))
            }
        }

        // If we've exhausted all retries
        throw lastError ?: Exception("Failed to get a response after $maxRetries attempts")
    }

    @POST("chat/completions")
    suspend fun getSummaryInternal(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/perwendel/MySummarizer",
        @Header("X-Title") title: String = "MySummarizer",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse

    @POST("api/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/",
        @Header("X-Title") title: String = "MySummarizer",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): ChatResponse = withContext(Dispatchers.IO) {
        var retryCount = 0
        val maxRetries = 5
        var lastError: Exception? = null

        while (retryCount < maxRetries) {
            try {
                Log.d("OpenRouterService", "Attempt ${retryCount + 1}/$maxRetries: Sending chat request")
                val response = chatInternal(authorization, referer, title, contentType, request)
                return@withContext response

            } catch (e: Exception) {
                lastError = e
                Log.e("OpenRouterService", "Error during chat API call (attempt ${retryCount + 1}/$maxRetries): ${e.message}")

                // Check if it's worth retrying
                if (shouldRetry(e)) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        val delayMs = 1000L * (1 shl retryCount)
                        Log.d("OpenRouterService", "Retrying chat in ${delayMs}ms...")
                        delay(delayMs)
                        continue
                    }
                }

                // If we shouldn't retry or have exhausted retries, throw with user-friendly message
                throw Exception(getUserFriendlyError(null, e.message, null))
            }
        }

        // If we've exhausted all retries
        throw lastError ?: Exception("Failed to get a chat response after $maxRetries attempts")
    }

    @POST("api/v1/chat/completions")
    suspend fun chatInternal(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/",
        @Header("X-Title") title: String = "MySummarizer",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): ChatResponse

    data class ChatRequest(
        val model: String,
        val messages: List<Map<String, String>>
    )

    data class ChatResponse(
        val choices: List<Choice>
    ) {
        data class Choice(
            val message: Message
        )

        data class Message(
            val content: String
        )
    }

    companion object {
        private fun shouldRetry(error: Exception): Boolean {
            val message = error.message?.lowercase() ?: return false
            return message.contains("429") || 
                   message.contains("rate limit") ||
                   message.contains("timeout") ||
                   message.contains("connection") ||
                   message.contains("temporary")
        }

        private fun getUserFriendlyError(code: Int?, message: String?, provider: String?): String {
            val providerInfo = if (provider != null) " ($provider)" else ""
            return when {
                code == 429 || message?.contains("rate limit", ignoreCase = true) == true ->
                    "You've made too many requests. Please wait a moment and try again."
                code == 400 ->
                    "Oops! Something went wrong with the input. Please check and try again."
                code == 401 || code == 403 || message?.contains("api key", ignoreCase = true) == true ->
                    "Access denied. Please ensure your API key is valid."
                code == 404 ->
                    "The requested resource could not be found. Please check and try again."
                code == 500 ->
                    "The service is temporarily unavailable. Please try again later."
                code in listOf(502, 503, 504) ->
                    "The server is currently unavailable. Please try again after some time."
                code == 418 ->
                    "Request rate exceeded. Please slow down and try again soon."
                code == 409 ->
                    "This action cannot be completed due to a conflict. Please check your request."
                code == 422 ->
                    "The request could not be processed. Please review your input."
                code == 402 ->
                    "Your subscription or credits have expired. Please update your payment information."
                message?.contains("context length", ignoreCase = true) == true ->
                    "The conversation is too long. Some older messages will be removed to continue."
                else -> "An error occurred: ${message ?: "Unknown error"}"
            }
        }
    }
}
