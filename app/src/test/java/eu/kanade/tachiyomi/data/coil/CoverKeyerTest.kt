package eu.kanade.tachiyomi.data.coil

import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import java.io.File

class CoverKeyerTest {
    private val options = mockk<Options>()
    private val customCover = mockk<File> { every { exists() } returns true }

    @Test
    fun mangaUsesCustomCoverOnlyInLibrary() {
        val cache = mockk<MangaCoverCache> { every { getCustomCoverFile(1L) } returns customCover }
        val keyer = MangaCoverKeyer(cache)
        val cover = MangaCover(1L, 2L, false, "https://example.com/cover", 3L)

        assertEquals("manga;https://example.com/cover;3", keyer.key(cover, options))
        verify(exactly = 0) { cache.getCustomCoverFile(any()) }
        assertEquals("manga;1;3", keyer.key(cover.copy(isMangaFavorite = true), options))
    }

    @Test
    fun animeUsesCustomCoverOnlyInLibrary() {
        val cache = mockk<AnimeCoverCache> { every { getCustomCoverFile(1L) } returns customCover }
        val keyer = AnimeCoverKeyer(cache)
        val cover = AnimeCover(1L, 2L, false, "https://example.com/cover", 3L)

        assertEquals("anime;https://example.com/cover;3", keyer.key(cover, options))
        verify(exactly = 0) { cache.getCustomCoverFile(any()) }
        assertEquals("anime;1;3", keyer.key(cover.copy(isAnimeFavorite = true), options))
    }
}
