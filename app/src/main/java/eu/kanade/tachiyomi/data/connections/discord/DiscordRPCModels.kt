// AM (DISCORD) -->

// Taken from Animiru. Thank you Quickdev for permission!
// Original library from https://github.com/dead8309/KizzyRPC (Thank you)
// Thank you to the 最高 man for the refactored and simplified code
// https://github.com/saikou-app/saikou
package eu.kanade.tachiyomi.data.connections.discord

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.isPreviewBuildType

// Constant for logging tag
const val RICH_PRESENCE_TAG = "discord_rpc"

// Constant for application id
const val RICH_PRESENCE_APPLICATION_ID = "1173423931865170070"

const val DOWNLOAD_BUTTON_LABEL = "Download"
const val DOWNLOAD_BUTTON_URL = "https://github.com/Dark25/Animetail2/releases"
const val DISCORD_BUTTON_LABEL = "Discord"
const val DISCORD_BUTTON_URL = "https://discord.gg/fvskrQZb9j"

data class PlayerData(
    val incognitoMode: Boolean = false,
    val animeId: Long? = null,
    val animeTitle: String? = null,
    val episodeNumber: String? = null,
    val episodeProgress: Pair<Int, Int>? = null,
    val thumbnailUrl: String? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
)

data class ReaderData(
    val incognitoMode: Boolean = false,
    val mangaId: Long? = null,
    val mangaTitle: String? = null,
    val chapterProgress: Pair<Int, Int> = Pair(0, 0),
    val chapterNumber: String? = null,
    val thumbnailUrl: String? = null,
)

// Enum class for standard Rich Presence in-app screens
enum class DiscordScreen(
    @StringRes val text: Int,
    @StringRes val details: Int,
    val imageUrl: String,
) {
    APP(R.string.app_name, R.string.browsing, ANIMETAIL_IMAGE),
    LIBRARY(R.string.label_library, R.string.browsing, LIBRARY_IMAGE_URL),
    UPDATES(R.string.label_recent_updates, R.string.scrolling, UPDATES_IMAGE_URL),
    HISTORY(R.string.label_recent_manga, R.string.scrolling, HISTORY_IMAGE_URL),
    BROWSE(R.string.label_sources, R.string.browsing, BROWSE_IMAGE_URL),
    MORE(R.string.label_settings, R.string.messing, MORE_IMAGE_URL),
    WEBVIEW(R.string.action_web_view, R.string.browsing, WEBVIEW_IMAGE_URL),
    VIDEO(R.string.video, R.string.watching, VIDEO_IMAGE_URL),
    MANGA(R.string.manga, R.string.reading, MANGA_IMAGE_URL),
}

// Constants for standard Rich Presence image urls
// change the image Urls used here to match animetail brown/ green theme, Luft
private const val BASE_URL = "https://cdn.discordapp.com/emojis/"
private const val ANIMETAIL_IMAGE_URL = "${BASE_URL}1286834441981005824.webp?quality=lossless"
private const val ANIMETAIL_PREVIEW_IMAGE_URL = "${BASE_URL}1286834519533420544.webp?quality=lossless"
private val ANIMETAIL_IMAGE = if (isPreviewBuildType) {
    ANIMETAIL_PREVIEW_IMAGE_URL
} else {
    ANIMETAIL_IMAGE_URL
}
private const val LIBRARY_IMAGE_URL = "${BASE_URL}1235353629867638924.webp?quality=lossless"
private const val UPDATES_IMAGE_URL = "${BASE_URL}1235354596570955917.webp?quality=lossless"
private const val HISTORY_IMAGE_URL = "${BASE_URL}1235354299089817671.webp?quality=lossless"
private const val BROWSE_IMAGE_URL = "${BASE_URL}1235354864419344455.webp?quality=lossless"
private const val MORE_IMAGE_URL = "${BASE_URL}1235355169752088706.webp?quality=lossless"
private const val WEBVIEW_IMAGE_URL = "${BASE_URL}1235355362169851996.webp?quality=lossless"
private const val VIDEO_IMAGE_URL = "${BASE_URL}1235355607201218660.webp?quality=lossless"
private const val MANGA_IMAGE_URL = "${BASE_URL}1235355804274659390.webp?quality=lossless"
// <-- AM (DISCORD)
