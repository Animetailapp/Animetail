package eu.kanade.tachiyomi.data.download.anime

import android.text.TextUtils
import com.hippo.unifile.UniFile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The downloader downloads an episode into `<episode>_tmp` and then moves that directory to its
 * final name. A move that failed halfway must not make every retry of the episode fail.
 */
class EpisodeDirFinalizerTest {

    @TempDir
    lateinit var root: File

    @BeforeEach
    fun beforeEach() {
        // RawFile uses TextUtils.isEmpty, which is a stub in local unit tests.
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun `Finished episode is moved to its final directory`() {
        writeVideo("Ep 1_tmp", "video")

        val result = finalizeEpisodeDir(animeDir(), child("Ep 1_tmp"), "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `Provider rejecting the rename gets a copy`() {
        writeVideo("Ep 1_tmp", "video")
        val tmpDir = spyk(child("Ep 1_tmp"))
        every { tmpDir.renameTo(any()) } throws IllegalStateException("Failed to rename")

        val result = finalizeEpisodeDir(animeDir(), tmpDir, "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `Partial copy left by an earlier attempt is replaced by the finished episode`() {
        // The earlier attempt's copy failed halfway and could not be removed
        writeVideo("Ep 1", "vid")
        writeVideo("Ep 1_tmp", "video")
        val tmpDir = spyk(child("Ep 1_tmp"))
        every { tmpDir.renameTo(any()) } returns false

        val result = finalizeEpisodeDir(animeDir(), tmpDir, "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `Leftover directory that can't be removed keeps the finished episode in place`() {
        writeVideo("Ep 1", "vid")
        writeVideo("Ep 1_tmp", "video")
        val animeDir = spyk(animeDir())
        val leftover = spyk(child("Ep 1"))
        every { animeDir.findFile("Ep 1") } returns leftover
        every { leftover.delete() } returns false

        val result = finalizeEpisodeDir(animeDir, child("Ep 1_tmp"), "Ep 1")

        result.shouldBeNull()
        File(root, "Ep 1_tmp/Ep 1.mkv").readText() shouldBe "video"
    }

    private fun writeVideo(dir: String, content: String) {
        File(root, dir).mkdir()
        File(root, "$dir/Ep 1.mkv").writeText(content)
    }

    private fun animeDir(): UniFile = UniFile.fromFile(root)!!

    // Look files up through the root so they have a parent, as they do in the downloader.
    private fun child(name: String): UniFile = animeDir().findFile(name)!!
}
