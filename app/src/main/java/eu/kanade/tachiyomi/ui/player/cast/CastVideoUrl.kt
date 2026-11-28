package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.cache.VideoCacheProxy
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * A cast receiver fetches the video itself, without the headers the host expects and without
 * access to the phone's loopback, so videos are sent through a proxy on the local network.
 */
object CastVideoUrl {

    /**
     * URL of [video] through the cast proxy, which adds [video]'s headers or [sourceHeaders], or
     * null when it isn't a web URL or the phone has no local network address.
     */
    fun proxied(context: Context, video: Video, sourceHeaders: Headers?): String? {
        if (!video.videoUrl.startsWith("http")) return null
        val lanAddress = localIpAddress() ?: return null
        return VideoCacheProxy.castProxy(context, lanAddress)
            .proxyUrl(video.videoUrl, requiredHeaders = video.headers ?: sourceHeaders)
    }

    fun localIpAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (e: Exception) {
        logcat(LogPriority.DEBUG, e) { "Couldn't get the local IP address" }
        null
    }
}
