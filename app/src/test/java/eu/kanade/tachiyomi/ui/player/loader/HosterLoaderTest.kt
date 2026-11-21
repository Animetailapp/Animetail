package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HosterLoaderTest {

    @Test
    fun `waits while an earlier hoster is still loading`() {
        val states = listOf(
            HosterState.Loading("UPNShare"),
            ready("Voe", video("voe-720")),
        )

        assertNull(HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `starts with the first ready hoster once the earlier ones settled`() {
        val states = listOf(
            HosterState.Error("HLS"),
            ready("UPNShare"),
            ready("Voe", video("voe-720")),
            HosterState.Loading("Byse"),
        )

        assertEquals(2 to 0, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `a preferred video wins even if earlier hosters are still loading`() {
        val states = listOf(
            HosterState.Loading("UPNShare"),
            ready("Voe", video("voe-720"), video("voe-1080", preferred = true)),
        )

        assertEquals(1 to 1, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `skips videos that already failed`() {
        val states = listOf(
            ready("Voe", video("voe-1080"), video("voe-720"), states = listOf(Video.State.ERROR, Video.State.QUEUE)),
        )

        assertEquals(0 to 1, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `reports no video once every hoster settled without a playable one`() {
        val states = listOf(
            HosterState.Error("HLS"),
            HosterState.Idle("Mega"),
            ready("Voe", video("voe-720"), states = listOf(Video.State.ERROR)),
        )

        assertEquals(-1 to -1, HosterLoader.selectNextInOrder(states))
    }

    private fun video(url: String, preferred: Boolean = false) =
        Video(videoUrl = "https://cdn.example/$url", videoTitle = url, preferred = preferred)

    private fun ready(
        name: String,
        vararg videos: Video,
        states: List<Video.State> = List(videos.size) { Video.State.QUEUE },
    ) = HosterState.Ready(name, videos.toList(), states)
}
