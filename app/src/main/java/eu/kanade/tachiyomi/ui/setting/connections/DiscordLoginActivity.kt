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
    private var tokenExtracted = false

    companion object {
        private const val TAG = "DiscordLogin"
        private const val TOKEN_EXTRACTION_DELAY = 3000L
        private const val MAX_RETRY_ATTEMPTS = 8
        private const val MIN_TOKEN_LENGTH = 50
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
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
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
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
        if (url == null || tokenExtracted) return

        // Attempt token extraction on Discord app pages
        if (url.endsWith("/app") || url.contains("/channels/")) {
            Log.i(TAG, "Discord app page detected, attempting token extraction...")
            startTokenExtractionWithRetry(webView, 0)
        }
    }

    /**
     * Starts token extraction with retry mechanism.
     */
    private fun startTokenExtractionWithRetry(webView: WebView, attempt: Int) {
        if (tokenExtracted || attempt >= MAX_RETRY_ATTEMPTS) {
            if (!tokenExtracted && attempt >= MAX_RETRY_ATTEMPTS) {
                Log.e(TAG, "Max retry attempts reached, token extraction failed")
                handleLoginError("No se pudo extraer el token después de varios intentos")
            }
            return
        }

        webView.postDelayed({
            extractToken(webView, attempt)
        }, TOKEN_EXTRACTION_DELAY)
    }

    /**
     * Extracts Discord token using webpack chunks method.
     */
    private fun extractToken(webView: WebView, attempt: Int) {
        Log.i(TAG, "Token extraction attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS")

        webView.evaluateJavascript(getTokenExtractionScript()) { result ->
            val token = result?.trim('"')?.replace("\\\"", "") ?: ""
            Log.i(TAG, "Extraction result length: ${token.length}")

            if (isValidToken(token)) {
                Log.i(TAG, "Valid token found on attempt ${attempt + 1}")
                tokenExtracted = true
                verifyAndSaveToken(token)
            } else {
                Log.w(TAG, "Invalid token on attempt ${attempt + 1}, retrying...")
                startTokenExtractionWithRetry(webView, attempt + 1)
            }
        }
    }

    /**
     * Returns the JavaScript code for token extraction using multiple methods.
     */
    private fun getTokenExtractionScript(): String {
        return """
            (function() {
                // Find webpack chunk array dynamically
                var chunkName = Object.keys(window).find(function(k) {
                    return k.startsWith('webpackChunk');
                });
                if (!chunkName) return 'NO_TOKEN';

                var chunks = window[chunkName];
                if (!chunks || !chunks.push) return 'NO_TOKEN';

                var token = null;

                // Method 1: Inject into webpack require and scan all modules
                try {
                    var id = Symbol();
                    chunks.push([[id], {}, function(e) {
                        for (var key in e.c) {
                            if (!e.c.hasOwnProperty(key)) continue;
                            var mod = e.c[key];
                            if (!mod || !mod.exports) continue;
                            var ex = mod.exports;

                            // Check default export
                            if (ex.default && typeof ex.default.getToken === 'function') {
                                try {
                                    var t = ex.default.getToken();
                                    if (t && t.length > 50) { token = t; return; }
                                } catch(err) {}
                            }

                            // Check Z export (common in newer Discord builds)
                            if (ex.Z && typeof ex.Z.getToken === 'function') {
                                try {
                                    var t = ex.Z.getToken();
                                    if (t && t.length > 50) { token = t; return; }
                                } catch(err) {}
                            }

                            // Check ZP export
                            if (ex.ZP && typeof ex.ZP.getToken === 'function') {
                                try {
                                    var t = ex.ZP.getToken();
                                    if (t && t.length > 50) { token = t; return; }
                                } catch(err) {}
                            }

                            // Check direct getToken on exports
                            if (typeof ex.getToken === 'function') {
                                try {
                                    var t = ex.getToken();
                                    if (t && t.length > 50) { token = t; return; }
                                } catch(err) {}
                            }

                            // Scan all export properties for getToken
                            if (typeof ex === 'object') {
                                for (var prop in ex) {
                                    if (!ex.hasOwnProperty(prop)) continue;
                                    var val = ex[prop];
                                    if (val && typeof val.getToken === 'function') {
                                        try {
                                            var t = val.getToken();
                                            if (t && t.length > 50) { token = t; return; }
                                        } catch(err) {}
                                    }
                                }
                            }
                        }
                    }]);
                    // Clean up by removing our injected chunk
                    chunks.pop();
                } catch (e) {
                    console.log('Webpack scan failed:', e);
                }

                if (token) return token;

                // Method 2: Search iframe localStorage
                try {
                    var iframe = document.createElement('iframe');
                    iframe.style.display = 'none';
                    document.body.appendChild(iframe);
                    var iframeToken = iframe.contentWindow.localStorage.getItem('token');
                    iframe.remove();
                    if (iframeToken) {
                        var cleaned = JSON.parse(iframeToken);
                        if (cleaned && cleaned.length > 50) return cleaned;
                    }
                } catch (e) {
                    console.log('iframe localStorage failed:', e);
                }

                return 'NO_TOKEN';
            })()
        """.trimIndent()
    }

    /**
     * Validates if the extracted token meets Discord token format requirements.
     */
    private fun isValidToken(token: String): Boolean {
        return token.isNotEmpty() &&
            token != "NO_TOKEN" &&
            token != "null" &&
            token != "undefined" &&
            token.length > MIN_TOKEN_LENGTH &&
            // Discord token format: XXX.YYY.ZZZ (Base64-like segments)
            token.matches(Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"))
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
            val body = response.body.string()
            Log.i(TAG, "API response received successfully")
            parseUserInfo(body)
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
