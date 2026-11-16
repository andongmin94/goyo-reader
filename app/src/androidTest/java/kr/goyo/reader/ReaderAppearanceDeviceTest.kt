package kr.goyo.reader

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kr.goyo.reader.model.ReaderSettings
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Real local files, real PDF pixels and a real Readium WebView, in both Android themes. */
@RunWith(AndroidJUnit4::class)
class ReaderAppearanceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun model() = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]

    @After fun restoreLightTheme() { forceTheme(false) }

    @Test fun allFiveFormatsRenderInBothThemesWithoutInvertingVisualDocuments() {
        val directory = samples()
        for (dark in listOf(false, true)) {
            forceTheme(dark)
            browse(directory)
            val theme = if (dark) "dark" else "light"
            capture("directory-$theme.png")
            for ((name, format) in listOf(
                ReadingSamples.textName to "txt", ReadingSamples.pdfName to "pdf",
                ReadingSamples.imageName to "png", ReadingSamples.zipName to "zip", ReadingSamples.epubName to "epub",
            )) {
                open(name)
                assertReadingSpace()
                when (format) {
                    "txt" -> assertViewportCorner(if (dark) 0xFF191A1C.toInt() else Color.WHITE)
                    "epub" -> {
                        val expected = if (dark) "rgb(230, 230, 232)" else "rgb(32, 33, 36)"
                        var body = JSONObject()
                        compose.waitUntil(25_000) {
                            body = epubBody()
                            body.optString("text").contains(ReadingSamples.marker) && body.optString("color") == expected
                        }
                        assertTrue(body.optString("text").contains(ReadingSamples.marker))
                        compose.onNodeWithContentDescription("목차").assertIsDisplayed()
                        compose.onNodeWithContentDescription("본문 검색").assertIsEnabled()
                    }
                    else -> assertOriginalVisualColors()
                }
                capture("$format-$theme.png")
                compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("browser-file-list")).fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test fun explicitPaperChoiceStaysWhiteInDarkModeAndPdfSettingsAreRelevant() {
        forceTheme(true)
        browse(samples())
        open(ReadingSamples.textName)
        assertViewportCorner(0xFF191A1C.toInt())
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) { model().latestPosition(requireNotNull(model().session.value).book).index > 0 }
        val position = compose.runOnIdle { model().latestPosition(requireNotNull(model().session.value).book).index }
        compose.onNodeWithContentDescription("독서 설정").performClick()
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("본문도 시스템 테마 따르기"))
        compose.onNodeWithText("본문도 시스템 테마 따르기").assertIsOn()
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("흰색"))
        compose.onNodeWithText("흰색").performClick()
        compose.waitUntil(5_000) { !model().library.value.settings.followSystemTheme && model().library.value.settings.backgroundColor == 0xFFFFFFFFL }
        closeSettings()
        assertViewportCorner(Color.WHITE)
        assertEquals(position, compose.runOnIdle { model().latestPosition(requireNotNull(model().session.value).book).index })
        capture("txt-custom-white-in-dark.png")
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        open(ReadingSamples.pdfName)
        compose.onNodeWithContentDescription("독서 설정").performClick()
        compose.onNodeWithText("시스템 밝기 사용").assertIsDisplayed()
        compose.onNodeWithContentDescription("글자 크기 늘리기").assertDoesNotExist()
        capture("pdf-settings-dark.png")
        closeSettings()
        assertOriginalVisualColors()
    }

    private fun samples(): File = temporary.newFolder("내 자료").also {
        ReadingSamples.create(it)
        // Two loose image entries exercise sequence input as well as the ZIP input.
        File(it, ReadingSamples.imageName).copyTo(File(it, "02-관측일지.png"))
    }

    private fun forceTheme(dark: Boolean) {
        instrumentation.runOnMainSync {
            instrumentation.targetContext.getSystemService(UiModeManager::class.java)
                .setApplicationNightMode(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        }
        compose.waitUntil(10_000) {
            var night = 0
            compose.activityRule.scenario.onActivity { night = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK }
            night == if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        compose.waitForIdle()
    }

    private fun browse(directory: File) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            model().updateSettings(ReaderSettings(fontSizeSp = 18f, marginDp = 24f, lineSpacing = 1.55f))
            model().selectFolder(Uri.fromFile(directory))
        }
        compose.waitUntil(10_000) { !model().browser.value.loading && model().library.value.settings.followSystemTheme &&
            compose.onAllNodes(hasText(ReadingSamples.textName)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun open(name: String) {
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription("책갈피 추가") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun closeSettings() {
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("읽기 설정 닫기"))
        compose.onNodeWithText("읽기 설정 닫기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("읽기 설정 닫기")).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    private fun assertReadingSpace() {
        val screen = compose.onNodeWithTag("reader-screen").fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("reader-viewport").fetchSemanticsNode().boundsInRoot
        val tools = compose.onNodeWithTag("reader-tools").fetchSemanticsNode().boundsInRoot
        assertTrue("A real reading area, not floating frames: $page / $screen", page.height >= screen.height * 0.50f)
        assertTrue("Controls must not cover the document", page.bottom <= tools.top + 1f)
        assertEquals(screen.width, page.width, 1f)
        compose.onNodeWithContentDescription("독서 설정").assertIsDisplayed()
    }

    private fun assertViewportCorner(expected: Int) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("reader-viewport").captureToImage().asAndroidBitmap()
        try { assertEquals(expected, bitmap.getPixel(2, 2)) } finally { bitmap.recycle() }
    }

    private fun assertOriginalVisualColors() {
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("1쪽 ·", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val bitmap = compose.onNode(hasContentDescription("1쪽 ·", substring = true)).captureToImage().asAndroidBitmap()
        try {
            var white = 0
            var blue = 0
            for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                val rgb = bitmap.getPixel(x, y) and 0xFFFFFF
                if (rgb == 0xFFFFFF) white++
                if (rgb == 0x0035FF) blue++
            }
            assertTrue("The original white page must stay white", white > 100)
            assertTrue("The document's blue content must not be inverted", blue > 5)
        } finally { bitmap.recycle() }
    }

    private fun View.webViews(): List<WebView> = when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

    private fun epubBody(): JSONObject {
        val result = AtomicReference(JSONObject())
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val web = compose.activity.window.decorView.webViews().firstOrNull { it.isShown }
            if (web == null) done.countDown()
            else web.evaluateJavascript("(function(){var b=document.body;var p=document.querySelector('p')||b;return JSON.stringify({text:b?b.innerText:'',color:p?getComputedStyle(p).color:''});})()") { encoded ->
                val decoded = JSONTokener(encoded).nextValue()
                if (decoded is String) result.set(JSONObject(decoded))
                done.countDown()
            }
        }
        check(done.await(3, TimeUnit.SECONDS)) { "WebView did not answer the content check" }
        return result.get()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = instrumentation.uiAutomation
        val directory = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
        val file = File(directory, name)
        val bitmap = checkNotNull(automation.takeScreenshot())
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        // Keep captures available after AGP uninstalls the test application.
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/Download/min-viewer-formats")).use { it.readBytes() }
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/min-viewer-formats/$name")).use { it.readBytes() }
        println("FORMAT_CAPTURE $name")
    }
}
