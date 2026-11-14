package kr.goyo.reader

import java.nio.charset.Charset
import java.nio.file.Files
import kr.goyo.reader.reader.text.TextDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextDocumentTest {
    @Test
    fun utf8BomAndWindowsNewlinesHaveStableCanonicalOffsets() {
        val original = "\uFEFF첫째 줄\r\n둘째 줄\r끝"
        val decoded = TextDecoder.decode(original.toByteArray(Charsets.UTF_8))
        assertEquals("첫째 줄\n둘째 줄\n끝", decoded.text)
        assertEquals("UTF-8", decoded.encoding)
    }

    @Test
    fun bomSelectsUtf16ByteOrder() {
        val text = "한글 소설과 English. 🎐"
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val bytes = ("\uFEFF" + text).toByteArray(charset)
            assertEquals(text, TextDecoder.decode(bytes).text)
        }
    }

    @Test
    fun autoDetectsTypicalKoreanLegacyText() {
        val text = "눈이 내리는 날이었다. 오랜 시간이 지나도 그날의 이야기는 기억에 남아 있었다. ".repeat(15)
        val encoded = text.toByteArray(Charset.forName("MS949"))
        assertEquals(text, TextDecoder.decode(encoded).text)
    }

    @Test
    fun explicitMs949IncludesExtendedHangul() {
        val text = "똠방각하와 쀍, 뷁이라는 글자도 보존한다."
        val encoded = text.toByteArray(Charset.forName("MS949"))
        assertEquals(text, TextDecoder.decode(encoded, "MS949").text)
    }

    @Test
    fun utf8CharacterAcrossDetectionSampleBoundaryIsNotMisclassified() {
        val text = "a".repeat(65_535) + "한글 끝"
        val file = Files.createTempFile("goyo-text", ".txt").toFile()
        try {
            file.writeText(text, Charsets.UTF_8)
            val decoded = TextDecoder.read(file)
            assertEquals(text, decoded.text)
            assertEquals("UTF-8", decoded.encoding)
            assertFalse(decoded.text.contains('\uFFFD'))
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyFileRemainsEmpty() {
        assertEquals("", TextDecoder.decode(byteArrayOf()).text)
    }
}
