package tachiyomi.domain.track.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.anime.model.AnimeTrack

interface AnimeTrackRepository {

    suspend fun getTrackByAnimeId(id: Long): AnimeTrack?

    // SY -->
    suspend fun getAnimeTracks(): List<AnimeTrack>

    suspend fun getTracksByAnimeIds(animeIds: List<Long>): List<AnimeTrack>
    // SY <--

    suspend fun getTracksByAnimeId(animeId: Long): List<AnimeTrack>

    fun getAnimeTracksAsFlow(): Flow<List<AnimeTrack>>

    fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeTrack>>

    suspend fun delete(animeId: Long, trackerId: Long)

    suspend fun upsertAnime(track: AnimeTrack)

    suspend fun upsertAllAnime(tracks: List<AnimeTrack>)

    suspend fun insertAnime(track: AnimeTrack) = upsertAnime(track)

    suspend fun insertAllAnime(tracks: List<AnimeTrack>) = upsertAllAnime(tracks)
}
