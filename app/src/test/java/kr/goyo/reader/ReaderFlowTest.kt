package kr.goyo.reader

import android.app.Activity
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.goyo.reader.data.ImportPlan
import kr.goyo.reader.data.SourceDocument
import kr.goyo.reader.model.BookKind
import org.junit.Assert.assertEquals
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
// Robolectric #8460: platform-width dialogs containing a TextField can continuously
// remeasure when the configured width exceeds its 320dp window. Keep the real dialog
// and text-entry assertions; use the native test window's width instead.
@Config(sdk = [35], application = MinViewerApplication::class, qualifiers = "w320dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun importedBookCanBeReadBookmarkedAndResumedFromTheShelf() {
        val repository = (compose.activity.application as MinViewerApplication).library
        val source: File = temporary.newFile("읽기 흐름 샘플.txt").apply {
            writeText("고요한 저녁, 책장을 펼쳤습니다. 다음 문장도 천천히 읽습니다.\n\n".repeat(180))
        }
        val imported = runBlocking {
            repository.import(
                ImportPlan(listOf(SourceDocument(Uri.fromFile(source), source.name, source.length(), BookKind.TEXT, "txt")), emptyList()),
            ) { _, _, _ -> }.single()
        }

        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(imported.title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(imported.title).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("본문 검색") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        val progressSlider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        val actualProgress = progressSlider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        progressSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(actualProgress * 1.1f) }
        compose.waitForIdle()
        assertEquals("Releasing within the current page must show its actual progress", actualProgress,
            progressSlider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)

        // Dispatch through the framework Activity API so the real key callback path runs.
        val activity: Activity = compose.activity
        compose.runOnIdle {
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("3 / ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
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
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("책갈피 추가").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("다시 읽을 곳")
        compose.onNodeWithText("저장").performClick()
        // Let the v2 dispatcher run the click's queued work before awaiting disk state.
        compose.waitForIdle()
        val saved = runBlocking {
            withTimeout(5_000) {
                repository.state.first { state -> state.bookmarks.any { it.title == "다시 읽을 곳" } }
                    .bookmarks.single { it.title == "다시 읽을 곳" }
            }
        }
        assertEquals(imported.id, saved.bookId)
        assertTrue("The bookmark must retain the second page's text offset", saved.position.index > 0)

        compose.onNodeWithContentDescription("서재로 돌아가기").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("나만의 오프라인 서재")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(imported.title).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("2 / ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("책갈피 목록").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("다시 읽을 곳").isDisplayed() }
        compose.onNodeWithText("다시 읽을 곳").assertIsDisplayed()
        assertTrue("Import and reading must preserve the original file", source.exists())
    }
}
