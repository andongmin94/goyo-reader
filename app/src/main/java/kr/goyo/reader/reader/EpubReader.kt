package kr.goyo.reader.reader

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kr.goyo.reader.model.ReaderCommand
import kr.goyo.reader.model.ReaderInfo
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingPosition
import kr.goyo.reader.model.SearchResult
import kr.goyo.reader.reader.epub.EpubHostFragment

/** Readium owns EPUB layout, parsing, selection, and navigation; Compose owns the app chrome. */
@Composable
fun EpubReader(
    file: File,
    initialPosition: ReadingPosition,
    settings: ReaderSettings,
    commands: Flow<ReaderCommand>,
    autoReading: Boolean,
    onPosition: (ReadingPosition) -> Unit,
    onInfo: (ReaderInfo) -> Unit,
    onSearchResults: (List<SearchResult>) -> Unit,
    onToggleChrome: () -> Unit,
    onError: (String) -> Unit,
    onAutoReadingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionCallback = rememberUpdatedState(onPosition)
    val infoCallback = rememberUpdatedState(onInfo)
    val searchCallback = rememberUpdatedState(onSearchResults)
    val chromeCallback = rememberUpdatedState(onToggleChrome)
    val errorCallback = rememberUpdatedState(onError)
    val finishedCallback = rememberUpdatedState(onAutoReadingFinished)
    val latestPosition = remember(file.absolutePath) { EpubPositionCache(initialPosition) }

    // Font declarations are navigator configuration, not preferences. Recreate only when the
    // imported font file changes, keeping the most recently reported content locator.
    key(file.absolutePath, settings.customFontPath, commands) {
        val connection = remember {
            EpubConnection(
                settings = MutableStateFlow(settings),
                autoReading = MutableStateFlow(autoReading),
                commands = commands,
                onPosition = {
                    latestPosition.value = it
                    positionCallback.value(it)
                },
                onInfo = { infoCallback.value(it) },
                onSearchResults = { searchCallback.value(it) },
                onToggleChrome = { chromeCallback.value() },
                onError = { errorCallback.value(it) },
                onAutoReadingFinished = { finishedCallback.value() },
            )
        }
        SideEffect {
            connection.settings.value = settings
            connection.autoReading.value = autoReading
        }
        val arguments = remember {
            Bundle().apply {
                putString(EpubHostFragment.ARG_FILE, file.absolutePath)
                putString(EpubHostFragment.ARG_LOCATOR, latestPosition.value.locator)
                putFloat(EpubHostFragment.ARG_PROGRESS, latestPosition.value.progress)
                putString(EpubHostFragment.ARG_FONT, settings.customFontPath)
            }
        }
        AndroidFragment<EpubHostFragment>(
            modifier = modifier,
            arguments = arguments,
            onUpdate = { it.connect(connection) },
        )
    }
}

internal class EpubPositionCache(var value: ReadingPosition)

internal class EpubConnection(
    val settings: MutableStateFlow<ReaderSettings>,
    val autoReading: MutableStateFlow<Boolean>,
    val commands: Flow<ReaderCommand>,
    val onPosition: (ReadingPosition) -> Unit,
    val onInfo: (ReaderInfo) -> Unit,
    val onSearchResults: (List<SearchResult>) -> Unit,
    val onToggleChrome: () -> Unit,
    val onError: (String) -> Unit,
    val onAutoReadingFinished: () -> Unit,
)
