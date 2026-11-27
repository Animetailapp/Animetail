package eu.kanade.tachiyomi.data.track

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.domain.track.anime.interactor.UpsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.UpsertMangaTrack

@Inject
class TrackerDependencies(
    val context: Context,
    val trackPreferences: TrackPreferences,
    val networkHelper: Lazy<NetworkHelper>,
    val addMangaTracks: Lazy<AddMangaTracks>,
    val upsertMangaTrack: Lazy<UpsertMangaTrack>,
    val addAnimeTracks: Lazy<AddAnimeTracks>,
    val upsertAnimeTrack: Lazy<UpsertAnimeTrack>,
)
