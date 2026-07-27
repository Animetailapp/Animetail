// AM (DISCORD) -->
package eu.kanade.tachiyomi.data.connections.discord

/**
 * Data class representing a Discord Rich Presence activity for the native SDK.
 * Used to pass activity data to the JNI bridge via [DiscordRpcManager.setActivity].
 */
data class DiscordNativeActivity(
    val activityType: Int = TYPE_WATCHING,
    val name: String?,
    val state: String?,
    val details: String?,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long? = null,
    val largeImage: String?,
    val largeText: String?,
    val smallImage: String?,
    val smallText: String?,
    val button1Label: String? = null,
    val button1Url: String? = null,
    val button2Label: String? = null,
    val button2Url: String? = null,
) {
    companion object {
        const val TYPE_PLAYING = 0
        const val TYPE_STREAMING = 1
        const val TYPE_LISTENING = 2
        const val TYPE_WATCHING = 3
        const val TYPE_CUSTOM_STATUS = 4
        const val TYPE_COMPETING = 5
    }
}
// <-- AM (DISCORD)
