package kr.goyo.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.OpenedDocument
import kr.goyo.reader.reader.ReaderInput
import kr.goyo.reader.reader.visual.ImagePageSource
import kr.goyo.reader.reader.visual.NaturalFileOrder
import kotlin.coroutines.coroutineContext

private val libraryJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal object LibrarySerializer : Serializer<LibraryState> {
    override val defaultValue = LibraryState()
    override suspend fun readFrom(input: InputStream): LibraryState = try {
        libraryJson.decodeFromString<LibraryState>(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        throw CorruptionException("독서 기록을 읽을 수 없습니다.", error)
    }
    override suspend fun writeTo(t: LibraryState, output: OutputStream) {
        output.write(libraryJson.encodeToString(LibraryState.serializer(), t).toByteArray())
    }
}

/** Original documents remain with their provider. Only opened seekable documents are staged. */
class LibraryRepository(private val context: Context) {
    val directories = DirectoryAccess(context)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DataStoreFactory.create(
        serializer = LibrarySerializer,
        produceFile = { context.dataStoreFile("documents.json") },
    )
    val state = store.data
    private val openMutex = Mutex()
    private val covers = BookCoverCache(context)
    private val documentCache = File(context.cacheDir, "reader-documents")
    private val fontsDirectory = File(context.filesDir, "fonts")

    suspend fun browse(path: List<FolderLocation>): List<FileEntry> =
        if (path.isEmpty()) emptyList() else directories.children(path.last())

    suspend fun saveFolder(path: List<FolderLocation>) { store.updateData { it.copy(folderPath = path) } }

    suspend fun toggleFavorite(path: List<FolderLocation>) {
        if (path.isEmpty()) return
        store.updateData { state ->
            val exists = state.favoritePaths.any { it.lastOrNull()?.uri == path.last().uri }
            state.copy(favoritePaths = if (exists) state.favoritePaths.filterNot { it.lastOrNull()?.uri == path.last().uri }
                else state.favoritePaths + listOf(path))
        }
    }

    suspend fun openFile(uri: Uri, folderPath: List<FolderLocation> = emptyList()): OpenedDocument = openMutex.withLock {
        val entry = directories.entry(uri)
        require(!entry.directory) { "폴더 안에서 파일을 선택해 주세요." }
        if (entry.image && folderPath.isNotEmpty()) {
            openImages(folderPath, selectedUri = entry.uri)
        } else {
            val book = record(entry, folderPath)
            val input = if (entry.image) ReaderInput.Images(listOf(entry)) else ReaderInput.LocalFile(stage(book))
            rememberOpened(book)
            OpenedDocument(book, input)
        }
    }

    suspend fun openRecord(book: BookRecord): OpenedDocument = openMutex.withLock {
        if (book.imageFolder) openImages(book.folderPath, selectedUri = null, resume = book.position)
        else {
            val entry = directories.entry(Uri.parse(book.uri))
            val fresh = record(entry, book.folderPath).copy(position = book.position)
            val input = if (entry.image) ReaderInput.Images(listOf(entry)) else ReaderInput.LocalFile(stage(fresh))
            rememberOpened(fresh)
            OpenedDocument(fresh, input)
        }
    }

    suspend fun openImageFolder(path: List<FolderLocation>): OpenedDocument = openMutex.withLock {
        openImages(path, selectedUri = null)
    }

    private suspend fun openImages(path: List<FolderLocation>, selectedUri: String?, resume: ReadingPosition? = null): OpenedDocument {
        require(path.isNotEmpty()) { "먼저 이미지 폴더를 선택해 주세요." }
        val entries = browse(path).filter { it.image }.sortedWith { a, b -> NaturalFileOrder.compare(a.name, b.name) }
        require(entries.isNotEmpty()) { "이 폴더에 읽을 이미지가 없습니다." }
        val folder = path.last()
        val bookId = identity(Uri.parse(folder.uri))
        val saved = state.first().books.firstOrNull { it.id == bookId }
        val input = ReaderInput.Images(entries)
        val position = if (selectedUri != null) {
            val index = entries.indexOfFirst { it.uri == selectedUri }
            require(index >= 0) { "선택한 이미지가 폴더에서 사라졌습니다." }
            ReadingPosition(index = index, locator = selectedUri, progress = pageProgress(index, entries.size))
        } else input.resolve(resume ?: saved?.position ?: ReadingPosition())
        val book = BookRecord(bookId, folder.name, folder.uri, BookKind.COMIC,
            entries.sumOf { it.size }, saved?.addedAt ?: System.currentTimeMillis(),
            System.currentTimeMillis(), position, path, imageFolder = true)
        rememberOpened(book)
        return OpenedDocument(book, input)
    }

    private suspend fun record(entry: FileEntry, path: List<FolderLocation>): BookRecord {
        val kind = entry.kind ?: error("${entry.name}: 지원하지 않는 파일 형식입니다.")
        val id = identity(Uri.parse(entry.uri))
        val saved = state.first().books.firstOrNull { it.id == id }
        return BookRecord(id, entry.name, entry.uri, kind, entry.size,
            saved?.addedAt ?: System.currentTimeMillis(), System.currentTimeMillis(),
            saved?.position ?: ReadingPosition(), path)
    }

    private suspend fun rememberOpened(book: BookRecord) {
        store.updateData { state -> state.copy(
            books = state.books.filterNot { it.id == book.id } + book,
            openBookId = book.id,
        ) }
    }

    /** ZIP, EPUB and PDF engines need a seekable local file; TXT shares this input path.
     * Refresh on every explicit open so edits outside the app never leave a stale copy. */
    private suspend fun stage(book: BookRecord): File = withContext(Dispatchers.IO) {
        check(documentCache.isDirectory || documentCache.mkdirs())
        val target = fileFor(book)
        val partial = File(documentCache, "${book.id}.part")
        try {
            context.contentResolver.openInputStream(Uri.parse(book.uri))?.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("파일을 읽을 수 없습니다. 폴더 접근 권한을 다시 확인해 주세요.")
            coroutineContext.ensureActive()
            check(partial.renameTo(target)) { "읽기용 파일을 준비하지 못했습니다." }
            covers.remove(book.id)
            target
        } finally { partial.delete() }
    }

    fun fileFor(book: BookRecord) = File(documentCache, "${book.id}.${when (book.kind) {
        BookKind.TEXT -> "txt"; BookKind.PDF -> "pdf"; BookKind.EPUB -> "epub"; BookKind.COMIC -> "cbz"
    }}")

    suspend fun coverFor(book: BookRecord): Bitmap? = if (book.imageFolder) null else covers.coverFor(book, fileFor(book))

    suspend fun preview(entry: FileEntry): Bitmap? = withContext(Dispatchers.IO) {
        if (entry.directory || entry.kind == null || entry.kind == BookKind.TEXT) return@withContext null
        try {
            if (entry.image) {
                ImagePageSource(context, listOf(entry)).use { source -> source.render(0, 96, 0f, 1f) }
            } else {
                val book = state.first().books.firstOrNull { it.id == identity(Uri.parse(entry.uri)) }
                if (book == null || !fileFor(book).exists()) null else coverFor(book)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    }

    fun flushPosition(id: String, position: ReadingPosition, onError: (Throwable) -> Unit) {
        persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { updatePosition(id, position) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { onError(error) }
        }
    }

    /** Explicitly returning to the directory saves the final position and clears auto-open
     * together. This write outlives the screen; closing an older book cannot close a newer one. */
    fun closeReading(id: String, position: ReadingPosition, onError: (Throwable) -> Unit): Job =
        persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                store.updateData { state -> state.copy(
                    books = state.books.map { if (it.id == id) it.copy(position = position) else it },
                    openBookId = state.openBookId.takeUnless { it == id },
                ) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { onError(error) }
        }

    suspend fun updatePosition(id: String, position: ReadingPosition) {
        store.updateData { state -> state.copy(books = state.books.map {
            if (it.id == id) it.copy(position = position) else it
        }) }
    }
    suspend fun saveSettings(settings: ReaderSettings) { store.updateData { it.copy(settings = settings) } }
    suspend fun addBookmark(bookId: String, title: String, position: ReadingPosition) {
        val bookmark = Bookmark(UUID.randomUUID().toString(), bookId, title, position, System.currentTimeMillis())
        store.updateData { it.copy(bookmarks = it.bookmarks + bookmark) }
    }
    suspend fun deleteBookmark(id: String) { store.updateData { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) } }
    suspend fun removeBook(book: BookRecord) = withContext(Dispatchers.IO) {
        store.updateData { it.copy(books = it.books.filterNot { b -> b.id == book.id },
            bookmarks = it.bookmarks.filterNot { b -> b.bookId == book.id },
            openBookId = it.openBookId.takeUnless { id -> id == book.id }) }
        fileFor(book).delete()
        covers.remove(book.id)
    }

    suspend fun importFont(uri: Uri): String = withContext(Dispatchers.IO) {
        check(fontsDirectory.isDirectory || fontsDirectory.mkdirs())
        val file = File(fontsDirectory, "${UUID.randomUUID()}.font")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: error("선택한 글꼴을 읽을 수 없습니다.")
            android.graphics.Typeface.createFromFile(file)
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val extension = if (String(header, Charsets.US_ASCII) == "OTTO") "otf" else "ttf"
            val destination = File(fontsDirectory, "${file.nameWithoutExtension}.$extension")
            check(file.renameTo(destination)) { "글꼴 파일을 저장하지 못했습니다." }
            destination.absolutePath
        } catch (error: Throwable) { file.delete(); throw error }
    }

    private fun identity(uri: Uri): String {
        val key = if (DocumentsContract.isDocumentUri(context, uri)) "${uri.authority}:${DocumentsContract.getDocumentId(uri)}" else uri.toString()
        return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
    }
}
