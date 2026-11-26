package eu.kanade.tachiyomi.data.download.anime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the downloader's ffprobe run finds out about a video source: its duration, for progress, and
 * which variant of an HLS master playlist to download.
 *
 * ffmpeg shows each variant of a master playlist as a program with its own video stream (and audio
 * stream, when the variant has its audio muxed in). Mapping all video and audio downloads every
 * variant into the file, so the download maps only the streams of [variantProgramId].
 */
internal data class SourceProbe(
    val durationSeconds: Double?,
    val variantProgramId: Int?,
) {
    companion object {
        /** ffprobe arguments that print what [parse] reads. */
        const val FFPROBE_ARGS = "-v quiet -show_entries " +
            "format=duration:program=program_id:program_stream=index:" +
            "stream=index,codec_type,width,height:stream_tags=variant_bitrate " +
            "-of json"

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses ffprobe output written with [FFPROBE_ARGS]. */
        fun parse(output: String): SourceProbe {
            val result = runCatching { json.decodeFromString<Output>(output) }.getOrNull()
                ?: return SourceProbe(durationSeconds = null, variantProgramId = null)
            return SourceProbe(
                durationSeconds = result.format?.duration?.toDoubleOrNull()?.takeIf { it > 0 },
                variantProgramId = result.bestVariantProgramId(),
            )
        }

        /**
         * The program whose video is the largest, then has the highest bitrate. Null unless there are
         * several programs with video, so a source that isn't a master playlist is mapped as before.
         */
        private fun Output.bestVariantProgramId(): Int? {
            val videoByIndex = streams
                .filter { it.codecType == "video" }
                .associateBy { it.index }
            val programVideos = programs.mapNotNull { program ->
                program.streams.mapNotNull { videoByIndex[it.index] }
                    .maxWithOrNull(variantOrder)
                    ?.let { program.programId to it }
            }
            if (programVideos.size < 2) return null
            return programVideos.maxWith(compareBy(variantOrder) { it.second }).first
        }

        private val variantOrder = compareBy<Stream>(
            { it.width.toLong() * it.height },
            { it.tags["variant_bitrate"]?.toLongOrNull() ?: 0 },
        )
    }

    @Serializable
    private data class Output(
        val programs: List<Program> = emptyList(),
        val streams: List<Stream> = emptyList(),
        val format: Format? = null,
    )

    @Serializable
    private data class Program(
        @SerialName("program_id") val programId: Int,
        val streams: List<StreamIndex> = emptyList(),
    )

    @Serializable
    private data class StreamIndex(val index: Int)

    @Serializable
    private data class Stream(
        val index: Int,
        @SerialName("codec_type") val codecType: String? = null,
        val width: Int = 0,
        val height: Int = 0,
        val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Format(val duration: String? = null)
}
