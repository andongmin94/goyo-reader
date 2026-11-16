package kr.goyo.reader

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
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
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val directory = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
        val screenshot = File(directory, "spec-settings-device.png")
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            screenshot.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        // AGP cleans up test applications. Export this diagnostic to the emulator's shared
        // Downloads before teardown; CI pulls it unconditionally, so missing captures fail.
        val copied = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "cp '${screenshot.absolutePath}' /sdcard/Download/min-viewer-settings.png && echo copied"
        )).bufferedReader().use { it.readText().trim() }
        assertEquals("copied", copied)
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").performClick()
        compose.waitUntil(5_000) { !model.library.value.settings.rightToLeft }
        compose.onNodeWithText("오른쪽에서 왼쪽으로 읽기").assertIsOff()
        assertEquals(19f, model.library.value.settings.fontSizeSp, 0f)
    }
}
