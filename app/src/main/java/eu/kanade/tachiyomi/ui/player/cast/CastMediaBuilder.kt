package eu.kanade.tachiyomi.ui.player.cast

import android.content.Intent
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.torrentServer.TorrentServerApi
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import eu.kanade.tachiyomi.util.LocalHttpServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class CastMediaBuilder(
    private val viewModel: PlayerViewModel,
    private val activity: PlayerActivity,
) {

    private val player by lazy { activity.player }
    private val prefserver: LocalHttpServerHolder by injectLazy()
    private val port = prefserver.port().get()

    suspend fun buildMediaInfo(video: Video): PreparedCastMedia = withContext(Dispatchers.IO) {
        val sourceVideoUrl = video.videoUrl
        logcat(LogPriority.DEBUG) { "Video URL: $sourceVideoUrl" }

        val preparedTracks = buildTracks(video)

        val castVideoUrl = when {
            sourceVideoUrl.startsWith(
                "magnet",
            ) ||
                sourceVideoUrl.endsWith(".torrent") -> torrentLinkHandler(sourceVideoUrl, video.videoTitle)

            else -> getProxyUrl(sourceVideoUrl, video.headers)
        }

        val contentType = when {
            sourceVideoUrl.contains(".m3u8") -> "application/x-mpegURL"
            sourceVideoUrl.contains(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }

        PreparedCastMedia(
            mediaInfo = MediaInfo.Builder(castVideoUrl)
                .setContentType(contentType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .addMetadata(video)
                .setMediaTracks(preparedTracks.mediaTracks)
                .setStreamDuration((viewModel.mpv.getPropertyInt("duration") ?: 0).toLong() * 1000)
                .build(),
            initialActiveTrackIds = preparedTracks.initialActiveTrackIds,
        )
    }

    private fun torrentLinkHandler(videoUrl: String, quality: String): String {
        var index = 0

        if (videoUrl.startsWith("content://")) {
            val videoInputStream = activity.applicationContext.contentResolver.openInputStream(Uri.parse(videoUrl))
                ?: throw IllegalStateException("Unable to open InputStream for content: $videoUrl")
            val torrent = TorrentServerApi.uploadTorrent(videoInputStream, quality, "", "", false)
            return TorrentServerUtils.getTorrentPlayLink(torrent, 0)
        }

        if (videoUrl.startsWith("magnet") && videoUrl.contains("index=")) {
            index = try {
                videoUrl.substringAfter("index=").toInt()
            } catch (e: NumberFormatException) {
                0
            }
        }

        val currentTorrent = TorrentServerApi.addTorrent(videoUrl, quality, "", "", false)
        logcat(LogPriority.DEBUG) { "Torrent URL: $videoUrl" }
        return TorrentServerUtils.getTorrentPlayLink(currentTorrent, index)
    }

    private fun MediaInfo.Builder.addMetadata(video: Video): MediaInfo.Builder {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, viewModel.currentAnime.value?.title ?: "")
            putString(MediaMetadata.KEY_SUBTITLE, viewModel.currentEpisode.value?.name ?: "")
            viewModel.currentAnime.value?.thumbnailUrl?.let { url ->
                addImage(WebImage(Uri.parse(url)))
            }
        }
        return setMetadata(metadata)
    }

    private fun buildTracks(video: Video): PreparedTracks {
        val subtitleTracks = video.subtitleTracks.mapIndexedNotNull { trackIndex, sub ->
            buildSubtitleTrack(sub.url, sub.lang, video.headers, trackIndex).also { subtitleTrack ->
                if (subtitleTrack == null) {
                    logcat(LogPriority.INFO) { "Skipping unsupported Cast subtitle track: ${sub.url}" }
                }
            }
        }

        val selectedAudio = getPreselectedAudioTrack(video)
        val audioTrack = selectedAudio?.let { audio ->
            buildAudioTrack(
                audio = audio,
                trackId = (subtitleTracks.size + 100).toLong(),
                headers = video.headers,
            )
        }

        return PreparedTracks(
            mediaTracks = subtitleTracks + listOfNotNull(audioTrack),
            initialActiveTrackIds = audioTrack?.let { longArrayOf(it.id) } ?: LongArray(0),
        )
    }

    private fun buildAudioTrack(audio: Track, trackId: Long, headers: Headers?): MediaTrack {
        val audioUrl = getProxyUrl(audio.url, headers)
        logcat(LogPriority.DEBUG) { "Cast preselected audio URL: $audioUrl" }
        return MediaTrack.Builder(trackId, MediaTrack.TYPE_AUDIO)
            .setContentId(audioUrl)
            .setName(audio.lang)
            .setLanguage(getValidLanguage(audio.lang))
            .setContentType(getAudioContentType(audio.url))
            .build()
    }

    private fun getPreselectedAudioTrack(video: Video): Track? {
        if (video.audioTracks.isEmpty()) return null

        val selectedAudioId = viewModel.selectedAudio.value
        val selectedTrack = viewModel.audioTracks.value.firstOrNull { it.id == selectedAudioId }
        if (selectedTrack == null || selectedTrack.id == -1) {
            logcat(LogPriority.DEBUG) { "No preselected external audio track for Cast" }
            return null
        }

        val normalizedName = selectedTrack.name.trim().lowercase(Locale.ROOT)
        val normalizedLanguage = selectedTrack.language?.trim()?.lowercase(Locale.ROOT).orEmpty()

        val matchingTracks = video.audioTracks.filter { audioTrack ->
            val normalizedAudioLanguage = audioTrack.lang.trim().lowercase(Locale.ROOT)
            normalizedAudioLanguage.isNotEmpty() && (
                normalizedAudioLanguage == normalizedName ||
                    normalizedAudioLanguage == normalizedLanguage
                )
        }

        if (matchingTracks.size == 1) {
            logcat(LogPriority.DEBUG) { "Using matched preselected Cast audio track: ${matchingTracks.first().lang}" }
            return matchingTracks.first()
        }

        val selectableExternalTracks = viewModel.audioTracks.value.filter { it.id != -1 }
        if (selectableExternalTracks.size == video.audioTracks.size) {
            val selectedExternalIndex = selectableExternalTracks.indexOfFirst { it.id == selectedAudioId }
            if (selectedExternalIndex in video.audioTracks.indices) {
                val resolvedTrack = video.audioTracks[selectedExternalIndex]
                logcat(LogPriority.DEBUG) { "Using positional preselected Cast audio track: ${resolvedTrack.lang}" }
                return resolvedTrack
            }
        }

        logcat(LogPriority.INFO) { "Unable to resolve a preselected external audio track for Cast; using source default audio" }
        return null
    }

    private fun buildSubtitleTrack(
        url: String,
        language: String,
        headers: Headers?,
        trackIndex: Int,
    ): MediaTrack? {
        val normalizedPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        val trackInfo = when {
            normalizedPath.endsWith(".vtt") || normalizedPath.endsWith(".webvtt") -> {
                SubtitleTrackInfo(getProxyUrl(url, headers), "text/vtt")
            }
            normalizedPath.endsWith(".ttml") || normalizedPath.endsWith(".dfxp") -> {
                SubtitleTrackInfo(getProxyUrl(url, headers), "application/ttml+xml")
            }
            normalizedPath.endsWith(".srt") || normalizedPath.endsWith(".ass") || normalizedPath.endsWith(".ssa") -> {
                SubtitleTrackInfo(getSubtitleProxyUrl(url, headers), "text/vtt")
            }
            else -> null
        } ?: return null

        logcat(LogPriority.DEBUG) { "Subtitle URL: ${trackInfo.url}" }
        return MediaTrack.Builder((trackIndex + 100).toLong(), MediaTrack.TYPE_TEXT)
            .setContentId(trackInfo.url)
            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            .setName(language)
            .setLanguage(getValidLanguage(language))
            .setContentType(trackInfo.contentType)
            .build()
    }

    private fun getAudioContentType(trackUrl: String): String {
        val normalizedPath = trackUrl.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            normalizedPath.endsWith(".m3u8") -> "application/x-mpegURL"
            normalizedPath.endsWith(".mpd") -> "application/dash+xml"
            normalizedPath.endsWith(".mp3") -> "audio/mpeg"
            normalizedPath.endsWith(".webm") -> "audio/webm"
            normalizedPath.endsWith(".aac") ||
                normalizedPath.endsWith(".m4a") ||
                normalizedPath.endsWith(".mp4") -> "audio/mp4"
            else -> "audio/mp4"
        }
    }

    private fun getSubtitleProxyUrl(trackUrl: String, headers: Headers?): String {
        return buildLocalServerUrl(
            path = "/subtitle",
            parameterName = if (trackUrl.startsWith("content://")) "uri" else "url",
            parameterValue = trackUrl,
            headers = headers,
            sourceName = trackUrl,
        )
    }

    private fun getProxyUrl(sourceUrl: String, headers: Headers?): String {
        return when {
            sourceUrl.startsWith("content://") -> getLocalFileUrl(sourceUrl)
            else -> buildLocalServerUrl(
                path = "/proxy",
                parameterName = "url",
                parameterValue = sourceUrl,
                headers = headers,
            )
        }
    }

    private fun buildLocalServerUrl(
        path: String,
        parameterName: String,
        parameterValue: String,
        headers: Headers? = null,
        sourceName: String? = null,
    ): String {
        ensureLocalServerStarted()
        return Uri.Builder()
            .scheme("http")
            .encodedAuthority("${getLocalIpAddress()}:$port")
            .path(path)
            .appendQueryParameter(parameterName, parameterValue)
            .apply {
                sourceName?.let { appendQueryParameter("name", it) }
                headers?.names()?.forEach { name ->
                    headers.values(name).forEach { value ->
                        appendQueryParameter("header", "$name:$value")
                    }
                }
            }
            .build()
            .toString()
    }

    private fun ensureLocalServerStarted() {
        val context = activity.applicationContext
        context.startService(Intent(context, LocalHttpServerService::class.java))
    }

    private fun getLocalFileUrl(contentUri: String): String {
        return buildLocalServerUrl(
            path = "/file",
            parameterName = "uri",
            parameterValue = contentUri,
        )
    }

    private fun getValidLanguage(lang: String): String {
        if (lang.isBlank()) return "en"
        val cleanedLang = lang.trim().lowercase()

        if (cleanedLang.length == 2) return cleanedLang

        val locales = java.util.Locale.getAvailableLocales()

        locales.firstOrNull {
            try {
                it.getISO3Language().lowercase() == cleanedLang
            } catch (e: Exception) {
                false
            }
        }?.let { return it.language }

        locales.firstOrNull {
            it.displayLanguage.lowercase() == cleanedLang ||
                it.getDisplayLanguage(java.util.Locale.US).lowercase() == cleanedLang
        }?.let { return it.language }

        locales.firstOrNull {
            val displayLang = it.displayLanguage.lowercase()
            val displayLangUs = it.getDisplayLanguage(java.util.Locale.US).lowercase()
            (displayLang.isNotEmpty() && cleanedLang.contains(displayLang)) ||
                (displayLangUs.isNotEmpty() && cleanedLang.contains(displayLangUs))
        }?.let { return it.language }

        return "en"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: Exception) {
            logcat(LogPriority.DEBUG) { "Error getting local IP address" }
        }
        return "127.0.0.1"
    }

    private data class SubtitleTrackInfo(
        val url: String,
        val contentType: String,
    )

    data class PreparedCastMedia(
        val mediaInfo: MediaInfo,
        val initialActiveTrackIds: LongArray,
    )

    private data class PreparedTracks(
        val mediaTracks: List<MediaTrack>,
        val initialActiveTrackIds: LongArray,
    )
}
