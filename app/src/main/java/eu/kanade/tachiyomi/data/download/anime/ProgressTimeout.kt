package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration

class DownloadStalledException(timeout: Duration) : IOException("No download progress for $timeout")

/**
 * Runs [block] and fails it with [DownloadStalledException] if it doesn't report progress, by
 * calling the function it receives, for [timeout]. That function can be called from any thread.
 */
internal suspend fun <T> withProgressTimeout(
    timeout: Duration,
    block: suspend (onProgress: () -> Unit) -> T,
): T = coroutineScope {
    val progress = Channel<Unit>(Channel.CONFLATED)
    val watchdog = launch {
        while (true) {
            withTimeoutOrNull(timeout) { progress.receive() } ?: throw DownloadStalledException(timeout)
        }
    }
    try {
        block { progress.trySend(Unit) }
    } finally {
        watchdog.cancel()
    }
}
