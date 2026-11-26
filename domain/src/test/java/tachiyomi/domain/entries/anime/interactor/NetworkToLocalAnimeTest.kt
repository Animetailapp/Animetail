package tachiyomi.domain.entries.anime.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.anime.repository.CustomAnimeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

/**
 * Browsing a source resolves each listed entry against the stored one. An entry outside the library
 * must pick up the cover the source lists now, or it keeps the cover from its first visit forever.
 */
class NetworkToLocalAnimeTest {

    private val repository = mockk<AnimeRepository>(relaxed = true)
    private val networkToLocalAnime = NetworkToLocalAnime(repository, mockk())

    private val stored = Anime.create().copy(id = 1, source = 10, url = "/a", ogTitle = "Old title")

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `uses the source cover only for entries outside the library`(
        @Suppress("UNUSED_PARAMETER") description: String,
        favorite: Boolean,
        storedCover: String?,
        listedCover: String?,
        expectedCover: String?,
        persisted: Boolean,
    ) = runTest {
        val local = stored.copy(favorite = favorite, thumbnailUrl = storedCover)
        coEvery { repository.getAnimeByUrlAndSourceId(local.url, local.source) } returns local
        val listed = Anime.create().copy(source = 10, url = "/a", ogTitle = "New title", thumbnailUrl = listedCover)

        val result = networkToLocalAnime.await(listed)

        assertEquals(expectedCover, result.thumbnailUrl)
        assertEquals(if (favorite) "Old title" else "New title", result.ogTitle)
        coVerify(exactly = if (persisted) 1 else 0) {
            repository.updateAnime(AnimeUpdate(id = 1, thumbnailUrl = listedCover))
        }
        coVerify(exactly = 0) { repository.insertAnime(any()) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomInfo() {
            // Anime looks up the user's custom info for library entries.
            val customInfo = mockk<CustomAnimeRepository>()
            every { customInfo.get(any()) } returns null
            Injekt.addSingleton(GetCustomAnimeInfo(customInfo))
        }

        @JvmStatic
        fun cases() = listOf(
            Arguments.of("changed cover outside the library", false, "old.jpg", "new.webp", "new.webp", true),
            Arguments.of("first cover outside the library", false, null, "new.webp", "new.webp", true),
            Arguments.of("same cover outside the library", false, "old.jpg", "old.jpg", "old.jpg", false),
            Arguments.of("listing without a cover", false, "old.jpg", null, "old.jpg", false),
            Arguments.of("listing with a blank cover", false, "old.jpg", " ", "old.jpg", false),
            Arguments.of("library entry keeps its cover", true, "old.jpg", "new.webp", "old.jpg", false),
        )
    }
}
