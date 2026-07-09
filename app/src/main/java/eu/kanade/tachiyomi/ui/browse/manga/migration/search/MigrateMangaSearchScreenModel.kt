package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import eu.kanade.domain.source.service.SourcePreferences
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaSearchScreenModel(
    val mangaId: Long,
    initialExtensionFilter: String = "",
    getManga: GetManga = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : MangaSearchScreenModel() {

    init {
        extensionFilter = initialExtensionFilter
        screenModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = manga.source,
                    searchQuery = manga.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        val migrationSources = sourcePreferences.migrationMangaSources.get()
        return super.getEnabledSources()
            .filter { migrationSources.isEmpty() || it.id in migrationSources }
            .filter { state.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
