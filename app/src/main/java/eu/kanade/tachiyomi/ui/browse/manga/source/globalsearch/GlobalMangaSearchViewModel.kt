package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.CatalogueSource

class GlobalMangaSearchViewModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : MangaSearchViewModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    companion object {
        val INITIAL_QUERY_KEY = CreationExtras.Key<String>()
        val INITIAL_EXTENSION_FILTER_KEY = CreationExtras.Key<String?>()

        val Factory = viewModelFactory {
            initializer {
                GlobalMangaSearchViewModel(
                    initialQuery = get(INITIAL_QUERY_KEY) ?: "",
                    initialExtensionFilter = get(INITIAL_EXTENSION_FILTER_KEY),
                )
            }
        }
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(MangaSourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
