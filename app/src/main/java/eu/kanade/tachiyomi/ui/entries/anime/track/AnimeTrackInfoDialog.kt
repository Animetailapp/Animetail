package eu.kanade.tachiyomi.ui.entries.anime.track

import android.content.Context
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshResult
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackItemSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.anime.AnimeTrackInfoDialogHome
import eu.kanade.presentation.track.anime.AnimeTrackerSearch
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.trakt.Trakt
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.Navigator
import mihon.core.navigation.NavigatorDisplay
import mihon.core.navigation.rememberNavigator
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Clock
import tachiyomi.domain.track.anime.model.AnimeTrack as DbAnimeTrack

@Serializable
private data class AnimeTrackInfoDialogHomeRoute(
    val animeId: Long,
    val animeTitle: String,
    val sourceId: Long,
    val isSeason: Boolean = false,
) : NavKey

@Composable
private fun AnimeTrackInfoDialogHomeScreen(
    animeId: Long,
    animeTitle: String,
    sourceId: Long,
    isSeason: Boolean,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel =
        assistedMetroViewModel<AnimeTrackInfoDialogHomeViewModel, AnimeTrackInfoDialogHomeViewModel.Factory> {
            create(animeId = animeId, sourceId = sourceId, isSeason = isSeason)
        }

    val dateFormat = remember {
        UiPreferences.dateFormat(
            context.appGraph.uiPreferences.dateFormat.get(),
        )
    }
    val state by viewModel.state.collectAsState()

    AnimeTrackInfoDialogHome(
        trackItems = state.trackItems,
        dateFormat = dateFormat,
        // AM -->
        isSeason = isSeason,
        // <-- AM
        onStatusClick = {
            navigator.push(
                AnimeTrackStatusSelectorRoute(
                    track = it.track!!,
                    serviceId = it.tracker.id,
                ),
            )
        },
        onEpisodeClick = {
            navigator.push(
                AnimeTrackEpisodeSelectorRoute(
                    track = it.track!!,
                    serviceId = it.tracker.id,
                ),
            )
        },
        onScoreClick = {
            navigator.push(
                AnimeTrackScoreSelectorRoute(
                    track = it.track!!,
                    serviceId = it.tracker.id,
                ),
            )
        },
        onStartDateEdit = {
            navigator.push(
                AnimeTrackDateSelectorRoute(
                    track = it.track!!,
                    serviceId = it.tracker.id,
                    start = true,
                ),
            )
        },
        onEndDateEdit = {
            navigator.push(
                AnimeTrackDateSelectorRoute(
                    track = it.track!!,
                    serviceId = it.tracker.id,
                    start = false,
                ),
            )
        },
        onNewSearch = {
            if (it.tracker is EnhancedAnimeTracker) {
                viewModel.registerEnhancedTracking(it)
            } else {
                navigator.push(
                    AnimeTrackerSearchRoute(
                        animeId = animeId,
                        initialQuery = it.track?.title ?: animeTitle,
                        currentUrl = it.track?.remoteUrl,
                        serviceId = it.tracker.id,
                    ),
                )
            }
        },
        onOpenInBrowser = { openTrackerInBrowser(context, it) },
        onRemoved = {
            navigator.push(
                TrackerAnimeRemoveRoute(
                    animeId = animeId,
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
private fun openTrackerInBrowser(context: Context, trackItem: AnimeTrackItem) {
    val url = trackItem.track?.remoteUrl ?: return
    if (url.isNotBlank()) {
        context.openInBrowser(url)
    }
}

private fun Context.copyTrackerLink(trackItem: AnimeTrackItem) {
    val url = trackItem.track?.remoteUrl ?: return
    if (url.isNotBlank()) {
        copyToClipboard(url, url)
    }
}

@AssistedInject
class AnimeTrackInfoDialogHomeViewModel(
    private val context: Context,
    @Assisted private val animeId: Long,
    @Assisted private val sourceId: Long,
    // AM -->
    @Assisted private val isSeason: Boolean = false,
    // <-- AM
    private val getTracks: GetAnimeTracks,
    private val getAnime: GetAnime,
    private val refreshTracks: RefreshAnimeTracks,
    private val trackerManager: TrackerManager,
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            animeId: Long,
            sourceId: Long,
            isSeason: Boolean,
        ): AnimeTrackInfoDialogHomeViewModel
    }

    init {
        // AM (skip auto-refresh for seasons — handled by syncTrackers) -->
        if (!isSeason) {
            viewModelScope.launch {
                refreshTrackers()
            }
        }
        // <-- AM

        viewModelScope.launch {
            getTracks.subscribe(animeId)
                .catch { logcat(LogPriority.ERROR, it) }
                .distinctUntilChanged()
                .map { it.mapToTrackItem() }
                .collectLatest { trackItems ->
                    mutableState.update {
                        it.copy(
                            trackItems = trackItems,
                        )
                    }
                }
        }
    }

    fun registerEnhancedTracking(item: AnimeTrackItem) {
        item.tracker as EnhancedAnimeTracker
        viewModelScope.launchNonCancellable {
            val anime = getAnime.await(animeId) ?: return@launchNonCancellable
            try {
                // AM -->
                val matchResult = if (isSeason) {
                    item.tracker.matchSeason(anime)
                } else {
                    item.tracker.match(anime)
                } ?: throw Exception()
                item.tracker.animeService.register(matchResult, anime)
                // <-- AM
            } catch (e: Exception) {
                withUIContext { context.toast(MR.strings.error_no_match) }
            }
        }
    }

    private suspend fun refreshTrackers() {
        // AM -->
        refreshTracks.await(animeId)
            .filterIsInstance<RefreshResult.Failure>()
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data animeId=$animeId for service ${track.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
        // <-- AM
    }

    fun togglePrivate(item: AnimeTrackItem) {
        viewModelScope.launchNonCancellable {
            (item.tracker as? AnimeTracker)?.setRemotePrivate(item.track!!.toDbTrack(), !item.track.private)
        }
    }

    private fun List<AnimeTrack>.mapToTrackItem(): List<AnimeTrackItem> {
        val loggedInTrackers = trackerManager.trackers
            .filter { (it as? Tracker)?.isAvailableForUse() == true }
            .filter {
                it is AnimeTracker
            }
        val source = sourceManager.getOrStub(sourceId)
        return loggedInTrackers
            .map { service -> AnimeTrackItem(find { it.trackerId == service.id }, service) }
            .filter { (it.tracker as? EnhancedAnimeTracker)?.accept(source) ?: true }
    }

    @Immutable
    data class State(
        val trackItems: List<AnimeTrackItem> = emptyList(),
    )
}

@Serializable
private data class AnimeTrackStatusSelectorRoute(
    val track: DbAnimeTrack,
    val serviceId: Long,
) : NavKey

@Composable
private fun AnimeTrackStatusSelectorScreen(
    track: DbAnimeTrack,
    serviceId: Long,
) {
    val navigator = LocalNavigator.current
    val viewModel =
        assistedMetroViewModel<AnimeTrackStatusSelectorViewModel, AnimeTrackStatusSelectorViewModel.Factory> {
            create(track = track, trackerId = serviceId)
        }
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
class AnimeTrackStatusSelectorViewModel(
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    trackerManager: TrackerManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State(track.status))
    val state: StateFlow<State> = mutableState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(track: DbAnimeTrack, trackerId: Long): AnimeTrackStatusSelectorViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    fun getSelections(): Map<Long, StringResource?> {
        return tracker.animeService.getStatusListAnime().associateWith {
            (tracker as? AnimeTracker)?.getStatusForAnime(it)
        }
    }

    fun setSelection(selection: Long) {
        mutableState.update { it.copy(selection = selection) }
    }

    fun setStatus() {
        viewModelScope.launchNonCancellable {
            tracker.animeService.setRemoteAnimeStatus(track.toDbTrack(), state.value.selection)
        }
    }

    @Immutable
    data class State(
        val selection: Long,
    )
}

@Serializable
private data class AnimeTrackEpisodeSelectorRoute(
    val track: DbAnimeTrack,
    val serviceId: Long,
) : NavKey

@Composable
private fun AnimeTrackEpisodeSelectorScreen(
    track: DbAnimeTrack,
    serviceId: Long,
) {
    val navigator = LocalNavigator.current
    val viewModel =
        assistedMetroViewModel<AnimeTrackEpisodeSelectorViewModel, AnimeTrackEpisodeSelectorViewModel.Factory> {
            create(track = track, trackerId = serviceId)
        }
    val state by viewModel.state.collectAsState()

    TrackItemSelector(
        selection = state.selection,
        onSelectionChange = viewModel::setSelection,
        range = remember { viewModel.getRange() },
        onConfirm = {
            viewModel.setEpisode()
            navigator.pop()
        },
        onDismissRequest = navigator::pop,
        isManga = false,
    )
}

@AssistedInject
class AnimeTrackEpisodeSelectorViewModel(
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    trackerManager: TrackerManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State(track.lastEpisodeSeen.toInt()))
    val state: StateFlow<State> = mutableState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(track: DbAnimeTrack, trackerId: Long): AnimeTrackEpisodeSelectorViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    fun getRange(): Iterable<Int> {
        val endRange = if (track.totalEpisodes > 0) {
            track.totalEpisodes
        } else {
            10000
        }
        return 0..endRange.toInt()
    }

    fun setSelection(selection: Int) {
        mutableState.update { it.copy(selection = selection) }
    }

    fun setEpisode() {
        viewModelScope.launchNonCancellable {
            tracker.animeService.setRemoteLastEpisodeSeen(
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

@Serializable
private data class AnimeTrackScoreSelectorRoute(
    val track: DbAnimeTrack,
    val serviceId: Long,
) : NavKey

@Composable
private fun AnimeTrackScoreSelectorScreen(
    track: DbAnimeTrack,
    serviceId: Long,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<AnimeTrackScoreSelectorViewModel, AnimeTrackScoreSelectorViewModel.Factory> {
        create(track = track, trackerId = serviceId)
    }
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
class AnimeTrackScoreSelectorViewModel(
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    trackerManager: TrackerManager,
) : ViewModel() {

    private val mutableState: MutableStateFlow<State>
    val state: StateFlow<State>

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(track: DbAnimeTrack, trackerId: Long): AnimeTrackScoreSelectorViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    init {
        mutableState = MutableStateFlow(State(tracker.animeService.displayScore(track)))
        state = mutableState.asStateFlow()
    }

    fun getSelections(): List<String> {
        return tracker.animeService.getScoreList()
    }

    fun setSelection(selection: String) {
        mutableState.update { it.copy(selection = selection) }
    }

    fun setScore() {
        viewModelScope.launchNonCancellable {
            tracker.animeService.setRemoteScore(track.toDbTrack(), state.value.selection)
        }
    }

    @Immutable
    data class State(
        val selection: String,
    )
}

@Serializable
private data class AnimeTrackDateSelectorRoute(
    val track: DbAnimeTrack,
    val serviceId: Long,
    val start: Boolean,
) : NavKey

@Composable
private fun AnimeTrackDateSelectorScreen(
    track: DbAnimeTrack,
    serviceId: Long,
    start: Boolean,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<AnimeTrackDateSelectorViewModel, AnimeTrackDateSelectorViewModel.Factory> {
        create(track = track, trackerId = serviceId, start = start)
    }

    val selectableDates = remember(track, start) {
        object : SelectableDates {
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
class AnimeTrackDateSelectorViewModel(
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    @Assisted private val start: Boolean,
    trackerManager: TrackerManager,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(track: DbAnimeTrack, trackerId: Long, start: Boolean): AnimeTrackDateSelectorViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    // In UTC
    val initialSelection: Long
        get() {
            val millis =
                (if (start) track.startDate else track.finishDate)
                    .takeIf { it != 0L }
                    ?: Clock.System.now().toEpochMilliseconds()
            return millis.convertEpochMillisZone(TimeZone.currentSystemDefault(), TimeZone.UTC)
        }

    // In UTC
    fun setDate(millis: Long) {
        // Convert to local time
        val localMillis =
            millis.convertEpochMillisZone(TimeZone.UTC, TimeZone.currentSystemDefault())
        viewModelScope.launchNonCancellable {
            if (start) {
                tracker.animeService.setRemoteStartDate(track.toDbTrack(), localMillis)
            } else {
                tracker.animeService.setRemoteFinishDate(track.toDbTrack(), localMillis)
            }
        }
    }

    fun confirmRemoveDate(navigator: Navigator) {
        navigator.push(AnimeTrackDateRemoverRoute(track, tracker.id, start))
    }
}

@Serializable
private data class AnimeTrackDateRemoverRoute(
    val track: DbAnimeTrack,
    val serviceId: Long,
    val start: Boolean,
) : NavKey

@Composable
private fun AnimeTrackDateRemoverScreen(
    track: DbAnimeTrack,
    serviceId: Long,
    start: Boolean,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<AnimeTrackDateRemoverViewModel, AnimeTrackDateRemoverViewModel.Factory> {
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
                        navigator.popTo { it is AnimeTrackInfoDialogHomeRoute }
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
class AnimeTrackDateRemoverViewModel(
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    @Assisted private val start: Boolean,
    trackerManager: TrackerManager,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(track: DbAnimeTrack, trackerId: Long, start: Boolean): AnimeTrackDateRemoverViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    fun getName() = tracker.name

    fun removeDate() {
        viewModelScope.launchNonCancellable {
            if (start) {
                tracker.animeService.setRemoteStartDate(track.toDbTrack(), 0)
            } else {
                tracker.animeService.setRemoteFinishDate(track.toDbTrack(), 0)
            }
        }
    }
}

@Serializable
private data class AnimeTrackerSearchRoute(
    val animeId: Long,
    val initialQuery: String,
    val currentUrl: String?,
    val serviceId: Long,
) : NavKey

@Composable
private fun AnimeTrackerSearchScreen(
    animeId: Long,
    initialQuery: String,
    currentUrl: String?,
    serviceId: Long,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<AnimeTrackerSearchViewModel, AnimeTrackerSearchViewModel.Factory> {
        create(
            animeId = animeId,
            currentUrl = currentUrl,
            initialQuery = initialQuery,
            trackerId = serviceId,
        )
    }

    val state by viewModel.state.collectAsState()

    // Navigate back when the model signals that registration is complete.
    val navigateBack = state.navigateBack
    LaunchedEffect(navigateBack) {
        if (navigateBack) navigator.pop()
    }

    val textFieldState = rememberTextFieldState(initialQuery)
    AnimeTrackerSearch(
        state = textFieldState,
        onDispatchQuery = { viewModel.trackingSearch(textFieldState.text.toString()) },
        queryResult = state.queryResult,
        selected = state.selected,
        onSelectedChange = viewModel::updateSelection,
        onConfirmSelection = f@{ private: Boolean ->
            val selected = state.selected ?: return@f
            selected.private = private
            viewModel.requestSeasonPickerOrRegister(selected, private)
        },
        onDismissRequest = navigator::pop,
        supportsPrivateTracking = viewModel.supportsPrivateTracking,
    )

    // Show Trakt season picker when the model requests it.
    val seasonData = state.seasonPickerData
    if (seasonData != null) {
        TraktSeasonPickerDialog(
            seasons = seasonData.seasons,
            isLoading = seasonData.isLoading,
            onSeasonSelected = viewModel::confirmSeasonSelection,
            onDismissRequest = viewModel::dismissSeasonPicker,
        )
    }
}

@AssistedInject
class AnimeTrackerSearchViewModel(
    @Assisted private val animeId: Long,
    @Assisted private val currentUrl: String? = null,
    @Assisted initialQuery: String,
    @Assisted private val trackerId: Long,
    trackerManager: TrackerManager,
    private val getAnime: GetAnime,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            animeId: Long,
            currentUrl: String?,
            initialQuery: String,
            trackerId: Long,
        ): AnimeTrackerSearchViewModel
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
            mutableState.update { it.copy(queryResult = null, selected = null) }

            val result = withIOContext {
                try {
                    val results = tracker.animeService.searchAnime(query)
                    Result.success(results)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }
            mutableState.update { oldState ->
                oldState.copy(
                    queryResult = result,
                    selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                )
            }
        }
    }

    fun registerTracking(item: AnimeTrackSearch) {
        viewModelScope.launchNonCancellable {
            // AM -->
            val anime = getAnime.await(animeId) ?: return@launchNonCancellable
            tracker.animeService.register(item, anime)
            // <-- AM
        }
    }

    /**
     * For Trakt shows, fetches available seasons and shows the season picker.
     * For other trackers or Trakt movies, registers immediately and signals navigation.
     */
    fun requestSeasonPickerOrRegister(item: AnimeTrackSearch, @Suppress("UNUSED_PARAMETER") isPrivate: Boolean) {
        val traktTracker = tracker as? Trakt
        if (traktTracker == null || item.total_episodes == 1L) {
            // Not Trakt or it's a movie — register directly and navigate back.
            registerTracking(item)
            mutableState.update { it.copy(navigateBack = true) }
            return
        }
        val traktId = item.remote_id
        if (traktId == 0L) {
            registerTracking(item)
            mutableState.update { it.copy(navigateBack = true) }
            return
        }
        // Show loading state in the season picker dialog while fetching seasons.
        mutableState.update { it.copy(seasonPickerData = SeasonPickerData(pendingTrack = item, isLoading = true)) }
        viewModelScope.launch {
            val seasons = withIOContext {
                try {
                    traktTracker.fetchShowSeasons(traktId)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (seasons.size <= 1) {
                // Only one (or zero) seasons available — set episode count and register without picker.
                val singleSeason = seasons.firstOrNull()
                if (singleSeason != null) {
                    item.total_episodes = singleSeason.second.toLong()
                }
                registerTracking(item)
                mutableState.update { it.copy(seasonPickerData = null, navigateBack = true) }
            } else {
                mutableState.update {
                    it.copy(seasonPickerData = it.seasonPickerData?.copy(seasons = seasons, isLoading = false))
                }
            }
        }
    }

    /** Called when the user picks a season from the Trakt season picker dialog. */
    fun confirmSeasonSelection(seasonNumber: Int?, episodeCount: Int) {
        val pendingTrack = mutableState.value.seasonPickerData?.pendingTrack ?: return
        val baseUrl = pendingTrack.tracking_url.substringBefore("?")
        pendingTrack.tracking_url = if (seasonNumber != null) {
            "$baseUrl?season=$seasonNumber"
        } else {
            baseUrl
        }
        pendingTrack.total_episodes = episodeCount.toLong()
        registerTracking(pendingTrack)
        mutableState.update { it.copy(seasonPickerData = null, navigateBack = true) }
    }

    fun dismissSeasonPicker() {
        mutableState.update { it.copy(seasonPickerData = null) }
    }

    fun updateSelection(selected: AnimeTrackSearch) {
        mutableState.update { it.copy(selected = selected) }
    }

    data class SeasonPickerData(
        val pendingTrack: AnimeTrackSearch,
        val seasons: List<Pair<Int, Int>> = emptyList(),
        val isLoading: Boolean = false,
    )

    @Immutable
    data class State(
        val queryResult: Result<List<AnimeTrackSearch>>? = null,
        val selected: AnimeTrackSearch? = null,
        val seasonPickerData: SeasonPickerData? = null,
        val navigateBack: Boolean = false,
    )
}

@Serializable
private data class TrackerAnimeRemoveRoute(
    val animeId: Long,
    val track: DbAnimeTrack,
    val serviceId: Long,
) : NavKey

@Composable
private fun TrackerAnimeRemoveScreen(
    animeId: Long,
    track: DbAnimeTrack,
    serviceId: Long,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<TrackerAnimeRemoveViewModel, TrackerAnimeRemoveViewModel.Factory> {
        create(animeId = animeId, track = track, trackerId = serviceId)
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
                        if (removeRemoteTrack) viewModel.deleteAnimeFromService()
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
class TrackerAnimeRemoveViewModel(
    @Assisted private val animeId: Long,
    @Assisted private val track: DbAnimeTrack,
    @Assisted private val trackerId: Long,
    private val deleteTrack: DeleteAnimeTrack,
    trackerManager: TrackerManager,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            animeId: Long,
            track: DbAnimeTrack,
            trackerId: Long,
        ): TrackerAnimeRemoveViewModel
    }

    private val tracker = trackerManager.get(trackerId)!!

    fun getName() = tracker.name

    fun isDeletable() = tracker is DeletableAnimeTracker

    fun deleteAnimeFromService() {
        viewModelScope.launchNonCancellable {
            try {
                (tracker as DeletableAnimeTracker).delete(track)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to delete anime entry from service" }
            }
        }
    }

    fun unregisterTracking(serviceId: Long) {
        viewModelScope.launchNonCancellable { deleteTrack.await(animeId, serviceId) }
    }
}

@Composable
fun AnimeTrackInfoDialog(
    animeId: Long,
    animeTitle: String,
    sourceId: Long,
    isSeason: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val sheetNavigator = rememberNavigator(AnimeTrackInfoDialogHomeRoute(animeId, animeTitle, sourceId, isSeason))

    CompositionLocalProvider(LocalNavigator provides sheetNavigator) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            enableImplicitDismiss = !sheetNavigator.canPop,
        ) {
            NavigatorDisplay(
                navigator = sheetNavigator,
                sizeTransform = SizeTransform(),
                transitionSpec = { fade },
                popTransitionSpec = { fade },
                predictivePopTransitionSpec = { fade },
                // Otherwise every picker reuses the first one's ViewModel, keyed only by class
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<TrackerAnimeRemoveRoute> { route ->
                        TrackerAnimeRemoveScreen(route.animeId, route.track, route.serviceId)
                    }
                    entry<AnimeTrackerSearchRoute> { route ->
                        AnimeTrackerSearchScreen(route.animeId, route.initialQuery, route.currentUrl, route.serviceId)
                    }
                    entry<AnimeTrackEpisodeSelectorRoute> { route ->
                        AnimeTrackEpisodeSelectorScreen(route.track, route.serviceId)
                    }
                    entry<AnimeTrackDateRemoverRoute> { route ->
                        AnimeTrackDateRemoverScreen(route.track, route.serviceId, route.start)
                    }
                    entry<AnimeTrackDateSelectorRoute> { route ->
                        AnimeTrackDateSelectorScreen(route.track, route.serviceId, route.start)
                    }
                    entry<AnimeTrackInfoDialogHomeRoute> { route ->
                        AnimeTrackInfoDialogHomeScreen(route.animeId, route.animeTitle, route.sourceId, route.isSeason)
                    }
                    entry<AnimeTrackScoreSelectorRoute> { route ->
                        AnimeTrackScoreSelectorScreen(route.track, route.serviceId)
                    }
                    entry<AnimeTrackStatusSelectorRoute> { route ->
                        AnimeTrackStatusSelectorScreen(route.track, route.serviceId)
                    }
                },
            )
        }
    }
}

private val fade = fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))

/**
 * Dialog that lets users pick a specific Trakt season when tracking a multi-season show.
 * Shows a loading spinner while seasons are being fetched, then displays a list of seasons.
 *
 * @param seasons List of (seasonNumber, episodeCount) pairs.
 * @param isLoading Whether seasons are still loading from the network.
 * @param onSeasonSelected Called with (seasonNumber, episodeCount) when the user picks a season.
 * @param onDismissRequest Called when the user cancels the dialog.
 */
@Composable
private fun TraktSeasonPickerDialog(
    seasons: List<Pair<Int, Int>>,
    isLoading: Boolean,
    onSeasonSelected: (seasonNumber: Int?, episodeCount: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.trakt_select_season)) },
        text = {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    item {
                        TextButton(
                            onClick = { onSeasonSelected(null, seasons.sumOf { it.second }) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.trakt_all_seasons),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(seasons) { (seasonNumber, episodeCount) ->
                        val seasonLabel = stringResource(AYMR.strings.display_mode_season, seasonNumber.toString())
                        val itemLabel = stringResource(AYMR.strings.trakt_season_episodes, seasonLabel, episodeCount)
                        TextButton(
                            onClick = { onSeasonSelected(seasonNumber, episodeCount) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = itemLabel,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
