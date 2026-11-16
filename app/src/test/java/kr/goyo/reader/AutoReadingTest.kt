package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kr.goyo.reader.model.*
import org.junit.After
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
class AutoReadingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()

    @After fun closeTemporaryReadingAndResetItsPreferences() {
        val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
        compose.runOnIdle { model.close() }
        compose.waitUntil(5_000) { compose.runOnIdle { model.library.value.openBookId == null } }
        runBlocking { model.repository.saveSettings(ReaderSettings()) }
    }

    @Test(timeout = 60_000) fun textPagesFinishAutomaticReadingAtTheEnd() = checkCompletion(BookKind.TEXT, ReadingMode.PAGE)
    @Test fun textScrollFinishesAutomaticReadingAtTheEnd() = checkCompletion(BookKind.TEXT, ReadingMode.SCROLL)
    @Test fun comicPagesFinishAutomaticReadingAtTheEnd() = checkCompletion(BookKind.COMIC, ReadingMode.PAGE)
    @Test fun comicScrollFinishesAutomaticReadingAtTheEnd() = checkCompletion(BookKind.COMIC, ReadingMode.SCROLL)
    private fun checkCompletion(kind: BookKind, mode: ReadingMode) {
        val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
        val repository = model.repository
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        val source = if (kind == BookKind.TEXT) temporary.newFile("automatic-reading.txt").apply {
            writeText("마지막까지 읽은 뒤 자동 읽기는 정상적으로 끝나야 합니다. 0123456789.\n".repeat(180))
        } else comicFixture()
        val book = runBlocking {
            repository.saveSettings(ReaderSettings(readingMode = mode, autoPageSeconds = 1))
            val opened = repository.openFile(Uri.fromFile(source)).book
            val end = if (kind == BookKind.TEXT) ReadingPosition(index = source.readText().lastIndex, progress = 1f)
                else ReadingPosition(index = 1, offset = 1f, progress = 1f)
            repository.closeReading(opened.id, end) { throw AssertionError(it) }.join()
            opened
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("최근")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("최근").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(book.title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(book.title).performClick()
            compose.waitUntil(10_000) {
                val ready = if (kind == BookKind.TEXT) hasContentDescription("본문 검색") and isEnabled()
                    else hasContentDescription("2쪽 · 2.png")
                compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithContentDescription("자동 읽기 시작").performClick()
                compose.mainClock.advanceTimeBy(1_250)
                compose.waitForIdle()
                compose.onNodeWithContentDescription("자동 읽기 정지").assertDoesNotExist()
                compose.onNodeWithContentDescription("읽기 도구 열기").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithContentDescription("자동 읽기 시작").assertIsDisplayed()
                compose.onNodeWithContentDescription("이전 쪽").performClick()
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                compose.onNodeWithContentDescription("자동 읽기 시작").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithContentDescription("자동 읽기 정지").assertIsDisplayed().performClick()
                compose.mainClock.advanceTimeByFrame()
            } finally { compose.mainClock.autoAdvance = true }
        } catch (failure: Throwable) {
            println("AUTO_STATE kind=$kind mode=$mode ready=${model.ready.value} opening=${model.opening.value} session=${model.session.value?.book} error=${model.error.value} library=${model.library.value}")
            println(compose.onRoot().printToString())
            throw failure
        }
    }
    private fun comicFixture(): File {
        val file = temporary.newFile("automatic-reading.cbz")
        val bitmap = Bitmap.createBitmap(120, 1200, Bitmap.Config.ARGB_8888)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                listOf(Color.RED, Color.BLUE).forEachIndexed { page, color ->
                    bitmap.eraseColor(color)
                    zip.putNextEntry(ZipEntry("${page + 1}.png"))
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
                    zip.closeEntry()
                }
            }
        } finally { bitmap.recycle() }
        return file
    }
}
