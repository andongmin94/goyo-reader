package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Real Android render/interaction checks. Folder selection UI and reboot grants are separate device QA. */
@RunWith(AndroidJUnit4::class)
class ReaderFormatsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    @Test fun pdfOpensRendersAndResumesFromTheDirectory() {
        val pdf = temporary.newFile("교재.pdf")
        val document = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            pdf.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        browse()
        compose.onNodeWithText(pdf.name).performClick()
        waitImage("1쪽 · 1쪽", Color.RED)
        compose.onNodeWithContentDescription("본문 검색").assertIsNotEnabled()
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        waitImage("2쪽 · 2쪽", Color.BLUE)
        screenshot("pdf-reader.png")
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        waitText(pdf.name)
        compose.onNodeWithText(pdf.name).performClick()
        waitImage("2쪽 · 2쪽", Color.BLUE)
        assertTrue(pdf.isFile)
    }
    @Test fun loosePngImagesFollowTheFolderOrderWithoutRepacking() {
        File(temporary.root, "2.png").writeBytes(image(Color.RED))
        File(temporary.root, "10.png").writeBytes(image(Color.BLUE))
        browse()
        compose.onNodeWithText("2.png").performClick()
        waitImage("1쪽 · 2.png", Color.RED)
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        waitImage("2쪽 · 10.png", Color.BLUE)
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        waitText("2.png")
        screenshot("directory.png")
        val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
        val records = model.library.value.books.filter { it.folderPath.lastOrNull()?.uri == Uri.fromFile(temporary.root).toString() }
        assertEquals(1, records.size)
        assertTrue(records.single().imageFolder)
        assertFalse(model.repository.fileFor(records.single()).exists())
    }
    @Test fun zipComicUsesNaturalOrderInsideSubfolders() {
        val file = temporary.newFile("만화.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            listOf("chapter/10.png" to Color.BLUE, "chapter/2.png" to Color.RED,
                "__MACOSX/cover.png" to Color.GREEN).forEach { (name, color) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(image(color)); zip.closeEntry()
            }
        }
        browse()
        compose.onNodeWithText(file.name).performClick()
        waitImage("1쪽 · chapter/2.png", Color.RED)
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        waitImage("2쪽 · chapter/10.png", Color.BLUE)
        compose.onNodeWithText("2 / 2 쪽").assertIsDisplayed()
        assertTrue(file.isFile)
    }
    @Test fun koreanTxtSearchAndBookmarkWorkFromTheDirectory() {
        val file = temporary.newFile("소설.txt").apply { writeText("한글 소설을 읽습니다.\n표적문장입니다.\n\n".repeat(80)) }
        browse()
        compose.onNodeWithText(file.name).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("본문 검색") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("본문 검색").performClick()
        compose.onNodeWithText("검색어").performTextReplacement("표적문장")
        compose.onNodeWithText("검색", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("검색 결과 80개")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("책갈피 추가").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("다시 읽기")
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithContentDescription("책갈피 목록").performClick()
        waitText("다시 읽기")
        assertTrue(file.isFile)
    }
    private fun browse() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].selectFolder(Uri.fromFile(temporary.root)) }
        compose.waitUntil(10_000) { !ViewModelProvider(compose.activity)[LibraryViewModel::class.java].browser.value.loading &&
            compose.onAllNodes(hasText(temporary.root.listFiles()!!.first().name)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitText(value: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(value).assertIsDisplayed()
    }
    private fun waitImage(description: String, expectedColor: Int) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
        val bitmap = compose.onNodeWithContentDescription(description).captureToImage().asAndroidBitmap()
        try { assertEquals(expectedColor, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
        finally { bitmap.recycle() }
    }
    private fun image(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray() }
        } finally { bitmap.recycle() }
    }
    private fun screenshot(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(directory, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
