package kr.goyo.reader

import android.app.Activity
import android.net.Uri
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class ReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()
    @Test fun aFileCanBeReadBookmarkedAndResumedFromItsOriginalFolder() {
        val repository = (compose.activity.application as MinViewerApplication).library
        val source = temporary.newFile("읽기 흐름 샘플.txt").apply {
            writeText("고요한 저녁, 책장을 펼쳤습니다. 다음 문장도 천천히 읽습니다.\n\n".repeat(180))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java] }
        compose.runOnIdle { model.selectFolder(Uri.fromFile(temporary.root)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(source.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(source.name).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("본문 검색") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        val actual = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(actual * 1.1f) }
        compose.waitForIdle()
        assertEquals(actual, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)
        val activity: Activity = compose.activity
        compose.runOnIdle { assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("3 / ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            assertTrue(activity.dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 1)))
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)))
        }
        compose.waitForIdle()
        compose.onNodeWithText("3 / ", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("책갈피 추가").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("다시 읽을 곳")
        compose.onNodeWithText("저장").performClick()
        // Drain Android and Compose queues as the background DataStore write completes.
        compose.waitUntil(5_000) {
            compose.runOnIdle {
                assertNull("Bookmark save must not report an error", model.error.value)
                model.library.value.bookmarks.isNotEmpty()
            }
        }
        val persisted = runBlocking { repository.state.first() }
        val saved = persisted.bookmarks.single { it.title == "다시 읽을 곳" }
        val book = persisted.books.single()
        assertEquals(book.id, saved.bookId)
        assertTrue(saved.position.index > 0)
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(source.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(source.name).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("책갈피 목록").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("다시 읽을 곳").isDisplayed() }
        compose.onNodeWithText("다시 읽을 곳").assertIsDisplayed()
        assertTrue(source.exists())
    }
}
