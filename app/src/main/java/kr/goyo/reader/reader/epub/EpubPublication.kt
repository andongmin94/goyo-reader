@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package kr.goyo.reader.reader.epub

import android.content.Context
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.IOException
import kr.goyo.reader.model.OutlineEntry
import kr.goyo.reader.model.ReaderFont
import kr.goyo.reader.model.ReaderSettings
import kr.goyo.reader.model.ReadingMode
import kr.goyo.reader.model.ReadingPosition
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

internal val customFontFamily = FontFamily("Goyo User Font")

private object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.IO(IOException("외부 자료는 오프라인에서 열 수 없습니다.")))
}

internal suspend fun openEpub(context: Context, file: File): Publication {
    val retriever = AssetRetriever(context.contentResolver, OfflineHttpClient)
    val asset = retriever.retrieve(file).getOrElse {
        throw IOException("EPUB 파일을 읽을 수 없습니다. ${it.message}")
    }
    var publication: Publication? = null
    try {
        publication = PublicationOpener(EpubParser(OfflineHttpClient))
            .open(asset, allowUserInteraction = false)
            .getOrElse { throw IOException("EPUB 내용을 열 수 없습니다. ${it.message}") }
        if (publication.isRestricted) {
            throw IOException("이 EPUB은 DRM으로 보호되어 있습니다. DRM 없는 EPUB을 열어 주세요.")
        }
        if (publication.readingOrder.isEmpty()) {
            throw IOException("이 EPUB에는 읽을 수 있는 본문이 없습니다.")
        }
        return publication
    } catch (error: Throwable) {
        if (publication != null) publication.close() else asset.close()
        throw error
    }
}

internal fun ReaderSettings.epubPreferences(): EpubPreferences = EpubPreferences(
    fontFamily = when (font) {
        ReaderFont.SYSTEM -> FontFamily.SANS_SERIF
        ReaderFont.SERIF -> FontFamily.SERIF
        ReaderFont.MONOSPACE -> FontFamily.MONOSPACE
        ReaderFont.CUSTOM -> customFontFamily
    },
    fontSize = fontSizeSp / 16.0,
    lineHeight = lineSpacing.toDouble(),
    pageMargins = marginDp / 24.0,
    backgroundColor = Color(backgroundColor.toInt()),
    textColor = Color(textColor.toInt()),
    theme = if (ColorUtils.calculateLuminance(backgroundColor.toInt()) < 0.25) Theme.DARK else Theme.LIGHT,
    publisherStyles = false,
    scroll = readingMode == ReadingMode.SCROLL,
    readingProgression = if (rightToLeft) ReadingProgression.RTL else ReadingProgression.LTR,
    columnCount = ColumnCount.ONE,
    spread = Spread.NEVER,
    textAlign = TextAlign.START,
)

internal fun locatorFromJson(json: String): Locator =
    requireNotNull(Locator.fromJSON(JSONObject(json))) { "저장된 EPUB 읽기 위치를 해석할 수 없습니다." }

internal fun Locator.readingPosition(): ReadingPosition = ReadingPosition(
    index = ((locations.position ?: 1) - 1).coerceAtLeast(0),
    offset = (locations.progression ?: 0.0).toFloat(),
    locator = toJSON().toString(),
    progress = (locations.totalProgression ?: 0.0).toFloat(),
)

internal fun Publication.outlineEntries(): List<OutlineEntry> = buildList {
    fun addLinks(links: List<Link>, depth: Int) {
        links.forEach { link ->
            locatorFromLink(link)?.let { locator ->
                add(OutlineEntry(link.title ?: "제목 없는 장", locator.readingPosition(), depth))
            }
            addLinks(link.children, depth + 1)
        }
    }
    addLinks(tableOfContents, 0)
}
