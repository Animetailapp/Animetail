package tachiyomi.domain.entries.manga.interactor

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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaRemoteUpdate
import tachiyomi.domain.entries.manga.repository.CustomMangaRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository

/**
 * Browsing a source resolves each listed entry against the stored one. An entry outside the library
 * must pick up the cover the source lists now, or it keeps the cover from its first visit forever.
 */
class NetworkToLocalMangaTest {

    private val repository = mockk<MangaRepository>(relaxed = true)
    private val networkToLocalManga = NetworkToLocalManga(repository)

    private val stored = Manga.create().copy(id = 1, source = 10, url = "/a", ogTitle = "Old title")

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
        val local = stored.copy(favoriteAt = if (favorite) 1L else null, thumbnailUrl = storedCover)
        coEvery { repository.getMangaByUrlAndSourceId(local.url, local.source) } returns local
        val listed = Manga.create().copy(source = 10, url = "/a", ogTitle = "New title", thumbnailUrl = listedCover)

        val result = networkToLocalManga.await(listed)

        assertEquals(expectedCover, result.thumbnailUrl)
        assertEquals(if (favorite) "Old title" else "New title", result.ogTitle)
        coVerify(exactly = if (persisted) 1 else 0) {
            repository.updateRemote(match<MangaRemoteUpdate> { it.id == 1L && it.thumbnailUrl == listedCover })
        }
        coVerify(exactly = 0) { repository.insertManga(any()) }
        if (persisted) {
            // Only the cover changes; the stored title stays until the entry is added to the library.
            coVerify { repository.updateRemote(match<MangaRemoteUpdate> { it.title == "Old title" }) }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomInfo() {
            // Manga looks up the user's custom info for library entries.
            val customInfo = mockk<CustomMangaRepository>()
            every { customInfo.get(any()) } returns null
            Manga.getCustomMangaInfo = GetCustomMangaInfo(customInfo)
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
