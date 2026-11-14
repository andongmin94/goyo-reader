package kr.goyo.reader

import kr.goyo.reader.model.pageAtProgress
import kr.goyo.reader.model.pageProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressTest {
    @Test
    fun seekingTheDisplayedProgressReturnsToTheSamePage() {
        for (count in listOf(1, 2, 3, 11, 100, 1000)) {
            for (page in 0 until count) {
                assertEquals("page $page of $count", page, pageAtProgress(pageProgress(page, count), count))
            }
        }
    }

    @Test
    fun endpointsAndMiddleMatchThePageRange() {
        assertEquals(0f, pageProgress(0, 5), 0f)
        assertEquals(0.5f, pageProgress(2, 5), 0f)
        assertEquals(1f, pageProgress(4, 5), 0f)
        assertEquals(0, pageAtProgress(0f, 5))
        assertEquals(2, pageAtProgress(0.5f, 5))
        assertEquals(4, pageAtProgress(1f, 5))
    }

    @Test
    fun singlePageIsCompleteAndEverySeekStaysOnIt() {
        assertEquals(1f, pageProgress(0, 1), 0f)
        for (progress in listOf(0f, 0.5f, 1f)) assertEquals(0, pageAtProgress(progress, 1))
    }
}
