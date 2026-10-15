package com.example.photocad.report

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.ui.report.PdfPreview
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PdfPreviewLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun switchingRenderedPagesDoesNotRecyclePublishedImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("preview", ".pdf", context.cacheDir)
        // A plain three-page PDF: this test is about PdfPreview paging, not about how the file was produced.
        val document = PdfDocument()
        try {
            listOf(Color.WHITE, Color.LTGRAY, Color.WHITE).forEachIndexed { index, color ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(
                    ReportLayout.PAGE_WIDTH, ReportLayout.PAGE_HEIGHT, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        try {
            compose.setContent { MaterialTheme { PdfPreview(file, 3) } }
            repeat(4) {
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Страница 1 PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Страница 1 PDF").captureToImage()
                compose.onNodeWithText("Далее").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Страница 2 PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Страница 2 PDF").captureToImage()
                compose.onNodeWithText("Назад").performClick()
            }
            compose.waitForIdle()
        } finally { file.delete() }
    }
}
