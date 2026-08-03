package eu.kanade.presentation.history.anime

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import java.util.Date
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class AnimeHistoryViewModelStateProvider : PreviewParameterProvider<AnimeHistoryViewModel.State> {

    private val multiPage = AnimeHistoryViewModel.State(
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

    private val shortRecent = AnimeHistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val shortFuture = AnimeHistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val empty = AnimeHistoryViewModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = AnimeHistoryViewModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = AnimeHistoryViewModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<AnimeHistoryViewModel.State> = sequenceOf(
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
            AnimeHistoryUiModel.Header(
                Clock.System.now().plus(1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            AnimeHistoryUiModel.Header(
                instantBuilder(Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(title = "Example Title $count") })
                count += 1
            }
        }

        fun randItem(historyBuilder: (AnimeHistoryWithRelations) -> AnimeHistoryWithRelations = { it }) =
            AnimeHistoryUiModel.Item(
                historyBuilder(
                    AnimeHistoryWithRelations(
                        id = Random.nextLong(),
                        episodeId = Random.nextLong(),
                        animeId = Random.nextLong(),
                        title = "Test Title",
                        episodeNumber = Random.nextDouble(),
                        seenAt = Date.from(Clock.System.now().toJavaInstant()),
                        coverData = AnimeCover(
                            animeId = Random.nextLong(),
                            sourceId = Random.nextLong(),
                            isAnimeFavorite = Random.nextBoolean(),
                            url = "https://example.com/cover.png",
                            lastModified = Random.nextLong(),
                        ),
                    ),
                ),
            )
    }
}
