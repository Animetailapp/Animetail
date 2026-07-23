package eu.kanade.tachiyomi.ui.download.anime

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import kotlin.math.roundToInt

@Composable
fun AnimeDownloadQueueScreen(
    contentPadding: PaddingValues,
    scope: CoroutineScope,
    viewModel: AnimeDownloadQueueViewModel,
    downloadList: List<AnimeDownloadHeaderItem>,
    nestedScrollConnection: NestedScrollConnection,
) {
    Scaffold {
        if (downloadList.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_no_downloads,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val left = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx().roundToInt() }
        val top = with(density) { contentPadding.calculateTopPadding().toPx().roundToInt() }
        val right = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx().roundToInt() }
        val bottom = with(density) { contentPadding.calculateBottomPadding().toPx().roundToInt() }

        Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    viewModel.controllerBinding = DownloadListBinding.inflate(
                        LayoutInflater.from(context),
                    )
                    viewModel.adapter = AnimeDownloadAdapter(viewModel.listener)
                    viewModel.controllerBinding.root.adapter = viewModel.adapter
                    viewModel.adapter?.isHandleDragEnabled = true
                    viewModel.controllerBinding.root.layoutManager = LinearLayoutManager(
                        context,
                    )

                    ViewCompat.setNestedScrollingEnabled(viewModel.controllerBinding.root, true)

                    scope.launchUI {
                        viewModel.getDownloadStatusFlow()
                            .collect(viewModel::onStatusChange)
                    }
                    scope.launchUI {
                        viewModel.getDownloadProgressFlow()
                            .collect(viewModel::onUpdateDownloadedPages)
                    }

                    viewModel.controllerBinding.root
                },
                update = {
                    viewModel.controllerBinding.root
                        .updatePadding(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                        )

                    viewModel.adapter?.updateDataSet(downloadList)
                },
            )
        }
    }
}
