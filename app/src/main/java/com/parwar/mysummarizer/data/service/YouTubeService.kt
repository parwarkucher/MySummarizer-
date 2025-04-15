package com.parwar.mysummarizer.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class YouTubeService @Inject constructor() {
    
    fun extractVideoId(url: String): String {
        return when {
            url.contains("youtu.be/") -> {
                url.substringAfter("youtu.be/").substringBefore("?")
            }
            url.contains("youtube.com/watch?v=") -> {
                url.substringAfter("v=").substringBefore("&")
            }
            else -> throw IllegalArgumentException("Invalid YouTube URL")
        }
    }

    suspend fun getCaptions(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("YouTubeService", "Fetching captions for video ID: $videoId")
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val doc = Jsoup.connect(videoUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
            
            Log.d("YouTubeService", "Successfully fetched HTML for video")
            
            // Find and extract captions
            val scripts = doc.select("script")
            var captionsText = ""
            
            Log.d("YouTubeService", "Found ${scripts.size} script tags")
            
            for (script in scripts) {
                val content = script.html()
                if (content.contains("\"playerCaptionsTracklistRenderer\"")) {
                    Log.d("YouTubeService", "Found script with captions")
                    try {
                        val jsonStr = content.substringAfter("\"captions\":{").substringBefore("\"videoDetails\"")
                        val captionsJson = JSONObject("{$jsonStr}")
                        val captionTracks = captionsJson.getJSONObject("playerCaptionsTracklistRenderer")
                            .getJSONArray("captionTracks")
                        
                        if (captionTracks.length() > 0) {
                            val baseUrl = captionTracks.getJSONObject(0).getString("baseUrl")
                            val captionsResponse = Jsoup.connect(baseUrl)
                                .ignoreContentType(true)
                                .execute()
                            captionsText = captionsResponse.body()
                            
                            // Clean up XML captions
                            captionsText = captionsText.replace(Regex("<[^>]*>"), " ")
                                .replace("&amp;", "&")
                                .replace("&quot;", "\"")
                                .replace("&#39;", "'")
                                .replace("  ", " ")
                                .trim()
                            
                            Log.d("YouTubeService", "Successfully extracted captions")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("YouTubeService", "Error parsing captions JSON", e)
                    }
                }
            }
            
            if (captionsText.isEmpty()) {
                Log.e("YouTubeService", "No captions found in any script tags")
                throw IllegalStateException("No captions found for video ID: $videoId")
            }
            
            captionsText
        } catch (e: Exception) {
            Log.e("YouTubeService", "Failed to fetch captions", e)
            throw IllegalStateException("Failed to fetch captions: ${e.message}", e)
        }
    }
}
