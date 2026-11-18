package kr.goyo.reader

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MinViewerApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewModelOwnershipTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun readerUsesTheCurrentApplicationsRepository() {
        compose.runOnIdle {
            val application = compose.activity.application as MinViewerApplication
            val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
            assertSame(application, model.getApplication<MinViewerApplication>())
            assertSame(application.library, model.repository)
        }
    }
}
