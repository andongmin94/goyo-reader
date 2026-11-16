package kr.goyo.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.goyo.reader.LibraryViewModel
import kr.goyo.reader.MainActivity

@Composable
fun MinViewerApp(model: LibraryViewModel, activity: MainActivity) {
    val library by model.library.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val browser by model.browser.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val opening by model.opening.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val homeState = rememberSaveableStateHolder()
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::openPickedUri)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::selectFolder) }
    val font = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importFont) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val opened = session
            if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (opened != null) key(opened) {
                // Snapshot once; live position reports must not recreate the reader.
                val book = remember(opened) { opened.book.copy(position = model.latestPosition(opened.book)) }
                ReaderScreen(activity, book, opened.input, library.settings,
                    library.bookmarks.filter { it.bookId == opened.book.id }.sortedBy { it.createdAt },
                    settingsVisible = settings, onSettings = { settings = true }, onBack = model::close,
                    onPosition = { model.reportPosition(opened.book.id, it) },
                    onBookmark = { title, position -> model.addBookmark(opened.book, title, position) },
                    onDeleteBookmark = model::deleteBookmark)
            } else homeState.SaveableStateProvider("explorer") {
                HomeScreen(browser, library, model.repository::preview,
                    onFolder = { folder.launch(null) }, onFile = { file.launch(arrayOf("*/*")) },
                    onSelect = model::select, onNavigate = model::navigate, onUp = model::up,
                    onRefresh = model::refresh, onRecent = model::open, onForget = model::removeBook,
                    onFavorite = model::toggleFavorite, onReadImages = model::readImages,
                    onSettings = { settings = true })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
        }
    }
    if (settings) SettingsSheet(library.settings, model::updateSettings,
        { font.launch(arrayOf("*/*")) }, { settings = false }, kind = session?.book?.kind)
    if (opening) AlertDialog(onDismissRequest = model::cancelOpen, title = { Text("파일 여는 중") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("선택한 파일만 준비합니다. 원본은 변경하지 않습니다.")
        }
    }, confirmButton = { TextButton(model::cancelOpen) { Text("취소") } })
    error?.let { detail -> AlertDialog(onDismissRequest = { model.error.value = null },
        title = { Text("작업을 완료하지 못했습니다") }, text = { Text(detail) },
        confirmButton = { TextButton({ model.error.value = null }) { Text("확인") } }) }
}
