package kr.goyo.reader

import kr.goyo.reader.reader.text.findTextMatches
import kr.goyo.reader.reader.text.pageForOffset
import kr.goyo.reader.reader.text.safeTextOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TextOffsetsTest {
    @Test
    fun pageContainingSavedCharacterIsFoundAfterReflow() {
        val oldStarts = intArrayOf(0, 120, 240, 360)
        val newStarts = intArrayOf(0, 80, 160, 240, 320, 400)
        val savedCharacter = 270
        assertEquals(2, pageForOffset(oldStarts, savedCharacter))
        assertEquals(3, pageForOffset(newStarts, savedCharacter))
        assertEquals(4, pageForOffset(newStarts, 320))
        assertEquals(0, pageForOffset(newStarts, -1))
        assertEquals(5, pageForOffset(newStarts, 999))
    }

    @Test
    fun restoredOffsetDoesNotSplitEmojiSurrogatePair() {
        val text = "가🎐나"
        assertEquals(1, safeTextOffset(text, 2))
        assertEquals(3, safeTextOffset(text, 3))
        assertEquals(0, safeTextOffset(text, -1))
        assertEquals(text.length, safeTextOffset(text, 99))
    }

    @Test
    fun searchFindsCaseInsensitiveAndKoreanMatches() {
        assertEquals(listOf(0, 6), findTextMatches("Hello HELLO 안녕 안녕", "hello"))
        assertEquals(listOf(12, 15), findTextMatches("Hello HELLO 안녕 안녕", "안녕"))
        assertEquals(emptyList<Int>(), findTextMatches("가나다", "  "))
        assertEquals(listOf(0, 1), findTextMatches("가가가가", "가", 2))
    }
}
