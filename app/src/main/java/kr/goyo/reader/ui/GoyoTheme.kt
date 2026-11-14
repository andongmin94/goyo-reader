package kr.goyo.reader.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val GoyoColors = lightColorScheme(
    primary = Color(0xFF496A58), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8DC), onPrimaryContainer = Color(0xFF213A2B),
    secondary = Color(0xFF8C6954), secondaryContainer = Color(0xFFF0E1D5),
    background = Color(0xFFF6F2E9), surface = Color(0xFFFCFAF5),
    surfaceVariant = Color(0xFFECE9DF), onSurface = Color(0xFF262A27),
    onSurfaceVariant = Color(0xFF676B63), outlineVariant = Color(0xFFDADDD2),
)

@Composable
fun GoyoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GoyoColors, typography = Typography().let {
        it.copy(headlineLarge = it.headlineLarge.copy(fontFamily = FontFamily.Serif), headlineMedium = it.headlineMedium.copy(fontFamily = FontFamily.Serif))
    }, content = content)
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}
