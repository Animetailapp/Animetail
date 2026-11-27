package eu.kanade.tachiyomi.ui.webview

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.webview.WebViewScreenContent
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        WebViewScreenBody(
            url = url,
            initialTitle = initialTitle,
            sourceId = sourceId,
            onNavigateUp = { navigator.pop() },
            onUrlChange = { assistUrl = it },
        )
    }
}

@Composable
fun WebViewScreenBody(
    url: String,
    initialTitle: String?,
    sourceId: Long?,
    onNavigateUp: () -> Unit,
    onUrlChange: (String) -> Unit,
) {
    val context = LocalContext.current

    if (!remember { WebViewUtil.supportsWebView(context) }) {
        LaunchedEffect(Unit) {
            context.toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            onNavigateUp()
        }
        return
    }

    val viewModel =
        assistedMetroViewModel<WebViewViewModel, WebViewViewModel.Factory> { create(sourceId = sourceId) }

    val headers by viewModel.headers.collectAsState()
    if (headers == null) {
        LoadingScreen()
        return
    }

    WebViewScreenContent(
        onNavigateUp = onNavigateUp,
        initialTitle = initialTitle,
        url = url,
        headers = headers.orEmpty(),
        defaultUserAgentProvider = viewModel::defaultUserAgentProvider,
        onUrlChange = onUrlChange,
        onShare = { viewModel.shareWebpage(context, it) },
        onOpenInBrowser = { viewModel.openInBrowser(context, it) },
        onClearCookies = viewModel::clearCookies,
    )
}
