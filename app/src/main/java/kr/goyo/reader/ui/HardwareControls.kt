package kr.goyo.reader.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** All relief uses the same overhead light: white upper rim, contact shadow below. */
@Composable
internal fun Modifier.hardwarePanel(radius: Dp = 22.dp): Modifier {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val shape = RoundedCornerShape(radius)
    return dropShadow(shape, Shadow(9.dp, Color.Black.copy(alpha = if (light) 0.09f else 0.28f), offset = DpOffset(0.dp, 4.dp)))
        .dropShadow(shape, Shadow(1.dp, Color.Black.copy(alpha = 0.09f), offset = DpOffset(0.dp, 1.dp)))
        .background(Brush.verticalGradient(if (light) listOf(Color.White, Color(0xFFFAFAFA)) else listOf(Color(0xFF292A2C), Color(0xFF222325))), shape)
        .border(0.7.dp, if (light) Color(0xFFDDDDDD) else Color(0xFF3C3D40), shape)
        .innerShadow(shape, Shadow(1.dp, Color.White.copy(alpha = if (light) 0.95f else 0.08f), offset = DpOffset(0.dp, 1.dp)))
}

@Composable
internal fun HardwarePanel(number: String, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.hardwarePanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HardwareCaption(number, title)
        content()
    }
}

@Composable
internal fun HardwareCaption(number: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(number, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        HardwareLegend(title)
        HardwareRule(Modifier.weight(1f))
    }
}

@Composable
internal fun HardwareRule(modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    Column(modifier.height(2.dp)) {
        Spacer(Modifier.fillMaxWidth().height(0.6.dp).background(if (light) Color(0xFFE0E0E0) else Color(0xFF17181A)))
        Spacer(Modifier.fillMaxWidth().height(0.8.dp).background(Color.White.copy(alpha = if (light) 0.9f else 0.08f)))
    }
}

/** The contact shadow is separate from the cap. Layout and touch target never move. */
@Composable
internal fun HardwareKey(
    icon: ImageVector?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, showLabel: Boolean = false, active: Boolean = false,
    enabled: Boolean = true, stacked: Boolean = false, round: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val travel by animateFloatAsState(if (pressed && enabled) 1.8f else 0f, tween(90), label = "key-travel")
    val view = LocalView.current
    val shape = if (round) CircleShape else RoundedCornerShape(13.dp)
    val face = when { primary -> listOf(Color(0xFF174AFF), Color(0xFF0035F4)); light -> listOf(Color(0xFFEFEFEF), Color(0xFFFCFCFC)); else -> listOf(Color(0xFF3A3B3E), Color(0xFF303134)) }
    val ink = if (primary) Color.White else colors.onSurface
    Box(modifier.heightIn(min = if (stacked) 66.dp else 52.dp).widthIn(min = 52.dp)
        .semantics(mergeDescendants = true) { contentDescription = label; selected = active }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }.padding(3.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().padding(top = 2.dp)
            .dropShadow(shape, Shadow(3.dp, Color.Black.copy(alpha = if (light) 0.16f else 0.36f), offset = DpOffset(0.dp, 2.dp)))
            .background(if (primary) Color(0xFF0021B5) else if (light) Color(0xFFD6D6D6) else Color(0xFF151618), shape))
        Box(Modifier.matchParentSize().padding(bottom = 2.dp)
            .graphicsLayer { translationY = travel.dp.toPx() }
            .background(Brush.verticalGradient(face.map { if (enabled) it else if (light) Color(0xFFECECEC) else Color(0xFF28292B) }), shape)
            .border(if (focused) 1.5.dp else 0.7.dp, if (focused) colors.primary else if (primary) Color(0xFF0034DD) else if (light) Color(0xFFDADADA) else Color(0xFF46474B), shape)
            .innerShadow(shape, Shadow(1.dp, Color.White.copy(alpha = if (light && !primary) 1f else 0.28f), offset = DpOffset(0.dp, 1.dp))))
        Column(Modifier.padding(horizontal = if (showLabel) 14.dp else 12.dp, vertical = if (stacked) 12.dp else 13.dp)
            .graphicsLayer { translationY = (travel - 1f).dp.toPx(); alpha = if (enabled) 1f else 0.38f },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (stacked) HardwareLed(active, Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (icon != null && !stacked) Icon(icon, null, Modifier.size(18.dp), tint = ink)
                if (showLabel) Text(label, color = ink, fontSize = 12.sp, lineHeight = 17.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active && !stacked) HardwareLed(true, Modifier.align(Alignment.TopEnd).padding(6.dp).size(7.dp))
    }
}

@Composable
internal fun HardwareLed(on: Boolean, modifier: Modifier = Modifier) {
    val blue = MaterialTheme.colorScheme.primary
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    Canvas(modifier) {
        if (on) drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = 0.6f), Color.Transparent)), radius = size.minDimension * 0.85f)
        drawCircle(if (on) blue else if (light) Color(0xFFC6C6C3) else Color(0xFF55565A), radius = size.minDimension * 0.3f)
        drawCircle(Color.White.copy(alpha = if (on) 0.75f else 0.35f), radius = size.minDimension * 0.1f, center = center - Offset(size.width * 0.07f, size.height * 0.09f))
    }
}

@Composable
internal fun Modifier.hardwareWell(radius: Dp = 11.dp): Modifier {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val shape = RoundedCornerShape(radius)
    return background(if (light) Color(0xFFEEEEEE) else Color(0xFF17181A), shape)
        .border(0.7.dp, if (light) Color(0xFFDADADA) else Color(0xFF3B3C40), shape)
        .innerShadow(shape, Shadow(3.dp, Color.Black.copy(alpha = if (light) 0.12f else 0.45f), offset = DpOffset(0.dp, 1.5.dp)))
        .innerShadow(shape, Shadow(1.dp, Color.White.copy(alpha = if (light) 0.85f else 0.06f), offset = DpOffset(0.dp, (-1).dp)))
}

@Composable
internal fun HardwareLegend(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp,
        letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun HardwareSearchField(value: String, onValueChange: (String) -> Unit, leading: ImageVector, clear: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).hardwareWell().padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(leading, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        BasicTextField(value, onValueChange, Modifier.weight(1f).padding(vertical = 14.dp).semantics { contentDescription = "파일명 검색" },
            singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp), cursorBrush = SolidColor(colors.primary),
            decorationBox = { field -> Box { if (value.isEmpty()) Text("파일명 검색", color = colors.onSurfaceVariant, fontSize = 14.sp); field() } })
        if (value.isNotEmpty()) IconButton({ onValueChange("") }) { Icon(clear, "검색 지우기", Modifier.size(18.dp)) }
        else Spacer(Modifier.width(8.dp))
    }
}

/** A rectangular sliding switch; the entire labelled row is one accessible toggle. */
@Composable
internal fun HardwareSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val blue = MaterialTheme.colorScheme.primary
    val view = LocalView.current
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(120), label = "switch-position")
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(checked, role = Role.Switch) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onCheckedChange(it)
        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(62.dp, 34.dp).hardwareWell(9.dp).padding(3.dp)) {
            Canvas(Modifier.align(Alignment.CenterStart).size(26.dp, 24.dp)) {
                drawRoundRect(blue.copy(alpha = position * 0.95f), topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                    size = Size(17.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
            }
            Box(Modifier.offset(x = (28 * position).dp).size(28.dp)
                .dropShadow(RoundedCornerShape(6.dp), Shadow(2.dp, Color.Black.copy(alpha = 0.2f), offset = DpOffset(0.dp, 1.dp)))
                .background(Brush.verticalGradient(if (light) listOf(Color.White, Color(0xFFE8E8E8)) else listOf(Color(0xFF4C4D50), Color(0xFF343538))), RoundedCornerShape(6.dp))
                .border(0.7.dp, if (light) Color.White else Color(0xFF626368), RoundedCornerShape(6.dp))
                .drawBehind {
                    for (i in -1..1) {
                        val x = center.x + i * 3.dp.toPx()
                        drawLine(if (light) Color(0xFFB7B7B7) else Color(0xFF7D7E82), Offset(x, 8.dp.toPx()), Offset(x, size.height - 8.dp.toPx()), 0.7.dp.toPx())
                    }
                })
        }
    }
}

/** Native gesture, keyboard and accessibility behavior; only the rail and cap are styled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HardwareSlider(
    value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true, label: String = "읽기 진행률",
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(9.dp)) {
            for (i in 0..40) {
                val x = size.width * i / 40f
                val h = if (i % 10 == 0) 7.dp.toPx() else if (i % 5 == 0) 5.dp.toPx() else 3.dp.toPx()
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.38f), Offset(x, size.height), Offset(x, size.height - h), 0.7.dp.toPx())
            }
        }
        Slider(value, onValueChange, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
            enabled = enabled, valueRange = valueRange, onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(Modifier.size(30.dp, 32.dp)
                    .dropShadow(RoundedCornerShape(7.dp), Shadow(3.dp, Color.Black.copy(alpha = 0.18f), offset = DpOffset(0.dp, 2.dp)))
                    .background(Brush.verticalGradient(if (light) listOf(Color(0xFFFDFDFD), Color(0xFFE8E8E8)) else listOf(Color(0xFF494A4D), Color(0xFF303134))), RoundedCornerShape(7.dp))
                    .border(0.7.dp, if (light) Color.White else Color(0xFF5D5E62), RoundedCornerShape(7.dp))
                    .drawBehind {
                        for (i in -1..1) {
                            val x = center.x + i * 4.dp.toPx()
                            drawLine(colors.onSurfaceVariant.copy(alpha = 0.5f), Offset(x, 9.dp.toPx()), Offset(x, size.height - 9.dp.toPx()), 0.7.dp.toPx())
                        }
                        drawLine(colors.primary, Offset(center.x, 3.dp.toPx()), Offset(center.x, 6.dp.toPx()), 1.dp.toPx())
                    })
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawRoundRect(if (light) Color(0xFFD1D1D1) else Color(0xFF0D0E10), cornerRadius = CornerRadius(3.dp.toPx()))
                    val width = size.width * fraction
                    drawRoundRect(colors.primary.copy(alpha = if (enabled) 0.9f else 0.25f), topLeft = Offset(if (rtl) size.width - width else 0f, 1.dp.toPx()),
                        size = Size(width, (size.height - 2.dp.toPx()).coerceAtLeast(0f)), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawLine(Color.Black.copy(alpha = 0.14f), Offset.Zero, Offset(size.width, 0f), 0.7.dp.toPx())
                }
            })
    }
}
