package kr.goyo.reader

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
class DirectoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()
    @Test fun returningToAParentFolderRestoresItsScrolledList() {
        for (i in 1..30) temporary.newFolder("folder $i")
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].selectFolder(Uri.fromFile(temporary.root)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("folder 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("folder 30"))
        compose.onNodeWithText("folder 30").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("빈 폴더입니다.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("상위 폴더").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("folder 30")).fetchSemanticsNodes().isNotEmpty() &&
                !ViewModelProvider(compose.activity)[LibraryViewModel::class.java].browser.value.loading
        }
        compose.onNodeWithText("folder 30").assertIsDisplayed()
    }
}
