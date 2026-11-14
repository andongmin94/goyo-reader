package kr.goyo.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kr.goyo.reader.model.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, onImportFont: () -> Unit, onDismiss: () -> Unit) {
    var customColors by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text("나에게 맞는 읽기", style = MaterialTheme.typography.headlineSmall) }
            item {
                SectionTitle("읽기 방식")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(settings.readingMode == ReadingMode.PAGE, { onChange(settings.copy(readingMode = ReadingMode.PAGE)) }, label = { Text("한 장씩 넘기기") })
                    FilterChip(settings.readingMode == ReadingMode.SCROLL, { onChange(settings.copy(readingMode = ReadingMode.SCROLL)) }, label = { Text("세로 스크롤") })
                }
                TogglePreference("오른쪽에서 왼쪽으로 읽기", settings.rightToLeft) { onChange(settings.copy(rightToLeft = it)) }
            }
            item {
                SectionTitle("글자와 여백", "글자 설정은 TXT·EPUB에 적용됩니다.")
                FloatPreference("글자 크기", settings.fontSizeSp, 12f..40f, "${settings.fontSizeSp.roundToInt()} sp") { onChange(settings.copy(fontSizeSp = it.roundToInt().toFloat())) }
                FloatPreference("줄 간격", settings.lineSpacing, 1.1f..2.4f, "%.1f배".format(settings.lineSpacing)) { onChange(settings.copy(lineSpacing = it)) }
                FloatPreference("좌우 여백", settings.marginDp, 0f..48f, "${settings.marginDp.roundToInt()} dp") { onChange(settings.copy(marginDp = it.roundToInt().toFloat())) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReaderFont.SYSTEM to "기본", ReaderFont.SERIF to "명조", ReaderFont.MONOSPACE to "고정폭").forEach { (font, label) ->
                        FilterChip(settings.font == font, { onChange(settings.copy(font = font)) }, label = { Text(label) })
                    }
                    if (settings.customFontPath != null) FilterChip(settings.font == ReaderFont.CUSTOM, { onChange(settings.copy(font = ReaderFont.CUSTOM)) }, label = { Text("내 글꼴") })
                }
                TextButton(onClick = onImportFont) { Text("TTF / OTF 글꼴 가져오기") }
            }
            item {
                SectionTitle("종이와 글자 색")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("종이", 0xFFF6F2E9L, 0xFF262A27L), Triple("흰색", 0xFFFFFFFFL, 0xFF202124L), Triple("세피아", 0xFFE9DAC1L, 0xFF43382BL), Triple("밤", 0xFF202725L, 0xFFDBE2DCL), Triple("검정", 0xFF000000L, 0xFFCCCCCCL)).forEach { (label, bg, fg) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onChange(settings.copy(backgroundColor = bg, textColor = fg)) }) {
                            Surface(Modifier.size(42.dp), shape = CircleShape, color = Color(bg), border = androidx.compose.foundation.BorderStroke(if (settings.backgroundColor == bg) 3.dp else 1.dp, MaterialTheme.colorScheme.outline)) {
                                Box(contentAlignment = Alignment.Center) { Text("가", color = Color(fg)) }
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
                TextButton(onClick = { customColors = true }) { Text("원하는 색 직접 지정") }
            }
            item {
                SectionTitle("밝기")
                TogglePreference("시스템 밝기 사용", settings.brightness < 0) { onChange(settings.copy(brightness = if (it) -1f else 0.5f)) }
                if (settings.brightness >= 0) FloatPreference("독서 화면 밝기", settings.brightness, 0.01f..1f, "${(settings.brightness * 100).roundToInt()}%") { onChange(settings.copy(brightness = it)) }
                FloatPreference("더 어둡게", settings.dimAmount, 0f..0.8f, "${(settings.dimAmount * 100).roundToInt()}%") { onChange(settings.copy(dimAmount = it)) }
                TogglePreference("읽는 동안 화면 켜 두기", settings.keepScreenOn) { onChange(settings.copy(keepScreenOn = it)) }
            }
            item {
                SectionTitle("터치와 버튼")
                TogglePreference("볼륨 버튼으로 넘기기", settings.volumePaging) { onChange(settings.copy(volumePaging = it)) }
                if (settings.volumePaging) TogglePreference("볼륨 버튼 방향 뒤집기", settings.reverseVolumeKeys) { onChange(settings.copy(reverseVolumeKeys = it)) }
                Text("페이지 이동 터치 영역", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TapLayout.EDGES to "양쪽 가장자리", TapLayout.LEFT_HANDED to "왼손 모드", TapLayout.ANYWHERE_NEXT to "어디든 다음").forEach { (layout, label) ->
                        FilterChip(settings.tapLayout == layout, { onChange(settings.copy(tapLayout = layout)) }, label = { Text(label) })
                    }
                }
                Text("가운데를 누르면 도구가 나타납니다. ‘어디든 다음’에서는 오른쪽 아래 도구 버튼을 사용하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionTitle("자동 읽기", "읽기 화면의 재생 버튼으로 시작하고 멈춥니다.")
                FloatPreference("스크롤 속도", settings.autoScrollDpPerSecond, 5f..100f, "${settings.autoScrollDpPerSecond.roundToInt()} dp/초") { onChange(settings.copy(autoScrollDpPerSecond = it)) }
                FloatPreference("한 장당 읽는 시간", settings.autoPageSeconds.toFloat(), 2f..40f, "${settings.autoPageSeconds}초") { onChange(settings.copy(autoPageSeconds = it.roundToInt())) }
            }
            item {
                SectionTitle("TXT 인코딩", "한글이 깨지면 파일에 맞는 인코딩을 선택하세요.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AUTO" to "자동", "UTF-8" to "UTF-8", "MS949" to "CP949", "EUC-KR" to "EUC-KR", "UTF-16" to "UTF-16").forEach { (encoding, label) ->
                        FilterChip(settings.textEncoding == encoding, { onChange(settings.copy(textEncoding = encoding)) }, label = { Text(label) })
                    }
                }
            }
            item { FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("읽기 설정 닫기") } }
        }
    }
    if (customColors) {
        var background by remember { mutableStateOf("#%06X".format(settings.backgroundColor and 0xFFFFFF)) }
        var foreground by remember { mutableStateOf("#%06X".format(settings.textColor and 0xFFFFFF)) }
        fun parse(value: String): Long? = if (Regex("#[0-9a-fA-F]{6}").matches(value)) value.drop(1).toLong(16) or 0xFF000000L else null
        AlertDialog(onDismissRequest = { customColors = false }, title = { Text("독서 색상") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(background, { background = it }, label = { Text("배경 (#RRGGBB)") }, singleLine = true)
                OutlinedTextField(foreground, { foreground = it }, label = { Text("글자 (#RRGGBB)") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = { onChange(settings.copy(backgroundColor = parse(background)!!, textColor = parse(foreground)!!)); customColors = false }, enabled = parse(background) != null && parse(foreground) != null) { Text("적용") } }, dismissButton = { TextButton(onClick = { customColors = false }) { Text("취소") } })
    }
}

@Composable
private fun SectionTitle(title: String, description: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TogglePreference(title: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(enabled, change)
    }
}

@Composable
private fun FloatPreference(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, change: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(draft, { draft = it }, valueRange = range, onValueChangeFinished = { change(draft) })
}
