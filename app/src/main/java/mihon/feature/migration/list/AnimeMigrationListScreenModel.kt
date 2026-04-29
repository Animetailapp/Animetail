package mihon.feature.migration.list

import androidx.annotation.FloatRange
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.migration.usecases.MigrateAnimeUseCase
import mihon.feature.migration.list.models.MigratingAnime
import mihon.feature.migration.list.models.MigratingAnime.SearchResult
import mihon.feature.migration.list.search.SmartAnimeSourceSearchEngine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeMigrationListScreenModel(
    animeIds: Collection<Long>,
    extraSearchQuery: String?,
    private val preferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val migrateAnime: MigrateAnimeUseCase = Injekt.get(),
) : StateScreenModel<AnimeMigrationListScreenModel.State>(State()) {

    private val smartSearchEngine = SmartAnimeSourceSearchEngine(extraSearchQuery)

    val items
        inline get() = state.value.items

    private val hideUnmatched = false
    private val hideWithoutUpdates = false

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            val anime = animeIds
                .map {
                    async {
                        val anime = getAnime.await(it) ?: return@async null
                        val episodeInfo = getEpisodeInfo(it)
                        MigratingAnime(
                            anime = anime,
                            episodeCount = episodeInfo.episodeCount,
                            latestEpisode = episodeInfo.latestEpisode,
                            source = sourceManager.getOrStub(anime.source).getNameForAnimeInfo(),
                            parentContext = screenModelScope.coroutineContext,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()
            mutableState.update { it.copy(items = anime.toImmutableList()) }
            runMigrations(anime)
        }
    }

    private suspend fun getEpisodeInfo(id: Long) = getEpisodesByAnimeId.await(id).let { episodes ->
        EpisodeInfo(
            latestEpisode = episodes.maxOfOrNull { it.episodeNumber },
            episodeCount = episodes.size,
        )
    }

    private suspend fun runMigrations(items: List<MigratingAnime>) {
        val semaphore = Semaphore(3)
        val deepSearchMode = false
        val prioritizeByEpisodes = false
        val sourceIds = preferences.migrationAnimeSources.get()

        items.forEach { anime ->
            anime.migrationScope.launch {
                semaphore.withPermit {
                    val result = searchAnime(anime.anime, sourceIds, deepSearchMode, prioritizeByEpisodes)
                    anime.searchResult.value = result?.first?.toSuccessSearchResult() ?: SearchResult.NotFound

                    if (result != null && result.first.thumbnailUrl == null) {
                        try {
                            val newAnime = sourceManager.getOrStub(
                                result.first.source,
                            ).getAnimeDetails(result.first.toSAnime())
                            updateAnime.awaitUpdateFromSource(result.first, newAnime, true)
                        } catch (_: Exception) {
                        }
                    }

                    if (result == null && hideUnmatched) {
                        removeAnime(anime.anime.id)
                    }
                    updateMigrationProgress()
                }
            }
        }
    }

    private suspend fun searchAnime(
        anime: Anime,
        sourceIds: List<Long>,
        deepSearchMode: Boolean,
        prioritizeByEpisodes: Boolean,
    ): Pair<Anime, EpisodeInfo>? {
        val sources = sourceIds
            .mapNotNull { sourceManager.get(it) as? AnimeCatalogueSource }

        return if (prioritizeByEpisodes) {
            val results = sources.map { source ->
                screenModelScope.async { searchSource(anime, source, deepSearchMode) }
            }
                .awaitAll()
                .filterNotNull()

            results.maxByOrNull { it.second.latestEpisode ?: 0.0 }
        } else {
            for (source in sources) {
                val result = searchSource(anime, source, deepSearchMode)
                if (result != null) return result
            }
            null
        }
    }

    private suspend fun searchSource(
        anime: Anime,
        source: AnimeCatalogueSource,
        deepSearchMode: Boolean,
    ): Pair<Anime, EpisodeInfo>? {
        return try {
            val searchResult = if (deepSearchMode) {
                smartSearchEngine.deepSearch(source, anime.title)
            } else {
                smartSearchEngine.regularSearch(source, anime.title)
            }

            if (searchResult == null || (searchResult.url == anime.url && source.id == anime.source)) return null

            val localAnime = networkToLocalAnime.await(searchResult)
            try {
                val episodes = source.getEpisodeList(localAnime.toSAnime())
                syncEpisodesWithSource.await(episodes, localAnime, source)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            localAnime to getEpisodeInfo(localAnime.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun updateMigrationProgress() {
        mutableState.update { state ->
            state.copy(
                finishedCount = items.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = migrationComplete(),
            )
        }
    }

    private fun migrationComplete() = items.all { it.searchResult.value != SearchResult.Searching } &&
        items.any { it.searchResult.value is SearchResult.Success }

    fun migrateNow() {
        migrateAnimes(replace = true)
    }

    fun copyAnimes() {
        migrateAnimes(replace = false)
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
    }

    fun migrateNow(animeId: Long, replace: Boolean) {
        val item = items.find { it.anime.id == animeId } ?: return
        val target = (item.searchResult.value as? SearchResult.Success)?.anime ?: return
        screenModelScope.launchIO {
            try {
                migrateAnime(current = item.anime, target = target, replace = replace)
                removeAnime(animeId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, throwable = e)
            }
        }
    }

    fun migrateAnimes(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            mutableState.update { it.copy(dialog = Dialog.Progress(0f)) }
            val items = items
            try {
                items.forEachIndexed { index, anime ->
                    try {
                        val target = (anime.searchResult.value as? SearchResult.Success)?.anime
                        if (target != null) {
                            migrateAnime(current = anime.anime, target = target, replace = replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    mutableState.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / items.size).coerceAtMost(1f)))
                    }
                }
                navigateBack()
            } finally {
                mutableState.update { it.copy(dialog = null) }
                migrateJob = null
            }
        }
    }

    fun removeAnime(animeId: Long) {
        mutableState.update { state ->
            val item = state.items.find { it.anime.id == animeId } ?: return@update state
            item.migrationScope.cancel()
            state.copy(items = state.items.toPersistentList().remove(item))
        }
        updateMigrationProgress()
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    private suspend fun Anime.toSuccessSearchResult(): SearchResult.Success {
        val episodeInfo = getEpisodeInfo(id)
        return SearchResult.Success(
            anime = this,
            episodeCount = episodeInfo.episodeCount,
            latestEpisode = episodeInfo.latestEpisode,
            source = sourceManager.getOrStub(source).getNameForAnimeInfo(),
        )
    }

    override fun onDispose() {
        super.onDispose()
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = items.size,
                    skippedCount = items.count {
                        it.searchResult.value == SearchResult.Searching ||
                            it.searchResult.value == SearchResult.NotFound
                    },
                ),
            )
        }
    }

    fun showExitDialog() {
        mutableState.update {
            it.copy(dialog = Dialog.Exit)
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    data class EpisodeInfo(
        val latestEpisode: Double?,
        val episodeCount: Int,
    )

    sealed interface Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(@FloatRange(from = 0.0, to = 1.0) val progress: Float) : Dialog
        data object Exit : Dialog
    }

    data class State(
        val items: ImmutableList<MigratingAnime> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val dialog: Dialog? = null,
    )
}
