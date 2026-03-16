package eu.kanade.tachiyomi.data.track.ehentai

import android.graphics.Color
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

/**
 * E-Hentai/ExHentai login service.
 *
 * Not a full manga progress tracker — it only manages authentication credentials
 * (ipb_member_id + ipb_pass_hash) used by E-Hentai/ExHentai extensions.
 */
class EHentai(id: Long) : BaseTracker(id, "E-Hentai") {

    companion object {
        const val EHENTAI_ID = 10L
    }

    private val sourcePreferences: SourcePreferences by injectLazy()

    private val api by lazy { EHentaiApi(client) }

    // region Tracker identity

    override fun getLogo(): Int = R.drawable.ic_tracker_ehentai

    override fun getLogoColor(): Int = Color.rgb(123, 111, 165) // #7B6FA5

    override fun getCompletionStatus(): Long = 0L

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    // endregion

    // region Authentication
    //
    // Credentials are stored as:
    //   username  → E-Hentai username (visible in login dialog)
    //   password  → ipb_pass_hash cookie (treated as opaque token)
    //   sourcePreferences.ehMemberId() → ipb_member_id cookie
    //
    // Both memberId AND passHash must be present to be considered "logged in".

    override val isLoggedIn: Boolean
        get() = sourcePreferences.ehMemberId().get().isNotBlank() && getPassword().isNotBlank()

    override val isLoggedInFlow: Flow<Boolean> by lazy {
        combine(
            trackPreferences.trackPassword(this).changes(),
            sourcePreferences.ehMemberId().changes(),
        ) { passHash, memberId ->
            passHash.isNotBlank() && memberId.isNotBlank()
        }
    }

    override suspend fun login(username: String, password: String) {
        // Not used — cookie-based login via loginWithCookies()
    }

    /**
     * Saves E-Hentai session cookies directly (no HTTP request, bypasses Cloudflare).
     *
     * The user retrieves [memberId] and [passHash] from their browser cookies after
     * logging in to e-hentai.org manually.
     */
    /**
     * Saves E-Hentai session cookies directly from the WebView CookieManager.
     * [igneous] is required for ExHentai access.
     */
    fun loginWithCookies(memberId: String, passHash: String, igneous: String) {
        saveCredentials("Member #$memberId", passHash)
        sourcePreferences.ehMemberId().set(memberId)
        sourcePreferences.ehIgneous().set(igneous)
    }

    override fun logout() {
        super.logout()
        sourcePreferences.ehMemberId().set("")
        sourcePreferences.ehIgneous().set("")
        // Also clear optional session cookies captured during WebView login
        sourcePreferences.exhSettingsKey().set("")
        sourcePreferences.exhSessionCookie().set("")
        sourcePreferences.exhHathPerksCookies().set("")
        // Disable ExHentai mode on logout — mirrors Komikku's enableExhentai().set(false)
        sourcePreferences.ehUseExHentai().set(false)
    }

    /** Returns the stored ipb_member_id cookie value, or empty string if not logged in. */
    fun getMemberId(): String = sourcePreferences.ehMemberId().get()

    /** Returns the stored ipb_pass_hash cookie value (same as [getPassword]). */
    fun getPassHash(): String = getPassword()

    // endregion

    // region Unused tracker methods (E-Hentai is a service, not a progress tracker)

    override suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata? = null

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? = null

    // endregion
}
