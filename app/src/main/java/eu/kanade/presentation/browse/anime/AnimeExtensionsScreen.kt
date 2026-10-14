package eu.kanade.presentation.browse.anime

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.browse.BaseBrowseItem
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.components.ExtensionPill
import eu.kanade.presentation.browse.components.label
import eu.kanade.presentation.browse.manga.ExtensionHeader
import eu.kanade.presentation.browse.manga.ExtensionInstallErrorDialog
import eu.kanade.presentation.browse.manga.ExtensionSplitButton
import eu.kanade.presentation.browse.manga.ExtensionTrustDialog
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsViewModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import mihon.domain.extension.model.ExtensionStore
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.VerifiedUser
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun AnimeExtensionScreen(
    state: AnimeExtensionsViewModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onLongClickItem: (AnimeExtension) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onOpenWebView: (AnimeExtension.Available) -> Unit,
    onInstallExtension: (AnimeExtension.Available) -> Unit,
    onUninstallExtension: (AnimeExtension.Installed) -> Unit,
    onUpdateExtension: (AnimeExtension.Installed) -> Unit,
    onTrustExtension: (AnimeExtension.NotLoaded) -> Unit,
    onOpenExtension: (AnimeExtension.Loaded) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow

    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))

            state.isEmpty -> {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.empty_screen
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                    actions = listOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.extensionStores,
                            icon = MaterialSymbols.Rounded.Settings,
                            onClick = { navigator.push(ExtensionStoresScreen(isManga = false)) },
                        ),
                    ),
                )
            }

            else -> {
                AnimeExtensionContent(
                    state = state,
                    contentPadding = contentPadding,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onOpenWebView = onOpenWebView,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                )
            }
        }
    }
}

@Composable
private fun AnimeExtensionContent(
    state: AnimeExtensionsViewModel.State,
    contentPadding: PaddingValues,
    onLongClickItem: (AnimeExtension) -> Unit,
    onOpenWebView: (AnimeExtension.Available) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onInstallExtension: (AnimeExtension.Available) -> Unit,
    onUninstallExtension: (AnimeExtension.Installed) -> Unit,
    onUpdateExtension: (AnimeExtension.Installed) -> Unit,
    onTrustExtension: (AnimeExtension.NotLoaded) -> Unit,
    onOpenExtension: (AnimeExtension.Loaded) -> Unit,
    onClickUpdateAll: () -> Unit,
) {
    val context = LocalContext.current
    var notLoadedState by remember { mutableStateOf<AnimeExtension.NotLoaded?>(null) }
    var installErrorState by remember { mutableStateOf<InstallStep.Error?>(null) }
    val installGranted = rememberRequestPackageInstallsPermissionState(initialValue = true)

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        if (!installGranted && state.installer?.requiresSystemPermission == true) {
            item(key = "extension-permissions-warning") {
                WarningBanner(
                    textRes = MR.strings.ext_permission_install_apps_warning,
                    modifier = Modifier.clickable {
                        context.launchRequestPackageInstallsPermission()
                    },
                )
            }
        }

        state.items.forEach { (header, items) ->
            item(
                contentType = "header",
                key = "extensionHeader-${header.hashCode()}",
            ) {
                when (header) {
                    is AnimeExtensionUiModel.Header.Resource -> {
                        val action: @Composable RowScope.() -> Unit =
                            if (header.textRes == MR.strings.ext_updates_pending) {
                                {
                                    Button(onClick = { onClickUpdateAll() }) {
                                        Text(
                                            text = stringResource(MR.strings.ext_update_all),
                                            style = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                {}
                            }
                        ExtensionHeader(
                            textRes = header.textRes,
                            modifier = Modifier.animateItem(),
                            action = action,
                        )
                    }

                    is AnimeExtensionUiModel.Header.Text -> {
                        ExtensionHeader(
                            text = header.text,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            items(
                items = items,
                contentType = { "item" },
                key = { item ->
                    when (item.extension) {
                        is AnimeExtension.NotLoaded -> "extension-not-loaded-${item.hashCode()}"
                        is AnimeExtension.Loaded -> "extension-loaded-${item.hashCode()}"
                        is AnimeExtension.Available -> "extension-available-${item.hashCode()}"
                    }
                },
            ) { item ->
                AnimeExtensionItem(
                    item = item,
                    modifier = Modifier.animateItem(),
                    onClickItem = {
                        when (it) {
                            is AnimeExtension.Available -> onInstallExtension(it)

                            is AnimeExtension.Loaded -> onOpenExtension(it)

                            is AnimeExtension.NotLoaded -> {
                                notLoadedState = it
                            }
                        }
                    },
                    onLongClickItem = onLongClickItem,
                    onClickItemSecondaryAction = {
                        when (it) {
                            is AnimeExtension.Available -> onOpenWebView(it)

                            is AnimeExtension.Loaded -> onOpenExtension(it)

                            is AnimeExtension.NotLoaded -> {
                                notLoadedState = it
                            }
                        }
                    },
                    onClickItemCancel = onClickItemCancel,
                    onClickItemError = { installErrorState = it },
                    onClickItemAction = {
                        when (it) {
                            is AnimeExtension.Available -> onInstallExtension(it)

                            is AnimeExtension.Loaded -> {
                                if (it.hasUpdate) {
                                    onUpdateExtension(it)
                                } else {
                                    onOpenExtension(it)
                                }
                            }

                            is AnimeExtension.NotLoaded -> {
                                if (it.hasUpdate) {
                                    onUpdateExtension(it)
                                } else {
                                    notLoadedState = it
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    notLoadedState?.let { extension ->
        val dismiss = { notLoadedState = null }
        if (extension.reason is AnimeExtension.NotLoaded.Reason.Untrusted) {
            ExtensionTrustDialog(
                onClickConfirm = {
                    onTrustExtension(extension)
                    dismiss()
                },
                onClickDismiss = {
                    onUninstallExtension(extension)
                    dismiss()
                },
                onDismissRequest = dismiss,
            )
        } else {
            AnimeExtensionNotLoadedDialog(
                reason = extension.reason,
                onClickUninstall = {
                    onUninstallExtension(extension)
                    dismiss()
                },
                onDismissRequest = dismiss,
            )
        }
    }
    installErrorState?.let { error ->
        ExtensionInstallErrorDialog(
            error = error,
            onDismissRequest = { installErrorState = null },
        )
    }
}

@Composable
private fun AnimeExtensionItem(
    item: AnimeExtensionUiModel.Item,
    onClickItem: (AnimeExtension) -> Unit,
    onLongClickItem: (AnimeExtension) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onClickItemError: (InstallStep.Error) -> Unit,
    onClickItemAction: (AnimeExtension) -> Unit,
    onClickItemSecondaryAction: (AnimeExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (extension, installStep) = item
    val store = when (extension) {
        is AnimeExtension.Available -> extension.store
        is AnimeExtension.Installed -> extension.store
    }

    BaseBrowseItem(
        modifier = modifier,
        onClickItem = { onClickItem(extension) },
        onLongClickItem = { onLongClickItem(extension) },
        icon = {
            AnimeExtensionItemIcon(extension = extension, installStep = installStep)
        },
        action = {
            AnimeExtensionItemActions(
                extension = extension,
                installStep = installStep,
                onClickItemCancel = onClickItemCancel,
                onClickItemError = onClickItemError,
                onClickItemAction = onClickItemAction,
                onClickItemSecondaryAction = onClickItemSecondaryAction,
            )
        },
    ) {
        AnimeExtensionItemContent(
            extension = extension,
            installStep = installStep,
            store = store,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnimeExtensionItemContent(
    extension: AnimeExtension,
    installStep: InstallStep,
    store: ExtensionStore?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = MaterialTheme.padding.medium),
    ) {
        Text(
            text = extension.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (store != null) {
            Text(
                text = store.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimeExtensionItemMetadata(extension = extension, installStep = installStep)
    }
}

@Composable
private fun AnimeExtensionItemIcon(
    extension: AnimeExtension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val idle = installStep.isCompleted()
        if (!idle) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 2.dp,
            )
        }

        val padding by animateDpAsState(
            targetValue = if (idle) 0.dp else 8.dp,
            label = "iconPadding",
        )
        AnimeExtensionIcon(
            extension = extension,
            modifier = Modifier
                .matchParentSize()
                .padding(padding),
        )
    }
}

/**
 * Plain text for what an extension simply is, pills for the few things worth noticing about it.
 * Won't look good when it wraps, but it's not like overflowing content can be ellipsized.
 */
@Composable
private fun AnimeExtensionItemMetadata(
    extension: AnimeExtension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall / 2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val facts = listOfNotNull(
            (extension as? AnimeExtension.Loaded)
                ?.lang
                ?.takeIf { it.isNotEmpty() }
                ?.let { LocaleHelper.getSourceDisplayName(it, LocalContext.current) },
            extension.versionName.takeIf { it.isNotEmpty() },
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString(" \u2022 "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val notable = listOfNotNull(
            when (installStep) {
                InstallStep.Pending -> MR.strings.ext_pending
                InstallStep.Downloading -> MR.strings.ext_downloading
                InstallStep.Installing -> MR.strings.ext_installing
                else -> null
            }?.let { it to MaterialTheme.colorScheme.primary },
            when {
                extension is AnimeExtension.NotLoaded ->
                    extension.reason.labelRes?.let { it to MaterialTheme.colorScheme.error }

                extension is AnimeExtension.Loaded && extension.isObsolete ->
                    MR.strings.ext_obsolete to MaterialTheme.colorScheme.error

                else -> null
            },
            extension.contentWarning.label?.let { it.title to it.color },
            (extension as? AnimeExtension.Loaded)
                ?.takeIf { !it.isShared }
                ?.let { MR.strings.ext_installer_private to MaterialTheme.colorScheme.onSurfaceVariant },
        )
        notable.forEach { (label, color) ->
            ExtensionPill(text = stringResource(label), color = color)
        }
    }
}

@Composable
private fun AnimeExtensionItemActions(
    extension: AnimeExtension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
    onClickItemCancel: (AnimeExtension) -> Unit = {},
    onClickItemError: (InstallStep.Error) -> Unit = {},
    onClickItemAction: (AnimeExtension) -> Unit = {},
    onClickItemSecondaryAction: (AnimeExtension) -> Unit = {},
) {
    val isUntrusted = (extension as? AnimeExtension.NotLoaded)?.reason is AnimeExtension.NotLoaded.Reason.Untrusted
    val secondaryAction = when (extension) {
        is AnimeExtension.Available -> extension.sources.takeIf { it.isNotEmpty() }
            ?.let { stringResource(MR.strings.action_open_in_web_view) }

        is AnimeExtension.Loaded -> stringResource(MR.strings.action_settings)

        is AnimeExtension.NotLoaded -> if (isUntrusted) {
            stringResource(MR.strings.ext_trust)
        } else {
            stringResource(MR.strings.ext_not_loaded_details)
        }
    }
        ?.let { it to { onClickItemSecondaryAction(extension) } }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !installStep.isCompleted() -> {
                IconButton(onClick = { onClickItemCancel(extension) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_cancel),
                    )
                }
            }

            installStep is InstallStep.Error -> {
                ExtensionSplitButton(
                    icon = MaterialSymbols.Rounded.Refresh,
                    contentDescription = stringResource(MR.strings.action_retry),
                    onClick = { onClickItemAction(extension) },
                    menuItems = listOfNotNull(
                        stringResource(MR.strings.ext_install_error_details) to { onClickItemError(installStep) },
                        secondaryAction,
                    ),
                )
            }

            extension is AnimeExtension.Available -> {
                ExtensionSplitButton(
                    icon = MaterialSymbols.Rounded.Download,
                    contentDescription = stringResource(MR.strings.ext_install),
                    onClick = { onClickItemAction(extension) },
                    menuItems = listOfNotNull(secondaryAction),
                )
            }

            extension is AnimeExtension.Installed && extension.hasUpdate -> {
                ExtensionSplitButton(
                    icon = MaterialSymbols.Rounded.Download,
                    contentDescription = stringResource(MR.strings.ext_update),
                    onClick = { onClickItemAction(extension) },
                    menuItems = listOfNotNull(secondaryAction),
                )
            }

            extension is AnimeExtension.Loaded -> {
                FilledTonalIconButton(onClick = { onClickItemSecondaryAction(extension) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Settings,
                        contentDescription = stringResource(MR.strings.action_settings),
                    )
                }
            }

            extension is AnimeExtension.NotLoaded -> {
                IconButton(onClick = { onClickItemSecondaryAction(extension) }) {
                    Icon(
                        imageVector = if (isUntrusted) {
                            MaterialSymbols.Rounded.VerifiedUser
                        } else {
                            MaterialSymbols.Rounded.Info
                        },
                        contentDescription = secondaryAction?.first,
                    )
                }
            }
        }
    }
}

/**
 * Only the reasons the user can act on are worth naming in the row; the rest all mean "broken" to
 * them and are spelled out in [AnimeExtensionNotLoadedDialog] instead.
 */
private val AnimeExtension.NotLoaded.Reason.labelRes: StringResource?
    get() = when (this) {
        is AnimeExtension.NotLoaded.Reason.Untrusted -> MR.strings.ext_untrusted

        AnimeExtension.NotLoaded.Reason.Filtered -> MR.strings.ext_filtered

        // The section header already says these aren't loaded; the dialog says why
        AnimeExtension.NotLoaded.Reason.Unsigned,
        AnimeExtension.NotLoaded.Reason.UnsupportedLibVersion,
        AnimeExtension.NotLoaded.Reason.Malformed,
        is AnimeExtension.NotLoaded.Reason.Failed,
        -> null
    }

private val AnimeExtension.NotLoaded.Reason.descriptionRes: StringResource
    get() = when (this) {
        is AnimeExtension.NotLoaded.Reason.Untrusted -> MR.strings.untrusted_extension_message
        AnimeExtension.NotLoaded.Reason.Filtered -> MR.strings.ext_filtered_message
        AnimeExtension.NotLoaded.Reason.Unsigned -> MR.strings.ext_unsigned_message
        AnimeExtension.NotLoaded.Reason.UnsupportedLibVersion -> MR.strings.ext_unsupported_message
        AnimeExtension.NotLoaded.Reason.Malformed -> MR.strings.ext_malformed_message
        is AnimeExtension.NotLoaded.Reason.Failed -> MR.strings.ext_load_failed_message
    }

@Composable
private fun AnimeExtensionNotLoadedDialog(
    reason: AnimeExtension.NotLoaded.Reason,
    onClickUninstall: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_not_loaded_dialog))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(text = stringResource(reason.descriptionRes))

                if (reason is AnimeExtension.NotLoaded.Reason.Failed) {
                    Text(
                        text = reason.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val context = LocalContext.current
                    TextButton(
                        onClick = {
                            context.copyToClipboard(
                                label = context.stringResource(MR.strings.ext_copy_stacktrace),
                                content = reason.stackTrace,
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(text = stringResource(MR.strings.ext_copy_stacktrace))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onClickUninstall) {
                Text(text = stringResource(MR.strings.ext_uninstall))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
