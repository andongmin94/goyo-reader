package kr.goyo.reader.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kr.goyo.reader.model.*
import kr.goyo.reader.reader.visual.ImagePageSource
import kr.goyo.reader.reader.visual.VisualPageSource
import kr.goyo.reader.reader.visual.buildPageSlices
import kr.goyo.reader.reader.visual.sliceAtPosition

@Composable
fun VisualReader(
    input: ReaderInput,
    kind: BookKind,
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
    val context = LocalContext.current
    var source by remember(input, kind) { mutableStateOf<VisualPageSource?>(null) }
    var failure by remember(input, kind) { mutableStateOf<String?>(null) }
    var currentPosition by remember(input, kind) { mutableStateOf(initialPosition) }
    val errorCallback by rememberUpdatedState(onError)
    val positionCallback by rememberUpdatedState(onPosition)
    LaunchedEffect(input, kind) {
        var opened: VisualPageSource? = null
        try {
            withContext(Dispatchers.IO) {
                opened = when (input) {
                    is ReaderInput.Images -> ImagePageSource(context, input.entries)
                    is ReaderInput.LocalFile -> if (kind == BookKind.PDF) PdfPageSource(input.file) else ImagePageSource(input.file)
                }
            }
            source = opened
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val message = error.message ?: "이 파일을 열 수 없습니다."
            failure = message
            errorCallback(message)
        } finally {
            source = null
            withContext(NonCancellable + Dispatchers.IO) { opened?.close() }
        }
    }
    CompositionLocalProvider(LocalContentColor provides Color(settings.textColor)) {
        Box(modifier.fillMaxSize().background(Color(settings.backgroundColor))) {
            val loaded = source
            when {
                failure != null -> Text(failure.orEmpty(), color = Color(settings.textColor), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp))
                loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> key(loaded, settings.readingMode) {
                    val reportPosition: (ReadingPosition) -> Unit = { currentPosition = it; positionCallback(it) }
                    if (settings.readingMode == ReadingMode.PAGE) {
                        PagedVisualReader(loaded, currentPosition, settings, commands, autoReading,
                            reportPosition, onInfo, onSearchResults, onToggleChrome, onError, onAutoReadingFinished)
                    } else {
                        ScrolledVisualReader(loaded, currentPosition, settings, commands, autoReading,
                            reportPosition, onInfo, onSearchResults, onToggleChrome, onError, onAutoReadingFinished)
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedVisualReader(
    source: VisualPageSource,
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
) {
    val count = source.dimensions.size
    val pager = rememberPagerState(initialPage = initialPosition.index.coerceIn(0, count - 1)) { count }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSettings by rememberUpdatedState(settings)
    val positionCallback by rememberUpdatedState(onPosition)
    val infoCallback by rememberUpdatedState(onInfo)
    val searchCallback by rememberUpdatedState(onSearchResults)
    val finishedCallback by rememberUpdatedState(onAutoReadingFinished)
    var zoomed by remember { mutableStateOf(false) }

    suspend fun changePage(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, count - 1)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            zoomed = false
            positionCallback(ReadingPosition(index = page, progress = pageProgress(page, count)))
            infoCallback(ReaderInfo(current = page + 1, total = count, supportsSearch = false))
        }
    }
    LaunchedEffect(commands, pager) {
        commands.collect { command ->
            try {
                when (command) {
                    ReaderCommand.Next -> changePage(1)
                    ReaderCommand.Previous -> changePage(-1)
                    is ReaderCommand.Seek -> pager.scrollToPage(command.position.index.coerceIn(0, count - 1))
                    is ReaderCommand.SeekPage -> pager.scrollToPage(command.page.coerceIn(1, count) - 1)
                    is ReaderCommand.SeekProgress -> pager.scrollToPage(pageAtProgress(command.progress, count))
                    is ReaderCommand.Search -> searchCallback(emptyList())
                }
            } catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
        }
    }
    LaunchedEffect(autoReading, pager, lifecycle) {
        if (autoReading) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(currentSettings.autoPageSeconds.coerceAtLeast(1) * 1000L)
                if (pager.settledPage == count - 1 && !pager.isScrollInProgress) {
                    finishedCallback()
                    return@repeatOnLifecycle
                }
                if (!pager.isScrollInProgress && !zoomed) {
                    try { changePage(1) }
                    catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
                }
            }
        }
    }
    HorizontalPager(state = pager, reverseLayout = settings.rightToLeft, userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize(), key = { it }) { page ->
        val zoom = remember(page) { ViewportZoom() }
        LaunchedEffect(pager.settledPage) { if (pager.settledPage != page) zoom.reset() }
        LaunchedEffect(zoom, pager.settledPage) {
            if (pager.settledPage == page) snapshotFlow { zoom.scale > 1.01f }.collect { zoomed = it }
        }
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
            val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val dimensions = source.dimensions[page]
            val fitWidth = min(width.toFloat(), height * dimensions.aspectRatio)
            val fitHeight = fitWidth / dimensions.aspectRatio
            zoom.viewport = Size(width.toFloat(), height.toFloat())
            zoom.content = Size(fitWidth, fitHeight)
            LaunchedEffect(width, height) { zoom.rebound() }
            val gestureState = rememberTransformableState { centroid, scale, pan, _ -> zoom.transform(centroid, scale, pan) }
            Box(Modifier.fillMaxSize()
                .transformable(gestureState, canPan = { zoom.scale > 1.01f }, lockRotationOnZoomPan = true)
                .pointerInput(zoom, settings.tapLayout, settings.rightToLeft) {
                    detectTapGestures(
                        onDoubleTap = { zoom.toggle(it) }, onLongPress = { onToggleChrome() },
                        onTap = { point ->
                            if (zoom.scale > 1.01f) onToggleChrome()
                            else handleVisualTap(point.x / size.width.coerceAtLeast(1), settings,
                                { scope.launch { changePage(1) } }, { scope.launch { changePage(-1) } }, onToggleChrome)
                        },
                    )
                }) {
                PageBitmap(source, page, rasterWidth(fitWidth * zoom.scale), onError,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.scale; scaleY = zoom.scale
                        translationX = zoom.offset.x; translationY = zoom.offset.y
                    })
            }
        }
    }
}

@Composable
private fun ScrolledVisualReader(
    source: VisualPageSource,
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
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val slices = remember(source, width, height) { buildPageSlices(source.dimensions, width, height) }
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val positionCallback by rememberUpdatedState(onPosition)
        val infoCallback by rememberUpdatedState(onInfo)
        val searchCallback by rememberUpdatedState(onSearchResults)
        val finishedCallback by rememberUpdatedState(onAutoReadingFinished)
        val currentSettings by rememberUpdatedState(settings)
        val zoom = remember { ViewportZoom() }
        var restored by remember(source, width, height) { mutableStateOf(false) }
        zoom.viewport = Size(width.toFloat(), height.toFloat())
        zoom.content = zoom.viewport
        fun wholePageHeight(page: Int): Float = width / source.dimensions[page].aspectRatio
        suspend fun seek(position: ReadingPosition) {
            zoom.reset()
            val page = position.index.coerceIn(0, source.dimensions.lastIndex)
            val sliceIndex = sliceAtPosition(slices, page, position.offset)
            val slice = slices[sliceIndex]
            val offset = ((position.offset.coerceIn(0f, 1f) - slice.top) * wholePageHeight(page)).roundToInt().coerceAtLeast(0)
            list.scrollToItem(sliceIndex, offset)
        }
        LaunchedEffect(source, width, height) { seek(initialPosition); restored = true }
        LaunchedEffect(list, slices, restored) {
            if (restored) snapshotFlow {
                Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.canScrollForward)
            }.distinctUntilChanged().collect { (index, scrollOffset, canScrollForward) ->
                val slice = slices[index.coerceIn(slices.indices)]
                val offset = (slice.top + scrollOffset / wholePageHeight(slice.page)).coerceIn(0f, 1f)
                val progress = if (canScrollForward) (slice.page + offset) / source.dimensions.size else 1f
                positionCallback(ReadingPosition(index = slice.page, offset = offset, progress = progress))
                infoCallback(ReaderInfo(current = slice.page + 1, total = source.dimensions.size))
            }
        }
        LaunchedEffect(commands, list, slices) {
            commands.collect { command ->
                try {
                    when (command) {
                        ReaderCommand.Next -> { zoom.reset(); list.scrollBy(height * 0.85f) }
                        ReaderCommand.Previous -> { zoom.reset(); list.scrollBy(-height * 0.85f) }
                        is ReaderCommand.Seek -> seek(command.position)
                        is ReaderCommand.SeekPage -> seek(ReadingPosition(index = command.page.coerceIn(1, source.dimensions.size) - 1))
                        is ReaderCommand.SeekProgress -> {
                            val fraction = command.progress.coerceIn(0f, 1f)
                            val absolutePage = fraction * source.dimensions.size
                            val page = absolutePage.toInt().coerceIn(source.dimensions.indices)
                            seek(ReadingPosition(index = page, offset = (absolutePage - page).coerceIn(0f, 1f)))
                        }
                        is ReaderCommand.Search -> searchCallback(emptyList())
                    }
                } catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
            }
        }
        LaunchedEffect(autoReading, list, lifecycle, restored) {
            if (autoReading && restored) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = withFrameNanos { it }
                while (isActive) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    lastFrame = now
                    if (!list.canScrollForward && !list.isScrollInProgress) {
                        finishedCallback()
                        return@repeatOnLifecycle
                    }
                    if (!list.isScrollInProgress && zoom.scale <= 1.01f) {
                        val distance = currentSettings.autoScrollDpPerSecond * density.density * seconds
                        try { list.scrollBy(distance) }
                        catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
                    }
                }
            }
        }
        val gestureState = rememberTransformableState { centroid, scale, pan, _ -> zoom.transform(centroid, scale, pan) }
        Box(Modifier.fillMaxSize()
            .transformable(gestureState, canPan = { zoom.scale > 1.01f }, lockRotationOnZoomPan = true)
            .pointerInput(zoom, settings.tapLayout, settings.rightToLeft, height) {
                detectTapGestures(
                    onDoubleTap = { zoom.toggle(it) }, onLongPress = { onToggleChrome() },
                    onTap = { point ->
                        if (zoom.scale > 1.01f) onToggleChrome()
                        else handleVisualTap(point.x / size.width.coerceAtLeast(1), settings,
                            { scope.launch { list.scrollBy(height * 0.85f) } },
                            { scope.launch { list.scrollBy(-height * 0.85f) } }, onToggleChrome)
                    },
                )
            }) {
            LazyColumn(state = list, userScrollEnabled = zoom.scale <= 1.01f,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                }) {
                itemsIndexed(slices, key = { index, _ -> index }) { _, slice ->
                    val displayHeight = wholePageHeight(slice.page) * slice.fraction
                    PageBitmap(source, slice.page, rasterWidth(width * zoom.scale), onError,
                        top = slice.top, bottom = slice.bottom, contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxWidth().height(with(density) { displayHeight.toDp() }))
                }
            }
        }
    }
}

@Composable
private fun PageBitmap(
    source: VisualPageSource,
    page: Int,
    requestedWidth: Int,
    onError: (String) -> Unit,
    modifier: Modifier,
    top: Float = 0f,
    bottom: Float = 1f,
    contentScale: ContentScale,
) {
    var bitmap by remember(source, page, top, bottom) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(source, page, top, bottom) { mutableStateOf<String?>(null) }
    val errorCallback by rememberUpdatedState(onError)
    LaunchedEffect(source, page, requestedWidth, top, bottom) {
        try {
            if (bitmap != null) delay(120)
            val rendered = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                source.render(page, requestedWidth, top, bottom)
            }
            bitmap = rendered
            failure = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val message = error.message ?: "${page + 1}쪽을 읽을 수 없습니다."
            failure = message
            errorCallback(message)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        when {
            current != null -> Image(current.asImageBitmap(), "${page + 1}쪽 · ${source.labels[page]}",
                contentScale = contentScale, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            failure != null -> Text(failure.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

private fun rasterWidth(width: Float): Int = (ceil(width.coerceAtLeast(1f) / 128f) * 128f).toInt().coerceIn(128, 8192)

private fun handleVisualTap(x: Float, settings: ReaderSettings, next: () -> Unit, previous: () -> Unit, chrome: () -> Unit) {
    if (settings.tapLayout == TapLayout.ANYWHERE_NEXT) { next(); return }
    val nextOnRight = (settings.tapLayout != TapLayout.LEFT_HANDED) != settings.rightToLeft
    when {
        x < 0.30f -> if (nextOnRight) previous() else next()
        x > 0.70f -> if (nextOnRight) next() else previous()
        else -> chrome()
    }
}

internal class ViewportZoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport: Size = Size.Zero
    var content: Size = Size.Zero
    fun transform(centroid: Offset, change: Float, pan: Offset) {
        val nextScale = (scale * change).coerceIn(1f, 5f)
        val ratio = nextScale / scale
        val center = Offset(viewport.width / 2, viewport.height / 2)
        val focus = if (centroid.isSpecified) centroid - center else Offset.Zero
        // The content under the fingers stays there while zooming, then follows their pan.
        offset = bound((offset - focus) * ratio + focus + pan, nextScale)
        scale = nextScale
    }
    fun toggle(point: Offset) {
        if (scale > 1.01f) reset()
        else { scale = 2.5f; offset = bound((Offset(viewport.width / 2, viewport.height / 2) - point) * (scale - 1), scale) }
    }
    fun reset() { scale = 1f; offset = Offset.Zero }
    fun rebound() { offset = bound(offset, scale) }
    private fun bound(value: Offset, zoom: Float): Offset {
        val x = max(0f, (content.width * zoom - viewport.width) / 2)
        val y = max(0f, (content.height * zoom - viewport.height) / 2)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
}
