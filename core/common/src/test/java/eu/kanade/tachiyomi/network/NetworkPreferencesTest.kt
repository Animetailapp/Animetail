package eu.kanade.tachiyomi.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class NetworkPreferencesTest {
    @Test
    fun sensitiveHeadersAreRedactedByDefault() {
        val preferences = NetworkPreferences(InMemoryPreferenceStore())
        assertTrue(preferences.redactSensitiveHeaders.get())
    }

    @Test
    fun savedRedactionPreferenceIsRespected() {
        val saved = InMemoryPreference("redact_sensitive_headers", false, true)
        val preferences = NetworkPreferences(InMemoryPreferenceStore(sequenceOf(saved)))
        assertFalse(preferences.redactSensitiveHeaders.get())
    }
}
