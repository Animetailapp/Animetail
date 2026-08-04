package mihon.feature.upcoming.anime

import kotlinx.datetime.LocalDate
import tachiyomi.domain.entries.anime.model.Anime

sealed interface UpcomingAnimeUIModel {
    data class Header(val date: LocalDate, val animeCount: Int) : UpcomingAnimeUIModel
    data class Item(val anime: Anime) : UpcomingAnimeUIModel
}
