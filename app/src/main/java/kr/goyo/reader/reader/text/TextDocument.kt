package kr.goyo.reader.reader.text

import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.mozilla.universalchardet.UniversalDetector

data class TextDocument(val text: String, val encoding: String)

/** Decode once; all reading positions thereafter refer to UTF-16 offsets in this text. */
object TextDecoder {
    private const val SAMPLE_BYTES = 65_536

    fun read(file: File, requestedEncoding: String = "AUTO"): TextDocument =
        file.inputStream().buffered().use { input ->
            input.mark(SAMPLE_BYTES + 1)
            val sample = ByteArray(minOf(file.length(), SAMPLE_BYTES.toLong()).toInt())
            var length = 0
            while (length < sample.size) {
                val read = input.read(sample, length, sample.size - length)
                if (read < 0) break
                length += read
            }
            input.reset()
            val charset = selectCharset(sample.copyOf(length), requestedEncoding, length.toLong() == file.length())
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val text = InputStreamReader(input, decoder).readText().removePrefix("\uFEFF")
                .replace("\r\n", "\n").replace('\r', '\n')
            TextDocument(text, charset.name())
        }

    fun decode(bytes: ByteArray, requestedEncoding: String = "AUTO"): TextDocument {
        val charset = selectCharset(bytes, requestedEncoding, true)
        return TextDocument(
            bytes.toString(charset).removePrefix("\uFEFF")
                .replace("\r\n", "\n").replace('\r', '\n'),
            charset.name(),
        )
    }

    private fun selectCharset(sample: ByteArray, requested: String, complete: Boolean): Charset {
        if (requested != "AUTO") return Charset.forName(requested)
        val unsigned = sample.take(4).map { it.toInt() and 0xff }
        when {
            unsigned.take(4) == listOf(0x00, 0x00, 0xfe, 0xff) -> return Charset.forName("UTF-32BE")
            unsigned.take(4) == listOf(0xff, 0xfe, 0x00, 0x00) -> return Charset.forName("UTF-32LE")
            unsigned.take(3) == listOf(0xef, 0xbb, 0xbf) -> return Charsets.UTF_8
            unsigned.take(2) == listOf(0xff, 0xfe) -> return Charsets.UTF_16LE
            unsigned.take(2) == listOf(0xfe, 0xff) -> return Charsets.UTF_16BE
        }
        if (!sample.contains(0) && isUtf8(sample, complete)) return Charsets.UTF_8
        val detector = UniversalDetector()
        detector.handleData(sample, 0, sample.size)
        detector.dataEnd()
        val detected = detector.detectedCharset
        // MS949 also decodes EUC-KR and covers Hangul syllables outside its smaller repertoire.
        return when (detected?.uppercase()) {
            "EUC-KR", "WINDOWS-949", "CP949" -> Charset.forName("MS949")
            null -> if (isUtf8(sample, complete)) Charsets.UTF_8 else Charset.forName("MS949")
            else -> Charset.forName(detected)
        }
    }

    private fun isUtf8(bytes: ByteArray, complete: Boolean): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(bytes), CharBuffer.allocate(bytes.size + 1), complete)
        return !result.isError
    }
}

/** Never split the two UTF-16 code units of a supplementary character. */
fun safeTextOffset(text: String, offset: Int): Int {
    val result = offset.coerceIn(0, text.length)
    return if (result > 0 && result < text.length &&
        text[result].isLowSurrogate() && text[result - 1].isHighSurrogate()
    ) result - 1 else result
}

fun pageForOffset(starts: IntArray, offset: Int): Int {
    if (starts.isEmpty()) return 0
    val found = starts.binarySearch(offset)
    return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
}

fun findTextMatches(text: String, query: String, limit: Int = 500): List<Int> {
    if (query.isBlank() || text.isEmpty() || limit <= 0) return emptyList()
    val matches = ArrayList<Int>()
    var cursor = 0
    while (cursor < text.length && matches.size < limit) {
        val match = text.indexOf(query, cursor, ignoreCase = true)
        if (match < 0) break
        matches += match
        cursor = match + query.length
    }
    return matches
}
