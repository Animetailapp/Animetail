package eu.kanade.presentation.webview

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebViewHeadersHostTest {
    @Test
    fun sourceHeadersAreRestrictedToTheOriginalHost() {
        assertTrue(shouldForwardSourceHeaders("example.com", "EXAMPLE.COM"))
        assertFalse(shouldForwardSourceHeaders("example.com", "other.example.com"))
        assertFalse(shouldForwardSourceHeaders("example.com", null))
        assertFalse(shouldForwardSourceHeaders(null, null))
    }
}
