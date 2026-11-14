package kr.goyo.reader.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kr.goyo.reader.reader.visual.PageDimensions
import kr.goyo.reader.reader.visual.VisualPageSource

/** PdfRenderer owns its seekable file descriptor and serializes access to individual pages. */
class PdfPageSource(file: File) : VisualPageSource {
    private val lock = Any()
    private val renderer: PdfRenderer
    private var closed = false
    override val dimensions: List<PageDimensions>
    override val labels: List<String>

    init {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
        try {
            require(renderer.pageCount > 0) { "PDF에 읽을 페이지가 없습니다." }
            dimensions = List(renderer.pageCount) { index ->
                renderer.openPage(index).use { page -> PageDimensions(page.width, page.height) }
            }
            labels = List(renderer.pageCount) { "${it + 1}쪽" }
        } catch (error: Throwable) {
            renderer.close()
            throw error
        }
    }

    override fun render(page: Int, width: Int, top: Float, bottom: Float): Bitmap = synchronized(lock) {
        check(!closed) { "이 PDF는 이미 닫혔습니다." }
        val size = dimensions[page]
        val from = top.coerceIn(0f, 0.999999f)
        val to = bottom.coerceIn(from + 0.000001f, 1f)
        val regionHeight = size.height * (to - from)
        var scale = width.coerceAtLeast(1).toFloat() / size.width
        scale = min(scale, 8192f / size.width)
        scale = min(scale, 8192f / regionHeight)
        scale = min(scale, sqrt(6_000_000f / (size.width * regionHeight)))
        val bitmap = Bitmap.createBitmap(
            (size.width * scale).roundToInt().coerceAtLeast(1),
            (regionHeight * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        try {
            renderer.openPage(page).use { pdfPage ->
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(0f, -from * size.height * scale)
                }
                pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
        }
    }
}
