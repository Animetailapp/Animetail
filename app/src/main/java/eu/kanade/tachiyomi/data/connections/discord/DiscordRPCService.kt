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
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
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
                            when (currentScreen) {
                                DiscordScreen.VIDEO -> {
                                    val data = activePlayerData ?: PlayerData()
                                    setAnimeScreen(this@DiscordRPCService, currentScreen, data)
                                }

                                DiscordScreen.MANGA -> {
                                    val data = activeReaderData ?: ReaderData()
                                    setMangaScreen(this@DiscordRPCService, currentScreen, data)
                                }

                                else -> {
                                    setScreen(this@DiscordRPCService, currentScreen)
                                }
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
        DiscordRpcManager.destroy()
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

        internal var currentScreen = DiscordScreen.APP

        private var activePlayerData: PlayerData? = null
        private var activeReaderData: ReaderData? = null

        private const val MP_PREFIX = "mp:"
        private const val TAG = "DiscordRPCService"

        /**
         * Sets the appropriate screen (anime or manga) based on the last used screen context
         */
        internal suspend fun setScreen(context: Context, discordScreen: DiscordScreen) {
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            currentScreen = discordScreen
            if (discordScreen != DiscordScreen.VIDEO && discordScreen != DiscordScreen.MANGA &&
                discordScreen != DiscordScreen.WEBVIEW
            ) {
                lastUsedScreen = discordScreen
                activePlayerData = null
                activeReaderData = null
            }
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
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            currentScreen = discordScreen
            if (discordScreen == DiscordScreen.VIDEO) {
                activePlayerData = playerData
                activeReaderData = null
            } else if (discordScreen != DiscordScreen.MANGA && discordScreen != DiscordScreen.WEBVIEW) {
                lastUsedScreen = discordScreen
                activePlayerData = null
                activeReaderData = null
            }

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

            val name = appName
            val details = sanitizeField(
                when {
                    customMessage.isNotBlank() -> customMessage
                    playerData.animeTitle != null -> playerData.animeTitle
                    else -> context.getString(discordScreen.details)
                },
            )

            val state = sanitizeField(
                when {
                    !showProgress -> null
                    playerData.episodeNumber != null -> playerData.episodeNumber
                    else -> context.getString(discordScreen.text)
                },
            )

            val imageUrl = playerData.thumbnailUrl.takeUnless { it.isNullOrBlank() } ?: discordScreen.imageUrl

            val startTimestamp = if (showTimestamp) {
                (playerData.startTimestamp ?: since) / 1000L
            } else {
                0L
            }

            val endTimestamp = if (showTimestamp) {
                playerData.endTimestamp?.let { it / 1000L }
            } else {
                null
            }

            val button1Label = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_LABEL else null
            val button1Url = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_URL else null
            val button2Label = if (showButtons && showDiscordButton) DISCORD_BUTTON_LABEL else null
            val button2Url = if (showButtons && showDiscordButton) DISCORD_BUTTON_URL else null

            val largeImage = imageUrl
            val smallImage = DiscordScreen.APP.imageUrl

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
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            currentScreen = discordScreen
            if (discordScreen == DiscordScreen.MANGA) {
                activeReaderData = readerData
                activePlayerData = null
            } else if (discordScreen != DiscordScreen.VIDEO && discordScreen != DiscordScreen.WEBVIEW) {
                lastUsedScreen = discordScreen
                activeReaderData = null
                activePlayerData = null
            }
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
            val name = appName
            val details = sanitizeField(readerData.mangaTitle ?: context.getString(discordScreen.details))
            val state = sanitizeField(readerData.chapterNumber ?: context.getString(discordScreen.text))
            val imageUrl = readerData.thumbnailUrl.takeUnless { it.isNullOrBlank() } ?: discordScreen.imageUrl

            val showTimestamp = connectionsPreferences.discordShowTimestamp().get()
            val showButtons = connectionsPreferences.discordShowButtons().get()
            val showDownloadButton = connectionsPreferences.discordShowDownloadButton().get()
            val showDiscordButton = connectionsPreferences.discordShowDiscordButton().get()

            val button1Label = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_LABEL else null
            val button1Url = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_URL else null
            val button2Label = if (showButtons && showDiscordButton) DISCORD_BUTTON_LABEL else null
            val button2Url = if (showButtons && showDiscordButton) DISCORD_BUTTON_URL else null

            val largeImage = imageUrl
            val smallImage = DiscordScreen.APP.imageUrl

            DiscordRpcManager.setActivity(
                DiscordNativeActivity(
                    activityType = DiscordNativeActivity.TYPE_WATCHING,
                    name = name,
                    details = details,
                    state = state,
                    startTimestamp = if (showTimestamp) sinceTime / 1000L else 0L,
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
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            if (playerData.animeId == null) return

            try {
                val categories = getAnimeCategories(playerData.animeId)
                val discordIncognito = isIncognito(categories, playerData.incognitoMode)

                val animeTitle = playerData.animeTitle.takeUnless { discordIncognito }
                val episodeNumber = getFormattedEpisodeNumber(playerData, discordIncognito)
                val (startTime, end) = getTimestamps(playerData)
                val animeThumbnail = if (discordIncognito) {
                    null
                } else {
                    playerData.thumbnailUrl.takeUnless {
                        it.isNullOrBlank()
                    }
                }

                val data = PlayerData(
                    incognitoMode = discordIncognito,
                    animeId = playerData.animeId,
                    animeTitle = animeTitle,
                    episodeNumber = episodeNumber,
                    thumbnailUrl = animeThumbnail,
                    startTimestamp = startTime,
                    endTimestamp = end,
                )

                // Update current state
                currentScreen = DiscordScreen.VIDEO
                activePlayerData = data
                activeReaderData = null

                if (!DiscordRpcManager.isReady()) return

                withIOContext {
                    setAnimeScreen(
                        context = context,
                        discordScreen = DiscordScreen.VIDEO,
                        playerData = data,
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
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            if (readerData.mangaId == null) return
            try {
                val categories = getMangaCategories(readerData.mangaId)
                val discordIncognito = isIncognito(categories, readerData.incognitoMode)

                val mangaTitle = readerData.mangaTitle.takeUnless { discordIncognito }
                val chapterNumber = getFormattedChapterNumber(readerData, discordIncognito)
                val mangaThumbnail = if (discordIncognito) {
                    null
                } else {
                    readerData.thumbnailUrl.takeUnless {
                        it.isNullOrBlank()
                    }
                }

                val data = ReaderData(
                    incognitoMode = discordIncognito,
                    mangaId = readerData.mangaId,
                    mangaTitle = mangaTitle,
                    chapterProgress = readerData.chapterProgress,
                    chapterNumber = chapterNumber,
                    thumbnailUrl = mangaThumbnail,
                )

                // Update current state
                currentScreen = DiscordScreen.MANGA
                activeReaderData = data
                activePlayerData = null

                if (!DiscordRpcManager.isReady()) return

                withIOContext {
                    setMangaScreen(
                        context = context,
                        discordScreen = DiscordScreen.MANGA,
                        readerData = data,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting reader activity: ${e.message}", e)
            }
        }

        // Helper functions

        private fun sanitizeField(value: String?): String? {
            if (value == null) return null
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.length < 2) return "$trimmed " // Pad with space to satisfy length >= 2
            if (trimmed.length > 128) return trimmed.take(128)
            return trimmed
        }

        private suspend fun getAnimeCategories(id: Long?): List<String> {
            return Injekt.get<GetAnimeCategories>()
                .await(id!!)
                .map { it.id.toString() }
                .run { ifEmpty { plus(UNCATEGORIZED_ID.toString()) } }
        }

        private suspend fun getMangaCategories(id: Long?): List<String> {
            return Injekt.get<GetMangaCategories>()
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
            val episodeNumber = playerData.episodeNumber ?: return null
            if (discordIncognito) return null

            val isSpanish = java.util.Locale.getDefault().language == "es"
            val movieText = if (isSpanish) "Película" else "Movie"
            val episodeText = if (isSpanish) "Episodio" else "Episode"

            val progress = playerData.episodeProgress
            if (progress != null) {
                val current = progress.first
                val total = progress.second
                if (total == 1) {
                    // It's a Movie
                    return if (connectionsPreferences.useChapterTitles().get()) {
                        episodeNumber
                    } else {
                        movieText
                    }
                } else {
                    // It's a Series / Episode
                    val progressText = "$current/$total"
                    return if (connectionsPreferences.useChapterTitles().get()) {
                        "$episodeNumber ($progressText)"
                    } else {
                        val doubleVal = episodeNumber.toDoubleOrNull()
                        val epNumText = if (doubleVal != null) {
                            if (ceil(doubleVal) ==
                                floor(doubleVal)
                            ) {
                                doubleVal.toInt().toString()
                            } else {
                                doubleVal.toString()
                            }
                        } else {
                            episodeNumber
                        }
                        "$episodeText $epNumText ($progressText)"
                    }
                }
            } else {
                // Fallback (e.g. from External Intents)
                if (connectionsPreferences.useChapterTitles().get()) {
                    return episodeNumber
                }
                val doubleVal = episodeNumber.toDoubleOrNull()
                return if (doubleVal != null) {
                    if (ceil(doubleVal) == floor(doubleVal)) {
                        "$episodeText ${doubleVal.toInt()}"
                    } else {
                        "$episodeText $doubleVal"
                    }
                } else {
                    "$episodeText $episodeNumber"
                }
            }
        }

        private fun getFormattedChapterNumber(readerData: ReaderData, discordIncognito: Boolean): String? {
            val chapterNumber = readerData.chapterNumber ?: return null
            val chapterProgress = readerData.chapterProgress
            if (discordIncognito) return null

            val isSpanish = java.util.Locale.getDefault().language == "es"
            val chapterText = if (isSpanish) "Capítulo" else "Chapter"
            val pageProgress = if (isSpanish) {
                "Pág. ${chapterProgress.first}/${chapterProgress.second}"
            } else {
                "Page ${chapterProgress.first}/${chapterProgress.second}"
            }

            if (connectionsPreferences.useChapterTitles().get()) {
                return "$chapterNumber ($pageProgress)"
            }

            val doubleVal = chapterNumber.toDoubleOrNull()
            return if (doubleVal != null) {
                val chapNumText = if (ceil(doubleVal) ==
                    floor(doubleVal)
                ) {
                    doubleVal.toInt().toString()
                } else {
                    doubleVal.toString()
                }
                "$chapterText $chapNumText ($pageProgress)"
            } else {
                "$chapterText $chapterNumber ($pageProgress)"
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
