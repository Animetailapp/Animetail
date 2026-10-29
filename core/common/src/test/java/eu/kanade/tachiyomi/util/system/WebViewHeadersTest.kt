package eu.kanade.tachiyomi.util.system

import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebViewHeadersTest {
    @Test
    fun filtersUnsafeHeadersAndKeepsFirstAllowedValue() {
        val headers = Headers.Builder()
            .add("Origin", "https://example.com")
            .add("Host", "example.com")
            .add("Proxy-Authorization", "secret")
            .add("Connection", "Upgrade")
            .add("Referer", "https://example.com/")
            .add("X-Test", "first")
            .add("X-Test", "second")
            .build()

        assertEquals(
            mapOf("Referer" to "https://example.com/", "X-Test" to "first"),
            headers.toWebViewHeaders(),
        )
    }
}
