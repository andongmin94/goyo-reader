package kr.goyo.reader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kr.goyo.reader.model.*
import kotlin.coroutines.coroutineContext

private val libraryJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal object LibrarySerializer : Serializer<LibraryState> {
    override val defaultValue = LibraryState()
    override suspend fun readFrom(input: InputStream): LibraryState = try {
        libraryJson.decodeFromString<LibraryState>(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        throw CorruptionException("서재 정보를 읽을 수 없습니다.", error)
    }
    override suspend fun writeTo(t: LibraryState, output: OutputStream) {
        output.write(libraryJson.encodeToString(LibraryState.serializer(), t).toByteArray())
    }
}

data class SourceDocument(val uri: Uri, val name: String, val size: Long, val kind: BookKind)
data class ImageGroup(val title: String, val files: List<SourceDocument>)
data class ImportPlan(val documents: List<SourceDocument>, val imageGroups: List<ImageGroup>) {
    val bookCount get() = documents.size + imageGroups.size
    val bytes get() = documents.sumOf { it.size } + imageGroups.sumOf { group -> group.files.sumOf { it.size } }
}

/** The shelf owns imported copies; it never renames, edits or deletes source documents. */
class LibraryRepository(private val context: Context) {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DataStoreFactory.create(
        serializer = LibrarySerializer,
        produceFile = { context.dataStoreFile("library.json") },
    )
    val state = store.data
    private val booksDirectory = File(context.filesDir, "books").apply { mkdirs() }
    private val fontsDirectory = File(context.filesDir, "fonts").apply { mkdirs() }

    fun fileFor(book: BookRecord) = File(booksDirectory, book.fileName)

    suspend fun planFiles(uris: List<Uri>): ImportPlan = withContext(Dispatchers.IO) {
        val sources = uris.map { source(it) }
        if (sources.size > 1 && sources.all { isImage(it.name) }) {
            ImportPlan(emptyList(), listOf(ImageGroup("이미지 모음 · ${sources.first().name.substringBeforeLast('.')}", sources)))
        } else ImportPlan(sources, emptyList())
    }

    suspend fun planFolder(uri: Uri): ImportPlan = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: error("선택한 폴더를 열 수 없습니다.")
        val documents = mutableListOf<SourceDocument>()
        val imageGroups = mutableListOf<ImageGroup>()
        val importContext = coroutineContext
        fun scan(directory: DocumentFile) {
            importContext.ensureActive()
            val images = mutableListOf<SourceDocument>()
            for (child in directory.listFiles()) {
                val name = child.name ?: continue
                if (child.isDirectory) scan(child)
                else {
                    val kind = kindFor(name, child.type) ?: continue
                    val source = SourceDocument(child.uri, name, child.length(), kind)
                    if (isImage(name)) images += source else documents += source
                }
            }
            if (images.isNotEmpty()) imageGroups += ImageGroup(directory.name ?: "이미지 모음", images)
        }
        scan(root)
        ImportPlan(documents, imageGroups)
    }

    suspend fun import(plan: ImportPlan, onProgress: (String, Int, Int) -> Unit): List<BookRecord> = withContext(Dispatchers.IO) {
        val added = mutableListOf<BookRecord>()
        for (source in plan.documents) {
            coroutineContext.ensureActive()
            onProgress(source.name, added.size, plan.bookCount)
            val extension = storageExtension(source.name, source.kind)
            val id = UUID.randomUUID().toString()
            val fileName = "$id.$extension"
            val file = File(booksDirectory, fileName)
            val book = try {
                copyUri(source.uri, file)
                require(file.length() > 0) { "${source.name}: 빈 파일입니다." }
                BookRecord(id, source.name.substringBeforeLast('.'), fileName, source.kind, file.length(), System.currentTimeMillis())
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
            commitBook(book, file)
            added += book
        }
        for (group in plan.imageGroups) {
            coroutineContext.ensureActive()
            onProgress(group.title, added.size, plan.bookCount)
            val id = UUID.randomUUID().toString()
            val fileName = "$id.cbz"
            val file = File(booksDirectory, fileName)
            val book = try {
                ZipOutputStream(file.outputStream().buffered()).use { output ->
                    val ordered = group.files.sortedWith { left, right -> kr.goyo.reader.reader.visual.NaturalFileOrder.compare(left.name, right.name) }
                    ordered.forEachIndexed { index, image ->
                        coroutineContext.ensureActive()
                        val name = "${index.toString().padStart(8, '0')}.${image.name.substringAfterLast('.').lowercase()}"
                        output.putNextEntry(ZipEntry(name))
                        context.contentResolver.openInputStream(image.uri)?.use { it.copyTo(output) }
                            ?: error("${image.name} 파일을 읽을 수 없습니다.")
                        output.closeEntry()
                    }
                }
                BookRecord(id, group.title, fileName, BookKind.COMIC, file.length(), System.currentTimeMillis())
            } catch (error: Throwable) { file.delete(); throw error }
            commitBook(book, file)
            added += book
        }
        onProgress("가져오기 완료", added.size, plan.bookCount)
        added
    }

    private suspend fun commitBook(book: BookRecord, file: File) = withContext(NonCancellable) {
        try { store.updateData { it.copy(books = it.books + book) } }
        catch (error: Exception) { file.delete(); throw error }
    }

    fun flushPosition(id: String, position: ReadingPosition, onError: (Throwable) -> Unit) {
        persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { updatePosition(id, position) } catch (error: Exception) { onError(error) }
        }
    }

    suspend fun updatePosition(id: String, position: ReadingPosition) {
        store.updateData { state -> state.copy(books = state.books.map {
            if (it.id == id) it.copy(position = position, lastOpenedAt = System.currentTimeMillis()) else it
        }) }
    }

    suspend fun markOpened(id: String) {
        store.updateData { state -> state.copy(books = state.books.map {
            if (it.id == id) it.copy(lastOpenedAt = System.currentTimeMillis()) else it
        }) }
    }

    suspend fun saveSettings(settings: ReaderSettings) { store.updateData { it.copy(settings = settings) } }

    suspend fun addBookmark(bookId: String, title: String, position: ReadingPosition) {
        val bookmark = Bookmark(UUID.randomUUID().toString(), bookId, title, position, System.currentTimeMillis())
        store.updateData { it.copy(bookmarks = it.bookmarks + bookmark) }
    }

    suspend fun deleteBookmark(id: String) { store.updateData { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) } }

    suspend fun removeBook(book: BookRecord) = withContext(Dispatchers.IO) {
        store.updateData { it.copy(books = it.books.filterNot { b -> b.id == book.id }, bookmarks = it.bookmarks.filterNot { b -> b.bookId == book.id }) }
        fileFor(book).delete()
        Unit
    }

    suspend fun importFont(uri: Uri): String = withContext(Dispatchers.IO) {
        val file = File(fontsDirectory, "${UUID.randomUUID()}.font")
        try {
            copyUri(uri, file)
            // Check the selected bytes using Android's font parser before changing settings.
            android.graphics.Typeface.createFromFile(file)
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val extension = if (String(header, Charsets.US_ASCII) == "OTTO") "otf" else "ttf"
            val destination = File(fontsDirectory, "${file.nameWithoutExtension}.$extension")
            check(file.renameTo(destination)) { "글꼴 파일을 저장하지 못했습니다." }
            destination.absolutePath
        } catch (error: Throwable) { file.delete(); throw error }
    }

    private fun copyUri(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().buffered().use { input.copyTo(it) }
        } ?: error("선택한 파일을 읽을 수 없습니다.")
    }

    private fun source(uri: Uri): SourceDocument {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "파일"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !cursor.isNull(ni)) name = cursor.getString(ni)
                if (si >= 0 && !cursor.isNull(si)) size = cursor.getLong(si)
            }
        }
        val kind = kindFor(name, context.contentResolver.getType(uri))
            ?: error("$name: TXT, ZIP/CBZ, PDF, EPUB, JPG, PNG, WEBP 파일을 선택해 주세요.")
        return SourceDocument(uri, name, size, kind)
    }

    companion object {
        fun storageExtension(name: String, kind: BookKind): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when (kind) {
                BookKind.TEXT -> "txt"
                BookKind.PDF -> "pdf"
                BookKind.EPUB -> "epub"
                BookKind.COMIC -> if (extension in setOf("jpg", "jpeg", "png", "webp")) extension else "cbz"
            }
        }
        fun isImage(name: String) = name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp")
        fun kindFor(name: String, mime: String? = null): BookKind? = when (name.substringAfterLast('.').lowercase()) {
            "txt", "text" -> BookKind.TEXT
            "zip", "cbz", "jpg", "jpeg", "png", "webp" -> BookKind.COMIC
            "pdf" -> BookKind.PDF
            "epub" -> BookKind.EPUB
            else -> when (mime) {
                "text/plain" -> BookKind.TEXT
                "application/pdf" -> BookKind.PDF
                "application/epub+zip" -> BookKind.EPUB
                "application/zip", "application/vnd.comicbook+zip" -> BookKind.COMIC
                else -> null
            }
        }
    }
}
