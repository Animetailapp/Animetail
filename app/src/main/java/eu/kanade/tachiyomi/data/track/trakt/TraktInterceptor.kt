package eu.kanade.tachiyomi.data.track.trakt

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Interceptor that ensures requests include Trakt auth and attempts token refresh on expiry.
 * It requires a reference to the Trakt tracker instance to access persisted tokens and refresh logic.
 */
class TraktInterceptor(
    private val trakt: Trakt,
    accessToken: String? = null,
    private val clientId: String = Trakt.CLIENT_ID,
) : Interceptor {

    private val tokenRef = AtomicReference<String?>(accessToken)

    fun setAuth(token: String?) {
        tokenRef.set(token)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (tokenRef.get().isNullOrEmpty()) {
            trakt.restoreToken()?.let { saved ->
                tokenRef.set(saved.access_token)
            }
        }

        if (tokenRef.get().isNullOrEmpty()) {
            throw IOException("Not authenticated with Trakt")
        }

        try {
            val saved = trakt.restoreToken()
            if (saved != null) {
                val expiresAt = (saved.created_at * 1000L) + (saved.expires_in * 1000L)
                // Refresh 60 seconds before expiry
                if (System.currentTimeMillis() > (expiresAt - 60_000L)) {
                    val refreshed = trakt.refreshAuthBlocking()
                    if (!refreshed) {
                        trakt.logout()
                        throw IOException("Token expired")
                    } else {
                        // tokenRef updated by setAuth inside trakt.refreshAuthBlocking()
                    }
                }
            }
        } catch (e: Exception) {
            trakt.logout()
            throw IOException("Failed to refresh Trakt token", e)
        }

        // and avoid duplicate headers (which can cause Trakt to reject the request).
        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)

        tokenRef.get()?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }
}
