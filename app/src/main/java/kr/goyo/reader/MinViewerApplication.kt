package kr.goyo.reader

import android.app.Application
import kr.goyo.reader.data.LibraryRepository

class MinViewerApplication : Application() {
    val library by lazy { LibraryRepository(this) }
}
