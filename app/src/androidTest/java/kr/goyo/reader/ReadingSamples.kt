package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

/** Original test documents. No screenshot, app controls or copyrighted book pages are baked in. */
internal object ReadingSamples {
    const val textName = "파동의 기록.txt"
    const val pdfName = "빛과 파동.pdf"
    const val imageName = "01-관측일지.png"
    const val zipName = "관측일지.zip"
    const val epubName = "파동의 기록.epub"
    const val marker = "창가에 놓인 물컵"
    const val blue = 0xFF0035FF.toInt()
    val paragraphs = listOf(
        "창가에 놓인 물컵 위로 빛이 내려앉았다. 작은 물방울이 떨어지자 동그란 무늬가 가장자리로 퍼져 나갔다.",
        "파동을 이해하는 첫걸음은 물질의 이동과 변화의 전달을 구분하는 일이다. 물 위의 잎은 제자리에서 흔들려도, 수면의 무늬는 앞으로 나아간다.",
        "한 점을 오래 관찰하면 시간에 따른 진동이 보인다. 한순간의 전체 모습을 보면 공간에 반복되는 무늬가 보인다.",
        "이 기록은 익숙한 풍경에서 출발한다. 오늘은 빛이 지나간 자리를, 내일은 그 빛을 바라보는 방법을 살펴본다.",
    )

    fun create(directory: File) {
        directory.mkdirs()
        File(directory, textName).writeText("01  파동의 기록\n\n" + (paragraphs.joinToString("\n\n") + "\n\n").repeat(8))
        val image = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(image)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun label(text: String, x: Float, y: Float, size: Float, color: Int = Color.DKGRAY) {
                p.color = color; p.textSize = size; p.style = Paint.Style.FILL
                c.drawText(text, x, y, p)
            }
            label("OBSERVATION / 01", 36f, 50f, 18f, blue)
            label("관측 일지", 36f, 99f, 36f)
            p.color = Color.rgb(22, 32, 56); c.drawRect(30f, 130f, 570f, 430f, p)
            p.color = Color.WHITE; c.drawCircle(435f, 205f, 36f, p)
            p.color = Color.rgb(46, 66, 102)
            val mountains = Path().apply { moveTo(30f, 390f); lineTo(170f, 250f); lineTo(310f, 390f); lineTo(440f, 290f); lineTo(570f, 410f); lineTo(570f, 430f); lineTo(30f, 430f); close() }
            c.drawPath(mountains, p)
            p.color = blue; c.drawRect(40f, 445f, 48f, 483f, p)
            label("오늘의 관측을 시작합니다.", 64f, 472f, 22f)
            p.color = Color.rgb(243, 245, 249); c.drawRect(30f, 510f, 570f, 745f, p)
            p.color = blue; c.drawCircle(168f, 620f, 56f, p)
            p.color = Color.WHITE; c.drawCircle(183f, 602f, 48f, p)
            label("빛이 지나간 자리.", 280f, 614f, 21f)
            label("같은 하늘, 다른 시선.", 280f, 650f, 18f, Color.GRAY)
            label("MIN VIEWER / ORIGINAL TEST PAGE", 36f, 795f, 15f, Color.GRAY)
            File(directory, imageName).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
        ZipOutputStream(File(directory, zipName).outputStream()).use { z ->
            for (n in listOf("chapter/010.png", "chapter/002.png")) {
                z.putNextEntry(ZipEntry(n)); File(directory, imageName).inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        val pdf = PdfDocument()
        try {
            for (number in 1..3) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 840, number).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                fun text(value: String, x: Float, y: Float, size: Float, color: Int = Color.DKGRAY, bold: Boolean = false) {
                    p.color = color; p.textSize = size; p.style = Paint.Style.FILL
                    p.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
                    c.drawText(value, x, y, p)
                }
                text("PHYSICS / STUDY NOTES", 42f, 52f, 16f, blue)
                text("빛과 파동", 42f, 109f, 40f, bold = true)
                text("01  진동을 바라보는 두 가지 방법", 42f, 155f, 21f)
                p.color = blue; c.drawRect(42f, 177f, 558f, 181f, p)
                text("한 점에서 시간에 따른 변화를 관찰하면 주기가,", 42f, 222f, 20f)
                text("한순간의 공간 분포를 관찰하면 파장이 보인다.", 42f, 253f, 20f)
                p.color = Color.rgb(243, 245, 249); c.drawRect(42f, 286f, 558f, 543f, p)
                p.color = Color.LTGRAY; p.strokeWidth = 1f
                c.drawLine(70f, 415f, 530f, 415f, p); c.drawLine(70f, 310f, 70f, 519f, p)
                p.color = blue; p.strokeWidth = 3f
                for (x in 71..530) c.drawLine((x - 1).toFloat(), 415f - 78f * sin((x - 71) / 48f), x.toFloat(), 415f - 78f * sin((x - 70) / 48f), p)
                text("y(x, t) = A sin(kx − ωt)", 105f, 586f, 27f, blue)
                text("A  진폭     k  파수     ω  각진동수", 70f, 625f, 20f)
                text("읽기 연습", 42f, 689f, 23f, bold = true)
                text("서로 다른 위치의 위상 차이를 비교해 보자.", 42f, 726f, 20f)
                text("MIN VIEWER / SAMPLE", 42f, 800f, 14f, Color.GRAY)
                text("$number / 3", 510f, 800f, 14f, Color.GRAY)
                pdf.finishPage(page)
            }
            File(directory, pdfName).outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        ZipOutputStream(File(directory, epubName).outputStream()).use { z ->
            fun entry(name: String, content: String, stored: Boolean = false) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val e = ZipEntry(name)
                if (stored) { e.method = ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = e.size; e.crc = CRC32().apply { update(bytes) }.value }
                z.putNextEntry(e); z.write(bytes); z.closeEntry()
            }
            entry("mimetype", "application/epub+zip", true)
            entry("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("EPUB/package.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:ef6d4063-b18b-4af4-9695-001100220033</dc:identifier><dc:title>파동의 기록</dc:title><dc:language>ko</dc:language><dc:creator>Min Viewer</dc:creator><meta property="dcterms:modified">2026-11-16T00:00:00Z</meta></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="chapter"/></spine></package>""")
            entry("EPUB/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>목차</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">파동의 기록</a></li></ol></nav></body></html>""")
            entry("EPUB/chapter.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" lang="ko"><head><title>파동의 기록</title></head><body><h1>파동의 기록</h1><h2>01. 빛이 지나간 자리</h2>""" +
                paragraphs.joinToString("") { "<p>$it</p>" }.repeat(8) + "</body></html>")
        }
    }
}
