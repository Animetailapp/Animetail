package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeDownloadTest {

    @Test
    fun `download keeps its running job when its video changes`() {
        // The downloader keys its jobs by download and starts one for every active download not in
        // the map. Picking or switching the video must not make the download look like a new one,
        // or a second job downloads the same episode alongside the first.
        val download = AnimeDownload(mockk(), mockk(), mockk())
        val jobs = mutableMapOf<AnimeDownload, Job>(download to Job())

        download.video = Video(videoUrl = "https://example.com/a.m3u8", videoTitle = "Sub - a")
        assertTrue(download in jobs)

        download.video = Video(videoUrl = "https://example.com/b.m3u8", videoTitle = "Sub - b")
        assertTrue(download in jobs)
        assertEquals(1, jobs.size)
    }

    @Test
    fun `downloads are distinct queue entries`() {
        val source = mockk<AnimeHttpSource>()
        val anime = mockk<Anime>()
        val episode = mockk<Episode>()

        assertNotEquals(AnimeDownload(source, anime, episode), AnimeDownload(source, anime, episode))
    }
}
