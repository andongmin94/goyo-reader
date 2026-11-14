@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package kr.goyo.reader.reader.epub

import android.app.Application
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubPublicationTest {
    @Test
    fun `opens real EPUB with Korean search outline and resumable locators`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = createEpub(context.cacheDir)
        val book = openEpub(context, file)
        try {
            assertTrue(book.conformsTo(Publication.Profile.EPUB))
            assertEquals("고요한 서재", book.metadata.title)
            assertEquals(2, book.readingOrder.size)
            assertTrue(book.positions().isNotEmpty())
            assertEquals(listOf("첫째 장", "둘째 장"), book.outlineEntries().map { it.title })

            val iterator = requireNotNull(book.search("바다"))
            val matches = try {
                buildList {
                    while (true) {
                        val page = iterator.next().getOrElse { error(it.message) } ?: break
                        addAll(page.locators)
                    }
                }
            } finally {
                iterator.close()
            }
            assertEquals(2, matches.size)
            assertTrue(matches.all { it.text.highlight == "바다" })
            val saved = matches.last().readingPosition()
            val restored = locatorFromJson(requireNotNull(saved.locator))
            assertEquals(matches.last().href, restored.href)
            assertEquals(matches.last().text, restored.text)
            assertEquals(matches.last().locations, restored.locations)
        } finally {
            book.close()
            file.delete()
        }
    }

    @Test
    fun `external resources fail locally without an HTTP request`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = createEpub(context.cacheDir)
        val book = openEpub(context, file)
        try {
            val image = requireNotNull(book.get(requireNotNull(Url("https://example.invalid/picture.png"))))
            try {
                assertTrue(image.read().isFailure)
            } finally {
                image.close()
            }
        } finally {
            book.close()
            file.delete()
        }
    }

    private fun createEpub(directory: File): File {
        val file = File.createTempFile("goyo-epub-", ".epub", directory)
        ZipOutputStream(file.outputStream()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = size
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime)
            zip.closeEntry()
            fun entry(name: String, contents: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml" /></rootfiles>
                </container>
            """)
            entry("EPUB/book.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">urn:goyo:epub-test</dc:identifier>
                    <dc:title>고요한 서재</dc:title><dc:language>ko</dc:language>
                    <meta property="dcterms:modified">2026-11-14T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav" />
                    <item id="first" href="first.xhtml" media-type="application/xhtml+xml" />
                    <item id="second" href="second.xhtml" media-type="application/xhtml+xml" />
                    <item id="remote" href="https://example.invalid/picture.png" media-type="image/png" />
                  </manifest>
                  <spine><itemref idref="first" /><itemref idref="second" /></spine>
                </package>
            """)
            entry("EPUB/nav.xhtml", """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <head><title>목차</title></head><body><nav epub:type="toc"><ol>
                    <li><a href="first.xhtml#first">첫째 장</a></li>
                    <li><a href="second.xhtml#second">둘째 장</a></li>
                  </ol></nav></body>
                </html>
            """)
            entry("EPUB/first.xhtml", """
                <html xmlns="http://www.w3.org/1999/xhtml" lang="ko"><head><title>첫째 장</title></head>
                  <body><h1 id="first">첫째 장</h1><p>고요한 바다 옆에서 책을 읽습니다.</p></body>
                </html>
            """)
            entry("EPUB/second.xhtml", """
                <html xmlns="http://www.w3.org/1999/xhtml" lang="ko"><head><title>둘째 장</title></head>
                  <body><h1 id="second">둘째 장</h1><p>밤이 되면 바다 위에 달이 뜹니다.</p></body>
                </html>
            """)
        }
        return file
    }
}
