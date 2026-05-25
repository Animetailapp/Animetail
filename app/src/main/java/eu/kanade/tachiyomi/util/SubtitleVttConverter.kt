package eu.kanade.tachiyomi.util

object SubtitleVttConverter {

    fun convertToWebVtt(sourceName: String, content: String): String? {
        return when (sourceName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "srt" -> convertSrt(content)
            "ass", "ssa" -> convertAss(content)
            else -> null
        }
    }

    private fun convertSrt(content: String): String {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val output = StringBuilder("WEBVTT\n\n")
        var index = 0

        while (index < lines.size) {
            var line = lines[index].trimEnd()
            if (line.isBlank()) {
                index++
                continue
            }

            if (line.all(Char::isDigit) && index + 1 < lines.size && lines[index + 1].contains("-->")) {
                index++
                line = lines[index].trimEnd()
            }

            if (!line.contains("-->")) {
                index++
                continue
            }

            output.append(normalizeSrtTimestampLine(line)).append('\n')
            index++

            while (index < lines.size && lines[index].isNotBlank()) {
                output.append(lines[index].trimEnd()).append('\n')
                index++
            }

            output.append('\n')
        }

        return output.toString().trimEnd() + "\n"
    }

    private fun normalizeSrtTimestampLine(line: String): String {
        return line.split("-->")
            .map { normalizeSrtTimestamp(it.trim()) }
            .joinToString(" --> ")
    }

    private fun normalizeSrtTimestamp(timestamp: String): String {
        val match = SRT_TIMESTAMP_REGEX.matchEntire(timestamp)
            ?: return timestamp.replace(',', '.')
        val millis = match.groupValues[4].padEnd(3, '0').take(3)
        return "${match.groupValues[1]}:${match.groupValues[2]}:${match.groupValues[3]}.$millis"
    }

    private fun convertAss(content: String): String? {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val output = StringBuilder("WEBVTT\n\n")
        var inEvents = false
        var format: List<String>? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[", ignoreCase = true)) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents || trimmed.isBlank()) continue

            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                format = trimmed.substringAfter(':').split(',').map { it.trim() }
                continue
            }
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) continue

            val currentFormat = format ?: continue
            val fields = trimmed.substringAfter(':').split(',', limit = currentFormat.size)
            if (fields.size != currentFormat.size) continue

            val startIndex = currentFormat.indexOfFirst { it.equals("Start", ignoreCase = true) }
            val endIndex = currentFormat.indexOfFirst { it.equals("End", ignoreCase = true) }
            val textIndex = currentFormat.indexOfFirst { it.equals("Text", ignoreCase = true) }
            if (startIndex == -1 || endIndex == -1 || textIndex == -1) continue

            val start = normalizeAssTimestamp(fields[startIndex].trim()) ?: continue
            val end = normalizeAssTimestamp(fields[endIndex].trim()) ?: continue
            val text = cleanAssText(fields[textIndex].trim())
            if (text.isBlank()) continue

            output.append(start)
                .append(" --> ")
                .append(end)
                .append('\n')
                .append(text)
                .append("\n\n")
        }

        return output.takeIf { it.length > "WEBVTT\n\n".length }?.toString()?.trimEnd()?.plus("\n")
    }

    private fun normalizeAssTimestamp(timestamp: String): String? {
        val match = ASS_TIMESTAMP_REGEX.matchEntire(timestamp) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        val seconds = match.groupValues[3].toIntOrNull() ?: return null
        val centiseconds = match.groupValues[4].toIntOrNull() ?: return null
        val millis = centiseconds * 10
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    private fun cleanAssText(text: String): String {
        return text
            .replace(ASS_STYLE_REGEX, "")
            .replace("\\\\N", "\n")
            .replace("\\\\n", "\n")
            .replace("\\\\h", " ")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .trim()
    }

    private val SRT_TIMESTAMP_REGEX = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    private val ASS_TIMESTAMP_REGEX = Regex("(\\d+):(\\d{2}):(\\d{2})[.](\\d{2})")
    private val ASS_STYLE_REGEX = Regex("\\{[^}]*}")
}
