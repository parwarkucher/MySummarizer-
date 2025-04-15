package com.parwar.mysummarizer.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.mysummarizer.data.preferences.PreferencesManager
import com.parwar.mysummarizer.data.model.AIModel
import com.parwar.mysummarizer.data.api.Message
import com.parwar.mysummarizer.data.api.OpenRouterRequest
import com.parwar.mysummarizer.data.api.OpenRouterService
import com.parwar.mysummarizer.data.service.YouTubeService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

sealed class SummaryState {
    object Idle : SummaryState()
    object Loading : SummaryState()
    data class Success(
        val shortSummary: String,
        val detailedSummary: String
    ) : SummaryState()
    data class Error(val message: String) : SummaryState()
    data class Retrying(
        val message: String,
        val retryCount: Int,
        val retryingShort: Boolean,
        val retryingDetailed: Boolean,
        val existingShortSummary: String? = null,
        val existingDetailedSummary: String? = null
    ) : SummaryState()
}

enum class SummaryType {
    SHORT, DETAILED
}

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    var isOutOfContext: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val openRouterService: OpenRouterService,
    private val youTubeService: YouTubeService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _summaryState = MutableLiveData<SummaryState>(SummaryState.Idle)
    val summaryState: LiveData<SummaryState> = _summaryState

    private val _selectedModel = MutableLiveData<String>()
    val selectedModel: LiveData<String> = _selectedModel

    private val _lastTokenUsage = MutableLiveData<String>()
    val lastTokenUsage: LiveData<String> = _lastTokenUsage

    // Store video context for chat
    private var videoTranscript: String? = null
    private var videoDetailedSummary: String? = null

    private val TAG = "MainViewModel"
    private val CONTEXT_THRESHOLD_PERCENTAGE = 0.8 // Remove messages when reaching 80% of limit
    private val MESSAGES_TO_KEEP_PERCENTAGE = 0.7 // Keep 70% of most recent messages
    private var lastTotalTokens = 0 // Track actual token usage from last response
    private val MAX_RETRIES = 5
    private val RETRY_DELAYS = listOf(2000L, 5000L, 10000L, 30000L, 60000L) // 2s, 5s, 10s, 30s, 60s
    
    // Retry configuration for summary generation
    private val SUMMARY_MAX_RETRIES = 6
    private val SUMMARY_RETRY_DELAYS = listOf(10000L, 20000L, 30000L, 40000L, 50000L, 60000L) // 10s, 20s, 30s, 40s, 50s, 60s
    
    // Variables to track retry state
    private var currentRetryJob: kotlinx.coroutines.Job? = null
    private var lastVideoUrl: String? = null
    private var lastModelId: String? = null
    private var lastApiKey: String? = null
    private var lastCaptions: String? = null
    private var shortSummarySuccess: String? = null
    private var detailedSummarySuccess: String? = null

    // Store chat history as ChatMessage objects
    private val chatHistory = mutableListOf<ChatMessage>()

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        Log.d("ChatDebug", "ViewModel: Model set to $model")
    }

    fun sendChatMessage(message: String, onResponse: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("ChatDebug", "ViewModel: Sending chat message")
                val modelValue = selectedModel.value
                if (modelValue == null) {
                    Log.e("ChatDebug", "No model selected")
                    onResponse("Error: No AI model selected")
                    return@launch
                }
                
                val apiKey = preferencesManager.apiKey
                if (apiKey.isEmpty()) {
                    Log.e("ChatDebug", "No API key")
                    onResponse("Error: Please enter your OpenRouter API key")
                    return@launch
                }

                // Create messages list for API
                val currentMessages = mutableListOf<Message>()
                
                // Add system message with context
                val contextBuilder = StringBuilder()
                if (videoTranscript != null || videoDetailedSummary != null) {
                    contextBuilder.append("=== VIDEO CONTEXT ===\n")
                    videoDetailedSummary?.let { 
                        contextBuilder.append("\n## Detailed Summary\n")
                        contextBuilder.append("$it\n\n")
                    }
                    videoTranscript?.let {
                        contextBuilder.append("\n## Full Transcript\n")
                        contextBuilder.append("$it\n\n")
                    }
                    contextBuilder.append("=== END OF VIDEO CONTEXT ===\n\n")
                }

                val systemMessage = Message(
                    role = "system", 
                    content = """You are a helpful AI assistant discussing a video. Base your responses on the following context:

                        |$contextBuilder
                        |
                        |Instructions:
                        |1. If video context is provided above:
                        |   - Use it to give detailed and accurate responses
                        |   - Reference specific parts of the video when relevant
                        |   - Be clear about which part of the video you're discussing
                        |2. If no video context is provided:
                        |   - Inform the user that no video has been loaded yet
                        |   - Suggest loading a video to get better answers
                        |3. Keep responses clear and well-structured:
                        |   - Break down complex explanations into points
                        |   - Use examples from the video when possible
                        |   - Highlight key concepts or timestamps
                        |4. Always be clear about whether you're using video context in your response
                        |5. If asked about something not in the video:
                        |   - Clearly state that it's not covered in the video
                        |   - Provide a general answer if possible
                        |6. Format code examples properly if discussing programming topics
                    """.trimMargin()
                )
                currentMessages.add(systemMessage)

                // Add conversation history
                chatHistory
                    .takeLast((chatHistory.size * MESSAGES_TO_KEEP_PERCENTAGE).toInt())
                    .filterNot { it.isOutOfContext }
                    .forEach { msg ->
                        currentMessages.add(Message(
                            role = if (msg.isFromUser) "user" else "assistant",
                            content = msg.content
                        ))
                    }

                // Add user's current message
                val userMessage = ChatMessage(
                    content = message,
                    isFromUser = true
                )
                chatHistory.add(userMessage)
                currentMessages.add(Message("user", message))

                // Log messages being sent
                Log.d(TAG, "=== MESSAGES BEING SENT TO AI ===")
                currentMessages.forEachIndexed { index, msg ->
                    when (msg.role) {
                        "system" -> Log.d(TAG, "System message: [Video context and instructions]")
                        else -> Log.d(TAG, "${index + 1}. ${msg.role}: ${msg.content}")
                    }
                }
                Log.d(TAG, "=== END OF MESSAGES ===")

                // Get model's context length limit
                val modelInfo = AIModel.models.find { it.id == modelValue }
                if (modelInfo == null) {
                    throw Exception("Model information not found for $modelValue")
                }

                // Check if we're approaching the context length limit based on last response
                if (lastTotalTokens > modelInfo.contextLength * CONTEXT_THRESHOLD_PERCENTAGE) {
                    val messagesToKeep = (chatHistory.size * MESSAGES_TO_KEEP_PERCENTAGE).toInt()
                    if (messagesToKeep < chatHistory.size) {
                        chatHistory.subList(0, chatHistory.size - messagesToKeep).clear()
                        // Mark older messages as out of context
                        chatHistory.take((chatHistory.size * 0.3).toInt()).forEach { it.isOutOfContext = true }
                        Log.d(TAG, "Trimmed chat history to $messagesToKeep messages (Token usage: $lastTotalTokens/${modelInfo.contextLength})")
                    }
                }

                // Retry logic
                var retryCount = 0
                var lastError: Exception? = null

                while (retryCount < MAX_RETRIES) {
                    try {
                        Log.d("ChatDebug", "Using model: $modelValue (Attempt ${retryCount + 1})")
                        val response = openRouterService.getSummary(
                            apiKey = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = modelValue,
                                messages = currentMessages
                            )
                        )
                        
                        // Check for errors in response
                        if (response.error != null) {
                            val errorCode = response.error.code
                            if (errorCode == 429 || errorCode in 500..599) {
                                throw Exception("Server error (code: $errorCode): ${response.error.message}")
                            } else {
                                // Non-retryable error
                                throw Exception(response.error.message ?: "Unknown error")
                            }
                        }

                        // Success - get response and update token usage
                        val aiResponse = response.choices?.firstOrNull()?.message?.content ?: "No response"
                        response.usage?.let { usage ->
                            lastTotalTokens = usage.totalTokens
                            _lastTokenUsage.value = "Last message tokens: ${usage.totalTokens}"
                            Log.d(TAG, "Token usage - Total: ${usage.totalTokens} tokens")
                        }
                        
                        chatHistory.add(ChatMessage(
                            content = aiResponse,
                            isFromUser = false
                        ))
                        onResponse(aiResponse)
                        return@launch

                    } catch (e: Exception) {
                        lastError = e
                        if (retryCount < MAX_RETRIES - 1) {
                            val delayMs = RETRY_DELAYS[retryCount]
                            val delaySeconds = delayMs / 1000f
                            Log.w("ChatDebug", "Attempt ${retryCount + 1} failed: ${e.message}. Retrying in ${delaySeconds}s...")
                            delay(delayMs)
                            retryCount++
                        } else {
                            break
                        }
                    }
                }

                // If we get here, all retries failed
                val errorMessage = "Failed after $MAX_RETRIES attempts: ${lastError?.message}"
                Log.e("ChatDebug", errorMessage, lastError)
                onResponse("Error: $errorMessage")

            } catch (e: Exception) {
                Log.e("ChatDebug", "Error in chat: ${e.message}", e)
                onResponse("Error: ${e.message}")
            }
        }
    }

    fun processYouTubeUrl(url: String, model: String, apiKey: String) {
        // Cancel any ongoing retry job
        currentRetryJob?.cancel()
        
        // Reset retry state
        shortSummarySuccess = null
        detailedSummarySuccess = null
        
        // Save parameters for potential retries
        lastVideoUrl = url
        lastModelId = model
        lastApiKey = apiKey
        
        _summaryState.value = SummaryState.Loading
        viewModelScope.launch {
            try {
                val videoId = youTubeService.extractVideoId(url)
                val captions = youTubeService.getCaptions(videoId)
                if (captions.isEmpty()) {
                    _summaryState.value = SummaryState.Error("No transcript available for this video")
                    return@launch
                }

                // Store the transcript for chat context
                videoTranscript = captions
                lastCaptions = captions

                val shortPrompt = """
                    Summarize this YouTube video transcript in 3-4 bullet points, focusing on the main ideas:
                    $captions
                """.trimIndent()

                val detailedPrompt = """
                    Provide a detailed summary of this YouTube video transcript, including key points, examples, and important details:
                    $captions
                """.trimIndent()

                try {
                    // Try to get both summaries
                    var shortSummary: String? = null
                    var detailedSummary: String? = null
                    var shortError: Exception? = null
                    var detailedError: Exception? = null
                    
                    try {
                        Log.d(TAG, "Making API call for short summary with model: $model")
                        val shortResponse = openRouterService.getSummary(
                            apiKey = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = model,
                                messages = listOf(Message("user", shortPrompt))
                            )
                        )
                        
                        shortSummary = shortResponse.choices?.firstOrNull()?.message?.content
                            ?: throw Exception("Failed to get short summary")
                        
                        shortSummarySuccess = shortSummary
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting short summary", e)
                        shortError = e
                    }
                    
                    try {
                        Log.d(TAG, "Making API call for detailed summary with model: $model")
                        val detailedResponse = openRouterService.getSummary(
                            apiKey = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = model,
                                messages = listOf(Message("user", detailedPrompt))
                            )
                        )
                        
                        detailedSummary = detailedResponse.choices?.firstOrNull()?.message?.content
                            ?: throw Exception("Failed to get detailed summary")
                        
                        detailedSummarySuccess = detailedSummary
                        // Store the detailed summary for chat context
                        videoDetailedSummary = detailedSummary
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting detailed summary", e)
                        detailedError = e
                    }
                    
                    // Check results and decide what to do
                    when {
                        shortSummary != null && detailedSummary != null -> {
                            // Both succeeded
                            _summaryState.value = SummaryState.Success(
                                shortSummary = shortSummary,
                                detailedSummary = detailedSummary
                            )
                        }
                        shortSummary != null -> {
                            // Only short summary succeeded, retry detailed
                            startRetry(
                                retryShort = false,
                                retryDetailed = true,
                                existingShortSummary = shortSummary,
                                existingDetailedSummary = null,
                                errorMessage = "Failed to get detailed summary: ${detailedError?.message}. Retrying..."
                            )
                        }
                        detailedSummary != null -> {
                            // Only detailed summary succeeded, retry short
                            startRetry(
                                retryShort = true,
                                retryDetailed = false,
                                existingShortSummary = null,
                                existingDetailedSummary = detailedSummary,
                                errorMessage = "Failed to get short summary: ${shortError?.message}. Retrying..."
                            )
                        }
                        else -> {
                            // Both failed, retry both
                            startRetry(
                                retryShort = true,
                                retryDetailed = true,
                                existingShortSummary = null,
                                existingDetailedSummary = null,
                                errorMessage = "Failed to generate summaries. Retrying..."
                            )
                        }
                    }
                } catch (e: Exception) {
                    _summaryState.value = SummaryState.Error("Failed to generate summary: ${e.message}")
                }
            } catch (e: Exception) {
                _summaryState.value = SummaryState.Error("Failed to get video transcript: ${e.message}")
            }
        }
    }
    
    private fun startRetry(
        retryShort: Boolean,
        retryDetailed: Boolean,
        existingShortSummary: String?,
        existingDetailedSummary: String?,
        errorMessage: String,
        retryCount: Int = 0
    ) {
        if (retryCount >= SUMMARY_MAX_RETRIES) {
            // We've exhausted all retries
            val finalShortSummary = existingShortSummary ?: shortSummarySuccess
            val finalDetailedSummary = existingDetailedSummary ?: detailedSummarySuccess
            
            if (finalShortSummary != null && finalDetailedSummary != null) {
                // We have both summaries after retries
                _summaryState.value = SummaryState.Success(
                    shortSummary = finalShortSummary,
                    detailedSummary = finalDetailedSummary
                )
                // Store the detailed summary for chat context
                videoDetailedSummary = finalDetailedSummary
            } else {
                // Still missing at least one summary after all retries
                val missingParts = mutableListOf<String>()
                if (finalShortSummary == null) missingParts.add("short summary")
                if (finalDetailedSummary == null) missingParts.add("detailed summary")
                
                _summaryState.value = SummaryState.Error(
                    "Failed to generate ${missingParts.joinToString(" and ")} after multiple retries"
                )
            }
            return
        }
        
        // Update state to show we're retrying
        _summaryState.value = SummaryState.Retrying(
            message = errorMessage,
            retryCount = retryCount + 1,
            retryingShort = retryShort,
            retryingDetailed = retryDetailed,
            existingShortSummary = existingShortSummary,
            existingDetailedSummary = existingDetailedSummary
        )
        
        // Start retry job
        currentRetryJob = viewModelScope.launch {
            val delayMs = SUMMARY_RETRY_DELAYS[retryCount]
            Log.d(TAG, "Scheduling retry #${retryCount + 1} in ${delayMs/1000} seconds")
            delay(delayMs)
            
            try {
                val url = lastVideoUrl
                val model = lastModelId
                val apiKey = lastApiKey
                val captions = lastCaptions
                
                if (url == null || model == null || apiKey == null || captions == null) {
                    _summaryState.value = SummaryState.Error("Retry failed: Missing required information")
                    return@launch
                }
                
                var newShortSummary = existingShortSummary
                var newDetailedSummary = existingDetailedSummary
                var shortError: Exception? = null
                var detailedError: Exception? = null
                
                // Only retry what needs to be retried
                if (retryShort) {
                    try {
                        val shortPrompt = """
                            Summarize this YouTube video transcript in 3-4 bullet points, focusing on the main ideas:
                            $captions
                        """.trimIndent()
                        
                        Log.d(TAG, "Retry #${retryCount + 1}: Making API call for short summary")
                        val shortResponse = openRouterService.getSummary(
                            apiKey = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = model,
                                messages = listOf(Message("user", shortPrompt))
                            )
                        )
                        
                        newShortSummary = shortResponse.choices?.firstOrNull()?.message?.content
                            ?: throw Exception("Failed to get short summary")
                        
                        shortSummarySuccess = newShortSummary
                        Log.d(TAG, "Retry #${retryCount + 1}: Short summary succeeded")
                    } catch (e: Exception) {
                        Log.e(TAG, "Retry #${retryCount + 1}: Error getting short summary", e)
                        shortError = e
                    }
                }
                
                if (retryDetailed) {
                    try {
                        val detailedPrompt = """
                            Provide a detailed summary of this YouTube video transcript, including key points, examples, and important details:
                            $captions
                        """.trimIndent()
                        
                        Log.d(TAG, "Retry #${retryCount + 1}: Making API call for detailed summary")
                        val detailedResponse = openRouterService.getSummary(
                            apiKey = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = model,
                                messages = listOf(Message("user", detailedPrompt))
                            )
                        )
                        
                        newDetailedSummary = detailedResponse.choices?.firstOrNull()?.message?.content
                            ?: throw Exception("Failed to get detailed summary")
                        
                        detailedSummarySuccess = newDetailedSummary
                        // Store the detailed summary for chat context
                        videoDetailedSummary = newDetailedSummary
                        Log.d(TAG, "Retry #${retryCount + 1}: Detailed summary succeeded")
                    } catch (e: Exception) {
                        Log.e(TAG, "Retry #${retryCount + 1}: Error getting detailed summary", e)
                        detailedError = e
                    }
                }
                
                // Check results and decide what to do
                when {
                    (!retryShort || newShortSummary != null) && 
                    (!retryDetailed || newDetailedSummary != null) -> {
                        // All retried summaries succeeded
                        _summaryState.value = SummaryState.Success(
                            shortSummary = newShortSummary ?: existingShortSummary ?: "",
                            detailedSummary = newDetailedSummary ?: existingDetailedSummary ?: ""
                        )
                    }
                    retryCount + 1 >= SUMMARY_MAX_RETRIES -> {
                        // Last retry and still failed, use what we have
                        val finalShortSummary = newShortSummary ?: existingShortSummary ?: shortSummarySuccess
                        val finalDetailedSummary = newDetailedSummary ?: existingDetailedSummary ?: detailedSummarySuccess
                        
                        if (finalShortSummary != null && finalDetailedSummary != null) {
                            _summaryState.value = SummaryState.Success(
                                shortSummary = finalShortSummary,
                                detailedSummary = finalDetailedSummary
                            )
                        } else {
                            val missingParts = mutableListOf<String>()
                            if (finalShortSummary == null) missingParts.add("short summary")
                            if (finalDetailedSummary == null) missingParts.add("detailed summary")
                            
                            _summaryState.value = SummaryState.Error(
                                "Failed to generate ${missingParts.joinToString(" and ")} after multiple retries"
                            )
                        }
                    }
                    else -> {
                        // Need to retry again
                        val stillNeedShort = retryShort && newShortSummary == null
                        val stillNeedDetailed = retryDetailed && newDetailedSummary == null
                        
                        var errorMsg = "Retry #${retryCount + 1} "
                        if (stillNeedShort && stillNeedDetailed) {
                            errorMsg += "failed for both summaries"
                        } else if (stillNeedShort) {
                            errorMsg += "failed for short summary: ${shortError?.message}"
                        } else {
                            errorMsg += "failed for detailed summary: ${detailedError?.message}"
                        }
                        
                        startRetry(
                            retryShort = stillNeedShort,
                            retryDetailed = stillNeedDetailed,
                            existingShortSummary = newShortSummary ?: existingShortSummary,
                            existingDetailedSummary = newDetailedSummary ?: existingDetailedSummary,
                            errorMessage = "$errorMsg. Retrying...",
                            retryCount = retryCount + 1
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Job was cancelled, do nothing
                    return@launch
                }
                
                Log.e(TAG, "Error during retry", e)
                _summaryState.value = SummaryState.Error("Retry failed: ${e.message}")
            }
        }
    }

    fun clearVideoContext() {
        // Cancel any ongoing retry job
        currentRetryJob?.cancel()
        
        videoTranscript = null
        videoDetailedSummary = null
        _summaryState.value = SummaryState.Idle
    }

    fun clearAll() {
        // Cancel any ongoing retry job
        currentRetryJob?.cancel()
        
        videoTranscript = null
        videoDetailedSummary = null
        chatHistory.clear()
        _lastTokenUsage.value = ""
        lastTotalTokens = 0
        
        // Clear retry state
        shortSummarySuccess = null
        detailedSummarySuccess = null
        lastVideoUrl = null
        lastModelId = null
        lastApiKey = null
        lastCaptions = null
    }
}

