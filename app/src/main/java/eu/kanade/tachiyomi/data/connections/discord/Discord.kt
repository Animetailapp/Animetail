// AM (DISCORD) -->

// Taken from Animiru. Thank you Quickdev for permission!

package eu.kanade.tachiyomi.data.connections.discord

import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Discord(id: Long) : ConnectionsService(id) {

    @StringRes
    override fun nameRes() = R.string.connections_discord

    override fun getLogo() = R.drawable.ic_discord_24dp

    @Suppress("MagicNumber")
    override fun getLogoColor() = Color.rgb(88, 101, 242)

    override fun logout() {
        super.logout()
        connectionsPreferences.connectionsToken(this).delete()
        DiscordRpcManager.logout()
    }

    override suspend fun login(username: String, password: String) {
        // Not Needed
    }

    private val json: Json by injectLazy()

    fun getAccounts(): List<DiscordAccount> {
        val accountsJson = connectionsPreferences.discordAccounts().get()
        return try {
            if (accountsJson.isNotBlank()) {
                json.decodeFromString<List<DiscordAccount>>(accountsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAccount(account: DiscordAccount) {
        val accounts = getAccounts().toMutableList()
        logcat(LogPriority.DEBUG) { "Debug: Adding account: $account" }

        if (account.isActive) {
            accounts.replaceAll { it.copy(isActive = false) }
            connectionsPreferences.connectionsToken(this).set(account.token)
            DiscordTokenStore.store(
                token = account.token,
                refreshToken = account.refreshToken,
                expiresInSeconds = if (account.expiresAt > System.currentTimeMillis()) {
                    (account.expiresAt - System.currentTimeMillis()) / 1000L
                } else {
                    0L
                },
            )
        }

        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }

        logcat(LogPriority.DEBUG) { "Debug: Updated accounts: $accounts" } // Debug log
        saveAccounts(accounts)
    }

    fun removeAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == accountId }
        saveAccounts(accounts)
    }

    fun setActiveAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.replaceAll { it.copy(isActive = it.id == accountId) }
        saveAccounts(accounts)
        // Update active token and restart RPC
        accounts.find { it.id == accountId }?.let { account ->
            connectionsPreferences.connectionsToken(this).set(account.token)
            DiscordTokenStore.store(
                token = account.token,
                refreshToken = account.refreshToken,
                expiresInSeconds = if (account.expiresAt > System.currentTimeMillis()) {
                    (account.expiresAt - System.currentTimeMillis()) / 1000L
                } else {
                    0L
                },
            )
            // Trigger RPC restart
            connectionsPreferences.enableDiscordRPC().set(false)
            connectionsPreferences.enableDiscordRPC().set(true)
        }
    }

    fun updateToken(token: String, refreshToken: String?, expiresAt: Long) {
        val accounts = getAccounts().toMutableList()
        val activeIndex = accounts.indexOfFirst { it.isActive }
        if (activeIndex >= 0) {
            val active = accounts[activeIndex]
            accounts[activeIndex] = active.copy(
                token = token,
                refreshToken = refreshToken ?: active.refreshToken,
                expiresAt = if (expiresAt > 0) expiresAt else active.expiresAt,
            )
            connectionsPreferences.connectionsToken(this).set(token)
            saveAccounts(accounts)
        }
    }

    fun restartRichPresence() {
        // Trigger RPC restart by toggling the preference
        connectionsPreferences.enableDiscordRPC().set(false)
        connectionsPreferences.enableDiscordRPC().set(true)
    }

    private fun saveAccounts(accounts: List<DiscordAccount>) {
        try {
            val accountsJson = json.encodeToString(accounts)
            connectionsPreferences.discordAccounts().set(accountsJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
// <-- AM (DISCORD)
