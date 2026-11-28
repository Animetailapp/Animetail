package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.ExtensionPreferencesSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.ExtensionSourcePreferencesContent
import eu.kanade.tachiyomi.ui.player.cache.VideoCacheProxy
import eu.kanade.tachiyomi.ui.player.loader.HosterCache
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * @param animeId the anime the screen was opened from, whose cached videos are the only ones
 * cleared here; null clears those of the whole source.
 */
@Composable
fun AnimeSourcePreferencesScreen(sourceId: Long, animeId: Long? = null) {
    if (!ifAnimeSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val scope = rememberCoroutineScope()
    var showHosterPriority by remember { mutableStateOf(false) }
    if (showHosterPriority) {
        SourceHosterPriorityDialog(sourceId, onDismissRequest = { showHosterPriority = false })
    }

    ExtensionSourcePreferencesContent(sourceId) { context ->
        val source = context.appGraph.animeSourceManager.getOrStub(sourceId)
        val configurable = source as? ConfigurableAnimeSource
        val preferences = configurable?.let {
            withContext(Dispatchers.IO) { it.sourcePreferences().apply { all } }
        }
        val linkCacheBytes = withIOContext { HosterCache.shared(context).sizeBytes(sourceId) }
        val videoCacheBytes = withIOContext { videoCacheSizeBytes(context, sourceId, animeId) }
        ExtensionPreferencesSource(source.toString(), preferences) { screen ->
            configurable?.setupPreferenceScreen(screen)
            if (source is AnimeHttpSource) {
                screen.addHosterCachePreferences(
                    sourceId = sourceId,
                    isForAnime = animeId != null,
                    linkCacheBytes = linkCacheBytes,
                    videoCacheBytes = videoCacheBytes,
                    onHosterPriorityClick = { showHosterPriority = true },
                    onClearVideoCache = { onCleared ->
                        scope.launchIO { clearVideoCache(context, sourceId, animeId, onCleared) }
                    },
                )
            }
        }
    }
}

private suspend fun videoCacheSizeBytes(context: Context, sourceId: Long, animeId: Long?): Long =
    if (animeId != null) {
        VideoCacheProxy.episodesSizeBytes(context, sourceId, animeEpisodeUrls(context, animeId))
    } else {
        VideoCacheProxy.sourceSizeBytes(context, sourceId)
    }

private suspend fun clearVideoCache(context: Context, sourceId: Long, animeId: Long?, onCleared: () -> Unit) {
    if (animeId != null) {
        VideoCacheProxy.clearEpisodes(context, sourceId, animeEpisodeUrls(context, animeId))
    } else {
        VideoCacheProxy.clearSource(context, sourceId)
    }
    withUIContext {
        onCleared()
        context.toast(AYMR.strings.pref_video_cache_cleared)
    }
}

private suspend fun animeEpisodeUrls(context: Context, animeId: Long): List<String> =
    context.appGraph.getEpisodesByAnimeId.await(animeId).map { it.url }
