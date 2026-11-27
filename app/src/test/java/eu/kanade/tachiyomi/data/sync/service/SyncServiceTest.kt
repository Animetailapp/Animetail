package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SyncServiceTest {

    private val service = object : SyncService(
        context = mockk(),
        json = Json,
        syncPreferences = mockk { every { uniqueDeviceID() } returns "test" },
    ) {
        override suspend fun doSync(syncData: SyncData): Backup? = error("Network sync is unused in this test")

        fun merge(local: BackupChapter, remote: BackupChapter): BackupChapter {
            return mergeSyncData(syncData(local), syncData(remote)).backup!!.backupManga.single().chapters.single()
        }

        private fun syncData(chapter: BackupChapter): SyncData {
            val manga = BackupManga(source = 1, url = "/manga", chapters = listOf(chapter))
            return SyncData(backup = Backup(backupManga = listOf(manga)))
        }
    }

    @Test
    fun `unversioned chapters retain completion and the furthest page in either direction`() {
        val unread = BackupChapter(url = "/chapter", name = "Chapter 1", lastPageRead = 12)
        val read = unread.copy(read = true, lastPageRead = 8)

        val fromRemote = service.merge(unread, read)
        val fromLocal = service.merge(read, unread)

        fromRemote.read shouldBe true
        fromRemote.lastPageRead shouldBe 12L
        fromLocal.read shouldBe true
        fromLocal.lastPageRead shouldBe 12L
        unread.read shouldBe false
    }

    @Test
    fun `unversioned chapters retain a bookmark in either direction when only the bookmark differs`() {
        val unbookmarked = BackupChapter(url = "/chapter", name = "Chapter 1")
        val bookmarked = unbookmarked.copy(bookmark = true)

        service.merge(unbookmarked, bookmarked) shouldBe bookmarked
        service.merge(bookmarked, unbookmarked) shouldBe bookmarked
    }

    @Test
    fun `versioned chapters retain version precedence`() {
        val local = BackupChapter(url = "/chapter", name = "Chapter 1", read = true, lastPageRead = 12, version = 1)
        val remote = local.copy(read = false, lastPageRead = 0, version = 2)

        service.merge(local, remote) shouldBe remote
    }
}
