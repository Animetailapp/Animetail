package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.track.comick.ComickMissingScopesException
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        when (data?.host) {
            "anilist-auth" -> handleAnilist(data)
            "comick-auth" -> handleComick(data)
            "bangumi-auth" -> handleBangumi(data)
            "mangabaka-auth" -> handleMangaBaka(data)
            "myanimelist-auth" -> handleMyAnimeList(data)
            "shikimori-auth" -> handleShikimori(data)
            "simkl-auth" -> handleSimkl(data)
            "trakt-auth" -> handleTrakt(data)
            "tmdb-auth" -> handleTmdb(data)
            "hikka-auth" -> handleHikka(data)
        }
    }

    private fun handleAnilist(data: Uri) {
        val regex = "(?:access_token=)(.*?)(?:&)".toRegex()
        val matchResult = regex.find(data.fragment.toString())
        if (matchResult?.groups?.get(1) != null) {
            lifecycleScope.launchIO {
                trackerManager.aniList.login(matchResult.groups[1]!!.value)
                returnToSettings()
            }
        } else {
            trackerManager.aniList.logout()
            returnToSettings()
        }
    }

    private fun handleComick(data: Uri) {
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        if (state == null) {
            logcat(LogPriority.WARN) { "Did not receive state parameter from Comick OAuth" }
            returnToSettings()
            return
        }
        if (code != null) {
            if (!trackerManager.comick.verifyOAuthState(state)) {
                logcat(LogPriority.WARN) { "Received wrong OAuth state back from Comick" }
                returnToSettings()
                return
            }
            lifecycleScope.launchIO {
                try {
                    trackerManager.comick.login(code)
                } catch (e: ComickMissingScopesException) {
                    withContext(Dispatchers.Main) {
                        this@TrackLoginActivity.toast(e.message, Toast.LENGTH_LONG)
                    }
                }
                returnToSettings()
            }
        } else {
            trackerManager.comick.logout()
            returnToSettings()
        }
    }

    private fun handleBangumi(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.bangumi.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.bangumi.logout()
            returnToSettings()
        }
    }

    private fun handleMangaBaka(data: Uri) {
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        if (code != null && state != null) {
            if (!trackerManager.mangaBaka.verifyOAuthState(state)) {
                return
            }
            lifecycleScope.launchIO {
                trackerManager.mangaBaka.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.mangaBaka.logout()
            returnToSettings()
        }
    }

    private fun handleMyAnimeList(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.myAnimeList.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.myAnimeList.logout()
            returnToSettings()
        }
    }

    private fun handleShikimori(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.shikimori.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.shikimori.logout()
            returnToSettings()
        }
    }

    private fun handleSimkl(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.simkl.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.simkl.logout()
            returnToSettings()
        }
    }

    private fun handleTrakt(data: Uri) {
        val code = data.getQueryParameter("code") ?: data.fragment?.let { frag ->
            val regex = "(?:code=)([^&]+)".toRegex()
            regex.find(frag)?.groups?.get(1)?.value
        }

        if (code != null) {
            lifecycleScope.launchIO {
                try {
                    trackerManager.trakt.login(code)
                } finally {
                    returnToSettings()
                }
            }
        } else {
            trackerManager.trakt.logout()
            returnToSettings()
        }
    }

    private fun handleTmdb(data: Uri) {
        val requestToken = data.getQueryParameter("request_token")
        if (requestToken != null) {
            lifecycleScope.launchIO {
                trackerManager.tmdb.login(requestToken, "")
                returnToSettings()
            }
        } else {
            trackerManager.tmdb.logout()
            returnToSettings()
        }
    }

    private fun handleHikka(data: Uri) {
        val reference = data.getQueryParameter("reference")
        if (reference != null) {
            lifecycleScope.launchIO {
                trackerManager.hikka.login(reference)
                returnToSettings()
            }
        } else {
            trackerManager.hikka.logout()
            returnToSettings()
        }
    }
}
