package eu.kanade.tachiyomi.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.home.HomeFeedScreen
import eu.kanade.presentation.util.Tab

import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

import cafe.adriel.voyager.core.model.rememberScreenModel

data object HomeTab : Tab {
    private fun readResolve(): Any = HomeTab

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_home),
                icon = rememberVectorPainter(Icons.Outlined.Home),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        // No-op or scroll to top
    }

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { HomeFeedScreenModel() }
        HomeFeedScreen(screenModel = screenModel)
    }
}
