package kr.goyo.reader

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kr.goyo.reader.data.ImageGroup
import kr.goyo.reader.data.ImportPlan
import kr.goyo.reader.data.LibraryRepository
import kr.goyo.reader.data.LibrarySerializer
import kr.goyo.reader.data.SourceDocument
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.BookRecord
import kr.goyo.reader.model.Bookmark
import kr.goyo.reader.model.LibraryState
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingPosition
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun mimeIdentifiedArchivesReceiveAnExtensionTheReaderRecognizes() {
        val archiveKind = LibraryRepository.kindFor("download.bin", "application/zip")
        assertEquals(BookKind.COMIC, archiveKind)
        assertEquals("cbz", LibraryRepository.storageExtension("download.bin", requireNotNull(archiveKind)))
        assertEquals(BookKind.COMIC, LibraryRepository.kindFor("만화.CBZ"))
        assertEquals("png", LibraryRepository.storageExtension("한 장.PNG", BookKind.COMIC))
        assertEquals("pdf", LibraryRepository.storageExtension("document", BookKind.PDF))
        assertNull(LibraryRepository.kindFor("archive.rar", "application/x-rar-compressed"))
    }

    @Test
    fun metadataRoundTripPreservesUnicodeBookmarksAndPreciseLocations() = runBlocking {
        val position = ReadingPosition(
            index = 917,
            offset = 0.375f,
            locator = """{"href":"본문/제1장.xhtml","text":{"highlight":"기억 \"한 줄\" 🎐"}}""",
            progress = 0.625f,
        )
        val original = LibraryState(
            books = listOf(BookRecord("book-1", "고요한 밤 🎐", "book-1.epub", BookKind.EPUB, 123456L, 1L, 2L, position)),
            bookmarks = listOf(Bookmark("mark-1", "book-1", "다시 읽을 문장\n둘째 줄", position, 3L)),
            settings = ReaderSettings(fontSizeSp = 23f, marginDp = 19f, backgroundColor = 0xFF172321, textEncoding = "MS949"),
        )
        val output = ByteArrayOutputStream()
        LibrarySerializer.writeTo(original, output)
        val restored = LibrarySerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original, restored)
    }

    @Test
    fun importAndShelfRemovalPreserveTheSourceAndFlushOutlivesItsCaller() = runBlocking {
        val repository = repository()
        val source = temporary.newFile("원본 소설.txt").apply { writeText("첫 문장 🎐\n다음 문장", Charsets.UTF_8) }
        val originalBytes = source.readBytes()
        val plan = ImportPlan(
            documents = listOf(SourceDocument(Uri.fromFile(source), source.name, source.length(), BookKind.TEXT)),
            imageGroups = emptyList(),
        )
        val book = repository.import(plan) { _, _, _ -> }.single()
        val ownedCopy = repository.fileFor(book)
        assertTrue(ownedCopy.canonicalPath != source.canonicalPath)
        assertArrayEquals(originalBytes, ownedCopy.readBytes())
        assertArrayEquals(originalBytes, source.readBytes())

        val lastPosition = ReadingPosition(index = 7, offset = 0.25f, progress = 0.5f)
        launch {
            repository.flushPosition(book.id, lastPosition) { throw AssertionError("Flush failed", it) }
            cancel()
        }.join()
        val saved = withTimeout(5_000) {
            repository.state.first { it.books.singleOrNull()?.position == lastPosition }
        }
        assertEquals(lastPosition, saved.books.single().position)

        repository.addBookmark(book.id, "기억할 곳", lastPosition)
        repository.removeBook(book)
        val afterRemoval = repository.state.first()
        assertTrue(afterRemoval.books.isEmpty())
        assertTrue(afterRemoval.bookmarks.isEmpty())
        assertFalse(ownedCopy.exists())
        assertArrayEquals(originalBytes, source.readBytes())
    }

    @Test
    fun imageGroupsKeepEveryInputWhenDisplayNamesCollide() = runBlocking {
        val repository = repository()
        val first = temporary.newFile("first-source").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val second = temporary.newFile("second-source").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val third = temporary.newFile("third-source").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val files = listOf(
            SourceDocument(Uri.fromFile(first), "image.png", first.length(), BookKind.COMIC),
            SourceDocument(Uri.fromFile(second), "000002-image.png", second.length(), BookKind.COMIC),
            SourceDocument(Uri.fromFile(third), "image.png", third.length(), BookKind.COMIC),
        )
        val book = repository.import(ImportPlan(emptyList(), listOf(ImageGroup("겹치는 파일명", files)))) { _, _, _ -> }.single()
        ZipFile(repository.fileFor(book)).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(3, entries.size)
            assertEquals(3, entries.map { it.name }.toSet().size)
            val payloads = entries.map { entry -> zip.getInputStream(entry).use { it.readBytes().toList() } }.toSet()
            assertEquals(setOf(first.readBytes().toList(), second.readBytes().toList(), third.readBytes().toList()), payloads)
        }
        assertTrue(listOf(first, second, third).all { it.exists() })
    }

    private fun repository(): LibraryRepository {
        val appFiles = temporary.newFolder()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = appFiles
            override fun getApplicationContext(): Context = this
        }
        return LibraryRepository(context)
    }
}
