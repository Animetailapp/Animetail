package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.source.local.entries.manga.isLocal

class TransitionDownloadState(
    val currChapterDownloaded: Boolean,
    goingToChapterDownloaded: Boolean,
    private val isGoingToChapterOnDisk: (suspend () -> Boolean)?,
) {
    var goingToChapterDownloaded by mutableStateOf(goingToChapterDownloaded)
        private set

    suspend fun checkDisk() {
        isGoingToChapterOnDisk?.let { check ->
            goingToChapterDownloaded = withContext(Dispatchers.IO) { check() }
        }
    }
}

fun MangaDownloadManager.transitionDownloadState(
    transition: ChapterTransition,
    manga: Manga?,
    source: MangaSource?,
): TransitionDownloadState? {
    if (manga == null || source == null) return null
    val goingToChapter = transition.to?.chapter.takeUnless { manga.isLocal() }
    return TransitionDownloadState(
        currChapterDownloaded = transition.from.pageLoader?.isLocal == true,
        goingToChapterDownloaded = manga.isLocal() ||
            goingToChapter?.let {
                isChapterDownloaded(
                    chapterName = it.name,
                    chapterScanlator = it.scanlator,
                    chapterUrl = it.url,
                    mangaTitle = manga.ogTitle,
                    sourceId = manga.source,
                )
            } ?: false,
        isGoingToChapterOnDisk = goingToChapter?.let {
            {
                isChapterDownloadedOnDisk(
                    chapterName = it.name,
                    chapterScanlator = it.scanlator,
                    chapterUrl = it.url,
                    mangaTitle = manga.ogTitle,
                    source = source,
                )
            }
        },
    )
}
