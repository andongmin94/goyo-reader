package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.ReaderInput
import kr.goyo.reader.reader.VisualReader
import kr.goyo.reader.ui.MinViewerTheme
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
@Config(sdk = [35], qualifiers = "w320dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class VisualReaderInteractionTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val commands = MutableSharedFlow<ReaderCommand>(extraBufferCapacity = 8)
    private val position = AtomicReference(ReadingPosition(index = -1))
    private val info = AtomicReference(ReaderInfo())
    private val error = AtomicReference<String?>(null)
    @Test fun aMoveInterruptedByDraggingDoesNotDisableLaterPageCommands() {
        openComic()
        val viewport = compose.onNodeWithTag("visual-reader")
        viewport.performTouchInput { down(center); moveBy(Offset(-80f, 0f)) }
        try {
            compose.waitForIdle()
            val pager = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes().single()
            assertTrue(pager.config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
            send(ReaderCommand.Next)
        } finally { viewport.performTouchInput { up() } }
        compose.waitForIdle()
        val target = if (position.get().index == 0) 3 else 0
        send(ReaderCommand.SeekPage(target + 1))
        assertEquals(target, position.get().index)
        assertNull(error.get())
    }
    @Test fun seekingTheDisplayedProgressKeepsTheSameImagePage() {
        openComic()
        for (page in 0 until 4) {
            send(ReaderCommand.SeekPage(page + 1))
            assertEquals(page, position.get().index)
            val progress = position.get().progress
            send(ReaderCommand.SeekProgress(progress))
            assertEquals(page, position.get().index)
            assertEquals(page / 3f, progress, 0.000001f)
        }
        assertNull(error.get())
    }
    private fun openComic() {
        val file = comicFixture()
        compose.setContent {
            MinViewerTheme {
                VisualReader(ReaderInput.LocalFile(file), BookKind.COMIC, ReadingPosition(), ReaderSettings(), commands,
                    false, position::set, info::set, {}, {}, error::set, {}, Modifier.fillMaxSize().testTag("visual-reader"))
            }
        }
        compose.waitUntil(10_000) { (info.get().total == 4 && commands.subscriptionCount.value == 1) || error.get() != null }
        assertNull("The PNG comic fixture must open", error.get())
        compose.waitForIdle()
        assertEquals(0, position.get().index)
    }
    private fun send(command: ReaderCommand) { compose.runOnIdle { assertTrue(commands.tryEmit(command)) }; compose.waitForIdle() }
    private fun comicFixture(): File {
        val file = temporary.newFile("four-pages.cbz")
        val bitmap = Bitmap.createBitmap(240, 480, Bitmap.Config.ARGB_8888)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { page, color ->
                    bitmap.eraseColor(color)
                    zip.putNextEntry(ZipEntry("${page + 1}.png")); check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)); zip.closeEntry()
                }
            }
        } finally { bitmap.recycle() }
        return file
    }
}
