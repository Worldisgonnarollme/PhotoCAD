package com.example.photocad.report

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.ui.report.PdfPreview
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PdfPreviewLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun switchingRenderedPagesDoesNotRecyclePublishedImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("preview", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val report = runBlocking { PdfReportGenerator().generate(List(3) { PdfPhoto(source.path, "Страница") }, context.cacheDir) }
        try {
            compose.setContent { MaterialTheme { PdfPreview(report.file, report.pageCount) } }
            repeat(4) {
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Страница 1 PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Страница 1 PDF").captureToImage()
                compose.onNodeWithText("Далее").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Страница 2 PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Страница 2 PDF").captureToImage()
                compose.onNodeWithText("Назад").performClick()
            }
            compose.waitForIdle()
        } finally { source.delete(); report.file.delete() }
    }
}
