package tachiyomi.domain.entries.manga.model

import mihon.domain.common.PartialUpdate

class MangaUpdate(val id: Long, block: MangaUpdate.() -> Unit) : PartialUpdate() {
    var favoriteAt: Long? by field(null)
    var lastUpdate: Long? by field(null)
    var nextUpdate: Long? by field(null)
    var fetchInterval: Int? by field(null)
    var viewerFlags: Long? by field(null)
    var chapterFlags: Long? by field(null)
    var coverLastModified: Long? by field(null)
    var notes: String? by field(null)

    init {
        block()
    }
}

fun Manga.toMangaUpdate(): MangaUpdate = MangaUpdate(id) {
    favoriteAt = this@toMangaUpdate.favoriteAt
    lastUpdate = this@toMangaUpdate.lastUpdate
    nextUpdate = this@toMangaUpdate.nextUpdate
    fetchInterval = this@toMangaUpdate.fetchInterval
    viewerFlags = this@toMangaUpdate.viewerFlags
    chapterFlags = this@toMangaUpdate.chapterFlags
    coverLastModified = this@toMangaUpdate.coverLastModified
    notes = this@toMangaUpdate.notes
}
