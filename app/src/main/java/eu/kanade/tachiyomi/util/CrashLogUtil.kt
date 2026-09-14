package eu.kanade.tachiyomi.util

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.lang.withUIContext
import kotlin.time.Clock

@Inject
class CrashLogUtil(
    private val context: Context,
    private val mangaExtensionManager: MangaExtensionManager,
    private val animeExtensionManager: AnimeExtensionManager,
    private val preferences: BasePreferences,
    private val networkPreferences: NetworkPreferences,
) {

    suspend fun dumpLogs(exception: Throwable? = null) = withNonCancellableContext {
        try {
            val file = context.createFileInCacheDir("animetail_crash_logs.txt")

            file.appendText(getDebugInfo() + "\n\n")
            getMangaExtensionsInfo()?.let { file.appendText("$it\n\n") }
            getAnimeExtensionsInfo()?.let { file.appendText("$it\n\n") }
            exception?.let { file.appendText("$it\n\n") }

            val logPriority = if (networkPreferences.verboseLogging.get()) "V" else "E"
            Runtime.getRuntime().exec("logcat *:$logPriority -d -v year -v zone -f ${file.absolutePath}").waitFor()

            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (_: Throwable) {
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    fun getDebugInfo(): String {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        return """
            App ID: ${BuildConfig.APPLICATION_ID}
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Installation ID: ${preferences.installationId.get()}
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${WebViewUtil.getVersion(context)}
            Current time: ${now.toLocalDateTime(tz)}${tz.offsetAt(now)}
            MPV version: 6764488
            Libplacebo version: v7.349.0
            FFmpeg version: n7.1
        """.trimIndent()
        // TODO: Use this again (from aniyomi-mpv-lib 1.17.n onwards):

        //    MPV version: ${Utils.VERSIONS.mpv}
        //    Libplacebo version: ${Utils.VERSIONS.libPlacebo}
        //    FFmpeg version: ${Utils.VERSIONS.ffmpeg}
    }

    private suspend fun getMangaExtensionsInfo(): String? {
        val availableExtensions = mangaExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val outdatedInfoList = mangaExtensionManager.getLoadedExtensions()
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        val notLoadedInfoList = mangaExtensionManager.getNotLoadedExtensions()
            .sortedBy { it.name }
            .map { extension ->
                buildString {
                    appendLine("- ${extension.name}")
                    appendLine("  Installed: ${extension.versionName} (lib ${extension.libVersion ?: "?"})")
                    append("  Not loaded: ${extension.reason.description}")

                    val reason = extension.reason
                    if (reason is MangaExtension.NotLoaded.Reason.Failed) {
                        appendLine()
                        append(reason.stackTrace.trimEnd().prependIndent("  "))
                    }
                }
            }

        val extensionInfoList = outdatedInfoList + notLoadedInfoList

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic manga extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }

    private suspend fun getAnimeExtensionsInfo(): String? {
        val availableExtensions = animeExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val outdatedInfoList = animeExtensionManager.getLoadedExtensions()
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        val notLoadedInfoList = animeExtensionManager.getNotLoadedExtensions()
            .sortedBy { it.name }
            .map { extension ->
                buildString {
                    appendLine("- ${extension.name}")
                    appendLine("  Installed: ${extension.versionName} (lib ${extension.libVersion ?: "?"})")
                    append("  Not loaded: ${extension.reason.description}")

                    val reason = extension.reason
                    if (reason is AnimeExtension.NotLoaded.Reason.Failed) {
                        appendLine()
                        append(reason.stackTrace.trimEnd().prependIndent("  "))
                    }
                }
            }

        val extensionInfoList = outdatedInfoList + notLoadedInfoList

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic anime extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}

private val MangaExtension.NotLoaded.Reason.description: String
    get() = when (this) {
        is MangaExtension.NotLoaded.Reason.Untrusted -> "Untrusted"
        MangaExtension.NotLoaded.Reason.Filtered -> "Filtered by content warning"
        MangaExtension.NotLoaded.Reason.Unsigned -> "Unsigned"
        MangaExtension.NotLoaded.Reason.UnsupportedLibVersion -> "Unsupported lib version"
        MangaExtension.NotLoaded.Reason.Malformed -> "Malformed"
        is MangaExtension.NotLoaded.Reason.Failed -> "Failed ($message)"
    }

private val AnimeExtension.NotLoaded.Reason.description: String
    get() = when (this) {
        is AnimeExtension.NotLoaded.Reason.Untrusted -> "Untrusted"
        AnimeExtension.NotLoaded.Reason.Filtered -> "Filtered by content warning"
        AnimeExtension.NotLoaded.Reason.Unsigned -> "Unsigned"
        AnimeExtension.NotLoaded.Reason.UnsupportedLibVersion -> "Unsupported lib version"
        AnimeExtension.NotLoaded.Reason.Malformed -> "Malformed"
        is AnimeExtension.NotLoaded.Reason.Failed -> "Failed ($message)"
    }
