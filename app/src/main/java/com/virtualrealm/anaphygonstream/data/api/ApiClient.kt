package com.virtualrealm.anaphygonstream.data.api

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ApiClient {
    private const val TAG = "ApiClient"
    private const val BASE_URL = "https://wajik-anime-api.vercel.app/"
    private const val TIMEOUT_SECONDS = 30L // Increased timeout
    private const val MAX_RETRIES = 3

    // Create HTTP logging interceptor for debugging
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Custom interceptor for retries and error handling
    private val customInterceptor = okhttp3.Interceptor { chain ->
        var request = chain.request()

        // Improve headers to better mimic a browser
        request = request.newBuilder()
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Connection", "keep-alive")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Pragma", "no-cache")
            .addHeader("Sec-Fetch-Dest", "empty")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "cross-site")
            .build()

        // Implement retry logic for specific errors (403, network errors)
        var response: okhttp3.Response? = null
        var tryCount = 0
        var exception: Exception? = null
        val isCancelled = AtomicBoolean(false)

        while (tryCount < MAX_RETRIES && !isCancelled.get()) {
            try {
                // Close any previous response
                response?.close()

                // Check if the current thread is interrupted or the coroutine is cancelled
                if (Thread.currentThread().isInterrupted) {
                    isCancelled.set(true)
                    throw InterruptedException("Thread was interrupted")
                }

                // Attempt the request
                response = chain.proceed(request)

                // If we got a server error (5xx) or 403, retry
                if ((response.code in 500..599 || response.code == 403) && tryCount < MAX_RETRIES - 1) {
                    Log.w(TAG, "Server error ${response.code}, retry attempt ${tryCount + 1}/$MAX_RETRIES")
                    response.close() // Important: close the response before retrying
                    tryCount++

                    // Wait before retrying with exponential backoff
                    Thread.sleep(1000L * tryCount)
                    continue
                }

                // For other responses, return the response
                return@Interceptor response
            } catch (e: CancellationException) {
                // Propagate cancellation exceptions directly
                isCancelled.set(true)
                throw e
            } catch (e: InterruptedException) {
                // Handle thread interruption
                isCancelled.set(true)
                exception = e
                Log.w(TAG, "Thread interrupted", e)
                break
            } catch (e: Exception) {
                exception = e
                Log.w(TAG, "Request failed with ${e.javaClass.simpleName}, retry attempt ${tryCount + 1}/$MAX_RETRIES", e)
                tryCount++

                if (tryCount >= MAX_RETRIES) {
                    break
                }

                try {
                    // Wait before retrying with exponential backoff
                    Thread.sleep(1000L * tryCount)
                } catch (ie: InterruptedException) {
                    Log.w(TAG, "Sleep interrupted during retry delay", ie)
                    isCancelled.set(true)
                    break
                }
            }
        }

        // If the operation was cancelled, throw a cancellation exception
        if (isCancelled.get()) {
            throw CancellationException("Request was cancelled")
        }

        // If we exhausted all retries and still have an exception, throw it
        exception?.let { throw it }

        // Otherwise return the last response (which will be an error response)
        return@Interceptor response!!
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(customInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val animeApi: AnimeApi = retrofit.create(AnimeApi::class.java)
}