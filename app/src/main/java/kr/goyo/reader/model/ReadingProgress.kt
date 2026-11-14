package kr.goyo.reader.model

import kotlin.math.roundToInt

/** Page readers use 0% at the first page and 100% at the last page. */
internal fun pageProgress(pageIndex: Int, pageCount: Int): Float =
    if (pageCount == 1) 1f else pageIndex.toFloat() / (pageCount - 1)

internal fun pageAtProgress(progress: Float, pageCount: Int): Int =
    (progress.coerceIn(0f, 1f) * (pageCount - 1)).roundToInt()
