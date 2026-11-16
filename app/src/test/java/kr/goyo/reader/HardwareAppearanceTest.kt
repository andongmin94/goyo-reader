package kr.goyo.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Capture the actual app with valid local reading documents, not an HTML mock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MinViewerApplication::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HardwareAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun lightDirectorySettingsAndTextReaderShareTheHardwareComponents() {
        val folder = browseSamples()
        capture("spec-directory-light.png")
        compose.onNodeWithContentDescription("독서 설정").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("글자 크기 늘리기")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("글자 크기 늘리기").performClick()
        compose.waitUntil(5_000) { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].library.value.settings.fontSizeSp == 19f }
        capture("spec-settings.png")
        // Lazy settings below the viewport are not composed until scrolled into view.
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("읽기 설정 닫기"))
        compose.onNodeWithText("읽기 설정 닫기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("파일명 검색")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("01. 파동과 빛.txt").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("본문 검색") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("독서 설정").assertIsDisplayed()
        compose.onNodeWithContentDescription("책갈피 추가").assertIsDisplayed()
        capture("spec-text-reader.png")
        compose.onNodeWithContentDescription("폴더로 돌아가기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("01. 파동과 빛.txt")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(File(folder, "01. 파동과 빛.txt").isFile)
    }

    @Test @Config(qualifiers = "w390dp-h844dp-night")
    fun darkDirectoryRetainsReadableLabelsAndActions() {
        browseSamples()
        compose.onNodeWithContentDescription("파일").assertIsSelected()
        compose.onNodeWithContentDescription("새로고침").assertIsEnabled()
        compose.onNodeWithContentDescription("독서 설정").assertIsDisplayed()
        capture("spec-directory-dark.png")
    }

    private fun browseSamples(): File {
        val folder = temporary.newFolder("내 자료")
        listOf("교재", "소설", "만화").forEach { File(folder, it).mkdir() }
        File(folder, "01. 파동과 빛.txt").writeText((
            "01  파동과 빛\n\n" +
            "창가에 놓인 물컵 위로 빛이 내려앉았다. 잔잔하던 수면에 작은 물방울이 떨어지자 동그란 무늬가 가장자리를 향해 퍼져 나갔다.\n\n" +
            "파동을 이해하는 첫걸음은 물질의 이동과 변화의 전달을 구분하는 일이다. 물 위에 띄운 작은 잎은 멀리 떠내려가지 않더라도, 수면의 흔들림은 계속 앞으로 나아간다.\n\n" +
            "같은 현상도 어디에서 바라보는지에 따라 다르게 보인다. 한 점을 오래 관찰하면 주기적인 움직임이 보이고, 한순간의 전체 모습을 보면 공간에 반복되는 무늬가 보인다.\n\n" +
            "이 노트에서는 익숙한 풍경에서 출발해 진동과 빛의 성질을 천천히 살펴본다.\n\n").repeat(8))
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val png = File(folder, "진동 패턴.png")
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 24f }
            canvas.drawText("WAVE / STUDY 01", 50f, 85f, paint)
            paint.strokeWidth = 1f; paint.color = Color.rgb(225, 225, 225)
            for (i in 0..10) {
                canvas.drawLine(50f + 50f * i, 160f, 50f + 50f * i, 560f, paint)
                if (i <= 8) canvas.drawLine(50f, 160f + 50f * i, 550f, 160f + 50f * i, paint)
            }
            paint.color = Color.rgb(0, 53, 255); paint.strokeWidth = 3f
            for (x in 51..550) canvas.drawLine((x - 1).toFloat(), 360f - 135f * sin((x - 51) / 65f), x.toFloat(), 360f - 135f * sin((x - 50) / 65f), paint)
            paint.color = Color.rgb(90, 90, 90); paint.textSize = 18f
            canvas.drawText("WAVE / DETAIL", 50f, 650f, paint)
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        // PdfDocument creation is not implemented by this JVM environment. Use a real
        // ReportLab-generated, renderer-checked fixture; native PDF reading has device tests.
        checkNotNull(javaClass.getResourceAsStream("/wave-notes.pdf")).use { input ->
            File(folder, "실험 노트.pdf").outputStream().use { output -> input.copyTo(output) }
        }
        ZipOutputStream(File(folder, "도해 모음.zip").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.png")); png.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("파일")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
            model.updateSettings(model.library.value.settings.copy(backgroundColor = 0xFFFFFFFFL, textColor = 0xFF252525L, fontSizeSp = 18f, lineSpacing = 1.65f, marginDp = 24f))
            model.selectFolder(Uri.fromFile(folder))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("01. 파동과 빛.txt")).fetchSemanticsNodes().isNotEmpty() &&
            ViewModelProvider(compose.activity)[LibraryViewModel::class.java].library.value.settings.fontSizeSp == 18f }
        return folder
    }

    private fun capture(name: String) {
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        try {
            val target = File("build/outputs/screenshots", name).apply { parentFile.mkdirs() }
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
