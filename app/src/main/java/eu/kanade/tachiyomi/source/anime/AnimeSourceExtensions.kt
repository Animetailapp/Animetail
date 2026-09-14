package eu.kanade.tachiyomi.source.anime

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import mihon.domain.extension.model.ContentWarning
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

suspend fun AnimeSource.icon(): Drawable? = Injekt.get<AnimeExtensionManager>().getAppIconForSource(this.id)

fun AnimeSource.getPreferenceKey(): String = "source_$id"

fun AnimeSource.toStubSource(): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)

fun AnimeSource.getNameForAnimeInfo(): String {
    val preferences = Injekt.get<SourcePreferences>()
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
fun AnimeSource?.isNsfw(): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = Injekt.get<AnimeExtensionManager>().loadedExtensions
        .find { ext -> ext.sources.any { it.id == this.id } }
    return sourceUsed?.contentWarning == ContentWarning.NSFW
}

// <-- AM (DISCORD)
fun AnimeSource?.isSourceForTorrents(): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = Injekt.get<AnimeExtensionManager>().loadedExtensions
        .find { ext -> ext.sources.any { it.id == this.id } }
    return sourceUsed?.isTorrent ?: false
}
