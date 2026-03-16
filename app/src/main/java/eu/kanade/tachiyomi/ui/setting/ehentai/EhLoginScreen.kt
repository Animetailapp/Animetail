package eu.kanade.tachiyomi.ui.setting.ehentai

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.HttpCookie
import java.util.Locale

/**
 * Full-screen WebView login for E-Hentai / ExHentai, 1:1 with Komikku.
 *
 * Step 1 — forums.e-hentai.org login page:
 *   Wait for ipb_member_id + ipb_pass_hash to appear in CookieManager.
 *   When present, navigate to exhentai.org.
 *
 * Step 2 — exhentai.org:
 *   Extract ipb_member_id, ipb_pass_hash, igneous from CookieManager.
 *   Save credentials and pop back.
 */
class EhLoginScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }

        // Clear all existing cookies so we start fresh
        val loading by produceState(true) {
            CookieManager.getInstance().removeAllCookies {
                value = false
            }
        }

        val state = rememberWebViewState(
            url = "https://forums.e-hentai.org/index.php?act=Login",
        )
        val webNavigator = rememberWebViewNavigator()

        Scaffold(
            topBar = {
                Box {
                    AppBar(
                        title = stringResource(MR.strings.login_title, "E-Hentai"),
                        navigateUp = { navigator.pop() },
                        navigationIcon = Icons.Outlined.Close,
                    )
                    when (val loadingState = state.loadingState) {
                        is LoadingState.Initializing -> LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        )
                        is LoadingState.Loading -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = loadingState.progress,
                                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                                label = "eh_login_progress",
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                            )
                        }
                        else -> {}
                    }
                }
            },
        ) { contentPadding ->
            if (loading) {
                return@Scaffold
            }

            val webClient = remember {
                object : AccompanistWebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        url ?: return
                        val parsedUrl = android.net.Uri.parse(url)
                        val host = parsedUrl.host ?: return

                        when {
                            // ── Step 1: forums — wait for login cookies, then go to ExHentai ──
                            host.equals("forums.e-hentai.org", ignoreCase = true) -> {
                                // Hide distracting forum chrome, show only the login form
                                view.evaluateJavascript(HIDE_JS, null)

                                // Check that login cookies exist (same as Komikku checkLoginCookies)
                                if (checkLoginCookies(url)) {
                                    state.content = WebContent.Url("https://exhentai.org/")
                                }
                            }

                            // ── Step 2: ExHentai — extract all three cookies and save ──────────
                            host.equals("exhentai.org", ignoreCase = true) -> {
                                if (applyExHentaiCookies(url, trackerManager, sourcePreferences)) {
                                    val hasExH = sourcePreferences.ehUseExHentai().get()
                                    if (hasExH) {
                                        context.toast(TLMR.strings.eh_login_success_exhentai)
                                    } else {
                                        context.toast(TLMR.strings.eh_login_success_ehentai_only)
                                    }
                                    navigator.pop()
                                }
                            }
                        }
                    }
                }
            }

            Box(Modifier.padding(contentPadding)) {
                com.kevinnzou.web.WebView(
                    state = state,
                    navigator = webNavigator,
                    modifier = Modifier.fillMaxSize(),
                    onCreated = { webView ->
                        webView.setDefaultSettings()
                    },
                    client = webClient,
                )
            }
        }
    }

    // ── Helpers (1:1 with Komikku EhLoginActivity) ────────────────────────────

    private fun getCookies(url: String): List<HttpCookie>? =
        CookieManager.getInstance().getCookie(url)?.let { raw ->
            raw.split("; ").flatMap {
                runCatching { HttpCookie.parse(it) }.getOrElse { emptyList() }
            }
        }

    /** Returns true when both ipb_member_id and ipb_pass_hash are present and non-blank. */
    private fun checkLoginCookies(url: String): Boolean {
        return getCookies(url)?.count {
            (
                it.name.equals(MEMBER_ID_COOKIE, ignoreCase = true) ||
                    it.name.equals(PASS_HASH_COOKIE, ignoreCase = true)
                ) && it.value.isNotBlank()
        }?.let { it >= 2 } ?: false
    }

    /** Reads cookies from ExHentai and saves credentials. Returns true on success. */
    private fun applyExHentaiCookies(
        url: String,
        trackerManager: TrackerManager,
        sourcePreferences: SourcePreferences,
    ): Boolean {
        val parsed = getCookies(url) ?: return false

        var memberId: String? = null
        var passHash: String? = null
        var igneous: String? = null
        var settingsKey: String? = null
        var sessionCookie: String? = null
        var hathPerks: String? = null

        parsed.forEach {
            when (it.name.lowercase(Locale.getDefault())) {
                MEMBER_ID_COOKIE -> memberId = it.value
                PASS_HASH_COOKIE -> passHash = it.value
                IGNEOUS_COOKIE -> igneous = it.value
                "sk" -> settingsKey = it.value
                "s" -> sessionCookie = it.value
                "hath_perks" -> hathPerks = it.value
            }
        }

        if (memberId.isNullOrBlank() || passHash.isNullOrBlank()) return false
        if (memberId == "0") return false

        val hasExHentaiAccess = !igneous.isNullOrBlank() &&
            !igneous.equals("mystery", ignoreCase = true)

        // Always save E-Hentai credentials (memberId + passHash)
        // Use empty string for igneous if not available — E-Hentai still works without it
        trackerManager.eHentai.loginWithCookies(
            memberId!!,
            passHash!!,
            if (hasExHentaiAccess) igneous!! else "",
        )

        // Auto-enable or auto-disable ExHentai based on whether igneous is real
        sourcePreferences.ehUseExHentai().set(hasExHentaiAccess)

        // Persist optional cookies — mirrors Komikku rawCookies sk/s/hath_perks
        if (hasExHentaiAccess) {
            settingsKey?.takeIf { it.isNotBlank() }?.let { sourcePreferences.exhSettingsKey().set(it) }
            sessionCookie?.takeIf { it.isNotBlank() }?.let { sourcePreferences.exhSessionCookie().set(it) }
            hathPerks?.takeIf { it.isNotBlank() }?.let { sourcePreferences.exhHathPerksCookies().set(it) }
        }

        return true
    }

    companion object {
        const val MEMBER_ID_COOKIE = "ipb_member_id"
        const val PASS_HASH_COOKIE = "ipb_pass_hash"
        const val IGNEOUS_COOKIE = "igneous"

        /**
         * JavaScript injected into the forums login page to hide distracting
         * content and show only the login form — identical to Komikku's HIDE_JS.
         */
        const val HIDE_JS = """
            javascript:(function () {
                document.getElementsByTagName('body')[0].style.visibility = 'hidden';
                document.getElementsByName('submit')[0].style.visibility = 'visible';
                document.querySelector('td[width="60%"][valign="top"]').style.visibility = 'visible';

                function hide(e) {if(e != null) e.style.display = 'none';}

                hide(document.querySelector(".errorwrap"));
                hide(document.querySelector('td[width="40%"][valign="top"]'));
                var child = document.querySelector(".page").querySelector('div');
                child.style.padding = null;
                var ft = child.querySelectorAll('table');
                var fd = child.parentNode.querySelectorAll('div > div');
                var fh = document.querySelector('#border').querySelectorAll('td > table');
                hide(ft[0]);
                hide(ft[1]);
                hide(fd[1]);
                hide(fd[2]);
                hide(child.querySelector('br'));
                var error = document.querySelector(".page > div > .borderwrap");
                if(error != null) error.style.visibility = 'visible';
                hide(fh[0]);
                hide(fh[1]);
                hide(document.querySelector("#gfooter"));
                hide(document.querySelector(".copyright"));
                document.querySelectorAll("td").forEach(function(e) {
                    e.style.color = "white";
                });
                var pc = document.querySelector(".postcolor");
                if(pc != null) pc.style.color = "#26353F";
            })()
        """
    }
}
