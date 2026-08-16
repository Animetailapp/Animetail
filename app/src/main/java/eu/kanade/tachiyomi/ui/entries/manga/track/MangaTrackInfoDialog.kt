package eu.kanade.tachiyomi.ui.entries.manga.track

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackItemSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.manga.MangaTrackInfoDialogHome
import eu.kanade.presentation.track.manga.MangaTrackerSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.DeletableMangaTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import tachiyomi.domain.track.manga.model.MangaTrack as DbMangaTrack

data class MangaTrackInfoDialogHomeScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val sourceId: Long,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(mangaId = mangaId, sourceId = sourceId) }

        val dateFormat = remember { UiPreferences.dateFormat(context.appGraph.uiPreferences.dateFormat.get()) }
        val state by viewModel.state.collectAsState()

        MangaTrackInfoDialogHome(
            trackItems = state.trackItems,
            dateFormat = dateFormat,
            onStatusClick = {
                navigator.push(
                    TrackStatusSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onChapterClick = {
                navigator.push(
                    TrackChapterSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onScoreClick = {
                navigator.push(
                    TrackScoreSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onStartDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                        start = true,
                    ),
                )
            },
            onEndDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                        start = false,
                    ),
                )
            },
            onNewSearch = {
                if (it.tracker is EnhancedMangaTracker) {
                    viewModel.registerEnhancedTracking(it)
                } else {
                    navigator.push(
                        TrackServiceSearchScreen(
                            mangaId = mangaId,
                            initialQuery = it.track?.title ?: mangaTitle,
                            currentUrl = it.track?.remoteUrl,
                            serviceId = it.tracker.id,
                        ),
                    )
                }
            },
            onOpenInBrowser = { openTrackerInBrowser(context, it) },
            onRemoved = {
                navigator.push(
                    TrackerMangaRemoveScreen(
                        mangaId = mangaId,
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onCopyLink = { context.copyTrackerLink(it) },
            onTogglePrivate = viewModel::togglePrivate,
        )
    }

    /**
     * Opens registered tracker url in browser
     */
    private fun openTrackerInBrowser(context: Context, trackItem: MangaTrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            context.openInBrowser(url)
        }
    }

    private fun Context.copyTrackerLink(trackItem: MangaTrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    @AssistedInject
    class Model(
        @Assisted private val mangaId: Long,
        @Assisted private val sourceId: Long,
        private val context: Context,
        private val getTracks: GetMangaTracks,
        private val getManga: GetManga,
        private val trackerManager: TrackerManager,
        private val sourceManager: MangaSourceManager,
        private val refreshTracks: RefreshMangaTracks,
    ) : ViewModel() {

        val state: StateFlow<State>
            field = MutableStateFlow<State>(State())

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(mangaId: Long, sourceId: Long): Model
        }

        init {
            viewModelScope.launch {
                refreshTrackers()
            }

            viewModelScope.launch {
                getTracks.subscribe(mangaId)
                    .catch { logcat(LogPriority.ERROR, it) }
                    .distinctUntilChanged()
                    .map { it.mapToTrackItem() }
                    .collectLatest { trackItems -> state.update { it.copy(trackItems = trackItems) } }
            }
        }

        fun registerEnhancedTracking(item: MangaTrackItem) {
            item.tracker as EnhancedMangaTracker
            viewModelScope.launchNonCancellable {
                val manga = getManga.await(mangaId) ?: return@launchNonCancellable
                try {
                    val matchResult = item.tracker.match(manga) ?: throw Exception()
                    item.tracker.mangaService.register(matchResult, mangaId)
                } catch (_: Exception) {
                    withUIContext {
                        context.toast(MR.strings.error_no_match)
                    }
                }
            }
        }

        private suspend fun refreshTrackers() {
            refreshTracks.await(mangaId)
                .filter { it.first != null }
                .forEach { (track, e) ->
                    logcat(LogPriority.ERROR, e) {
                        "Failed to refresh track data mangaId=$mangaId for service ${track!!.name}"
                    }
                    withUIContext {
                        context.toast(
                            context.stringResource(
                                MR.strings.track_error,
                                track!!.name,
                                e.message ?: "",
                            ),
                        )
                    }
                }
        }

        fun togglePrivate(item: MangaTrackItem) {
            viewModelScope.launchNonCancellable {
                (item.tracker as? MangaTracker)?.setRemotePrivate(item.track!!.toDbTrack(), !item.track.private)
            }
        }

        private fun List<MangaTrack>.mapToTrackItem(): List<MangaTrackItem> {
            val loggedInTrackers = trackerManager.loggedInTrackers().filter {
                it is MangaTracker
            }
            val source = sourceManager.getOrStub(sourceId)
            return loggedInTrackers
                // Map to TrackItem
                .map { service -> MangaTrackItem(find { it.trackerId == service.id }, service) }
                // Show only if the service supports this manga's source
                .filter { (it.tracker as? EnhancedMangaTracker)?.accept(source) ?: true }
        }

        @Immutable
        data class State(
            val trackItems: List<MangaTrackItem> = emptyList(),
        )
    }
}

data class TrackStatusSelectorScreen(
    private val track: DbMangaTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(track = track, trackerId = serviceId) }
        val state by viewModel.state.collectAsState()
        TrackStatusSelector(
            selection = state.selection,
            onSelectionChange = viewModel::setSelection,
            selections = remember { viewModel.getSelections() },
            onConfirm = {
                viewModel.setStatus()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        val state: StateFlow<Model.State>
            field = MutableStateFlow<Model.State>(State(track.status))

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(track: DbMangaTrack, trackerId: Long): Model
        }

        val tracker = trackerManager.get(trackerId)!!

        fun getSelections(): Map<Long, StringResource?> {
            return tracker.mangaService.getStatusListManga().associateWith {
                (tracker as? MangaTracker)?.getStatusForManga(it)
            }
        }

        fun setSelection(selection: Long) {
            state.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            viewModelScope.launchNonCancellable {
                tracker.mangaService.setRemoteMangaStatus(track.toDbTrack(), state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: Long,
        )
    }
}

data class TrackChapterSelectorScreen(
    private val track: DbMangaTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(track = track, trackerId = serviceId) }
        val state by viewModel.state.collectAsState()

        TrackItemSelector(
            selection = state.selection,
            onSelectionChange = viewModel::setSelection,
            range = remember { viewModel.getRange() },
            onConfirm = {
                viewModel.setChapter()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
            isManga = true,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        val state: StateFlow<Model.State>
            field = MutableStateFlow<Model.State>(State(track.lastChapterRead.toInt()))

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(track: DbMangaTrack, trackerId: Long): Model
        }

        val tracker = trackerManager.get(trackerId)!!

        fun getRange(): Iterable<Int> {
            val endRange = if (track.totalChapters > 0) {
                track.totalChapters
            } else {
                10000
            }
            return 0..endRange.toInt()
        }

        fun setSelection(selection: Int) {
            state.update { it.copy(selection = selection) }
        }

        fun setChapter() {
            viewModelScope.launchNonCancellable {
                tracker.mangaService.setRemoteLastChapterRead(
                    track.toDbTrack(),
                    state.value.selection,
                )
            }
        }

        @Immutable
        data class State(
            val selection: Int,
        )
    }
}

data class TrackScoreSelectorScreen(
    private val track: DbMangaTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(track = track, trackerId = serviceId) }
        val state by viewModel.state.collectAsState()

        TrackScoreSelector(
            selection = state.selection,
            onSelectionChange = viewModel::setSelection,
            selections = remember { viewModel.getSelections() },
            onConfirm = {
                viewModel.setScore()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        val state: StateFlow<Model.State>
            field = MutableStateFlow<Model.State>(State(""))

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(track: DbMangaTrack, trackerId: Long): Model
        }

        val tracker = trackerManager.get(trackerId)!!

        init {
            tracker.mangaService.displayScore(track).let(::setSelection)
        }

        fun getSelections(): List<String> {
            return tracker.mangaService.getScoreList()
        }

        fun setSelection(selection: String) {
            state.update { it.copy(selection = selection) }
        }

        fun setScore() {
            viewModelScope.launchNonCancellable {
                tracker.mangaService.setRemoteScore(track.toDbTrack(), state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: String,
        )
    }
}

data class TrackDateSelectorScreen(
    private val track: DbMangaTrack,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val targetDate = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC)

            // Disallow future dates
            if (targetDate > Clock.System.now().toLocalDateTime(TimeZone.UTC)) return false

            return when {
                // Disallow setting start date after finish date
                start && track.finishDate > 0 -> {
                    val finishDate = Instant.fromEpochMilliseconds(track.finishDate).toLocalDateTime(TimeZone.UTC)
                    targetDate <= finishDate
                }

                // Disallow setting finish date before start date
                !start && track.startDate > 0 -> {
                    val startDate = Instant.fromEpochMilliseconds(track.startDate).toLocalDateTime(TimeZone.UTC)
                    startDate <= targetDate
                }

                else -> {
                    true
                }
            }
        }

        override fun isSelectableYear(year: Int): Boolean {
            // Disallow future years
            if (year > Clock.System.now().toLocalDateTime(TimeZone.UTC).year) return false

            return when {
                // Disallow setting start year after finish year
                start && track.finishDate > 0 -> {
                    val finishDate = Instant.fromEpochMilliseconds(track.finishDate).toLocalDateTime(TimeZone.UTC)
                    year <= finishDate.year
                }

                // Disallow setting finish year before start year
                !start && track.startDate > 0 -> {
                    val startDate = Instant.fromEpochMilliseconds(track.startDate).toLocalDateTime(TimeZone.UTC)
                    startDate.year <= year
                }

                else -> {
                    true
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> {
            create(track = track, trackerId = serviceId, start = start)
        }

        val canRemove = if (start) {
            track.startDate > 0
        } else {
            track.finishDate > 0
        }
        TrackDateSelector(
            title = if (start) {
                stringResource(MR.strings.track_started_reading_date)
            } else {
                stringResource(MR.strings.track_finished_reading_date)
            },
            initialSelectedDateMillis = viewModel.initialSelection,
            selectableDates = selectableDates,
            onConfirm = {
                viewModel.setDate(it)
                navigator.pop()
            },
            onRemove = { viewModel.confirmRemoveDate(navigator) }.takeIf { canRemove },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        @Assisted private val start: Boolean,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(track: DbMangaTrack, trackerId: Long, start: Boolean): Model
        }

        private val tracker = trackerManager.get(trackerId)!!

        // In UTC
        val initialSelection: Long
            get() {
                val millis = (if (start) track.startDate else track.finishDate)
                    .takeIf { it != 0L }
                    ?: Clock.System.now().toEpochMilliseconds()
                return millis.convertEpochMillisZone(TimeZone.currentSystemDefault(), TimeZone.UTC)
            }

        // In UTC
        fun setDate(millis: Long) {
            // Convert to local time
            val localMillis = millis.convertEpochMillisZone(TimeZone.UTC, TimeZone.currentSystemDefault())
            viewModelScope.launchNonCancellable {
                if (start) {
                    tracker.mangaService.setRemoteStartDate(track.toDbTrack(), localMillis)
                } else {
                    tracker.mangaService.setRemoteFinishDate(track.toDbTrack(), localMillis)
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, tracker.id, start))
        }
    }
}

data class TrackDateRemoverScreen(
    private val track: DbMangaTrack,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> {
            create(track = track, trackerId = serviceId, start = start)
        }
        AlertDialogContent(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(MR.strings.track_remove_date_conf_title),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                val serviceName = viewModel.getName()
                Text(
                    text = if (start) {
                        stringResource(MR.strings.track_remove_start_date_conf_text, serviceName)
                    } else {
                        stringResource(MR.strings.track_remove_finish_date_conf_text, serviceName)
                    },
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.small,
                        Alignment.End,
                    ),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.removeDate()
                            navigator.popUntil { it is MangaTrackInfoDialogHomeScreen }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_remove))
                    }
                }
            },
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        @Assisted private val start: Boolean,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(track: DbMangaTrack, trackerId: Long, start: Boolean): Model
        }

        private val tracker = trackerManager.get(trackerId)!!

        fun getName() = tracker.name

        fun removeDate() {
            viewModelScope.launchNonCancellable {
                if (start) {
                    tracker.mangaService.setRemoteStartDate(track.toDbTrack(), 0)
                } else {
                    tracker.mangaService.setRemoteFinishDate(track.toDbTrack(), 0)
                }
            }
        }
    }
}

data class TrackServiceSearchScreen(
    private val mangaId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> {
            create(
                mangaId = mangaId,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                trackerId = serviceId,
            )
        }

        val state by viewModel.state.collectAsState()

        val textFieldState = rememberTextFieldState(initialQuery)
        MangaTrackerSearch(
            state = textFieldState,
            onDispatchQuery = { viewModel.trackingSearch(textFieldState.text.toString()) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = viewModel::updateSelection,
            onConfirmSelection = f@{ private: Boolean ->
                val selected = state.selected ?: return@f
                selected.private = private
                viewModel.registerTracking(selected)
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
            supportsPrivateTracking = viewModel.supportsPrivateTracking,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val mangaId: Long,
        @Assisted private val currentUrl: String?,
        @Assisted initialQuery: String,
        @Assisted private val trackerId: Long,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        val state: StateFlow<Model.State>
            field = MutableStateFlow<Model.State>(State())

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(
                mangaId: Long,
                currentUrl: String?,
                initialQuery: String,
                trackerId: Long,
            ): Model
        }

        private val tracker = trackerManager.get(trackerId)!!

        val supportsPrivateTracking = tracker.supportsPrivateTracking

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            viewModelScope.launch {
                // To show loading state
                state.update { it.copy(queryResult = null, selected = null) }

                val result = withIOContext {
                    try {
                        val results = tracker.mangaService.searchManga(query)
                        Result.success(results)
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
                state.update { oldState ->
                    oldState.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                    )
                }
            }
        }

        fun registerTracking(item: MangaTrackSearch) {
            viewModelScope.launchNonCancellable { tracker.mangaService.register(item, mangaId) }
        }

        fun updateSelection(selected: MangaTrackSearch) {
            state.update { it.copy(selected = selected) }
        }

        @Immutable
        data class State(
            val queryResult: Result<List<MangaTrackSearch>>? = null,
            val selected: MangaTrackSearch? = null,
        )
    }
}

data class TrackerMangaRemoveScreen(
    private val mangaId: Long,
    private val track: DbMangaTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> {
            create(mangaId = mangaId, track = track, trackerId = serviceId)
        }
        val serviceName = viewModel.getName()
        var removeRemoteTrack by remember { mutableStateOf(false) }
        AlertDialogContent(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(MR.strings.track_delete_title, serviceName),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = stringResource(MR.strings.track_delete_text, serviceName),
                    )
                    if (viewModel.isDeletable()) {
                        LabeledCheckbox(
                            label = stringResource(MR.strings.track_delete_remote_text, serviceName),
                            checked = removeRemoteTrack,
                            onCheckedChange = { removeRemoteTrack = it },
                        )
                    }
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.small,
                        Alignment.End,
                    ),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.unregisterTracking(serviceId)
                            if (removeRemoteTrack) viewModel.deleteMangaFromService()
                            navigator.pop()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            },
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val mangaId: Long,
        @Assisted private val track: DbMangaTrack,
        @Assisted private val trackerId: Long,
        private val deleteTrack: DeleteMangaTrack,
        trackerManager: TrackerManager,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(mangaId: Long, track: DbMangaTrack, trackerId: Long): Model
        }

        private val tracker = trackerManager.get(trackerId)!!

        fun getName() = tracker.name

        fun isDeletable() = tracker is DeletableMangaTracker

        fun deleteMangaFromService() {
            viewModelScope.launchNonCancellable {
                try {
                    (tracker as DeletableMangaTracker).delete(track)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to delete manga entry from service" }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            viewModelScope.launchNonCancellable { deleteTrack.await(mangaId, serviceId) }
        }
    }
}
