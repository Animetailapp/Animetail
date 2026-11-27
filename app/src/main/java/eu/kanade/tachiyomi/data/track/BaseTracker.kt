package eu.kanade.tachiyomi.data.track

import android.content.Context
import androidx.annotation.CallSuper
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat

abstract class BaseTracker(
    override val id: Long,
    override val name: String,
    override val dependencies: TrackerDependencies,
) : Tracker {

    val trackPreferences: TrackPreferences = dependencies.trackPreferences
    val networkService: NetworkHelper by dependencies.networkHelper
    protected val context: Context = dependencies.context

    override val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    override val supportsReadingDates: Boolean = false

    override val supportsPrivateTracking: Boolean = false

    @CallSuper
    override fun logout() {
        trackPreferences.setCredentials(this, "", "")
    }

    override val isLoggedIn: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    override val isLoggedInFlow: Flow<Boolean> by lazy {
        combine(
            trackPreferences.trackUsername(this).changes(),
            trackPreferences.trackPassword(this).changes(),
        ) { username, password ->
            username.isNotEmpty() && password.isNotEmpty()
        }
    }

    final override val isRefreshingFlow: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = MutableStateFlow(false)

    final override val refreshResultFlow: SharedFlow<RefreshResult>
        field: MutableSharedFlow<RefreshResult> = MutableSharedFlow(extraBufferCapacity = 1)

    final override suspend fun refreshUser() {
        isRefreshingFlow.value = true
        try {
            updateUserConfig()
            refreshResultFlow.emit(RefreshResult.Success)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to update user config id=$id" }
            refreshResultFlow.emit(RefreshResult.Error(e.message ?: "Failed with unknown error"))
        } finally {
            isRefreshingFlow.value = false
        }
    }

    // does the actual remote calls shielded from outside access to guarantee proper refresh flow setting
    protected abstract suspend fun updateUserConfig()

    override fun getUsername() = trackPreferences.trackUsername(this).get()

    override fun getDisplayUsername(): String = trackPreferences.trackDisplayUsername(this).get()

    override fun saveDisplayUsername(displayName: String) = trackPreferences.trackDisplayUsername(this).set(displayName)

    override fun getPassword() = trackPreferences.trackPassword(this).get()

    override fun saveCredentials(username: String, password: String) {
        trackPreferences.setCredentials(this, username, password)
    }
}

sealed interface RefreshResult {
    data object Success : RefreshResult
    data class Error(val msg: String) : RefreshResult
}
