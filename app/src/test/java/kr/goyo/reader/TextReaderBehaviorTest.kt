package kr.goyo.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kr.goyo.reader.model.ReaderCommand
import kr.goyo.reader.model.ReaderFont
import kr.goyo.reader.model.ReaderInfo
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingMode
import kr.goyo.reader.model.ReadingPosition
import kr.goyo.reader.reader.TextReader
import kr.goyo.reader.ui.GoyoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
@Config(sdk = [35], application = GoyoApplication::class, qualifiers = "w320dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TextReaderBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val temporary = TemporaryFolder()

    private val commands = MutableSharedFlow<ReaderCommand>(extraBufferCapacity = 8)
    private val preferences = mutableStateOf(ReaderSettings())
    @Volatile private var position = ReadingPosition()
    @Volatile private var info = ReaderInfo()
    @Volatile private var error: String? = null

    @Test
    fun pageProgressSeekUsesTheSamePageScaleAsTheDisplayedProgress() {
        // Short blank lines occupy many pages but very few character offsets.
        showReader("\n".repeat(240) + "가나다라마바사아자차카타파하 ".repeat(240))
        val total = info.total
        val middlePage = total / 2
        val requestedProgress = middlePage.toFloat() / (total - 1)

        send(ReaderCommand.SeekProgress(requestedProgress))
        compose.waitUntil(5_000) { info.current > 1 }
        compose.waitForIdle()

        assertEquals("Seeking the displayed fraction must select the same page fraction", middlePage + 1, info.current)
        assertEquals(requestedProgress, position.progress, 0.0001f)
    }

    @Test
    fun scrollAnchorSurvivesFontChangesAndPauseWithoutUserMovement() {
        val initial = ReadingPosition(index = 1_237, offset = 0.35f)
        val original = ReaderSettings(readingMode = ReadingMode.SCROLL, font = ReaderFont.MONOSPACE, fontSizeSp = 18f)
        showReader("읽던 문장의 정확한 위치를 유지합니다. ABCDEFGHIJ 0123456789.\n".repeat(350), original, initial)
        val originalPageCount = info.total

        compose.runOnIdle { preferences.value = original.copy(fontSizeSp = 31f) }
        compose.waitUntil(10_000) { info.total > originalPageCount }
        // Flush the reader's 180 ms position sampling after layout completes.
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        compose.runOnIdle { preferences.value = original }
        compose.waitUntil(10_000) { info.total == originalPageCount }
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitForIdle()
        assertEquals("Relayout and ON_PAUSE must retain the exact character anchor", initial.index, position.index)
        assertEquals("Relayout must also retain the within-line anchor", initial.offset, position.offset, 0.0001f)

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val bookmark = ReadingPosition(index = 2_243, offset = 0.4f)
        send(ReaderCommand.Seek(bookmark))
        compose.waitUntil(5_000) { position.index == bookmark.index }
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitForIdle()
        assertEquals("Seeking inside a line must preserve the requested bookmark", bookmark.index, position.index)
        assertEquals(bookmark.offset, position.offset, 0.0001f)
    }

    @Test
    fun draggingDuringNextDoesNotDisableLaterPageCommands() {
        showReader(
            "스크롤 도중에도 다음 이동 명령을 계속 처리해야 합니다. 0123456789.\n".repeat(400),
            ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        val destination = info.total - 1
        compose.mainClock.autoAdvance = false
        send(ReaderCommand.Next)
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("text-reader").performTouchInput { swipeUp(durationMillis = 250) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        send(ReaderCommand.SeekPage(destination))
        compose.waitUntil(5_000) { info.current == destination }
        assertEquals(destination, info.current)
        assertTrue(position.index > 0)
    }

    @Test
    fun nearEndScrollAnchorSurvivesFontReductionAndPause() {
        val text = "0123456789 abcdefghijklmnopqrstuvwxyz ".repeat(200)
        val initial = ReadingPosition(index = text.length - 200, offset = 0.35f)
        val large = ReaderSettings(readingMode = ReadingMode.SCROLL, font = ReaderFont.MONOSPACE, fontSizeSp = 31f)
        showReader(text, large, initial)
        val largePageCount = info.total

        compose.runOnIdle { preferences.value = large.copy(fontSizeSp = 18f) }
        compose.waitUntil(10_000) { info.total < largePageCount }
        // The shorter final content can clamp the first measured scroll viewport.
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitForIdle()

        assertEquals("End-of-document layout clamping must preserve the exact anchor", initial.index, position.index)
        assertEquals(initial.offset, position.offset, 0.0001f)
    }

    private fun showReader(
        text: String,
        settings: ReaderSettings = ReaderSettings(),
        initialPosition: ReadingPosition = ReadingPosition(),
    ) {
        val source = temporary.newFile("reader.txt").apply { writeText(text) }
        preferences.value = settings
        compose.setContent {
            GoyoTheme {
                TextReader(
                    file = source,
                    initialPosition = initialPosition,
                    settings = preferences.value,
                    commands = commands,
                    autoReading = false,
                    onPosition = { position = it },
                    onInfo = { info = it },
                    onSearchResults = {},
                    onToggleChrome = {},
                    onError = { error = it },
                    onAutoReadingFinished = {},
                    modifier = Modifier.size(320.dp, 520.dp).testTag("text-reader"),
                )
            }
        }
        compose.waitUntil(10_000) {
            assertNull(error)
            info.total > 3 && commands.subscriptionCount.value > 0
        }
        compose.waitForIdle()
    }

    private fun send(command: ReaderCommand) {
        compose.runOnIdle { assertTrue(commands.tryEmit(command)) }
    }
}
