package kr.goyo.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kr.goyo.reader.reader.ViewportZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportZoomTest {
    @Test fun offCenterPinchKeepsTheSameContentUnderTheFingers() {
        val zoom = zoom()
        val focus = Offset(100f, 240f)
        zoom.transform(focus, 2f, Offset.Zero)
        assertOffset(focus, screenPoint(zoom, focus))
        assertOffset(Offset(100f, 160f), zoom.offset)
    }

    @Test fun aPinchAfterPanningKeepsItsAnchorAndAppliesTheNewPan() {
        val zoom = zoom()
        zoom.transform(Offset(200f, 400f), 2f, Offset(35f, -50f))
        val focus = Offset(250f, 300f)
        val original = contentPoint(zoom, focus)
        val pan = Offset(24f, -18f)
        zoom.transform(focus, 1.5f, pan)
        assertOffset(focus + pan, screenPoint(zoom, original))
    }

    @Test fun maximumScaleUsesTheClampedRatioWithoutMovingTheAnchor() {
        val zoom = zoom()
        zoom.transform(Offset(200f, 400f), 4f, Offset.Zero)
        val focus = Offset(120f, 260f)
        val original = contentPoint(zoom, focus)
        zoom.transform(focus, 3f, Offset.Zero)
        assertEquals(5f, zoom.scale, 0f)
        assertOffset(focus, screenPoint(zoom, original))
        zoom.transform(focus, 2f, Offset.Zero)
        assertOffset(focus, screenPoint(zoom, original))
    }

    @Test fun reducingToFitRemovesOffsetsAndDoesNotExposeEmptyEdges() {
        val zoom = zoom()
        zoom.transform(Offset(80f, 180f), 3f, Offset(40f, 70f))
        zoom.transform(Offset(300f, 600f), 0.1f, Offset(100f, 100f))
        assertEquals(1f, zoom.scale, 0f)
        assertOffset(Offset.Zero, zoom.offset)
    }

    @Test fun landscapeImageCannotPanVerticallyWhileShorterThanTheViewport() {
        val zoom = zoom().apply { content = Size(400f, 200f) }
        zoom.transform(Offset(100f, 100f), 2f, Offset(500f, 500f))
        assertOffset(Offset(200f, 0f), zoom.offset)
        zoom.viewport = Size(800f, 400f)
        zoom.rebound()
        assertOffset(Offset.Zero, zoom.offset)
    }

    @Test fun unspecifiedProgrammaticCentroidUsesTheViewportCenter() {
        val zoom = zoom()
        zoom.transform(Offset.Unspecified, 2f, Offset(20f, -30f))
        assertOffset(Offset(20f, -30f), zoom.offset)
        zoom.toggle(Offset(100f, 200f))
        assertEquals(1f, zoom.scale, 0f)
        assertOffset(Offset.Zero, zoom.offset)
    }

    private fun zoom() = ViewportZoom().apply {
        viewport = Size(400f, 800f)
        content = viewport
    }
    private fun center(zoom: ViewportZoom) = Offset(zoom.viewport.width / 2, zoom.viewport.height / 2)
    private fun contentPoint(zoom: ViewportZoom, screen: Offset): Offset =
        center(zoom) + (screen - center(zoom) - zoom.offset) / zoom.scale
    private fun screenPoint(zoom: ViewportZoom, original: Offset): Offset =
        center(zoom) + (original - center(zoom)) * zoom.scale + zoom.offset
    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
