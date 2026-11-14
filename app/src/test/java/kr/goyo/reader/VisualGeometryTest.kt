package kr.goyo.reader

import kr.goyo.reader.reader.visual.NaturalFileOrder
import kr.goyo.reader.reader.visual.PageDimensions
import kr.goyo.reader.reader.visual.buildPageSlices
import kr.goyo.reader.reader.visual.imageSampleSize
import kr.goyo.reader.reader.visual.sliceAtPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualGeometryTest {
    @Test fun `comic filenames use numeric order across folders and leading zeros`() {
        val names = listOf("2권/10.jpg", "1권/2.jpg", "1권/10.jpg", "1권/01.jpg", "1권/1.jpg")
        assertEquals(
            listOf("1권/1.jpg", "1권/01.jpg", "1권/2.jpg", "1권/10.jpg", "2권/10.jpg"),
            names.sortedWith(NaturalFileOrder),
        )
    }

    @Test fun `arbitrarily long page numbers do not overflow`() {
        assertTrue(NaturalFileOrder.compare("page99999999999999999999.jpg", "page100000000000000000000.jpg") < 0)
        assertEquals(0, NaturalFileOrder.compare("001.jpg", "001.jpg"))
    }

    @Test fun `long webtoon is split into bounded contiguous source regions`() {
        val dimensions = listOf(PageDimensions(1000, 10000), PageDimensions(1000, 1000))
        val slices = buildPageSlices(dimensions, viewportWidth = 1000, viewportHeight = 1000)
        assertEquals(11, slices.size)
        assertEquals(0f, slices.first().top, 0.000001f)
        assertEquals(1f, slices[9].bottom, 0.000001f)
        assertEquals(1, slices.last().page)
        slices.take(10).zipWithNext().forEach { (left, right) -> assertEquals(left.bottom, right.top, 0.000001f) }
    }

    @Test fun `saved fraction locates the same content after viewport dimensions change`() {
        val pages = listOf(PageDimensions(1000, 10000), PageDimensions(600, 900))
        for ((width, height) in listOf(1000 to 1000, 700 to 1300, 1300 to 700)) {
            val slices = buildPageSlices(pages, width, height)
            val tile = slices[sliceAtPosition(slices, 0, 0.63f)]
            assertEquals(0, tile.page)
            assertTrue(tile.top <= 0.63f && tile.bottom > 0.63f)
            assertEquals(1, slices[sliceAtPosition(slices, 1, 0f)].page)
        }
    }

    @Test fun `decoded pixels stay bounded even for an extremely tall page`() {
        val sample = imageSampleSize(8000, 100000, 1080)
        assertTrue((8000 / sample).toLong() * (100000 / sample) <= 6_000_000L)
        assertTrue(100000 / sample <= 8192)
        assertTrue(sample > 0 && (sample and (sample - 1)) == 0)
    }
}
