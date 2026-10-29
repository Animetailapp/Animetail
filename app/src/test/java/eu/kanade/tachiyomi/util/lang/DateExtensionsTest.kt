package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateExtensionsTest {
    @Test
    fun timestampUsesFormatterAndSystemTimeZone() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val date = Date.from(java.time.Instant.parse("2026-10-27T18:05:00Z"))
            val twentyFourHour = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            val twelveHour = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

            assertEquals("14:05", date.toTimestampString(twentyFourHour))
            assertEquals("2:05 PM", date.toTimestampString(twelveHour))
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}
