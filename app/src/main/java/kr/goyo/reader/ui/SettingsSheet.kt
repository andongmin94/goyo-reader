package kr.goyo.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kr.goyo.reader.model.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onImportFont: () -> Unit,
    onDismiss: () -> Unit,
    kind: BookKind? = null,
) {
    var customColors by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val textOptions = kind == null || kind == BookKind.TEXT || kind == BookKind.EPUB
    val shown = settings.forAppearance(kind ?: BookKind.TEXT, colors.surface.luminance() < 0.5f)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.background) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("독서 설정", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    HardwareLegend("READER / CONTROL")
                }
            }
            item {
                HardwarePanel("01", "읽기 방식", Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HardwareKey(null, "한 장씩 넘기기", { onChange(settings.copy(readingMode = ReadingMode.PAGE)) }, Modifier.weight(1f), showLabel = true, stacked = true, active = settings.readingMode == ReadingMode.PAGE)
                        HardwareKey(null, "세로 스크롤", { onChange(settings.copy(readingMode = ReadingMode.SCROLL)) }, Modifier.weight(1f), showLabel = true, stacked = true, active = settings.readingMode == ReadingMode.SCROLL)
                    }
                    HardwareSwitch("오른쪽에서 왼쪽으로 읽기", settings.rightToLeft) { onChange(settings.copy(rightToLeft = it)) }
                    if (!textOptions) Text("페이지의 원본 색상은 유지합니다. 다크모드는 주변 배경과 조작부에 적용됩니다.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (textOptions) item {
                HardwarePanel("02", "글자와 여백", Modifier.fillMaxWidth()) {
                    Text("TXT·재조판형 EPUB에 적용됩니다.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    HardwareNumberStepper("글자 크기", settings.fontSizeSp.roundToInt(), 12..40, "sp") { onChange(settings.copy(fontSizeSp = it.toFloat())) }
                    FloatPreference("줄 간격", settings.lineSpacing, 1.1f..2.4f, { String.format(Locale.ROOT, "%.1f배", it) }) { onChange(settings.copy(lineSpacing = it)) }
                    FloatPreference("좌우 여백", settings.marginDp, 0f..48f, { "${it.roundToInt()} dp" }) { onChange(settings.copy(marginDp = it.roundToInt().toFloat())) }
                    HardwareRule(Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ReaderFont.SYSTEM to "기본", ReaderFont.SERIF to "명조", ReaderFont.MONOSPACE to "고정폭").forEach { (font, label) ->
                            HardwareKey(null, label, { onChange(settings.copy(font = font)) }, showLabel = true, active = settings.font == font)
                        }
                        if (settings.customFontPath != null) HardwareKey(null, "내 글꼴", { onChange(settings.copy(font = ReaderFont.CUSTOM)) }, showLabel = true, active = settings.font == ReaderFont.CUSTOM)
                    }
                    TextButton(onClick = onImportFont) { Text("TTF / OTF 글꼴 가져오기") }
                }
            }
            if (textOptions) item {
                HardwarePanel("03", "종이와 글자 색", Modifier.fillMaxWidth()) {
                    HardwareSwitch("본문도 시스템 테마 따르기", settings.followSystemTheme) { follow ->
                        onChange(settings.copy(followSystemTheme = follow,
                            backgroundColor = shown.backgroundColor, textColor = shown.textColor))
                    }
                    Text("색상을 직접 고르면 자동 전환을 끄고 선택한 색을 유지합니다.",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(Triple("종이", 0xFFF6F2E9L, 0xFF262A27L), Triple("흰색", 0xFFFFFFFFL, 0xFF202124L), Triple("세피아", 0xFFE9DAC1L, 0xFF43382BL), Triple("밤", 0xFF191A1CL, 0xFFE6E6E8L), Triple("검정", 0xFF000000L, 0xFFCCCCCCL)).forEach { (label, bg, fg) ->
                            val selected = !settings.followSystemTheme && settings.backgroundColor == bg && settings.textColor == fg
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(min = 48.dp).clickable {
                                    onChange(settings.copy(followSystemTheme = false, backgroundColor = bg, textColor = fg))
                                }.padding(3.dp)) {
                                Surface(Modifier.size(44.dp, 52.dp), shape = RoundedCornerShape(9.dp), color = Color(bg),
                                    border = BorderStroke(if (selected) 2.dp else 0.7.dp, if (selected) colors.primary else colors.outlineVariant)) {
                                    Box(contentAlignment = Alignment.Center) { Text("가", color = Color(fg), fontSize = 22.sp) }
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    TextButton(onClick = { customColors = true }) { Text("원하는 색 직접 지정") }
                }
            }
            item {
                HardwarePanel("04", "밝기", Modifier.fillMaxWidth()) {
                    HardwareSwitch("시스템 밝기 사용", settings.brightness < 0) { onChange(settings.copy(brightness = if (it) -1f else 0.5f)) }
                    if (settings.brightness >= 0) FloatPreference("독서 화면 밝기", settings.brightness, 0.01f..1f, { "${(it * 100).roundToInt()}%" }) { onChange(settings.copy(brightness = it)) }
                    FloatPreference("더 어둡게", settings.dimAmount, 0f..0.8f, { "${(it * 100).roundToInt()}%" }) { onChange(settings.copy(dimAmount = it)) }
                    HardwareSwitch("읽는 동안 화면 켜 두기", settings.keepScreenOn) { onChange(settings.copy(keepScreenOn = it)) }
                }
            }
            item {
                HardwarePanel("05", "터치와 버튼", Modifier.fillMaxWidth()) {
                    HardwareSwitch("볼륨 버튼으로 넘기기", settings.volumePaging) { onChange(settings.copy(volumePaging = it)) }
                    if (settings.volumePaging) HardwareSwitch("볼륨 버튼 방향 뒤집기", settings.reverseVolumeKeys) { onChange(settings.copy(reverseVolumeKeys = it)) }
                    HardwareRule(Modifier.fillMaxWidth())
                    Text("페이지 이동 터치 영역", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(TapLayout.EDGES to "양쪽 가장자리", TapLayout.LEFT_HANDED to "왼손 모드", TapLayout.ANYWHERE_NEXT to "어디든 다음").forEach { (layout, label) ->
                            HardwareKey(null, label, { onChange(settings.copy(tapLayout = layout)) }, showLabel = true, active = settings.tapLayout == layout)
                        }
                    }
                    Text("가운데를 누르면 도구가 나타납니다. ‘어디든 다음’에서는 오른쪽 아래 도구 버튼을 사용하세요.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            item {
                HardwarePanel("06", "자동 읽기", Modifier.fillMaxWidth()) {
                    Text("읽기 화면의 재생 버튼으로 시작하고 멈춥니다.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    FloatPreference("스크롤 속도", settings.autoScrollDpPerSecond, 5f..100f, { "${it.roundToInt()} dp/초" }) { onChange(settings.copy(autoScrollDpPerSecond = it)) }
                    HardwareNumberStepper("한 장당 읽는 시간", settings.autoPageSeconds, 2..40, "초") { onChange(settings.copy(autoPageSeconds = it)) }
                }
            }
            if (kind == null || kind == BookKind.TEXT) item {
                HardwarePanel("07", "TXT 인코딩", Modifier.fillMaxWidth()) {
                    Text("한글이 깨지면 파일에 맞는 인코딩을 선택하세요.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AUTO" to "자동", "UTF-8" to "UTF-8", "MS949" to "CP949", "EUC-KR" to "EUC-KR", "UTF-16" to "UTF-16").forEach { (encoding, label) ->
                            HardwareKey(null, label, { onChange(settings.copy(textEncoding = encoding)) }, showLabel = true, active = settings.textEncoding == encoding)
                        }
                    }
                }
            }
            item { HardwareKey(null, "읽기 설정 닫기", onDismiss, Modifier.fillMaxWidth(), primary = true, showLabel = true) }
        }
    }
    if (customColors) {
        var background by remember { mutableStateOf("#%06X".format(shown.backgroundColor and 0xFFFFFF)) }
        var foreground by remember { mutableStateOf("#%06X".format(shown.textColor and 0xFFFFFF)) }
        fun parse(value: String): Long? = if (Regex("#[0-9a-fA-F]{6}").matches(value)) value.drop(1).toLong(16) or 0xFF000000L else null
        AlertDialog(onDismissRequest = { customColors = false }, title = { Text("독서 색상") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(background, { background = it }, label = { Text("배경 (#RRGGBB)") }, singleLine = true)
                OutlinedTextField(foreground, { foreground = it }, label = { Text("글자 (#RRGGBB)") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = {
            onChange(settings.copy(followSystemTheme = false, backgroundColor = parse(background)!!, textColor = parse(foreground)!!)); customColors = false
        }, enabled = parse(background) != null && parse(foreground) != null) { Text("적용") } }, dismissButton = { TextButton(onClick = { customColors = false }) { Text("취소") } })
    }
}

/** Reference number-stepper layout: physical +/- keys around an inset numeric display. */
@Composable
internal fun HardwareNumberStepper(title: String, value: Int, range: IntRange, unit: String, change: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HardwareKey(Icons.Outlined.Remove, "$title 줄이기", { change(value - 1) }, enabled = value > range.first)
            Row(Modifier.weight(1f).heightIn(min = 48.dp).hardwareWell().padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$title 현재 값" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(value.toString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
                Text(" $unit", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HardwareKey(Icons.Outlined.Add, "$title 늘리기", { change(value + 1) }, enabled = value < range.last)
        }
    }
}

/** Show the draft while scrubbing; persist/reflow only once the user releases the control. */
@Composable
internal fun FloatPreference(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, change: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(format(draft), Modifier.hardwareWell(7.dp).padding(horizontal = 12.dp, vertical = 7.dp).semantics { contentDescription = "$title 현재 값" },
                style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
        HardwareSlider(draft, { draft = it }, { change(draft) }, valueRange = range, label = title)
    }
}
