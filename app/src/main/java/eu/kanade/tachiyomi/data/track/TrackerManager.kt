package eu.kanade.tachiyomi.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.comick.Comick
import eu.kanade.tachiyomi.data.track.hikka.Hikka
import eu.kanade.tachiyomi.data.track.jellyfin.Jellyfin
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.data.track.tmdb.Tmdb
import eu.kanade.tachiyomi.data.track.trakt.Trakt
import kotlinx.coroutines.flow.combine

@Inject
@SingleIn(AppScope::class)
class TrackerManager(
    val myAnimeList: MyAnimeList,
    val aniList: Anilist,
    val kitsu: Kitsu,
    val shikimori: Shikimori,
    val bangumi: Bangumi,
    val komga: Komga,
    val mangaUpdates: MangaUpdates,
    val kavita: Kavita,
    val suwayomi: Suwayomi,
    val simkl: Simkl,
    val jellyfin: Jellyfin,
    val tmdb: Tmdb,
    val trakt: Trakt,
    val hikka: Hikka,
    val mangaBaka: MangaBaka,
    val comick: Comick,
) {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
        const val MANGABAKA = 11L
        const val COMICK = 12L
        const val SIMKL = 101L
        const val JELLYFIN = 102L
        const val TRAKT = 201L
    }

    val trackers = listOf(
        myAnimeList, aniList, kitsu, shikimori, bangumi,
        komga, mangaUpdates, kavita, suwayomi, simkl, jellyfin,
        tmdb, trakt, hikka, mangaBaka, comick,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
