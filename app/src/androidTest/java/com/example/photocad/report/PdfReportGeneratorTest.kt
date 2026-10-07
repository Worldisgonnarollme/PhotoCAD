package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfReportGeneratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(): File {
        val file = File.createTempFile("portrait", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(240, 480, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        return file
    }
    @Test fun oneTwoThreeAndLargeReports() = runBlocking {
        val input = source()
        try {
            for ((count, pages) in listOf(1 to 1, 2 to 2, 3 to 3, 100 to 100)) {
                val output = PdfReportGenerator().generate(
                    List(count) { PdfPhoto(input.path, "Русское описание ${it + 1}") }, context.cacheDir
                )
                try {
                    PdfRenderer(ParcelFileDescriptor.open(output.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                        assertEquals(pages, renderer.pageCount)
                        renderer.openPage(0).use { page ->
                            assertEquals(595, page.width)
                            assertEquals(842, page.height)
                        }
                    }
                } finally { output.file.delete() }
            }
        } finally { input.delete() }
    }
    @Test fun longCaptionFitsWithoutEllipsis() {
        val description = "Подготовка поверхности и проверка качества выполненных работ. ".repeat(15)
        val slot = ReportLayout.prepare(1, "План второго этажа", 2, 8, description)
        assertEquals(ReportRules.caption(1, "План второго этажа", 2, 8, description), slot.caption.text.toString())
        assertTrue(slot.frameHeight >= ReportLayout.MIN_FRAME_HEIGHT)
        assertTrue(slot.drawingFrameHeight + ReportLayout.GAP + slot.frameHeight +
            ReportLayout.CAPTION_GAP + slot.caption.height <= ReportLayout.CONTENT_HEIGHT)
    }
    @Test fun noInputOrMissingFileLeavesNoOutput() = runBlocking {
        val dir = File(context.cacheDir, "pdf-failure-${System.nanoTime()}").apply { mkdirs() }
        try {
            for (items in listOf(emptyList(), listOf(PdfPhoto("/not/present.jpg", "Нет файла")))) {
                try { PdfReportGenerator().generate(items, dir); fail("Expected readable error") }
                catch (expected: ReportException) { assertFalse(expected.message.isNullOrBlank()) }
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun exportVisualFixtureForManualComparison() = runBlocking {
        val input = source()
        try {
            val dir = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
            val output = PdfReportGenerator().generate(
                listOf(PdfPhoto(input.path, "Вертикальный снимок без обрезки"),
                    PdfPhoto(input.path, "Длинное описание выполненных работ. ".repeat(12)),
                    PdfPhoto(input.path, "Последняя фотография в верхней области")), dir)
            val fixture = File(dir, "visual-comparison.pdf")
            output.file.copyTo(fixture, overwrite = true)
            output.file.delete()
            assertTrue(fixture.length() > 0)
        } finally { input.delete() }
    }
    @Test fun cancellationAndCorruptionLeaveNoFinalFile() = runBlocking {
        val input = source()
        val dir = File(context.cacheDir, "cancel-${System.nanoTime()}").apply { mkdirs() }
        try {
            try {
                PdfReportGenerator().generate(List(3) { PdfPhoto(input.path, "Описание") }, dir) { _, _ ->
                    throw kotlinx.coroutines.CancellationException("Test cancellation")
                }
                fail("Expected cancellation")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
            input.writeText("broken image")
            try { PdfReportGenerator().generate(listOf(PdfPhoto(input.path, "Повреждено")), dir); fail("Expected failure") }
            catch (_: ReportException) { }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { input.delete(); dir.deleteRecursively() }
    }
    @Test fun portraitIsCenteredAndOddPageHasEmptyLowerHalf() = runBlocking {
        val input = source()
        val report = PdfReportGenerator().generate(listOf(PdfPhoto(input.path, "Портрет")), context.cacheDir)
        try {
            PdfRenderer(ParcelFileDescriptor.open(report.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.BLUE, bitmap.getPixel(297, 200))
                        assertEquals(Color.WHITE, bitmap.getPixel(50, 200))
                        assertEquals(Color.BLUE, bitmap.getPixel(297, 600))
                    } finally { bitmap.recycle() }
                }
            }
        } finally { input.delete(); report.file.delete() }
    }

    @Test fun pageShowsDrawingPreviewAndPointMarkerAboveThePhoto() = runBlocking {
        val drawing = twoPageDrawing()
        val photo = sourceWithColor("photo", Color.GREEN)
        val report = PdfReportGenerator().generate(
            listOf(PdfPhoto(photo.path, "Подпись", pointNumber = 3, drawingPage = 2,
                drawingPath = drawing.path, drawingName = "План второго этажа", pointX = 0.5f, pointY = 0.5f,
                colorIndex = 1)),
            context.cacheDir
        )
        try {
            PdfRenderer(ParcelFileDescriptor.open(report.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.BLUE, bitmap.getPixel(297, 90))
                        assertPdfColorNear(com.example.photocad.data.PointColors.argb(1), bitmap.getPixel(297, 153))
                        assertPdfColorNear(Color.GREEN, bitmap.getPixel(297, 500))
                    } finally { bitmap.recycle() }
                }
            }
        } finally {
            drawing.delete()
            photo.delete()
            report.file.delete()
        }
    }

    private fun sourceWithColor(prefix: String, color: Int): File {
        val file = File.createTempFile(prefix, ".png", context.cacheDir)
        val image = Bitmap.createBitmap(240, 480, Bitmap.Config.ARGB_8888)
        image.eraseColor(color)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        return file
    }

    private fun twoPageDrawing(): File {
        val file = File.createTempFile("drawing-pages", ".pdf", context.cacheDir)
        val pdf = android.graphics.pdf.PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(240, 480, index + 1).create())
                page.canvas.drawColor(if (index == 0) Color.WHITE else Color.BLUE)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
            return file
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        } finally {
            pdf.close()
        }
    }

}
