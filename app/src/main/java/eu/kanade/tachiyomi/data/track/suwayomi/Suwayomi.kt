package eu.kanade.tachiyomi.data.track.suwayomi

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.domain.entries.manga.model.Manga as DomainManga
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class Suwayomi(id: Long) : BaseTracker(id, "Suwayomi"), EnhancedMangaTracker, MangaTracker {

    val api by lazy { SuwayomiApi(id) }

    override fun getLogo() = R.drawable.ic_tracker_suwayomi

    override fun getLogoColor() = Color.rgb(255, 35, 35) // TODO

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L
    }

    override fun getStatusListManga(): List<Long> = listOf(UNREAD, READING, COMPLETED)

    override fun getStatusForManga(status: Long): StringResource? = when (status) {
        UNREAD -> MR.strings.unread
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }

        return api.updateProgress(track)
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        TODO("Not yet implemented")
    }

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? {
        throw NotImplementedError("Not implemented.")
    }

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata? {
        throw NotImplementedError("Not implemented.")
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources(): List<String> = listOf(
        "eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk",
    )

    override suspend fun match(manga: DomainManga): MangaTrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: DomainTrack, manga: DomainManga, source: MangaSource?): Boolean = source?.let {
        accept(
            it,
        )
    } == true

    override fun migrateTrack(track: DomainTrack, manga: DomainManga, newSource: MangaSource): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }
}
