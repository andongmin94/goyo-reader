package kr.goyo.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentProvider
import android.content.ContentValues
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
import kr.goyo.reader.reader.visual.ImagePageSource
import kr.goyo.reader.reader.visual.PageDimensions
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun mimeIdentifiedArchivesReceiveAnExtensionTheReaderRecognizes() {
        val archiveKind = LibraryRepository.kindFor("download.bin", "application/zip")
        assertEquals(BookKind.COMIC, archiveKind)
        assertEquals("cbz", LibraryRepository.storageExtension("download.bin", requireNotNull(archiveKind), "application/zip"))
        assertEquals(BookKind.COMIC, LibraryRepository.kindFor("만화.CBZ"))
        assertEquals("png", LibraryRepository.storageExtension("한 장.PNG", BookKind.COMIC, null))
        assertEquals("pdf", LibraryRepository.storageExtension("document", BookKind.PDF, "application/pdf"))
        assertEquals("cbz", LibraryRepository.storageExtension("만화.zip", BookKind.COMIC, "image/png"))
        assertNull(LibraryRepository.kindFor("archive.rar", "application/x-rar-compressed"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mimeIdentifiedImageFilesCanBeImportedAndOpened() = runBlocking {
        val repository = repository()
        val formats = listOf(
            Triple("image/jpeg", Bitmap.CompressFormat.JPEG, "jpg"),
            Triple("image/png", Bitmap.CompressFormat.PNG, "png"),
            Triple("image/webp", Bitmap.CompressFormat.WEBP_LOSSLESS, "webp"),
        )
        val originals = formats.mapIndexed { index, (_, format, _) -> imageFile("source-$index", format, 3, 5) }
        val uris = provideDocuments(*formats.mapIndexed { index, (mime, _, _) ->
            ProvidedDocument("사진 ${index + 1}", mime, originals[index])
        }.toTypedArray())

        uris.forEachIndexed { index, uri ->
            val plan = repository.planFiles(listOf(uri))
            val book = repository.import(plan) { _, _, _ -> }.single()
            val copy = repository.fileFor(book)
            assertEquals(BookKind.COMIC, book.kind)
            assertEquals(formats[index].third, copy.extension)
            assertArrayEquals(originals[index].readBytes(), copy.readBytes())
            ImagePageSource(copy).use { pages ->
                assertEquals(listOf(PageDimensions(3, 5)), pages.dimensions)
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mimeIdentifiedImagesAreGroupedIntoAReadableComic() = runBlocking {
        val repository = repository()
        val uris = provideDocuments(
            ProvidedDocument("page 10", "image/jpeg", imageFile("tenth", Bitmap.CompressFormat.JPEG, 7, 9)),
            ProvidedDocument("page 2", "image/png", imageFile("second", Bitmap.CompressFormat.PNG, 4, 6)),
        )
        val plan = repository.planFiles(uris)
        assertTrue(plan.documents.isEmpty())
        assertEquals(1, plan.imageGroups.size)
        assertEquals(2, plan.imageGroups.single().files.size)

        val book = repository.import(plan) { _, _, _ -> }.single()
        ImagePageSource(repository.fileFor(book)).use { pages ->
            assertEquals(listOf("00000000.png", "00000001.jpg"), pages.labels)
            assertEquals(listOf(PageDimensions(4, 6), PageDimensions(7, 9)), pages.dimensions)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun folderImportIncludesMimeIdentifiedImagesAndKeepsKnownExtensions() = runBlocking {
        val repository = repository()
        val text = temporary.newFile("text-source").apply { writeText("문서 본문") }
        provideDocuments(
            ProvidedDocument("page 1", "image/png", imageFile("first", Bitmap.CompressFormat.PNG, 3, 5)),
            ProvidedDocument("page 2.PNG", "application/octet-stream", imageFile("second", Bitmap.CompressFormat.PNG, 4, 6)),
            ProvidedDocument("문서.txt", "image/png", text),
        )
        val plan = repository.planFolder(DocumentsContract.buildTreeDocumentUri(DOCUMENT_AUTHORITY, "root"))
        assertEquals(2, plan.bookCount)
        assertEquals(BookKind.TEXT, plan.documents.single().kind)
        assertEquals(2, plan.imageGroups.single().files.size)

        val books = repository.import(plan) { _, _, _ -> }
        assertEquals("txt", repository.fileFor(books.single { it.kind == BookKind.TEXT }).extension)
        val comic = books.single { it.kind == BookKind.COMIC }
        ImagePageSource(repository.fileFor(comic)).use { pages ->
            assertEquals(listOf(PageDimensions(3, 5), PageDimensions(4, 6)), pages.dimensions)
        }
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
            documents = listOf(SourceDocument(Uri.fromFile(source), source.name, source.length(), BookKind.TEXT, "txt")),
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
            SourceDocument(Uri.fromFile(first), "image.png", first.length(), BookKind.COMIC, "png"),
            SourceDocument(Uri.fromFile(second), "000002-image.png", second.length(), BookKind.COMIC, "png"),
            SourceDocument(Uri.fromFile(third), "image.png", third.length(), BookKind.COMIC, "png"),
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

    private fun imageFile(name: String, format: Bitmap.CompressFormat, width: Int, height: Int): File {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return temporary.newFile(name).apply {
            outputStream().use { assertTrue(image.compress(format, 100, it)) }
            image.recycle()
        }
    }

    private fun provideDocuments(vararg documents: ProvidedDocument): List<Uri> {
        val provider = TestDocumentProvider(documents.toList())
        provider.attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().apply { authority = DOCUMENT_AUTHORITY })
        ShadowContentResolver.registerProviderInternal(DOCUMENT_AUTHORITY, provider)
        return documents.indices.map { DocumentsContract.buildDocumentUri(DOCUMENT_AUTHORITY, it.toString()) }
    }

    private data class ProvidedDocument(val name: String, val mime: String, val file: File)

    private class TestDocumentProvider(private val documents: List<ProvidedDocument>) : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val ids = if (uri.lastPathSegment == "children") documents.indices.map { it.toString() }
                else listOf(DocumentsContract.getDocumentId(uri))
            return MatrixCursor(columns).apply {
                ids.forEach { id ->
                    val document = if (id == "root") null else documents[id.toInt()]
                    addRow(columns.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                            OpenableColumns.DISPLAY_NAME -> document?.name ?: "이미지 폴더"
                            OpenableColumns.SIZE -> document?.file?.length() ?: 0L
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> document?.mime ?: DocumentsContract.Document.MIME_TYPE_DIR
                            else -> null
                        }
                    })
                }
            }
        }

        override fun getType(uri: Uri): String = documents[DocumentsContract.getDocumentId(uri).toInt()].mime

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(documents[DocumentsContract.getDocumentId(uri).toInt()].file, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    }

    private companion object {
        const val DOCUMENT_AUTHORITY = "kr.goyo.reader.test.documents"
    }
}
