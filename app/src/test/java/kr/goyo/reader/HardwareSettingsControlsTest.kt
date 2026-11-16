package kr.goyo.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import kr.goyo.reader.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HardwareSettingsControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labelledSwitchIsOneAccessibleToggleAndChangesOncePerClick() {
        val changes = AtomicInteger()
        compose.setContent {
            MinViewerTheme {
                var checked by remember { mutableStateOf(false) }
                Surface {
                    HardwarePanel("01", "INPUT", Modifier.padding(16.dp)) {
                        HardwareSwitch("볼륨 버튼으로 넘기기", checked) { checked = it; changes.incrementAndGet() }
                    }
                }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).assertIsOff().assertHeightIsAtLeast(48.dp).performClick().assertIsOn()
        assertEquals(1, changes.get())
    }

    @Test fun numberStepperRespectsBothLimits() {
        compose.setContent {
            MinViewerTheme {
                var value by remember { mutableIntStateOf(39) }
                Column(Modifier.padding(16.dp)) { HardwareNumberStepper("글자 크기", value, 12..40, "sp") { value = it } }
            }
        }
        compose.onNodeWithContentDescription("글자 크기 늘리기").performClick().assertIsNotEnabled()
        compose.onNodeWithText("40").assertIsDisplayed()
        repeat(28) { compose.onNodeWithContentDescription("글자 크기 줄이기").performClick() }
        compose.onNodeWithContentDescription("글자 크기 줄이기").assertIsNotEnabled()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithContentDescription("글자 크기 늘리기").assertIsEnabled()
    }

    @Test fun largeKoreanLabelsGrowTheKeyInsteadOfClipping() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MinViewerTheme {
                    HardwareKey(null, "즐겨찾기", {}, Modifier.width(96.dp), showLabel = true, stacked = true, active = true)
                }
            }
        }
        compose.onNodeWithContentDescription("즐겨찾기").assertIsSelected().assertHeightIsAtLeast(66.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("즐겨찾기", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
    }
}
