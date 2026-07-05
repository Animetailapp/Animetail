// AM (DISCORD) -->

// Taken from Animiru. Thank you Quickdev for permission!

package eu.kanade.tachiyomi.data.connections.discord

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category.Companion.UNCATEGORIZED_ID
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.ceil
import kotlin.math.floor

class DiscordRPCService : Service() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val scope: CoroutineScope by injectLazy()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        // Initialize the native SDK if not already done
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(applicationContext)
        }

        // Get stored OAuth token for the native SDK
        val effectiveToken = DiscordTokenStore.retrieve()

        if (!effectiveToken.isNullOrBlank()) {
            if (!DiscordRpcManager.isAuthorized()) {
                DiscordRpcManager.reconnectWithToken(effectiveToken)
            }

            // Listen for connection ready state and update RPC automatically when established
            scope.launchIO {
                DiscordRpcManager.connectionStatus.collect { status ->
                    if (status == DiscordRpcManager.Status.Connected) {
                        try {
                            if (lastUsedScreen == DiscordScreen.VIDEO) {
                                setAnimeScreen(this@DiscordRPCService, lastUsedScreen)
                            } else if (lastUsedScreen == DiscordScreen.MANGA) {
                                setMangaScreen(this@DiscordRPCService, lastUsedScreen)
                            } else {
                                setScreen(this@DiscordRPCService, lastUsedScreen)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating presence on connection ready: ${e.message}", e)
                        }
                    }
                }
            }
            notification(this)
        } else {
            connectionsPreferences.enableDiscordRPC().set(false)
        }
    }

    override fun onDestroy() {
        NotificationReceiver.dismissNotification(this, Notifications.ID_DISCORD_RPC)
        DiscordRpcManager.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun notification(context: Context) {
        val builder = context.notificationBuilder(Notifications.CHANNEL_DISCORD_RPC) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_discord_24dp)
            setContentText(context.resources.getString(R.string.pref_discord_rpc))
            setAutoCancel(false)
            setOngoing(true)
            setUsesChronometer(true)
        }

        startForeground(Notifications.ID_DISCORD_RPC, builder.build())
    }

    companion object {

        private val connectionsPreferences: ConnectionsPreferences by injectLazy()

        private val handler = Handler(Looper.getMainLooper())
        private val playerPreferences: PlayerPreferences by injectLazy()

        fun start(context: Context) {
            handler.removeCallbacksAndMessages(null)
            if (connectionsPreferences.enableDiscordRPC().get()) {
                since = System.currentTimeMillis()
                context.startService(Intent(context, DiscordRPCService::class.java))
            }
        }

        fun stop(context: Context, delay: Long = 30000L) {
            handler.postDelayed(
                { context.stopService(Intent(context, DiscordRPCService::class.java)) },
                delay,
            )
        }

        private var since = 0L

        internal var lastUsedScreen = DiscordScreen.APP
            set(value) {
                field = if ((
                        value == DiscordScreen.VIDEO ||
                            value == DiscordScreen.MANGA
                        ) ||
                    value == DiscordScreen.WEBVIEW
                ) {
                    field
                } else {
                    value
                }
            }
        private const val MP_PREFIX = "mp:"
        private const val TAG = "DiscordRPCService"

        /**
         * Sets the appropriate screen (anime or manga) based on the last used screen context
         */
        internal suspend fun setScreen(context: Context, discordScreen: DiscordScreen) {
            if (!DiscordRpcManager.isReady()) return
            when (lastUsedScreen) {
                DiscordScreen.VIDEO -> {
                    setAnimeScreen(context, discordScreen)
                }

                DiscordScreen.MANGA -> {
                    setMangaScreen(context, discordScreen)
                }

                else -> {
                    setAnimeScreen(context, discordScreen)
                }
            }
        }

        internal suspend fun setAnimeScreen(
            context: Context,
            discordScreen: DiscordScreen,
            playerData: PlayerData = PlayerData(),
        ) {
            lastUsedScreen = discordScreen

            if (!DiscordRpcManager.isReady()) return
            updateDiscordRPC(context, playerData, discordScreen)
        }

        private suspend fun updateDiscordRPC(
            context: Context,
            playerData: PlayerData,
            discordScreen: DiscordScreen,
            sinceTime: Long = since,
        ) {
            val appName = context.getString(R.string.app_name)

            val customMessage = connectionsPreferences.discordCustomMessage().get()
            val showProgress = connectionsPreferences.discordShowProgress().get()
            val showTimestamp = connectionsPreferences.discordShowTimestamp().get()
            val showButtons = connectionsPreferences.discordShowButtons().get()
            val showDownloadButton = connectionsPreferences.discordShowDownloadButton().get()
            val showDiscordButton = connectionsPreferences.discordShowDiscordButton().get()

            val name = playerData.animeTitle ?: appName
            val details = when {
                customMessage.isNotBlank() -> customMessage
                playerData.animeTitle != null -> playerData.animeTitle
                else -> context.getString(discordScreen.details)
            }

            val state = when {
                !showProgress -> null
                playerData.episodeNumber != null -> playerData.episodeNumber
                else -> context.getString(discordScreen.text)
            }

            val imageUrl = playerData.thumbnailUrl ?: discordScreen.imageUrl

            val startTimestamp = if (showTimestamp) {
                playerData.startTimestamp ?: since
            } else {
                0L
            }

            val endTimestamp = if (showTimestamp) {
                playerData.endTimestamp
            } else {
                null
            }

            val button1Label = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_LABEL else null
            val button1Url = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_URL else null
            val button2Label = if (showButtons && showDiscordButton) DISCORD_BUTTON_LABEL else null
            val button2Url = if (showButtons && showDiscordButton) DISCORD_BUTTON_URL else null

            val largeImage = if (imageUrl.startsWith(
                    "http",
                )
            ) {
                "mp:${imageUrl.replace("://", "/")}"
            } else {
                "$MP_PREFIX$imageUrl"
            }
            val smallImage = if (DiscordScreen.APP.imageUrl.startsWith(
                    "http",
                )
            ) {
                "mp:${DiscordScreen.APP.imageUrl.replace("://", "/")}"
            } else {
                "$MP_PREFIX${DiscordScreen.APP.imageUrl}"
            }

            DiscordRpcManager.setActivity(
                DiscordNativeActivity(
                    activityType = DiscordNativeActivity.TYPE_WATCHING,
                    name = name,
                    details = details,
                    state = state,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    largeImage = largeImage,
                    largeText = name,
                    smallImage = smallImage,
                    smallText = context.getString(DiscordScreen.APP.text),
                    button1Label = button1Label,
                    button1Url = button1Url,
                    button2Label = button2Label,
                    button2Url = button2Url,
                ),
            )
        }

        internal suspend fun setMangaScreen(
            context: Context,
            discordScreen: DiscordScreen,
            readerData: ReaderData = ReaderData(),
        ) {
            lastUsedScreen = discordScreen
            if (!DiscordRpcManager.isReady()) return
            updateDiscordRPC(context, readerData, discordScreen)
        }

        private suspend fun updateDiscordRPC(
            context: Context,
            readerData: ReaderData,
            discordScreen: DiscordScreen,
            sinceTime: Long = since,
        ) {
            val appName = context.getString(R.string.app_name)
            val name = readerData.mangaTitle ?: appName
            val details = readerData.mangaTitle ?: context.getString(discordScreen.details)
            val state = readerData.chapterNumber ?: context.getString(discordScreen.text)
            val imageUrl = readerData.thumbnailUrl ?: discordScreen.imageUrl

            val showButtons = connectionsPreferences.discordShowButtons().get()
            val showDownloadButton = connectionsPreferences.discordShowDownloadButton().get()
            val showDiscordButton = connectionsPreferences.discordShowDiscordButton().get()

            val button1Label = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_LABEL else null
            val button1Url = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_URL else null
            val button2Label = if (showButtons && showDiscordButton) DISCORD_BUTTON_LABEL else null
            val button2Url = if (showButtons && showDiscordButton) DISCORD_BUTTON_URL else null

            val largeImage = if (imageUrl.startsWith(
                    "http",
                )
            ) {
                "mp:${imageUrl.replace("://", "/")}"
            } else {
                "$MP_PREFIX$imageUrl"
            }
            val smallImage = if (DiscordScreen.APP.imageUrl.startsWith(
                    "http",
                )
            ) {
                "mp:${DiscordScreen.APP.imageUrl.replace("://", "/")}"
            } else {
                "$MP_PREFIX${DiscordScreen.APP.imageUrl}"
            }

            DiscordRpcManager.setActivity(
                DiscordNativeActivity(
                    activityType = DiscordNativeActivity.TYPE_WATCHING,
                    name = name,
                    details = details,
                    state = state,
                    startTimestamp = sinceTime,
                    largeImage = largeImage,
                    largeText = name,
                    smallImage = smallImage,
                    smallText = context.getString(DiscordScreen.APP.text),
                    button1Label = button1Label,
                    button1Url = button1Url,
                    button2Label = button2Label,
                    button2Url = button2Url,
                ),
            )
        }

        @Suppress("SwallowedException", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
        internal suspend fun setPlayerActivity(
            context: Context,
            playerData: PlayerData = PlayerData(),
        ) {
            if (!DiscordRpcManager.isReady() || playerData.thumbnailUrl == null || playerData.animeId == null) return

            try {
                val categories = getCategories(playerData.animeId)
                val discordIncognito = isIncognito(categories, playerData.incognitoMode)

                val animeTitle = playerData.animeTitle.takeUnless { discordIncognito }
                val episodeNumber = getFormattedEpisodeNumber(playerData, discordIncognito)
                val (startTime, end) = getTimestamps(playerData)

                withIOContext {
                    val animeThumbnail = if (discordIncognito) null else playerData.thumbnailUrl

                    setAnimeScreen(
                        context = context,
                        discordScreen = DiscordScreen.VIDEO,
                        playerData = PlayerData(
                            animeTitle = animeTitle,
                            episodeNumber = episodeNumber,
                            thumbnailUrl = animeThumbnail,
                            startTimestamp = startTime,
                            endTimestamp = end,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting player activity: ${e.message}", e)
            }
        }

        @Suppress("SwallowedException", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
        internal suspend fun setReaderActivity(
            context: Context,
            readerData: ReaderData = ReaderData(),
        ) {
            if (!DiscordRpcManager.isReady() || readerData.thumbnailUrl == null || readerData.mangaId == null) return
            try {
                val categories = getCategories(readerData.mangaId)
                val discordIncognito = isIncognito(categories, readerData.incognitoMode)

                val mangaTitle = readerData.mangaTitle.takeUnless { discordIncognito }
                val chapterNumber = getFormattedChapterNumber(readerData, discordIncognito)

                withIOContext {
                    val mangaThumbnail = if (discordIncognito) null else readerData.thumbnailUrl

                    setMangaScreen(
                        context = context,
                        discordScreen = DiscordScreen.MANGA,
                        readerData = ReaderData(
                            mangaTitle = mangaTitle,
                            chapterNumber = chapterNumber,
                            thumbnailUrl = mangaThumbnail,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting reader activity: ${e.message}", e)
            }
        }

        // Helper functions

        private suspend fun getCategories(id: Long?): List<String> {
            return Injekt.get<GetAnimeCategories>()
                .await(id!!)
                .map { it.id.toString() }
                .run { ifEmpty { plus(UNCATEGORIZED_ID.toString()) } }
        }

        private fun isIncognito(categories: List<String>, incognitoMode: Boolean): Boolean {
            val discordIncognitoMode = connectionsPreferences.discordRPCIncognito().get()
            val incognitoCategories = connectionsPreferences.discordRPCIncognitoCategories().get()
            val incognitoCategory = categories.fastAny { it in incognitoCategories }
            return discordIncognitoMode || incognitoMode || incognitoCategory
        }

        private fun getFormattedEpisodeNumber(playerData: PlayerData, discordIncognito: Boolean): String? {
            return playerData.episodeNumber?.let {
                when {
                    discordIncognito -> null
                    connectionsPreferences.useChapterTitles().get() -> it
                    ceil(it.toDouble()) == floor(it.toDouble()) -> "Episode ${it.toInt()}"
                    else -> "Episode $it"
                }
            }
        }
        private fun getFormattedChapterNumber(readerData: ReaderData, discordIncognito: Boolean): String? {
            val chapterNumber = readerData.chapterNumber
            val chapterProgress = readerData.chapterProgress
            return chapterNumber?.let {
                when {
                    discordIncognito -> null

                    connectionsPreferences.useChapterTitles().get() ->
                        "$it (${chapterProgress.first}/${chapterProgress.second})"

                    ceil(it.toDouble()) == floor(it.toDouble()) -> "Chapter ${it.toInt()}" + " " +
                        "(${chapterProgress.first}/${chapterProgress.second})"

                    else -> "Chapter $it (${chapterProgress.first}/${chapterProgress.second}"
                }
            }
        }

        private fun getTimestamps(playerData: PlayerData): Pair<Long?, Long?> {
            val startTime = playerData.startTimestamp ?: System.currentTimeMillis()
            val end = playerData.endTimestamp
            return Pair(startTime, end)
        }
    }
}
// <-- AM (DISCORD)
