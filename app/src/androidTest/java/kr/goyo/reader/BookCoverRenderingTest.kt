package kr.goyo.reader

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kr.goyo.reader.data.BookCoverCache
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.BookRecord
import kr.goyo.reader.reader.PdfPageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Robolectric 4.16.1 does not implement native PDF or region-decoder pixels. */
@RunWith(AndroidJUnit4::class)
class BookCoverRenderingTest {
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)

    @Test
    fun longComicCoverShowsTheTopInsteadOfShrinkingTheWholeStrip() = runBlocking {
        val context = context()
        val file = temporary.newFile("long.png")
        val bitmap = Bitmap.createBitmap(480, 2400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            Canvas(bitmap).drawRect(0f, 0f, 480f, 800f, Paint().apply { color = Color.RED })
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }

        val cover = requireNotNull(BookCoverCache(context).coverFor(book(file, BookKind.COMIC), file))

        assertTrue(cover.width in 1..240)
        assertEquals(320, cover.height)
        assertEquals(0.75, cover.width.toDouble() / cover.height, 0.01)
        assertEquals(Color.RED, cover.getPixel(cover.width / 2, 0))
        assertEquals(Color.RED, cover.getPixel(cover.width / 2, 319))
    }

    @Test
    fun pdfCoverUsesTheEntireFirstPageAndPreservesItsAspectRatio() = runBlocking {
        val context = context()
        val file = temporary.newFile("pages.pdf")
        val document = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 300, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }

        val cover = requireNotNull(BookCoverCache(context).coverFor(book(file, BookKind.PDF), file))

        assertEquals(240, cover.width)
        assertEquals(120, cover.height)
        assertEquals(Color.RED, cover.getPixel(120, 60))

        PdfPageSource(file).use { pages ->
            assertEquals(2, pages.dimensions.size)
            val secondPage = pages.render(1, 240, 0f, 1f)
            assertEquals(Color.BLUE, secondPage.getPixel(120, 60))
        }
    }

    private fun context(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getCacheDir(): File = temporary.root
        override fun getApplicationContext(): Context = this
    }

    private fun book(file: File, kind: BookKind) = BookRecord(
        id = file.nameWithoutExtension,
        title = file.nameWithoutExtension,
        fileName = file.name,
        kind = kind,
        sizeBytes = file.length(),
        addedAt = 1L,
    )
}
