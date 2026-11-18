package kr.goyo.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import kr.goyo.reader.ui.MinViewerApp
import kr.goyo.reader.ui.MinViewerTheme

class MainActivity : FragmentActivity() {
    // Use this Application, not the process-wide factory's first cached Application.
    private val model: LibraryViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }
    var volumeKeyHandler: ((Boolean) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.start(if (savedInstanceState == null) documentUri(intent) else null)
        setContent { MinViewerTheme { MinViewerApp(model, this) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        documentUri(intent)?.let(model::openUri)
    }
    @Suppress("DEPRECATION")
    private fun documentUri(intent: Intent?): Uri? {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        return uri?.takeIf { it.scheme == "content" }
    }
    override fun onStop() { model.flushPosition(); super.onStop() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = volumeKeyHandler
        if (handler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.repeatCount == 0) handler(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyHandler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }
}
