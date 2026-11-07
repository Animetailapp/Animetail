package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.Comparator
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.MangaField
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import kotlin.math.abs
import kotlin.time.Instant

fun QueryNode.matches(item: MangaLibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
    }
}

fun QueryNode.matches(item: AnimeLibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
    }
}

private fun compareDates(comparator: Comparator, timestamp: Long, value: String): Boolean? {
    val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return comparator.apply(date, inputDate)
}

private fun GeneralQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    // Use when so each added field has to be handled explicitly
    val match = MangaField.entries.any { field ->
        if (field.fieldOnly) return@any false

        when (field) {
            MangaField.TITLE -> manga.title.contains(value, ignoreCase = true)

            MangaField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false

            MangaField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false

            MangaField.DESCRIPTION -> manga.description?.contains(value, ignoreCase = true) ?: false

            MangaField.GENRE -> manga.genre?.any { g -> g.contains(value, ignoreCase = true) } ?: false

            MangaField.SOURCE -> {
                val fullSourceName = if (item.sourceLanguage.isNotBlank()) {
                    "${item.sourceName} (${item.sourceLanguage})"
                } else {
                    item.sourceName
                }
                item.sourceName.contains(value, ignoreCase = true) ||
                    fullSourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }

            MangaField.NOTES -> manga.notes.contains(value, ignoreCase = true)

            // field-only queries; unreachable; added here to make `when` exhaustive
            MangaField.LANGUAGE, MangaField.SOURCE_ID -> error("How did we get here?")
        }
    }
    return if (negated) !match else match
}

private fun GeneralQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    // Use when so each added field has to be handled explicitly
    val match = MangaField.entries.any { field ->
        if (field.fieldOnly) return@any false

        when (field) {
            MangaField.TITLE -> anime.title.contains(value, ignoreCase = true)

            MangaField.AUTHOR -> anime.author?.contains(value, ignoreCase = true) ?: false

            MangaField.ARTIST -> anime.artist?.contains(value, ignoreCase = true) ?: false

            MangaField.DESCRIPTION -> anime.description?.contains(value, ignoreCase = true) ?: false

            MangaField.GENRE -> anime.genre?.any { g -> g.contains(value, ignoreCase = true) } ?: false

            MangaField.SOURCE -> {
                val fullSourceName = if (item.sourceLanguage.isNotBlank()) {
                    "${item.sourceName} (${item.sourceLanguage})"
                } else {
                    item.sourceName
                }
                item.sourceName.contains(value, ignoreCase = true) ||
                    fullSourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
            }

            MangaField.NOTES -> anime.notes.contains(value, ignoreCase = true)

            // field-only queries; unreachable; added here to make `when` exhaustive
            MangaField.LANGUAGE, MangaField.SOURCE_ID -> error("How did we get here?")
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = when (field) {
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { g -> g.contains(value, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                val fullSourceName = if (item.sourceLanguage.isNotBlank()) {
                    "${item.sourceName} (${item.sourceLanguage})"
                } else {
                    item.sourceName
                }
                item.sourceName.contains(value, ignoreCase = true) ||
                    fullSourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
        }

        MangaField.SOURCE_ID -> {
            value.toLongOrNull()?.let { it == manga.source } ?: false
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> manga.title

                MangaField.AUTHOR -> manga.author

                MangaField.ARTIST -> manga.artist

                MangaField.DESCRIPTION -> manga.description

                MangaField.NOTES -> manga.notes

                MangaField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID -> error("How did we get here?")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(value, ignoreCase = true) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = when (field) {
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                anime.genre.isNullOrEmpty()
            } else {
                anime.genre?.any { g -> g.contains(value, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                val fullSourceName = if (item.sourceLanguage.isNotBlank()) {
                    "${item.sourceName} (${item.sourceLanguage})"
                } else {
                    item.sourceName
                }
                item.sourceName.contains(value, ignoreCase = true) ||
                    fullSourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
            }
        }

        MangaField.SOURCE_ID -> {
            value.toLongOrNull()?.let { it == anime.source } ?: false
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> anime.title

                MangaField.AUTHOR -> anime.author

                MangaField.ARTIST -> anime.artist

                MangaField.DESCRIPTION -> anime.description

                MangaField.NOTES -> anime.notes

                MangaField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID -> error("How did we get here?")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(value, ignoreCase = true) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(manga.id, it) }

        ComparisonField.DATE_ADDED -> manga.favoriteAt?.let { compareDates(queryComparator, it, value) }

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(manga.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> compareDates(queryComparator, manga.nextUpdate, value)

        ComparisonField.UNREAD -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unreadCount, it)
            }
        }

        ComparisonField.READ -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.readCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.totalChapters, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(anime.id, it) }

        ComparisonField.DATE_ADDED -> anime.dateAdded.takeIf {
            it > 0L
        }?.let { compareDates(queryComparator, it, value) }

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(anime.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> compareDates(queryComparator, anime.nextUpdate, value)

        ComparisonField.UNREAD -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unseenCount, it)
            }
        }

        ComparisonField.READ -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryAnime.seenCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryAnime.totalCount, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}
