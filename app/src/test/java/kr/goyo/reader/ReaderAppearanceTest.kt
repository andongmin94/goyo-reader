package kr.goyo.reader

import kr.goyo.reader.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReaderAppearanceTest {
    @Test fun textFormatsFollowTheSystemWithoutMutatingSavedColors() {
        val original = ReaderSettings()
        for (kind in listOf(BookKind.TEXT, BookKind.EPUB)) {
            val dark = original.forAppearance(kind, true)
            assertEquals(0xFF191A1CL, dark.backgroundColor)
            assertEquals(0xFFE6E6E8L, dark.textColor)
            assertEquals(0xFFFFFFFFL, original.forAppearance(kind, false).backgroundColor)
            assertEquals(0xFFFFFFFFL, original.backgroundColor)
            assertEquals(original.fontSizeSp, dark.fontSizeSp, 0f)
        }
    }
    @Test fun anExplicitPaletteSurvivesLightAndDarkModes() {
        val manual = ReaderSettings(followSystemTheme = false, backgroundColor = 0xFFE9DAC1L, textColor = 0xFF43382BL)
        for (kind in listOf(BookKind.TEXT, BookKind.EPUB)) {
            assertSame(manual, manual.forAppearance(kind, false))
            assertSame(manual, manual.forAppearance(kind, true))
        }
    }
    @Test fun visualFormatsUseANeutralStageNotTheNovelPalette() {
        val manual = ReaderSettings(followSystemTheme = false, backgroundColor = 0xFFE9DAC1L, textColor = 0xFF43382BL)
        for (kind in listOf(BookKind.PDF, BookKind.COMIC)) {
            assertEquals(0xFF101113L, manual.forAppearance(kind, true).backgroundColor)
            assertEquals(0xFFECEDEFL, manual.forAppearance(kind, false).backgroundColor)
        }
        assertEquals(0xFFE9DAC1L, manual.backgroundColor)
    }
    @Test fun explicitThemePreferenceIsPersistedWithSettings() {
        val settings = ReaderSettings(followSystemTheme = false, backgroundColor = 0xFF000000L)
        assertEquals(settings, Json.decodeFromString<ReaderSettings>(Json.encodeToString(settings)))
    }
}
