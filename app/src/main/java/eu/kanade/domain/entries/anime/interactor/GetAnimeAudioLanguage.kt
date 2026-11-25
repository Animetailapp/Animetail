package eu.kanade.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DefaultAudioLanguage
import eu.kanade.tachiyomi.ui.player.utils.AudioLanguageTarget
import eu.kanade.tachiyomi.ui.player.utils.LanguageTags
import eu.kanade.tachiyomi.ui.player.utils.OriginalLanguageResolver
import eu.kanade.tachiyomi.ui.player.utils.OriginalLanguageResolver.Origin
import eu.kanade.tachiyomi.ui.player.utils.OriginalLanguageResolver.Resolved
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

/**
 * Works out which audio language to play or download for an anime, from its own setting, the
 * global "Default audio language" setting and its original language.
 */
@Inject
class GetAnimeAudioLanguage(
    private val audioPreferences: AudioPreferences,
    private val animeRepository: AnimeRepository,
    private val getTracks: GetAnimeTracks,
    private val trackerManager: TrackerManager,
    private val sourceManager: AnimeSourceManager,
) {

    suspend fun await(anime: Anime): AudioLanguageTarget {
        // The caller's copy can predate a change made from the anime screen or the player
        val current = anime.takeIf { it.id < 0 } ?: getAnime(anime.id) ?: anime
        val parent = current.parentId?.let { getAnime(it) }
        val sourceLanguage = sourceManager.get(current.source)?.lang
        // A season without its own setting follows the show's
        val setting = current.audioLanguage.takeUnless { it == Anime.AUDIO_LANGUAGE_DEFAULT }
            ?: parent?.audioLanguage
            ?: Anime.AUDIO_LANGUAGE_DEFAULT
        return AudioLanguageTarget.build(
            animeSetting = setting,
            originalByDefault = audioPreferences.defaultAudioLanguage().get() == DefaultAudioLanguage.Original,
            preferred = LanguageTags.parseList(audioPreferences.preferredAudioLanguages().get()),
            originalLanguage = originalLanguage(current, parent, sourceLanguage),
            sourceLanguage = sourceLanguage,
        )
    }

    /**
     * Resolves the anime's original language and caches it in the anime's `original_language`.
     * A tracker answer is kept until cleared; cheaper answers are recomputed so that a tracker
     * added later, or a refreshed description, can still improve them.
     */
    suspend fun originalLanguage(anime: Anime, parent: Anime? = null, sourceLanguage: String? = null): String? {
        OriginalLanguageResolver.fromMemo(anime.memo)?.let { return cache(anime, it, Origin.SOURCE) }

        val cached = Resolved.decode(anime.originalLanguage)
        if (cached?.origin == Origin.TRACKER) return cached.language

        (fromTracker(anime.id) ?: parent?.let { fromTracker(it.id) })
            ?.let { return cache(anime, it, Origin.TRACKER) }

        val lang = sourceLanguage ?: sourceManager.get(anime.source)?.lang
        val fromMetadata = OriginalLanguageResolver.fromMetadata(anime.title, anime.description, lang)
            ?: parent?.let { OriginalLanguageResolver.fromMetadata(it.title, it.description, lang) }
        return fromMetadata?.let { cache(anime, it, Origin.METADATA) }
    }

    private suspend fun fromTracker(animeId: Long): String? {
        val aniList = trackerManager.aniList
        if (!aniList.isLoggedIn) return null
        val track = getTracks.await(animeId).firstOrNull { it.trackerId == aniList.id } ?: return null
        val country = try {
            aniList.getAnimeCountryOfOrigin(track.remoteId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to get the AniList country of origin of anime $animeId" }
            null
        }
        return OriginalLanguageResolver.fromCountryOfOrigin(country)
    }

    private suspend fun cache(anime: Anime, language: String, origin: Origin): String {
        val encoded = Resolved(language, origin).encode()
        if (anime.id >= 0 && anime.originalLanguage != encoded) {
            animeRepository.updateAnime(AnimeUpdate(id = anime.id, originalLanguage = encoded))
        }
        return language
    }

    private suspend fun getAnime(id: Long): Anime? {
        return try {
            animeRepository.getAnimeById(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}

@Inject
class SetAnimeAudioLanguage(
    private val animeRepository: AnimeRepository,
) {
    /** [value] is [Anime.AUDIO_LANGUAGE_DEFAULT], [Anime.AUDIO_LANGUAGE_ORIGINAL] or a language tag. */
    suspend fun await(animeId: Long, value: String): Boolean {
        return animeRepository.updateAnime(AnimeUpdate(id = animeId, audioLanguage = value))
    }
}
