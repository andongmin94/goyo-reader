package kr.goyo.reader

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.goyo.reader.model.*
import org.json.JSONTokener
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Finish the old Activity and its ViewModel, then launch with no saved-instance Bundle.
 * This tests persisted startup, not ActivityScenario.recreate() or retained memory. */
@RunWith(AndroidJUnit4::class)
class ReadingResumeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = (context.applicationContext as MinViewerApplication).library
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: LibraryViewModel

    @Before fun preparePreferences() = runBlocking {
        val state = repository.state.first()
        state.books.firstOrNull { it.id == state.openBookId }?.let {
            repository.closeReading(it.id, it.position) { cause -> throw AssertionError(cause) }.join()
        }
        repository.saveSettings(ReaderSettings(fontSizeSp = 22f))
    }

    @After fun closeTestDocument() {
        scenario?.onActivity { model.close() }
        runBlocking {
            val state = repository.state.first()
            state.books.firstOrNull { it.id == state.openBookId }?.let {
                repository.closeReading(it.id, it.position) { cause -> throw AssertionError(cause) }.join()
            }
        }
        scenario?.close()
        scenario = null
    }

    @Test fun txtResumesOnFreshLaunchWithoutBookmarkingAndExplicitExitStaysInDirectory() {
        val directory = samples()
        launch()
        browse(directory)
        open(ReadingSamples.textName)
        compose.onNodeWithContentDescription("다음 쪽").performClick()
        compose.waitUntil(10_000) { position().index > 0 }
        val before = position()
        val id = requireNotNull(model.session.value).book.id
        assertTrue(model.library.value.bookmarks.none { it.bookId == id })
        restartFromLauncher(id, before)
        compose.waitUntil(10_000) { position().index == before.index }
        assertEquals(22f, model.library.value.settings.fontSizeSp, 0f)
        compose.onNodeWithText(ReadingSamples.textName).assertIsDisplayed()

        compose.onNodeWithContentDescription("책갈피 추가").performClick()
        compose.onNodeWithText("책갈피 이름").performTextReplacement("내일 읽을 곳")
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(5_000) { model.library.value.bookmarks.any { it.bookId == id && it.title == "내일 읽을 곳" } }
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        compose.waitUntil(5_000) { model.library.value.openBookId == null && model.session.value == null }
        scenario!!.close()
        scenario = null
        launch()
        compose.onNodeWithTag("browser-file-list").assertIsDisplayed()
        assertNull(model.session.value)
        assertEquals(Uri.fromFile(directory).toString(), model.browser.value.path.last().uri)
        open(ReadingSamples.textName)
        compose.waitUntil(10_000) { position().index == before.index }
        compose.onNodeWithContentDescription("책갈피 목록").performClick()
        compose.onNodeWithText("내일 읽을 곳").assertIsDisplayed()
    }

    @Test fun pdfPngZipAndEpubResumeWithFreshViewModels() {
        val directory = samples()
        launch()
        browse(directory)
        for (name in listOf(ReadingSamples.pdfName, ReadingSamples.imageName, ReadingSamples.zipName, ReadingSamples.epubName)) {
            println("RESUME_OPEN $name")
            open(name)
            val epub = name.endsWith(".epub")
            val initial = position()
            compose.onNodeWithContentDescription(if (epub) "다음 위치" else "다음 쪽").performClick()
            compose.waitUntil(15_000) {
                val now = position()
                if (epub) now.offset > initial.offset + 0.001f else now.index == 1
            }
            val before = position()
            val id = requireNotNull(model.session.value).book.id
            println("RESUME_RESTART $name $before")
            val stopped = restartFromLauncher(id, before)
            compose.waitUntil(15_000) {
                val restored = position()
                restored.index == stopped.index && kotlin.math.abs(restored.offset - stopped.offset) < 0.01f
            }
            if (name == ReadingSamples.imageName) assertEquals(stopped.locator, position().locator)
            println("RESUME_VERIFIED $name ${position()}")
            compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
            compose.waitUntil(5_000) { model.session.value == null && model.library.value.openBookId == null }
        }
    }

    private fun samples(): File = temporary.newFolder("이어 읽기").also {
        ReadingSamples.create(it)
        File(it, ReadingSamples.imageName).copyTo(File(it, "02-관측일지.png"))
    }

    private fun launch() {
        scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        })
        scenario!!.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
        compose.waitUntil(10_000) { model.ready.value && !model.browser.value.loading }
    }

    private fun browse(directory: File) {
        compose.runOnIdle { model.navigate(listOf(FolderLocation(Uri.fromFile(directory).toString(), directory.name))) }
        compose.waitUntil(10_000) { !model.browser.value.loading &&
            compose.onAllNodes(hasText(ReadingSamples.textName)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun open(name: String) {
        compose.onNodeWithTag("browser-file-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(10_000) { !model.opening.value && model.session.value?.book?.title ==
            (if (name == ReadingSamples.imageName) "이어 읽기" else name) }
        waitReader()
    }

    private fun waitReader() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("책갈피 추가") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        if (model.session.value?.book?.kind == BookKind.EPUB) awaitEpubDocument()
    }

    private fun View.webViews(): List<WebView> = when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

    private fun awaitEpubDocument() {
        // Readium publishes metadata before the WebView page has loaded. Match the existing
        // format test's real-content/visual-state check before pressing Next or checking resume.
        compose.waitUntil(25_000) {
            val body = AtomicReference("")
            val done = CountDownLatch(1)
            scenario!!.onActivity { activity ->
                val web = activity.window.decorView.webViews().firstOrNull { it.isShown }
                if (web == null) done.countDown()
                else web.evaluateJavascript("document.body ? document.body.innerText : ''") { result ->
                    body.set(JSONTokener(result).nextValue() as? String ?: "")
                    done.countDown()
                }
            }
            check(done.await(3, TimeUnit.SECONDS)) { "EPUB WebView did not return its text" }
            body.get().contains(ReadingSamples.marker)
        }
        val drawn = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val web = requireNotNull(activity.window.decorView.webViews().firstOrNull { it.isShown })
            web.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { drawn.countDown() }
            })
        }
        check(drawn.await(5, TimeUnit.SECONDS)) { "EPUB page did not reach the compositor" }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun position(): ReadingPosition = compose.runOnIdle {
        model.latestPosition(requireNotNull(model.session.value).book)
    }

    private fun restartFromLauncher(id: String, beforeStop: ReadingPosition): ReadingPosition {
        val oldModel = model
        scenario!!.moveToState(Lifecycle.State.CREATED)
        // Readium can finish a Locator callback while the Activity is stopping. Verify the
        // final lifecycle snapshot, rather than requiring an intermediate animated snapshot
        // to remain on disk. The Activity's normal onStop performs the save; this test does not.
        var stopped = beforeStop
        scenario!!.onActivity {
            stopped = oldModel.latestPosition(requireNotNull(oldModel.session.value).book)
        }
        assertEquals("Stopping must retain the page", beforeStop.index, stopped.index)
        assertEquals("Stopping must retain the reading progression", beforeStop.offset, stopped.offset, 0.01f)
        println("RESUME_STOPPED $stopped")
        runBlocking {
            withTimeout(10_000) {
                repository.state.first { state -> state.openBookId == id && state.books.single { it.id == id }.position == stopped }
            }
        }
        scenario!!.close()
        scenario = null
        launch()
        assertNotSame("Do not mistake a retained ViewModel for persistence", oldModel, model)
        waitReader()
        assertEquals(id, requireNotNull(model.session.value).book.id)
        assertEquals(stopped, requireNotNull(model.session.value).book.position)
        return stopped
    }
}
