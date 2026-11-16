package kr.goyo.reader.model

/** Resolve display colors without overwriting the user's saved custom palette.
 * Visual document colors are never transformed: only the surrounding stage changes. */
internal fun ReaderSettings.forAppearance(kind: BookKind, dark: Boolean): ReaderSettings = when {
    kind == BookKind.PDF || kind == BookKind.COMIC -> copy(
        backgroundColor = if (dark) 0xFF101113L else 0xFFECEDEFL,
        textColor = if (dark) 0xFFE6E6E8L else 0xFF252525L,
    )
    !followSystemTheme -> this
    else -> copy(
        backgroundColor = if (dark) 0xFF191A1CL else 0xFFFFFFFFL,
        textColor = if (dark) 0xFFE6E6E8L else 0xFF202124L,
    )
}
