package kr.goyo.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
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
@Config(sdk = [35], qualifiers = "w320dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HardwareControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keysRemainAccessibleAndDisabledKeysDoNotInvokeActions() {
        val clicks = AtomicInteger()
        compose.setContent {
            MinViewerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HardwareKey(Icons.Outlined.FolderOpen, "폴더 선택", { clicks.incrementAndGet() }, active = true)
                        HardwareKey(Icons.Outlined.Refresh, "새로고침", { clicks.addAndGet(100) }, enabled = false)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("폴더 선택").assertIsSelected().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("새로고침").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, clicks.get())
    }

    @Test fun rulerSliderRetainsTheNativeAccessibilitySeekAction() {
        val committed = AtomicReference(-1f)
        compose.setContent {
            MinViewerTheme {
                var value by remember { mutableFloatStateOf(0.2f) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp)) {
                        HardwareSlider(value, { value = it }, { committed.set(value) })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("읽기 진행률").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.75f)) }
        compose.waitForIdle()
        assertEquals(0.75f, committed.get(), 0.0001f)
    }

    @Test fun settingsDisplayTheDraftWithoutSavingOrReflowingUntilRelease() {
        val committed = AtomicReference<Float?>(null)
        compose.setContent {
            MinViewerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp)) {
                        FloatPreference("글자 크기", 20f, 12f..40f, { "${it.roundToInt()} sp" }) { committed.set(it) }
                    }
                }
            }
        }
        val slider = compose.onNodeWithContentDescription("글자 크기")
        slider.performTouchInput { down(Offset(width * 0.8f, height / 2f)) }
        compose.waitForIdle()
        compose.onNodeWithText("20 sp").assertDoesNotExist()
        assertNull("Dragging must not repeatedly persist settings or repaginate a novel", committed.get())
        slider.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(requireNotNull(committed.get()) > 20f)
    }

    @Test @Config(qualifiers = "w320dp-h891dp-night")
    fun darkControlsRenderAndKeepTheSameSelectionSemantics() {
        compose.setContent {
            MinViewerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HardwareLegend("MIN / VIEWER")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HardwareKey(Icons.Outlined.FolderOpen, "파일", {}, primary = true)
                            HardwareKey(Icons.Outlined.Refresh, "새로고침", {})
                        }
                        HardwareKey(null, "즐겨찾기", {}, Modifier.fillMaxWidth(), showLabel = true, stacked = true, active = true)
                        HardwareSlider(0.6f, {}, {})
                        FloatPreference("글자 크기", 22f, 12f..40f, { "${it.roundToInt()} sp" }) {}
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("즐겨찾기").assertIsSelected().assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            val output = File("build/outputs/screenshots/hardware-dark.png").apply { parentFile.mkdirs() }
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
