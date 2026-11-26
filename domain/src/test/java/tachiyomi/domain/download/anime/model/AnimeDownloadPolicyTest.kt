package tachiyomi.domain.download.anime.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AnimeDownloadPolicyTest {

    data class Case(
        val description: String,
        val global: GlobalDownloadPolicy,
        val override: AnimeDownloadPolicyOverride?,
        val categoryIds: List<Long>,
        val expected: Expected,
    ) {
        override fun toString() = description
    }

    data class Expected(
        val deleteAfterSeen: Boolean,
        val keepSeen: Int,
        val playerSlots: Int,
        val deleteOnMarkedSeen: Boolean,
        val keepUnseen: Int,
        val protected: Boolean,
    )

    @ParameterizedTest
    @MethodSource("cases")
    fun `show override sits on top of the existing global delete settings`(case: Case) {
        val policy = AnimeDownloadPolicy.resolve(case.global, case.override, case.categoryIds)

        assertEquals(
            case.expected,
            Expected(
                policy.deleteAfterSeen,
                policy.keepSeen,
                policy.playerRemoveAfterSeenSlots,
                policy.deleteOnMarkedSeen,
                policy.keepUnseen,
                policy.protected,
            ),
            case.description,
        )
    }

    companion object {
        private val GLOBAL_OFF = GlobalDownloadPolicy(
            removeAfterSeenSlots = -1,
            removeAfterMarkedAsSeen = false,
            keepUnseen = 0,
            deleteBookmarked = false,
            downloadFillers = false,
            excludedCategoryIds = emptySet(),
        )

        private fun override(deleteAfterSeen: Boolean? = null, keepUnseen: Int? = null) =
            AnimeDownloadPolicyOverride(animeId = 1, deleteAfterSeen, keepUnseen)

        @JvmStatic
        fun cases() = listOf(
            Case(
                "No override, everything off globally: behaves exactly as before",
                GLOBAL_OFF,
                null,
                listOf(2),
                Expected(false, 0, -1, false, 0, false),
            ),
            Case(
                "Global 'delete after watching: second to last' keeps one watched episode",
                GLOBAL_OFF.copy(removeAfterSeenSlots = 1),
                null,
                listOf(2),
                Expected(true, 1, 1, false, 0, false),
            ),
            Case(
                "Global 'after marked as seen' alone also means the show deletes watched episodes",
                GLOBAL_OFF.copy(removeAfterMarkedAsSeen = true),
                null,
                listOf(2),
                Expected(true, 0, -1, true, 0, false),
            ),
            Case(
                "Show on while global is off: the player deletes the episode just watched",
                GLOBAL_OFF,
                override(deleteAfterSeen = true),
                listOf(2),
                Expected(true, 0, 0, true, 0, false),
            ),
            Case(
                "Show on keeps the global slot when there is one",
                GLOBAL_OFF.copy(removeAfterSeenSlots = 2),
                override(deleteAfterSeen = true),
                listOf(2),
                Expected(true, 2, 2, true, 0, false),
            ),
            Case(
                "Show never: no deleting anywhere, and protected from the storage limit",
                GLOBAL_OFF.copy(removeAfterSeenSlots = 0, removeAfterMarkedAsSeen = true),
                override(deleteAfterSeen = false),
                listOf(2),
                Expected(false, 0, -1, false, 0, true),
            ),
            Case(
                "Excluded category wins over the global setting, like getEpisodesToDelete",
                GLOBAL_OFF.copy(removeAfterSeenSlots = 0, excludedCategoryIds = setOf(2)),
                null,
                listOf(2),
                Expected(false, 0, 0, false, 0, true),
            ),
            Case(
                "No category is the default category (0) for exclusion",
                GLOBAL_OFF.copy(removeAfterSeenSlots = 0, excludedCategoryIds = setOf(0)),
                null,
                emptyList(),
                Expected(false, 0, 0, false, 0, true),
            ),
            Case(
                "Keep unseen: show value wins, 0 turns the global one off for this show",
                GLOBAL_OFF.copy(keepUnseen = 3),
                override(keepUnseen = 0),
                listOf(2),
                Expected(false, 0, -1, false, 0, false),
            ),
            Case(
                "Keep unseen follows the global value without an override",
                GLOBAL_OFF.copy(keepUnseen = 3),
                override(),
                listOf(2),
                Expected(false, 0, -1, false, 3, false),
            ),
        )
    }
}
