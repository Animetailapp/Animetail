// AM (DISCORD) -->

package eu.kanade.tachiyomi.ui.setting.connections

import android.os.Bundle
import android.util.Log
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordRpcManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordTokenStore
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

/**
 * Activity for Discord login using the official Discord Partner SDK.
 * Opens the Discord Android app for secure OAuth PKCE consent flow.
 * No WebView, no token extraction — completely safe.
 */
class DiscordLoginActivity : BaseActivity() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "DiscordLogin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize DiscordRpcManager if not already done
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(applicationContext)
        }

        Log.i(TAG, "Starting Discord OAuth PKCE authorization flow")

        // Start the OAuth PKCE flow through the Discord app
        DiscordRpcManager.authorize { success ->
            if (success) {
                Log.i(TAG, "Authorization successful, fetching user info")
                scope.launch {
                    val token = DiscordRpcManager.getAccessToken()
                    if (token != null) {
                        val user = withContext(Dispatchers.IO) {
                            DiscordRpcManager.fetchCurrentUser(token)
                        }
                        if (user != null) {
                            Log.i(TAG, "Got user info: ${user.username}")
                            saveAccount(user, token)
                            handleLoginSuccess()
                        } else {
                            Log.e(TAG, "Failed to fetch user info")
                            handleLoginError("Failed to fetch Discord user info")
                        }
                    } else {
                        Log.e(TAG, "Access token is null after successful authorization")
                        handleLoginError("Authorization succeeded but no token received")
                    }
                }
            } else {
                Log.e(TAG, "Authorization failed or was cancelled")
                handleLoginError("Discord authorization failed or was cancelled")
            }
        }
    }

    /**
     * Creates a DiscordAccount and saves it to preferences.
     */
    private fun saveAccount(user: eu.kanade.tachiyomi.data.connections.discord.DiscordUser, token: String) {
        val account = DiscordAccount(
            id = user.id,
            username = user.username,
            avatarUrl = user.avatar,
            token = token,
            isActive = true,
        )

        Log.i(TAG, "Saving Discord account: ${account.username}")

        // Save account through ConnectionsManager
        connectionsManager.discord.addAccount(account)

        // Store token in ConnectionsPreferences for backward compatibility
        connectionsPreferences.connectionsToken(connectionsManager.discord).set(token)
        connectionsPreferences.setConnectionsCredentials(
            connectionsManager.discord,
            "Discord",
            "Logged In",
        )

        // Also store in DiscordTokenStore for the native SDK
        DiscordTokenStore.store(token)
    }

    /**
     * Handles successful login completion.
     */
    private fun handleLoginSuccess() {
        toast(MR.strings.login_success)
        setResult(RESULT_OK)
        finish()
    }

    /**
     * Handles login errors.
     */
    private fun handleLoginError(message: String) {
        toast(message)
        setResult(RESULT_CANCELED)
        finish()
    }
}
// <-- AM (DISCORD)
