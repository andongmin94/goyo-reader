package kr.goyo.reader.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookKind { TEXT, COMIC, PDF, EPUB }
@Serializable
enum class ReadingMode { PAGE, SCROLL }
@Serializable
enum class ReaderFont { SYSTEM, SERIF, MONOSPACE, CUSTOM }
@Serializable
enum class TapLayout { EDGES, LEFT_HANDED, ANYWHERE_NEXT }

@Serializable
data class ReaderSettings(
    val fontSizeSp: Float = 20f,
    val lineSpacing: Float = 1.6f,
    val marginDp: Float = 24f,
    val font: ReaderFont = ReaderFont.SYSTEM,
    val customFontPath: String? = null,
    val backgroundColor: Long = 0xFFFFFFFF,
    val textColor: Long = 0xFF202124,
    val followSystemTheme: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.PAGE,
    val rightToLeft: Boolean = false,
    val volumePaging: Boolean = true,
    val reverseVolumeKeys: Boolean = false,
    val tapLayout: TapLayout = TapLayout.EDGES,
    val brightness: Float = -1f,
    val dimAmount: Float = 0f,
    val keepScreenOn: Boolean = true,
    val autoScrollDpPerSecond: Float = 24f,
    val autoPageSeconds: Int = 8,
    val textEncoding: String = "AUTO",
)

/** TXT: UTF-16 offset. Images/PDF: zero-based page + within-page fraction.
 * EPUB: Readium Locator. An image folder uses locator for the current image URI. */
@Serializable
data class ReadingPosition(
    val index: Int = 0,
    val offset: Float = 0f,
    val locator: String? = null,
    val progress: Float = 0f,
)

@Serializable
data class FolderLocation(val uri: String, val name: String)

/** A reference to an original document, not an imported library copy. */
@Serializable
data class BookRecord(
    val id: String,
    val title: String,
    val uri: String,
    val kind: BookKind,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastOpenedAt: Long = 0,
    val position: ReadingPosition = ReadingPosition(),
    val folderPath: List<FolderLocation> = emptyList(),
    val imageFolder: Boolean = false,
)

@Serializable
data class Bookmark(
    val id: String,
    val bookId: String,
    val title: String,
    val position: ReadingPosition,
    val createdAt: Long,
)

@Serializable
data class LibraryState(
    val books: List<BookRecord> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val settings: ReaderSettings = ReaderSettings(),
    val folderPath: List<FolderLocation> = emptyList(),
    val favoritePaths: List<List<FolderLocation>> = emptyList(),
)

data class SearchResult(val title: String, val excerpt: String, val position: ReadingPosition)
data class OutlineEntry(val title: String, val position: ReadingPosition, val depth: Int = 0)
data class ReaderInfo(
    val current: Int = 1,
    val total: Int = 1,
    val supportsSearch: Boolean = false,
    val outline: List<OutlineEntry> = emptyList(),
)

sealed interface ReaderCommand {
    data object Next : ReaderCommand
    data object Previous : ReaderCommand
    data class Seek(val position: ReadingPosition) : ReaderCommand
    data class SeekProgress(val progress: Float) : ReaderCommand
    data class SeekPage(val page: Int) : ReaderCommand
    data class Search(val query: String) : ReaderCommand
}
