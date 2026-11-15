package kr.goyo.reader.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kr.goyo.reader.MainActivity
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    activity: MainActivity,
    book: BookRecord,
    input: ReaderInput,
    settings: ReaderSettings,
    bookmarks: List<Bookmark>,
    settingsVisible: Boolean,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onPosition: (ReadingPosition) -> Unit,
    onBookmark: (String, ReadingPosition) -> Unit,
    onDeleteBookmark: (String) -> Unit,
) {
    val initialPosition = remember(book.id) { if (input is ReaderInput.Images) input.resolve(book.position) else book.position }
    var position by remember(book.id) { mutableStateOf(initialPosition) }
    var progress by remember(book.id) { mutableFloatStateOf(initialPosition.progress.coerceIn(0f, 1f)) }
    var isSeeking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(ReaderInfo()) }
    var chrome by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var pageDialog by remember { mutableStateOf(false) }
    var bookmarkDialog by remember { mutableStateOf(false) }
    var readError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val commands = remember(book.id) { MutableSharedFlow<ReaderCommand>(extraBufferCapacity = 8) }
    val send: (ReaderCommand) -> Unit = { command -> scope.launch {
        commands.emit(if (command is ReaderCommand.Seek && input is ReaderInput.Images)
            ReaderCommand.Seek(input.resolve(command.position)) else command)
    } }
    val modalOpen = settingsVisible || sheet != null || pageDialog || bookmarkDialog
    val activeAuto = auto && !isSeeking && !modalOpen && readError == null
    val unit = if (book.kind == BookKind.EPUB) "위치" else "쪽"
    val background = Color(settings.backgroundColor)
    val ink = Color(settings.textColor)
    val surface = MaterialTheme.colorScheme.surface
    LaunchedEffect(position.progress) { if (!isSeeking) progress = position.progress.coerceIn(0f, 1f) }
    BackHandler { if (!chrome) chrome = true else onBack() }
    DisposableEffect(activity, settings.volumePaging, settings.reverseVolumeKeys, modalOpen, readError) {
        activity.volumeKeyHandler = if (settings.volumePaging && !modalOpen && readError == null) { down ->
            send(if (down xor settings.reverseVolumeKeys) ReaderCommand.Next else ReaderCommand.Previous)
        } else null
        onDispose { activity.volumeKeyHandler = null }
    }
    LaunchedEffect(activity, settings.brightness, settings.keepScreenOn, chrome, modalOpen, background, surface) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = settings.brightness }
        if (settings.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val light = (if (chrome || modalOpen) surface else background).luminance() > 0.5f
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
            if (chrome || modalOpen) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity, surface) {
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = surface.luminance() > 0.5f
                isAppearanceLightNavigationBars = surface.luminance() > 0.5f
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = background, topBar = {
            if (chrome) TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "폴더로 돌아가기") } },
                actions = { IconButton(onClick = { chrome = false }) { Icon(Icons.Outlined.Fullscreen, "도구 숨기고 읽기") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        }, bottomBar = {
            if (chrome) Surface(color = surface) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ send(ReaderCommand.Previous) }) { Icon(Icons.AutoMirrored.Outlined.NavigateBefore, "이전 $unit") }
                        Column(Modifier.weight(1f).clickable { pageDialog = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${info.current} / ${info.total} $unit", style = MaterialTheme.typography.labelLarge)
                            Text("${(position.progress * 100).toInt()}% · 눌러서 이동", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton({ send(ReaderCommand.Next) }) { Icon(Icons.AutoMirrored.Outlined.NavigateNext, "다음 $unit") }
                    }
                    Slider(progress, { isSeeking = true; progress = it }, onValueChangeFinished = {
                        val requested = progress
                        isSeeking = false
                        progress = position.progress.coerceIn(0f, 1f)
                        send(ReaderCommand.SeekProgress(requested))
                    }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton({ sheet = "search" }, enabled = info.supportsSearch) { Icon(Icons.Outlined.Search, "본문 검색") }
                        IconButton({ sheet = "contents" }, enabled = info.outline.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, "목차") }
                        IconButton({ sheet = "bookmarks" }) { Icon(Icons.Outlined.Bookmarks, "책갈피 목록") }
                        IconButton({ bookmarkDialog = true }) { Icon(Icons.Outlined.BookmarkAdd, "책갈피 추가") }
                        IconButton({ auto = !auto; if (auto) chrome = false }) {
                            Icon(if (auto) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (auto) "자동 읽기 정지" else "자동 읽기 시작")
                        }
                        IconButton(onSettings) { Icon(Icons.Outlined.Tune, "독서 설정") }
                    }
                }
            } else Row(Modifier.fillMaxWidth().background(background).navigationBarsPadding().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${info.current} / ${info.total}", color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (auto) IconButton({ auto = false }) { Icon(Icons.Outlined.Pause, "자동 읽기 정지", tint = ink) }
                IconButton({ chrome = true }) { Icon(Icons.Outlined.MoreHoriz, "읽기 도구 열기", tint = ink) }
            }
        }) { padding ->
            // The reader gets the actual free viewport: toolbars never cover a textbook's content.
            val viewport = Modifier.fillMaxSize().padding(padding)
            val reportPosition: (ReadingPosition) -> Unit = { position = it; onPosition(it) }
            val reportInfo: (ReaderInfo) -> Unit = { info = it }
            val results: (List<SearchResult>) -> Unit = { searchResults = it; searching = false; searched = true }
            val toggle: () -> Unit = { chrome = !chrome }
            val fail: (String) -> Unit = { readError = it; auto = false; chrome = true }
            val finish = { auto = false }
            if (readError == null) when (book.kind) {
                BookKind.TEXT -> TextReader((input as ReaderInput.LocalFile).file, initialPosition, settings, commands,
                    activeAuto, reportPosition, reportInfo, results, toggle, fail, finish, viewport)
                BookKind.COMIC, BookKind.PDF -> VisualReader(input, book.kind, initialPosition, settings, commands,
                    activeAuto, reportPosition, reportInfo, results, toggle, fail, finish, viewport)
                BookKind.EPUB -> EpubReader((input as ReaderInput.LocalFile).file, initialPosition, settings, commands,
                    activeAuto, reportPosition, reportInfo, results, toggle, fail, finish, viewport)
            } else Column(viewport.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("파일을 열지 못했습니다", color = ink, style = MaterialTheme.typography.titleLarge)
                Text(readError.orEmpty(), Modifier.padding(vertical = 16.dp), color = ink)
                TextButton(onBack) { Text("폴더로 돌아가기") }
            }
        }
        if (settings.dimAmount > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dimAmount)))
    }
    if (pageDialog) {
        var value by remember { mutableStateOf(info.current.toString()) }
        val target = value.toIntOrNull()
        AlertDialog(onDismissRequest = { pageDialog = false }, title = { Text("$unit 이동") }, text = {
            OutlinedTextField(value, { value = it }, label = { Text("1–${info.total}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }, confirmButton = { TextButton({ send(ReaderCommand.SeekPage(target!!)); pageDialog = false }, enabled = target != null && target in 1..info.total) { Text("이동") } },
            dismissButton = { TextButton({ pageDialog = false }) { Text("취소") } })
    }
    if (bookmarkDialog) {
        var title by remember { mutableStateOf("${info.current} $unit · ${(position.progress * 100).toInt()}%") }
        AlertDialog(onDismissRequest = { bookmarkDialog = false }, title = { Text("이곳에 책갈피") }, text = {
            OutlinedTextField(title, { title = it }, label = { Text("책갈피 이름") }, singleLine = true)
        }, confirmButton = { TextButton({ onBookmark(title.trim(), position); bookmarkDialog = false }, enabled = title.isNotBlank()) { Text("저장") } },
            dismissButton = { TextButton({ bookmarkDialog = false }) { Text("취소") } })
    }
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(when (sheet) { "search" -> "본문에서 찾기"; "contents" -> "목차"; else -> "책갈피" }, style = MaterialTheme.typography.headlineSmall) }
            when (sheet) {
                "search" -> {
                    item {
                        OutlinedTextField(query, { query = it }, label = { Text("검색어") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button({ searching = true; searchResults = emptyList(); send(ReaderCommand.Search(query.trim())) }, enabled = query.isNotBlank() && !searching) { Text("검색") }
                        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else if (searched) Text("검색 결과 ${searchResults.size}개", style = MaterialTheme.typography.labelMedium)
                    }
                    items(searchResults) { result -> ResultRow(result.title, result.excerpt) { send(ReaderCommand.Seek(result.position)); sheet = null; chrome = false } }
                }
                "contents" -> items(info.outline) { entry ->
                    Text(entry.title, Modifier.fillMaxWidth().clickable { send(ReaderCommand.Seek(entry.position)); sheet = null; chrome = false }
                        .padding(start = (entry.depth * 12).dp, top = 14.dp, bottom = 14.dp), style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    if (bookmarks.isEmpty()) item { Text("저장한 책갈피가 없습니다.") }
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { send(ReaderCommand.Seek(bookmark.position)); sheet = null; chrome = false }.padding(vertical = 12.dp)) {
                                Text(bookmark.title, style = MaterialTheme.typography.titleSmall)
                                Text("${(bookmark.position.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ onDeleteBookmark(bookmark.id) }) { Icon(Icons.Outlined.DeleteOutline, "책갈피 삭제") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, excerpt: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(excerpt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}
