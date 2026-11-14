@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
)

package kr.goyo.reader.reader.epub

import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.goyo.reader.R
import kr.goyo.reader.model.ReaderCommand
import kr.goyo.reader.model.ReaderInfo
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingMode
import kr.goyo.reader.model.SearchResult
import kr.goyo.reader.model.TapLayout
import kr.goyo.reader.reader.EpubConnection
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.getOrElse

/** A no-argument host lets Android restore the screen before the EPUB file has been reopened. */
class EpubHostFragment : Fragment() {
    internal companion object {
        const val ARG_FILE = "epub.file"
        const val ARG_LOCATOR = "epub.locator"
        const val ARG_PROGRESS = "epub.progress"
        const val ARG_FONT = "epub.font"
        private const val STATE_LOCATOR = "epub.currentLocator"
    }

    private val connection = MutableStateFlow<EpubConnection?>(null)
    private val readyNavigator = MutableStateFlow<EpubNavigatorFragment?>(null)
    private var publication: Publication? = null
    private var latestLocator: Locator? = null
    private var restoredLocator: String? = null
    private var positions: List<Locator> = emptyList()
    private var positionsByPath: Map<String?, List<Locator>> = emptyMap()
    private var baseInfo = ReaderInfo()
    private var loadingView: ProgressBar? = null
    private var errorView: TextView? = null
    private var noticeToast: Toast? = null
    private var searchJob: Job? = null
    private var pageLocation: PageLocation? = null

    private data class PageLocation(val index: Int, val total: Int, val locator: Locator)

    internal fun connect(value: EpubConnection) {
        connection.value = value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium's real factory needs a Publication. Its provided dummy factory exists exactly
        // for process recreation. Remove restored children synchronously, before onResume,
        // then reopen the local file and restore the saved content Locator below.
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        childFragmentManager.fragments.takeIf { it.isNotEmpty() }?.let { restored ->
            childFragmentManager.beginTransaction().apply {
                restored.forEach(::remove)
            }.commitNow()
        }
        restoredLocator = savedInstanceState?.getString(STATE_LOCATOR)
            ?: arguments?.getString(ARG_LOCATOR)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext()).apply {
        addView(FragmentContainerView(context).apply {
            id = R.id.epub_navigator_container
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        loadingView = ProgressBar(context).also {
            it.contentDescription = "EPUB을 여는 중"
            addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        errorView = TextView(context).also {
            it.visibility = View.GONE
            it.gravity = Gravity.CENTER
            it.textSize = 16f
            val padding = (24 * resources.displayMetrics.density).toInt()
            it.setPadding(padding, padding, padding, padding)
            addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch { loadPublication() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(readyNavigator.filterNotNull(), connection.filterNotNull()) { navigator, callbacks ->
                    navigator to callbacks
                }.collectLatest { (navigator, callbacks) ->
                    bindNavigator(navigator, callbacks)
                }
            }
        }
    }

    private suspend fun loadPublication() {
        var opened: Publication? = null
        try {
            val args = requireArguments()
            val file = File(requireNotNull(args.getString(ARG_FILE)))
            val fontFile = args.getString(ARG_FONT)?.let(::File)
            val appContext = requireContext().applicationContext
            var fontSource: Url? = null
            withContext(Dispatchers.IO) {
                opened = publication ?: openEpub(appContext, file)
                positions = requireNotNull(opened).positions()
                positionsByPath = positions.groupBy { it.href.normalize().path?.trimStart('/') }
                if (fontFile != null) {
                    val mimeType = if (fontFile.extension.equals("otf", true)) "font/otf" else "font/ttf"
                    // Readium injects declared fonts into each loaded resource. A data URL
                    // supports imported local fonts without a server or cross-origin requests.
                    fontSource = requireNotNull(Url("data:$mimeType;base64," + Base64.encodeToString(fontFile.readBytes(), Base64.NO_WRAP)))
                }
            }
            currentCoroutineContext().ensureActive()
            val book = requireNotNull(opened)
            val callbacks = connection.filterNotNull().first()
            val initial = latestLocator ?: restoredLocator?.let(::locatorFromJson)
                ?: book.locateProgression(args.getFloat(ARG_PROGRESS).toDouble())
            val positionCount = positions.size.coerceAtLeast(1)
            baseInfo = ReaderInfo(
                current = (initial?.locations?.position ?: 1).coerceIn(1, positionCount),
                total = positionCount,
                supportsSearch = book.isSearchable,
                outline = book.outlineEntries(),
            )
            val factory = EpubNavigatorFactory(book).createFragmentFactory(
                initialLocator = initial,
                initialPreferences = callbacks.settings.value.epubPreferences(),
                listener = object : EpubNavigatorFragment.Listener {
                    override fun onResourceLoadFailed(href: Url, error: ReadError) {
                        showNotice("일부 그림이나 글꼴을 불러오지 못했습니다.")
                    }

                    override fun onExternalLinkActivated(url: AbsoluteUrl) {
                        showNotice("외부 링크는 오프라인에서 열 수 없습니다.")
                    }
                },
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                        pageLocation = PageLocation(pageIndex, totalPages, locator)
                    }
                },
                configuration = EpubNavigatorFragment.Configuration {
                    // Compose applies the system bar and toolbar insets around this fragment.
                    shouldApplyInsetsPadding = false
                    fontSource?.let { source ->
                        addFontFamilyDeclaration(customFontFamily) {
                            addFontFace { addSource(source) }
                        }
                    }
                },
            )
            val navigator = viewLifecycleOwner.lifecycle.withResumed {
                childFragmentManager.fragmentFactory = factory
                val instance = factory.instantiate(
                    requireContext().classLoader,
                    EpubNavigatorFragment::class.java.name,
                ) as EpubNavigatorFragment
                childFragmentManager.beginTransaction()
                    .replace(R.id.epub_navigator_container, instance, "epub-navigator")
                    .commitNow()
                instance
            }
            publication = book
            readyNavigator.value = navigator
            loadingView?.visibility = View.GONE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: "EPUB을 열 수 없습니다."
            loadingView?.visibility = View.GONE
            errorView?.apply {
                text = message
                setTextColor(connection.value?.settings?.value?.textColor?.toInt() ?: 0xFF262A27.toInt())
                visibility = View.VISIBLE
            }
            connection.value?.onError(message)
        } finally {
            if (publication !== opened) opened?.close()
        }
    }

    private suspend fun bindNavigator(
        navigator: EpubNavigatorFragment,
        callbacks: EpubConnection,
    ): Unit = coroutineScope {
        val book = requireNotNull(publication)
        callbacks.onInfo(baseInfo)
        val taps = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val preferences = callbacks.settings.value
                val fraction = event.point.x / navigator.publicationView.width.coerceAtLeast(1)
                if (preferences.tapLayout == TapLayout.ANYWHERE_NEXT) {
                    launch { turn(navigator, book, forward = true) }
                } else if (fraction in 0.3f..0.7f) {
                    callbacks.onToggleChrome()
                } else {
                    val nextOnLeft = preferences.rightToLeft xor (preferences.tapLayout == TapLayout.LEFT_HANDED)
                    launch { turn(navigator, book, forward = (fraction < 0.3f) == nextOnLeft) }
                }
                return true
            }
        }
        navigator.addInputListener(taps)
        try {
            launch {
                // Keep Readium's full Locator, including within-resource progression. Replacing
                // it with the first visible block would jump backward in multi-page paragraphs.
                navigator.currentLocator.collect { locator ->
                    latestLocator = locator
                    callbacks.onPosition(locator.readingPosition())
                    callbacks.onInfo(baseInfo.copy(
                        current = (locator.locations.position ?: 1).coerceIn(1, baseInfo.total),
                    ))
                }
            }
            launch {
                callbacks.settings.collect { preferences ->
                    navigator.submitPreferences(preferences.epubPreferences())
                }
            }
            launch {
                callbacks.commands.collect { command ->
                    when (command) {
                        ReaderCommand.Next -> turn(navigator, book, forward = true)
                        ReaderCommand.Previous -> turn(navigator, book, forward = false)
                        is ReaderCommand.Seek -> {
                            val locator = command.position.locator?.let(::locatorFromJson)
                                ?: book.locateProgression(command.position.progress.toDouble().coerceIn(0.0, 1.0))
                            locator?.let { navigator.go(it) }
                        }
                        is ReaderCommand.SeekProgress -> {
                            book.locateProgression(command.progress.toDouble().coerceIn(0.0, 1.0))
                                ?.let { navigator.go(it) }
                        }
                        is ReaderCommand.SeekPage -> {
                            positions.getOrNull(command.page - 1)?.let { navigator.go(it) }
                        }
                        is ReaderCommand.Search -> {
                            searchJob?.cancel()
                            // Keep an in-flight search across STOP/START; destroy the view to cancel it.
                            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    callbacks.onSearchResults(search(book, command.query))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    callbacks.onSearchResults(emptyList())
                                    showNotice("본문 검색을 완료하지 못했습니다.")
                                }
                            }
                        }
                    }
                }
            }
            launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    combine(callbacks.autoReading, callbacks.settings) { enabled, preferences -> enabled to preferences }
                        .collectLatest { (enabled, preferences) ->
                            if (enabled) autoRead(navigator, book, preferences)
                        }
                }
            }
            awaitCancellation()
        } finally {
            navigator.removeInputListener(taps)
        }
    }

    private suspend fun search(book: Publication, query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val iterator = book.search(query) ?: return@withContext emptyList()
        try {
            buildList {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = iterator.next().getOrElse {
                        throw IllegalStateException("EPUB 검색을 완료하지 못했습니다. ${it.message}")
                    } ?: break
                    page.locators.forEach { locator ->
                        add(SearchResult(
                            title = locator.title ?: "검색 결과",
                            excerpt = listOfNotNull(
                                locator.text.before?.takeLast(80),
                                locator.text.highlight,
                                locator.text.after?.take(120),
                            ).joinToString("").replace(Regex("\\s+"), " ").trim(),
                            position = locator.readingPosition(),
                        ))
                    }
                }
            }
        } finally {
            iterator.close()
        }
    }

    private suspend fun turn(navigator: EpubNavigatorFragment, book: Publication, forward: Boolean) {
        if (navigator.overflow.value.scroll && navigator.overflow.value.axis == Axis.VERTICAL) {
            val direction = if (forward) 1 else -1
            val moved = navigator.evaluateJavascript(
                """(() => {
                    const e = document.scrollingElement;
                    if (!e) return false;
                    const y = e.scrollTop;
                    e.scrollTop += $direction * e.clientHeight * 0.85;
                    return Math.abs(e.scrollTop - y) > 0.5;
                })()""".trimIndent(),
            ) == "true"
            if (!moved) moveResource(navigator, book, forward)
        } else {
            if (forward) navigator.goForward(animated = true) else navigator.goBackward(animated = true)
        }
    }

    private fun moveResource(
        navigator: EpubNavigatorFragment,
        book: Publication,
        forward: Boolean,
        currentHref: Url = navigator.currentLocator.value.href,
    ): Boolean {
        val href = currentHref.removeFragment().removeQuery()
        val index = book.readingOrder.indexOfFirst { it.url().removeFragment().removeQuery().isEquivalent(href) }
        if (index < 0) return false
        val next = book.readingOrder.getOrNull(index + if (forward) 1 else -1) ?: return false
        val target = book.locatorFromLink(next) ?: return false
        return navigator.go(target.copy(
            locations = target.locations.copy(progression = if (forward) 0.0 else 1.0),
        ))
    }

    private suspend fun autoRead(
        navigator: EpubNavigatorFragment,
        book: Publication,
        preferences: ReaderSettings,
    ) {
        if (preferences.readingMode == ReadingMode.SCROLL && book.metadata.layout != Layout.FIXED && navigator.overflow.value.axis == Axis.VERTICAL) {
            var lastTick = SystemClock.elapsedRealtime()
            var remainder = 0.0
            while (currentCoroutineContext().isActive) {
                delay(50)
                val now = SystemClock.elapsedRealtime()
                remainder += preferences.autoScrollDpPerSecond * (now - lastTick) / 1000.0
                lastTick = now
                val pixels = remainder.toInt()
                if (pixels < 1) continue
                remainder -= pixels
                val result = navigator.evaluateJavascript(
                    """(() => {
                        const e = document.scrollingElement;
                        if (!e) return null;
                        const y = e.scrollTop;
                        e.scrollTop += $pixels;
                        return {
                            moved: e.scrollTop > y,
                            href: document.location.href,
                            progression: e.scrollHeight > 0 ? e.scrollTop / e.scrollHeight : 0
                        };
                    })()""".trimIndent(),
                )?.takeUnless { it == "null" } ?: break
                val frame = JSONObject(result)
                reportAutoScroll(frame.getString("href"), frame.getDouble("progression"))
                if (!frame.getBoolean("moved")) {
                    val href = latestLocator?.href ?: navigator.currentLocator.value.href
                    if (isLastResource(book, href)) {
                        connection.value?.onAutoReadingFinished()
                        return
                    }
                    if (!moveResource(navigator, book, forward = true, currentHref = href)) break
                    delay(200)
                    lastTick = SystemClock.elapsedRealtime()
                }
            }
        } else {
            while (currentCoroutineContext().isActive) {
                delay(preferences.autoPageSeconds.coerceAtLeast(1) * 1000L)
                if (book.metadata.layout == Layout.FIXED) {
                    // Spread.NEVER keeps each fixed-layout resource on a separate screen.
                    if (!navigator.goForward(animated = true) && isLastResource(book, navigator.currentLocator.value.href)) {
                        connection.value?.onAutoReadingFinished()
                        return
                    }
                } else {
                    // Reflowable goForward() only reports whether a WebView exists, even at EOF.
                    // Readium reports settled, loaded pages with a zero-based physical index.
                    val page = pageLocation
                    if (page != null) {
                        val lastPage = if (navigator.settings.value.readingProgression == ReadingProgression.RTL) 0 else page.total - 1
                        // In horizontal scrolling mode, Readium's page turns move whole resources.
                        val atEndOfResource = navigator.overflow.value.scroll || page.index == lastPage
                        if (page.locator == navigator.currentLocator.value && isLastResource(book, page.locator.href) && atEndOfResource) {
                            connection.value?.onAutoReadingFinished()
                            return
                        }
                    }
                    navigator.goForward(animated = true)
                }
            }
        }
    }

    private fun isLastResource(book: Publication, href: Url): Boolean =
        book.readingOrder.last().url().removeFragment().removeQuery()
            .isEquivalent(href.removeFragment().removeQuery())

    private fun reportAutoScroll(resourceUrl: String, progression: Double) {
        // Readium emits locations after 100 ms of idle scrolling. Continuous automatic scrolling
        // never becomes idle, so derive the same position from its public position list while
        // recording the exact document scroll fraction used by Readium's scrollToPosition().
        // Readium 3.4 serves local EPUB reading-order paths at https://readium_package/.
        // Assets use the separate readium_assets host; no asset path prefix is added to a
        // document URL. normalize().path decodes both ZIP hrefs and the served URL equally.
        val path = Url(resourceUrl)?.normalize()?.path?.trimStart('/') ?: return
        val resourcePositions = positionsByPath[path].orEmpty()
        if (resourcePositions.isEmpty()) return
        val index = ceil(progression * (resourcePositions.size - 1)).toInt()
            .coerceIn(resourcePositions.indices)
        val position = resourcePositions[index]
        val locator = position.copy(locations = position.locations.copy(progression = progression))
        latestLocator = locator
        connection.value?.let { callbacks ->
            callbacks.onPosition(locator.readingPosition())
            callbacks.onInfo(baseInfo.copy(current = (locator.locations.position ?: 1).coerceIn(1, baseInfo.total)))
        }
    }

    private fun showNotice(message: String) {
        // Missing optional resources are nonfatal; callbacks.onError closes the reading screen.
        // Readium can report these failures from a WebView worker thread.
        view?.post {
            val appContext = context?.applicationContext ?: return@post
            noticeToast?.cancel()
            noticeToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LOCATOR, (latestLocator ?: readyNavigator.value?.currentLocator?.value)?.toJSON()?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        readyNavigator.value = null
        pageLocation = null
        searchJob = null
        noticeToast?.cancel()
        noticeToast = null
        loadingView = null
        errorView = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
    }
}
