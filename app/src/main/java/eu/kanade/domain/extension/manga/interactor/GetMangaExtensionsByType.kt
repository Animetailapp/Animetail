package eu.kanade.domain.extension.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.extension.manga.model.MangaExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Inject
class GetMangaExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: MangaExtensionManager,
) {

    fun subscribe(): Flow<MangaExtensions> {
        val enabledContentWarnings = preferences.enabledContentWarnings.get()

        return combine(
            preferences.enabledLanguages.changes(),
            preferences.disabledRepos.changes(),
            extensionManager.loadedExtensionsFlow,
            extensionManager.notLoadedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, disabledRepos, _loaded, _notLoaded, _available ->
            val (updates, loaded) = _loaded
                .sortedWith(
                    compareBy<MangaExtension.Loaded> { !it.isObsolete }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val notLoaded = _notLoaded
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = _available
                .filter { extension ->
                    extension.store.indexUrl !in disabledRepos &&
                        _loaded.none {
                            it.pkgName == extension.pkgName
                        } &&
                        _notLoaded.none {
                            it.pkgName == extension.pkgName
                        } &&
                        extension.contentWarning in enabledContentWarnings
                }
                .flatMap { ext ->
                    if (ext.sources.isEmpty()) {
                        return@flatMap if (ext.lang in enabledLanguages) listOf(ext) else emptyList()
                    }
                    ext.sources.filter { it.lang in enabledLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            MangaExtensions(updates, loaded, available, notLoaded)
        }
    }
}
