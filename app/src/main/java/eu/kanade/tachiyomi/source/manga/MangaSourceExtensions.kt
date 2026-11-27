package eu.kanade.tachiyomi.source.manga

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import mihon.domain.extension.model.ContentWarning
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.source.local.entries.manga.isLocal

suspend fun MangaSource.icon(extensionManager: MangaExtensionManager): Drawable? =
    extensionManager.getAppIconForSource(this.id)

fun MangaSource.getPreferenceKey(): String = "source_$id"

fun MangaSource.toStubSource(): StubMangaSource = StubMangaSource(id = id, lang = lang, name = name)

fun MangaSource.getNameForMangaInfo(preferences: SourcePreferences): String {
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

fun MangaSource.isLocalOrStub(): Boolean = isLocal() || this is StubMangaSource

// AM (DISCORD) -->
fun MangaSource?.isNsfw(extensionManager: MangaExtensionManager): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = extensionManager.loadedExtensions
        .find { ext -> ext.sources.any { it.id == this.id } }
    return sourceUsed?.contentWarning == ContentWarning.NSFW
}
// <-- AM (DISCORD)
