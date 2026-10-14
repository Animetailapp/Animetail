package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import mihon.graphql.anilist.AniListGetAnimeDetailsQuery
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibAnimeQuery
import mihon.graphql.anilist.AniListGetLibMangaQuery
import mihon.graphql.anilist.AniListGetMangaDetailsQuery
import mihon.graphql.anilist.AniListGetPopularAnimeQuery
import mihon.graphql.anilist.AniListGetPopularMangaQuery
import mihon.graphql.anilist.AniListSearchAnimeQuery
import mihon.graphql.anilist.AniListSearchMangaQuery
import mihon.graphql.anilist.fragment.AnimeFragment
import mihon.graphql.anilist.fragment.MangaFragment
import mihon.graphql.anilist.type.MediaListStatus
import mihon.graphql.anilist.type.ScoreFormat
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

internal fun AniListGetCurrentUserQuery.Viewer.toALUser(): ALUser {
    return ALUser(
        id = id,
        name = name,
        scoreFormat = mediaListOptions?.scoreFormat?.rawValue ?: ScoreFormat.POINT_10_DECIMAL.rawValue,
    )
}

internal fun AniListGetLibMangaQuery.MediaList.toTrack(trackId: Long): MangaTrack {
    val mediaList = this
    requireNotNull(mediaList.media) { "Missing Media data from AniList" }

    return mediaList.media.mangaFragment
        .toTrackSearch(trackId)
        .apply {
            library_id = mediaList.id.toLong()
            status = when (mediaList.status) {
                MediaListStatus.CURRENT -> Anilist.READING
                MediaListStatus.COMPLETED -> Anilist.COMPLETED
                MediaListStatus.PAUSED -> Anilist.ON_HOLD
                MediaListStatus.DROPPED -> Anilist.DROPPED
                MediaListStatus.PLANNING -> Anilist.PLAN_TO_READ
                MediaListStatus.REPEATING -> Anilist.REREADING
                else -> throw NotImplementedError("Unknown status: ${mediaList.status?.rawValue}")
            }
            score = mediaList.scoreRaw ?: 0.0
            last_chapter_read = mediaList.progress?.toDouble() ?: 0.0

            started_reading_date = mediaList.startedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            finished_reading_date = mediaList.completedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            private = mediaList.private ?: false
        }
}

internal fun AniListGetLibAnimeQuery.MediaList.toTrack(trackId: Long): AnimeTrack {
    val mediaList = this
    requireNotNull(mediaList.media) { "Missing Media data from AniList" }

    return mediaList.media.animeFragment
        .toTrackSearch(trackId)
        .apply {
            library_id = mediaList.id.toLong()
            status = when (mediaList.status) {
                MediaListStatus.CURRENT -> Anilist.WATCHING
                MediaListStatus.COMPLETED -> Anilist.COMPLETED
                MediaListStatus.PAUSED -> Anilist.ON_HOLD
                MediaListStatus.DROPPED -> Anilist.DROPPED
                MediaListStatus.PLANNING -> Anilist.PLAN_TO_WATCH
                MediaListStatus.REPEATING -> Anilist.REWATCHING
                else -> throw NotImplementedError("Unknown status: ${mediaList.status?.rawValue}")
            }
            score = mediaList.scoreRaw ?: 0.0
            last_episode_seen = mediaList.progress?.toDouble() ?: 0.0

            started_watching_date = mediaList.startedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            finished_watching_date = mediaList.completedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            private = mediaList.private ?: false
        }
}

internal fun AniListSearchMangaQuery.Medium.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun AniListGetMangaDetailsQuery.Medium.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun AniListGetPopularMangaQuery.Medium.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun AniListSearchAnimeQuery.Medium.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun AniListGetAnimeDetailsQuery.Medium.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun AniListGetPopularAnimeQuery.Medium.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

private fun MangaFragment.toTrackSearch(trackId: Long): MangaTrackSearch {
    val alManga = this
    return MangaTrackSearch.create(trackId).apply {
        remote_id = alManga.id.toLong()
        title = alManga.title?.userPreferred ?: "(no title)"
        total_chapters = alManga.chapters?.toLong() ?: 0
        cover_url = alManga.coverImage?.large ?: ""
        summary = alManga.description?.htmlDecode() ?: ""
        score = alManga.averageScore?.toDouble() ?: -1.0
        tracking_url = "https://anilist.co/manga/$remote_id"
        publishing_status = alManga.status?.rawValue ?: ""
        publishing_type = if (alManga.format == null) {
            "Manga"
        } else if (alManga.format.rawValue != "MANGA") {
            alManga.format.rawValue.replace("_", "-")
        } else {
            when (alManga.countryOfOrigin) {
                "KR" -> "Manhwa"
                "CN", "TW" -> "Manhua"
                else -> "Manga"
            }
        }
        start_date = alManga.startDate?.let {
            buildString {
                it.year?.let { year -> append(year) }
                it.month?.let { month -> append("-$month") }
                it.day?.let { day -> append("-$day") }
            }.trim('-')
        } ?: ""

        authors = alManga.staff?.edges
            ?.filter { "Story" in (it?.role ?: "") }
            ?.mapNotNull { it?.node?.name?.userPreferred }
            ?: emptyList()

        artists = alManga.staff?.edges
            ?.filter { "Art" in (it?.role ?: "") }
            ?.mapNotNull { it?.node?.name?.userPreferred }
            ?: emptyList()
    }
}

internal fun AnimeFragment.toTrackSearch(trackId: Long): AnimeTrackSearch {
    val alAnime = this
    return AnimeTrackSearch.create(trackId).apply {
        remote_id = alAnime.id.toLong()
        title = alAnime.title?.userPreferred ?: "(no title)"
        total_episodes = alAnime.episodes?.toLong() ?: 0
        cover_url = alAnime.coverImage?.large ?: ""
        summary = alAnime.description?.htmlDecode() ?: ""
        score = alAnime.averageScore?.toDouble() ?: -1.0
        tracking_url = "https://anilist.co/anime/$remote_id"
        publishing_status = alAnime.status?.rawValue ?: ""
        publishing_type = alAnime.format?.rawValue?.replace("_", "-") ?: "TV"
        start_date = alAnime.startDate?.let {
            buildString {
                it.year?.let { year -> append(year) }
                it.month?.let { month -> append("-$month") }
                it.day?.let { day -> append("-$day") }
            }.trim('-')
        } ?: ""

        authors = alAnime.staff?.edges
            ?.filter { "Original Creator" in (it?.role ?: "") }
            ?.mapNotNull { it?.node?.name?.userPreferred }
            ?: emptyList()

        artists = alAnime.studios?.edges
            ?.filter { it?.isMain == true }
            ?.mapNotNull { it?.node?.name }
            ?.ifEmpty {
                alAnime.studios.edges.mapNotNull { it?.node?.name }
            }
            ?: emptyList()
    }
}

internal fun MangaTrack.toApiStatus(): MediaListStatus = when (status) {
    Anilist.READING -> MediaListStatus.CURRENT
    Anilist.COMPLETED -> MediaListStatus.COMPLETED
    Anilist.ON_HOLD -> MediaListStatus.PAUSED
    Anilist.DROPPED -> MediaListStatus.DROPPED
    Anilist.PLAN_TO_READ -> MediaListStatus.PLANNING
    Anilist.REREADING -> MediaListStatus.REPEATING
    else -> throw NotImplementedError("Unknown status: $status")
}

internal fun AnimeTrack.toApiStatus(): MediaListStatus = when (status) {
    Anilist.WATCHING -> MediaListStatus.CURRENT
    Anilist.COMPLETED -> MediaListStatus.COMPLETED
    Anilist.ON_HOLD -> MediaListStatus.PAUSED
    Anilist.DROPPED -> MediaListStatus.DROPPED
    Anilist.PLAN_TO_WATCH -> MediaListStatus.PLANNING
    Anilist.REWATCHING -> MediaListStatus.REPEATING
    else -> throw NotImplementedError("Unknown status: $status")
}

internal fun DomainMangaTrack.toApiScore(preferences: TrackPreferences): String =
    when (preferences.anilistScoreType.get()) {
        // 10 point
        "POINT_10" -> (score.toInt() / 10).toString()

        // 100 point
        "POINT_100" -> score.toInt().toString()

        // 5 stars
        "POINT_5" -> when {
            score == 0.0 -> "0"
            score < 30 -> "1"
            score < 50 -> "2"
            score < 70 -> "3"
            score < 90 -> "4"
            else -> "5"
        }

        // Smiley
        "POINT_3" -> when {
            score == 0.0 -> "0"
            score <= 35 -> ":("
            score <= 60 -> ":|"
            else -> ":)"
        }

        // 10 point decimal
        "POINT_10_DECIMAL" -> (score / 10).toString()

        else -> throw NotImplementedError("Unknown score type")
    }

internal fun DomainAnimeTrack.toApiScore(preferences: TrackPreferences): String =
    when (preferences.anilistScoreType.get()) {
        // 10 point
        "POINT_10" -> (score.toInt() / 10).toString()

        // 100 point
        "POINT_100" -> score.toInt().toString()

        // 5 stars
        "POINT_5" -> when {
            score == 0.0 -> "0"
            score < 30 -> "1"
            score < 50 -> "2"
            score < 70 -> "3"
            score < 90 -> "4"
            else -> "5"
        }

        // Smiley
        "POINT_3" -> when {
            score == 0.0 -> "0"
            score <= 35 -> ":("
            score <= 60 -> ":|"
            else -> ":)"
        }

        // 10 point decimal
        "POINT_10_DECIMAL" -> (score / 10).toString()

        else -> throw NotImplementedError("Unknown score type")
    }
