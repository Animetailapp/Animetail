package eu.kanade.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Inject
class GetAnimeExtensionLanguages(
    private val preferences: SourcePreferences,
    private val extensionManager: AnimeExtensionManager,
) {
    fun subscribe(): Flow<List<String>> {
        // Installed extensions count too, otherwise the list is empty when no repo is added.
        return combine(
            preferences.enabledLanguages.changes(),
            extensionManager.availableExtensionsFlow,
            extensionManager.loadedExtensionsFlow,
        ) { enabledLanguage, availableExtensions, installedExtensions ->
            val availableLanguages = availableExtensions.flatMap { ext -> ext.sources.map { it.lang } }
            val installedLanguages = installedExtensions.flatMap { ext -> ext.sources.map { it.lang } }
            (availableLanguages + installedLanguages)
                .distinct()
                .sortedWith(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
