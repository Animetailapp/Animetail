package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueViewModel
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadQueueViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueSectionTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reorderKeepsNewItemsAndDoesNotRestoreRemovedItems() {
        val requested = listOf(3, 1, 2)
        val current = listOf(1, 3, 4)

        assertEquals(listOf(3, 1, 4), reconcileQueueOrder(requested, current) { it })
    }

    @Test
    fun reorderUsesLatestEntriesAndRemovesDuplicateRequests() {
        val requested = listOf(1 to "old", 1 to "old", 2 to "old")
        val current = listOf(2 to "current", 1 to "current")

        assertEquals(
            listOf(1 to "current", 2 to "current"),
            reconcileQueueOrder(requested, current) { it.first },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mangaPagesNotifyObserversWhenLoaded() = runTest {
        val download = MangaDownload(mockk(), mockk(), mockk())
        val observed = mutableListOf<List<Page>?>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            download.pagesFlow.collect { observed.add(it) }
        }
        val pages = listOf(Page(0))

        download.pages = pages

        assertEquals(listOf(null, pages), observed)
        collector.cancel()
    }

    @Test
    fun animeProgressRemainsObservable() {
        val download = AnimeDownload(mockk(), mockk(), mockk())
        download.progress = 42

        assertEquals(42, download.progressFlow.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mangaQueueMovesAndCancelsSeries() = runTest {
        val source = mockk<eu.kanade.tachiyomi.source.online.HttpSource> {
            every { id } returns 1L
            every { name } returns "Source"
        }
        val first = MangaDownload(source, mockk { every { id } returns 10L }, mockk { every { id } returns 1L })
        val second = MangaDownload(source, mockk { every { id } returns 20L }, mockk { every { id } returns 2L })
        val third = MangaDownload(source, mockk { every { id } returns 10L }, mockk { every { id } returns 3L })
        val queue = MutableStateFlow(listOf(first, second, third))
        val manager = mockk<MangaDownloadManager>(relaxed = true) {
            every { queueState } returns queue
            every { isDownloaderRunning } returns MutableStateFlow(false)
        }
        val viewModel = MangaDownloadQueueViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.composeState.collect {} }
        advanceUntilIdle()

        viewModel.move(third, toTop = true)
        viewModel.cancelSeries(first)

        verify { manager.reorderQueue(listOf(third, first, second)) }
        verify { manager.cancelQueuedDownloads(listOf(first, third)) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun animeQueueMovesAndCancelsSeries() = runTest {
        val source = mockk<eu.kanade.tachiyomi.animesource.online.AnimeHttpSource> {
            every { id } returns 1L
            every { name } returns "Source"
        }
        val first = AnimeDownload(source, mockk { every { id } returns 10L }, mockk { every { id } returns 1L })
        val second = AnimeDownload(source, mockk { every { id } returns 20L }, mockk { every { id } returns 2L })
        val third = AnimeDownload(source, mockk { every { id } returns 10L }, mockk { every { id } returns 3L })
        val queue = MutableStateFlow(listOf(first, second, third))
        val manager = mockk<AnimeDownloadManager>(relaxed = true) {
            every { queueState } returns queue
            every { isDownloaderRunning } returns MutableStateFlow(false)
        }
        val viewModel = AnimeDownloadQueueViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.composeState.collect {} }
        advanceUntilIdle()

        viewModel.move(third, toTop = true)
        viewModel.cancelSeries(first)

        verify { manager.reorderQueue(listOf(third, first, second)) }
        verify { manager.cancelQueuedDownloads(listOf(first, third)) }
    }
}
