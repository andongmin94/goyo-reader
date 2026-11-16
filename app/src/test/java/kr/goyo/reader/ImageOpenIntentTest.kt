package kr.goyo.reader

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MinViewerApplication::class)
class ImageOpenIntentTest {
    @Test fun supportedImagesResolveToTheReaderFromAnExternalFileApp() {
        val context = RuntimeEnvironment.getApplication()
        for (mime in listOf("image/png", "image/jpeg", "image/webp")) {
            val resolved = context.packageManager.resolveActivity(view(mime), PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(mime, MainActivity::class.java.name, resolved?.activityInfo?.name)
        }
    }

    @Test fun unsupportedImagesAreNotAdvertisedAsViewable() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(context.packageManager.resolveActivity(view("image/gif"), PackageManager.MATCH_DEFAULT_ONLY))
    }

    private fun view(mime: String) = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse("content://external.files/image"), mime)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .setPackage(RuntimeEnvironment.getApplication().packageName)
}
