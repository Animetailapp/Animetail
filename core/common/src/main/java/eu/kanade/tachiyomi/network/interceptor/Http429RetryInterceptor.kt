package eu.kanade.tachiyomi.network.interceptor

import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * An OkHttp interceptor that handles HTTP 429 (Too Many Requests) errors
 * by implementing exponential backoff with jitter retry logic.
 *
 * This interceptor will automatically retry requests that fail with HTTP 429
 * using an exponential backoff strategy to avoid overwhelming the server.
 *
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @param baseDelayMs Base delay in milliseconds for the first retry (default: 1000ms)
 * @param maxDelayMs Maximum delay in milliseconds between retries (default: 30000ms)
 */
class Http429RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30000L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var retryCount = 0

        while (response.code == 429 && retryCount < maxRetries) {
            response.close()
            retryCount++

            val delayMs = calculateDelay(retryCount)
            logcat(LogPriority.WARN) {
                "HTTP 429 received for ${request.url}. Retrying in ${delayMs}ms (attempt $retryCount/$maxRetries)"
            }

            try {
                Thread.sleep(delayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Request interrupted during retry delay", e)
            }

            response = chain.proceed(request)
        }

        if (response.code == 429) {
            logcat(LogPriority.ERROR) {
                "HTTP 429 received for ${request.url}. Max retries ($maxRetries) exceeded."
            }
        }

        return response
    }

    /**
     * Calculates the delay for the next retry using exponential backoff with jitter.
     * 
     * The formula is: min(maxDelay, baseDelay * (2^retryCount)) + jitter
     * where jitter is a random value between 0 and 1000ms to avoid thundering herd.
     */
    private fun calculateDelay(retryCount: Int): Long {
        val exponentialDelay = baseDelayMs * (2.0.pow(retryCount - 1)).toLong()
        val delayWithCap = min(exponentialDelay, maxDelayMs)
        val jitter = Random.nextLong(0, 1000) // Add up to 1 second of jitter
        return delayWithCap + jitter
    }
}

/**
 * Extension function to add HTTP 429 retry interceptor to OkHttpClient.Builder
 *
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @param baseDelayMs Base delay in milliseconds for the first retry (default: 1000ms)
 * @param maxDelayMs Maximum delay in milliseconds between retries (default: 30000ms)
 */
fun okhttp3.OkHttpClient.Builder.addHttp429RetryInterceptor(
    maxRetries: Int = 3,
    baseDelayMs: Long = 1000L,
    maxDelayMs: Long = 30000L,
) = addInterceptor(Http429RetryInterceptor(maxRetries, baseDelayMs, maxDelayMs))
