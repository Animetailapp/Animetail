package eu.kanade.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.extension.anime.model.AnimeExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Inject
class GetAnimeExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: AnimeExtensionManager,
) {

    fun subscribe(): Flow<AnimeExtensions> {
        val enabledContentWarnings = preferences.enabledContentWarnings.get()

        return combine(
            preferences.enabledLanguages.changes(),
            preferences.disabledRepos.changes(),
            extensionManager.loadedExtensionsFlow,
            extensionManager.notLoadedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, disabledRepos, _loaded, _notLoaded, _available ->
            val byName = compareBy<AnimeExtension, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

            val (loadedUpdates, loaded) = _loaded.partition { it.hasUpdate }
            val (notLoadedUpdates, notLoaded) = _notLoaded.partition { it.hasUpdate }

            val updates = (loadedUpdates + notLoadedUpdates).sortedWith(byName)

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
                .sortedWith(byName)

            AnimeExtensions(
                updates = updates,
                loaded = loaded.sortedWith(compareBy<AnimeExtension.Loaded> { !it.isObsolete }.then(byName)),
                available = available,
                notLoaded = notLoaded.sortedWith(byName),
            )
        }
    }
}
