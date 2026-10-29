package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@AssistedInject
class BackupRestorer(
    @Assisted private val notifier: BackupNotifier,
    @Assisted private val isSync: Boolean,
    private val context: Context,
    private val animeHandler: AnimeDatabaseHandler,
    private val mangaDownloadCache: MangaDownloadCache,
    private val animeDownloadCache: AnimeDownloadCache,
    private val animeCategoriesRestorer: AnimeCategoriesRestorer,
    private val mangaCategoriesRestorer: MangaCategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val customButtonRestorer: CustomButtonRestorer,
    private val animeRestorer: AnimeRestorer,
    private val mangaRestorer: MangaRestorer,
    private val extensionsRestorer: ExtensionsRestorer,
    private val mangaSourceManager: MangaSourceManager,
    private val animeSourceManager: AnimeSourceManager,
    private val mangaStubSourceRepository: MangaStubSourceRepository,
    private val animeStubSourceRepository: AnimeStubSourceRepository,
    private val backupDecoder: BackupDecoder,
) {

    @AssistedFactory
    fun interface Factory {
        fun create(notifier: BackupNotifier, isSync: Boolean): BackupRestorer
    }

    private var restoreAmount = 0
    private val restoreProgress = AtomicInteger(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var animeSourceMapping: Map<Long, String> = emptyMap()
    private var mangaSourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        // Invalidate download cache to ensure UI reflects any restored downloads
        if (options.libraryEntries) {
            try {
                mangaDownloadCache.invalidateCache()
                animeDownloadCache.invalidateCache()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after restore" }
            }
        }

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val (mangaCount, animeCount, backup) = backupDecoder.decodeMetadata(uri)

        // Store source mapping for error messages
        val backupAnimeMaps = backup.backupAnimeSources
        animeSourceMapping = backupAnimeMaps.associate { it.sourceId to it.name }
        val backupMangaMaps = backup.backupSources
        mangaSourceMapping = backupMangaMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreSourceNames(backupMangaMaps, backupAnimeMaps)
            restoreAmount += mangaCount + animeCount
        }
        if (options.categories) {
            restoreAmount += 2 // +2 for anime and manga categories
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupAnimeExtensionStore.size + backup.backupMangaExtensionStore.size
        }
        if (options.customButtons) {
            restoreAmount += 1
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.extensions) {
            restoreAmount += 1
        }

        coroutineScope {
            val restoreCategoriesJob = if (options.categories) {
                restoreCategories(
                    backupAnimeCategories = backup.backupAnimeCategories,
                    backupMangaCategories = backup.backupCategories,
                )
            } else {
                null
            }
            if (options.appSettings) {
                restoreAppPreferences(
                    backup.backupPreferences,
                    backup.backupCategories.takeIf { options.categories },
                    restoreCategoriesJob,
                )
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreAnime(
                    uri,
                    if (options.categories) backup.backupAnimeCategories else emptyList(),
                    restoreCategoriesJob,
                )
                restoreManga(
                    uri,
                    if (options.categories) backup.backupCategories else emptyList(),
                    restoreCategoriesJob,
                )
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupAnimeExtensionStore, backup.backupMangaExtensionStore)
            }
            if (options.customButtons) {
                restoreCustomButtons(backup.backupCustomButton)
            }
            if (options.extensions) {
                restoreExtensions(backup.backupExtensions)
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    // Without a stub, a source that isn't installed has no name for the next backup to write
    private suspend fun restoreSourceNames(
        backupMangaSources: List<BackupSource>,
        backupAnimeSources: List<BackupAnimeSource>,
    ) {
        backupMangaSources
            .filter { it.name.isNotBlank() }
            .filter {
                mangaSourceManager.get(it.sourceId) == null &&
                    mangaStubSourceRepository.getStubMangaSource(it.sourceId) == null
            }
            .forEach { mangaStubSourceRepository.upsertStubMangaSource(it.sourceId, lang = "", name = it.name) }

        backupAnimeSources
            .filter { it.name.isNotBlank() }
            .filter {
                animeSourceManager.get(it.sourceId) == null &&
                    animeStubSourceRepository.getStubAnimeSource(it.sourceId) == null
            }
            .forEach { animeStubSourceRepository.upsertStubAnimeSource(it.sourceId, lang = "", name = it.name) }
    }

    private fun CoroutineScope.restoreCategories(
        backupAnimeCategories: List<BackupCategory>,
        backupMangaCategories: List<BackupCategory>,
    ) = launch {
        ensureActive()
        animeCategoriesRestorer(backupAnimeCategories)
        mangaCategoriesRestorer(backupMangaCategories)

        val progress = restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreAnime(
        uri: Uri,
        backupAnimeCategories: List<BackupCategory>,
        categoriesRestoreJob: Job?,
    ) = launch {
        categoriesRestoreJob?.join()

        backupDecoder.decodeAnimeEntries(uri)
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    animeHandler.await(inTransaction = true) {
                        chunk.forEach {
                            ensureActive()
                            val seasons = emptyList<BackupAnime>()
                            animeRestorer.restore(it, backupAnimeCategories, seasons)
                        }
                    }
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndGet(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()

                        try {
                            val seasons = emptyList<BackupAnime>()
                            animeRestorer.restore(it, backupAnimeCategories, seasons)
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = animeSourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndGet()
                    }
                }

                notifier.showRestoreProgress(chunk.last().title, restoreProgress.get(), restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreManga(
        uri: Uri,
        backupMangaCategories: List<BackupCategory>,
        categoriesRestoreJob: Job?,
    ) = launch {
        categoriesRestoreJob?.join()

        backupDecoder.decodeMangaEntries(uri)
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    ensureActive()
                    mangaRestorer.restore(chunk, backupMangaCategories)
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndGet(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()

                        try {
                            mangaRestorer.restore(listOf(it), backupMangaCategories)
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = mangaSourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndGet()
                    }
                }

                notifier.showRestoreProgress(chunk.last().title, restoreProgress.get(), restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
        categoriesRestoreJob: Job?,
    ) = launch {
        ensureActive()
        categoriesRestoreJob?.join()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        val progress = restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        val progress = restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupAnimeExtensionStore: List<BackupExtensionStore>,
        backupMangaExtensionStore: List<BackupExtensionStore>,
    ) = launch {
        backupAnimeExtensionStore
            .forEach {
                ensureActive()

                try {
                    extensionStoreRestorer.restoreAnime(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Anime Store: ${it.name} : ${e.message}")
                }

                val progress = restoreProgress.incrementAndGet()
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionStores),
                    progress,
                    restoreAmount,
                    isSync,
                )
            }

        backupMangaExtensionStore
            .forEach {
                ensureActive()

                try {
                    extensionStoreRestorer.restoreManga(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Manga Store: ${it.name} : ${e.message}")
                }

                val progress = restoreProgress.incrementAndGet()
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionStores),
                    progress,
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun CoroutineScope.restoreCustomButtons(customButtons: List<BackupCustomButtons>) = launch {
        ensureActive()
        customButtonRestorer(customButtons)

        val progress = restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(AYMR.strings.custom_button_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensions(extensions: List<BackupExtension>) = launch {
        ensureActive()
        extensionsRestorer.restoreExtensions(extensions)

        val progress = restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("animetail_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
