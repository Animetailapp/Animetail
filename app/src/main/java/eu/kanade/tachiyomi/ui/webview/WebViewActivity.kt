package eu.kanade.tachiyomi.ui.webview

import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import tachiyomi.core.common.util.lang.launchIO

class WebViewActivity : BaseActivity() {

    private var assistUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        val url = intent.extras?.getString(URL_KEY) ?: return
        val initialTitle = intent.extras?.getString(TITLE_KEY)
        val sourceId = if (intent.hasExtra(SOURCE_KEY)) intent.getLongExtra(SOURCE_KEY, 0L) else null
        assistUrl = url
        setComposeContent {
            WebViewScreenBody(
                url = url,
                initialTitle = initialTitle,
                sourceId = sourceId,
                onNavigateUp = ::finish,
                onUrlChange = { assistUrl = it },
            )
        }
        // AM (DISCORD) -->
        lifecycleScope.launchIO {
            DiscordRPCService.setAnimeScreen(this@WebViewActivity, DiscordScreen.WEBVIEW)
            DiscordRPCService.setMangaScreen(this@WebViewActivity, DiscordScreen.WEBVIEW)
        }
        // <-- AM (DISCORD)
    }

    // AM (DISCORD) -->
    override fun onDestroy() {
        lifecycleScope.launchIO {
            DiscordRPCService.setAnimeScreen(this@WebViewActivity, DiscordRPCService.lastUsedScreen)
            DiscordRPCService.setMangaScreen(this@WebViewActivity, DiscordRPCService.lastUsedScreen)
        }
        super.onDestroy()
    }
    // <-- AM (DISCORD)

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val SOURCE_KEY = "source_key"
        private const val TITLE_KEY = "title_key"
        private const val ANIME_KEY = "anime_key"

        fun newIntent(
            context: Context,
            url: String,
            sourceId: Long? = null,
            title: String? = null,
            isAnime: Boolean = false,
        ): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(URL_KEY, url)
                sourceId?.let { putExtra(SOURCE_KEY, it) }
                putExtra(TITLE_KEY, title)
                putExtra(ANIME_KEY, isAnime)
            }
        }
    }
}
