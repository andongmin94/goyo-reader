package kr.goyo.reader.reader.text

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.LruCache
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.ensureActive

data class TextTypography(
    val widthPx: Int,
    val heightPx: Int,
    val fontSizePx: Float,
    val lineSpacing: Float,
    val typeface: Typeface,
) {
    fun paint(color: Int = android.graphics.Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.textSize = fontSizePx
        it.typeface = typeface
        it.color = color
        it.isSubpixelText = true
    }
}

fun createTextLayout(text: String, paint: TextPaint, widthPx: Int, lineSpacing: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(false)
        .setLineSpacing(0f, lineSpacing)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

class TextPagination(val document: TextDocument, val starts: IntArray, val typography: TextTypography) {
    val size: Int get() = starts.size
    fun pageFor(offset: Int) = pageForOffset(starts, offset.coerceIn(0, document.text.length))
    fun end(page: Int) = starts.getOrNull(page + 1) ?: document.text.length
    fun text(page: Int): String = pageText(document.text, starts[page], end(page))
}

private fun pageText(text: String, start: Int, end: Int): String {
    val content = text.substring(start, end)
    // An intermediate page's terminating newline belongs to the next page's first
    // baseline. The document's final newline, including its last blank line, is retained.
    return if (end < text.length) content.removeSuffix("\n") else content
}

/** Measure bounded windows and keep only page offsets, never one layout for the whole novel. */
suspend fun paginateText(document: TextDocument, typography: TextTypography): TextPagination {
    val text = document.text
    val starts = ArrayList<Int>()
    val paint = typography.paint()
    var cursor = 0
    var windowSize = max(8192, (
        typography.widthPx / max(typography.fontSizePx * 0.25f, 1f) *
            typography.heightPx / max(typography.fontSizePx * 0.75f, 1f) * 2
        ).toInt())
    while (cursor < text.length) {
        coroutineContext.ensureActive()
        val end = safeTextOffset(text, minOf(cursor.toLong() + windowSize, text.length.toLong()).toInt())
        val window = text.substring(cursor, end)
        val layout = createTextLayout(window, paint, typography.widthPx, typography.lineSpacing)
        var firstLine = 0
        while (firstLine < layout.lineCount) {
            coroutineContext.ensureActive()
            val top = layout.getLineTop(firstLine)
            var afterLine = firstLine
            while (afterLine < layout.lineCount &&
                layout.getLineBottom(afterLine) - top <= typography.heightPx
            ) afterLine++
            if (afterLine == firstLine) afterLine++
            // A truncated window's last line may wrap differently once more text is present.
            // Carry its whole unfinished page into the next measurement window.
            if (afterLine == layout.lineCount && end < text.length) break
            val pageStart = cursor + layout.getLineStart(firstLine)
            if (pageStart < text.length && (starts.isEmpty() || starts.last() != pageStart)) {
                // Standalone pages have their own first/last font metrics. In particular,
                // a final newline and mixed emoji/Korean fonts can make this layout taller
                // than the same lines measured inside a larger window. Decide the break
                // using exactly the text and layout that the reader will display.
                while (afterLine > firstLine + 1) {
                    val pageEnd = cursor + if (afterLine < layout.lineCount) {
                        layout.getLineStart(afterLine)
                    } else window.length
                    val displayed = createTextLayout(
                        pageText(text, pageStart, pageEnd), paint, typography.widthPx, typography.lineSpacing,
                    )
                    if (displayed.height <= typography.heightPx) break
                    afterLine--
                }
                starts += pageStart
            }
            firstLine = afterLine
        }
        if (end == text.length) break
        if (firstLine == 0) {
            windowSize = minOf(windowSize.toLong() * 2, text.length.toLong()).toInt()
        } else {
            cursor += layout.getLineStart(firstLine)
        }
    }
    if (starts.isEmpty()) starts += 0
    return TextPagination(document, starts.toIntArray(), typography)
}

/** Only visible and nearby pages retain native layouts. Access on the UI thread. */
class TextPageLayouts(private val pagination: TextPagination, color: Int) {
    private val paint = pagination.typography.paint(color)
    private val cache = LruCache<Int, StaticLayout>(6)

    fun get(page: Int): StaticLayout = cache.get(page) ?: createTextLayout(
        pagination.text(page), paint, pagination.typography.widthPx, pagination.typography.lineSpacing,
    ).also { cache.put(page, it) }

    fun scrollHeight(page: Int): Int {
        val layout = get(page)
        if (page == pagination.size - 1) return layout.height
        val normalHeight = if (layout.lineCount > 1) layout.getLineTop(1) - layout.getLineTop(0)
        else (paint.fontSpacing * pagination.typography.lineSpacing).toInt()
        val last = layout.lineCount - 1
        return layout.height + (normalHeight - (layout.getLineBottom(last) - layout.getLineTop(last))).coerceAtLeast(0)
    }
}
