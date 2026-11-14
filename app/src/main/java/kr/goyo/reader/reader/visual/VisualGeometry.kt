package kr.goyo.reader.reader.visual

import kotlin.math.ceil
import kotlin.math.max

data class PageDimensions(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
    val aspectRatio: Float get() = width.toFloat() / height
}

/** A scroll item is a bounded vertical part of an original page, not another book page. */
data class PageSlice(val page: Int, val top: Float, val bottom: Float) {
    val fraction: Float get() = bottom - top
}

fun buildPageSlices(
    dimensions: List<PageDimensions>,
    viewportWidth: Int,
    viewportHeight: Int,
): List<PageSlice> = buildList {
    val tileHeight = max(1, viewportHeight)
    dimensions.forEachIndexed { page, size ->
        val displayHeight = max(1, viewportWidth).toDouble() * size.height / size.width
        val count = max(1, ceil(displayHeight / tileHeight).toInt())
        repeat(count) { tile ->
            add(PageSlice(page, tile.toFloat() / count, (tile + 1f) / count))
        }
    }
}

fun sliceAtPosition(slices: List<PageSlice>, page: Int, fraction: Float): Int {
    if (slices.isEmpty()) return 0
    val targetPage = page.coerceIn(0, slices.last().page)
    val targetOffset = fraction.coerceIn(0f, 1f)
    return slices.indexOfLast { it.page < targetPage || (it.page == targetPage && it.top <= targetOffset) }
        .coerceAtLeast(0)
}

/** Natural order without converting digit runs to a fixed-width integer. */
object NaturalFileOrder : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var a = 0
        var b = 0
        while (a < left.length && b < right.length) {
            if (left[a] in '0'..'9' && right[b] in '0'..'9') {
                val startA = a
                val startB = b
                while (a < left.length && left[a] in '0'..'9') a++
                while (b < right.length && right[b] in '0'..'9') b++
                var significantA = startA
                var significantB = startB
                while (significantA < a - 1 && left[significantA] == '0') significantA++
                while (significantB < b - 1 && right[significantB] == '0') significantB++
                val lengthResult = (a - significantA).compareTo(b - significantB)
                if (lengthResult != 0) return lengthResult
                for (offset in 0 until a - significantA) {
                    val digitResult = left[significantA + offset].compareTo(right[significantB + offset])
                    if (digitResult != 0) return digitResult
                }
                val zeroResult = (a - startA).compareTo(b - startB)
                if (zeroResult != 0) return zeroResult
            } else {
                val result = left[a].lowercaseChar().compareTo(right[b].lowercaseChar())
                if (result != 0) return result
                a++
                b++
            }
        }
        val remaining = (left.length - a).compareTo(right.length - b)
        return if (remaining != 0) remaining else left.compareTo(right)
    }
}

/** ARGB decoded pixels are bounded independently of the archive's compressed byte size. */
fun imageSampleSize(width: Int, height: Int, requestedWidth: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= requestedWidth.coerceAtLeast(1)) sample *= 2
    while ((width / sample).toLong() * (height / sample) > 6_000_000L ||
        width / sample > 8192 || height / sample > 8192
    ) sample *= 2
    return sample
}
