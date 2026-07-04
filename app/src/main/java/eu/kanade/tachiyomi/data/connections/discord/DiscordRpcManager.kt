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
    @Volatile private var _authorized = false
    @Volatile private var _ready = false
    @Volatile private var accessToken: String? = null

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
            _ready = ready
            _authorized = authorized
            _connectionStatus.value = when {
                ready && authorized -> Status.Connected
                statusCode == 0 || (!ready && !authorized) -> Status.Disconnected
                else -> Status.Authorizing
            }
        }
    }

    fun getAccessToken(): String? = accessToken

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeSetActivity(
        activityType: Int,
        name: String?, state: String?, details: String?,
        startSecs: Long, endSecs: Long,
        largeImage: String?, largeText: String?,
        smallImage: String?, smallText: String?,
        button1Label: String?, button1Url: String?,
        button2Label: String?, button2Url: String?,
    )
    private external fun nativeSetOnlineStatus(statusType: Int)
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDestroy()
    private external fun nativeDisconnect()
    private external fun nativeIsAuthorized(): Boolean
    private external fun nativeIsReady(): Boolean

    fun isInitialized(): Boolean = initialized.get()
    fun isAuthorized(): Boolean = _authorized
    fun isReady(): Boolean = _ready

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
        if (storedToken != null) {
            accessToken = storedToken
            _accessTokenFlow.value = storedToken
            Log.i(TAG, "init: populated accessTokenFlow with stored token (length=${storedToken.length})")
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
                    if (newAccessToken.isNotEmpty()) {
                        Log.i(TAG, "exchange: got access_token (length=${newAccessToken.length}), calling nativeSetTokenAndConnect")
                        this@DiscordRpcManager.accessToken = newAccessToken
                        _accessTokenFlow.value = newAccessToken
                        DiscordTokenStore.store(newAccessToken)
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
            if (!_ready) {
                Log.w(TAG, "setActivity: skipping — _ready=false, activity name=${activity.name}")
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
            if (!_ready) {
                Log.w(TAG, "setOnlineStatus: skipping — _ready=false")
                return
            }
        }
        Log.i(TAG, "setOnlineStatus: status=$status")
        nativeSetOnlineStatus(status.value)
    }

    fun clear() {
        synchronized(this) {
            if (!_ready) {
                Log.w(TAG, "clear: skipping — _ready=false")
                return
            }
        }
        Log.i(TAG, "clear: calling nativeClear")
        nativeClear()
    }

    fun reconnectWithToken(token: String) {
        synchronized(this) {
            if (!initialized.get()) {
                Log.w(TAG, "reconnectWithToken: skipping — not initialized")
                return
            }
            Log.i(TAG, "reconnectWithToken: calling nativeSetTokenAndConnect (token length=${token.length})")
            accessToken = token
            _accessTokenFlow.value = token
            DiscordTokenStore.store(token)
            _connectionStatus.value = Status.Authorizing
        }
        Log.i(TAG, "reconnectWithToken: set status=Authorizing, posting nativeConnect")
        nativeSetTokenAndConnect(token)
        Handler(Looper.getMainLooper()).post {
            Log.i(TAG, "reconnectWithToken: executing nativeConnect on main thread")
            nativeConnect()
        }
    }

    fun destroy() {
        synchronized(this) {
            if (!initialized.get()) {
                Log.i(TAG, "destroy: skipping — not initialized")
                return
            }
            Log.i(TAG, "destroy: entering (_ready=$_ready, _authorized=$_authorized, initialized=$initialized)")
            _ready = false
            _authorized = false
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
            Log.i(TAG, "disconnect: entering (_ready=$_ready, _authorized=$_authorized)")
            _connectionStatus.value = Status.Disconnected
            _ready = false
            _authorized = false
        }
        nativeDisconnect()
        Log.i(TAG, "disconnect: complete")
    }

    fun logout() {
        Log.i(TAG, "logout: entering")
        disconnect()
        accessToken = null
        _accessTokenFlow.value = null
        DiscordTokenStore.clear()
        Log.i(TAG, "logout: complete, accessToken cleared")
    }
}
// <-- AM (DISCORD)
