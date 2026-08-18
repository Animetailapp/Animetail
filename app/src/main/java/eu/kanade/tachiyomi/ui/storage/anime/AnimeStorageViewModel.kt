package eu.kanade.tachiyomi.ui.storage.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.storage.CommonStorageViewModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeStorageViewModel(
    downloadCache: AnimeDownloadCache,
    private val getLibraries: GetLibraryAnime,
    getCategories: GetAnimeCategories,
    getVisibleCategories: GetVisibleAnimeCategories,
    private val downloadManager: AnimeDownloadManager,
    private val sourceManager: AnimeSourceManager,
    libraryPreferences: LibraryPreferences,
) : CommonStorageViewModel<LibraryAnime>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    categories = { hideHiddenCategories ->
        if (hideHiddenCategories) {
            getVisibleCategories.subscribe()
        } else {
            getCategories.subscribe()
        }
    },
    getDownloadSize = { downloadManager.getDownloadSize(anime) },
    getDownloadCount = { downloadManager.getDownloadCount(anime) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { anime.title },
    getThumbnail = { anime.thumbnailUrl },
    libraryPreferences = libraryPreferences,
) {
    override fun deleteEntry(id: Long) {
        viewModelScope.launchNonCancellable {
            val anime = getLibraries.await().find {
                it.id == id
            }?.anime ?: return@launchNonCancellable
            val source = sourceManager.get(anime.source) ?: return@launchNonCancellable
            downloadManager.deleteAnime(anime, source)
        }
    }
}
