package kr.goyo.reader.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val visible = remember(browser.entries, query, sort, descending) { orderedEntries(browser.entries, query, sort, descending) }
    val favorite = library.favoritePaths.any { it.lastOrNull()?.uri == pathKey }
    BackHandler(tab != "files" || query.isNotEmpty() || browser.path.isNotEmpty()) {
        when { tab != "files" -> tab = "files"; query.isNotEmpty() -> query = ""; else -> onUp() }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("민뷰어", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("MIN VIEWER", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", { tab = "files"; onFolder() }, primary = true)
            HardwareKey(Icons.Outlined.Tune, "독서 설정", onSettings)
        }
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("files", "파일", Icons.Outlined.FolderOpen),
                    Triple("recent", "최근", Icons.Outlined.History),
                    Triple("favorites", "즐겨찾기", Icons.Outlined.StarOutline)).forEach { (value, label, icon) ->
                    HardwareKey(icon, label, { tab = value }, Modifier.weight(1f), primary = tab == value, showLabel = true)
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tab == "files") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onUp, enabled = browser.path.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "상위 폴더") }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Text("파일", Modifier.clickable { onNavigate(emptyList()) }.padding(8.dp), fontSize = 12.sp)
                        browser.path.forEachIndexed { index, part ->
                            Text("/", color = MaterialTheme.colorScheme.outline)
                            Text(part.name, Modifier.clickable { onNavigate(browser.path.take(index + 1)) }.padding(8.dp),
                                fontSize = 12.sp, maxLines = 1, fontWeight = if (index == browser.path.lastIndex) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    IconButton(onFavorite, enabled = browser.path.isNotEmpty()) {
                        Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            if (favorite) "즐겨찾기 해제" else "즐겨찾기 추가", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else Text(if (tab == "recent") "최근 읽은 파일" else "즐겨찾는 폴더", Modifier.padding(20.dp, 12.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("파일명 검색", fontSize = 14.sp) }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "검색 지우기") } },
                shape = RoundedCornerShape(10.dp))
            if (tab == "files") Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} 항목", Modifier.padding(start = 8.dp).weight(1f), fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    TextButton({ sortMenu = true }) { Text(when (sort) { FileSort.NAME -> "이름"; FileSort.SIZE -> "크기"; FileSort.MODIFIED -> "수정일" } + if (descending) " ↓" else " ↑") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        FileSort.entries.forEach { item -> DropdownMenuItem(
                            text = { Text(when (item) { FileSort.NAME -> "이름순"; FileSort.SIZE -> "크기순"; FileSort.MODIFIED -> "수정일순" }) },
                            onClick = { sort = item; sortMenu = false }) }
                        DropdownMenuItem(text = { Text(if (descending) "오름차순" else "내림차순") }, onClick = { descending = !descending; sortMenu = false })
                    }
                }
                IconButton({ previews = !previews }) { Icon(if (previews) Icons.Outlined.Image else Icons.Outlined.List,
                    if (previews) "썸네일 끄기" else "썸네일 켜기") }
                IconButton(onRefresh, enabled = !browser.loading) { Icon(Icons.Outlined.Refresh, "새로고침") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Do not restore a saved scroll position against a temporary one-row loading list.
            if (tab == "files" && browser.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else lists.SaveableStateProvider("$tab:$pathKey") {
                val scroll = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(bottom = 16.dp)) {
                    when (tab) {
                        "files" -> when {
                            browser.error != null -> item {
                                Column(Modifier.padding(24.dp)) {
                                    Text(browser.error, color = MaterialTheme.colorScheme.error)
                                    TextButton(onFolder) { Text("폴더 다시 선택") }
                                }
                            }
                            browser.path.isEmpty() -> item {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("읽을 파일이 있는 폴더를 선택하세요.", style = MaterialTheme.typography.titleMedium)
                                    Text("폴더 구조와 파일명을 그대로 탐색합니다. 원본 파일을 수정하거나 폴더 전체를 복사하지 않습니다.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", onFolder, primary = true, showLabel = true)
                                    TextButton(onFile) { Text("파일 하나 열기") }
                                }
                            }
                            else -> {
                                if (browser.entries.any { it.image }) item {
                                    TextButton(onReadImages, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                        Icon(Icons.Outlined.Collections, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("이 폴더 이미지 연속 읽기")
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
                                    modifier = Modifier.clickable { onRecent(book) },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
                            }
                        }
                        else -> {
                            val favorites = library.favoritePaths.filter { it.lastOrNull()?.name?.contains(query, true) == true }
                            if (favorites.isEmpty()) item { Text("폴더 경로 옆의 별을 눌러 즐겨찾기에 추가하세요.", Modifier.padding(24.dp)) }
                            items(favorites, key = { it.last().uri }) { path ->
                                ListItem(headlineContent = { Text(path.last().name) },
                                    supportingContent = { Text(path.joinToString(" / ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { tab = "files"; onNavigate(path) },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
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
    val preview by produceState<Bitmap?>(null, entry.uri, entry.modified, previews) {
        value = if (previews) load(entry) else null
    }
    val date = remember(entry.modified) { if (entry.modified == 0L) "" else SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(entry.modified)) }
    Row(Modifier.fillMaxWidth().clickable(enabled = entry.directory || entry.kind != null, onClick = open)
        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp, 44.dp), contentAlignment = Alignment.Center) {
            val bitmap = preview
            if (bitmap != null) Image(bitmap.asImageBitmap(), "${entry.name} 미리보기", Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Fit)
            else Icon(when { entry.directory -> Icons.Outlined.Folder; entry.image -> Icons.Outlined.Image
                entry.kind == BookKind.PDF -> Icons.Outlined.PictureAsPdf; entry.kind == BookKind.COMIC -> Icons.Outlined.FolderZip
                else -> Icons.Outlined.Description }, null, Modifier.size(28.dp),
                tint = if (entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (entry.directory) "폴더" else humanSize(entry.size), date.takeIf { it.isNotBlank() },
                progress?.let { "${(it * 100).toInt()}%" }).joinToString("  ·  "),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(info) { Icon(Icons.Outlined.MoreHoriz, "${entry.name} 정보", Modifier.size(20.dp)) }
    }
    HorizontalDivider(Modifier.padding(start = 70.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun HardwareKey(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, showLabel: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).padding(bottom = 3.dp)
        .offset(y = if (pressed) 2.dp else 0.dp)
        .shadow(if (pressed) 0.dp else 2.dp, shape)
        .clip(shape).background(background)
        .border(1.dp, if (primary) background else MaterialTheme.colorScheme.outlineVariant, shape)
        .clickable(source, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
        .padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = foreground)
            if (showLabel) Text(label, fontSize = 12.sp, color = foreground, maxLines = 1)
        }
    }
}
