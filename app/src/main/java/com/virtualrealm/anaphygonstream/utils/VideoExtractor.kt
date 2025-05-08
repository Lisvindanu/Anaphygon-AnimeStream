// Add this file: app/src/main/java/com/virtualrealm/anaphygonstream/utils/VideoExtractor.kt

package com.virtualrealm.anaphygonstream.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {
    private const val TAG = "VideoExtractor"

    // Cache extracted URLs to avoid repeated requests
    private val extractedUrls = mutableMapOf<String, String>()

    suspend fun extractVideoUrl(playerUrl: String): String? {
        // Check cache first
        if (extractedUrls.containsKey(playerUrl)) {
            return extractedUrls[playerUrl]
        }

        try {
            Log.d(TAG, "Extracting video from: $playerUrl")

            // Specialized extractors based on domain
            val mediaUrl = when {
                playerUrl.contains("desustream") -> extractFromDesustream(playerUrl)
                playerUrl.contains("yourupload") -> extractFromYourupload(playerUrl)
                playerUrl.contains("streamsb") -> extractFromStreamsb(playerUrl)
                playerUrl.contains("streamtape") -> extractFromStreamtape(playerUrl)
                else -> null
            }

            // Cache the result if successful
            if (!mediaUrl.isNullOrEmpty()) {
                extractedUrls[playerUrl] = mediaUrl
            }

            return mediaUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video: ${e.message}", e)
            return null
        }
    }

    private suspend fun extractFromDesustream(url: String): String? {
        val html = fetchHtml(url) ?: return null

        // Look for the source variable in JavaScript
        // Pattern: var source = "https://example.com/video.mp4";
        val sourcePattern = "var\\s+source\\s*=\\s*[\"'](https?://[^\"']+)[\"']"
        val sourceMatcher = Pattern.compile(sourcePattern).matcher(html)
        if (sourceMatcher.find()) {
            return sourceMatcher.group(1)
        }

        // Alternative pattern for file attribute
        // Pattern: file: "https://example.com/video.mp4"
        val filePattern = "file\\s*:\\s*[\"'](https?://[^\"']+)[\"']"
        val fileMatcher = Pattern.compile(filePattern).matcher(html)
        if (fileMatcher.find()) {
            return fileMatcher.group(1)
        }

        // Generic pattern for m3u8 or mp4 URLs
        val genericPattern = "[\"'](https?://[^\"']+\\.(mp4|m3u8)[^\"']*)[\"']"
        val genericMatcher = Pattern.compile(genericPattern).matcher(html)
        if (genericMatcher.find()) {
            return genericMatcher.group(1)
        }

        return null
    }

    private suspend fun extractFromYourupload(url: String): String? {
        val html = fetchHtml(url) ?: return null

        // YouUpload specific pattern
        val pattern = "file:\\s*\"(https?://[^\"]+\\.mp4)\""
        val matcher = Pattern.compile(pattern).matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        return null
    }

    private suspend fun extractFromStreamsb(url: String): String? {
        // StreamSB is more complex and uses obfuscation
        // This is a simplified approach
        val html = fetchHtml(url) ?: return null

        val pattern = "sources:\\s*\\[\\{file:\\s*\"(https?://[^\"]+)\"\\}"
        val matcher = Pattern.compile(pattern).matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        return null
    }

    private suspend fun extractFromStreamtape(url: String): String? {
        val html = fetchHtml(url) ?: return null

        // Streamtape-specific extraction
        val pattern = "document\\.getElementById\\('videolink'\\)\\.innerHTML\\s*=\\s*'([^']+)'"
        val matcher = Pattern.compile(pattern).matcher(html)
        if (matcher.find()) {
            // Streamtape usually needs to construct the final URL
            val partialLink = matcher.group(1).trim()
            return "https:" + partialLink
        }

        return null
    }

    private suspend fun fetchHtml(url: String): String? {
        return try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://otakudesu.cloud/")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "cross-site")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch HTML: ${response.code}")
                return null
            }

            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HTML: ${e.message}", e)
            null
        }
    }
}