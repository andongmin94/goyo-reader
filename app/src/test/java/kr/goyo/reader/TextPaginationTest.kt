package kr.goyo.reader

import android.graphics.Typeface
import kr.goyo.reader.reader.text.TextDocument
import kr.goyo.reader.reader.text.TextPageLayouts
import kr.goyo.reader.reader.text.TextTypography
import kr.goyo.reader.reader.text.createTextLayout
import kr.goyo.reader.reader.text.paginateText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextPaginationTest {
    @Test
    fun measuredPagesFitViewportWithoutDroppingUnicodeText() = runBlocking {
        val text = "바람이 부는 저녁이었다. 창문 너머로 작은 풍경 🎐 소리가 들렸다.\n\n".repeat(350)
        val typography = TextTypography(340, 580, 22f, 1.6f, Typeface.DEFAULT)
        val pages = paginateText(TextDocument(text, "UTF-8"), typography)
        val layouts = TextPageLayouts(pages, android.graphics.Color.BLACK)
        assertEquals(0, pages.starts.first())
        assertTrue(pages.size > 10)
        assertEquals(text, (0 until pages.size).joinToString("") { text.substring(pages.starts[it], pages.end(it)) })
        for (page in 0 until pages.size) {
            assertTrue(
                "page $page/${pages.size} overflows: ${layouts.get(page).height} > ${typography.heightPx}, " +
                    "offsets ${pages.starts[page]}..${pages.end(page)}, " +
                    "text=${pages.text(page).replace("\n", "\\n")}",
                layouts.get(page).height <= typography.heightPx,
            )
            val start = pages.starts[page]
            assertTrue(start == 0 || !text[start].isLowSurrogate())
        }
    }

    @Test
    fun pageBoundariesDoNotInsertBlankLinesInContinuousMode() = runBlocking {
        val text = "first line\nsecond line\n\nthird line\nfourth line\n".repeat(8)
        val typography = TextTypography(250, 75, 20f, 1.5f, Typeface.MONOSPACE)
        val pages = paginateText(TextDocument(text, "UTF-8"), typography)
        val layouts = TextPageLayouts(pages, android.graphics.Color.BLACK)
        val fullLayout = createTextLayout(text, typography.paint(), typography.widthPx, typography.lineSpacing)
        assertEquals(fullLayout.lineCount, (0 until pages.size).sumOf { layouts.get(it).lineCount })
    }

    @Test
    fun fontChangeKeepsSavedSentenceWithinSelectedPage() = runBlocking {
        val text = "새로운 장의 시작입니다. 같은 문장을 계속 읽을 수 있어야 합니다. ".repeat(200)
        val document = TextDocument(text, "UTF-8")
        val small = paginateText(document, TextTypography(350, 600, 18f, 1.4f, Typeface.DEFAULT))
        val large = paginateText(document, TextTypography(350, 600, 30f, 1.8f, Typeface.DEFAULT))
        val saved = small.starts[small.size / 2] + 10
        val restored = large.pageFor(saved)
        assertTrue(large.starts[restored] <= saved)
        assertTrue(large.end(restored) > saved)
        assertTrue(large.size > small.size)
    }
}
