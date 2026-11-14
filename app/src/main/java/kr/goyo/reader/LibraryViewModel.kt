package kr.goyo.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kr.goyo.reader.data.ImportPlan
import kr.goyo.reader.model.*

data class ImportProgress(val title: String, val complete: Int = 0, val total: Int = 0)

@OptIn(FlowPreview::class)
class LibraryViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val repository = (application as GoyoApplication).library
    private val _library = MutableStateFlow(LibraryState())
    val library = _library.asStateFlow()
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val importProgress = MutableStateFlow<ImportProgress?>(null)
    val pendingImport = MutableStateFlow<ImportPlan?>(null)
    val bookId = savedState.getStateFlow<String?>("openBookId", null)
    private val positionChanges = MutableStateFlow<Pair<String, ReadingPosition>?>(null)
    private val latestPositions = mutableMapOf<String, ReadingPosition>()

    init {
        viewModelScope.launch {
            repository.state.catch { error.value = "서재를 불러오지 못했습니다: ${it.message}" }
                .collect { _library.value = it; ready.value = true }
        }
        viewModelScope.launch {
            positionChanges.filterNotNull().sample(500).collect { (id, position) ->
                try { repository.updatePosition(id, position) }
                catch (cause: Exception) { if (cause is CancellationException) throw cause; error.value = "읽던 위치를 저장하지 못했습니다: ${cause.message}" }
            }
        }
    }

    fun open(book: BookRecord) {
        flushPosition()
        savedState["openBookId"] = book.id
        positionChanges.value = book.id to latestPosition(book)
        task { repository.markOpened(book.id) }
    }

    fun close() { flushPosition(); savedState["openBookId"] = null }

    fun latestPosition(book: BookRecord): ReadingPosition = latestPositions[book.id] ?: book.position

    fun reportPosition(id: String, position: ReadingPosition) {
        latestPositions[id] = position
        positionChanges.value = id to position
    }

    fun flushPosition() {
        val latest = positionChanges.value ?: return
        repository.flushPosition(latest.first, latest.second) { error.value = "읽던 위치를 저장하지 못했습니다: ${it.message}" }
    }

    fun updateSettings(settings: ReaderSettings) { task { repository.saveSettings(settings) } }

    fun selectFiles(uris: List<Uri>, openAfterImport: Boolean = false) {
        if (uris.isEmpty()) return
        task {
            importProgress.value = ImportProgress("선택한 파일 확인 중")
            try {
                val plan = repository.planFiles(uris)
                val books = importPlan(plan)
                if (openAfterImport && books.size == 1) open(books.single())
            } finally { importProgress.value = null }
        }
    }

    fun selectFolder(uri: Uri) {
        task {
            importProgress.value = ImportProgress("폴더의 책 확인 중")
            try {
                val plan = repository.planFolder(uri)
                if (plan.bookCount == 0) message.value = "이 폴더에는 지원하는 책이나 이미지가 없습니다."
                else pendingImport.value = plan
            } finally { importProgress.value = null }
        }
    }

    fun confirmFolderImport() {
        val plan = pendingImport.value ?: return
        pendingImport.value = null
        task { try { importPlan(plan) } finally { importProgress.value = null } }
    }

    private suspend fun importPlan(plan: ImportPlan): List<BookRecord> {
        val books = repository.import(plan) { title, done, total -> importProgress.value = ImportProgress(title, done, total) }
        message.value = "${books.size}권을 서재에 가져왔습니다."
        return books
    }

    fun addBookmark(book: BookRecord, title: String, position: ReadingPosition) {
        task {
            repository.addBookmark(book.id, title, position)
            message.value = "책갈피를 저장했습니다."
        }
    }

    fun deleteBookmark(id: String) { task { repository.deleteBookmark(id) } }

    fun removeBook(book: BookRecord) {
        if (bookId.value == book.id) close()
        task { repository.removeBook(book) }
    }

    fun importFont(uri: Uri) {
        task {
            val path = repository.importFont(uri)
            repository.saveSettings(library.value.settings.copy(font = ReaderFont.CUSTOM, customFontPath = path))
            message.value = "글꼴을 적용했습니다."
        }
    }

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
