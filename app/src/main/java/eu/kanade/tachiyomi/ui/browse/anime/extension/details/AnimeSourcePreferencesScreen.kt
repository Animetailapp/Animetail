package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.ExtensionPreferencesSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.ExtensionSourcePreferencesContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeSourcePreferencesScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        ExtensionSourcePreferencesContent(sourceId) { context ->
            val source = context.appGraph.animeSourceManager.getOrStub(sourceId)
            val configurable = source as? ConfigurableAnimeSource
            val preferences = configurable?.let {
                withContext(Dispatchers.IO) { it.sourcePreferences().apply { all } }
            }
            ExtensionPreferencesSource(source.toString(), preferences) { configurable?.setupPreferenceScreen(it) }
        }
    }
}
