package kr.goyo.reader

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verify the real modal window and capture it using Android's compositor. */
@RunWith(AndroidJUnit4::class)
class HardwareSettingsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun stepperAndSwitchUpdateSettingsInTheNativeWindow() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
        compose.runOnIdle { model.updateSettings(model.library.value.settings.copy(fontSizeSp = 18f, rightToLeft = false)) }
        compose.waitUntil(5_000) { model.library.value.settings.fontSizeSp == 18f && !model.library.value.settings.rightToLeft }
        compose.onNodeWithContentDescription("독서 설정").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("글자 크기 늘리기")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("글자 크기 늘리기").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { model.library.value.settings.fontSizeSp == 19f }
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").assertIsOff().performClick()
        compose.waitUntil(5_000) { model.library.value.settings.rightToLeft }
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").assertIsOn()
        compose.waitForIdle()
        // A modal is a separate surface. A window-independent screen capture includes it.
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val directory = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
            File(directory, "spec-settings-device.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").performClick()
        compose.waitUntil(5_000) { !model.library.value.settings.rightToLeft }
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").assertIsOff()
        assertEquals(19f, model.library.value.settings.fontSizeSp, 0f)
    }
}
