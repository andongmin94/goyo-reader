package kr.goyo.reader.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kr.goyo.reader.BrowserState
import kr.goyo.reader.data.*
import kr.goyo.reader.model.*

@Composable
fun HomeScreen(
    browser: BrowserState,
    library: LibraryState,
    loadPreview: suspend (FileEntry) -> Bitmap?,
    onFolder: () -> Unit,
    onFile: () -> Unit,
    onSelect: (FileEntry) -> Unit,
    onNavigate: (List<FolderLocation>) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onRecent: (BookRecord) -> Unit,
    onForget: (BookRecord) -> Unit,
    onFavorite: () -> Unit,
    onReadImages: () -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf("files") }
    val pathKey = browser.path.lastOrNull()?.uri ?: "root"
    val pathScroll = rememberScrollState()
    LaunchedEffect(pathKey, pathScroll.maxValue) { pathScroll.scrollTo(pathScroll.maxValue) }
    var query by rememberSaveable(pathKey, tab) { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(FileSort.NAME) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var previews by rememberSaveable { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<FileEntry?>(null) }
    var forget by remember { mutableStateOf<BookRecord?>(null) }
    val lists = rememberSaveableStateHolder()
    val colors = MaterialTheme.colorScheme
    val visible = remember(browser.entries, query, sort, descending) { orderedEntries(browser.entries, query, sort, descending) }
    val favorite = library.favoritePaths.any { it.lastOrNull()?.uri == pathKey }
    BackHandler(tab != "files" || query.isNotEmpty() || browser.path.isNotEmpty()) {
        when { tab != "files" -> tab = "files"; query.isNotEmpty() -> query = ""; else -> onUp() }
    }
    // One continuous device face. Relief belongs to controls, not to floating screen frames.
    Scaffold(modifier = Modifier.testTag("browser-screen"), containerColor = colors.surface, topBar = {
        Surface(color = colors.surface) {
            Row(Modifier.fillMaxWidth().statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("민뷰어", Modifier.weight(1f), fontSize = 22.sp, lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
                HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", { tab = "files"; onFolder() }, primary = true)
                HardwareKey(Icons.Outlined.Tune, "독서 설정", onSettings)
            }
        }
    }, bottomBar = {
        Surface(color = colors.surface) {
            Column(Modifier.fillMaxWidth().testTag("browser-navigation").navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                HardwareRule()
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("files" to "파일", "recent" to "최근", "favorites" to "즐겨찾기").forEach { (value, label) ->
                        HardwareKey(null, label, { tab = value }, Modifier.weight(1f), active = tab == value, showLabel = true)
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tab == "files") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onUp, enabled = browser.path.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "상위 폴더", Modifier.size(18.dp)) }
                    Row(Modifier.weight(1f).horizontalScroll(pathScroll), verticalAlignment = Alignment.CenterVertically) {
                        Text("파일", Modifier.clickable { onNavigate(emptyList()) }.padding(horizontal = 6.dp, vertical = 14.dp), fontSize = 12.sp)
                        browser.path.forEachIndexed { index, part ->
                            Text("/", color = colors.onSurfaceVariant)
                            Text(part.name, Modifier.clickable { onNavigate(browser.path.take(index + 1)) }.padding(horizontal = 6.dp, vertical = 14.dp),
                                fontSize = 12.sp, maxLines = 1, fontWeight = if (index == browser.path.lastIndex) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    IconButton(onFavorite, enabled = browser.path.isNotEmpty()) {
                        Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            if (favorite) "즐겨찾기 해제" else "즐겨찾기 추가", Modifier.size(19.dp), tint = if (favorite) colors.primary else colors.onSurfaceVariant)
                    }
                }
            } else Text(if (tab == "recent") "최근 읽은 파일" else "즐겨찾는 폴더",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                HardwareSearchField(query, { query = it }, Icons.Outlined.Search, Icons.Outlined.Close)
            }
            if (tab == "files") Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HardwareLed(!browser.loading && browser.error == null && browser.path.isNotEmpty(), Modifier.size(8.dp))
                    Text("${visible.size} 항목", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                }
                Box {
                    HardwareKey(null, when (sort) { FileSort.NAME -> "이름"; FileSort.SIZE -> "크기"; FileSort.MODIFIED -> "수정일" } + if (descending) " ↓" else " ↑",
                        { sortMenu = true }, showLabel = true)
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        FileSort.entries.forEach { item -> DropdownMenuItem(
                            text = { Text(when (item) { FileSort.NAME -> "이름순"; FileSort.SIZE -> "크기순"; FileSort.MODIFIED -> "수정일순" }) },
                            onClick = { sort = item; sortMenu = false }) }
                        DropdownMenuItem(text = { Text(if (descending) "오름차순" else "내림차순") }, onClick = { descending = !descending; sortMenu = false })
                    }
                }
                HardwareKey(if (previews) Icons.Outlined.Image else Icons.AutoMirrored.Outlined.List,
                    if (previews) "썸네일 끄기" else "썸네일 켜기", { previews = !previews }, active = previews)
                HardwareKey(Icons.Outlined.Refresh, "새로고침", onRefresh, enabled = !browser.loading)
            }
            HardwareRule()
            Column(Modifier.weight(1f).fillMaxWidth()) {
                // Do not consume the saved position against an incomplete loading list.
                if (tab == "files" && browser.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else lists.SaveableStateProvider("$tab:$pathKey") {
                    val scroll = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().testTag("browser-file-list"), state = scroll,
                        contentPadding = PaddingValues(bottom = 8.dp)) {
                        when (tab) {
                            "files" -> when {
                                browser.error != null -> item {
                                    Column(Modifier.padding(20.dp)) {
                                        Text(browser.error, color = colors.error)
                                        TextButton(onFolder) { Text("폴더 다시 선택") }
                                    }
                                }
                                browser.path.isEmpty() -> item {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text("읽을 폴더를 선택하세요.", style = MaterialTheme.typography.titleMedium)
                                        Text("TXT · PDF · EPUB · 이미지 · ZIP\n원래 폴더에서 바로 읽습니다.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", onFolder, showLabel = true)
                                        TextButton(onFile) { Text("파일 하나 열기") }
                                    }
                                }
                                else -> {
                                    if (browser.entries.any { it.image }) item {
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onReadImages)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            Icon(Icons.Outlined.Collections, null, Modifier.size(17.dp), tint = colors.primary)
                                            Text("이 폴더 이미지 연속 읽기", color = colors.primary, fontSize = 12.sp)
                                        }
                                        HardwareRule(Modifier.padding(horizontal = 16.dp))
                                    }
                                    if (visible.isEmpty()) item { Text(if (query.isBlank()) "빈 폴더입니다." else "검색 결과가 없습니다.", Modifier.padding(20.dp)) }
                                    items(visible, key = { it.uri }) { entry ->
                                        val history = library.books.firstOrNull { it.uri == entry.uri }
                                        FileRow(entry, previews, loadPreview, history?.position?.progress, { onSelect(entry) }, { info = entry })
                                    }
                                }
                            }
                            "recent" -> {
                                val recent = library.books.filter { it.title.contains(query, true) }.sortedByDescending { it.lastOpenedAt }
                                if (recent.isEmpty()) item { Text("최근 읽은 파일이 없습니다.", Modifier.padding(20.dp)) }
                                items(recent, key = { it.id }) { book ->
                                    ListItem(headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                                        supportingContent = { Text("${(book.position.progress * 100).toInt()}% · ${book.folderPath.joinToString(" / ") { it.name }}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                                        leadingContent = { Icon(if (book.imageFolder) Icons.Outlined.Collections else Icons.Outlined.Description, null, Modifier.size(22.dp)) },
                                        trailingContent = { IconButton({ forget = book }) { Icon(Icons.Outlined.Close, "${book.title} 기록 지우기", Modifier.size(18.dp)) } },
                                        modifier = Modifier.clickable { onRecent(book) }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                                    HardwareRule(Modifier.padding(horizontal = 16.dp))
                                }
                            }
                            else -> {
                                val favorites = library.favoritePaths.filter { it.lastOrNull()?.name?.contains(query, true) == true }
                                if (favorites.isEmpty()) item { Text("폴더 경로 옆의 별을 눌러 즐겨찾기에 추가하세요.", Modifier.padding(20.dp)) }
                                items(favorites, key = { it.last().uri }) { path ->
                                    ListItem(headlineContent = { Text(path.last().name, fontSize = 14.sp) },
                                        supportingContent = { Text(path.joinToString(" / ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                                        leadingContent = { Icon(Icons.Outlined.Folder, null, Modifier.size(22.dp)) },
                                        modifier = Modifier.clickable { tab = "files"; onNavigate(path) }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                                    HardwareRule(Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    info?.let { entry -> AlertDialog(onDismissRequest = { info = null }, title = { Text(entry.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (entry.directory) "폴더" else "${entry.mime ?: entry.name.substringAfterLast('.', "파일")} · ${humanSize(entry.size)}")
            Text(browser.path.joinToString(" / ") { it.name }, style = MaterialTheme.typography.bodySmall)
            if (entry.kind == null && !entry.directory) Text("현재 지원하지 않는 형식입니다.")
        }
    }, confirmButton = { TextButton({ info = null }) { Text("닫기") } }) }
    forget?.let { book -> AlertDialog(onDismissRequest = { forget = null }, title = { Text("독서 기록을 지울까요?") },
        text = { Text("${book.title}\n읽던 위치와 책갈피를 지웁니다. 원본 파일은 그대로 남습니다.") },
        confirmButton = { TextButton({ onForget(book); forget = null }) { Text("기록 지우기") } },
        dismissButton = { TextButton({ forget = null }) { Text("취소") } }) }
}

@Composable
private fun FileRow(entry: FileEntry, previews: Boolean, load: suspend (FileEntry) -> Bitmap?, progress: Float?, open: () -> Unit, info: () -> Unit) {
    val preview by produceState<Bitmap?>(null, entry.uri, entry.modified, entry.size, previews) { value = if (previews) load(entry) else null }
    val date = remember(entry.modified) { if (entry.modified == 0L) "" else SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(entry.modified)) }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable { if (entry.directory || entry.kind != null) open() else info() }
        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp, 42.dp), contentAlignment = Alignment.Center) {
            val bitmap = preview
            if (bitmap != null) Image(bitmap.asImageBitmap(), "${entry.name} 미리보기", Modifier.fillMaxSize().hardwareWell(5.dp).padding(2.dp).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Fit)
            else Icon(when { entry.directory -> Icons.Outlined.Folder; entry.image -> Icons.Outlined.Image
                entry.kind == BookKind.PDF -> Icons.Outlined.PictureAsPdf; entry.kind == BookKind.COMIC -> Icons.Outlined.FolderZip
                else -> Icons.Outlined.Description }, null, Modifier.size(24.dp), tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.name, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (entry.directory) "폴더" else humanSize(entry.size), date.takeIf { it.isNotBlank() },
                progress?.let { "${(it * 100).toInt()}%" }).joinToString(" · "),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant)
        }
        IconButton(info) { Icon(Icons.Outlined.MoreHoriz, "${entry.name} 정보", Modifier.size(18.dp)) }
    }
    HardwareRule(Modifier.padding(start = 62.dp, end = 16.dp))
}
