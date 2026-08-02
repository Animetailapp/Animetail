package eu.kanade.presentation.history.manga

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import java.util.Date
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class MangaHistoryScreenModelStateProvider : PreviewParameterProvider<MangaHistoryViewModel.State> {

    private val multiPage = MangaHistoryViewModel.State(
        searchQuery = null,
        list =
        listOf(HistoryUiModelExamples.headerToday)
            .asSequence()
            .plus(HistoryUiModelExamples.items().take(3))
            .plus(HistoryUiModelExamples.header { it.minus(1.days) })
            .plus(HistoryUiModelExamples.items().take(1))
            .plus(HistoryUiModelExamples.header { it.minus(2.days) })
            .plus(HistoryUiModelExamples.items().take(7))
            .toList(),
        dialog = null,
    )

    private val shortRecent = MangaHistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val shortFuture = MangaHistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val empty = MangaHistoryViewModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = MangaHistoryViewModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = MangaHistoryViewModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<MangaHistoryViewModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    private object HistoryUiModelExamples {
        val headerToday = header()
        val headerTomorrow =
            MangaHistoryUiModel.Header(Clock.System.now().plus(1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date)

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            MangaHistoryUiModel.Header(
                instantBuilder(Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(title = "Example Title $count") })
                count += 1
            }
        }

        fun randItem(historyBuilder: (MangaHistoryWithRelations) -> MangaHistoryWithRelations = { it }) =
            MangaHistoryUiModel.Item(
                historyBuilder(
                    MangaHistoryWithRelations(
                        id = Random.nextLong(),
                        chapterId = Random.nextLong(),
                        mangaId = Random.nextLong(),
                        title = "Test Title",
                        chapterNumber = Random.nextDouble(),
                        lastPageRead = Random.nextLong(0, 10),
                        readAt = Date.from(Clock.System.now().toJavaInstant()),
                        readDuration = Random.nextLong(),
                        coverData = MangaCover(
                            mangaId = Random.nextLong(),
                            sourceId = Random.nextLong(),
                            isMangaFavorite = Random.nextBoolean(),
                            url = "https://example.com/cover.png",
                            lastModified = Random.nextLong(),
                        ),
                    ),
                ),
            )
    }
}
