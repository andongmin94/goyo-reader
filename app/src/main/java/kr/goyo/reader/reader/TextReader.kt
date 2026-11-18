package kr.goyo.reader.reader

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kr.goyo.reader.model.ReaderCommand
import kr.goyo.reader.model.ReaderFont
import kr.goyo.reader.model.ReaderInfo
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingMode
import kr.goyo.reader.model.ReadingPosition
import kr.goyo.reader.model.SearchResult
import kr.goyo.reader.model.TapLayout
import kr.goyo.reader.model.pageAtProgress
import kr.goyo.reader.model.pageProgress
import kr.goyo.reader.reader.text.TextDecoder
import kr.goyo.reader.reader.text.TextDocument
import kr.goyo.reader.reader.text.TextPageLayouts
import kr.goyo.reader.reader.text.TextPagination
import kr.goyo.reader.reader.text.TextTypography
import kr.goyo.reader.reader.text.findTextMatches
import kr.goyo.reader.reader.text.paginateText
import kr.goyo.reader.reader.text.safeTextOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TextReader(
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
    val anchor = remember(file.absolutePath) { mutableStateOf(initialPosition) }
    val positionCallback by rememberUpdatedState(onPosition)
    val errorCallback by rememberUpdatedState(onError)
    val loaded by produceState<Result<TextDocument>?>(null, file.absolutePath, settings.textEncoding) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { TextDecoder.read(file, settings.textEncoding) }
        }
    }
    val fontResult by produceState<Result<Typeface>?>(null, settings.font, settings.customFontPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (settings.font) {
                    ReaderFont.SYSTEM -> Typeface.DEFAULT
                    ReaderFont.SERIF -> Typeface.SERIF
                    ReaderFont.MONOSPACE -> Typeface.MONOSPACE
                    ReaderFont.CUSTOM -> Typeface.createFromFile(requireNotNull(settings.customFontPath))
                }
            }
        }
    }
    LaunchedEffect(loaded, fontResult) {
        (loaded?.exceptionOrNull() ?: fontResult?.exceptionOrNull())?.let {
            if (it is CancellationException) throw it
            errorCallback("텍스트를 열 수 없습니다: ${it.localizedMessage ?: it.javaClass.simpleName}")
        }
    }
    DisposableEffect(file.absolutePath) {
        onDispose { positionCallback(anchor.value) }
    }
    val document = loaded?.getOrNull()
    val typeface = fontResult?.getOrNull()
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(settings.backgroundColor))
            .padding(horizontal = settings.marginDp.dp, vertical = 16.dp),
    ) {
        if (document == null || typeface == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val fontSizePx = with(density) { settings.fontSizeSp.sp.toPx() }
        val typography = remember(width, height, fontSizePx, settings.lineSpacing, typeface) {
            TextTypography(width, height, fontSizePx, settings.lineSpacing, typeface)
        }
        val pagination by produceState<TextPagination?>(null, document, typography) {
            value = null
            value = withContext(Dispatchers.Default) { paginateText(document, typography) }
        }
        val pages = pagination
        if (pages == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (document.text.isEmpty()) {
            LaunchedEffect(pages, autoReading) {
                onInfo(ReaderInfo(supportsSearch = true))
                onPosition(ReadingPosition())
                if (autoReading) onAutoReadingFinished()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("빈 텍스트 파일입니다.", color = Color(settings.textColor))
            }
        } else {
            key(pages, settings.readingMode) {
                TextReaderContent(
                    pages, anchor, settings, commands, autoReading, onPosition,
                    onInfo, onSearchResults, onToggleChrome, onAutoReadingFinished,
                )
            }
        }
    }
}

@Composable
private fun TextReaderContent(
    pagination: TextPagination,
    anchor: MutableState<ReadingPosition>,
    settings: ReaderSettings,
    commands: Flow<ReaderCommand>,
    autoReading: Boolean,
    onPosition: (ReadingPosition) -> Unit,
    onInfo: (ReaderInfo) -> Unit,
    onSearchResults: (List<SearchResult>) -> Unit,
    onToggleChrome: () -> Unit,
    onAutoReadingFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val layouts = remember(pagination, settings.textColor) {
        TextPageLayouts(pagination, settings.textColor.toInt())
    }
    val initial = remember {
        val index = safeTextOffset(pagination.document.text, anchor.value.index)
        anchor.value.copy(
            index = index,
            progress = if (settings.readingMode == ReadingMode.PAGE) {
                pageProgress(pagination.pageFor(index), pagination.size)
            } else index.toFloat() / (pagination.document.text.length - 1).coerceAtLeast(1),
        )
    }
    val initialPage = pagination.pageFor(initial.index)
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { pagination.size })
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage,
        initialFirstVisibleItemScrollOffset = scrollOffset(pagination, layouts, initial),
    )
    val positionCallback by rememberUpdatedState(onPosition)
    val infoCallback by rememberUpdatedState(onInfo)
    val searchCallback by rememberUpdatedState(onSearchResults)
    val chromeCallback by rememberUpdatedState(onToggleChrome)
    val finishedCallback by rememberUpdatedState(onAutoReadingFinished)
    val pendingSeek = remember { mutableStateOf<ReadingPosition?>(null) }
    val anchorViewport = remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun syncScrollAnchor(viewport: Pair<Int, Int> = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) {
        val previous = anchorViewport.value
        // Restoring a character inside a line must not replace it with that line's start.
        if (previous != null && previous != viewport) {
            anchor.value = positionAtScroll(pagination, layouts, viewport.first, viewport.second)
        }
        anchorViewport.value = viewport
    }

    DisposableEffect(lifecycle, pagination, settings.readingMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (settings.readingMode == ReadingMode.SCROLL) {
                    syncScrollAnchor()
                }
                positionCallback(anchor.value)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (settings.readingMode == ReadingMode.SCROLL) syncScrollAnchor()
            positionCallback(anchor.value)
        }
    }

    fun report(position: ReadingPosition, page: Int) {
        positionCallback(position)
        infoCallback(ReaderInfo(current = page + 1, total = pagination.size, supportsSearch = true))
    }

    suspend fun turn(forward: Boolean) {
        runReaderScroll {
            if (settings.readingMode == ReadingMode.PAGE) {
                val target = (pager.currentPage + if (forward) 1 else -1).coerceIn(0, pagination.size - 1)
                pager.animateScrollToPage(target)
            } else {
                list.animateScrollBy(pagination.typography.heightPx * 0.85f * if (forward) 1f else -1f)
            }
        }
    }

    suspend fun seek(request: ReadingPosition) {
        val index = safeTextOffset(pagination.document.text, request.index)
        val page = pagination.pageFor(index)
        val position = request.copy(
            index = index,
            progress = if (settings.readingMode == ReadingMode.PAGE) pageProgress(page, pagination.size)
                else index.toFloat() / (pagination.document.text.length - 1).coerceAtLeast(1),
        )
        if (settings.readingMode == ReadingMode.PAGE) {
            pendingSeek.value = null
            if (runReaderScroll { pager.scrollToPage(page) }) {
                pendingSeek.value = position
                anchor.value = position
                report(position, page)
            }
        } else {
            if (runReaderScroll { list.scrollToItem(page, scrollOffset(pagination, layouts, position)) }) {
                anchorViewport.value = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
                anchor.value = position
                report(position, page)
            } else {
                syncScrollAnchor()
            }
        }
    }

    LaunchedEffect(pagination, settings.readingMode) {
        anchor.value = initial
        report(initial, initialPage)
        if (settings.readingMode == ReadingMode.PAGE) {
            var previous = initialPage
            snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
                // Keep the exact old text anchor through relayout; only navigation replaces it.
                if (page != previous) {
                    previous = page
                    val position = pendingSeek.value?.takeIf { pagination.pageFor(it.index) == page }
                        ?: ReadingPosition(index = pagination.starts[page], progress = pageProgress(page, pagination.size))
                    pendingSeek.value = null
                    anchor.value = position
                    report(position, page)
                }
            }
        } else {
            snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { viewport ->
                    syncScrollAnchor(viewport)
                    // Closing the reader must see the current anchor immediately. The
                    // ViewModel already limits how often this position is written to disk.
                    report(anchor.value, pagination.pageFor(anchor.value.index))
                }
        }
    }

    LaunchedEffect(commands, pagination, settings.readingMode) {
        commands.collect { command ->
            when (command) {
                ReaderCommand.Next -> turn(true)
                ReaderCommand.Previous -> turn(false)
                is ReaderCommand.Seek -> seek(command.position)
                is ReaderCommand.SeekPage -> {
                    val page = command.page.coerceIn(1, pagination.size) - 1
                    seek(ReadingPosition(index = pagination.starts[page]))
                }
                is ReaderCommand.SeekProgress -> seek(ReadingPosition(
                    index = if (settings.readingMode == ReadingMode.PAGE) {
                        pagination.starts[pageAtProgress(command.progress, pagination.size)]
                    } else ((pagination.document.text.length - 1) * command.progress.coerceIn(0f, 1f)).toInt(),
                ))
                is ReaderCommand.Search -> {
                    val results = withContext(Dispatchers.Default) {
                        val text = pagination.document.text
                        findTextMatches(text, command.query).mapIndexed { index, offset ->
                            val start = safeTextOffset(text, (offset - 45).coerceAtLeast(0))
                            val end = safeTextOffset(text, (offset + command.query.length + 90).coerceAtMost(text.length))
                            SearchResult(
                                title = "${index + 1}번째 결과",
                                excerpt = text.substring(start, end).replace('\n', ' '),
                                position = ReadingPosition(
                                    index = offset,
                                    progress = offset.toFloat() / (text.length - 1).coerceAtLeast(1),
                                ),
                            )
                        }
                    }
                    searchCallback(results)
                }
            }
        }
    }

    LaunchedEffect(autoReading, pagination, settings.readingMode, settings.autoPageSeconds, settings.autoScrollDpPerSecond) {
        if (!autoReading) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (settings.readingMode == ReadingMode.PAGE) {
                while (isActive && (pager.settledPage < pagination.size - 1 || pager.isScrollInProgress)) {
                    delay(settings.autoPageSeconds.coerceAtLeast(1) * 1000L)
                    turn(true)
                }
            } else {
                val pixelsPerSecond = with(density) { settings.autoScrollDpPerSecond.dp.toPx() }
                var lastFrame = withFrameNanos { it }
                while (isActive && (list.canScrollForward || list.isScrollInProgress)) {
                    val frame = withFrameNanos { it }
                    val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.1f)
                    runReaderScroll { list.scrollBy(pixelsPerSecond * seconds) }
                    lastFrame = frame
                }
            }
            currentCoroutineContext().ensureActive()
            finishedCallback()
        }
    }

    val tapModifier = Modifier.pointerInput(settings.tapLayout, settings.rightToLeft) {
        detectTapGestures(
            onLongPress = { chromeCallback() },
            onTap = { offset ->
                val fraction = offset.x / size.width.coerceAtLeast(1)
                if (settings.tapLayout != TapLayout.ANYWHERE_NEXT && fraction in 0.3f..0.7f) {
                    chromeCallback()
                } else {
                    val forward = when (settings.tapLayout) {
                        TapLayout.ANYWHERE_NEXT -> true
                        else -> {
                            val nextOnLeft = settings.rightToLeft xor (settings.tapLayout == TapLayout.LEFT_HANDED)
                            (fraction < 0.5f) == nextOnLeft
                        }
                    }
                    scope.launch { turn(forward) }
                }
            },
        )
    }
    if (settings.readingMode == ReadingMode.PAGE) {
        HorizontalPager(
            state = pager,
            reverseLayout = settings.rightToLeft,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            NativeTextPage(pagination, layouts, page, Modifier.fillMaxSize().then(tapModifier))
        }
    } else {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            items(pagination.size, key = { pagination.starts[it] }) { page ->
                NativeTextPage(
                    pagination, layouts, page,
                    Modifier.fillMaxWidth().height(with(density) { layouts.scrollHeight(page).toDp() })
                        .then(tapModifier),
                )
            }
        }
    }
}

private suspend fun runReaderScroll(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (_: CancellationException) {
    // A drag cancels the scroll mutation; disposing the reader cancels its owner too.
    currentCoroutineContext().ensureActive()
    false
}

@Composable
private fun NativeTextPage(
    pagination: TextPagination,
    layouts: TextPageLayouts,
    page: Int,
    modifier: Modifier,
) {
    val layout = remember(layouts, page) { layouts.get(page) }
    val pageText = remember(pagination, page) { AnnotatedString(pagination.text(page)) }
    Canvas(modifier.semantics { text = pageText }) {
        drawIntoCanvas { layout.draw(it.nativeCanvas) }
    }
}

private fun scrollOffset(pagination: TextPagination, layouts: TextPageLayouts, position: ReadingPosition): Int {
    val page = pagination.pageFor(position.index)
    val layout = layouts.get(page)
    val offset = (position.index - pagination.starts[page]).coerceIn(0, pagination.text(page).length)
    val line = layout.getLineForOffset(offset)
    val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(1)
    return layout.getLineTop(line) + (position.offset.coerceIn(0f, 1f) * lineHeight).toInt()
}

private fun positionAtScroll(pagination: TextPagination, layouts: TextPageLayouts, page: Int, pixelOffset: Int): ReadingPosition {
    val layout = layouts.get(page)
    val line = layout.getLineForVertical(pixelOffset)
    val index = pagination.starts[page] + layout.getLineStart(line)
    val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(1)
    return ReadingPosition(
        index = index,
        offset = ((pixelOffset - layout.getLineTop(line)).toFloat() / lineHeight).coerceIn(0f, 1f),
        progress = index.toFloat() / (pagination.document.text.length - 1).coerceAtLeast(1),
    )
}
