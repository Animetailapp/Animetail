package eu.kanade.tachiyomi.ui.library.anime

import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource

private const val LOCAL_SOURCE_ID_ALIAS = "local"

class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val downloadCount: Int,
    val unseenCount: Long,
    val isLocal: Boolean,
    val badges: Badges,
) {
    /**
     * Checks if a query matches the anime
     *
     * @param constraint the query to check.
     * @return true if the anime matches the query, false otherwise.
     */
    fun matches(constraint: String, sourceManager: AnimeSourceManager): Boolean {
        val source = sourceManager.getOrStub(libraryAnime.anime.source)
        val sourceName by lazy { source.getNameForAnimeInfo() }
        if (constraint.startsWith("id:", true)) {
            val id = constraint.substringAfter("id:").toLongOrNull()
            return libraryAnime.id == id
        } else if (constraint.startsWith("src:", true)) {
            val querySource = constraint.substringAfter("src:")
            return if (querySource.equals(LOCAL_SOURCE_ID_ALIAS, ignoreCase = true)) {
                source.id == LocalAnimeSource.ID
            } else {
                source.id == querySource.toLongOrNull()
            }
        }
        return libraryAnime.anime.title.contains(constraint, true) ||
            (libraryAnime.anime.author?.contains(constraint, true) ?: false) ||
            (libraryAnime.anime.artist?.contains(constraint, true) ?: false) ||
            (libraryAnime.anime.description?.contains(constraint, true) ?: false) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (libraryAnime.anime.genre?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
    }

    /**
     * Checks a predicate on a negatable constraint. If the constraint starts with a minus character,
     * the minus is stripped and the result of the predicate is inverted.
     *
     * @param constraint the argument to the predicate. Inverts the predicate if it starts with '-'.
     * @param predicate the check to be run against the constraint.
     * @return !predicate(x) if constraint = "-x", otherwise predicate(constraint)
     */
    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }

    data class Badges(
        val downloadCount: Int,
        val unseenCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
