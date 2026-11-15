package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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

    @Test
    fun importedComicsShowTheirCoversAndOpenFromTheShelf() {
        val repository = (compose.activity.application as MinViewerApplication).library
        val text = temporary.newFile("종이 위의 산책.txt").apply {
            writeText("서두르지 않고 읽는 시간. 한 장을 넘길 때마다 새로운 풍경을 만납니다.\n\n".repeat(40))
        }
        val blue = sampleComic("수평선 너머.cbz", "BEYOND", "THE BLUE", 0xFF283E57.toInt(), 0xFFE8B98B.toInt())
        val quiet = sampleComic("고요한 시간.cbz", "QUIET", "HOURS", 0xFF354E45.toInt(), 0xFFDC9369.toInt())
        val imported = runBlocking {
            repository.import(repository.planFiles(listOf(text, blue, quiet).map(Uri::fromFile))) { _, _, _ -> }
        }
        val book = imported.single { it.title == "고요한 시간" }
        val secondBook = imported.single { it.title == "수평선 너머" }

        // These descriptions exist only after the real asynchronous cover has loaded.
        compose.waitUntil(10_000) {
            listOf(book, secondBook).all {
                compose.onAllNodes(hasContentDescription("${it.title} 표지 미리보기"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithContentDescription("${book.title} 표지 미리보기", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("${secondBook.title} 표지 미리보기", useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("library.png")

        compose.onNodeWithText(book.title).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("1쪽 · 1.png")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("1쪽 · 1.png").assertIsDisplayed()
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("2쪽 · 2.png")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("2쪽 · 2.png").assertIsDisplayed()
        saveScreenshot("comic-reader.png")
        assertTrue("Importing and opening a preview must preserve the source", quiet.exists())
    }

    private fun saveScreenshot(name: String) {
        val target = File("build/outputs/screenshots", name).apply { parentFile.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    // Original geometric artwork keeps the screenshots reproducible and free of external assets.
    private fun sampleComic(name: String, firstLine: String, secondLine: String, background: Int, accent: Int): File {
        val file = temporary.newFile(name)
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val paper = 0xFFF3EBDD.toInt()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                canvas.drawColor(background)
                paint.color = paper
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                paint.textSize = 60f
                canvas.drawText(firstLine, 38f, 102f, paint)
                canvas.drawText(secondLine, 38f, 170f, paint)
                paint.textSize = 16f
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText("MIN VIEWER  /  SAMPLE COLLECTION", 40f, 210f, paint)
                paint.color = accent
                canvas.drawCircle(320f, 350f, 86f, paint)
                paint.color = paper
                canvas.drawRoundRect(56f, 300f, 226f, 610f, 84f, 84f, paint)
                paint.color = background
                canvas.drawRoundRect(90f, 334f, 192f, 610f, 50f, 50f, paint)
                paint.color = paper
                canvas.drawRect(56f, 495f, 424f, 514f, paint)
                paint.textSize = 18f
                canvas.drawText("01", 388f, 596f, paint)
                zip.putNextEntry(ZipEntry("1.png"))
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
                zip.closeEntry()

                canvas.drawColor(paper)
                paint.color = background
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                paint.textSize = 28f
                canvas.drawText("A QUIET MORNING", 32f, 52f, paint)
                canvas.drawRect(32f, 78f, 448f, 350f, paint)
                paint.color = accent
                canvas.drawCircle(330f, 160f, 48f, paint)
                paint.color = 0xFF81947D.toInt()
                canvas.drawOval(-48f, 222f, 330f, 380f, paint)
                paint.color = paper
                canvas.drawRect(0f, 350f, 480f, 390f, paint)
                canvas.drawRect(0f, 78f, 32f, 350f, paint)
                canvas.drawRect(32f, 212f, 448f, 224f, paint)
                canvas.drawRect(235f, 78f, 247f, 350f, paint)
                paint.color = accent
                canvas.drawRect(32f, 382f, 448f, 562f, paint)
                paint.color = background
                canvas.drawOval(74f, 470f, 406f, 536f, paint)
                paint.color = paper
                canvas.drawRoundRect(186f, 406f, 278f, 494f, 12f, 12f, paint)
                canvas.drawCircle(282f, 440f, 27f, paint)
                paint.color = accent
                canvas.drawCircle(282f, 440f, 14f, paint)
                paint.color = background
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.textSize = 16f
                canvas.drawText("SLOW DOWN. TURN A PAGE.", 32f, 607f, paint)
                canvas.drawText("02", 423f, 607f, paint)
                zip.putNextEntry(ZipEntry("2.png"))
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
                zip.closeEntry()
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
