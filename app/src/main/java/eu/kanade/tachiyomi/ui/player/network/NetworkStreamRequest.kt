package eu.kanade.tachiyomi.ui.player.network

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.Serializable
import okhttp3.Headers

@Serializable
data class NetworkStreamRequest(
    val url: String,
    val title: String,
    val subtitleUrl: String? = null,
    val subtitleLabel: String? = null,
    val headers: List<NetworkHeader> = emptyList(),
) {

    @Serializable
    data class NetworkHeader(val name: String, val value: String)

    fun toHeaders(): Headers? {
        if (headers.isEmpty()) return null

        val builder = Headers.Builder()
        headers.forEach { header ->
            builder.add(header.name, header.value)
        }
        return builder.build()
    }

    fun toVideo(): Video {
        val subtitleTracks = subtitleUrl
            ?.let {
                listOf(
                    Track(
                        url = it,
                        lang = subtitleLabel?.takeIf { label -> label.isNotBlank() } ?: "",
                    ),
                )
            }
            ?: emptyList()

        return Video(
            videoUrl = url,
            videoTitle = title.ifBlank { url },
            headers = toHeaders(),
            preferred = true,
            subtitleTracks = subtitleTracks,
            initialized = true,
        )
    }

    companion object {
        const val EXTRA_KEY = "networkStreamRequest"
    }
}
