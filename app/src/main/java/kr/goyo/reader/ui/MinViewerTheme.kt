package kr.goyo.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Locale

@Composable
fun MinViewerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF819BFF), onPrimary = Color(0xFF00175E),
        primaryContainer = Color(0xFF243665), onPrimaryContainer = Color(0xFFDDE5FF),
        secondary = Color(0xFFBDBDBF), secondaryContainer = Color(0xFF37383B),
        onSecondaryContainer = Color(0xFFE6E6E8),
        background = Color(0xFF191A1C), surface = Color(0xFF242527),
        onBackground = Color(0xFFF3F3F3), onSurface = Color(0xFFF3F3F3),
        onSurfaceVariant = Color(0xFFB5B5B8), outline = Color(0xFF88898D), outlineVariant = Color(0xFF414245),
        surfaceVariant = Color(0xFF323335), surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color(0xFF141517), surfaceContainerLow = Color(0xFF202123),
        surfaceContainer = Color(0xFF27282A), surfaceContainerHigh = Color(0xFF303133), surfaceContainerHighest = Color(0xFF393A3C),
    ) else lightColorScheme(
        primary = Color(0xFF0035FF), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDF1FF), onPrimaryContainer = Color(0xFF0035FF),
        secondary = Color(0xFF565656), secondaryContainer = Color(0xFFECECEC), onSecondaryContainer = Color(0xFF252525),
        background = Color.White, surface = Color.White,
        onBackground = Color(0xFF252525), onSurface = Color(0xFF252525),
        onSurfaceVariant = Color(0xFF717171), outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFFDEDEDE),
        surfaceVariant = Color(0xFFF0F0F0), surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF4F4F4), surfaceContainerHigh = Color(0xFFEEEEEE), surfaceContainerHighest = Color(0xFFE7E7E7),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
