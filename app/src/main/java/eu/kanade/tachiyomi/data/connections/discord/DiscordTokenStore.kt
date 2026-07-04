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

    fun store(token: String) {
        prefs?.edit()?.putString(TOKEN_KEY, token)?.apply()
    }

    fun retrieve(): String? = prefs?.getString(TOKEN_KEY, null)

    fun clear() {
        prefs?.edit()?.remove(TOKEN_KEY)?.apply()
    }
}
// <-- AM (DISCORD)
