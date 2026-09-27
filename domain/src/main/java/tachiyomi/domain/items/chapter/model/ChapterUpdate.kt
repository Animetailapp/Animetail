package tachiyomi.domain.items.chapter.model

import mihon.domain.common.PartialUpdate

class ChapterUpdate(val id: Long, block: ChapterUpdate.() -> Unit) : PartialUpdate() {
    var read: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastPageRead: Long? by field(null)
    var dateFetch: Long? by field(null)
    var dateUploadOverride: Long? by field(null)

    init {
        block()
    }
}

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(id) {
        read = this@toChapterUpdate.read
        bookmark = this@toChapterUpdate.bookmark
        lastPageRead = this@toChapterUpdate.lastPageRead
        dateFetch = this@toChapterUpdate.dateFetch
        dateUploadOverride = this@toChapterUpdate.dateUploadOverride
    }
}
