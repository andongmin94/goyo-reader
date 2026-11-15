package kr.goyo.reader

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kr.goyo.reader.data.*
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.ReaderInput
import kr.goyo.reader.reader.visual.ImagePageSource
import kr.goyo.reader.reader.visual.PageDimensions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun knownExtensionsTakePrecedenceOverIncorrectMimeTypes() {
        assertEquals(BookKind.COMIC, fileKind("download.bin", "application/zip"))
        assertEquals(BookKind.COMIC, fileKind("만화.CBZ"))
        assertEquals(BookKind.PDF, fileKind("document", "application/pdf"))
        assertEquals(BookKind.TEXT, fileKind("문서.txt", "image/png"))
        assertFalse(FileEntry("content://test/1", "문서.txt", false, mime = "image/png").image)
        assertFalse(FileEntry("content://test/1", "만화.zip", false, mime = "image/png").image)
        assertTrue(FileEntry("content://test/1", "사진", false, mime = "image/png").image)
        assertNull(fileKind("archive.rar", "application/x-rar-compressed"))
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mimeIdentifiedImagesOpenDirectlyWithoutCreatingAnArchive() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val formats = listOf("image/jpeg" to Bitmap.CompressFormat.JPEG,
            "image/png" to Bitmap.CompressFormat.PNG, "image/webp" to Bitmap.CompressFormat.WEBP_LOSSLESS)
        val docs = formats.mapIndexed { i, (mime, format) -> ProvidedDocument("$i", "root", "사진 $i", mime, imageFile("img$i", format, 3, 5)) }
        provide(docs)
        docs.forEach { document ->
            val opened = repository.openFile(DocumentsContract.buildDocumentUri(AUTHORITY, document.id))
            val input = opened.input as ReaderInput.Images
            assertEquals(BookKind.COMIC, opened.book.kind)
            assertFalse(repository.fileFor(opened.book).exists())
            ImagePageSource(context, input.entries).use { assertEquals(listOf(PageDimensions(3, 5)), it.dimensions) }
            assertTrue(requireNotNull(document.file).exists())
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun contentTreeListsOneDirectoryAndReadsItsImagesInNaturalOrder() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val text = temporary.newFile("text").apply { writeText("문서 본문") }
        val documents = listOf(
            ProvidedDocument("10", "root", "page 10", "image/jpeg", imageFile("ten", Bitmap.CompressFormat.JPEG, 7, 9)),
            ProvidedDocument("2", "root", "page 2.PNG", "application/octet-stream", imageFile("two", Bitmap.CompressFormat.PNG, 4, 6)),
            ProvidedDocument("txt", "root", "문서.txt", "image/png", text),
            ProvidedDocument("nested", "root", "하위 폴더", DocumentsContract.Document.MIME_TYPE_DIR, null),
            ProvidedDocument("hidden", "nested", "하위 문서.txt", "text/plain", text),
        )
        val provider = provide(documents)
        val root = repository.directories.selectedFolder(DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root"))
        val entries = repository.browse(listOf(root))
        assertEquals(4, entries.size)
        assertFalse(entries.any { it.name == "하위 문서.txt" })
        assertEquals(listOf("root"), provider.listedParents)
        assertEquals(0, provider.openedFiles)
        assertEquals("하위 폴더", orderedEntries(entries).first().name)
        val tenth = entries.single { it.name == "page 10" }
        val opened = repository.openFile(Uri.parse(tenth.uri), listOf(root))
        val input = opened.input as ReaderInput.Images
        assertEquals(listOf("page 2.PNG", "page 10"), input.entries.map { it.name })
        assertEquals(1, opened.book.position.index)
        assertTrue(opened.book.imageFolder)
        assertEquals(0, provider.openedFiles)
        ImagePageSource(context, input.entries).use {
            assertEquals(listOf(PageDimensions(4, 6), PageDimensions(7, 9)), it.dimensions)
        }
        assertFalse(File(context.cacheDir, "reader-documents").exists())
        val nested = entries.single { it.directory }.folder()
        assertEquals("하위 문서.txt", repository.browse(listOf(root, nested)).single().name)
    }
    @Test fun sortingKeepsFoldersFirstEvenInDescendingOrder() {
        val entries = listOf(FileEntry("10", "page10.png", false, 10), FileEntry("folder", "z", true), FileEntry("2", "page2.png", false, 2))
        assertEquals(listOf("z", "page2.png", "page10.png"), orderedEntries(entries).map { it.name })
        assertEquals(listOf("z", "page10.png", "page2.png"), orderedEntries(entries, sort = FileSort.SIZE, descending = true).map { it.name })
        assertEquals(listOf("page2.png"), orderedEntries(entries, query = "PAGE2").map { it.name })
    }
    @Test fun repeatedOpenRefreshesOnlyTheSelectedFileAndRetainsItsIdentityAndPosition() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val folder = temporary.newFolder("books")
        val source = File(folder, "소설.txt").apply { writeText("첫 문장") }
        File(folder, "열지 않은 파일.txt").writeText("이 파일은 복사하면 안 됩니다.")
        val location = FolderLocation(Uri.fromFile(folder).toString(), folder.name)
        repository.browse(listOf(location))
        assertFalse(File(context.cacheDir, "reader-documents").exists())
        val first = repository.openFile(Uri.fromFile(source), listOf(location))
        val position = ReadingPosition(index = 2, progress = 0.5f)
        repository.updatePosition(first.book.id, position)
        source.writeText("수정된 본문입니다.")
        val next = repository.openFile(Uri.fromFile(source), listOf(location))
        assertEquals(first.book.id, next.book.id)
        assertEquals("소설.txt", next.book.title)
        assertEquals(position, next.book.position)
        assertEquals(source.readText(), (next.input as ReaderInput.LocalFile).file.readText())
        assertEquals(1, repository.state.first().books.size)
        assertEquals(1, File(context.cacheDir, "reader-documents").listFiles()!!.size)
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun imageFolderResumeUsesTheImageUriAfterANewImageIsInserted() = runBlocking {
        val repository = LibraryRepository(context())
        val directory = temporary.newFolder("comic")
        fun image(name: String) = File(directory, name).apply { writeBytes(imageFile("source-$name", Bitmap.CompressFormat.PNG, 4, 6).readBytes()) }
        image("2.png")
        val ten = image("10.png")
        val path = listOf(FolderLocation(Uri.fromFile(directory).toString(), "comic"))
        val opened = repository.openFile(Uri.fromFile(ten), path)
        val input = opened.input as ReaderInput.Images
        val atTen = input.anchor(ReadingPosition(index = 1, offset = 0.25f))
        repository.updatePosition(opened.book.id, atTen)
        image("1.png")
        val restored = repository.openRecord(repository.state.first().books.single())
        assertEquals(2, restored.book.position.index)
        assertEquals(Uri.fromFile(ten).toString(), restored.book.position.locator)
        assertEquals(0.25f, restored.book.position.offset)
    }
    @Test fun metadataRoundTripPreservesUnicodeBookmarksFoldersAndPreciseLocations() = runBlocking {
        val position = ReadingPosition(917, 0.375f, """{"href":"본문/제1장.xhtml","text":{"highlight":"기억 \"한 줄\" 🎐"}}""", 0.625f)
        val path = listOf(FolderLocation("content://books/tree/문서", "문서 🎐"))
        val original = LibraryState(
            books = listOf(BookRecord("book-1", "고요한 밤 🎐.epub", "content://books/1", BookKind.EPUB, 123456L, 1L, 2L, position, path)),
            bookmarks = listOf(Bookmark("mark-1", "book-1", "다시 읽을 문장\n둘째 줄", position, 3L)),
            settings = ReaderSettings(fontSizeSp = 23f, marginDp = 19f, backgroundColor = 0xFF172321, textEncoding = "MS949"),
            folderPath = path, favoritePaths = listOf(path),
        )
        val output = ByteArrayOutputStream()
        LibrarySerializer.writeTo(original, output)
        assertEquals(original, LibrarySerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun forgettingAndFlushPreserveTheSourceAndFlushOutlivesItsCaller() = runBlocking {
        val repository = LibraryRepository(context())
        val source = temporary.newFile("원본.txt").apply { writeText("첫 문장 🎐\n다음 문장") }
        val original = source.readBytes()
        val opened = repository.openFile(Uri.fromFile(source))
        val staged = (opened.input as ReaderInput.LocalFile).file
        assertNotEquals(source.canonicalPath, staged.canonicalPath)
        assertArrayEquals(original, staged.readBytes())
        val position = ReadingPosition(7, 0.25f, progress = 0.5f)
        launch { repository.flushPosition(opened.book.id, position) { throw AssertionError(it) }; cancel() }.join()
        val saved = withTimeout(5_000) { repository.state.first { it.books.singleOrNull()?.position == position } }
        assertEquals(position, saved.books.single().position)
        repository.addBookmark(opened.book.id, "기억할 곳", position)
        repository.removeBook(opened.book)
        assertTrue(repository.state.first().books.isEmpty())
        assertTrue(repository.state.first().bookmarks.isEmpty())
        assertFalse(staged.exists())
        assertArrayEquals(original, source.readBytes())
    }
    @Test fun distinctUrisWithTheSameFilenameRemainDistinctDocuments() = runBlocking {
        val repository = LibraryRepository(context())
        val first = File(temporary.newFolder("a"), "same.txt").apply { writeText("first") }
        val second = File(temporary.newFolder("b"), "same.txt").apply { writeText("second") }
        val a = repository.openFile(Uri.fromFile(first))
        val b = repository.openFile(Uri.fromFile(second))
        assertNotEquals(a.book.id, b.book.id)
        assertEquals("first", (a.input as ReaderInput.LocalFile).file.readText())
        assertEquals("second", (b.input as ReaderInput.LocalFile).file.readText())
        assertEquals(2, repository.state.first().books.size)
    }
    @Test fun favoriteToggleAndFolderStateDoNotCopyFiles() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val path = listOf(FolderLocation("content://provider/tree/1/document/1", "Books"))
        repository.saveFolder(path)
        repository.toggleFavorite(path)
        assertEquals(path, repository.state.first().folderPath)
        assertEquals(listOf(path), repository.state.first().favoritePaths)
        repository.toggleFavorite(path)
        assertTrue(repository.state.first().favoritePaths.isEmpty())
        assertFalse(File(context.cacheDir, "reader-documents").exists())
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
    private fun imageFile(name: String, format: Bitmap.CompressFormat, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return temporary.newFile(name).apply {
            try { outputStream().use { assertTrue(bitmap.compress(format, 100, it)) } }
            finally { bitmap.recycle() }
        }
    }
    private data class ProvidedDocument(val id: String, val parent: String, val name: String, val mime: String, val file: File?)
    private fun provide(documents: List<ProvidedDocument>): TestProvider {
        val provider = TestProvider(documents)
        provider.attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().apply { authority = AUTHORITY })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        return provider
    }
    private class TestProvider(private val documents: List<ProvidedDocument>) : ContentProvider() {
        val listedParents = mutableListOf<String>()
        var openedFiles = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val id = DocumentsContract.getDocumentId(uri)
            val ids = if (uri.lastPathSegment == "children") {
                listedParents += id
                documents.filter { it.parent == id }.map { it.id }
            } else listOf(id)
            return MatrixCursor(columns).apply { ids.forEach { documentId ->
                val document = documents.firstOrNull { it.id == documentId }
                addRow(columns.map { column -> when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                    OpenableColumns.DISPLAY_NAME -> document?.name ?: "이미지 폴더"
                    OpenableColumns.SIZE -> document?.file?.length() ?: 0L
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> document?.mime ?: DocumentsContract.Document.MIME_TYPE_DIR
                    else -> null
                } })
            } }
        }
        override fun getType(uri: Uri) = documents.firstOrNull { it.id == DocumentsContract.getDocumentId(uri) }?.mime ?: DocumentsContract.Document.MIME_TYPE_DIR
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openedFiles++
            check(mode == "r")
            return ParcelFileDescriptor.open(requireNotNull(documents.single { it.id == DocumentsContract.getDocumentId(uri) }.file), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    }
    private companion object { const val AUTHORITY = "kr.goyo.reader.test.documents" }
}
