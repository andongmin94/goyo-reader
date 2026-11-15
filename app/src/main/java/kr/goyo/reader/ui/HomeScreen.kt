package kr.goyo.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.goyo.reader.R
import kr.goyo.reader.model.BookKind
import kr.goyo.reader.model.BookRecord

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    books: List<BookRecord>,
    loadCover: suspend (BookRecord) -> Bitmap?,
    onOpen: (BookRecord) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onRemove: (BookRecord) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<BookKind?>(null) }
    var sortByTitle by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf<BookRecord?>(null) }
    val visible = remember(books, query, kind, sortByTitle) {
        books.filter { (kind == null || it.kind == kind) && it.title.contains(query, true) }.let {
            if (sortByTitle) it.sortedBy { b -> b.title.lowercase() }
            else it.sortedWith(compareByDescending<BookRecord> { b -> b.lastOpenedAt }.thenByDescending { b -> b.addedAt })
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium) }, actions = {
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, "독서 설정") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onImport, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("책 가져오기") }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("나만의 오프라인 서재", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("조용히, 한 장씩.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(7.dp))
                Text("${books.size}권의 책 · 광고 없이 읽는 시간", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }
            if (books.isEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(24.dp))
                            Text("첫 번째 책을 펼쳐 보세요", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("TXT · ZIP/CBZ · PDF · EPUB\n이미지 파일과 이미지 폴더도 읽을 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            FilledTonalButton(onClick = onImport) { Text("파일 또는 폴더 선택") }
                            Spacer(Modifier.height(12.dp))
                            Text("선택한 파일을 서재로 복사하고 원본은 유지합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("제목으로 찾기") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "전체", BookKind.TEXT to "소설", BookKind.COMIC to "만화", BookKind.PDF to "PDF", BookKind.EPUB to "EPUB").forEach { (value, label) ->
                            FilterChip(kind == value, { kind = value }, label = { Text(label) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("내 책 ${visible.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { sortByTitle = !sortByTitle }) { Text(if (sortByTitle) "제목순 ↓" else "최근 읽은 순 ↓") }
                    }
                }
                if (visible.isEmpty()) item { Text("조건에 맞는 책이 없습니다.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(visible, key = { it.id }) { book ->
                    BookCard(book, loadCover, onClick = { onOpen(book) }, onRemove = { remove = book })
                }
            }
        }
    }
    remove?.let { book ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("서재에서 삭제할까요?") }, text = { Text("${book.title}\n\n가져온 사본과 책갈피를 삭제합니다. 원본 파일은 그대로 남습니다.") }, confirmButton = { TextButton(onClick = { onRemove(book); remove = null }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("취소") } })
    }
}

@Composable
private fun BookCard(book: BookRecord, loadCover: suspend (BookRecord) -> Bitmap?, onClick: () -> Unit, onRemove: () -> Unit) {
    val cover by produceState<Bitmap?>(null, book.id) { value = loadCover(book) }
    val color = when (book.kind) { BookKind.TEXT -> Color(0xFF496A58); BookKind.COMIC -> Color(0xFF92654D); BookKind.PDF -> Color(0xFF696F89); BookKind.EPUB -> Color(0xFF7D6B46) }
    val format = when (book.kind) { BookKind.TEXT -> "TXT"; BookKind.COMIC -> "COMIC"; else -> book.kind.name }
    val coverShape = RoundedCornerShape(topStart = 3.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 3.dp)
    Card(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp, 96.dp).clip(coverShape).background(color), contentAlignment = Alignment.Center) {
                val bitmap = cover
                if (bitmap != null) Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "${book.title} 표지 미리보기",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                ) else Text(format, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("$format · ${humanSize(book.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(if (book.lastOpenedAt == 0L) "아직 펼치지 않은 책" else "${(book.position.progress * 100).toInt()}% 읽음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.lastOpenedAt > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { book.position.progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(3.dp), color = color, trackColor = color.copy(alpha = 0.12f))
                }
            }
            IconButton(onRemove) { Icon(Icons.Outlined.DeleteOutline, "${book.title} 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
