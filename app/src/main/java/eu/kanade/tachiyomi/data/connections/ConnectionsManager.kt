package eu.kanade.tachiyomi.data.connections

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.data.connections.discord.Discord
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json

@Inject
@SingleIn(AppScope::class)
class ConnectionsManager(
    connectionsPreferences: ConnectionsPreferences,
    networkHelper: NetworkHelper,
    json: Json,
) {

    companion object {
        const val DISCORD = 201L
    }

    val discord = Discord(DISCORD, connectionsPreferences, networkHelper, json)

    private val services = listOf(discord)

    fun getService(id: Long) = services.find { it.id == id }
}
// <-- AM (CONNECTIONS)
