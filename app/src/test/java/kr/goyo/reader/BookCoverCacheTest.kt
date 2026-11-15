package kr.goyo.reader

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kr.goyo.reader.data.BookCoverCache
import kr.goyo.reader.data.LibraryRepository
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.BookRecord
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookCoverCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun comicCoverUsesTheNaturallySortedFirstImageAndBoundsItsPixels() = runBlocking {
        val file = comic("pages.cbz", damagedSecond = false)
        val cover = requireNotNull(BookCoverCache(context()).coverFor(book(file, BookKind.COMIC), file))
        assertEquals(240, cover.width); assertEquals(320, cover.height)
        assertEquals(Color.GREEN, cover.getPixel(120, 160))
    }
    @Test fun comicCoverDoesNotDecodeLaterDamagedImages() = runBlocking {
        val file = comic("damaged.cbz", damagedSecond = true)
        val cover = requireNotNull(BookCoverCache(context()).coverFor(book(file, BookKind.COMIC), file))
        assertEquals(240, cover.width); assertEquals(320, cover.height)
        assertEquals(Color.GREEN, cover.getPixel(120, 160))
    }
    @Test fun diskCacheSurvivesANewLoaderAndHistoryRemovalDeletesItWithoutRecyclingVisibleCovers() = runBlocking {
        val context = context()
        val repository = LibraryRepository(context)
        val original = comic("original.cbz", damagedSecond = false)
        val book = repository.openFile(Uri.fromFile(original)).book
        val first = requireNotNull(repository.coverFor(book))
        val cachedFile = File(context.cacheDir, "book-covers/${book.id}.png")
        assertTrue(cachedFile.isFile)
        val staged = repository.fileFor(book)
        assertTrue(staged.delete())
        val cached = requireNotNull(BookCoverCache(context).coverFor(book, staged))
        assertEquals(Color.GREEN, cached.getPixel(cached.width / 2, cached.height / 2))
        repository.removeBook(book)
        assertFalse(cachedFile.exists()); assertTrue(original.isFile)
        assertFalse(first.isRecycled); assertFalse(cached.isRecycled)
        assertNull(repository.coverFor(book))
    }
    @Test fun epubCoverUsesThePublicationCoverAndFitsTheThumbnailBounds() = runBlocking {
        val file = epubFile("cover.epub", Color.GREEN)
        val cover = requireNotNull(BookCoverCache(context()).coverFor(book(file, BookKind.EPUB), file))
        assertTrue(cover.width in 1..240); assertTrue(cover.height in 1..320)
        assertEquals(2.0 / 3.0, cover.width.toDouble() / cover.height, 0.01)
        assertEquals(Color.GREEN, cover.getPixel(cover.width / 2, cover.height / 2))
    }
    @Test fun textAndEpubWithoutACoverReturnNoImage() = runBlocking {
        val context = context()
        val cache = BookCoverCache(context)
        val text = temporary.newFile("book.txt").apply { writeText("표지가 없는 소설입니다.") }
        val epub = epubFile("no-cover.epub", null)
        assertNull(cache.coverFor(book(text, BookKind.TEXT), text))
        assertNull(cache.coverFor(book(epub, BookKind.EPUB), epub))
        assertFalse(File(context.cacheDir, "book-covers").exists())
    }
    @Test fun anUnreadableBookDoesNotPreventTheNextCoverFromLoading() = runBlocking {
        val cache = BookCoverCache(context())
        val broken = temporary.newFile("broken.cbz").apply { writeText("not ZIP") }
        assertNull(cache.coverFor(book(broken, BookKind.COMIC), broken))
        val image = temporary.newFile("valid.png").apply { writeBytes(imageBytes(120, 160, Color.BLUE)) }
        assertEquals(Color.BLUE, requireNotNull(cache.coverFor(book(image, BookKind.COMIC), image)).getPixel(60, 80))
    }
    private fun context(): Context {
        val files = temporary.newFolder(); val cache = temporary.newFolder()
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
            override fun getApplicationContext(): Context = this
        }
    }
    private fun book(file: File, kind: BookKind) = BookRecord(file.nameWithoutExtension, file.name,
        Uri.fromFile(file).toString(), kind, file.length(), 1L)
    private fun imageBytes(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray() }
        } finally { bitmap.recycle() }
    }
    private fun comic(name: String, damagedSecond: Boolean): File = temporary.newFile(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("page10.png"))
            zip.write(if (damagedSecond) "damaged".toByteArray() else imageBytes(600, 800, Color.BLUE)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("page2.png")); zip.write(imageBytes(600, 800, Color.GREEN)); zip.closeEntry()
        }
    }
    private fun epubFile(name: String, coverColor: Int?): File = temporary.newFile(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value
            }); zip.write(mime); zip.closeEntry()
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val cover = if (coverColor == null) "" else """<item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>"""
            entry("EPUB/book.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:min-viewer:cover</dc:identifier><dc:title>표지 테스트</dc:title><dc:language>ko</dc:language><meta property="dcterms:modified">2026-11-15T00:00:00Z</meta></metadata><manifest>$cover<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""")
            entry("EPUB/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>목차</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">첫째 장</a></li></ol></nav></body></html>""")
            entry("EPUB/chapter.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" lang="ko"><head><title>첫째 장</title></head><body><p>민뷰어에서 책을 읽습니다.</p></body></html>""")
            if (coverColor != null) { zip.putNextEntry(ZipEntry("EPUB/cover.png")); zip.write(imageBytes(600, 900, coverColor)); zip.closeEntry() }
        }
    }
}
