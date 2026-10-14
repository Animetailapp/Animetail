// AM (DISCORD) -->
package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CompletableDeferred

/**
 * Stores the Discord OAuth access token in SharedPreferences.
 * Uses regular (non-encrypted) SharedPreferences since the OAuth token
 * has limited scopes (identify, openid, sdk.social_layer_presence) and
 * is less sensitive than a user token.
 */
object DiscordTokenStore {
    private const val TAG = "DiscordTokenStore"
    private const val PREFS_NAME = "discord_token"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val EXPIRES_AT_KEY = "expires_at"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val initDeferred = CompletableDeferred<Unit>()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            try {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                initDeferred.complete(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "DiscordTokenStore init failed", e)
                initDeferred.completeExceptionally(e)
            }
        }
    }

    suspend fun retrieveSuspend(): String? {
        try {
            initDeferred.await()
        } catch (_: Exception) {
            return null
        }
        return retrieve()
    }

    fun store(token: String, refreshToken: String? = null, expiresInSeconds: Long = 0L) {
        val editor = prefs?.edit()?.putString(TOKEN_KEY, token) ?: return
        if (!refreshToken.isNullOrEmpty()) {
            editor.putString(REFRESH_TOKEN_KEY, refreshToken)
        }
        if (expiresInSeconds > 0L) {
            val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            editor.putLong(EXPIRES_AT_KEY, expiresAt)
        }
        editor.apply()
    }

    fun storeRefreshToken(refreshToken: String) {
        prefs?.edit()?.putString(REFRESH_TOKEN_KEY, refreshToken)?.apply()
    }

    fun storeExpiresAt(expiresAt: Long) {
        prefs?.edit()?.putLong(EXPIRES_AT_KEY, expiresAt)?.apply()
    }

    fun retrieve(): String? = prefs?.getString(TOKEN_KEY, null)

    fun retrieveRefreshToken(): String? = prefs?.getString(REFRESH_TOKEN_KEY, null)

    fun retrieveExpiresAt(): Long = prefs?.getLong(EXPIRES_AT_KEY, 0L) ?: 0L

    /**
     * Checks if the stored access token is expired or close to expiring (within a 10-minute safety window).
     */
    fun isExpired(): Boolean {
        val expiresAt = retrieveExpiresAt()
        if (expiresAt <= 0L) {
            return false
        }
        return System.currentTimeMillis() >= (expiresAt - 10 * 60 * 1000L)
    }

    fun clear() {
        prefs?.edit()
            ?.remove(TOKEN_KEY)
            ?.remove(REFRESH_TOKEN_KEY)
            ?.remove(EXPIRES_AT_KEY)
            ?.apply()
    }
}
// <-- AM (DISCORD)
