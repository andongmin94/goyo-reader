@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package kr.goyo.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.BookRecord
import kr.goyo.reader.reader.PdfPageSource
import kr.goyo.reader.reader.epub.openEpub
import kr.goyo.reader.reader.visual.ImagePageSource
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.use
import kotlin.coroutines.coroutineContext

/** Imported books are immutable, so their IDs also identify their cached covers. */
internal class BookCoverCache(private val context: Context) {
    private val directory = File(context.cacheDir, "book-covers")
    private val mutex = Mutex()

    suspend fun coverFor(book: BookRecord, source: File): Bitmap? = withContext(Dispatchers.IO) {
        if (book.kind == BookKind.TEXT) return@withContext null
        mutex.withLock {
            var bitmap: Bitmap? = null
            try {
                coroutineContext.ensureActive()
                val cachedFile = File(directory, "${book.id}.png")
                val cached = if (cachedFile.isFile) BitmapFactory.decodeFile(cachedFile.absolutePath) else null
                if (cached != null) {
                    bitmap = cached
                    coroutineContext.ensureActive()
                    return@withLock cached
                }

                val cover = createCover(book.kind, source) ?: return@withLock null
                bitmap = cover
                coroutineContext.ensureActive()
                check(directory.isDirectory || directory.mkdirs())
                cachedFile.outputStream().use { output ->
                    check(cover.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                coroutineContext.ensureActive()
                cover
            } catch (error: CancellationException) {
                bitmap?.recycle()
                throw error
            } catch (error: Exception) {
                bitmap?.recycle()
                null
            }
        }
    }

    suspend fun remove(bookId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { File(directory, "$bookId.png").delete() }
        Unit
    }

    private suspend fun createCover(kind: BookKind, file: File): Bitmap? {
        val bitmap = when (kind) {
            BookKind.COMIC -> ImagePageSource(file, firstPageOnly = true).use { pages ->
                coroutineContext.ensureActive()
                val size = pages.dimensions.first()
                val bottom = (size.width * (HEIGHT.toFloat() / WIDTH) / size.height).coerceAtMost(1f)
                pages.render(0, WIDTH, 0f, bottom)
            }
            BookKind.PDF -> PdfPageSource(file, firstPageOnly = true).use { pages ->
                coroutineContext.ensureActive()
                val size = pages.dimensions.first()
                val width = minOf(WIDTH.toFloat(), HEIGHT.toFloat() * size.width / size.height)
                    .toInt().coerceAtLeast(1)
                pages.render(0, width, 0f, 1f)
            }
            BookKind.EPUB -> openEpub(context, file).use { publication ->
                coroutineContext.ensureActive()
                publication.coverFitting(Size(WIDTH, HEIGHT))
            }
            BookKind.TEXT -> null
        } ?: return null
        val scale = minOf(1f, WIDTH.toFloat() / bitmap.width, HEIGHT.toFloat() / bitmap.height)
        if (scale == 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { if (it !== bitmap) bitmap.recycle() }
    }

    private companion object {
        const val WIDTH = 240
        const val HEIGHT = 320
    }
}
