package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchViewModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeSearchViewModel(
    val animeId: Long,
    initialExtensionFilter: String = "",
    getAnime: GetAnime = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : AnimeSearchViewModel() {

    init {
        extensionFilter = initialExtensionFilter
        viewModelScope.launch {
            val anime = getAnime.await(animeId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = anime.source,
                    searchQuery = anime.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        val migrationSources = sourcePreferences.migrationAnimeSources.get()
        return super.getEnabledSources()
            .filter { migrationSources.isEmpty() || it.id in migrationSources }
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
