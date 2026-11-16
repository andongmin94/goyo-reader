package kr.goyo.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.goyo.reader.data.FileEntry
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.OpenedDocument
import kr.goyo.reader.reader.ReaderInput

data class BrowserState(
    val path: List<FolderLocation> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as MinViewerApplication).library
    private val _library = MutableStateFlow(LibraryState())
    val library = _library.asStateFlow()
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val browser = MutableStateFlow(BrowserState())
    val session = MutableStateFlow<OpenedDocument?>(null)
    val opening = MutableStateFlow(false)
    private var navigation: Job? = null
    private var openingJob: Job? = null
    private var startupJob: Job? = null
    private var started = false
    private val positionChanges = MutableStateFlow<Pair<String, ReadingPosition>?>(null)
    private val latestPositions = mutableMapOf<String, ReadingPosition>()
    init {
        viewModelScope.launch {
            repository.state.catch { error.value = "독서 기록을 불러오지 못했습니다: ${it.message}"; ready.value = true }
                .collect { state ->
                    _library.value = state
                    if (!ready.value) {
                        ready.value = true
                        navigate(state.folderPath)
                    }
                }
        }
        viewModelScope.launch {
            // Read the current snapshot, not an older sampled position after a close/switch.
            positionChanges.filterNotNull().sample(500).collect { flushPosition() }
        }
    }

    /** Called once per ViewModel. A fresh external open takes precedence over automatic resume. */
    fun start(uri: Uri? = null) {
        if (started) return
        started = true
        startupJob = viewModelScope.launch {
            ready.first { it }
            startupJob = null
            if (uri != null) openUri(uri)
            else {
                val state = library.value
                state.books.firstOrNull { it.id == state.openBookId }?.let(::open)
            }
        }
    }

    fun navigate(path: List<FolderLocation>) {
        navigation?.cancel()
        browser.value = BrowserState(path = path, loading = true)
        navigation = viewModelScope.launch {
            try {
                val entries = repository.browse(path)
                repository.saveFolder(path)
                browser.value = BrowserState(path, entries)
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                browser.value = BrowserState(path = path, error = cause.message ?: "폴더를 읽을 수 없습니다.")
            }
        }
    }
    fun selectFolder(uri: Uri) { task {
        repository.directories.retainReadAccess(uri)
        navigate(listOf(repository.directories.selectedFolder(uri)))
    } }
    fun up() { if (browser.value.path.isNotEmpty()) navigate(browser.value.path.dropLast(1)) }
    fun refresh() = navigate(browser.value.path)
    fun select(entry: FileEntry) {
        if (entry.directory) navigate(browser.value.path + entry.folder())
        else openTask { repository.openFile(Uri.parse(entry.uri), browser.value.path) }
    }
    fun openUri(uri: Uri) = openTask { repository.openFile(uri) }
    fun openPickedUri(uri: Uri) { task {
        repository.directories.retainReadAccess(uri)
        openUri(uri)
    } }
    fun latestPosition(book: BookRecord): ReadingPosition = latestPositions[book.id] ?: book.position
    fun open(book: BookRecord) = openTask { repository.openRecord(book.copy(position = latestPosition(book))) }
    fun readImages() {
        val path = browser.value.path
        val existing = library.value.books.firstOrNull { it.imageFolder && it.uri == path.lastOrNull()?.uri }
        if (existing != null) open(existing) else openTask { repository.openImageFolder(path) }
    }
    fun toggleFavorite() { task { repository.toggleFavorite(browser.value.path) } }
    private fun openTask(block: suspend () -> OpenedDocument) {
        startupJob?.cancel()
        startupJob = null
        flushPosition()
        val previous = openingJob
        opening.value = true
        openingJob = viewModelScope.launch {
            try {
                // An explicit new intent must not be discarded while auto-resume is preparing.
                previous?.cancelAndJoin()
                opening.value = true
                val prepared = block()
                val latest = latestPositions[prepared.book.id]
                val opened = if (latest != null && prepared.input is ReaderInput.LocalFile)
                    prepared.copy(book = prepared.book.copy(position = latest)) else prepared
                latestPositions[opened.book.id] = opened.book.position
                session.value = opened
                positionChanges.value = opened.book.id to opened.book.position
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                error.value = cause.message ?: "파일을 열지 못했습니다."
            } finally { opening.value = false }
        }
    }
    fun cancelOpen() { openingJob?.cancel() }
    fun close() {
        val current = session.value ?: return
        val position = latestPosition(current.book)
        positionChanges.value = null
        session.value = null
        repository.closeReading(current.book.id, position) {
            error.value = "읽던 위치를 저장하지 못했습니다: ${it.message}"
        }
    }
    fun reportPosition(id: String, position: ReadingPosition) {
        // A disposed reader must not overwrite the position saved by close().
        val input = session.value?.takeIf { it.book.id == id }?.input ?: return
        val anchored = if (input is ReaderInput.Images) input.anchor(position) else position
        latestPositions[id] = anchored
        positionChanges.value = id to anchored
    }
    fun flushPosition() {
        val latest = positionChanges.value ?: return
        repository.flushPosition(latest.first, latest.second) { error.value = "읽던 위치를 저장하지 못했습니다: ${it.message}" }
    }
    fun updateSettings(settings: ReaderSettings) { task { repository.saveSettings(settings) } }
    fun addBookmark(book: BookRecord, title: String, position: ReadingPosition) { task {
        val input = session.value?.input
        repository.addBookmark(book.id, title, if (input is ReaderInput.Images) input.anchor(position) else position)
        message.value = "책갈피를 저장했습니다."
    } }
    fun deleteBookmark(id: String) { task { repository.deleteBookmark(id) } }
    fun removeBook(book: BookRecord) { task {
        repository.removeBook(book)
        latestPositions.remove(book.id)
    } }
    fun importFont(uri: Uri) { task {
        val path = repository.importFont(uri)
        repository.saveSettings(library.value.settings.copy(font = ReaderFont.CUSTOM, customFontPath = path))
        message.value = "글꼴을 적용했습니다."
    } }
    private fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                error.value = cause.message ?: "작업을 완료하지 못했습니다."
            }
        }
    }
}
