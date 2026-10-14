package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import mihon.graphql.kitsu.KitsuFindLibAnimeQuery
import mihon.graphql.kitsu.KitsuFindLibMangaQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuSearchAnimeByTitleQuery
import mihon.graphql.kitsu.KitsuSearchMangaByTitleQuery
import mihon.graphql.kitsu.fragment.AnimeFragment
import mihon.graphql.kitsu.fragment.MangaFragment
import mihon.graphql.kitsu.type.LibraryEntryStatusEnum
import mihon.graphql.kitsu.type.MangaSubtypeEnum
import mihon.graphql.kitsu.type.ReleaseStatusEnum

internal fun MangaTrack.toKitsuStatus() = when (this.status) {
    Kitsu.READING -> LibraryEntryStatusEnum.CURRENT
    Kitsu.PLAN_TO_READ -> LibraryEntryStatusEnum.PLANNED
    Kitsu.COMPLETED -> LibraryEntryStatusEnum.COMPLETED
    Kitsu.ON_HOLD -> LibraryEntryStatusEnum.ON_HOLD
    Kitsu.DROPPED -> LibraryEntryStatusEnum.DROPPED
    else -> throw Exception("Unknown status: ${this.status}")
}

internal fun AnimeTrack.toKitsuStatus() = when (status) {
    Kitsu.WATCHING -> LibraryEntryStatusEnum.CURRENT
    Kitsu.COMPLETED -> LibraryEntryStatusEnum.COMPLETED
    Kitsu.ON_HOLD -> LibraryEntryStatusEnum.ON_HOLD
    Kitsu.DROPPED -> LibraryEntryStatusEnum.DROPPED
    Kitsu.PLAN_TO_WATCH -> LibraryEntryStatusEnum.PLANNED
    else -> throw Exception("Unknown status: $status")
}

internal fun LibraryEntryStatusEnum.toLocalStatus(): Long = when (this) {
    LibraryEntryStatusEnum.CURRENT -> Kitsu.READING
    LibraryEntryStatusEnum.PLANNED -> Kitsu.PLAN_TO_READ
    LibraryEntryStatusEnum.COMPLETED -> Kitsu.COMPLETED
    LibraryEntryStatusEnum.ON_HOLD -> Kitsu.ON_HOLD
    LibraryEntryStatusEnum.DROPPED -> Kitsu.DROPPED
    else -> throw Exception("Unknown status: $this")
}

internal fun LibraryEntryStatusEnum.toLocalStatusAnime(): Long = when (this) {
    LibraryEntryStatusEnum.CURRENT -> Kitsu.WATCHING
    LibraryEntryStatusEnum.PLANNED -> Kitsu.PLAN_TO_WATCH
    LibraryEntryStatusEnum.COMPLETED -> Kitsu.COMPLETED
    LibraryEntryStatusEnum.ON_HOLD -> Kitsu.ON_HOLD
    LibraryEntryStatusEnum.DROPPED -> Kitsu.DROPPED
    else -> throw Exception("Unknown status: $this")
}

internal fun KitsuGetCurrentAccountQuery.CurrentAccount.toKitsuUser(): KitsuUser {
    return KitsuUser(
        id = id,
        name = profile.name,
        ratingSystem = ratingSystem.rawValue,
    )
}

internal fun KitsuGetMangaDetailsByIdQuery.FindMangaById.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun KitsuGetMangaDetailsBySlugQuery.FindMangaBySlug.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun KitsuSearchMangaByTitleQuery.Node.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun KitsuFindLibMangaQuery.FindMangaById.toTrackSearch(trackId: Long): MangaTrackSearch? {
    if (myLibraryEntry == null) return null

    return mangaFragment.toTrackSearch(trackId).apply {
        library_id = myLibraryEntry.id.toLong()
        started_reading_date = myLibraryEntry.startedAt?.toEpochMilliseconds() ?: 0
        finished_reading_date = myLibraryEntry.finishedAt?.toEpochMilliseconds() ?: 0
        status = myLibraryEntry.status.toLocalStatus()
        score = myLibraryEntry.rating?.toDouble() ?: 0.0
        last_chapter_read = myLibraryEntry.progress.toDouble()
        private = myLibraryEntry.private
    }
}

internal fun KitsuGetAnimeDetailsByIdQuery.FindAnimeById.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuGetAnimeDetailsBySlugQuery.FindAnimeBySlug.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuSearchAnimeByTitleQuery.Node.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuFindLibAnimeQuery.FindAnimeById.toTrackSearch(trackId: Long): AnimeTrackSearch? {
    if (myLibraryEntry == null) return null

    return animeFragment.toTrackSearch(trackId).apply {
        library_id = myLibraryEntry.id.toLong()
        started_watching_date = myLibraryEntry.startedAt?.toEpochMilliseconds() ?: 0
        finished_watching_date = myLibraryEntry.finishedAt?.toEpochMilliseconds() ?: 0
        status = myLibraryEntry.status.toLocalStatusAnime()
        score = myLibraryEntry.rating?.toDouble() ?: 0.0
        last_episode_seen = myLibraryEntry.progress.toDouble()
        private = myLibraryEntry.private
    }
}

private fun MangaFragment.toTrackSearch(trackId: Long): MangaTrackSearch {
    val kitsuManga = this
    return MangaTrackSearch.create(trackId).apply {
        remote_id = kitsuManga.id.toLong()
        title = kitsuManga.titles.preferred
        total_chapters = kitsuManga.chapterCount?.toLong() ?: 0
        cover_url = kitsuManga.posterImage?.views?.firstOrNull()?.url ?: kitsuManga.posterImage?.original?.url.orEmpty()
        summary = (kitsuManga.description["en"] as? String).orEmpty()
        tracking_url = "https://kitsu.app/manga/${kitsuManga.slug}"
        score = kitsuManga.averageRating ?: -1.0
        publishing_status = when (kitsuManga.status) {
            ReleaseStatusEnum.TBA -> ReleaseStatusEnum.TBA.rawValue
            else -> kitsuManga.status.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        publishing_type = when (kitsuManga.subtype) {
            MangaSubtypeEnum.OEL -> MangaSubtypeEnum.OEL.rawValue
            else -> kitsuManga.subtype.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        start_date = kitsuManga.startDate.orEmpty()
        authors = kitsuManga.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Story") }
            ?.map { it.person.name }
            ?: emptyList()
        artists = kitsuManga.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Art") }
            ?.map { it.person.name }
            ?: emptyList()
    }
}

internal fun AnimeFragment.toTrackSearch(trackId: Long): AnimeTrackSearch {
    val kitsuAnime = this
    return AnimeTrackSearch.create(trackId).apply {
        remote_id = kitsuAnime.id.toLong()
        title = kitsuAnime.titles.preferred
        total_episodes = kitsuAnime.episodeCount?.toLong() ?: 0
        cover_url = kitsuAnime.posterImage?.views?.firstOrNull()?.url ?: kitsuAnime.posterImage?.original?.url.orEmpty()
        summary = (kitsuAnime.description["en"] as? String).orEmpty()
        tracking_url = "https://kitsu.app/anime/${kitsuAnime.slug}"
        score = kitsuAnime.averageRating ?: -1.0
        publishing_status = when (kitsuAnime.status) {
            ReleaseStatusEnum.TBA -> ReleaseStatusEnum.TBA.rawValue
            else -> kitsuAnime.status.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        publishing_type = kitsuAnime.subtype.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        start_date = kitsuAnime.startDate.orEmpty()
        authors = kitsuAnime.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Director") || it.role.contains("Creator") }
            ?.map { it.person.name }
            ?: emptyList()
        artists = kitsuAnime.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Animation") || it.role.contains("Character") }
            ?.map { it.person.name }
            ?: emptyList()
    }
}
