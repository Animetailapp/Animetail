package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A download whose FFmpeg session stops making progress must fail, so it is retried, instead of
 * holding the download queue forever. A slow download that keeps making progress must not.
 */
class ProgressTimeoutTest {

    private val timeout = 3.minutes

    @Test
    fun `a download that keeps making progress runs past the timeout`() = runTest {
        val result = withProgressTimeout(timeout) { onProgress ->
            repeat(10) {
                delay(1.minutes)
                onProgress()
            }
            "done"
        }

        assertEquals("done", result)
        assertEquals(10.minutes.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `a download that never makes progress fails after the timeout and is cancelled`() = runTest {
        var cancelled = false

        assertThrows<DownloadStalledException> {
            withProgressTimeout(timeout) {
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        }

        assertTrue(cancelled)
        assertEquals(timeout.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `a download that stalls fails one timeout after its last progress`() = runTest {
        assertThrows<DownloadStalledException> {
            withProgressTimeout(timeout) { onProgress ->
                repeat(3) {
                    delay(2.minutes)
                    onProgress()
                }
                awaitCancellation()
            }
        }

        assertEquals((6.minutes + timeout).inWholeMilliseconds, currentTime)
    }

    @Test
    fun `progress reported many times at once counts once`() = runTest {
        assertThrows<DownloadStalledException> {
            withProgressTimeout(timeout) { onProgress ->
                delay(1.minutes)
                repeat(1000) { onProgress() }
                awaitCancellation()
            }
        }

        assertEquals((1.minutes + timeout).inWholeMilliseconds, currentTime)
    }

    @Test
    fun `progress can be reported from another thread`() = runTest {
        val result = withProgressTimeout(timeout) { onProgress ->
            repeat(5) {
                delay(2.minutes)
                Thread { onProgress() }.apply { start() }.join()
            }
            "done"
        }

        assertEquals("done", result)
    }

    @Test
    fun `the download's own failure is passed through`() = runTest {
        val error = assertThrows<IOException> {
            withProgressTimeout(timeout) {
                delay(10.seconds)
                throw IOException("Server returned 403 Forbidden")
            }
        }

        assertEquals("Server returned 403 Forbidden", error.message)
    }
}
