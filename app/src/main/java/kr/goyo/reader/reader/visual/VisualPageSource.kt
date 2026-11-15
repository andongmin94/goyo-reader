package kr.goyo.reader.reader.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import kr.goyo.reader.data.FileEntry
import kotlin.math.ceil
import kotlin.math.floor

interface VisualPageSource : Closeable {
    val dimensions: List<PageDimensions>
    val labels: List<String>
    fun render(page: Int, width: Int, top: Float = 0f, bottom: Float = 1f): Bitmap
}

/** One decoder for loose images and archive entries. Folder images are never repackaged. */
class ImagePageSource private constructor(private val input: ImageStreams) : VisualPageSource {
    constructor(file: File, firstPageOnly: Boolean = false) : this(fileStreams(file, firstPageOnly))
    constructor(context: Context, entries: List<FileEntry>) : this(ImageStreams(
        entries.map { it.name },
        { page -> context.contentResolver.openInputStream(Uri.parse(entries[page].uri))
            ?: error("이미지를 읽을 수 없습니다: ${entries[page].name}") },
    ))

    private val lock = Any()
    private var closed = false
    override val labels = input.labels
    override val dimensions: List<PageDimensions>

    init {
        try {
            require(labels.isNotEmpty()) { "읽을 JPG, PNG 또는 WebP 이미지가 없습니다." }
            dimensions = labels.indices.map { page ->
                input.open(page).use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    require(options.outWidth > 0 && options.outHeight > 0) {
                        "이미지를 읽을 수 없습니다: ${labels[page]}"
                    }
                    PageDimensions(options.outWidth, options.outHeight)
                }
            }
        } catch (error: Throwable) {
            input.close()
            throw error
        }
    }

    @Suppress("DEPRECATION")
    override fun render(page: Int, width: Int, top: Float, bottom: Float): Bitmap = synchronized(lock) {
        check(!closed) { "이 파일은 이미 닫혔습니다." }
        val size = dimensions[page]
        val firstRow = floor(top.coerceIn(0f, 1f) * size.height).toInt().coerceIn(0, size.height - 1)
        val lastRow = ceil(bottom.coerceIn(0f, 1f) * size.height).toInt().coerceIn(firstRow + 1, size.height)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(size.width, lastRow - firstRow, width)
        }
        input.open(page).use { stream ->
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
                } finally { decoder.recycle() }
            }
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) { closed = true; input.close() }
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

        private fun fileStreams(file: File, firstPageOnly: Boolean): ImageStreams {
            if (file.extension.lowercase() !in setOf("zip", "cbz")) {
                return ImageStreams(listOf(file.name), { file.inputStream() })
            }
            // Flagged UTF-8 names stay UTF-8; unflagged Korean ZIP names use MS949.
            val archive = ZipFile(file, Charset.forName("MS949"))
            try {
                val names = archive.entries().asSequence().filter {
                    !it.isDirectory && !it.name.startsWith("__MACOSX/") &&
                        !it.name.substringAfterLast('/').startsWith(".") &&
                        it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
                }.map { it.name }.let {
                    if (firstPageOnly) listOfNotNull(it.minWithOrNull(NaturalFileOrder))
                    else it.sortedWith(NaturalFileOrder).toList()
                }
                return ImageStreams(names, { page -> archive.getInputStream(archive.getEntry(names[page])) }, archive::close)
            } catch (error: Throwable) { archive.close(); throw error }
        }
    }
}

private data class ImageStreams(
    val labels: List<String>,
    val open: (Int) -> InputStream,
    val close: () -> Unit = {},
)
