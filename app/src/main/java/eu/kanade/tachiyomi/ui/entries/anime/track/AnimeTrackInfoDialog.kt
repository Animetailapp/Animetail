package eu.kanade.tachiyomi.ui.entries.anime.track

import android.app.Application
import android.content.Context
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
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshResult
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackItemSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.anime.AnimeTrackInfoDialogHome
import eu.kanade.presentation.track.anime.AnimeTrackerSearch
import eu.kanade.presentation.util.Screen
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import tachiyomi.domain.track.anime.model.AnimeTrack as DbAnimeTrack

data class AnimeTrackInfoDialogHomeScreen(
    private val animeId: Long,
    private val animeTitle: String,
    private val sourceId: Long,
    // AM -->
    private val isSeason: Boolean = false,
    // <-- AM
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { Model(animeId, sourceId, isSeason = isSeason) }

        val dateFormat = remember {
            UiPreferences.dateFormat(
                Injekt.get<UiPreferences>().dateFormat.get(),
            )
        }
        val state by screenModel.state.collectAsState()

        AnimeTrackInfoDialogHome(
            trackItems = state.trackItems,
            dateFormat = dateFormat,
            // AM -->
            isSeason = isSeason,
            // <-- AM
            onStatusClick = {
                navigator.push(
                    TrackStatusSelectorScreen(
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onEpisodeClick = {
                navigator.push(
                    TrackEpisodeSelectorScreen(
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
                if (it.tracker is EnhancedAnimeTracker) {
                    screenModel.registerEnhancedTracking(it)
                } else {
                    navigator.push(
                        TrackServiceSearchScreen(
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
                    TrackerAnimeRemoveScreen(
                        animeId = animeId,
                        track = it.track!!,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onCopyLink = { context.copyTrackerLink(it) },
            onTogglePrivate = screenModel::togglePrivate,
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

    private class Model(
        private val animeId: Long,
        private val sourceId: Long,
        // AM -->
        private val isSeason: Boolean = false,
        // <-- AM
        private val getTracks: GetAnimeTracks = Injekt.get(),
    ) : StateScreenModel<Model.State>(State()) {

        init {
            // AM (skip auto-refresh for seasons — handled by syncTrackers) -->
            if (!isSeason) {
                screenModelScope.launch {
                    refreshTrackers()
                }
            }
            // <-- AM

            screenModelScope.launch {
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
            screenModelScope.launchNonCancellable {
                val anime = Injekt.get<GetAnime>().await(animeId) ?: return@launchNonCancellable
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
                    withUIContext { Injekt.get<Application>().toast(MR.strings.error_no_match) }
                }
            }
        }

        private suspend fun refreshTrackers() {
            val refreshTracks = Injekt.get<RefreshAnimeTracks>()
            val context = Injekt.get<Application>()

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
            screenModelScope.launchNonCancellable {
                (item.tracker as? AnimeTracker)?.setRemotePrivate(item.track!!.toDbTrack(), !item.track.private)
            }
        }

        private fun List<AnimeTrack>.mapToTrackItem(): List<AnimeTrackItem> {
            // Include trackers that are either logged in or explicitly available for metadata use
            val trackerManager: TrackerManager = Injekt.get()
            val loggedInTrackers = trackerManager.trackers
                .filter { (it as? Tracker)?.isAvailableForUse() == true }
                .filter {
                    it is AnimeTracker
                }
            val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId)
            return loggedInTrackers
                // Map to TrackItem
                .map { service -> AnimeTrackItem(find { it.trackerId == service.id }, service) }
                // Show only if the service supports this anime's source
                .filter { (it.tracker as? EnhancedAnimeTracker)?.accept(source) ?: true }
        }

        @Immutable
        data class State(
            val trackItems: List<AnimeTrackItem> = emptyList(),
        )
    }
}

private data class TrackStatusSelectorScreen(
    private val track: DbAnimeTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()
        TrackStatusSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setStatus()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: DbAnimeTrack,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.status)) {

        fun getSelections(): Map<Long, StringResource?> {
            return tracker.animeService.getStatusListAnime().associateWith {
                (tracker as? AnimeTracker)?.getStatusForAnime(it)
            }
        }

        fun setSelection(selection: Long) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            screenModelScope.launchNonCancellable {
                tracker.animeService.setRemoteAnimeStatus(track.toDbTrack(), state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: Long,
        )
    }
}

private data class TrackEpisodeSelectorScreen(
    private val track: DbAnimeTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()

        TrackItemSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            range = remember { screenModel.getRange() },
            onConfirm = {
                screenModel.setEpisode()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
            isManga = false,
        )
    }

    private class Model(
        private val track: DbAnimeTrack,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.lastEpisodeSeen.toInt())) {

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
            screenModelScope.launchNonCancellable {
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
}

private data class TrackScoreSelectorScreen(
    private val track: DbAnimeTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()

        TrackScoreSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setScore()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: DbAnimeTrack,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(tracker.animeService.displayScore(track))) {

        fun getSelections(): ImmutableList<String> {
            return tracker.animeService.getScoreList()
        }

        fun setSelection(selection: String) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setScore() {
            screenModelScope.launchNonCancellable {
                tracker.animeService.setRemoteScore(track.toDbTrack(), state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: String,
        )
    }
}

private data class TrackDateSelectorScreen(
    private val track: DbAnimeTrack,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val dateToCheck = Instant.ofEpochMilli(utcTimeMillis)
                .atZone(ZoneOffset.systemDefault())
                .toLocalDate()

            if (dateToCheck > LocalDate.now()) {
                // Disallow future dates
                return false
            }

            return if (start && track.finishDate > 0) {
                // Disallow start date to be set later than finish date
                val dateFinished = Instant.ofEpochMilli(track.finishDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                dateToCheck <= dateFinished
            } else if (!start && track.startDate > 0) {
                // Disallow end date to be set earlier than start date
                val dateStarted = Instant.ofEpochMilli(track.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                dateToCheck >= dateStarted
            } else {
                // Nothing set before
                true
            }
        }

        override fun isSelectableYear(year: Int): Boolean {
            if (year > LocalDate.now().year) {
                // Disallow future dates
                return false
            }

            return if (start && track.finishDate > 0) {
                // Disallow start date to be set later than finish date
                val dateFinished = Instant.ofEpochMilli(track.finishDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .year
                year <= dateFinished
            } else if (!start && track.startDate > 0) {
                // Disallow end date to be set earlier than start date
                val dateStarted = Instant.ofEpochMilli(track.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .year
                year >= dateStarted
            } else {
                // Nothing set before
                true
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
                start = start,
            )
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
            initialSelectedDateMillis = screenModel.initialSelection,
            selectableDates = selectableDates,
            onConfirm = {
                screenModel.setDate(it)
                navigator.pop()
            },
            onRemove = { screenModel.confirmRemoveDate(navigator) }.takeIf { canRemove },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: DbAnimeTrack,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        // In UTC
        val initialSelection: Long
            get() {
                val millis =
                    (if (start) track.startDate else track.finishDate)
                        .takeIf { it != 0L }
                        ?: Instant.now().toEpochMilli()
                return millis.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
            }

        // In UTC
        fun setDate(millis: Long) {
            // Convert to local time
            val localMillis =
                millis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault())
            screenModelScope.launchNonCancellable {
                if (start) {
                    tracker.animeService.setRemoteStartDate(track.toDbTrack(), localMillis)
                } else {
                    tracker.animeService.setRemoteFinishDate(track.toDbTrack(), localMillis)
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, tracker.id, start))
        }
    }
}

private data class TrackDateRemoverScreen(
    private val track: DbAnimeTrack,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
                start = start,
            )
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
                val serviceName = screenModel.getName()
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
                            screenModel.removeDate()
                            navigator.popUntil { it is AnimeTrackInfoDialogHomeScreen }
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

    private class Model(
        private val track: DbAnimeTrack,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        fun getName() = tracker.name

        fun removeDate() {
            screenModelScope.launchNonCancellable {
                if (start) {
                    tracker.animeService.setRemoteStartDate(track.toDbTrack(), 0)
                } else {
                    tracker.animeService.setRemoteFinishDate(track.toDbTrack(), 0)
                }
            }
        }
    }
}

data class TrackServiceSearchScreen(
    private val animeId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                animeId = animeId,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }

        val state by screenModel.state.collectAsState()

        // Navigate back when the model signals that registration is complete.
        val navigateBack = state.navigateBack
        LaunchedEffect(navigateBack) {
            if (navigateBack) navigator.pop()
        }

        val textFieldState = rememberTextFieldState(initialQuery)
        AnimeTrackerSearch(
            state = textFieldState,
            onDispatchQuery = { screenModel.trackingSearch(textFieldState.text.toString()) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = screenModel::updateSelection,
            onConfirmSelection = f@{ private: Boolean ->
                val selected = state.selected ?: return@f
                selected.private = private
                screenModel.requestSeasonPickerOrRegister(selected, private)
            },
            onDismissRequest = navigator::pop,
            supportsPrivateTracking = screenModel.supportsPrivateTracking,
        )

        // Show Trakt season picker when the model requests it.
        val seasonData = state.seasonPickerData
        if (seasonData != null) {
            TraktSeasonPickerDialog(
                seasons = seasonData.seasons,
                isLoading = seasonData.isLoading,
                onSeasonSelected = screenModel::confirmSeasonSelection,
                onDismissRequest = screenModel::dismissSeasonPicker,
            )
        }
    }

    private class Model(
        private val animeId: Long,
        private val currentUrl: String? = null,
        initialQuery: String,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State()) {

        val supportsPrivateTracking = tracker.supportsPrivateTracking

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            screenModelScope.launch {
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
            screenModelScope.launchNonCancellable {
                // AM -->
                val anime = Injekt.get<GetAnime>().await(animeId) ?: return@launchNonCancellable
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
            screenModelScope.launch {
                val seasons = withIOContext {
                    try { traktTracker.fetchShowSeasons(traktId) } catch (_: Exception) { emptyList() }
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
        fun confirmSeasonSelection(seasonNumber: Int, episodeCount: Int) {
            val pendingTrack = mutableState.value.seasonPickerData?.pendingTrack ?: return
            val baseUrl = pendingTrack.tracking_url.substringBefore("?")
            pendingTrack.tracking_url = "$baseUrl?season=$seasonNumber"
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
}

private data class TrackerAnimeRemoveScreen(
    private val animeId: Long,
    private val track: AnimeTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                animeId = animeId,
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val serviceName = screenModel.getName()
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
                    if (screenModel.isDeletable()) {
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
                            screenModel.unregisterTracking(serviceId)
                            if (removeRemoteTrack) screenModel.deleteAnimeFromService()
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

    private class Model(
        private val animeId: Long,
        private val track: AnimeTrack,
        private val tracker: Tracker,
        private val deleteTrack: DeleteAnimeTrack = Injekt.get(),
    ) : ScreenModel {

        fun getName() = tracker.name

        fun isDeletable() = tracker is DeletableAnimeTracker

        fun deleteAnimeFromService() {
            screenModelScope.launchNonCancellable {
                try {
                    (tracker as DeletableAnimeTracker).delete(track)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to delete anime entry from service" }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            screenModelScope.launchNonCancellable { deleteTrack.await(animeId, serviceId) }
        }
    }
}

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
    onSeasonSelected: (seasonNumber: Int, episodeCount: Int) -> Unit,
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
