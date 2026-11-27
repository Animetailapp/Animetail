package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.browse.manga.MangaExtensionScreen
import eu.kanade.presentation.browse.manga.PrivateMangaExtensionUninstallDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import mihon.core.navigation.ExtensionStoresRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaExtensionDetailsRoute
import mihon.core.navigation.MangaExtensionFilterRoute
import mihon.core.navigation.WebViewRoute
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun mangaExtensionsTab(
    extensionsViewModel: MangaExtensionsViewModel,
): TabContent {
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val updatesCount by extensionsViewModel.updatesCount.collectAsStateWithLifecycle()
    var privateExtensionToUninstall by remember { mutableStateOf<MangaExtension.Installed?>(null) }

    return TabContent(
        titleRes = AYMR.strings.label_manga_extensions,
        badgeNumber = updatesCount.takeIf { it > 0 },
        searchEnabled = true,
        actions = listOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(MangaExtensionFilterRoute) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.extensionStores),
                onClick = { navigator.push(ExtensionStoresRoute(isManga = true)) },
            ),
        ),
        content = { contentPadding, _ ->
            val state by extensionsViewModel.state.collectAsStateWithLifecycle()

            BackHandler(enabled = state.searchQuery != null) {
                extensionsViewModel.search(null)
            }

            MangaExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    when (extension) {
                        is MangaExtension.Available -> extensionsViewModel.installExtension(extension)

                        is MangaExtension.Installed -> {
                            if (context.isPackageInstalled(extension.pkgName)) {
                                extensionsViewModel.uninstallExtension(extension)
                            } else {
                                privateExtensionToUninstall = extension
                            }
                        }
                    }
                },
                onClickItemCancel = extensionsViewModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsViewModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewRoute(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsViewModel::installExtension,
                onOpenExtension = { navigator.push(MangaExtensionDetailsRoute(it.pkgName)) },
                onTrustExtension = { extensionsViewModel.trustExtension(it) },
                onUninstallExtension = { extensionsViewModel.uninstallExtension(it) },
                onUpdateExtension = extensionsViewModel::updateExtension,
                onRefresh = extensionsViewModel::findAvailableExtensions,
            )

            privateExtensionToUninstall?.let { extension ->
                PrivateMangaExtensionUninstallDialog(
                    extension = extension,
                    onConfirm = {
                        extensionsViewModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }
        },
    )
}
