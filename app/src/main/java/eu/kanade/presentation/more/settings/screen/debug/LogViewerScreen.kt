package eu.kanade.presentation.more.settings.screen.debug

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.AppLogEntry
import eu.kanade.tachiyomi.util.system.AppLogTail
import eu.kanade.tachiyomi.util.system.FileLogcatLogger
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ArrowDownward
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.seconds

class LogViewerScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val viewModel = metroViewModel<LogViewerViewModel>()

        val entries by viewModel.visibleEntries.collectAsState()
        val filter by viewModel.filter.collectAsState()
        val query by viewModel.query.collectAsState()
        var showClearDialog by rememberSaveable { mutableStateOf(false) }

        val listState = rememberLazyListState()
        // Follow the newest entry until the user scrolls away from the bottom.
        var followTail by remember { mutableStateOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling -> if (!scrolling) followTail = !listState.canScrollForward }
        }
        LaunchedEffect(entries) {
            if (followTail && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(TLMR.strings.pref_log_viewer)) },
                    searchQuery = query,
                    onChangeSearchQuery = viewModel::setQuery,
                    navigateUp = navigator::pop,
                    placeholderText = stringResource(MR.strings.action_search_hint),
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = MaterialSymbols.Rounded.ContentCopy,
                                    onClick = { context.copyToClipboard(LOG_CLIP_LABEL, entries.toClipText()) },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_share),
                                    icon = MaterialSymbols.Rounded.Share,
                                    onClick = viewModel::share,
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(TLMR.strings.log_viewer_action_clear),
                                    onClick = { showClearDialog = true },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (!followTail && entries.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = {
                            followTail = true
                            scope.launch { listState.scrollToItem(entries.lastIndex) }
                        },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.ArrowDownward,
                            contentDescription = stringResource(TLMR.strings.log_viewer_scroll_to_bottom),
                        )
                    }
                }
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LogViewerFilter.entries) { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
                if (entries.isEmpty()) {
                    EmptyScreen(stringRes = TLMR.strings.log_viewer_empty, modifier = Modifier.fillMaxSize())
                } else {
                    LogEntryList(
                        entries = entries,
                        listState = listState,
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    )
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(stringResource(TLMR.strings.log_viewer_action_clear)) },
                text = { Text(stringResource(TLMR.strings.log_viewer_clear_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            followTail = true
                            viewModel.clearLog()
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun LogEntryList(
        entries: List<AppLogEntry>,
        listState: LazyListState,
        contentPadding: PaddingValues,
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val errorColor = MaterialTheme.colorScheme.error
        val warnColor = if (isDark) WARN_COLOR_DARK else WARN_COLOR_LIGHT
        LazyColumn(
            state = listState,
            contentPadding = contentPadding + PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = { it.id }) { entry ->
                SelectionContainer {
                    Text(
                        text = entry.text,
                        color = when (entry.priority) {
                            'E', 'A' -> errorColor
                            'W' -> warnColor
                            else -> Color.Unspecified
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }

    @Inject
    @ViewModelKey
    @ContributesIntoMap(AppScope::class)
    class LogViewerViewModel(
        private val context: Context,
        private val crashLogUtil: CrashLogUtil,
    ) : ViewModel() {

        private val tail = AppLogTail({ FileLogcatLogger.logFiles(context) })

        val filter = MutableStateFlow(LogViewerFilter.ALL)

        /** Null while the search field is closed. */
        val query = MutableStateFlow<String?>(null)

        // Polls only while the screen is collecting.
        private val entries: StateFlow<List<AppLogEntry>> = flow {
            while (true) {
                emit(tail.poll())
                delay(POLL_INTERVAL)
            }
        }
            .filterNotNull()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val visibleEntries: StateFlow<List<AppLogEntry>> = combine(entries, filter, query) { entries, filter, query ->
            entries.filter { matchesLogFilter(it, filter, query.orEmpty()) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun setFilter(value: LogViewerFilter) {
            filter.value = value
        }

        fun setQuery(value: String?) {
            query.value = value
        }

        fun share() {
            viewModelScope.launch { crashLogUtil.dumpLogs() }
        }

        fun clearLog() {
            viewModelScope.launch(Dispatchers.IO) {
                FileLogcatLogger.clear(context)
                tail.invalidate()
            }
        }
    }
}

private const val LOG_CLIP_LABEL = "Animetail log"
private val POLL_INTERVAL = 1.seconds

// Clipboard data goes through a binder transaction (about 1 MB), so copy at most this many
// characters, keeping the newest entries.
private const val MAX_COPY_CHARS = 200_000

private val WARN_COLOR_LIGHT = Color(0xFFB26A00)
private val WARN_COLOR_DARK = Color(0xFFFFB74D)

private val LogViewerFilter.label: StringResource
    get() = when (this) {
        LogViewerFilter.ALL -> MR.strings.all
        LogViewerFilter.ERRORS -> TLMR.strings.log_viewer_filter_errors
        LogViewerFilter.DOWNLOADS -> TLMR.strings.log_viewer_filter_downloads
        LogViewerFilter.CAST -> TLMR.strings.log_viewer_filter_cast
        LogViewerFilter.PLAYER -> TLMR.strings.log_viewer_filter_player
        LogViewerFilter.NETWORK -> TLMR.strings.log_viewer_filter_network
    }

private fun List<AppLogEntry>.toClipText(): String {
    val kept = ArrayDeque<String>()
    var length = 0
    for (entry in asReversed()) {
        if (length + entry.text.length + 1 > MAX_COPY_CHARS && kept.isNotEmpty()) break
        kept.addFirst(entry.text)
        length += entry.text.length + 1
    }
    return kept.joinToString("\n")
}
