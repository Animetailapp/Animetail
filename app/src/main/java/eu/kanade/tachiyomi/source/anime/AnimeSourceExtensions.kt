package eu.kanade.tachiyomi.source.anime

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import mihon.domain.extension.model.ContentWarning
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.source.local.entries.anime.isLocal

suspend fun AnimeSource.icon(extensionManager: AnimeExtensionManager): Drawable? =
    extensionManager.getAppIconForSource(this.id)

fun AnimeSource.getPreferenceKey(): String = "source_$id"

fun AnimeSource.toStubSource(): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)

fun AnimeSource.getNameForAnimeInfo(preferences: SourcePreferences): String {
    val enabledLanguages = preferences.enabledLanguages.get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> toString()

        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name

        else -> toString()
    }
}

fun AnimeSource.isLocalOrStub(): Boolean = isLocal() || this is StubAnimeSource

// AM (DISCORD) -->
fun AnimeSource?.isNsfw(extensionManager: AnimeExtensionManager): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = extensionManager.loadedExtensions
        .find { ext -> ext.sources.any { it.id == this.id } }
    return sourceUsed?.contentWarning == ContentWarning.NSFW
}

// <-- AM (DISCORD)
fun AnimeSource?.isSourceForTorrents(extensionManager: AnimeExtensionManager): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = extensionManager.loadedExtensions
        .find { ext -> ext.sources.any { it.id == this.id } }
    return sourceUsed?.isTorrent ?: false
}
