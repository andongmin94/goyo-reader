package kr.goyo.reader.reader

import java.io.File
import kr.goyo.reader.data.FileEntry
import kr.goyo.reader.model.BookRecord
import kr.goyo.reader.model.ReadingPosition

sealed interface ReaderInput {
    data class LocalFile(val file: File) : ReaderInput
    data class Images(val entries: List<FileEntry>) : ReaderInput {
        init { require(entries.isNotEmpty()) }
        fun resolve(position: ReadingPosition): ReadingPosition {
            val located = entries.indexOfFirst { it.uri == position.locator }
            return position.copy(index = if (located >= 0) located else position.index.coerceIn(entries.indices))
        }
        fun anchor(position: ReadingPosition): ReadingPosition = position.copy(
            locator = entries[position.index.coerceIn(entries.indices)].uri,
        )
    }
}

data class OpenedDocument(val book: BookRecord, val input: ReaderInput)
