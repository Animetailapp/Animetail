package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import mihon.graphql.shikimori.ShikimoriGetAnimeDetailsQuery
import mihon.graphql.shikimori.ShikimoriGetLibAnimeQuery
import mihon.graphql.shikimori.ShikimoriGetLibMangaQuery
import mihon.graphql.shikimori.ShikimoriGetMangaDetailsQuery
import mihon.graphql.shikimori.ShikimoriSearchAnimeQuery
import mihon.graphql.shikimori.ShikimoriSearchMangaQuery
import mihon.graphql.shikimori.fragment.AnimeFragment
import mihon.graphql.shikimori.fragment.MangaFragment
import mihon.graphql.shikimori.type.AnimeKindEnum
import mihon.graphql.shikimori.type.AnimeStatusEnum
import mihon.graphql.shikimori.type.MangaKindEnum
import mihon.graphql.shikimori.type.MangaStatusEnum
import mihon.graphql.shikimori.type.UserRateStatusEnum

internal fun ShikimoriGetLibMangaQuery.Manga.toTrack(trackId: Long): MangaTrack {
    val sManga = this
    return MangaTrack.create(trackId).apply {
        remote_id = sManga.id.toLong()
        title = sManga.name
        total_chapters = sManga.chapters.toLong()
        tracking_url = sManga.url

        if (sManga.userRate != null) {
            // null if not in user's list, must not throw here because it'd break adding titles
            // throws in the findLibManga method of ShikimoriApi if null and shouldn't be
            library_id = sManga.userRate.id.toLong()
            last_chapter_read = sManga.userRate.chapters.toDouble()
            score = sManga.userRate.score.toDouble()
            status = sManga.userRate.status.toLocalStatus()
        }
    }
}

internal fun ShikimoriGetLibAnimeQuery.Anime.toTrack(trackId: Long): AnimeTrack {
    val sAnime = this
    return AnimeTrack.create(trackId).apply {
        remote_id = sAnime.id.toLong()
        title = sAnime.name
        total_episodes = sAnime.episodes.toLong()
        tracking_url = sAnime.url

        if (sAnime.userRate != null) {
            library_id = sAnime.userRate.id.toLong()
            last_episode_seen = sAnime.userRate.episodes.toDouble()
            score = sAnime.userRate.score.toDouble()
            status = sAnime.userRate.status.toLocalStatus()
        }
    }
}

internal fun ShikimoriGetMangaDetailsQuery.Manga.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun ShikimoriSearchMangaQuery.Manga.toTrackSearch(trackId: Long): MangaTrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

internal fun ShikimoriGetAnimeDetailsQuery.Anime.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun ShikimoriSearchAnimeQuery.Anime.toTrackSearch(trackId: Long): AnimeTrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

private fun MangaFragment.toTrackSearch(trackId: Long): MangaTrackSearch {
    val sManga = this
    return MangaTrackSearch.create(trackId).apply {
        remote_id = sManga.id.toLong()
        title = sManga.name
        total_chapters = sManga.chapters.toLong()
        cover_url = sManga.poster?.mainUrl.orEmpty()
        summary = sManga.description.orEmpty()
        score = sManga.score?.takeIf { it > 0.0 } ?: -1.0
        tracking_url = sManga.url
        publishing_status = sManga.status.toLocalString()
        publishing_type = sManga.kind.toLocalString()
        start_date = sManga.airedOn?.date.orEmpty()
        authors = sManga.personRoles?.filter { it.rolesEn.contains("Story") }?.map { it.person.name } ?: emptyList()
        artists = sManga.personRoles?.filter { it.rolesEn.contains("Art") }?.map { it.person.name } ?: emptyList()
    }
}

private fun AnimeFragment.toTrackSearch(trackId: Long): AnimeTrackSearch {
    val sAnime = this
    return AnimeTrackSearch.create(trackId).apply {
        remote_id = sAnime.id.toLong()
        title = sAnime.name
        total_episodes = sAnime.episodes.toLong()
        cover_url = sAnime.poster?.mainUrl.orEmpty()
        summary = sAnime.description.orEmpty()
        score = sAnime.score?.takeIf { it > 0.0 } ?: -1.0
        tracking_url = sAnime.url
        publishing_status = sAnime.status.toLocalString()
        publishing_type = sAnime.kind.toLocalString()
        start_date = sAnime.airedOn?.date.orEmpty()
        authors = sAnime.personRoles?.filter { it.rolesEn.contains("Story") }?.map { it.person.name } ?: emptyList()
        artists = sAnime.personRoles?.filter { it.rolesEn.contains("Art") }?.map { it.person.name } ?: emptyList()
    }
}

private fun MangaKindEnum?.toLocalString() = when (this) {
    MangaKindEnum.manga -> "Manga"

    MangaKindEnum.manhwa -> "Manhwa"

    MangaKindEnum.manhua -> "Manhua"

    MangaKindEnum.one_shot -> "Oneshot"

    MangaKindEnum.doujin -> "Doujin"

    // light novel & novel omitted by query filter, fall back to empty if included
    else -> ""
}

private fun MangaStatusEnum?.toLocalString() = when (this) {
    MangaStatusEnum.anons -> "Planned"
    MangaStatusEnum.ongoing -> "Ongoing"
    MangaStatusEnum.released -> "Released"
    MangaStatusEnum.paused -> "Paused"
    MangaStatusEnum.discontinued -> "Discontinued"
    else -> ""
}

private fun AnimeKindEnum?.toLocalString() = when (this) {
    AnimeKindEnum.tv -> "TV"
    AnimeKindEnum.movie -> "Movie"
    AnimeKindEnum.ova -> "OVA"
    AnimeKindEnum.ona -> "ONA"
    AnimeKindEnum.special -> "Special"
    AnimeKindEnum.tv_special -> "TV Special"
    AnimeKindEnum.music -> "Music"
    AnimeKindEnum.pv -> "PV"
    AnimeKindEnum.cm -> "CM"
    else -> ""
}

private fun AnimeStatusEnum?.toLocalString() = when (this) {
    AnimeStatusEnum.anons -> "Planned"
    AnimeStatusEnum.ongoing -> "Ongoing"
    AnimeStatusEnum.released -> "Released"
    else -> ""
}

private fun UserRateStatusEnum.toLocalStatus() = when (this) {
    UserRateStatusEnum.planned -> Shikimori.PLAN_TO_READ
    UserRateStatusEnum.watching -> Shikimori.READING
    UserRateStatusEnum.rewatching -> Shikimori.REREADING
    UserRateStatusEnum.completed -> Shikimori.COMPLETED
    UserRateStatusEnum.on_hold -> Shikimori.ON_HOLD
    UserRateStatusEnum.dropped -> Shikimori.DROPPED
    else -> throw NotImplementedError("Unknown status: $this")
}

internal fun MangaTrack.toShikimoriStatus() = when (status) {
    // rawValues because Shikimori doesn't have GraphQL mutations and this goes through the v2 API
    // if/when Shikimori adds GraphQL mutations, this should return UserRateStatusEnum members
    Shikimori.READING -> UserRateStatusEnum.watching.rawValue

    Shikimori.COMPLETED -> UserRateStatusEnum.completed.rawValue

    Shikimori.ON_HOLD -> UserRateStatusEnum.on_hold.rawValue

    Shikimori.DROPPED -> UserRateStatusEnum.dropped.rawValue

    Shikimori.PLAN_TO_READ -> UserRateStatusEnum.planned.rawValue

    Shikimori.REREADING -> UserRateStatusEnum.rewatching.rawValue

    else -> throw NotImplementedError("Unknown status: $status")
}

internal fun AnimeTrack.toShikimoriStatus() = when (status) {
    Shikimori.READING -> UserRateStatusEnum.watching.rawValue
    Shikimori.COMPLETED -> UserRateStatusEnum.completed.rawValue
    Shikimori.ON_HOLD -> UserRateStatusEnum.on_hold.rawValue
    Shikimori.DROPPED -> UserRateStatusEnum.dropped.rawValue
    Shikimori.PLAN_TO_READ -> UserRateStatusEnum.planned.rawValue
    Shikimori.REREADING -> UserRateStatusEnum.rewatching.rawValue
    else -> throw NotImplementedError("Unknown status: $status")
}
