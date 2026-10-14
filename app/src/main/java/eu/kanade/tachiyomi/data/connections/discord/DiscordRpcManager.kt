// AM (DISCORD) -->
package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.discord.socialsdk.AuthenticationClientCallback
import com.discord.socialsdk.NativeCalls
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Data class representing a Discord user fetched from the API.
 */
data class DiscordUser(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String?,
)

/**
 * Manages Discord Rich Presence using the official Discord Partner SDK via JNI.
 *
 * This implementation uses the safe OAuth PKCE flow through the Discord Android app,
 * instead of the dangerous self-bot WebSocket approach that can get accounts banned.
 *
 * Flow:
 * 1. [init] — loads native library, calls nativeInit, starts callback coroutine
 * 2. [authorize] — opens Discord app for OAuth consent via NativeCalls.authorize
 * 3. Token exchange via PKCE (no client secret needed)
 * 4. [setActivity] — sends Rich Presence data via native SDK
 */
object DiscordRpcManager {
    private const val TAG = "DiscordRpcManager"
    private val APP_ID = BuildConfig.DISCORD_APP_ID
    private const val SCOPES = "identify openid sdk.social_layer_presence"
    private val REDIRECT_URI = "discord-$APP_ID:///authorize/callback"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val AUTH_URL = "https://discord.com/oauth2/authorize"

    private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile private var authorizedInternal = false

    @Volatile private var readyInternal = false

    @Volatile private var accessToken: String? = null

    @Volatile private var refreshToken: String? = null

    @Volatile private var expiresAt: Long = 0L

    @Volatile private var lastAutoRefreshTime = 0L

    private val _accessTokenFlow = MutableStateFlow<String?>(null)
    val accessTokenFlow: StateFlow<String?> = _accessTokenFlow

    private var callbackJob: Job? = null

    private val _connectionStatus = MutableStateFlow(Status.Disconnected)
    val connectionStatus: StateFlow<Status> = _connectionStatus

    private val _settingsChanged = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = _settingsChanged

    fun notifySettingsChanged() {
        _settingsChanged.value++
    }

    enum class Status { Disconnected, Authorizing, Connected }

    enum class StatusType(val value: Int) {
        Online(0),
        Idle(3),
        Dnd(4),
    }

    @JvmStatic
    fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
        synchronized(this) {
            Log.i(TAG, "onNativeStatusChanged: statusCode=$statusCode ready=$ready authorized=$authorized")
            readyInternal = ready
            authorizedInternal = authorized
            _connectionStatus.value = when {
                ready && authorized -> Status.Connected
                statusCode == 0 || (!ready && !authorized) -> Status.Disconnected
                else -> Status.Authorizing
            }

            if (!authorized && !ready && initialized.get()) {
                val rt = getRefreshToken()
                val now = System.currentTimeMillis()
                if (!rt.isNullOrBlank() && (now - lastAutoRefreshTime > 60_000L)) {
                    lastAutoRefreshTime = now
                    Log.i(
                        TAG,
                        "onNativeStatusChanged: disconnected & unauthorized with refresh token available, attempting auto-refresh",
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        if (refreshAccessTokenSync()) {
                            val newToken = accessToken
                            if (newToken != null) {
                                applyTokenAndConnect(newToken)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getAccessToken(): String? = accessToken

    fun getRefreshToken(): String? {
        refreshToken?.let { return it }
        val stored = DiscordTokenStore.retrieveRefreshToken()
        if (!stored.isNullOrBlank()) {
            refreshToken = stored
            return stored
        }
        try {
            val connectionsManager = Injekt.get<ConnectionsManager>()
            val activeAccount = connectionsManager.discord.getAccounts().firstOrNull { it.isActive }
            val rt = activeAccount?.refreshToken
            if (!rt.isNullOrBlank()) {
                refreshToken = rt
                DiscordTokenStore.storeRefreshToken(rt)
                return rt
            }
        } catch (_: Exception) {
            // Ignore if Injekt is not initialized
        }
        return null
    }

    fun getExpiresAt(): Long {
        if (expiresAt > 0L) return expiresAt
        val stored = DiscordTokenStore.retrieveExpiresAt()
        if (stored > 0L) {
            expiresAt = stored
            return stored
        }
        try {
            val connectionsManager = Injekt.get<ConnectionsManager>()
            val activeAccount = connectionsManager.discord.getAccounts().firstOrNull { it.isActive }
            if (activeAccount != null && activeAccount.expiresAt > 0L) {
                expiresAt = activeAccount.expiresAt
                DiscordTokenStore.storeExpiresAt(activeAccount.expiresAt)
                return activeAccount.expiresAt
            }
        } catch (_: Exception) {
            // Ignore if Injekt is not initialized
        }
        return 0L
    }

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeSetActivity(
        activityType: Int,
        name: String?,
        state: String?,
        details: String?,
        startSecs: Long,
        endSecs: Long,
        largeImage: String?,
        largeText: String?,
        smallImage: String?,
        smallText: String?,
        button1Label: String?,
        button1Url: String?,
        button2Label: String?,
        button2Url: String?,
    )
    private external fun nativeSetOnlineStatus(statusType: Int)
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDestroy()
    private external fun nativeDisconnect()
    private external fun nativeIsAuthorized(): Boolean
    private external fun nativeIsReady(): Boolean

    fun isInitialized(): Boolean = initialized.get()
    fun isAuthorized(): Boolean = authorizedInternal
    fun isReady(): Boolean = readyInternal

    fun init(context: Context) {
        DiscordTokenStore.init(context.applicationContext)
        if (!initialized.compareAndSet(false, true)) {
            Log.i(TAG, "init: already initialized, skipping")
            return
        }
        Log.i(TAG, "init: loading native library 'animetail_discord'")
        try {
            System.loadLibrary("animetail_discord")
            Log.i(TAG, "init: native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "init: Failed to load native library", e)
            initialized.set(false)
            return
        }
        Log.i(TAG, "init: calling nativeInit(appId=$APP_ID)")
        if (!nativeInit(APP_ID)) {
            Log.w(TAG, "init: nativeInit returned false")
            initialized.set(false)
            return
        }
        Log.i(TAG, "init: nativeInit succeeded")
        _connectionStatus.value = Status.Disconnected

        // Retrieve stored token to immediately populate flow
        val storedToken = DiscordTokenStore.retrieve()
        val storedRefreshToken = DiscordTokenStore.retrieveRefreshToken()
        val storedExpiresAt = DiscordTokenStore.retrieveExpiresAt()

        if (storedToken != null) {
            accessToken = storedToken
            refreshToken = storedRefreshToken
            expiresAt = storedExpiresAt
            _accessTokenFlow.value = storedToken
            Log.i(TAG, "init: populated accessTokenFlow with stored token (length=${storedToken.length})")
        } else {
            try {
                val connectionsManager = Injekt.get<ConnectionsManager>()
                val activeAccount = connectionsManager.discord.getAccounts().firstOrNull { it.isActive }
                if (activeAccount != null && activeAccount.token.isNotBlank()) {
                    accessToken = activeAccount.token
                    refreshToken = activeAccount.refreshToken
                    expiresAt = activeAccount.expiresAt
                    _accessTokenFlow.value = activeAccount.token
                    val expiresInSeconds = if (activeAccount.expiresAt > System.currentTimeMillis()) {
                        (activeAccount.expiresAt - System.currentTimeMillis()) / 1000L
                    } else {
                        0L
                    }
                    DiscordTokenStore.store(activeAccount.token, activeAccount.refreshToken, expiresInSeconds)
                    Log.i(TAG, "init: recovered token from active DiscordAccount (${activeAccount.username})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "init: unable to recover token from DiscordAccount", e)
            }
        }

        Log.i(TAG, "init: starting callback processing coroutine")
        callbackJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    nativeRunCallbacks()
                } catch (e: Exception) {
                    Log.w(TAG, "coroutine: error in callback processing iteration", e)
                }
                delay(1000)
            }
        }
        Log.i(TAG, "init: coroutine launched, returning")
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Initiates the OAuth PKCE authorization flow.
     * Opens the Discord Android app for user consent.
     *
     * @param onComplete callback with true on success, false on failure
     */
    fun authorize(onComplete: (Boolean) -> Unit) {
        if (!initialized.get()) {
            Log.w(TAG, "authorize: skipping — not initialized")
            onComplete(false)
            return
        }

        Log.i(TAG, "authorize: setting status=Authorizing, starting PKCE flow")
        _connectionStatus.value = Status.Authorizing
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val oauthUrl = "$AUTH_URL" +
            "?client_id=$APP_ID" +
            "&response_type=code" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge"

        Log.i(TAG, "authorize: calling NativeCalls.authorize with oauthUrl")

        val callback = object : AuthenticationClientCallback(0) {
            private val codeVerifier = verifier
            private var callbackFired = false

            override fun onAuthorizationComplete(error: String?, authCode: String?, state: String?) {
                if (callbackFired) {
                    Log.w(TAG, "authorize: callback already fired, ignoring duplicate")
                    return
                }
                callbackFired = true

                if (!error.isNullOrEmpty()) {
                    Log.e(TAG, "authorize: onAuthorizationComplete returned error=$error")
                    onComplete(false)
                    return
                }

                if (authCode.isNullOrEmpty()) {
                    Log.w(TAG, "authorize: onAuthorizationComplete returned null/empty authCode")
                    onComplete(false)
                    return
                }

                Log.i(TAG, "authorize: got authCode (length=${authCode.length}), exchanging for token")
                exchangeCodeForToken(authCode, codeVerifier, onComplete)
            }
        }

        try {
            NativeCalls.authorize(oauthUrl, callback)
            Log.i(TAG, "authorize: NativeCalls.authorize returned successfully")
        } catch (e: Exception) {
            Log.e(TAG, "authorize: NativeCalls.authorize threw exception", e)
            onComplete(false)
        }
    }

    private fun exchangeCodeForToken(
        authCode: String,
        codeVerifier: String,
        onComplete: (Boolean) -> Unit,
    ) {
        Thread {
            try {
                val body = "client_id=$APP_ID" +
                    "&grant_type=authorization_code" +
                    "&code=${java.net.URLEncoder.encode(authCode, "UTF-8")}" +
                    "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                    "&code_verifier=$codeVerifier"

                Log.i(TAG, "exchange: POSTing to $TOKEN_URL")
                val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                Log.i(TAG, "exchange: responseCode=$responseCode body.length=${responseBody.length}")

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val newAccessToken = json.optString("access_token")
                    val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                    val expiresIn = json.optLong("expires_in", 604800L)
                    if (newAccessToken.isNotEmpty()) {
                        Log.i(
                            TAG,
                            "exchange: got access_token (length=${newAccessToken.length}), calling nativeSetTokenAndConnect",
                        )
                        val newExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                        synchronized(this@DiscordRpcManager) {
                            accessToken = newAccessToken
                            refreshToken = newRefreshToken
                            expiresAt = newExpiresAt
                            _accessTokenFlow.value = newAccessToken
                        }
                        DiscordTokenStore.store(newAccessToken, newRefreshToken, expiresIn)
                        nativeSetTokenAndConnect(newAccessToken)
                        _connectionStatus.value = Status.Authorizing
                        Handler(Looper.getMainLooper()).post {
                            Log.i(TAG, "exchange: posting nativeConnect to main thread")
                            nativeConnect()
                            onComplete(true)
                        }
                        return@Thread
                    } else {
                        Log.w(TAG, "exchange: response 200 but no access_token in body: ${responseBody.take(200)}")
                    }
                } else {
                    Log.w(TAG, "exchange: HTTP $responseCode body=${responseBody.take(500)}")
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "exchange: exception during token exchange", e)
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            }
        }.apply { name = "DiscordTokenExchange" }.start()
    }

    /**
     * Fetches the current Discord user info using the OAuth access token.
     * Must be called from a background thread.
     */
    fun fetchCurrentUser(token: String): DiscordUser? {
        return try {
            val url = URL("https://discord.com/api/v10/users/@me")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                Log.w(TAG, "fetchCurrentUser: HTTP $responseCode body=$responseBody")
                return null
            }

            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val username = json.getString("username")
            val name = json.optString("global_name", username)
            val avatarHash = json.optString("avatar")
            val avatar = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
            } else {
                null
            }

            DiscordUser(id, username, name, avatar)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCurrentUser: exception", e)
            null
        }
    }

    /**
     * Sets the Rich Presence activity via the native Discord Partner SDK.
     */
    fun setActivity(activity: DiscordNativeActivity) {
        synchronized(this) {
            if (!readyInternal) {
                Log.w(TAG, "setActivity: skipping — ready=false, activity name=${activity.name}")
                return
            }
        }
        Log.i(
            TAG,
            "setActivity: type=${activity.activityType} name=${activity.name} state=${activity.state} details=${activity.details}",
        )
        nativeSetActivity(
            activity.activityType,
            activity.name, activity.state, activity.details,
            activity.startTimestamp, activity.endTimestamp ?: 0L,
            activity.largeImage, activity.largeText,
            activity.smallImage, activity.smallText,
            activity.button1Label, activity.button1Url,
            activity.button2Label, activity.button2Url,
        )
    }

    fun setOnlineStatus(status: StatusType) {
        synchronized(this) {
            if (!readyInternal) {
                Log.w(TAG, "setOnlineStatus: skipping — ready=false")
                return
            }
        }
        Log.i(TAG, "setOnlineStatus: status=$status")
        nativeSetOnlineStatus(status.value)
    }

    fun clear() {
        synchronized(this) {
            if (!readyInternal) {
                Log.w(TAG, "clear: skipping — ready=false")
                return
            }
        }
        Log.i(TAG, "clear: calling nativeClear")
        nativeClear()
    }

    /**
     * Refreshes the OAuth access token using the stored refresh token.
     * Synchronous / blocking call — must be called from a background thread or coroutine IO.
     *
     * @return true if token refresh was successful, false otherwise.
     */
    fun refreshAccessTokenSync(): Boolean {
        val currentRefreshToken = getRefreshToken()
        if (currentRefreshToken.isNullOrBlank()) {
            Log.w(TAG, "refreshAccessToken: no refresh token available")
            return false
        }

        return try {
            Log.i(TAG, "refreshAccessToken: requesting new token from Discord")
            val body = "client_id=$APP_ID" +
                "&grant_type=refresh_token" +
                "&refresh_token=${java.net.URLEncoder.encode(currentRefreshToken, "UTF-8")}"

            val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            Log.i(TAG, "refreshAccessToken: responseCode=$responseCode body.length=${responseBody.length}")

            if (responseCode in 200..299) {
                val json = JSONObject(responseBody)
                val newAccessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() } ?: currentRefreshToken
                val expiresIn = json.optLong("expires_in", 604800L)

                if (newAccessToken.isNotEmpty()) {
                    Log.i(TAG, "refreshAccessToken: successfully obtained new access token")
                    val newExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    synchronized(this) {
                        accessToken = newAccessToken
                        refreshToken = newRefreshToken
                        expiresAt = newExpiresAt
                        _accessTokenFlow.value = newAccessToken
                    }

                    DiscordTokenStore.store(newAccessToken, newRefreshToken, expiresIn)

                    try {
                        val connectionsManager = Injekt.get<ConnectionsManager>()
                        connectionsManager.discord.updateToken(newAccessToken, newRefreshToken, newExpiresAt)
                    } catch (e: Exception) {
                        Log.w(TAG, "refreshAccessToken: failed to update ConnectionsManager account", e)
                    }

                    true
                } else {
                    Log.w(TAG, "refreshAccessToken: 200 OK but access_token was empty")
                    false
                }
            } else {
                Log.e(TAG, "refreshAccessToken: failed HTTP $responseCode: $responseBody")
                if (responseCode == 400 || responseCode == 401) {
                    if (responseBody.contains("invalid_grant")) {
                        Log.w(TAG, "refreshAccessToken: invalid_grant, refresh token revoked")
                        logout()
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken: exception during token refresh", e)
            false
        }
    }

    suspend fun refreshAccessToken(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        refreshAccessTokenSync()
    }

    fun refreshAndReconnectSync(): Boolean {
        val success = refreshAccessTokenSync()
        if (success) {
            val token = accessToken
            if (token != null) {
                reconnectWithToken(token)
            }
        }
        return success
    }

    suspend fun refreshAndReconnect(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        refreshAndReconnectSync()
    }

    fun reconnectWithToken(token: String) {
        synchronized(this) {
            if (!initialized.get()) {
                Log.w(TAG, "reconnectWithToken: skipping — not initialized")
                return
            }
            val rt = getRefreshToken()
            if (DiscordTokenStore.isExpired() && !rt.isNullOrBlank()) {
                Log.i(TAG, "reconnectWithToken: token is expired, refreshing in background")
                CoroutineScope(Dispatchers.IO).launch {
                    if (refreshAccessTokenSync()) {
                        val newToken = accessToken
                        if (newToken != null) {
                            applyTokenAndConnect(newToken)
                        }
                    } else {
                        Log.w(TAG, "reconnectWithToken: refresh failed, attempting fallback connect with current token")
                        applyTokenAndConnect(token)
                    }
                }
                return
            }
            applyTokenAndConnect(token)
        }
    }

    private fun applyTokenAndConnect(token: String) {
        synchronized(this) {
            Log.i(TAG, "applyTokenAndConnect: calling nativeSetTokenAndConnect (token length=${token.length})")
            accessToken = token
            _accessTokenFlow.value = token
            DiscordTokenStore.store(token)
            _connectionStatus.value = Status.Authorizing
        }
        Log.i(TAG, "applyTokenAndConnect: set status=Authorizing, posting nativeConnect")
        nativeSetTokenAndConnect(token)
        Handler(Looper.getMainLooper()).post {
            Log.i(TAG, "applyTokenAndConnect: executing nativeConnect on main thread")
            nativeConnect()
        }
    }

    fun destroy() {
        synchronized(this) {
            if (!initialized.get()) {
                Log.i(TAG, "destroy: skipping — not initialized")
                return
            }
            Log.i(
                TAG,
                "destroy: entering (ready=$readyInternal, authorized=$authorizedInternal, initialized=$initialized)",
            )
            readyInternal = false
            authorizedInternal = false
            initialized.set(false)
            callbackJob?.cancel()
            callbackJob = null
            nativeDestroy()
            Log.i(TAG, "destroy: complete")
        }
    }

    fun disconnect() {
        synchronized(this) {
            if (!initialized.get()) {
                Log.i(TAG, "disconnect: skipping — not initialized")
                return
            }
            Log.i(TAG, "disconnect: entering (ready=$readyInternal, authorized=$authorizedInternal)")
            _connectionStatus.value = Status.Disconnected
            readyInternal = false
            authorizedInternal = false
        }
        nativeDisconnect()
        Log.i(TAG, "disconnect: complete")
    }

    fun logout() {
        Log.i(TAG, "logout: entering")
        disconnect()
        accessToken = null
        refreshToken = null
        expiresAt = 0L
        _accessTokenFlow.value = null
        DiscordTokenStore.clear()
        Log.i(TAG, "logout: complete, accessToken cleared")
    }
}
// <-- AM (DISCORD)
