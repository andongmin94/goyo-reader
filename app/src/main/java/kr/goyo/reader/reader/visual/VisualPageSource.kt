package kr.goyo.reader.reader.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlin.math.floor

interface VisualPageSource : Closeable {
    val dimensions: List<PageDimensions>
    val labels: List<String>
    fun render(page: Int, width: Int, top: Float = 0f, bottom: Float = 1f): Bitmap
}

class ImagePageSource(private val file: File, firstPageOnly: Boolean = false) : VisualPageSource {
    private val lock = Any()
    private val archive: ZipFile? = if (file.extension.lowercase() in setOf("zip", "cbz")) {
        // UTF-8-flagged ZIP names stay UTF-8; old unflagged Korean ZIP names use MS949.
        ZipFile(file, Charset.forName("MS949"))
    } else null
    private var closed = false

    override val labels: List<String>
    override val dimensions: List<PageDimensions>

    init {
        try {
            labels = archive?.entries()?.asSequence()
                ?.filter { entry ->
                    !entry.isDirectory &&
                        !entry.name.startsWith("__MACOSX/") &&
                        !entry.name.substringAfterLast('/').startsWith(".") &&
                        entry.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
                }
                ?.map { it.name }
                ?.let { names ->
                    if (firstPageOnly) listOfNotNull(names.minWithOrNull(NaturalFileOrder))
                    else names.sortedWith(NaturalFileOrder).toList()
                }
                ?: listOf(file.name)
            require(labels.isNotEmpty()) { "압축파일 안에 JPG, PNG 또는 WebP 이미지가 없습니다." }
            dimensions = labels.indices.map { page ->
                withStream(page) { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    require(options.outWidth > 0 && options.outHeight > 0) {
                        "이미지를 읽을 수 없습니다: ${labels[page]}"
                    }
                    PageDimensions(options.outWidth, options.outHeight)
                }
            }
        } catch (error: Throwable) {
            archive?.close()
            throw error
        }
    }

    private fun <T> withStream(page: Int, action: (InputStream) -> T): T {
        val stream = archive?.let { zip -> zip.getInputStream(zip.getEntry(labels[page])) }
            ?: file.inputStream()
        return stream.use(action)
    }

    @Suppress("DEPRECATION")
    override fun render(page: Int, width: Int, top: Float, bottom: Float): Bitmap = synchronized(lock) {
        check(!closed) { "이 책은 이미 닫혔습니다." }
        val size = dimensions[page]
        val firstRow = floor(top.coerceIn(0f, 1f) * size.height).toInt().coerceIn(0, size.height - 1)
        val lastRow = ceil(bottom.coerceIn(0f, 1f) * size.height).toInt().coerceIn(firstRow + 1, size.height)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(size.width, lastRow - firstRow, width)
        }
        withStream(page) { stream ->
            if (firstRow == 0 && lastRow == size.height) {
                requireNotNull(BitmapFactory.decodeStream(stream, null, options)) {
                    "이미지를 읽을 수 없습니다: ${labels[page]}"
                }
            } else {
                val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false)) {
                    "긴 이미지를 읽을 수 없습니다: ${labels[page]}"
                }
                try {
                    requireNotNull(decoder.decodeRegion(Rect(0, firstRow, size.width, lastRow), options))
                } finally {
                    decoder.recycle()
                }
            }
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            archive?.close()
        }
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
