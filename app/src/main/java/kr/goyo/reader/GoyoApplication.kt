package kr.goyo.reader

import android.app.Application
import kr.goyo.reader.data.LibraryRepository

class GoyoApplication : Application() {
    val library by lazy { LibraryRepository(this) }
}
