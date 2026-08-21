package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack

fun MangaTrack.toKitsuApiStatus() = when (status) {
    Kitsu.READING -> "CURRENT"
    Kitsu.COMPLETED -> "COMPLETED"
    Kitsu.ON_HOLD -> "ON_HOLD"
    Kitsu.DROPPED -> "DROPPED"
    Kitsu.PLAN_TO_READ -> "PLANNED"
    else -> throw Exception("Unknown status: $status")
}

fun AnimeTrack.toKitsuApiStatus() = when (status) {
    Kitsu.WATCHING -> "CURRENT"
    Kitsu.COMPLETED -> "COMPLETED"
    Kitsu.ON_HOLD -> "ON_HOLD"
    Kitsu.DROPPED -> "DROPPED"
    Kitsu.PLAN_TO_WATCH -> "PLANNED"
    else -> throw Exception("Unknown status: $status")
}

fun String.toKitsuLocalStatus() = when (this) {
    "CURRENT" -> Kitsu.READING
    "COMPLETED" -> Kitsu.COMPLETED
    "ON_HOLD" -> Kitsu.ON_HOLD
    "DROPPED" -> Kitsu.DROPPED
    "PLANNED" -> Kitsu.PLAN_TO_READ
    else -> throw Exception("Unknown status: $this")
}

fun String.toKitsuLocalStatusAnime() = when (this) {
    "CURRENT" -> Kitsu.WATCHING
    "COMPLETED" -> Kitsu.COMPLETED
    "ON_HOLD" -> Kitsu.ON_HOLD
    "DROPPED" -> Kitsu.DROPPED
    "PLANNED" -> Kitsu.PLAN_TO_WATCH
    else -> throw Exception("Unknown status: $this")
}
