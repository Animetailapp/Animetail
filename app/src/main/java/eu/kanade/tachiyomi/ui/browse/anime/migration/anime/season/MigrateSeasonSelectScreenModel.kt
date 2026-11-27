package eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@AssistedInject
class MigrateSeasonSelectScreenModel(
    @Assisted private val oldAnimeId: Long,
    @Assisted private val animeId: Long,
    private val sourceManager: AnimeSourceManager,
    sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val getAnime: GetAnime,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            oldAnimeId: Long,
            animeId: Long,
        ): MigrateSeasonSelectScreenModel
    }

    var displayMode by sourcePreferences.sourceDisplayMode.asState(viewModelScope)

    init {
        viewModelScope.launch {
            val oldAnime = getAnime.await(oldAnimeId)
            val anime = getAnime.await(animeId)
            val source = anime?.let { sourceManager.getOrStub(it.source) }
            mutableState.update {
                it.copy(
                    oldAnime = oldAnime,
                    anime = anime,
                    source = source,
                )
            }
        }
    }

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.animeLandscapeColumns
        } else {
            libraryPreferences.animePortraitColumns
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    private val hideInLibraryItems = sourcePreferences.hideInAnimeLibraryItems.get()
    val seasonPagerFlowFlow = flow {
        getAnime.await(animeId)?.let { emit(it) }
    }
        .map { anime ->
            Pager(
                config = PagingConfig(pageSize = 25),
                pagingSourceFactory = {
                    SeasonListPagingSource {
                        updateAnimeFromRemote.awaitSeasonsUpdate(
                            anime = anime,
                            fetchSeasons = true,
                        )
                            .getOrThrow()
                            .newSeasons
                            .map { it.toSAnime() }
                    }
                },
            ).flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAnime.await(it.toDomainAnime(anime.source))
                        .let { localAnime -> getAnime.subscribe(localAnime.url, localAnime.source) }
                        .filterNotNull()
                        .stateIn(viewModelScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    private class SeasonListPagingSource(
        private val loadSeasonList: suspend () -> List<SAnime>,
    ) : PagingSource<Int, SAnime>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SAnime> {
            return try {
                val seasonList = loadSeasonList()

                LoadResult.Page(
                    data = seasonList,
                    prevKey = null,
                    nextKey = null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, SAnime>): Int? {
            return null
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    sealed interface Dialog {
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val oldAnime: Anime? = null,
        val anime: Anime? = null,
        val source: AnimeSource? = null,
        val dialog: Dialog? = null,
    )
}
