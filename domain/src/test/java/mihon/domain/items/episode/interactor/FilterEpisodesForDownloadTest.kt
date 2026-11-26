package mihon.domain.items.episode.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

class FilterEpisodesForDownloadTest {

    data class Case(
        val description: String,
        val downloadNewEpisodes: Boolean,
        val excludedCategory: Boolean = false,
        val favorite: Boolean = true,
        val autoDownload: Long = Anime.AUTO_DOWNLOAD_DEFAULT,
        val showAutoDownload: Long? = null,
        val expectedDownload: Boolean,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `the show's own setting decides over the download settings`(case: Case) = runTest {
        val categoryId = 5L
        val downloadPreferences = mockk<DownloadPreferences> {
            every { downloadNewEpisodes.get() } returns case.downloadNewEpisodes
            every { downloadNewUnseenEpisodesOnly.get() } returns false
            every { downloadNewEpisodeCategories.get() } returns emptySet()
            every { downloadNewEpisodeCategoriesExclude.get() } returns
                if (case.excludedCategory) setOf(categoryId.toString()) else emptySet()
        }
        val getCategories = mockk<GetAnimeCategories> {
            coEvery { await(any()) } returns listOf(mockk { every { id } returns categoryId })
        }
        val show = Anime.create().copy(id = SHOW_ID, favorite = true, autoDownload = case.showAutoDownload ?: 0L)
        val getAnime = mockk<GetAnime> { coEvery { await(SHOW_ID) } returns show }
        val anime = Anime.create().copy(
            id = 1,
            favorite = case.favorite,
            autoDownload = case.autoDownload,
            parentId = SHOW_ID.takeIf { case.showAutoDownload != null },
        )
        val filter =
            FilterEpisodesForDownload(mockk<GetEpisodesByAnimeId>(), downloadPreferences, getCategories, getAnime)
        val newEpisodes = listOf(Episode.create().copy(id = 10, animeId = 1))

        filter.await(anime, newEpisodes) shouldBe if (case.expectedDownload) newEpisodes else emptyList()
    }

    companion object {
        private const val SHOW_ID = 100L

        @JvmStatic
        fun cases() = listOf(
            Case("Default follows Download new episodes when on", downloadNewEpisodes = true, expectedDownload = true),
            Case(
                "Default follows Download new episodes when off",
                downloadNewEpisodes = false,
                expectedDownload = false,
            ),
            Case(
                "Default follows the excluded categories",
                downloadNewEpisodes = true,
                excludedCategory = true,
                expectedDownload = false,
            ),
            Case(
                "Always downloads with Download new episodes off",
                downloadNewEpisodes = false,
                autoDownload = Anime.AUTO_DOWNLOAD_ALWAYS,
                expectedDownload = true,
            ),
            Case(
                "Always downloads from an excluded category",
                downloadNewEpisodes = true,
                excludedCategory = true,
                autoDownload = Anime.AUTO_DOWNLOAD_ALWAYS,
                expectedDownload = true,
            ),
            Case(
                "Never skips the show with Download new episodes on",
                downloadNewEpisodes = true,
                autoDownload = Anime.AUTO_DOWNLOAD_NEVER,
                expectedDownload = false,
            ),
            Case(
                "Always still needs the show to be in the library",
                downloadNewEpisodes = true,
                favorite = false,
                autoDownload = Anime.AUTO_DOWNLOAD_ALWAYS,
                expectedDownload = false,
            ),
            Case(
                "A season on Default follows its show",
                downloadNewEpisodes = false,
                showAutoDownload = Anime.AUTO_DOWNLOAD_ALWAYS,
                expectedDownload = true,
            ),
            Case(
                "A season's own setting wins over its show's",
                downloadNewEpisodes = true,
                autoDownload = Anime.AUTO_DOWNLOAD_NEVER,
                showAutoDownload = Anime.AUTO_DOWNLOAD_ALWAYS,
                expectedDownload = false,
            ),
        )
    }
}
