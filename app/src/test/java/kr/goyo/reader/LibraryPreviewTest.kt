package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MinViewerApplication::class, qualifiers = "w320dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()
    @Test fun folderPreviewsOpenTheSelectedImageAndPreserveNavigationAndSearch() {
        val folder = temporary.newFolder("만화")
        image(File(folder, "page10.png"), Color.BLUE)
        image(File(folder, "page2.png"), Color.GREEN)
        File(folder, "읽기 안내.txt").writeText("원본 이미지를 바로 읽습니다.")
        File(folder, "외전").mkdir()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].selectFolder(Uri.fromFile(temporary.root)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("만화")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("만화").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("page2.png 미리보기"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("page2.png 미리보기", useUnmergedTree = true).assertIsDisplayed()
        screenshot("library.png")
        compose.onNode(hasSetTextAction()).performTextReplacement("page2")
        compose.onNodeWithText("page10.png").assertDoesNotExist()
        compose.onNodeWithText("page2.png").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("1쪽 · page2.png")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("2쪽 · page10.png")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("comic-reader.png")
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("page2.png")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("page10.png").assertDoesNotExist()
        compose.onNodeWithContentDescription("검색 지우기").performClick()
        compose.onNodeWithText("page10.png").assertIsDisplayed()
        compose.onNodeWithContentDescription("즐겨찾기 추가").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("즐겨찾기 해제")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("상위 폴더").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("만화")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("page2.png").assertDoesNotExist()
        assertTrue(File(folder, "page2.png").isFile)
        assertFalse(File(compose.activity.cacheDir, "reader-documents").exists())
    }
    private fun image(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { this.color = color }
            canvas.drawRect(20f, 20f, 220f, 200f, paint)
            paint.color = Color.DKGRAY; paint.textSize = 20f
            canvas.drawText(file.name, 20f, 270f, paint)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
    private fun screenshot(name: String) {
        val target = File("build/outputs/screenshots", name).apply { parentFile.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
