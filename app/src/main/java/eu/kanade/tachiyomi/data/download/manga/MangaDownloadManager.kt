package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDownloadManager(
    private val context: Context,
    private val provider: MangaDownloadProvider = Injekt.get(),
    private val cache: MangaDownloadCache = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val downloader = MangaDownloader(context, provider, cache)

    val isRunning: Boolean
        get() = downloader.isRunning

    private val pendingDeleter = MangaDownloadPendingDeleter(context)

    val queueState
        get() = downloader.queueState

    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = MangaDownloadJob.isRunningFlow(context)

    fun startDownloads() {
        if (downloader.isRunning) return

        if (MangaDownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            MangaDownloadJob.start(context)
        }
    }

    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    fun getQueuedDownloadOrNull(chapterId: Long): MangaDownload? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long) {
        val existingDownload = getQueuedDownloadOrNull(chapterId)
        val toAdd = existingDownload ?: runBlocking { MangaDownload.fromChapterId(chapterId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    fun reorderQueue(downloads: List<MangaDownload>) {
        downloader.updateQueue(downloads)
    }

    fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {
        downloader.queueChapters(manga, chapters, autoStart)
    }

    fun addDownloadsToStartOfQueue(downloads: List<MangaDownload>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!MangaDownloadJob.isRunning(context)) startDownloads()
    }

    fun buildPageList(source: MangaSource, manga: Manga, chapter: Chapter): List<Page> {
        val chapterDir = provider.findChapterDir(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            mangaTitle = manga.title,
            source = source,
        )
        val files = chapterDir?.listFiles().orEmpty()
            .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.page_list_empty_error))
        }

        return files.sortedBy { it.name }
            .mapIndexed { i, file ->
                Page(i, uri = file.uri).apply { status = Page.State.READY }
            }
    }

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(
            chapterName = chapterName,
            chapterScanlator = chapterScanlator,
            mangaTitle = mangaTitle,
            sourceId = sourceId,
            skipCache = skipCache,
        )
    }

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return isChapterDownloaded(chapterName, chapterScanlator, mangaTitle, sourceId, skipCache)
    }

    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    fun getDownloadCount(manga: Manga): Int {
        return cache.getDownloadCount(manga)
    }

    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    fun getDownloadSize(manga: Manga): Long {
        return cache.getDownloadSize(manga)
    }

    fun cancelQueuedDownloads(downloads: List<MangaDownload>) {
        removeFromDownloadQueue(downloads.map { it.chapter })
    }

    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: MangaSource) {
        launchIO {
            val filteredChapters = getChaptersToDelete(chapters, manga)
            if (filteredChapters.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredChapters)

            val (mangaDir, chapterDirs) = provider.findChapterDirs(filteredChapters, manga, source)
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, manga)

            if (mangaDir?.listFiles()?.isEmpty() == true) {
                deleteManga(manga, source, removeQueued = false)
            }
        }
    }

    fun deleteManga(manga: Manga, source: MangaSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(manga)
            }
            provider.findMangaDir(manga.title, source)?.delete()
            cache.removeManga(manga)

            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<Chapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(chapters)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    suspend fun enqueueChaptersToDelete(chapters: List<Chapter>, manga: Manga) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, manga), manga)
    }

    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    fun renameSource(oldSource: MangaSource, newSource: MangaSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + MangaDownloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (!oldFolder.renameTo(newName)) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    suspend fun renameManga(manga: Manga, newTitle: String) {
        val source = sourceManager.getOrStub(manga.source)
        val oldFolder = provider.findMangaDir(manga.title, source) ?: return
        val newName = provider.getMangaDirName(newTitle)

        if (oldFolder.name == newName) return

        downloader.removeFromQueue(manga)

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + MangaDownloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename manga download folder: ${oldFolder.name}" }
                return
            }
        }

        if (oldFolder.renameTo(newName)) {
            cache.renameManga(manga, oldFolder, newTitle)
        } else {
            logcat(LogPriority.ERROR) { "Failed to rename manga download folder: ${oldFolder.name}" }
        }
    }

    suspend fun renameChapter(source: MangaSource, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val mangaDir = try {
            provider.getMangaDir(manga.title, source)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Manga download folder doesn't exist. Skipping renaming after source sync" }
            return
        }

        val oldDownload = oldNames.asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull()
            ?: return

        var newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)
        if (oldDownload.isFile && oldDownload.extension == "cbz") {
            newName += ".cbz"
        }

        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldChapter, manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded chapter: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getChaptersToDelete(chapters: List<Chapter>, manga: Manga): List<Chapter> {
        val categoriesToExclude = downloadPreferences.removeExcludeCategories().get().map(String::toLong)

        val categoriesForManga = getCategories.await(manga.id)
            .map { it.id }
            .ifEmpty { listOf(0) }
        val filteredCategoryManga = if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get()) {
            filteredCategoryManga.filterNot { it.bookmark }
        } else {
            filteredCategoryManga
        }
    }

    fun statusFlow(): Flow<MangaDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == MangaDownload.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<MangaDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == MangaDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }
}
