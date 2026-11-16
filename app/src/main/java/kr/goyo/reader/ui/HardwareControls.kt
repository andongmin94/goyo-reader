package kr.goyo.reader.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A fixed well, a visible key skirt and a moving cap. Only the cap moves on press. */
@Composable
internal fun HardwareKey(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    showLabel: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    stacked: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateFloatAsState(if (pressed && enabled) 2.5f else 0f, tween(85), label = "key-travel")
    val view = LocalView.current
    val shape = RoundedCornerShape(10.dp)
    val face = if (primary) colors.primary else colors.surface
    val ink = if (primary) colors.onPrimary else colors.onSurface
    val skirt = if (primary) Color(0xFF001FAA) else if (light) Color(0xFFC9CCD0) else Color(0xFF101114)
    Box(
        modifier.height(if (stacked) 66.dp else 48.dp).widthIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = label; selected = active }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }.padding(2.dp)
            .background(if (light) Color(0xFFE4E5E8) else Color(0xFF111215), shape)
            .border(1.dp, if (light) Color(0xFFDADCDF) else Color(0xFF373A40), shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().padding(1.dp).padding(top = 3.dp).background(skirt, shape))
        Box(
            Modifier.matchParentSize().padding(1.dp).padding(bottom = 3.dp)
                .graphicsLayer { translationY = travel.dp.toPx(); alpha = if (enabled) 1f else 0.42f }
                .dropShadow(shape, Shadow(radius = 2.dp, color = Color.Black.copy(alpha = if (pressed) 0.04f else 0.17f), offset = DpOffset(0.dp, 2.dp)))
                .background(Brush.verticalGradient(listOf(face, if (primary) face else if (light) Color(0xFFF7F8F9) else Color(0xFF25272C))), shape)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light || primary) 0.85f else 0.13f), Color.Black.copy(alpha = 0.04f))), shape),
        )
        Column(
            Modifier.padding(horizontal = if (showLabel) 12.dp else 11.dp, vertical = 6.dp)
                .graphicsLayer { translationY = (travel - 1.5f).dp.toPx(); alpha = if (enabled) 1f else 0.42f },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (stacked) HardwareLed(active, modifier = Modifier.size(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = ink)
                if (showLabel) Text(label, color = ink, fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active && !stacked) HardwareLed(true, Modifier.align(Alignment.TopEnd).padding(5.dp).size(5.dp))
    }
}

@Composable
internal fun HardwareLed(on: Boolean, modifier: Modifier = Modifier) {
    val blue = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (on) drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension)
        drawCircle(if (on) blue else off, radius = size.minDimension * 0.32f)
        if (on) drawCircle(Color.White.copy(alpha = 0.8f), radius = size.minDimension * 0.1f, center = center - Offset(size.width * 0.08f, size.height * 0.08f))
    }
}

/** Recessed neutral surfaces, deliberately never cream or a large black LCD in light mode. */
@Composable
internal fun Modifier.hardwareWell(): Modifier {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val shape = RoundedCornerShape(12.dp)
    return this.background(if (light) Color(0xFFF5F6F7) else Color(0xFF141518), shape)
        .border(1.dp, if (light) Color(0xFFE0E2E5) else Color(0xFF34363D), shape)
        .innerShadow(shape, Shadow(radius = 3.dp, color = Color.Black.copy(alpha = if (light) 0.10f else 0.4f), offset = DpOffset(0.dp, 2.dp)))
}

@Composable
internal fun HardwareLegend(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 13.sp,
        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun HardwareSearchField(value: String, onValueChange: (String) -> Unit, leading: ImageVector, clear: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).hardwareWell().padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(leading, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        BasicTextField(value, onValueChange, Modifier.weight(1f).padding(vertical = 14.dp).semantics { contentDescription = "파일명 검색" },
            singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp),
            cursorBrush = Brush.solidColor(colors.primary), decorationBox = { field ->
                Box { if (value.isEmpty()) Text("파일명 검색", color = colors.onSurfaceVariant, fontSize = 14.sp); field() }
            })
        if (value.isNotEmpty()) IconButton({ onValueChange("") }) { Icon(clear, "검색 지우기", Modifier.size(18.dp)) }
        else Spacer(Modifier.width(8.dp))
    }
}

/** Native Slider behavior/semantics, with a ruler, inset rail and ridged physical thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HardwareSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    label: String = "읽기 진행률",
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(8.dp)) {
            for (i in 0..40) {
                val x = size.width * i / 40f
                val h = if (i % 10 == 0) 7.dp.toPx() else if (i % 5 == 0) 5.dp.toPx() else 3.dp.toPx()
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.38f), Offset(x, size.height), Offset(x, size.height - h), 1.dp.toPx())
            }
        }
        Slider(value, onValueChange, modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
            enabled = enabled, valueRange = valueRange, onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(Modifier.size(28.dp).dropShadow(RoundedCornerShape(5.dp), Shadow(2.dp, Color.Black.copy(alpha = 0.22f), offset = DpOffset(0.dp, 2.dp)))
                    .background(Brush.verticalGradient(listOf(colors.surface, if (light) Color(0xFFE3E5E8) else Color(0xFF34373C))), RoundedCornerShape(5.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(5.dp)).drawBehind {
                        for (i in -1..1) {
                            val x = center.x + i * 4.dp.toPx()
                            drawLine(colors.onSurfaceVariant.copy(alpha = 0.55f), Offset(x, 8.dp.toPx()), Offset(x, size.height - 8.dp.toPx()), 1.dp.toPx())
                        }
                    })
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                    drawRoundRect(if (light) Color(0xFFCDD0D5) else Color(0xFF0B0C0E), cornerRadius = CornerRadius(2.dp.toPx()))
                    val width = size.width * fraction
                    drawRoundRect(colors.primary.copy(alpha = if (enabled) 1f else 0.3f),
                        topLeft = Offset(if (rtl) size.width - width else 0f, 1.dp.toPx()),
                        size = Size(width, (size.height - 2.dp.toPx()).coerceAtLeast(0f)), cornerRadius = CornerRadius(1.dp.toPx()))
                    drawLine(Color.Black.copy(alpha = 0.12f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                }
            })
    }
}
