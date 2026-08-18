package eu.kanade.tachiyomi.data.connections

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.connections.discord.Discord

@Inject
@SingleIn(AppScope::class)
class ConnectionsManager {

    companion object {
        const val DISCORD = 201L
    }

    val discord = Discord(DISCORD)

    private val services = listOf(discord)

    fun getService(id: Long) = services.find { it.id == id }
}
// <-- AM (CONNECTIONS)
