package eu.kanade.tachiyomi.ui.player.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HosterPriorityTest {

    private val servers = listOf("SUB HLS", "SUB UPNShare", "SUB Voe", "SUB Byse", "SUB MP4Upload")

    @Test
    fun `blank keywords keep the extension order`() {
        val priority = HosterPriority(" , ")

        assertTrue(priority.isEmpty)
        assertEquals(servers, priority.sort(servers) { it })
    }

    @Test
    fun `matched servers go first in keyword order and the rest keep their order`() {
        val sorted = HosterPriority("byse, upnshare").sort(servers) { it }

        assertEquals(
            listOf("SUB Byse", "SUB UPNShare", "SUB HLS", "SUB Voe", "SUB MP4Upload"),
            sorted,
        )
    }

    @Test
    fun `a server matching several keywords takes the earliest one`() {
        val sorted = HosterPriority("Voe, SUB").sort(servers) { it }

        assertEquals("SUB Voe", sorted.first())
        assertEquals(servers - "SUB Voe", sorted.drop(1))
    }

    @Test
    fun `disabled servers go last even when they have priority`() {
        val priority = HosterPriority("HLS, Voe", rawDisabled = "sub hls")

        assertTrue(priority.isDisabled("SUB HLS"))
        assertFalse(priority.isDisabled("DUB HLS"))
        assertEquals(
            listOf("SUB Voe", "SUB UPNShare", "SUB Byse", "SUB MP4Upload", "SUB HLS"),
            priority.sort(servers) { it },
        )
    }
}
