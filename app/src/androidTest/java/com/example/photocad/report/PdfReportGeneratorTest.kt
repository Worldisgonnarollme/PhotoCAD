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
            for ((count, pages) in listOf(1 to 1, 2 to 1, 3 to 2, 100 to 50)) {
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
        val slot = ReportLayout.prepare(1, description)
        assertEquals(ReportRules.caption(1, description), slot.caption.text.toString())
        assertTrue(slot.frameHeight >= ReportLayout.MIN_FRAME_HEIGHT)
        assertTrue(slot.frameHeight + ReportLayout.CAPTION_GAP + slot.caption.height <= ReportLayout.SLOT_HEIGHT)
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
                        assertEquals(Color.WHITE, bitmap.getPixel(297, 600))
                    } finally { bitmap.recycle() }
                }
            }
        } finally { input.delete(); report.file.delete() }
    }

}
