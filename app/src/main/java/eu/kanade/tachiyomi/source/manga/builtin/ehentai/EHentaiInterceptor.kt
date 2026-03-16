package eu.kanade.tachiyomi.source.manga.builtin.ehentai

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * OkHttp interceptor that manages E-Hentai / ExHentai cookies, 1:1 with Komikku.
 *
 * On every request the interceptor:
 *  1. Extracts any Cloudflare cookies already present on the request (cf_*, _cf_*, __cf_*).
 *  2. Builds a fresh cookie header from stored preferences, merged with those CF cookies.
 *  3. Replaces the Cookie header with the combined result.
 *
 * This mirrors Komikku's EHentai.client interceptor so CF clearance cookies obtained
 * via WebView are automatically reused on subsequent API calls.
 */
internal class EHentaiInterceptor : Interceptor {

    private val trackerManager: TrackerManager by lazy { Injekt.get() }
    private val sourcePreferences: SourcePreferences by lazy { Injekt.get() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // ── Step 1: extract Cloudflare cookies from the outgoing request ──────────
        val cfCookies: Map<String, String> = request.header("Cookie")
            ?.split("; ")
            ?.filter { part ->
                if (!part.contains("=")) return@filter false
                val name = part.substringBefore("=").trim().lowercase()
                name.startsWith("cf") || name.startsWith("_cf") || name.startsWith("__cf")
            }
            ?.associate { part ->
                part.substringBefore("=").trim() to part.substringAfter("=").trim()
            }
            ?: emptyMap()

        // ── Step 2: build base cookie map (1:1 with Komikku rawCookies) ───────────
        val cookies = mutableMapOf<String, String>()

        val eHentai = trackerManager.eHentai
        if (eHentai.isLoggedIn) {
            cookies["ipb_member_id"] = eHentai.getMemberId()
            cookies["ipb_pass_hash"] = eHentai.getPassHash()
            // Do NOT send igneous if it is "mystery" — that is ExHentai's sentinel for
            // accounts without ExHentai membership and will trigger an empty-gallery response.
            val igneous = sourcePreferences.ehIgneous().get().let { stored ->
                if (stored.isBlank() || stored.equals("mystery", ignoreCase = true)) null else stored
            }
            if (igneous != null) {
                cookies["igneous"] = igneous
            }
            // Settings profile — mirrors Komikku's sp cookie (controls EH server display settings)
            val sp = sourcePreferences.ehSettingsProfile().get().toIntOrNull() ?: 0
            cookies["sp"] = sp.toString()

            // Optional cookies — only sent when the user has them (e.g. sk, s, hath_perks)
            val sk = sourcePreferences.exhSettingsKey().get()
            if (sk.isNotBlank()) cookies["sk"] = sk

            val s = sourcePreferences.exhSessionCookie().get()
            if (s.isNotBlank()) cookies["s"] = s

            val hathPerks = sourcePreferences.exhHathPerksCookies().get()
            if (hathPerks.isNotBlank()) cookies["hath_perks"] = hathPerks
        }

        // "sl=dm_2" enables extended display mode without requiring an ExHentai account
        cookies["sl"] = "dm_2"
        // Bypass "You must be 18 to enter" interstitial
        cookies["nw"] = "1"

        // ── Step 3: merge CF cookies (they override nothing, they are additive) ───
        val merged = cookies + cfCookies

        val newCookieHeader = merged.entries.joinToString("; ") { (k, v) -> "$k=$v" }

        val newRequest = request.newBuilder()
            .removeHeader("Cookie")
            .apply { if (newCookieHeader.isNotBlank()) addHeader("Cookie", newCookieHeader) }
            .build()

        return chain.proceed(newRequest)
    }
}
