package kr.goyo.reader

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.dataStoreFile
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.goyo.reader.data.LibraryRepository
import kr.goyo.reader.data.LibrarySerializer
import kr.goyo.reader.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingResumeRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun activeBookAndPositionAreWrittenToDiskNotOnlyActivityState() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val file = temporary.newFile("소설.txt").apply { writeText("읽던 문장을 기억합니다.\n".repeat(100)) }
        val book = repository.openFile(Uri.fromFile(file)).book
        val position = ReadingPosition(index = 321, offset = 0.25f, progress = 0.4f)
        repository.updatePosition(book.id, position)
        val disk = context.dataStoreFile("documents.json").inputStream().use { LibrarySerializer.readFrom(it) }
        assertEquals(book.id, disk.openBookId)
        assertEquals(position, disk.books.single().position)
        assertEquals(position, repository.openRecord(disk.books.single()).book.position)
    }

    @Test fun returningToDirectoryClearsAutoOpenButKeepsPositionAndBookmarks() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val file = temporary.newFile("소설.txt").apply { writeText("본문".repeat(100)) }
        val book = repository.openFile(Uri.fromFile(file)).book
        val final = ReadingPosition(index = 73, progress = 0.5f)
        repository.addBookmark(book.id, "다시 읽기", final)
        repository.closeReading(book.id, final) { throw AssertionError(it) }.join()
        val disk = context.dataStoreFile("documents.json").inputStream().use { LibrarySerializer.readFrom(it) }
        assertNull(disk.openBookId)
        assertEquals(final, disk.books.single().position)
        assertEquals("다시 읽기", disk.bookmarks.single().title)
        assertTrue(file.isFile)
        assertEquals(final, repository.openFile(Uri.fromFile(file)).book.position)
    }

    @Test fun closingAnOlderBookCannotClearTheNewlyOpenedBook() = runBlocking {
        val repository = LibraryRepository(context())
        val first = repository.openFile(Uri.fromFile(temporary.newFile("A.txt").apply { writeText("A".repeat(100)) })).book
        val second = repository.openFile(Uri.fromFile(temporary.newFile("B.txt").apply { writeText("B".repeat(100)) })).book
        val position = ReadingPosition(index = 42)
        repository.closeReading(first.id, position) { throw AssertionError(it) }.join()
        val state = repository.state.first()
        assertEquals(second.id, state.openBookId)
        assertEquals(position, state.books.single { it.id == first.id }.position)
    }

    @Test fun forgettingTheActiveRecordRemovesTheResumeTargetNotTheOriginal() = runBlocking {
        val repository = LibraryRepository(context())
        val file = temporary.newFile("소설.txt").apply { writeText("원본은 유지합니다.") }
        val book = repository.openFile(Uri.fromFile(file)).book
        repository.removeBook(book)
        val state = repository.state.first()
        assertNull(state.openBookId)
        assertTrue(state.books.isEmpty())
        assertTrue(file.isFile)
    }

    private fun context(): Context {
        val files = temporary.newFolder()
        val cache = temporary.newFolder()
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
            override fun getApplicationContext(): Context = this
        }
    }
}
