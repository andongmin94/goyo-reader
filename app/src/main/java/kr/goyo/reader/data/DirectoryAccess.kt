package kr.goyo.reader.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.FolderLocation
import kr.goyo.reader.reader.visual.NaturalFileOrder
import kotlin.coroutines.coroutineContext

val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

data class FileEntry(
    val uri: String,
    val name: String,
    val directory: Boolean,
    val size: Long = 0,
    val modified: Long = 0,
    val mime: String? = null,
) {
    val kind: BookKind? get() = if (directory) null else fileKind(name, mime)
    val image: Boolean get() = !directory && when (name.substringAfterLast('.', "").lowercase()) {
        in imageExtensions -> true
        "txt", "text", "pdf", "epub", "zip", "cbz" -> false
        else -> mime in setOf("image/jpeg", "image/png", "image/webp")
    }
    fun folder() = FolderLocation(uri, name)
}

enum class FileSort { NAME, MODIFIED, SIZE }

fun orderedEntries(entries: List<FileEntry>, query: String = "", sort: FileSort = FileSort.NAME, descending: Boolean = false): List<FileEntry> {
    val byName = Comparator<FileEntry> { a, b -> NaturalFileOrder.compare(a.name, b.name) }
    val byValue = when (sort) {
        FileSort.NAME -> byName
        FileSort.MODIFIED -> compareBy<FileEntry> { it.modified }.then(byName)
        FileSort.SIZE -> compareBy<FileEntry> { it.size }.then(byName)
    }.let { if (descending) it.reversed() else it }
    return entries.filter { it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<FileEntry> { it.directory }.then(byValue))
}

fun fileKind(name: String, mime: String? = null): BookKind? = when (name.substringAfterLast('.', "").lowercase()) {
    "txt", "text" -> BookKind.TEXT
    "zip", "cbz", "jpg", "jpeg", "png", "webp" -> BookKind.COMIC
    "pdf" -> BookKind.PDF
    "epub" -> BookKind.EPUB
    else -> when (mime) {
        "text/plain" -> BookKind.TEXT
        "application/pdf" -> BookKind.PDF
        "application/epub+zip" -> BookKind.EPUB
        "application/zip", "application/vnd.comicbook+zip", "image/jpeg", "image/png", "image/webp" -> BookKind.COMIC
        else -> null
    }
}

/** Reads only the current directory. No recursive import, write access or source deletion. */
class DirectoryAccess(private val context: Context) {
    fun retainReadAccess(uri: Uri) {
        if (uri.scheme == "content") context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    suspend fun selectedFolder(tree: Uri): FolderLocation = withContext(Dispatchers.IO) {
        val document = if (tree.scheme == "file") tree else DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val entry = entry(document)
        require(entry.directory) { "선택한 위치는 폴더가 아닙니다." }
        entry.folder()
    }

    suspend fun children(folder: FolderLocation): List<FileEntry> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(folder.uri)
        if (uri.scheme == "file") {
            val directory = File(requireNotNull(uri.path))
            require(directory.isDirectory) { "폴더가 이동되었거나 삭제되었습니다." }
            return@withContext requireNotNull(directory.listFiles()) { "폴더를 읽을 수 없습니다." }.map(::fileEntry)
        }
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val result = mutableListOf<FileEntry>()
        val cursor = context.contentResolver.query(childUri, projection, null, null, null)
            ?: error("폴더를 읽을 수 없습니다. 폴더를 다시 선택해 주세요.")
        cursor.use {
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                val id = requireNotNull(it.string(Document.COLUMN_DOCUMENT_ID))
                val mime = it.string(Document.COLUMN_MIME_TYPE)
                result += FileEntry(
                    DocumentsContract.buildDocumentUriUsingTree(uri, id).toString(),
                    it.string(Document.COLUMN_DISPLAY_NAME) ?: id,
                    mime == Document.MIME_TYPE_DIR, it.number(Document.COLUMN_SIZE),
                    it.number(Document.COLUMN_LAST_MODIFIED), mime,
                )
            }
        }
        result
    }

    suspend fun entry(uri: Uri): FileEntry = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            require(file.exists()) { "파일이 이동되었거나 삭제되었습니다." }
            return@withContext fileEntry(file)
        }
        require(uri.scheme == "content") { "지원하지 않는 파일 위치입니다." }
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: error("파일 정보를 읽을 수 없습니다.")
        cursor.use {
            require(it.moveToFirst()) { "파일이 이동되었거나 삭제되었습니다." }
            val mime = it.string(Document.COLUMN_MIME_TYPE) ?: context.contentResolver.getType(uri)
            FileEntry(uri.toString(), it.string(OpenableColumns.DISPLAY_NAME) ?: "파일",
                mime == Document.MIME_TYPE_DIR, it.number(OpenableColumns.SIZE),
                it.number(Document.COLUMN_LAST_MODIFIED), mime)
        }
    }

    private fun fileEntry(file: File) = FileEntry(Uri.fromFile(file).toString(), file.name,
        file.isDirectory, if (file.isDirectory) 0 else file.length(), file.lastModified())
    private fun Cursor.string(column: String): String? = getColumnIndex(column).let {
        if (it < 0 || isNull(it)) null else getString(it)
    }
    private fun Cursor.number(column: String): Long = getColumnIndex(column).let {
        if (it < 0 || isNull(it)) 0L else getLong(it)
    }
}
