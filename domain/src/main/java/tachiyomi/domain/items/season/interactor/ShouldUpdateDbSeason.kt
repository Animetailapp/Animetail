package tachiyomi.domain.items.season.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.model.Anime

@Inject
class ShouldUpdateDbSeason {
    fun await(dbSeason: Anime, sourceSeason: Anime): Boolean {
        return dbSeason.title != sourceSeason.title ||
            dbSeason.seasonNumber != sourceSeason.seasonNumber ||
            dbSeason.seasonSourceOrder != sourceSeason.seasonSourceOrder
    }
}
