package eu.kanade.presentation.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols(Locale.ROOT),
)

fun formatChapterNumber(chapterNumber: Double): String {
    return formatter.format(chapterNumber)
}

fun formatEpisodeNumber(episodeNumber: Double): String {
    return formatter.format(episodeNumber)
}
