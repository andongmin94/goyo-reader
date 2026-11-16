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
    Scaffold(containerColor = colors.background, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp, 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("민뷰어", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                HardwareLegend("MIN / VIEWER")
            }
            HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", { tab = "files"; onFolder() }, primary = true)
            HardwareKey(Icons.Outlined.Tune, "독서 설정", onSettings)
        }
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp)) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).hardwareWell().padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(Triple("files", "파일", Icons.Outlined.FolderOpen),
                    Triple("recent", "최근", Icons.Outlined.History),
                    Triple("favorites", "즐겨찾기", Icons.Outlined.StarOutline)).forEach { (value, label, icon) ->
                    HardwareKey(icon, label, { tab = value }, Modifier.weight(1f), active = tab == value, showLabel = true, stacked = true)
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tab == "files") {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().hardwareWell().padding(6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HardwareLegend("DIRECTORY", Modifier.weight(1f))
                        HardwareLed(browser.path.isNotEmpty() && browser.error == null, Modifier.size(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onUp, enabled = browser.path.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "상위 폴더", Modifier.size(19.dp)) }
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            Text("파일", Modifier.clickable { onNavigate(emptyList()) }.padding(8.dp), fontSize = 12.sp)
                            browser.path.forEachIndexed { index, part ->
                                Text("/", color = colors.outline)
                                Text(part.name, Modifier.clickable { onNavigate(browser.path.take(index + 1)) }.padding(8.dp),
                                    fontSize = 12.sp, maxLines = 1, fontWeight = if (index == browser.path.lastIndex) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                        IconButton(onFavorite, enabled = browser.path.isNotEmpty()) {
                            Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                if (favorite) "즐겨찾기 해제" else "즐겨찾기 추가", Modifier.size(19.dp),
                                tint = if (favorite) colors.primary else colors.onSurfaceVariant)
                        }
                    }
                }
            } else Column(Modifier.padding(20.dp, 10.dp)) {
                HardwareLegend(if (tab == "recent") "HISTORY" else "FAVORITES")
                Text(if (tab == "recent") "최근 읽은 파일" else "즐겨찾는 폴더", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                HardwareSearchField(query, { query = it }, Icons.Outlined.Search, Icons.Outlined.Close)
            }
            if (tab == "files") Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(visible.size.toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = colors.primary)
                    HardwareLegend("항목")
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
            HorizontalDivider(color = colors.outlineVariant)
            if (tab == "files" && browser.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else lists.SaveableStateProvider("$tab:$pathKey") {
                val scroll = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(bottom = 16.dp)) {
                    when (tab) {
                        "files" -> when {
                            browser.error != null -> item {
                                Column(Modifier.padding(24.dp)) {
                                    Text(browser.error, color = colors.error)
                                    TextButton(onFolder) { Text("폴더 다시 선택") }
                                }
                            }
                            browser.path.isEmpty() -> item {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    HardwareLegend("LOCAL FILE READER")
                                    Text("읽을 파일이 있는 폴더를 선택하세요.", style = MaterialTheme.typography.titleMedium)
                                    Text("폴더 구조와 파일명을 그대로 탐색합니다. 원본 파일을 수정하거나 폴더 전체를 복사하지 않습니다.",
                                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", onFolder, primary = true, showLabel = true)
                                    TextButton(onFile) { Text("파일 하나 열기") }
                                }
                            }
                            else -> {
                                if (browser.entries.any { it.image }) item {
                                    Row(Modifier.fillMaxWidth().clickable(onClick = onReadImages).padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Outlined.Collections, null, Modifier.size(18.dp), tint = colors.primary)
                                        Text("이 폴더 이미지 연속 읽기", color = colors.primary, fontSize = 12.sp)
                                    }
                                }
                                if (visible.isEmpty()) item { Text(if (query.isBlank()) "빈 폴더입니다." else "검색 결과가 없습니다.", Modifier.padding(24.dp)) }
                                items(visible, key = { it.uri }) { entry ->
                                    val history = library.books.firstOrNull { it.uri == entry.uri }
                                    FileRow(entry, previews, loadPreview, history?.position?.progress,
                                        { onSelect(entry) }, { info = entry })
                                }
                            }
                        }
                        "recent" -> {
                            val recent = library.books.filter { it.title.contains(query, true) }.sortedByDescending { it.lastOpenedAt }
                            if (recent.isEmpty()) item { Text("최근 읽은 파일이 없습니다.", Modifier.padding(24.dp)) }
                            items(recent, key = { it.id }) { book ->
                                ListItem(headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${(book.position.progress * 100).toInt()}% · ${book.folderPath.joinToString(" / ") { it.name }}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(if (book.imageFolder) Icons.Outlined.Collections else Icons.Outlined.Description, null) },
                                    trailingContent = { IconButton({ forget = book }) { Icon(Icons.Outlined.Close, "${book.title} 기록 지우기") } },
                                    modifier = Modifier.clickable { onRecent(book) }, colors = ListItemDefaults.colors(containerColor = colors.background))
                            }
                        }
                        else -> {
                            val favorites = library.favoritePaths.filter { it.lastOrNull()?.name?.contains(query, true) == true }
                            if (favorites.isEmpty()) item { Text("폴더 경로 옆의 별을 눌러 즐겨찾기에 추가하세요.", Modifier.padding(24.dp)) }
                            items(favorites, key = { it.last().uri }) { path ->
                                ListItem(headlineContent = { Text(path.last().name) },
                                    supportingContent = { Text(path.joinToString(" / ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(Icons.Outlined.Folder, null, tint = colors.primary) },
                                    modifier = Modifier.clickable { tab = "files"; onNavigate(path) }, colors = ListItemDefaults.colors(containerColor = colors.background))
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
    val preview by produceState<Bitmap?>(null, entry.uri, entry.modified, entry.size, previews) {
        value = if (previews) load(entry) else null
    }
    val date = remember(entry.modified) { if (entry.modified == 0L) "" else SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(entry.modified)) }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable { if (entry.directory || entry.kind != null) open() else info() }
        .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp, 50.dp).hardwareWell().padding(5.dp), contentAlignment = Alignment.Center) {
            val bitmap = preview
            if (bitmap != null) Image(bitmap.asImageBitmap(), "${entry.name} 미리보기", Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Fit)
            else Icon(when { entry.directory -> Icons.Outlined.Folder; entry.image -> Icons.Outlined.Image
                entry.kind == BookKind.PDF -> Icons.Outlined.PictureAsPdf; entry.kind == BookKind.COMIC -> Icons.Outlined.FolderZip
                else -> Icons.Outlined.Description }, null, Modifier.size(24.dp), tint = if (entry.directory) colors.primary else colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.name, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (entry.directory) "폴더" else humanSize(entry.size), date.takeIf { it.isNotBlank() },
                progress?.let { "${(it * 100).toInt()}%" }).joinToString(" · "),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp, color = colors.onSurfaceVariant)
        }
        IconButton(info) { Icon(Icons.Outlined.MoreHoriz, "${entry.name} 정보", Modifier.size(20.dp)) }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp, end = 18.dp), color = colors.outlineVariant.copy(alpha = 0.65f))
}
