// AM (DISCORD) -->

// Original library from https://github.com/dead8309/KizzyRPC (Thank you)
// Thank you to the 最高 man for the refactored and simplified code
// https://github.com/saikou-app/saikou
package eu.kanade.tachiyomi.ui.setting.connections

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Activity for Discord login and token extraction.
 * Uses WebView to authenticate with Discord and extract the user token for Discord RPC.
 */
class DiscordLoginActivity : BaseActivity() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    companion object {
        private const val TAG = "DiscordLogin"
        private const val TOKEN_EXTRACTION_DELAY = 3000L
        private const val MIN_TOKEN_LENGTH = 20
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.discord_login_activity)

        val webView = findViewById<WebView>(R.id.webview)
        setupWebView(webView)
        webView.loadUrl("https://discord.com/login")
    }

    /**
     * Configures the WebView with necessary settings and client.
     */
    private fun setupWebView(webView: WebView) {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.databaseEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = USER_AGENT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(TAG, "Page finished loading: $url")
                handlePageFinished(webView, url)
            }
        }
    }

    /**
     * Handles page load completion and initiates token extraction if appropriate.
     */
    private fun handlePageFinished(webView: WebView, url: String?) {
        if (url == null) return

        // Inject LOCAL_STORAGE reference on any Discord page
        if (url.contains("discord.com")) {
            injectLocalStorageReference(webView)
        }

        // Attempt token extraction on Discord app pages
        if (url.endsWith("/app") || url.contains("/channels/")) {
            Log.i(TAG, "Discord app page detected, attempting token extraction...")
            webView.postDelayed({
                extractToken(webView)
            }, TOKEN_EXTRACTION_DELAY)
        }
    }

    /**
     * Injects LOCAL_STORAGE reference into the page for reliable localStorage access.
     */
    private fun injectLocalStorageReference(webView: WebView) {
        webView.evaluateJavascript(
            """
            try {
                window.LOCAL_STORAGE = localStorage;
                console.log('LOCAL_STORAGE injected successfully');
            } catch (e) {
                console.log('Failed to inject LOCAL_STORAGE:', e);
            }
            """.trimIndent(),
        ) { }
    }

    /**
     * Extracts Discord token using the injected LOCAL_STORAGE reference.
     */
    private fun extractToken(webView: WebView) {
        Log.i(TAG, "Attempting token extraction using Flutter method")

        webView.evaluateJavascript(getTokenExtractionScript()) { result ->
            val token = result?.trim('"') ?: ""
            Log.i(TAG, "Token extraction result: ${if (token.length > 10) "${token.substring(0, 10)}..." else token}")

            if (isValidToken(token)) {
                Log.i(TAG, "Valid token found, proceeding with verification")
                verifyAndSaveToken(token)
            } else {
                Log.e(TAG, "Token extraction failed")
            }
        }
    }

    /**
     * Returns the JavaScript code for token extraction.
     */
    private fun getTokenExtractionScript(): String {
        return """
            (function() {
                let token = null;
                try {
                    if (window.LOCAL_STORAGE) {
                        const storageToken = window.LOCAL_STORAGE.getItem('token');
                        if (storageToken && storageToken.length > 10) {
                            token = storageToken.replace(/['"]/g, '');
                            console.log('Token found in injected localStorage');
                        }
                    }
                } catch (e) {
                    console.log('Token extraction failed:', e);
                }
                return token || 'NO_TOKEN';
            })()
        """.trimIndent()
    }

    /**
     * Validates if the extracted token meets minimum requirements.
     */
    private fun isValidToken(token: String): Boolean {
        return token.isNotEmpty() &&
            token != "NO_TOKEN" &&
            token != "null" &&
            token.length > MIN_TOKEN_LENGTH
    }

    /**
     * Verifies the token with Discord API and saves the account if valid.
     */
    private fun verifyAndSaveToken(token: String) {
        Log.i(TAG, "Verifying token with Discord API...")

        Thread {
            try {
                val userInfo = fetchUserInfo(token)
                if (userInfo != null) {
                    val account = createDiscordAccount(userInfo, token)
                    saveAccount(account, token)
                    handleLoginSuccess()
                } else {
                    handleLoginError("Failed to fetch user information")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during token verification", e)
                handleLoginError("Error al verificar el token: ${e.message}")
            }
        }.start()
    }

    /**
     * Fetches user information from Discord API using the token.
     */
    private fun fetchUserInfo(token: String): UserInfo? {
        val response = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder()
                .url("https://discord.com/api/v10/users/@me")
                .addHeader("Authorization", token)
                .build(),
        ).execute()

        Log.i(TAG, "Discord API response: ${response.code}")

        return if (response.isSuccessful) {
            val body = response.body?.string()
            if (body != null) {
                Log.i(TAG, "API response received successfully")
                parseUserInfo(body)
            } else {
                Log.e(TAG, "Response body is null")
                null
            }
        } else {
            Log.e(TAG, "Token verification failed: ${response.code}")
            null
        }
    }

    /**
     * Parses user information from Discord API response.
     */
    private fun parseUserInfo(responseBody: String): UserInfo {
        val jsonObject = org.json.JSONObject(responseBody)
        val id = jsonObject.getString("id")
        val username = jsonObject.getString("username")
        val avatarId = jsonObject.optString("avatar")
        val avatarUrl = if (avatarId.isNotEmpty()) {
            "https://cdn.discordapp.com/avatars/$id/$avatarId.png"
        } else {
            null
        }

        Log.i(TAG, "User verified: $username (ID: $id)")
        return UserInfo(id, username, avatarUrl)
    }

    /**
     * Creates a DiscordAccount object from user information.
     */
    private fun createDiscordAccount(userInfo: UserInfo, token: String): DiscordAccount {
        return DiscordAccount(
            id = userInfo.id,
            username = userInfo.username,
            avatarUrl = userInfo.avatarUrl,
            token = token,
            isActive = true,
        )
    }

    /**
     * Saves the Discord account and token to preferences.
     */
    private fun saveAccount(account: DiscordAccount, token: String) {
        Log.i(TAG, "Saving Discord account: ${account.username}")

        // Save account through ConnectionsManager
        connectionsManager.discord.addAccount(account)

        // Additional token storage (required for proper functionality)
        connectionsPreferences.connectionsToken(connectionsManager.discord).set(token)
        connectionsPreferences.setConnectionsCredentials(
            connectionsManager.discord,
            "Discord",
            "Logged In",
        )

        // Verify account was saved
        val savedAccounts = connectionsManager.discord.getAccounts()
        Log.i(TAG, "Accounts after save: ${savedAccounts.size} account(s)")
        savedAccounts.forEach { savedAccount ->
            Log.i(TAG, "Saved account: ${savedAccount.username} (active: ${savedAccount.isActive})")
        }
    }

    /**
     * Handles successful login completion.
     */
    private fun handleLoginSuccess() {
        runOnUiThread {
            toast(MR.strings.login_success)
            cleanupWebViewData()
            setResult(RESULT_OK)
            finish()
        }
    }

    /**
     * Handles login errors.
     */
    private fun handleLoginError(message: String) {
        runOnUiThread {
            toast(message)
        }
    }

    /**
     * Cleans up WebView data after successful login.
     */
    private fun cleanupWebViewData() {
        try {
            applicationInfo.dataDir.let {
                File("$it/app_webview/").deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup WebView data", e)
        }
    }

    /**
     * Data class to hold user information from Discord API.
     */
    private data class UserInfo(
        val id: String,
        val username: String,
        val avatarUrl: String?,
    )
}
// <-- AM (DISCORD)
