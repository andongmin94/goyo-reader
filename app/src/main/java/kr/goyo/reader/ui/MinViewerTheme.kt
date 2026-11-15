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
        primary = Color(0xFF7190FF), onPrimary = Color(0xFF06164B),
        primaryContainer = Color(0xFF162653), onPrimaryContainer = Color(0xFFDCE5FF),
        background = Color(0xFF151619), surface = Color(0xFF1E2024),
        onBackground = Color(0xFFF4F5F7), onSurface = Color(0xFFF4F5F7),
        onSurfaceVariant = Color(0xFFA7ABB4), outlineVariant = Color(0xFF353840),
    ) else lightColorScheme(
        primary = Color(0xFF0035FF), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDF1FF), onPrimaryContainer = Color(0xFF0035FF),
        background = Color.White, surface = Color.White,
        onBackground = Color(0xFF202124), onSurface = Color(0xFF202124),
        onSurfaceVariant = Color(0xFF696F7A), outlineVariant = Color(0xFFE4E6EB),
        surfaceVariant = Color(0xFFF5F6F8), surfaceTint = Color.Transparent,
    )
    MaterialTheme(colorScheme = colors, content = content)
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
