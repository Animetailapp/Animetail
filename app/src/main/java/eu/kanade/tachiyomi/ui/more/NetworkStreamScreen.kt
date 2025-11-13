package eu.kanade.tachiyomi.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import aniyomi.util.trimOrNull
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.network.NetworkStreamRequest
import eu.kanade.tachiyomi.ui.player.network.NetworkStreamRequest.NetworkHeader
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.core.common.i18n.stringResource as coreStringResource

/**
 * Screen that allows users to launch arbitrary network streams inside the built-in player.
 */
object NetworkStreamScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

        var url by rememberSaveable { mutableStateOf("") }
        var title by rememberSaveable { mutableStateOf("") }
        var subtitleUrl by rememberSaveable { mutableStateOf("") }
        var subtitleLabel by rememberSaveable { mutableStateOf("") }
        var referer by rememberSaveable { mutableStateOf("") }
        var userAgent by rememberSaveable { mutableStateOf("") }
        var headersRaw by rememberSaveable { mutableStateOf("") }

        val isUrlValid = remember(url) { url.isValidStreamUrl() }
        val isSubtitleValid = remember(subtitleUrl) {
            subtitleUrl.isBlank() || subtitleUrl.isValidStreamUrl()
        }
        val canPlay = isUrlValid && isSubtitleValid

        Scaffold(
            topBarScrollBehavior = scrollBehavior,
            topBar = { behavior ->
                AppBar(
                    title = stringResource(TLMR.strings.network_stream),
                    navigateUp = navigator::pop,
                    scrollBehavior = behavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = MaterialTheme.padding.large, vertical = MaterialTheme.padding.medium),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_url_label)) },
                    placeholder = { Text(stringResource(TLMR.strings.network_stream_url_placeholder)) },
                    isError = url.isNotBlank() && !isUrlValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (url.isNotBlank() && !isUrlValid) {
                    Text(
                        text = stringResource(TLMR.strings.network_stream_invalid_url),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_title_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = subtitleUrl,
                    onValueChange = { subtitleUrl = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_subtitle_label)) },
                    singleLine = true,
                    isError = subtitleUrl.isNotBlank() && !isSubtitleValid,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (subtitleUrl.isNotBlank() && !isSubtitleValid) {
                    Text(
                        text = stringResource(TLMR.strings.network_stream_invalid_subtitle),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = subtitleLabel,
                    onValueChange = { subtitleLabel = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_subtitle_language_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = referer,
                    onValueChange = { referer = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_referer_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_user_agent_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                OutlinedTextField(
                    value = headersRaw,
                    onValueChange = { headersRaw = it },
                    label = { Text(stringResource(TLMR.strings.network_stream_headers_label)) },
                    placeholder = { Text(stringResource(TLMR.strings.network_stream_headers_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default, keyboardType = KeyboardType.Text),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.large))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val validationResult = parseHeaderLines(headersRaw)
                        val customHeaders = validationResult.fold(
                            onSuccess = { it.toMutableList() },
                            onFailure = { error ->
                                val message = if (error is HeaderFormatException) {
                                    context.coreStringResource(
                                        TLMR.strings.network_stream_header_error,
                                        error.lineIndex + 1,
                                    )
                                } else {
                                    error.message ?: context.coreStringResource(MR.strings.internal_error)
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                                return@Button
                            },
                        )

                        referer.trimOrNull()?.let { customHeaders.add(NetworkHeader("Referer", it)) }
                        userAgent.trimOrNull()?.let { customHeaders.add(NetworkHeader("User-Agent", it)) }

                        val request = NetworkStreamRequest(
                            url = url.trim(),
                            title = title.ifBlank { url.trim() },
                            subtitleUrl = subtitleUrl.trimOrNull(),
                            subtitleLabel = subtitleLabel.trimOrNull(),
                            headers = customHeaders,
                        )

                        context.startActivity(PlayerActivity.newStreamIntent(context, request))
                        navigator.pop()
                    },
                    enabled = canPlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(TLMR.strings.network_stream_play))
                }

                Text(
                    text = stringResource(TLMR.strings.network_stream_summary),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = MaterialTheme.padding.small)
                        .secondaryItemAlpha(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = {
                            url = ""
                            title = ""
                            subtitleUrl = ""
                            subtitleLabel = ""
                            referer = ""
                            userAgent = ""
                            headersRaw = ""
                        },
                    ) {
                        Text(text = stringResource(TLMR.strings.network_stream_reset))
                    }
                }
            }
        }
    }
}

private fun String.isValidStreamUrl(): Boolean {
    if (isBlank()) return false
    val trimmed = trim()
    return runCatching { trimmed.toUri() }
        .mapCatching { uri ->
            val scheme = uri.scheme?.lowercase()
            when (scheme) {
                "http", "https", "rtmp", "rtsp", "ftp", "magnet", "file", "content" -> true
                else -> false
            }
        }
        .getOrDefault(false)
}

data class HeaderFormatException(val lineIndex: Int) : IllegalArgumentException()

private fun parseHeaderLines(raw: String): Result<List<NetworkHeader>> {
    val headers = mutableListOf<NetworkHeader>()
    raw.lineSequence().forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEachIndexed
        val separatorIndex = trimmed.indexOf(":")
        if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) {
            return Result.failure(HeaderFormatException(index))
        }
        val name = trimmed.substring(0, separatorIndex).trim()
        val value = trimmed.substring(separatorIndex + 1).trim()
        if (name.isEmpty() || value.isEmpty()) {
            return Result.failure(HeaderFormatException(index))
        }
        headers.add(NetworkHeader(name, value))
    }
    return Result.success(headers)
}
