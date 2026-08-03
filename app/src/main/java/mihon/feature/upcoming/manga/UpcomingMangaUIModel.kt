package mihon.feature.upcoming.manga

import kotlinx.datetime.LocalDate
import tachiyomi.domain.entries.manga.model.Manga

sealed interface UpcomingMangaUIModel {
    data class Header(val date: LocalDate, val mangaCount: Int) : UpcomingMangaUIModel
    data class Item(val manga: Manga) : UpcomingMangaUIModel
}
