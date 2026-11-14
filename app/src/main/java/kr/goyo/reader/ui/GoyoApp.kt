package kr.goyo.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.goyo.reader.LibraryViewModel
import kr.goyo.reader.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoyoApp(model: LibraryViewModel, activity: MainActivity) {
    val library by model.library.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val bookId by model.bookId.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val importing by model.importProgress.collectAsStateWithLifecycle()
    val plan by model.pendingImport.collectAsStateWithLifecycle()
    val book = library.books.firstOrNull { it.id == bookId }
    var settings by remember { mutableStateOf(false) }
    var importSheet by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.selectFiles(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::selectFolder) }
    val font = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importFont) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else {
                if (book != null) key(book.id) {
                    val sessionBook = remember(book.id) { book.copy(position = model.latestPosition(book)) }
                    ReaderScreen(activity, sessionBook, model.repository.fileFor(book), library.settings,
                        library.bookmarks.filter { it.bookId == book.id }.sortedBy { it.createdAt },
                        settingsVisible = settings, onSettings = { settings = true }, onBack = model::close,
                        onPosition = { model.reportPosition(book.id, it) },
                        onBookmark = { title, position -> model.addBookmark(book, title, position) },
                        onDeleteBookmark = model::deleteBookmark)
                } else HomeScreen(library.books, model::open, { importSheet = true }, { settings = true }, model::removeBook)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (book != null) 176.dp else 12.dp))
        }
    }
    if (settings) SettingsSheet(library.settings, model::updateSettings, { font.launch(arrayOf("*/*")) }, { settings = false })
    if (importSheet) {
        ModalBottomSheet(onDismissRequest = { importSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("서재에 책 가져오기", style = MaterialTheme.typography.headlineSmall)
                Text("선택한 파일을 이 기기에 복사합니다. 원본 파일은 유지됩니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { importSheet = false; files.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.NoteAdd, null); Spacer(Modifier.width(10.dp)); Text("파일 선택 · 여러 개 가능")
                }
                OutlinedButton(onClick = { importSheet = false; folder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(10.dp)); Text("폴더 선택")
                }
                Text("TXT · ZIP/CBZ · PDF · EPUB · JPG/PNG/WEBP\n이미지만 여러 장 선택하면 한 권으로 묶습니다. 폴더는 하위 폴더까지 확인한 후 가져올 권수를 보여 줍니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    plan?.let { selected ->
        AlertDialog(onDismissRequest = { model.pendingImport.value = null }, title = { Text("폴더에서 ${selected.bookCount}권 가져오기") }, text = { Text("문서 ${selected.documents.size}개, 이미지 폴더 ${selected.imageGroups.size}개\n파일 크기 약 ${humanSize(selected.bytes)}\n\n이미지는 폴더별로 한 권으로 묶고 원본은 그대로 둡니다.") }, confirmButton = { TextButton(onClick = model::confirmFolderImport) { Text("가져오기") } }, dismissButton = { TextButton(onClick = { model.pendingImport.value = null }) { Text("취소") } })
    }
    importing?.let { progress ->
        AlertDialog(onDismissRequest = {}, title = { Text("서재에 가져오는 중") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(progress.title)
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.complete.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                    Text("${progress.complete} / ${progress.total}권", style = MaterialTheme.typography.labelMedium)
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = {})
    }
    error?.let { detail ->
        AlertDialog(onDismissRequest = { model.error.value = null }, title = { Text("작업을 완료하지 못했습니다") }, text = { Text(detail) }, confirmButton = { TextButton(onClick = { model.error.value = null }) { Text("확인") } })
    }
}
