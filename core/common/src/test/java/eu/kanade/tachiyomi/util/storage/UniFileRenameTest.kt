package eu.kanade.tachiyomi.util.storage

import android.text.TextUtils
import com.hippo.unifile.UniFile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class UniFileRenameTest {

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
    fun `Successful rename returns the same file under the new name`() {
        val file = createFile("page.tmp", "data")

        val result = file.renameToOrCopy("page.jpg")

        result shouldBeSameInstanceAs file
        File(root, "page.jpg").readText() shouldBe "data"
        File(root, "page.tmp").exists() shouldBe false
    }

    @Test
    fun `Rename returning false copies to the new name and removes the original`() {
        val file = spyk(createFile("page.tmp", "data"))
        every { file.renameTo(any()) } returns false

        val result = file.renameToOrCopy("page.jpg")

        result.shouldNotBeNull().name shouldBe "page.jpg"
        File(root, "page.jpg").readText() shouldBe "data"
        File(root, "page.tmp").exists() shouldBe false
    }

    @Test
    fun `Rename throwing IllegalStateException copies to the new name and removes the original`() {
        val file = spyk(createFile("page.tmp", "data"))
        every { file.renameTo(any()) } throws IllegalStateException("rename not supported")

        val result = file.renameToOrCopy("page.jpg")

        result.shouldNotBeNull().name shouldBe "page.jpg"
        File(root, "page.jpg").readText() shouldBe "data"
        File(root, "page.tmp").exists() shouldBe false
    }

    @Test
    fun `Directory rename falls back to copying its contents`() {
        File(root, "chapter_tmp").mkdir()
        File(root, "chapter_tmp/001.jpg").writeText("one")
        File(root, "chapter_tmp/002.jpg").writeText("two")
        val dir = spyk(child("chapter_tmp"))
        every { dir.renameTo(any()) } throws IllegalStateException("rename not supported")

        val result = dir.renameToOrCopy("chapter")

        result.shouldNotBeNull().name shouldBe "chapter"
        File(root, "chapter/001.jpg").readText() shouldBe "one"
        File(root, "chapter/002.jpg").readText() shouldBe "two"
        File(root, "chapter_tmp").exists() shouldBe false
    }

    @Test
    fun `Failed file copy keeps the original and leaves no partial copy`() {
        val file = spyk(createFile("page.tmp", "data"))
        every { file.renameTo(any()) } returns false
        every { file.openInputStream() } throws IOException("read failed")

        val result = file.renameToOrCopy("page.jpg")

        result.shouldBeNull()
        File(root, "page.tmp").readText() shouldBe "data"
        File(root, "page.jpg").exists() shouldBe false
    }

    @Test
    fun `Failed directory copy keeps the original and leaves no partial copy`() {
        File(root, "chapter_tmp").mkdir()
        File(root, "chapter_tmp/001.jpg").writeText("one")
        File(root, "chapter_tmp/002.jpg").writeText("two")
        val dir = spyk(child("chapter_tmp"))
        val children = dir.listFiles()!!.sortedBy { it.name }
        val failingChild = spyk(children[1])
        every { failingChild.openInputStream() } throws IOException("read failed")
        every { dir.renameTo(any()) } returns false
        every { dir.listFiles() } returns arrayOf(children[0], failingChild)

        val result = dir.renameToOrCopy("chapter")

        result.shouldBeNull()
        File(root, "chapter_tmp/001.jpg").readText() shouldBe "one"
        File(root, "chapter_tmp/002.jpg").readText() shouldBe "two"
        File(root, "chapter").exists() shouldBe false
    }

    @Test
    fun `Existing file at the new name is not overwritten`() {
        val file = spyk(createFile("page.tmp", "new"))
        createFile("page.jpg", "existing")
        every { file.renameTo(any()) } returns false

        val result = file.renameToOrCopy("page.jpg")

        result.shouldBeNull()
        File(root, "page.tmp").readText() shouldBe "new"
        File(root, "page.jpg").readText() shouldBe "existing"
    }

    private fun createFile(name: String, content: String): UniFile {
        File(root, name).writeText(content)
        return child(name)
    }

    // Look files up through the root so they have a parent, as they do in the downloaders.
    private fun child(name: String): UniFile = UniFile.fromFile(root)!!.findFile(name)!!
}
